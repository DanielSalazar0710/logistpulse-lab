#!/usr/bin/env bash
# Parte 2 · JPA: cuenta las consultas SQL que genera GET /api/orders?size=10 (requiere spring.jpa.show-sql=true).
source "$(dirname "$0")/_lib.sh"
ETIQUETA="${1:-con @EntityGraph}"

say "Preparación: pedidos despachados con AMR distintos (para que haya varios robots que cargar)"
TOTAL=$(curl -s "$HOST/api/orders?size=1" | grep -o '"totalElements":[0-9]*' | cut -d: -f2)
for i in $(seq "$TOTAL" 11); do curl -s -o /dev/null -X POST -H "$J" -d "{\"customer\":\"Cliente $i\",\"destX\":$((i % 12)),\"destY\":2}" "$HOST/api/orders"; done
for id in 2 3 4; do curl -s -o /dev/null -X POST "$HOST/api/orders/$id/dispatch"; done
sleep 2
run "curl -s '$HOST/api/orders?size=12' | grep -o '\"code\":\"LP-[A-Z0-9]*\"[^}]*\"robot\":[^,]*' | sed 's/,\"customer.*\"robot\"/ robot/'"

say "GET /api/orders?size=10  ($ETIQUETA)"
sleep 2; DESDE=$(date -u +%Y-%m-%dT%H:%M:%SZ); sleep 1
run "curl -s -o /dev/null -w 'HTTP %{http_code}\n' '$HOST/api/orders?size=10'"
sleep 1
# se excluyen los count(robot) que dispara Prometheus cada 5 s al leer los gauges de la flota
docker compose logs --no-log-prefix --since "$DESDE" api | grep '^Hibernate:' | grep -v 'count(r1_0.id) from robot' > /tmp/sql.txt
echo "Consultas SQL ejecutadas: $(wc -l < /tmp/sql.txt)"
sed -E 's/select .{60,} from /select … from /' /tmp/sql.txt | cut -c1-160 | sed 's/^/  /'
