#!/usr/bin/env bash
# Disposable local CI backend. The two test APKs trust only this runner's short-lived CA on loopback.
set -euo pipefail
: "${RUNNER_TEMP:?Run in a disposable CI checkout}"
fixture="$RUNNER_TEMP/partner-week"
mkdir -p "$fixture"
chmod 700 "$fixture"
psql -v ON_ERROR_STOP=1 <<'SQL'
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
CREATE ROLE service_role NOLOGIN BYPASSRLS;
CREATE SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;
SQL
psql -v ON_ERROR_STOP=1 -f server/partner-week/migration.sql
psql -v ON_ERROR_STOP=1 -c "NOTIFY pgrst, 'reload schema'"
openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 1 -keyout "$fixture/ca.key" -out "$fixture/ca.crt" -subj '/CN=Partner week runner CA' -addext 'basicConstraints=critical,CA:TRUE' >/dev/null 2>&1
openssl req -newkey rsa:2048 -nodes -keyout "$fixture/server.key" -out "$fixture/server.csr" -subj '/CN=127.0.0.1' >/dev/null 2>&1
openssl x509 -req -days 1 -sha256 -in "$fixture/server.csr" -CA "$fixture/ca.crt" -CAkey "$fixture/ca.key" -CAcreateserial -out "$fixture/server.crt" -extfile <(printf '%s\n' 'subjectAltName=IP:127.0.0.1' 'basicConstraints=CA:FALSE' 'extendedKeyUsage=serverAuth') >/dev/null 2>&1
for variant in debug staging; do
  target="app/src/$variant"
  mkdir -p "$target/res/raw" "$target/res/xml"
  cp "$fixture/ca.crt" "$target/res/raw/partner_test_ca.pem"
  cat > "$target/res/xml/partner_week_test_network.xml" <<'XML'
<network-security-config><domain-config><domain>127.0.0.1</domain><trust-anchors><certificates src="@raw/partner_test_ca"/></trust-anchors></domain-config></network-security-config>
XML
  python - "$target/AndroidManifest.xml" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
android = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', android)
ET.register_namespace('tools','http://schemas.android.com/tools')
destination = Path(sys.argv[1])
root = ET.parse(destination).getroot() if destination.exists() else ET.Element('manifest')
app = root.find('application')
if app is None:
    app = ET.SubElement(root, 'application')
app.set('{%s}networkSecurityConfig' % android, '@xml/partner_week_test_network')
ET.ElementTree(root).write(destination, encoding='utf-8', xml_declaration=True)
PY
done
# Provide Supabase's gateway path in front of the real PostgREST instance.
cat > "$fixture/nginx.conf" <<CONF
pid $fixture/nginx.pid;
error_log $fixture/proxy.log warn;
events {}
http {
  access_log off;
  client_body_temp_path $fixture/body;
  proxy_temp_path $fixture/proxy;
  fastcgi_temp_path $fixture/fastcgi;
  uwsgi_temp_path $fixture/uwsgi;
  scgi_temp_path $fixture/scgi;
  server {
    listen 127.0.0.1:18481;
    location /rest/v1/ { proxy_pass http://127.0.0.1:18480/; }
  }
  server {
    listen 127.0.0.1:18443 ssl;
    ssl_certificate $fixture/server.crt;
    ssl_certificate_key $fixture/server.key;
    location / { proxy_pass http://127.0.0.1:8000; }
  }
}
CONF
nginx -p "$fixture/" -c "$fixture/nginx.conf"
export SUPABASE_URL="http://127.0.0.1:18481"
SUPABASE_SERVICE_ROLE_KEY="$(python - <<'PY'
import base64,hashlib,hmac,json,os
def enc(b):
    return base64.urlsafe_b64encode(b).rstrip(b'=')
def segment(obj):
    return enc(json.dumps(obj,separators=(',', ':')).encode())
token=segment({'alg':'HS256','typ':'JWT'})+b'.'+segment({'role':'service_role'})
print((token+b'.'+enc(hmac.new(os.environ['TEST_JWT_SECRET'].encode(),token,hashlib.sha256).digest())).decode())
PY
)"
export SUPABASE_SERVICE_ROLE_KEY
nohup deno run --allow-env --allow-net server/partner-week/index.ts > "$fixture/edge.log" 2>&1 </dev/null &
# Verify TLS, Edge, PostgREST authorization and migrations before starting an emulator.
for attempt in {1..40}; do
  result="$(curl --silent --cacert "$fixture/ca.crt" --max-time 5 --header 'Content-Type: application/json' --data '{"action":"get","deviceKey":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}' https://127.0.0.1:18443/ || true)"
  if python -c 'import json,sys; assert json.load(sys.stdin)=={"week":None}' <<<"$result" 2>/dev/null; then exit 0; fi
  sleep 1
done
cat "$fixture/edge.log" >&2
echo 'Local partner service did not confirm empty membership' >&2
exit 1
