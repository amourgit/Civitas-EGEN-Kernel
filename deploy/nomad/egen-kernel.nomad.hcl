# Job Nomad du Kernel EGEN (docs/architecture/19, phase P0, étape P0-2).
#
# Valider  : nomad job validate -var-file=deploy/nomad/egen-kernel.vars.example.hcl deploy/nomad/egen-kernel.nomad.hcl
# Planifier: nomad job plan     -var-file=<votre fichier de variables> deploy/nomad/egen-kernel.nomad.hcl
# Lancer   : nomad job run      -var-file=<votre fichier de variables> deploy/nomad/egen-kernel.nomad.hcl
#
# Ordre de démarrage : Keycloak d'abord, puis ce job (le Kernel ne démarre pas sans l'adresse de Keycloak ; voir deploy/README.md).
#
# Limites connues de cette étape (écrites dans deploy/README.md) :
#  - les mots de passe passent par les variables du job et sont donc visibles dans sa spécification : acceptable pour un
#    essai, remplacé par Vault à l'étape P0-4 (le bloc « vault » du job y sera ajouté) ;
#  - le conteneur utilise le réseau de l'hôte (network_mode = "host") pour atteindre PostgreSQL, Keycloak, Consul et NATS
#    par « localhost » comme dans l'environnement d'essai ;
#  - count supérieur à 1 exige l'étape P0-3 (idempotence et liens de traces partagés) : tant qu'elle n'est pas faite,
#    gardez count = 1.

variable "image" {
  type        = string
  default     = "egen-kernel:dev"
  description = "Image du Kernel (construite par le Dockerfile de la racine du dépôt)."
}

variable "datacenter" {
  type    = string
  default = "dc1"
}

variable "count" {
  type        = number
  default     = 1
  description = "Nombre d'exemplaires du Kernel. Gardez 1 tant que l'étape P0-3 n'est pas faite."
}

variable "oidc_issuer_url" {
  type        = string
  description = "Adresse du realm Keycloak qui émet les jetons, par exemple http://localhost:8180/realms/egen."
}

variable "oidc_jwks_url" {
  type        = string
  description = "Adresse des clés publiques de ce realm, par exemple http://localhost:8180/realms/egen/protocol/openid-connect/certs."
}

variable "registry_db_url" {
  type    = string
  default = "jdbc:postgresql://localhost:5432/egen_registry"
}

variable "registry_db_user" {
  type    = string
  default = "egen"
}

variable "registry_db_password" {
  type        = string
  description = "Mot de passe PostgreSQL du Registry (remplacé par Vault à l'étape P0-4)."
}

variable "deployment_adapters" {
  type        = string
  default     = "nomad"
  description = "Moteurs de déploiement activés : nomad, kubernetes, ou une liste séparée par des virgules."
}

variable "nomad_addr" {
  type    = string
  default = "http://localhost:4646"
}

variable "consul_addr" {
  type    = string
  default = "http://localhost:8500"
}

variable "vault_addr" {
  type    = string
  default = "http://localhost:8200"
}

variable "nats_url" {
  type    = string
  default = "nats://localhost:4222"
}

variable "otel_endpoint" {
  type        = string
  default     = ""
  description = "Collecteur OpenTelemetry (OTLP/gRPC). Vide : aucun export (docs/architecture/15)."
}

job "egen-kernel" {
  datacenters = [var.datacenter]
  type        = "service"

  # Mise à jour progressive : un exemplaire à la fois, retour automatique à la version précédente si le nouvel
  # exemplaire ne devient pas sain.
  update {
    max_parallel      = 1
    min_healthy_time  = "20s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "kernel" {
    count = var.count

    network {
      port "http" {}
    }

    # Enregistrement du Kernel dans le catalogue de services de Nomad, avec son contrôle de santé.
    service {
      name     = "egen-kernel"
      port     = "http"
      provider = "nomad"

      check {
        type     = "http"
        path     = "/api/v1/health"
        interval = "10s"
        timeout  = "3s"
      }
    }

    restart {
      attempts = 3
      interval = "5m"
      delay    = "15s"
      mode     = "delay"
    }

    task "kernel" {
      driver = "docker"

      config {
        image        = var.image
        network_mode = "host"
      }

      env {
        QUARKUS_HTTP_HOST = "0.0.0.0"
        QUARKUS_HTTP_PORT = "${NOMAD_PORT_http}"

        EGEN_OIDC_ISSUER_URL      = var.oidc_issuer_url
        EGEN_OIDC_JWKS_URL        = var.oidc_jwks_url
        EGEN_REGISTRY_DB_URL      = var.registry_db_url
        EGEN_REGISTRY_DB_USER     = var.registry_db_user
        EGEN_REGISTRY_DB_PASSWORD = var.registry_db_password
        EGEN_DEPLOYMENT_ADAPTERS  = var.deployment_adapters

        NOMAD_ADDR                  = var.nomad_addr
        CONSUL_HTTP_ADDR            = var.consul_addr
        VAULT_ADDR                  = var.vault_addr
        NATS_URL                    = var.nats_url
        OTEL_EXPORTER_OTLP_ENDPOINT = var.otel_endpoint
      }

      resources {
        cpu    = 500
        memory = 768
      }

      # Le Kernel arrête proprement ses réconciliations en cours avant de sortir.
      kill_timeout = "30s"
    }
  }
}
