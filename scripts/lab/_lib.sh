#!/usr/bin/env bash
# Utilidades para las demostraciones del laboratorio: imprime cada comando antes de ejecutarlo.
HOST="${HOST:-http://localhost:8080}"
J='Content-Type: application/json'
say() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
run() { printf '\033[0;33m$ %s\033[0m\n' "$*"; eval "$@"; echo; }
