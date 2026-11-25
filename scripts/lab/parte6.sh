#!/usr/bin/env bash
# Parte 6 · RestClient y resiliencia: transportista lento, caído, y conexión de BD retenida durante la llamada HTTP.
source "$(dirname "$0")/_lib.sh"
ETIQUETA="${1:-antes del refactor}"
PSQL="docker compose exec -T db psql -U logistpulse -X -q -t"
SUF=$(date +%H%M%S)
reset_fleet

say "6.1 Pedido LP-SLOW (WireMock tarda 5 s, CarrierClient corta a los 2 s)  [$ETIQUETA]"
curl -s -o /dev/null -X POST -H "$J" -d "{\"code\":\"LP-SLOW$SUF\",\"customer\":\"Prueba timeout\",\"destX\":5,\"destY\":3}" "$HOST/api/orders"
ID=$(curl -s "$HOST/api/orders?customer=timeout&sort=id,desc&size=1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
DESDE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
curl -s -o /tmp/r.json -w 'POST /api/orders/'$ID'/dispatch -> HTTP %{http_code} en %{time_total} s\n' -X POST "$HOST/api/orders/$ID/dispatch" &
sleep 1
echo "Durante la llamada al transportista, transacciones abiertas en Postgres:"
TX=$($PSQL -c "select '   ' || state || ' | ' || left(query, 70) from pg_stat_activity where datname='logistpulse' and state like 'idle in transaction%'" | sed '/^$/d')
echo "${TX:-   (ninguna)}"
wait
grep -o '"etaMinutes":[0-9]*' /tmp/r.json
echo "Ruta (0,0)->(5,3) cuesta $(curl -s "$HOST/api/routes?toX=5&toY=3" | grep -o '"cost":[0-9]*' | cut -d: -f2) -> ETA local = costo x 2"
sleep 1; docker compose logs --no-log-prefix --since "$DESDE" api | grep -E 'Transportista|reintento' | sed -E 's/^.*(WARN|INFO).*(CarrierClient|OrderService) *: /\1 \2: /' | cut -c1-200

say "6.2 Transportista apagado"
docker compose stop carrier-mock 2>&1 | tail -1
ID2=$(new_order "Sin transportista" 10 5)
DESDE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
run "curl -s -w '  <- %{http_code} en %{time_total} s\n' -X POST $HOST/api/orders/$ID2/dispatch | grep -o '\"etaMinutes\":[0-9]*\|<- .*'"
sleep 1; docker compose logs --no-log-prefix --since "$DESDE" api | grep -E 'Transportista|reintento' | sed -E 's/^.*(WARN|INFO).*(CarrierClient|OrderService) *: /\1 \2: /' | cut -c1-200
docker compose start carrier-mock 2>&1 | tail -1
