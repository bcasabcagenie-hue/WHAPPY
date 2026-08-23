#!/usr/bin/env bash
set -euo pipefail

readonly WAPI_PROJECT_ID="whappy-d97e7"
readonly WAPI_LIVEKIT_API_KEY="wapi-prod"
readonly WAPI_LIVEKIT_SECRET_NAME="WAPI_LIVEKIT_API_SECRET"
readonly WAPI_RUNTIME_DIR="/opt/wapi-live"

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends ca-certificates curl docker.io jq
systemctl enable --now docker

cat > /etc/sysctl.d/99-wapi-live.conf <<EOF
net.core.rmem_max=5000000
net.core.wmem_max=5000000
EOF
sysctl --system >/dev/null

install -d -m 700 "${WAPI_RUNTIME_DIR}" "${WAPI_RUNTIME_DIR}/caddy-data" "${WAPI_RUNTIME_DIR}/caddy-config"

WAPI_ACCESS_TOKEN="$(curl --fail --silent \
  -H 'Metadata-Flavor: Google' \
  'http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token' \
  | jq -r '.access_token')"
WAPI_SECRET_PAYLOAD="$(curl --fail --silent \
  -H "Authorization: Bearer ${WAPI_ACCESS_TOKEN}" \
  "https://secretmanager.googleapis.com/v1/projects/${WAPI_PROJECT_ID}/secrets/${WAPI_LIVEKIT_SECRET_NAME}/versions/latest:access" \
  | jq -r '.payload.data')"
WAPI_LIVEKIT_API_SECRET="$(printf '%s' "${WAPI_SECRET_PAYLOAD}" | tr '_-' '/+' | base64 --decode)"
WAPI_EXTERNAL_IP="$(curl --fail --silent \
  -H 'Metadata-Flavor: Google' \
  'http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip')"
WAPI_LIVE_HOST="${WAPI_EXTERNAL_IP//./-}.sslip.io"

umask 077
cat > "${WAPI_RUNTIME_DIR}/livekit.yaml" <<EOF
port: 7880
log_level: info

rtc:
  tcp_port: 7881
  port_range_start: 50000
  port_range_end: 60000
  use_external_ip: true

redis:
  address: 127.0.0.1:6379

keys:
  ${WAPI_LIVEKIT_API_KEY}: '${WAPI_LIVEKIT_API_SECRET}'

turn:
  enabled: true
  domain: ${WAPI_LIVE_HOST}
  udp_port: 3478

room:
  empty_timeout: 300
  departure_timeout: 20

webhook:
  api_key: ${WAPI_LIVEKIT_API_KEY}
  urls:
    - https://europe-west1-${WAPI_PROJECT_ID}.cloudfunctions.net/livekitWebhook
EOF

cat > "${WAPI_RUNTIME_DIR}/Caddyfile" <<EOF
${WAPI_LIVE_HOST} {
  encode zstd gzip
  reverse_proxy 127.0.0.1:7880
  header {
    Strict-Transport-Security "max-age=31536000; includeSubDomains"
    X-Content-Type-Options "nosniff"
  }
}
EOF

docker rm -f wapi-livekit wapi-live-redis wapi-live-caddy 2>/dev/null || true

docker run -d \
  --name wapi-live-redis \
  --restart unless-stopped \
  --network host \
  redis:7.4-alpine \
  redis-server --bind 127.0.0.1 --protected-mode yes --save 60 1

docker run -d \
  --name wapi-livekit \
  --restart unless-stopped \
  --network host \
  -v "${WAPI_RUNTIME_DIR}/livekit.yaml:/etc/livekit.yaml:ro" \
  livekit/livekit-server:v1.12.0 \
  --config /etc/livekit.yaml

docker run -d \
  --name wapi-live-caddy \
  --restart unless-stopped \
  --network host \
  -v "${WAPI_RUNTIME_DIR}/Caddyfile:/etc/caddy/Caddyfile:ro" \
  -v "${WAPI_RUNTIME_DIR}/caddy-data:/data" \
  -v "${WAPI_RUNTIME_DIR}/caddy-config:/config" \
  caddy:2-alpine

cat > "${WAPI_RUNTIME_DIR}/healthcheck.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
curl --silent --show-error --max-time 10 -o /dev/null -w '%{http_code}' "https://${WAPI_LIVE_HOST}" | grep -Eq '^(200|404)$'
docker inspect --format='{{.State.Running}}' wapi-livekit | grep -qx true
docker inspect --format='{{.State.Running}}' wapi-live-redis | grep -qx true
docker inspect --format='{{.State.Running}}' wapi-live-caddy | grep -qx true
EOF
chmod 700 "${WAPI_RUNTIME_DIR}/healthcheck.sh"

cat > /etc/systemd/system/wapi-live-health.service <<EOF
[Unit]
Description=WAPI Live health check
After=docker.service network-online.target

[Service]
Type=oneshot
ExecStart=${WAPI_RUNTIME_DIR}/healthcheck.sh
EOF

cat > /etc/systemd/system/wapi-live-health.timer <<EOF
[Unit]
Description=Run WAPI Live health check every minute

[Timer]
OnBootSec=3min
OnUnitActiveSec=1min
Unit=wapi-live-health.service

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now wapi-live-health.timer

unset WAPI_ACCESS_TOKEN WAPI_SECRET_PAYLOAD WAPI_LIVEKIT_API_SECRET
