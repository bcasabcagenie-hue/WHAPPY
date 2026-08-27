#!/usr/bin/env bash
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends coturn ca-certificates curl jq

metadata_url="http://metadata.google.internal/computeMetadata/v1"
metadata_header="Metadata-Flavor: Google"
project_id="$(curl -fsS -H "$metadata_header" "$metadata_url/project/project-id")"
internal_ip="$(curl -fsS -H "$metadata_header" "$metadata_url/instance/network-interfaces/0/ip")"
external_ip="$(curl -fsS -H "$metadata_header" "$metadata_url/instance/network-interfaces/0/access-configs/0/external-ip")"
access_token="$(curl -fsS -H "$metadata_header" "$metadata_url/instance/service-accounts/default/token" | jq -r .access_token)"
turn_secret="$(curl -fsS -H "Authorization: Bearer ${access_token}" \
  "https://secretmanager.googleapis.com/v1/projects/${project_id}/secrets/WAPI_TURN_SHARED_SECRET/versions/latest:access" \
  | jq -r .payload.data | base64 --decode)"

if [[ ${#turn_secret} -lt 32 ]]; then
  echo "WAPI TURN: Secret Manager returned an invalid shared secret" >&2
  exit 1
fi

cat >/etc/turnserver.conf <<EOF
listening-port=3478
alt-listening-port=443
listening-ip=${internal_ip}
relay-ip=${internal_ip}
external-ip=${external_ip}/${internal_ip}
min-port=49160
max-port=49200
realm=wapi.app
use-auth-secret
static-auth-secret=${turn_secret}
fingerprint
stale-nonce=600
user-quota=24
total-quota=1200
max-allocate-lifetime=3600
no-cli
no-tls
no-dtls
no-multicast-peers
no-software-attribute
denied-peer-ip=0.0.0.0-0.255.255.255
denied-peer-ip=10.0.0.0-10.255.255.255
denied-peer-ip=100.64.0.0-100.127.255.255
denied-peer-ip=127.0.0.0-127.255.255.255
denied-peer-ip=169.254.0.0-169.254.255.255
denied-peer-ip=172.16.0.0-172.31.255.255
denied-peer-ip=192.168.0.0-192.168.255.255
log-file=syslog
simple-log
EOF
chmod 600 /etc/turnserver.conf

if [[ -f /etc/default/coturn ]]; then
  sed -i 's/^#\?TURNSERVER_ENABLED=.*/TURNSERVER_ENABLED=1/' /etc/default/coturn
fi
systemctl enable coturn
systemctl restart coturn

# Coturn's alternate port is intended for RFC 5780 and is not bound on every
# distribution with a single listening address. Keep the production fallback
# deterministic by forwarding 443 to the authenticated TURN listener.
iptables -t nat -C PREROUTING -p tcp --dport 443 -j REDIRECT --to-ports 3478 2>/dev/null \
  || iptables -t nat -A PREROUTING -p tcp --dport 443 -j REDIRECT --to-ports 3478
iptables -t nat -C PREROUTING -p udp --dport 443 -j REDIRECT --to-ports 3478 2>/dev/null \
  || iptables -t nat -A PREROUTING -p udp --dport 443 -j REDIRECT --to-ports 3478

for attempt in {1..20}; do
  if systemctl is-active --quiet coturn && ss -lntup | grep -qE ':3478|:443'; then
    echo "WAPI TURN active on ${external_ip}"
    exit 0
  fi
  sleep 2
done

systemctl status coturn --no-pager >&2 || true
exit 1
