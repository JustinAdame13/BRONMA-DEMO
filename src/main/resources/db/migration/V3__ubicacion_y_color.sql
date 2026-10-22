-- =====================================================================
-- V3: Ubicacion de los clientes y color de las rutas.
--
-- Para que sirve cada cosa (ver el contrato de API):
--   * GET /mapa/clientes necesita latitud/longitud de cada tiendita y el
--     color de su ruta para pintar los pines agrupados por ruta.
--   * PATCH /clientes/{id}/ubicacion guarda de donde salio la coordenada:
--     MANUAL (la oficina hace clic en el mapa) o GPS (el celular del
--     repartidor, parado afuera de la tienda).
--   * PATCH /rutas/{id}/orden guarda en que orden se visitan los clientes.
--
-- Convenciones (las mismas de V1):
--   * Coordenadas: DOUBLE PRECISION -> Double (objeto, admite NULL).
--     NULL = cliente sin ubicar, que es un caso valido y normal.
--     No se usa NUMERIC a proposito: NUMERIC se mapea a BigDecimal y la
--     API serializa todo BigDecimal como string (para no perder centavos
--     en los montos). El contrato pide las coordenadas como number, asi
--     que tienen que quedar fuera de esa regla.
--   * Enums: VARCHAR + CHECK, para mapear con @Enumerated(EnumType.STRING).
--   * Enteros: INT -> Integer.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. clientes: donde esta la tiendita y en que orden se visita
-- ---------------------------------------------------------------------
ALTER TABLE clientes
    ADD COLUMN latitud          DOUBLE PRECISION,
    ADD COLUMN longitud         DOUBLE PRECISION,
    ADD COLUMN ubicacion_fuente VARCHAR(10),
    ADD COLUMN orden_visita     INT;

ALTER TABLE clientes
    ADD CONSTRAINT ck_clientes_latitud
        CHECK (latitud IS NULL OR latitud BETWEEN -90 AND 90),

    ADD CONSTRAINT ck_clientes_longitud
        CHECK (longitud IS NULL OR longitud BETWEEN -180 AND 180),

    ADD CONSTRAINT ck_clientes_ubicacion_fuente
        CHECK (ubicacion_fuente IS NULL OR ubicacion_fuente IN ('MANUAL', 'GPS')),

    -- O el cliente esta ubicado del todo, o no lo esta. Media coordenada
    -- no sirve para nada, y una coordenada sin fuente no se puede auditar.
    ADD CONSTRAINT ck_clientes_ubicacion_completa
        CHECK (
            (latitud IS     NULL AND longitud IS     NULL AND ubicacion_fuente IS     NULL)
         OR (latitud IS NOT NULL AND longitud IS NOT NULL AND ubicacion_fuente IS NOT NULL)
        ),

    ADD CONSTRAINT ck_clientes_orden_visita
        CHECK (orden_visita IS NULL OR orden_visita > 0);

COMMENT ON COLUMN clientes.latitud          IS 'NULL = sin ubicar. Grados decimales, WGS84.';
COMMENT ON COLUMN clientes.longitud         IS 'NULL = sin ubicar. Grados decimales, WGS84.';
COMMENT ON COLUMN clientes.ubicacion_fuente IS 'MANUAL = clic en el mapa desde la oficina. GPS = celular del repartidor.';
COMMENT ON COLUMN clientes.orden_visita     IS 'Orden de visita dentro de la ruta. NULL = sin ordenar.';

-- ---------------------------------------------------------------------
-- 2. rutas: color para distinguirlas en el mapa
-- ---------------------------------------------------------------------
-- La tabla esta vacia (V2 solo carga configuracion y politica), pero el
-- DEFAULT se queda para que una ruta creada sin color siga siendo valida.
-- Gris neutro: se ve bien en el mapa y no se confunde con una ruta real.
ALTER TABLE rutas
    ADD COLUMN color VARCHAR(7) NOT NULL DEFAULT '#64748B';

ALTER TABLE rutas
    ADD CONSTRAINT ck_rutas_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$');

COMMENT ON COLUMN rutas.color IS 'Formato #RRGGBB. Lo pinta el frontend; aqui solo se valida y se guarda.';
