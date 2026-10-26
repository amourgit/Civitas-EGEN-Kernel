#!/usr/bin/env bash
# Lance Nomad en mode dev comme PROCESSUS DE L'HOTE (jamais en conteneur : un client Nomad
# en conteneur n'est pas supporte). Voir docs/architecture/17-strategie-de-tests.md.
# Il annonce l'interface reseau reelle (en -dev par defaut il annonce le loopback, que
# Consul, dans un conteneur, ne peut pas sonder).
set -euo pipefail

command -v nomad >/dev/null || { echo "nomad introuvable : https://developer.hashicorp.com/nomad/install" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "le demon Docker doit tourner (driver docker de Nomad)" >&2; exit 1; }

IFACE="${EGEN_NOMAD_INTERFACE:-$(ip route show default | awk '/default/ {print $5; exit}')}"
echo "Nomad dev sur l'interface ${IFACE} (API : http://localhost:4646)"
exec sudo nomad agent -dev -bind=0.0.0.0 -network-interface="${IFACE}"
