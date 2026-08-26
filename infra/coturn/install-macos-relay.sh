#!/bin/zsh
set -euo pipefail

wapi_script_dir="${0:A:h}"
wapi_source_supervisor="${wapi_script_dir}/wapi-turn-supervisor.sh"
wapi_libexec_dir="${HOME}/.local/libexec/wapi-turn"
wapi_supervisor="${wapi_libexec_dir}/wapi-turn-supervisor.sh"
wapi_launch_agents="${HOME}/Library/LaunchAgents"
wapi_plist_path="${wapi_launch_agents}/com.wapi.turn-relay.plist"
wapi_state_root="${HOME}/.local/state/wapi-turn"
wapi_user_id=$(/usr/bin/id -u)

if [[ ! -x "$wapi_source_supervisor" ]]; then
  print -u2 "Le superviseur WAPI TURN n’est pas exécutable."
  exit 1
fi

# The old Docker/Colima bridge did not forward TURN UDP reliably on macOS.
# Stop only WAPI's coturn container before binding the native service.
if [[ -x /opt/homebrew/bin/docker ]]; then
  for wapi_container in $(/opt/homebrew/bin/docker ps --filter name=wapi-turn --format '{{.Names}}' 2>/dev/null); do
    /opt/homebrew/bin/docker stop "$wapi_container" >/dev/null
  done
fi

/bin/mkdir -p "$wapi_launch_agents" "$wapi_state_root" "$wapi_libexec_dir"
/bin/chmod 700 "$wapi_state_root"
/bin/chmod 700 "$wapi_libexec_dir"
/bin/cp -f "$wapi_source_supervisor" "$wapi_supervisor"
/bin/chmod 700 "$wapi_supervisor"

wapi_temp_plist=$(/usr/bin/mktemp "${wapi_state_root}/launch-agent.XXXXXX")
{
  print '<?xml version="1.0" encoding="UTF-8"?>'
  print '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">'
  print '<plist version="1.0"><dict>'
  print '  <key>Label</key><string>com.wapi.turn-relay</string>'
  print '  <key>ProgramArguments</key><array>'
  print "    <string>${wapi_supervisor}</string><string>run</string>"
  print '  </array>'
  print '  <key>RunAtLoad</key><true/>'
  print '  <key>KeepAlive</key><true/>'
  print '  <key>ThrottleInterval</key><integer>15</integer>'
  print "  <key>StandardOutPath</key><string>${wapi_state_root}/relay.log</string>"
  print "  <key>StandardErrorPath</key><string>${wapi_state_root}/relay-error.log</string>"
  print '</dict></plist>'
} > "$wapi_temp_plist"
/usr/bin/plutil -lint "$wapi_temp_plist" >/dev/null
/bin/mv -f "$wapi_temp_plist" "$wapi_plist_path"
/bin/chmod 600 "$wapi_plist_path"

/bin/launchctl bootout "gui/${wapi_user_id}" "$wapi_plist_path" 2>/dev/null || true
/bin/launchctl bootstrap "gui/${wapi_user_id}" "$wapi_plist_path"
/bin/launchctl kickstart -k "gui/${wapi_user_id}/com.wapi.turn-relay"

print "WAPI TURN installé. Journaux: ${wapi_state_root}/relay.log"
