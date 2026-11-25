-- Mismas invariantes que postgresql/V3, salvo I3: H2 no tiene índices únicos parciales,
-- así que en modo lite "un AMR con un solo pedido en misión" lo cuida solo el @Version de Robot.
ALTER TABLE orders ADD CONSTRAINT ck_orders_mision_con_robot
    CHECK (status NOT IN ('DISPATCHED', 'IN_TRANSIT') OR robot_id IS NOT NULL);
ALTER TABLE orders ADD CONSTRAINT ck_orders_created_sin_robot
    CHECK (status <> 'CREATED' OR robot_id IS NULL);
ALTER TABLE robot ADD CONSTRAINT ck_robot_bateria CHECK (battery BETWEEN 0 AND 100);
ALTER TABLE orders ADD CONSTRAINT ck_orders_destino CHECK (destx >= 0 AND desty >= 0);
