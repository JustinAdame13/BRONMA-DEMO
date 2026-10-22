-- =====================================================================
-- V4: Completa la ubicacion del cliente.
--
-- Que faltaba en V3:
--   * ubicacion_fijada_en: el contrato lo pide en ClienteDetalleDto y en
--     la respuesta del PATCH /clientes/{id}/ubicacion. Lo pone el backend
--     con la hora del servidor cada vez que se fija una ubicacion, para
--     saber que tan vieja es la coordenada.
--   * GEOCODIFICADA: el contrato la reserva como tercera fuente (sacar la
--     coordenada de la direccion escrita). El front no la manda todavia,
--     pero no cabia en VARCHAR(10) ni la permitia el CHECK.
--
-- No se edita V3 porque ya esta aplicada: Flyway valida su checksum y
-- fallaria al arrancar en cualquier base que ya la tenga.
-- =====================================================================

ALTER TABLE clientes
    ADD COLUMN ubicacion_fijada_en TIMESTAMPTZ;

COMMENT ON COLUMN clientes.ubicacion_fijada_en IS 'Cuando se fijo la coordenada. Lo pone el backend, no el cliente.';

-- 'GEOCODIFICADA' son 13 caracteres y la columna admitia 10.
ALTER TABLE clientes
    ALTER COLUMN ubicacion_fuente TYPE VARCHAR(14);

ALTER TABLE clientes
    DROP CONSTRAINT ck_clientes_ubicacion_fuente;

ALTER TABLE clientes
    ADD CONSTRAINT ck_clientes_ubicacion_fuente
        CHECK (ubicacion_fuente IS NULL
            OR ubicacion_fuente IN ('MANUAL', 'GPS', 'GEOCODIFICADA'));

-- Se rehace el "todo o nada" para que la fecha entre al grupo: una
-- coordenada sin fecha dejaria de poder auditarse por antiguedad.
ALTER TABLE clientes
    DROP CONSTRAINT ck_clientes_ubicacion_completa;

ALTER TABLE clientes
    ADD CONSTRAINT ck_clientes_ubicacion_completa
        CHECK (
            (    latitud IS     NULL AND longitud IS     NULL
             AND ubicacion_fuente IS NULL AND ubicacion_fijada_en IS NULL)
         OR (    latitud IS NOT NULL AND longitud IS NOT NULL
             AND ubicacion_fuente IS NOT NULL AND ubicacion_fijada_en IS NOT NULL)
        );
