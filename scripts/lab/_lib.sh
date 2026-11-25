#!/usr/bin/env bash
# Utilidades para las demostraciones del laboratorio: imprime cada comando antes de ejecutarlo.
HOST="${HOST:-http://localhost:8080}"
J='Content-Type: application/json'
say() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
run() { printf '\033[0;33m$ %s\033[0m\n' "$*"; eval "$@"; echo; }

# Deja la flota lista: entrega los pedidos en curso y carga las baterías de los AMR libres.
reset_fleet() {
  for id in $(curl -s "$HOST/api/orders?status=IN_TRANSIT&size=100" | grep -o '"id":[0-9]*' | cut -d: -f2); do
    curl -s -o /dev/null -X POST -H "$J" -d '{"to":"DELIVERED"}' "$HOST/api/orders/$id/transitions"
  done
  for id in $(curl -s "$HOST/api/orders?status=DISPATCHED&size=100" | grep -o '"id":[0-9]*' | cut -d: -f2); do
    curl -s -o /dev/null -X POST -H "$J" -d '{"to":"CANCELLED"}' "$HOST/api/orders/$id/transitions"
  done
  for id in $(curl -s "$HOST/api/robots" | grep -o '"id":[0-9]*' | cut -d: -f2); do
    curl -s -o /dev/null -X POST "$HOST/api/robots/$id/charge"
  done
}
# Crea un pedido y devuelve su id
new_order() { curl -s -X POST -H "$J" -d "{\"customer\":\"${1:-Prueba}\",\"destX\":${2:-10},\"destY\":${3:-5}}" "$HOST/api/orders" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2; }
wait_api() { for i in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' "$HOST/actuator/health")" = 200 ] && return; sleep 2; done; }
colas() { docker compose exec -T rabbitmq rabbitmqctl -q list_queues name messages_ready messages_unacknowledged consumers | column -t; }
