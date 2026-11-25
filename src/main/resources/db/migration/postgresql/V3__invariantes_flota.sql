-- V3 (Parte 4.3 + proyecto de cierre): invariantes que antes solo garantizaba el código Java.

-- I1. Un pedido en misión (DISPATCHED / IN_TRANSIT) siempre tiene AMR asignado.
ALTER TABLE orders ADD CONSTRAINT ck_orders_mision_con_robot
    CHECK (status NOT IN ('DISPATCHED', 'IN_TRANSIT') OR robot_id IS NOT NULL);

-- I2. Un pedido recién creado todavía no tiene AMR.
ALTER TABLE orders ADD CONSTRAINT ck_orders_created_sin_robot
    CHECK (status <> 'CREATED' OR robot_id IS NULL);

-- I3. Un AMR tiene como máximo UN pedido en misión (índice único parcial, propio de PostgreSQL).
CREATE UNIQUE INDEX ux_orders_robot_en_mision
    ON orders (robot_id)
    WHERE status IN ('DISPATCHED', 'IN_TRANSIT');

-- I4. Batería en rango y destino con coordenadas no negativas.
ALTER TABLE robot ADD CONSTRAINT ck_robot_bateria CHECK (battery BETWEEN 0 AND 100);
ALTER TABLE orders ADD CONSTRAINT ck_orders_destino CHECK (destx >= 0 AND desty >= 0);
