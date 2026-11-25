"""Dibuja el plano del almacén con una ruta A*: S origen, D destino, * camino. Uso: plano.py HOST fx fy tx ty [diagonal]"""
import json, sys, urllib.request
host, fx, fy, tx, ty = sys.argv[1], *map(int, sys.argv[2:6])
extra = "&diagonal=true" if len(sys.argv) > 6 and sys.argv[6] == "diagonal" else ""
grid = json.load(urllib.request.urlopen(f"{host}/api/routes/grid"))
route = json.load(urllib.request.urlopen(f"{host}/api/routes?fromX={fx}&fromY={fy}&toX={tx}&toY={ty}{extra}"))
rows = [list(r) for r in grid["rows"]]
for p in route["path"]:
    rows[p["y"]][p["x"]] = "*"
rows[fy][fx], rows[ty][tx] = "S", "D"
print(f"({fx},{fy}) -> ({tx},{ty}){' [8 vecinos]' if extra else ''}: costo={route['cost']} pasos={route['steps']}")
print("    " + "".join(str(x % 10) for x in range(grid["width"])))
for y, r in enumerate(rows):
    print(f"  {y} " + "".join(r))
