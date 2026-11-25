#!/usr/bin/env bash
# Parte 4.2 · Concurrencia: despachos simultáneos y bloqueo optimista (@Version + OptimisticLockMapper).
source "$(dirname "$0")/_lib.sh"
reset_fleet

say "A) Dos despachos simultáneos del MISMO pedido (5 intentos)"
for n in 1 2 3 4 5; do
  id=$(new_order "Carrera $n")
  curl -s -w ' <- %{http_code}\n' -X POST "$HOST/api/orders/$id/dispatch" > /tmp/a.txt &
  curl -s -w ' <- %{http_code}\n' -X POST "$HOST/api/orders/$id/dispatch" > /tmp/b.txt &
  wait
  echo "pedido $id:"
  for f in /tmp/a.txt /tmp/b.txt; do sed -E 's/.*"robot":"([^"]*)".*(<- [0-9]+)/   ganó  -> robot \1 \2/; s/^\{.*"error":"([^"]*)".*(<- [0-9]+)/   perdió -> \1 \2/' $f; done
  sleep 1; reset_fleet
done

say "B) Dos pedidos DISTINTOS despachados a la vez: ¿pueden recibir el mismo AMR? (5 intentos)"
for n in 1 2 3 4 5; do
  a=$(new_order "Paralelo A$n"); b=$(new_order "Paralelo B$n")
  curl -s -w ' <- %{http_code}\n' -X POST "$HOST/api/orders/$a/dispatch" > /tmp/a.txt &
  curl -s -w ' <- %{http_code}\n' -X POST "$HOST/api/orders/$b/dispatch" > /tmp/b.txt &
  wait
  echo "pedidos $a y $b:"
  for f in /tmp/a.txt /tmp/b.txt; do sed -E 's/.*"id":([0-9]+).*"robot":"([^"]*)".*(<- [0-9]+)/   pedido \1 -> robot \2 \3/; s/^\{.*"error":"([^"]*)".*(<- [0-9]+)/   pedido rechazado -> \1 \2/' $f; done
  sleep 1; reset_fleet
done

say "Estado final de la flota (ningún AMR quedó con dos pedidos)"
curl -s "$HOST/api/robots" | python -c "import json,sys; [print('  ', r['code'], r['status'], str(r['battery'])+'%') for r in sorted(json.load(sys.stdin), key=lambda r: r['id'])]"
