-- V5 (Parte 8.5): momento del despacho, para el SLO "tiempo máximo en DISPATCHED".
ALTER TABLE orders ADD COLUMN dispatched_at TIMESTAMP(6) WITH TIME ZONE;
