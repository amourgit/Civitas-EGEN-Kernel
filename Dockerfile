# Image du Kernel EGEN (docs/architecture/19, phase P0, étape P0-1).
#
# Construction : docker build -t egen-kernel:dev .
# Exécution    : voir deploy/README.md (variables d'environnement, ordre de démarrage Keycloak puis Kernel).
#
# Deux étages : le premier compile le Kernel avec Maven (tests exclus : ils se lancent avec « mvn verify »),
# le second ne contient que l'environnement d'exécution Java et l'application, sous un utilisateur non-root.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl egen-bootstrap -am -DskipTests package

FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.title="egen-kernel" \
      org.opencontainers.image.description="Kernel EGEN : plateforme neutre qui monte, enregistre, connecte et exploite des services" \
      org.opencontainers.image.source="https://github.com/amourgit/Civitas-EGEN-Kernel"

# Utilisateur non-root 10001 : le conteneur ne tourne jamais en administrateur.
RUN groupadd --system --gid 10001 egen \
 && useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin egen
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/egen-bootstrap/target/quarkus-app/ /app/
USER 10001:10001

EXPOSE 8080
# 75 % de la mémoire allouée au conteneur pour le tas Java : le reste sert à la machine virtuelle et au système.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
