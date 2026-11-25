#!/usr/bin/env bash
# Parte 9.1 · Colección de Postman ejecutada con Newman (el Runner de Postman en línea de comandos, en Docker).
source "$(dirname "$0")/_lib.sh"
reset_fleet
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W 2>/dev/null || pwd)/postman:/etc/newman" postman/newman:6-alpine \
  run logistpulse.postman_collection.json --env-var host=http://host.docker.internal:8080 --disable-unicode --color on \
  | grep -vE '^\s*$|total data received|average response|prerequest|iterations'
