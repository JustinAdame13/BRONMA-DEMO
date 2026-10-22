-- =====================================================================
-- V6: acentos en los mensajes de WhatsApp.
--
-- Las plantillas de V2 se escribieron sin acentos ("proxima", "vencio",
-- "comuniquese"...). Un mensaje de cobranza con faltas de ortografia se ve
-- poco profesional frente al cliente final.
--
-- Se corrigen por frase (REPLACE) y no reescribiendo la plantilla completa:
-- si alguien ya edito una plantilla con PUT /recordatorios/politicas/{id},
-- su texto se respeta y solo cambian las palabras sin acento.
-- Tambien se corrigen los mensajes ya guardados (historial y datos de demo).
-- =====================================================================

UPDATE politica_recordatorios
SET plantilla =
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(plantilla,
        'en su proxima visita', 'en su próxima visita'),
        ' vencio el ',          ' venció el '),
        'Por favor comuniquese', 'Por favor comuníquese'),
        'Comuniquese con',      'Comuníquese con'),
        ' dias de atraso',      ' días de atraso'),
        'Su credito podria',    'Su crédito podría'),
        'comuniquese con',      'comuníquese con');

UPDATE recordatorios
SET mensaje =
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(mensaje,
        'en su proxima visita', 'en su próxima visita'),
        ' vencio el ',          ' venció el '),
        'Por favor comuniquese', 'Por favor comuníquese'),
        'Comuniquese con',      'Comuníquese con'),
        ' dias de atraso',      ' días de atraso'),
        'Su credito podria',    'Su crédito podría'),
        'comuniquese con',      'comuníquese con')
WHERE mensaje IS NOT NULL;
