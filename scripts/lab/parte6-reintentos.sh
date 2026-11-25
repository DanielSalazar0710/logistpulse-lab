#!/usr/bin/env bash
# Parte 6.4 (reto) · Reintentos con backoff solo para 5xx (WireMock simula cada caso por el prefijo del código).
source "$(dirname "$0")/_lib.sh"
WM=http://localhost:9091
SUF=$(date +%M%S)
reset_fleet
curl -s -o /dev/null -X POST "$WM/__admin/scenarios/reset"

caso() {  # caso CODIGO DESCRIPCION
  curl -s -o /dev/null -X POST -H "$J" -d "{\"code\":\"$1\",\"customer\":\"Reintentos\",\"destX\":5,\"destY\":3}" "$HOST/api/orders"
  local id=$(curl -s "$HOST/api/orders?customer=reintentos&sort=id,desc&size=1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
  local desde=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  say "$2"
  curl -s -o /tmp/r.json -w "despacho $1 -> HTTP %{http_code} en %{time_total} s; " -X POST "$HOST/api/orders/$id/dispatch"
  grep -o '"etaMinutes":[0-9]*' /tmp/r.json
  echo "llamadas que recibió el transportista: $(curl -s -X POST -H "$J" -d "{\"method\":\"GET\",\"urlPath\":\"/carrier/eta\",\"queryParameters\":{\"code\":{\"equalTo\":\"$1\"}}}" "$WM/__admin/requests/count" | grep -o '[0-9]*')"
  sleep 1; docker compose logs --no-log-prefix --since "$desde" api | grep -E 'Transportista' | sed -E 's/^.*(WARN|INFO).*CarrierClient *: /\1 CarrierClient: /' | cut -c1-160
  docker compose exec -T api true 2>/dev/null; reset_fleet
}
caso "LP-FLAKY$SUF" "503 transitorio: el 1.er reintento (200 ms) obtiene el ETA real del transportista (31)"
caso "LP-FAIL$SUF"  "503 permanente: 1 intento + 2 reintentos (200 ms, 400 ms) y luego ETA local"
caso "LP-BAD$SUF"   "404 del transportista: error del cliente, NO se reintenta"
say "Métricas"
curl -s "$HOST/actuator/prometheus" | grep -E '^logistpulse_carrier_(retries|fallback)'
