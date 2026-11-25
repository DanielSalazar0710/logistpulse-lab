#!/usr/bin/env bash
# Parte 4.1 · Nuevo estado RETURNED (DELIVERED -> RETURNED) probado contra Postgres.
source "$(dirname "$0")/_lib.sh"
ID=$(curl -s "$HOST/api/orders?status=DELIVERED&size=1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

say "Pedido $ID en DELIVERED: el enlace HATEOAS ya ofrece la devolución"
run "curl -s $HOST/api/orders/$ID | grep -o '\"status\":\"[A-Z_]*\"\|\"to-returned\":\"[^\"]*\"'"

say "DELIVERED -> RETURNED"
DESDE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
run "curl -s -w '  <- %{http_code}\n' -X POST -H '$J' -d '{\"to\":\"RETURNED\"}' $HOST/api/orders/$ID/transitions | tail -c 160"
sleep 1
say "Causa en el log de la API"
docker compose logs --no-log-prefix --since "$DESDE" api | grep -m2 -E 'violates check constraint|Failing row' | cut -c1-200
run "docker compose exec -T db psql -U logistpulse -tc \"select pg_get_constraintdef(oid) from pg_constraint where conname='orders_status_check'\""

say "Transición ilegal: un pedido no entregado no se puede devolver -> 409"
OTRO=$(new_order "Devolucion invalida")
run "curl -s -w '  <- %{http_code}\n' -X POST -H '$J' -d '{\"to\":\"RETURNED\"}' $HOST/api/orders/$OTRO/transitions"
