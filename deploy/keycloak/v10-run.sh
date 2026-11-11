#!/usr/bin/env bash
# V10 : le Kernel accepte-t-il les jetons Keycloak, et le role egen-admin est-il bien lu ?
K=${KERNEL:-http://localhost:8080}; KC=http://localhost:8180; R=$KC/realms/egen; B=$KC/admin/realms/egen
token() { curl -s -m 10 -d grant_type=client_credentials -d client_id="$1" -d client_secret="$2" $R/protocol/openid-connect/token \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])"; }
code() { curl -s -m 15 -o /dev/null -w "%{http_code}" "$@"; }
declare_svc() { code -X POST $K/api/v1/services -H "Authorization: Bearer $1" -H "Content-Type: application/yaml" \
  -H "Idempotency-Key: v10-$RANDOM$RANDOM" --data-binary @/tmp/v10/news.yaml; }
read_svc() { code $K/api/v1/services -H "Authorization: Bearer $1"; }

# client temporaire SANS mapper "groups", mais qui porte le role egen-admin dans realm_access
ADM=$(curl -s -m 10 -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin-test $KC/realms/master/protocol/openid-connect/token | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
A="Authorization: Bearer $ADM"
if [ "$(curl -s -m 10 "$B/clients?clientId=sans-mapper" -H "$A" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))")" = "0" ]; then
  curl -s -m 10 -o /dev/null -X POST $B/clients -H "$A" -H "Content-Type: application/json" -d '{"clientId":"sans-mapper","publicClient":false,"secret":"secret-sans-mapper","serviceAccountsEnabled":true,"standardFlowEnabled":false}'
  CID=$(curl -s -m 10 "$B/clients?clientId=sans-mapper" -H "$A" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['id'])")
  UID_=$(curl -s -m 10 "$B/clients/$CID/service-account-user" -H "$A" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
  curl -s -m 10 -o /dev/null -X POST "$B/users/$UID_/role-mappings/realm" -H "$A" -H "Content-Type: application/json" -d "[$(curl -s -m 10 $B/roles/egen-admin -H "$A")]"
fi

printf "%-30s %-12s %-12s %s\n" "appelant" "lecture" "declarer" "attendu"
for spec in "platform-operator-service secret-operator-test 200 202" \
            "deploy-pipeline secret-pipeline-test 200 202" \
            "catalog-service secret-catalog-test 200 403" \
            "sans-mapper secret-sans-mapper 200 403"; do
  set -- $spec; T=$(token $1 $2)
  printf "%-30s %-12s %-12s %s\n" "$1" "$(read_svc $T)" "$(declare_svc $T)" "lecture $3, declarer $4"
done
printf "%-30s %-12s %-12s %s\n" "jeton invalide" "$(read_svc abc.def.ghi)" "$(declare_svc abc.def.ghi)" "lecture 401, declarer 401"
printf "%-30s %-12s %-12s %s\n" "sans jeton" "$(code $K/api/v1/services)" "-" "lecture 401"

echo; echo "--- qui porte le role egen-admin (API d'administration de Keycloak) :"
curl -s -m 10 "$B/roles/egen-admin/users" -H "$A" | python3 -c "import sys,json;print('  utilisateurs :',sorted(u['username'] for u in json.load(sys.stdin)))"
curl -s -m 10 "$B/roles/egen-admin/groups" -H "$A" | python3 -c "import sys,json;print('  groupes      :',[g['name'] for g in json.load(sys.stdin)])"
curl -s -m 10 "$B/roles/default-roles-egen/composites" -H "$A" | python3 -c "import sys,json;print('  roles par defaut (composites) :',sorted(r['name'] for r in json.load(sys.stdin)))"
