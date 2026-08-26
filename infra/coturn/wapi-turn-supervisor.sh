#!/bin/zsh
set -euo pipefail

wapi_project_id="${WAPI_FIREBASE_PROJECT:-whappy-d97e7}"
wapi_state_root="${WAPI_TURN_STATE_DIR:-${HOME}/.local/state/wapi-turn}"
wapi_config_path="${wapi_state_root}/turnserver.conf"
wapi_pid_path="${wapi_state_root}/turnserver.pid"
wapi_public_ip_path="${wapi_state_root}/public-ip"
wapi_relay_min_port=49160
wapi_relay_max_port=49200
wapi_heartbeat_seconds=240
wapi_turnserver_bin="${WAPI_TURNSERVER_BIN:-/opt/homebrew/bin/turnserver}"
wapi_gcloud_bin="${WAPI_GCLOUD_BIN:-/opt/homebrew/bin/gcloud}"
wapi_jq_bin="${WAPI_JQ_BIN:-/usr/bin/jq}"
wapi_curl_bin="${WAPI_CURL_BIN:-/usr/bin/curl}"

require_executable() {
  if [[ ! -x "$1" ]]; then
    print -u2 "WAPI TURN: exécutable introuvable: $1"
    exit 1
  fi
}

current_network() {
  local wapi_interface wapi_candidate_interface wapi_lan_ip="" wapi_public_ip
  wapi_interface=$(/usr/sbin/route -n get default 2>/dev/null | /usr/bin/awk '/interface:/{print $2; exit}')
  for wapi_candidate_interface in "$wapi_interface" en0 en1 en2; do
    [[ -n "$wapi_candidate_interface" ]] || continue
    wapi_lan_ip=$(/usr/sbin/ipconfig getifaddr "$wapi_candidate_interface" 2>/dev/null || true)
    if print -r -- "$wapi_lan_ip" | /usr/bin/grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$'; then
      break
    fi
  done
  wapi_public_ip=$($wapi_curl_bin -fsS --max-time 10 https://api.ipify.org)
  if ! print -r -- "$wapi_lan_ip" | /usr/bin/grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$'; then
    print -u2 "WAPI TURN: adresse LAN indisponible"
    return 1
  fi
  if ! print -r -- "$wapi_public_ip" | /usr/bin/grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$'; then
    print -u2 "WAPI TURN: adresse publique indisponible"
    return 1
  fi
  print -r -- "${wapi_lan_ip}|${wapi_public_ip}"
}

load_shared_secret() {
  local wapi_secret
  wapi_secret=$($wapi_gcloud_bin secrets versions access latest \
    --secret=WAPI_TURN_SHARED_SECRET \
    --project="$wapi_project_id" 2>/dev/null)
  if [[ ${#wapi_secret} -lt 32 ]]; then
    print -u2 "WAPI TURN: secret Firebase invalide"
    return 1
  fi
  print -r -- "$wapi_secret"
}

write_turn_config() {
  local wapi_lan_ip="$1" wapi_public_ip="$2" wapi_secret="$3" wapi_temp_config
  /bin/mkdir -p "$wapi_state_root"
  /bin/chmod 700 "$wapi_state_root"
  wapi_temp_config=$(/usr/bin/mktemp "${wapi_state_root}/turnserver.XXXXXX")
  /bin/chmod 600 "$wapi_temp_config"
  {
    print -r -- "listening-port=3478"
    print -r -- "listening-ip=${wapi_lan_ip}"
    print -r -- "relay-ip=${wapi_lan_ip}"
    print -r -- "external-ip=${wapi_public_ip}/${wapi_lan_ip}"
    print -r -- "min-port=${wapi_relay_min_port}"
    print -r -- "max-port=${wapi_relay_max_port}"
    print -r -- "realm=wapi.app"
    print -r -- "use-auth-secret"
    print -r -- "static-auth-secret=${wapi_secret}"
    print -r -- "fingerprint"
    print -r -- "stale-nonce=600"
    print -r -- "user-quota=12"
    print -r -- "total-quota=500"
    print -r -- "max-allocate-lifetime=3600"
    print -r -- "no-cli"
    print -r -- "no-tls"
    print -r -- "no-dtls"
    print -r -- "no-multicast-peers"
    print -r -- "no-software-attribute"
    print -r -- "denied-peer-ip=0.0.0.0-0.255.255.255"
    print -r -- "denied-peer-ip=10.0.0.0-10.255.255.255"
    print -r -- "denied-peer-ip=100.64.0.0-100.127.255.255"
    print -r -- "denied-peer-ip=127.0.0.0-127.255.255.255"
    print -r -- "denied-peer-ip=169.254.0.0-169.254.255.255"
    print -r -- "denied-peer-ip=172.16.0.0-172.31.255.255"
    print -r -- "denied-peer-ip=192.168.0.0-192.168.255.255"
    print -r -- "log-file=stdout"
    print -r -- "simple-log"
  } > "$wapi_temp_config"
  /bin/mv -f "$wapi_temp_config" "$wapi_config_path"
}

publish_heartbeat() {
  local wapi_public_ip="$1" wapi_access_token wapi_now wapi_payload
  wapi_access_token=$($wapi_gcloud_bin auth print-access-token 2>/dev/null)
  wapi_now=$(/bin/date -u +"%Y-%m-%dT%H:%M:%SZ")
  wapi_payload=$($wapi_jq_bin -nc \
    --arg udp "turn:${wapi_public_ip}:3478?transport=udp" \
    --arg tcp "turn:${wapi_public_ip}:3478?transport=tcp" \
    --arg timestamp "$wapi_now" \
    '{fields:{active:{booleanValue:true},urls:{arrayValue:{values:[{stringValue:$udp},{stringValue:$tcp}]}},updatedAt:{timestampValue:$timestamp},source:{stringValue:"wapi-macos-native"},relayMinPort:{integerValue:"49160"},relayMaxPort:{integerValue:"49200"}}}')
  $wapi_curl_bin -fsS --max-time 15 -X PATCH \
    -H "Authorization: Bearer ${wapi_access_token}" \
    -H "Content-Type: application/json" \
    --data-binary "$wapi_payload" \
    "https://firestore.googleapis.com/v1/projects/${wapi_project_id}/databases/(default)/documents/systemConfig/webrtcRelay" \
    >/dev/null
}

run_supervisor() {
  require_executable "$wapi_turnserver_bin"
  require_executable "$wapi_gcloud_bin"
  require_executable "$wapi_jq_bin"
  require_executable "$wapi_curl_bin"
  /bin/mkdir -p "$wapi_state_root"
  /bin/chmod 700 "$wapi_state_root"

  local wapi_child_pid="" wapi_signature="" wapi_network wapi_lan_ip wapi_public_ip wapi_secret
  cleanup_turn() {
    if [[ -n "$wapi_child_pid" ]] && /bin/kill -0 "$wapi_child_pid" 2>/dev/null; then
      /bin/kill "$wapi_child_pid" 2>/dev/null || true
      wait "$wapi_child_pid" 2>/dev/null || true
    fi
  }
  trap cleanup_turn EXIT INT TERM

  while true; do
    if wapi_network=$(current_network); then
      wapi_lan_ip="${wapi_network%%|*}"
      wapi_public_ip="${wapi_network##*|}"
      if [[ "${wapi_lan_ip}|${wapi_public_ip}" != "$wapi_signature" ]] ||
         [[ -z "$wapi_child_pid" ]] || ! /bin/kill -0 "$wapi_child_pid" 2>/dev/null; then
        cleanup_turn
        wapi_secret=$(load_shared_secret)
        write_turn_config "$wapi_lan_ip" "$wapi_public_ip" "$wapi_secret"
        "$wapi_turnserver_bin" -c "$wapi_config_path" --pidfile "$wapi_pid_path" &
        wapi_child_pid=$!
        /bin/sleep 2
        if ! /bin/kill -0 "$wapi_child_pid" 2>/dev/null; then
          print -u2 "WAPI TURN: coturn n’a pas démarré"
          exit 1
        fi
        wapi_signature="${wapi_lan_ip}|${wapi_public_ip}"
        print -r -- "$wapi_public_ip" > "$wapi_public_ip_path"
        /bin/chmod 600 "$wapi_public_ip_path"
        print -r -- "WAPI TURN: relais actif, réseau actualisé"
      fi
      publish_heartbeat "$wapi_public_ip" || print -u2 "WAPI TURN: heartbeat Firebase différé"
    else
      print -u2 "WAPI TURN: réseau indisponible, nouvelle tentative"
    fi
    /bin/sleep "$wapi_heartbeat_seconds"
  done
}

case "${1:-run}" in
  run) run_supervisor ;;
  *) print -u2 "Usage: $0 run"; exit 2 ;;
esac
