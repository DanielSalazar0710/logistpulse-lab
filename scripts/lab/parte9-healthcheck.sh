#!/usr/bin/env bash
# Parte 9.4 (reto) · healthcheck del servicio api sin curl: bash + /dev/tcp contra /actuator/health/liveness.
source "$(dirname "$0")/_lib.sh"
estado() { docker inspect --format '{{.State.Health.Status}} (último código {{(index .State.Health.Log 0).ExitCode}})' logistpulse-lab-api-1; }
say "¿Trae curl la imagen JRE? (el healthcheck NO depende de eso: usa solo bash)"
run "docker compose exec -T api sh -c 'for b in curl wget bash; do printf \"%-5s -> \" \$b; command -v \$b || echo no; done'"
say "Definición del healthcheck en docker-compose.yml"
sed -n '/Reto Parte 9.4/,/start_period/p' docker-compose.yml
say "Estado del contenedor"
run "docker compose ps api prometheus --format 'table {{.Service}}\t{{.Status}}'"
say "Con Redis caído la API sigue 'healthy' (liveness no depende de dependencias externas)"
docker compose stop redis 2>&1 | tail -1; sleep 22
echo "/actuator/health          -> $(curl -s -o /dev/null -w '%{http_code}' $HOST/actuator/health)"
echo "/actuator/health/liveness -> $(curl -s -o /dev/null -w '%{http_code}' $HOST/actuator/health/liveness)"
echo "healthcheck de Docker     -> $(estado)"
docker compose start redis 2>&1 | tail -1
