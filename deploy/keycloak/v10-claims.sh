#!/usr/bin/env bash
# Affiche les claims utiles du jeton d'un client Keycloak (sans jamais afficher le jeton lui-meme).
set -euo pipefail
tok=$(curl -s -m 10 -d grant_type=client_credentials -d client_id="$1" -d client_secret="$2" \
  http://localhost:8180/realms/egen/protocol/openid-connect/token)
python3 - "$1" "$tok" <<'PY'
import sys,json,base64
name,raw=sys.argv[1],json.loads(sys.argv[2])
if 'access_token' not in raw: print(f"=== {name} : ERREUR", raw); sys.exit()
p=raw['access_token'].split('.')[1]; c=json.loads(base64.urlsafe_b64decode(p+'='*(-len(p)%4)))
print(f"=== {name}")
for k in ['iss','azp','preferred_username','groups','realm_access','aud']:
    print(f"  {k:20} {c.get(k,'(absent)')}")
PY
