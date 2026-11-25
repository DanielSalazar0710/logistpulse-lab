#!/usr/bin/env bash
# Parte 2.3 (índice) y Parte 3 · Spring Data: filtro, orden con lista blanca, paginación y enlaces HATEOAS.
source "$(dirname "$0")/_lib.sh"
js() { python -c "import json,sys; d=json.load(sys.stdin); $1"; }

say "2.3 Índice sobre orders.status (creado por @Table(indexes=...))"
run "docker compose exec -T db psql -U logistpulse -c '\d orders' | sed -n '/Indexes/,\$p'"

say "3.1 ?customer= (contiene, sin distinguir mayúsculas)"
run "curl -s '$HOST/api/orders?customer=sUpErMaXi' | js \"print(d['totalElements'], [o['customer'] for o in d['content']])\""

say "3.2 ?sort=createdAt,desc + paginación: los enlaces conservan filtro y orden"
run "curl -s '$HOST/api/orders?customer=cliente&sort=createdAt,desc&size=3&page=1' | js \"[print(o['id'], o['customer'], o['createdAt']) for o in d['content']]; [print(' ', k, '->', v) for k,v in d['links'].items()]\""
run "curl -s -w '  <- %{http_code}\n' '$HOST/api/orders?sort=version,desc'"
run "curl -s -w '  <- %{http_code}\n' '$HOST/api/orders?sort=id,arriba'"
run "curl -s -w '  <- %{http_code}\n' '$HOST/api/orders?status=FOO'"
run "curl -s '$HOST/api/orders?size=500' | js \"print('size pedido=500 -> size aplicado =', d['size'])\""

say "3.3 Enlaces HATEOAS según el estado de cada pedido"
for st in CREATED DISPATCHED IN_TRANSIT DELIVERED CANCELLED RETURNED; do
  curl -s "$HOST/api/orders?status=$st&size=1" | js "
c=d.get('content') or []
print('$st'.ljust(11), '->', ', '.join(k for k in c[0]['links'] if k not in ('self','collection')) or '(solo self y collection)') if c else print('$st'.ljust(11), '-> (sin pedidos en este estado)')" 2>/dev/null
done
