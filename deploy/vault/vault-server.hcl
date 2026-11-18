# Configuration du serveur Vault hors mode développement (docs/architecture/19, phase P0, étape P0-4).
#
# Différences avec le Vault du docker-compose de développement (mode « -dev ») :
#  - le stockage est PERSISTANT (Raft, volume Docker) : un redémarrage ne perd plus les secrets ;
#  - il n'y a PAS de jeton racine connu d'avance : à la première initialisation Vault produit 5 parts de clé de
#    descellage (seuil : 3) et un jeton racine, à conserver hors de la machine (voir deploy/vault/vault-bootstrap.sh) ;
#  - Vault démarre SCELLÉ : il refuse tout appel tant que 3 parts n'ont pas été fournies.
#
# Limite connue : le port 8200 est en HTTP clair (tls_disable = true). TLS est l'objet de l'étape P0-5 ; d'ici là
# n'exposez ce port que sur un réseau privé.

ui = false
disable_mlock = false

storage "raft" {
  path    = "/vault/file"
  node_id = "vault-1"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = true
}

api_addr     = "http://127.0.0.1:8200"
cluster_addr = "http://127.0.0.1:8201"
