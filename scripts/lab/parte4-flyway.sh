#!/usr/bin/env bash
# Parte 4.1 + 4.3 (y proyecto de cierre): Flyway migra la base existente y las invariantes pasan a la base de datos.
source "$(dirname "$0")/_lib.sh"
PSQL="docker compose exec -T db psql -U logistpulse -X -q"

say "Flyway al arrancar: base existente -> baseline V1, aplica V2 y V3"
docker compose logs --no-log-prefix api | grep -E 'DbBaseline|DbMigrate' | sed -E 's/^.{0,60}(INFO|WARN)/\1/' | cut -c1-150
run "$PSQL -c 'select version, description, type, success from flyway_schema_history order by installed_rank'"

say "4.1 Ahora DELIVERED -> RETURNED funciona"
ID=$(curl -s "$HOST/api/orders?status=DELIVERED&size=1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
run "curl -s -w '  <- %{http_code}\n' -X POST -H '$J' -d '{\"to\":\"RETURNED\"}' $HOST/api/orders/$ID/transitions | grep -o '\"status\":\"[A-Z_]*\"\|<- [0-9]*'"

say "4.3 La base rechaza estados que violan las invariantes (aunque alguien salte la API)"
LIBRE=$(new_order "Invariante")
run "$PSQL -c \"update orders set status='DISPATCHED' where id=$LIBRE\" 2>&1 | head -2"
reset_fleet; A=$(new_order "Mision A"); B=$(new_order "Mision B")
curl -s -o /dev/null -X POST "$HOST/api/orders/$A/dispatch"; sleep 1
R=$(curl -s "$HOST/api/orders/$A" | grep -o '"robot":"[^"]*"' | cut -d'"' -f4)
run "$PSQL -c \"update orders set status='DISPATCHED', robot_id=(select id from robot where code='$R') where id=$B\" 2>&1 | head -2"
run "$PSQL -c 'update robot set battery=150 where id=1' 2>&1 | head -2"
