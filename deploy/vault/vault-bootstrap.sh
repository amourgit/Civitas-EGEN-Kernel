#!/usr/bin/env bash
# Initialise, descelle et prépare le Vault de deploy/vault/docker-compose.yml (docs/architecture/19, étape P0-4).
# Rejouable : chaque étape vérifie d'abord ce qui est déjà fait.
#
# Sorties (droits 600, ignorées par git) :
#  - vault-init.json         : les 5 parts de clé de descellage ET le jeton racine. À DÉPLACER HORS DE CETTE MACHINE
#                              (gestionnaire de mots de passe, coffre physique), puis à supprimer d'ici.
#  - vault-kernel-token.txt  : le jeton du Kernel (politique egen-kernel, durée 720 h) : valeur de VAULT_TOKEN du Kernel.
set -euo pipefail

CONTAINER="${VAULT_CONTAINER:-egen-vault-prod}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INIT_FILE="${VAULT_INIT_OUTPUT:-$DIR/vault-init.json}"
KERNEL_TOKEN_FILE="${VAULT_KERNEL_TOKEN_OUTPUT:-$DIR/vault-kernel-token.txt}"
V() { docker exec -i -e VAULT_ADDR=http://127.0.0.1:8200 ${VAULT_TOKEN:+-e VAULT_TOKEN="$VAULT_TOKEN"} "$CONTAINER" vault "$@"; }

echo "=== 1. attendre que Vault réponde"
for i in $(seq 1 30); do
  # « vault status » : code 0 = descellé, 2 = scellé (répond), 1 = ne répond pas.
  set +e; V status >/dev/null 2>&1; rc=$?; set -e
  [ "$rc" = 0 ] || [ "$rc" = 2 ] && break
  sleep 2
done
[ "$rc" = 0 ] || [ "$rc" = 2 ] || { echo "ECHEC : Vault ne répond pas (docker ps ; docker logs $CONTAINER)"; exit 1; }

echo "=== 2. initialisation (5 parts, seuil 3)"
if V status 2>/dev/null | grep -Eq '^Initialized[[:space:]]+true'; then
  echo "  déjà initialisé"
else
  umask 077
  V operator init -key-shares=5 -key-threshold=3 -format=json > "$INIT_FILE"
  chmod 600 "$INIT_FILE"
  echo "  fait : $INIT_FILE (5 parts + jeton racine)"
fi
[ -f "$INIT_FILE" ] || { echo "ECHEC : Vault est initialisé mais $INIT_FILE est absent : fournissez les parts avec VAULT_INIT_OUTPUT=<fichier>"; exit 1; }

echo "=== 3. descellage (3 parts sur 5)"
if V status 2>/dev/null | grep -Eq '^Sealed[[:space:]]+false'; then
  echo "  déjà descellé"
else
  for n in 0 1 2; do
    KEY=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['unseal_keys_b64'][int(sys.argv[2])])" "$INIT_FILE" "$n")
    V operator unseal "$KEY" >/dev/null
  done
  echo "  fait"
fi

export VAULT_TOKEN
VAULT_TOKEN=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['root_token'])" "$INIT_FILE")

echo "=== 4. moteur de secrets « secret/ » (KV version 2)"
if V secrets list -format=json | python3 -c "import json,sys;sys.exit(0 if 'secret/' in json.load(sys.stdin) else 1)"; then
  echo "  déjà activé"
else
  V secrets enable -path=secret kv-v2 >/dev/null
  echo "  activé"
fi

echo "=== 5. politique du Kernel (egen-kernel : lecture de secret/metadata/* seulement)"
V policy write egen-kernel - < "$DIR/policy-egen-kernel.hcl" >/dev/null
echo "  écrite"

echo "=== 6. jeton du Kernel (durée 720 h, sans politique par défaut)"
if [ -f "$KERNEL_TOKEN_FILE" ]; then
  echo "  $KERNEL_TOKEN_FILE existe déjà : non recréé (supprimez-le pour en créer un nouveau)"
else
  umask 077
  V token create -policy=egen-kernel -no-default-policy -ttl=720h -field=token > "$KERNEL_TOKEN_FILE"
  chmod 600 "$KERNEL_TOKEN_FILE"
  echo "  créé : $KERNEL_TOKEN_FILE"
fi

cat <<MSG

Terminé. À faire maintenant, à la main :
  1. Déplacer $INIT_FILE hors de cette machine, puis le supprimer ici.
  2. Révoquer le jeton racine quand vous n'en avez plus besoin :  VAULT_TOKEN=<racine> vault token revoke -self
     (un nouveau jeton racine se régénère plus tard avec 3 parts de clé).
  3. Donner au Kernel le contenu de $KERNEL_TOKEN_FILE comme variable VAULT_TOKEN.
  4. Ce jeton expire dans 720 heures (30 jours) : renouvelez-le AVANT, en recréant un jeton et en redémarrant le Kernel
     (le Kernel ne relit pas son jeton après le démarrage, docs/architecture/14).
MSG
