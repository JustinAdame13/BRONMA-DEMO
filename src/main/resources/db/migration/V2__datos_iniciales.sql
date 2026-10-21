-- =====================================================================
-- V2: Datos minimos para que la app arranque usable.
-- Despues se cambian con la API (configuracion, politica de recordatorios).
-- =====================================================================

-- Fila unica de configuracion
INSERT INTO configuracion (
    id, nombre_empresa, zona_horaria,
    plazo_credito_default_dias, limite_credito_default,
    whatsapp_habilitado, hora_envio_recordatorios,
    proveedor_ia, modelo_ia
) VALUES (
    1, 'Distribuidora de Lacteos', 'America/Mexico_City',
    7, 0,
    FALSE, '09:00',
    'NINGUNO', NULL
);

-- Politica por defecto: 3 dias antes, el dia que vence, +3, +7 y +15.
-- {saldo} lo formatea el servicio con signo ("$1,250.00"). No se escribe
-- el signo de pesos pegado a la llave porque Flyway lo toma como placeholder.
INSERT INTO politica_recordatorios (dias_offset, plantilla) VALUES
    (-3, 'Hola {cliente}, le recordamos que su nota {folio} por {saldo} vence el {vencimiento}. Gracias por su preferencia.'),
    ( 0, 'Hola {cliente}, su nota {folio} por {saldo} vence hoy. Puede pagar al repartidor en su proxima visita o por transferencia.'),
    ( 3, 'Hola {cliente}, su nota {folio} por {saldo} vencio el {vencimiento}. Le agradeceremos ponerse al corriente.'),
    ( 7, 'Hola {cliente}, su nota {folio} tiene un saldo pendiente de {saldo} con una semana de atraso. Por favor comuniquese con nosotros.'),
    (15, 'Hola {cliente}, su nota {folio} por {saldo} tiene 15 dias de atraso. Su credito podria suspenderse. Comuniquese con nosotros.');
