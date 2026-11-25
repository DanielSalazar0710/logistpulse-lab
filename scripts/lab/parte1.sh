#!/usr/bin/env bash
# Parte 1 · Jersey / JAX-RS: nuevos endpoints y mapeo de errores de validación.
source "$(dirname "$0")/_lib.sh"

say "1.1 GET /api/robots/{id} -> 200 y 404"
run "curl -s -i $HOST/api/robots/2 | sed -n '1p;\$p'"
run "curl -s -i $HOST/api/robots/99 | sed -n '1p;\$p'"

say "1.2 DELETE /api/orders/{id}: CREATED -> 204, otro estado -> 409, inexistente -> 404"
run "curl -s -X POST -H '$J' -d '{\"customer\":\"Para borrar\",\"destX\":3,\"destY\":0}' $HOST/api/orders | grep -o '\"id\":[0-9]*'"
ID=$(curl -s "$HOST/api/orders?status=CREATED&size=1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
run "curl -s -o /dev/null -w 'DELETE /api/orders/$ID -> %{http_code}\n' -X DELETE $HOST/api/orders/$ID"
run "curl -s -o /dev/null -w 'GET    /api/orders/$ID -> %{http_code}\n' $HOST/api/orders/$ID"
run "curl -s -w '  <- %{http_code}\n' -X DELETE $HOST/api/orders/1"
run "curl -s -w '  <- %{http_code}\n' -X DELETE $HOST/api/orders/999"

say "1.3 ExceptionMapper<ConstraintViolationException> -> 400 con los campos inválidos"
run "curl -s -w '  <- %{http_code}\n' -X POST -H '$J' -d '{\"code\":\"bad\",\"customer\":\"\",\"destX\":-1,\"destY\":0}' $HOST/api/orders"
run "curl -s -w '  <- %{http_code}\n' -X POST -H '$J' -d '{}' $HOST/api/orders/2/transitions"
