#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${CODEX_PROXY_CERT:-}" ]]; then
  docker compose build
  exit
fi
build_config_dir="$(mktemp -d)"
trap 'rm -rf "$build_config_dir"' EXIT
python3 - "$build_config_dir" <<'PY'
import os,sys,urllib.parse,socket,json
from pathlib import Path
root=Path(sys.argv[1]); proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',os.environ.get('HTTP_PROXY','')))
assert proxy.hostname and not proxy.username and not proxy.password, 'Requires supported credential-free session proxy'
# Preserve session proxy route; containers cannot resolve the outer-only hostname.
host=socket.gethostbyname(proxy.hostname)
(root/'settings.xml').write_text(f'<settings><proxies><proxy><id>session</id><active>true</active><protocol>http</protocol><host>{host}</host><port>{proxy.port or 8080}</port><nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy></proxies></settings>')
ca=os.environ['CODEX_PROXY_CERT'];assert Path(ca).is_file()
(root/'compose.json').write_text(json.dumps({'services':{'api':{'build':{'secrets':['proxy_ca','maven_settings']}},'web':{'build':{'secrets':['proxy_ca']}}},'secrets':{'proxy_ca':{'file':ca},'maven_settings':{'file':str(root/'settings.xml')}}}))
PY
docker compose -f compose.yml -f "$build_config_dir/compose.json" build
