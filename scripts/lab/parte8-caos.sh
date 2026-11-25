#!/usr/bin/env bash
# Parte 8.3 · Caos: apago una dependencia a la vez, observo el efecto y mido cuánto tarda en recuperarse.
# Uso: parte8-caos.sh [rabbitmq|redis|carrier|db]  (sin argumento: los cuatro)
source "$(dirname "$0")/_lib.sh"
PSQL="docker compose exec -T db psql -U logistpulse -X -q -tA"
req() { curl -s -o /tmp/c.json -w '%{http_code} en %{time_total} s' "$@"; }
health() { curl -s -o /dev/null -w '%{http_code}' "$HOST/actuator/health"; }
cronometro() {  # cronometro "descripcion" comando-que-debe-tener-exito
  local t0=$(date +%s); for i in $(seq 1 120); do eval "$2" && break; sleep 1; done
  echo "   recuperado ($1) en $(( $(date +%s) - t0 )) s desde el start"
}
CASOS="${1:-rabbitmq redis carrier db}"
reset_fleet

for c in $CASOS; do case $c in
rabbitmq)
  say "Caos 1: docker compose stop rabbitmq -> despacho"
  docker compose stop rabbitmq 2>&1 | tail -1; ID=$(new_order "Caos rabbit")
  echo "   despacho: $(req -X POST "$HOST/api/orders/$ID/dispatch")   health: $(health)"
  sleep 3; echo "   outbox pendiente: $($PSQL -c 'select count(*) from outbox_event where sent_at is null')   estado: $(curl -s "$HOST/api/orders/$ID" | grep -o '"status":"[A-Z_]*"')"
  docker compose start rabbitmq 2>&1 | tail -1
  cronometro "evento publicado y pedido IN_TRANSIT" "curl -s $HOST/api/orders/$ID | grep -q IN_TRANSIT"
  echo "   health: $(health)"; reset_fleet ;;
redis)
  say "Caos 2: docker compose stop redis -> rutas"
  docker compose stop redis 2>&1 | tail -1
  echo "   ruta nueva: $(req "$HOST/api/routes?toX=11&toY=$((RANDOM % 8))")   $(head -c 90 /tmp/c.json)"
  ID=$(new_order "Caos redis"); echo "   despacho: $(req -X POST "$HOST/api/orders/$ID/dispatch")   health: $(health)"
  docker compose start redis 2>&1 | tail -1
  cronometro "rutas 200 y health 200" "[ \$(curl -s -o /dev/null -w '%{http_code}' '$HOST/api/routes?toX=9&toY=7') = 200 ] && [ \$(curl -s -o /dev/null -w '%{http_code}' $HOST/actuator/health) = 200 ]"
  reset_fleet ;;
carrier)
  say "Caos 3: docker compose stop carrier-mock -> despacho"
  docker compose stop carrier-mock 2>&1 | tail -1; ID=$(new_order "Caos carrier")
  echo "   despacho: $(req -X POST "$HOST/api/orders/$ID/dispatch")   $(grep -o '"etaMinutes":[0-9]*' /tmp/c.json) (ETA local)   health: $(health)"
  docker compose start carrier-mock 2>&1 | tail -1
  cronometro "el transportista vuelve a dar ETA" "curl -s -o /dev/null -f 'http://localhost:9091/carrier/eta?code=LP-PING'"
  reset_fleet ;;
db)
  say "Caos 4: docker compose stop db -> /actuator/health y API"
  docker compose stop db 2>&1 | tail -1
  echo "   GET /actuator/health: $(req "$HOST/actuator/health")   db=$(grep -o '"db":{"status":"[A-Z]*"' /tmp/c.json | cut -d'"' -f6)"
  echo "   GET /api/orders     : $(req "$HOST/api/orders?size=1")"
  docker compose start db 2>&1 | tail -1
  cronometro "health 200 y API 200" "[ \$(curl -s -o /dev/null -w '%{http_code}' $HOST/actuator/health) = 200 ] && [ \$(curl -s -o /dev/null -w '%{http_code}' '$HOST/api/orders?size=1') = 200 ]" ;;
esac; done
