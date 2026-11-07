CREATE TABLE planes (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL,
    precio_mensual NUMERIC(14, 2) NOT NULL,
    max_productos INT NOT NULL,
    max_empleados INT NOT NULL
);

CREATE TABLE comercios (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(160) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nombre VARCHAR(160) NOT NULL,
    rol VARCHAR(30) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE suscripciones (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL UNIQUE REFERENCES comercios (id),
    plan_id BIGINT NOT NULL REFERENCES planes (id),
    estado VARCHAR(20) NOT NULL,
    vence_en DATE NOT NULL
);

CREATE TABLE vidrieras (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL UNIQUE REFERENCES comercios (id),
    titulo VARCHAR(160) NOT NULL,
    descripcion VARCHAR(2000),
    color VARCHAR(20) NOT NULL DEFAULT '#6d28d9',
    publicada BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE clientes (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    nombre VARCHAR(160) NOT NULL,
    telefono VARCHAR(40),
    email VARCHAR(180),
    observaciones VARCHAR(2000),
    saldo NUMERIC(14, 2) NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_clientes_comercio ON clientes (comercio_id);

CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    nombre VARCHAR(120) NOT NULL,
    CONSTRAINT uq_categoria_comercio UNIQUE (comercio_id, nombre)
);

CREATE TABLE productos (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    categoria_id BIGINT REFERENCES categorias (id),
    nombre VARCHAR(180) NOT NULL,
    sku VARCHAR(80),
    costo NUMERIC(14, 2) NOT NULL DEFAULT 0,
    precio NUMERIC(14, 2) NOT NULL,
    stock NUMERIC(14, 3) NOT NULL DEFAULT 0,
    stock_minimo NUMERIC(14, 3) NOT NULL DEFAULT 0,
    publicado BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    usa_variantes BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_productos_comercio ON productos (comercio_id);
CREATE UNIQUE INDEX uq_producto_sku ON productos (comercio_id, sku) WHERE sku IS NOT NULL;

CREATE TABLE variantes (
    id BIGSERIAL PRIMARY KEY,
    producto_id BIGINT NOT NULL REFERENCES productos (id),
    nombre VARCHAR(120) NOT NULL,
    sku VARCHAR(80),
    precio NUMERIC(14, 2),
    stock NUMERIC(14, 3) NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE imagenes_producto (
    id BIGSERIAL PRIMARY KEY,
    producto_id BIGINT NOT NULL REFERENCES productos (id),
    archivo VARCHAR(200) NOT NULL,
    orden INT NOT NULL DEFAULT 0
);

CREATE TABLE promociones (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    producto_id BIGINT NOT NULL REFERENCES productos (id),
    precio_promo NUMERIC(14, 2) NOT NULL,
    desde DATE,
    hasta DATE,
    activa BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE cajas (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    abierta_por BIGINT NOT NULL REFERENCES usuarios (id),
    abierta_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cerrada_en TIMESTAMP,
    monto_inicial NUMERIC(14, 2) NOT NULL,
    monto_contado NUMERIC(14, 2),
    estado VARCHAR(20) NOT NULL
);
CREATE UNIQUE INDEX uq_caja_abierta ON cajas (comercio_id) WHERE estado = 'ABIERTA';

CREATE TABLE pedidos (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    cliente_id BIGINT REFERENCES clientes (id),
    estado VARCHAR(20) NOT NULL,
    total NUMERIC(14, 2) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pedidos_comercio ON pedidos (comercio_id);

CREATE TABLE detalle_pedido (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedidos (id),
    producto_id BIGINT NOT NULL REFERENCES productos (id),
    variante_id BIGINT REFERENCES variantes (id),
    cantidad NUMERIC(14, 3) NOT NULL,
    precio_unitario NUMERIC(14, 2) NOT NULL,
    subtotal NUMERIC(14, 2) NOT NULL
);

CREATE TABLE ventas (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    cliente_id BIGINT REFERENCES clientes (id),
    usuario_id BIGINT NOT NULL REFERENCES usuarios (id),
    caja_id BIGINT REFERENCES cajas (id),
    pedido_id BIGINT REFERENCES pedidos (id),
    estado VARCHAR(20) NOT NULL,
    total NUMERIC(14, 2) NOT NULL,
    costo_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
    creada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    anulada_en TIMESTAMP
);
CREATE INDEX idx_ventas_comercio_fecha ON ventas (comercio_id, creada_en);

CREATE TABLE detalle_venta (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES ventas (id),
    producto_id BIGINT NOT NULL REFERENCES productos (id),
    variante_id BIGINT REFERENCES variantes (id),
    cantidad NUMERIC(14, 3) NOT NULL,
    precio_unitario NUMERIC(14, 2) NOT NULL,
    costo_unitario NUMERIC(14, 2) NOT NULL,
    subtotal NUMERIC(14, 2) NOT NULL
);

CREATE TABLE pagos (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES ventas (id),
    medio VARCHAR(30) NOT NULL,
    monto NUMERIC(14, 2) NOT NULL
);

CREATE TABLE movimientos_stock (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    producto_id BIGINT NOT NULL REFERENCES productos (id),
    variante_id BIGINT REFERENCES variantes (id),
    venta_id BIGINT REFERENCES ventas (id),
    tipo VARCHAR(20) NOT NULL,
    cantidad NUMERIC(14, 3) NOT NULL,
    motivo VARCHAR(200) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE movimientos_caja (
    id BIGSERIAL PRIMARY KEY,
    comercio_id BIGINT NOT NULL REFERENCES comercios (id),
    caja_id BIGINT NOT NULL REFERENCES cajas (id),
    venta_id BIGINT REFERENCES ventas (id),
    tipo VARCHAR(20) NOT NULL,
    concepto VARCHAR(200) NOT NULL,
    monto NUMERIC(14, 2) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO planes (codigo, nombre, precio_mensual, max_productos, max_empleados)
VALUES ('INICIO', 'Inicio', 0, 200, 5);
