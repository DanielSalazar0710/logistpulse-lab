-- V2 (Parte 4.1): nuevo estado RETURNED.
-- ddl-auto=update nunca modifica un CHECK existente, por eso el UPDATE a RETURNED fallaba con
-- "violates check constraint orders_status_check". La regla de la base debe evolucionar junto con el enum.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('CREATED', 'DISPATCHED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED', 'RETURNED'));
