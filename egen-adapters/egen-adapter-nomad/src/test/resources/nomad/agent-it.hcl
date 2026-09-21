# Config de l'agent Nomad pour NomadDeploymentAdapterIT (voir
# docs/architecture/17-strategie-de-tests.md, niveau 3).
#
# Pourquoi pas simplement "-dev" ? "nomad agent -dev" demarre un serveur
# ET un client dans le meme processus. Au demarrage, le role client tente
# de fingerprinter les drivers de tache (docker, exec) via des appels
# cgroup — ce qui echoue et fait sortir le processus en erreur (exit
# code 1) dans un conteneur Docker non privilegie, ce qu'est par defaut
# un conteneur demarre par Testcontainers. C'est la cause du
# "ContainerLaunchException / Connection refused" observe en CI.
#
# Ce test (voir NomadDeploymentAdapterIT, "Perimetre assume") verifie
# uniquement que Nomad ACCEPTE le job traduit par l'adapter, expose son
# statut, et le supprime de facon idempotente — jamais qu'une allocation
# atteint l'etat "running". Un serveur Nomad seul (sans role client)
# suffit donc entierement a ce perimetre, et evite completement le
# fingerprinting qui plante : aucun besoin d'un conteneur privilegie.
data_dir  = "/nomad/data"
bind_addr = "0.0.0.0"
log_level = "WARN"
datacenter = "dc1" # cf. NomadDeploymentAdapter.DEFAULT_DATACENTER — explicite plutot que suppose

server {
  enabled          = true
  bootstrap_expect = 1
}

client {
  enabled = false
}
