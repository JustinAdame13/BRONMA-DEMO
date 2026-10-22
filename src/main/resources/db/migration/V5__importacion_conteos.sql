-- =====================================================================
-- V5: Conteos que faltaban en la bitacora de importaciones.
--
-- V1 solo guardaba creadas / actualizadas / errores. Para que la oficina
-- entienda el resultado de subir un archivo hacen falta tambien:
--   * sin_cambios:     notas que ya estaban igual (re-subir el mismo archivo
--                      debe dar todo aqui: la importacion es idempotente).
--   * omitidas:        filas de venta de CONTADO (no generan cuenta por cobrar).
--   * clientes_nuevos: tienditas que no existian y se dieron de alta.
--   * rutas_nuevas:    rutas que no existian y se dieron de alta (gris, para
--                      que la oficina les ponga nombre y color despues).
--
-- Nota de unidades: filas_total cuenta FILAS del archivo; creadas,
-- actualizadas y sin_cambios cuentan NOTAS (un folio puede venir en varias
-- filas si el sistema exporta una fila por producto).
-- =====================================================================

ALTER TABLE importaciones
    ADD COLUMN sin_cambios     INT NOT NULL DEFAULT 0,
    ADD COLUMN omitidas        INT NOT NULL DEFAULT 0,
    ADD COLUMN clientes_nuevos INT NOT NULL DEFAULT 0,
    ADD COLUMN rutas_nuevas    INT NOT NULL DEFAULT 0;

ALTER TABLE importaciones
    ADD CONSTRAINT ck_importaciones_conteos_v5
        CHECK (sin_cambios >= 0 AND omitidas >= 0 AND clientes_nuevos >= 0 AND rutas_nuevas >= 0);

CREATE INDEX ix_importaciones_importado_en ON importaciones (importado_en DESC);
