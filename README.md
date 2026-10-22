# Cobranza Inteligente — Distribuidora de Lácteos — Backend

API REST con Spring Boot 4 + PostgreSQL + Flyway. Base: `http://localhost:8080/api/v1`.

## Cómo correrlo

1. Crea **una** base en PostgreSQL (por ejemplo `bronma`). Al arrancar, Flyway la migra sola.
2. Configura las **variables de entorno**. No hay archivo `.env`: `application.properties` las lee directo.
   En IntelliJ: *Run → Edit Configurations → CobranzaApplication → Environment variables*.

   | Variable | Obligatoria | Por defecto |
   |---|---|---|
   | `DB_USERNAME` | **Sí** | — |
   | `DB_PASSWORD` | **Sí** | — |
   | `DB_URL` | No | `jdbc:postgresql://localhost:5432/bronma` |
   | `DB_POOL_MAXIMO` | No | `5` |
   | `PORT` | No | `8080` |
   | `CORS_ORIGINS` | No | `http://localhost:5173,http://localhost:3000` |
   | `IA_ANTHROPIC_API_KEY` / `IA_OPENAI_API_KEY` / `IA_GEMINI_API_KEY` | No | vacía (IA apagada) |
   | `IA_ANTHROPIC_MODELO` / `IA_OPENAI_MODELO` / `IA_GEMINI_MODELO` | No | el del código |
   | `IA_TIMEOUT` | No | `90` (segundos) |
   | `SHOW_SQL` | No | `false` |

   **Deploy:** Render (Docker, con el `Dockerfile` de la raíz) + Neon. En Neon usa el host **directo** (sin `-pooler`)
   y la URL en formato JDBC: `jdbc:postgresql://<host>/<base>?sslmode=require`.
3. Corre `CobranzaApplication`. Flyway crea el esquema (V1) y los datos iniciales (V2: configuración y política de recordatorios).

## Modelo de datos

| Tabla | Para qué |
|---|---|
| `configuracion` | Fila única: empresa, zona horaria, plazo/límite de crédito por defecto, WhatsApp, IA |
| `usuarios` | Oficina, repartidores y admin |
| `rutas` | Rutas de reparto (`codigo_origen` = clave en el sistema de ventas) |
| `clientes` | Tienditas y abarrotes (`codigo_origen` único, teléfono E.164, consentimiento WhatsApp) |
| `importaciones` | Bitácora de cada Excel/CSV importado |
| `notas` | Cuentas por cobrar (`folio_origen` único; el estado siempre cuadra con el saldo; "vencida" se calcula) |
| `pagos` | Lo que entrega el cliente (`uuid_dispositivo` único para reintentos desde el celular) |
| `aplicaciones_pago` | Cuánto de cada pago se abona a cada nota (un pago puede cubrir varias) |
| `politica_recordatorios` | Cuándo avisar respecto al vencimiento (-3, 0, +3, +7, +15 por defecto) |
| `recordatorios` | Cada mensaje: pendiente, enviado, fallido o cancelado (uno por nota y regla) |
| `evaluaciones_riesgo` | Resultado de Ayax por cliente, día y modelo (BASE o IA) |

Dashboard, antigüedad de cartera, lista de cobro por ruta y monto recuperado por recordatorios son **consultas**, no tablas.

## Módulos

| Paquete | Estado |
|---|---|
| `configuracion` | `GET/PUT /configuracion`, `PATCH /configuracion/ia`, `GET /configuracion/ia/proveedores`, `GET /configuracion/ia/estado` |
| `ia`, `Infraestructura`, `Interfaces` | Clientes de Anthropic / OpenAI / Gemini intercambiables en caliente |
| `usuario` | `GET /usuarios?rol=` |
| `ruta` | `GET /rutas?activo=`, `POST /rutas`, `PUT /rutas/{id}` (baja = `activo: false`), `PATCH /rutas/{id}/orden` |
| `cliente` | `GET /clientes` (filtros `rutaId`, `estadoCredito`, `q`, `sinUbicacion`, `activo`; paginado), `GET /clientes/{id}`, `POST /clientes`, `PUT /clientes/{id}` (baja = `activo: false`), `PATCH /clientes/{id}/ubicacion` |
| `importacion` | `POST /importaciones` (multipart, campo `archivo`; `?simular=true`, `?usuarioId=`), `GET /importaciones`, `GET /importaciones/{id}`, `GET /importaciones/columnas` |
| `nota` | `GET /notas` (filtros `clienteId`, `rutaId`, `estado`, `vivas`, `vencidas`, `q`, `desde`, `hasta`; paginado), `GET /notas/{id}` (con abonos), `POST /notas` (captura manual), `PATCH /notas/{id}/cancelar` (sus abonos pasan a saldo a favor) |
| `pago` | `POST /pagos`, `GET /pagos` (filtros `clienteId`, `rutaId`, `metodo`, `origen`, `registradoPorId`, `desde`, `hasta`; paginado), `GET /pagos/{id}`, `DELETE /pagos/{id}` (revertir) |
| `riesgo` (Ayax) | `POST /riesgo/evaluar` (`?clienteId=`, `?usarIa=`), `GET /riesgo` (vigente por cliente; filtros `nivel`, `rutaId`), `GET /riesgo/clientes/{id}` (historial). Corre solo a las 6:15 am |
| `cobro` | `GET /lista-cobro?rutaId=` o `?repartidorId=` (`orden=PRIORIDAD\|VISITA`, `diasPorVencer=3`) |
| `dashboard` | `GET /dashboard` (`desde`, `hasta`, `top`) |
| `mapa` | `GET /mapa/clientes?rutaId=` (contrato B4) |
| `recordatorio` | `GET /recordatorios` (`estado`, `fecha`, `clienteId`, `notaId`), `POST /recordatorios/ejecutar`, `GET/POST /recordatorios/politicas`, `PUT /recordatorios/politicas/{id}` |
| `demo` | Solo con perfil `demo`: carga inicial automática y `POST /demo/reiniciar?confirmar=SI` |

`GET /clientes/{id}` trae `notasAbiertas` y `riesgo` (evaluación vigente de Ayax).

### Variables nuevas

| Variable | Por defecto | Para qué |
|---|---|---|
| `WHATSAPP_PROVEEDOR` | `consola` | `consola` (solo log) o `twilio` |
| `TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` | vacías | Obligatorias con `twilio` |
| `TWILIO_WHATSAPP_FROM` | `whatsapp:+14155238886` | Número del sandbox |
| `WHATSAPP_MX_AGREGAR_1` | `false` | Si no llegan a celulares MX, probar `true` (+52 1…) |
| `SPRING_PROFILES_ACTIVE` | — | `demo` para cargar datos de demo |
| `DEMO_TELEFONOS` | vacía | Teléfonos E.164 del equipo (separados por coma) que la demo asigna a los primeros clientes |

## Importación de ventas

Sube el Excel (`.xlsx`/`.xls`, primera hoja) o CSV que exporta el sistema de ventas. Es **idempotente**: el folio de la
nota y la clave del cliente son las llaves, así que subir el mismo archivo dos veces no duplica nada.

- **Columnas obligatorias:** folio, fecha, clave del cliente y monto. Opcionales: nombre del cliente, ruta, vencimiento,
  plazo, tipo de venta, teléfono y dirección. Cada una acepta varios nombres de encabezado (`GET /importaciones/columnas`);
  para el formato real del cliente solo hay que agregar alias en `ColumnaImportacion`.
- El encabezado se busca en las primeras 15 filas (aguanta reportes con título). CSV con `,` `;` o tabulador, en UTF-8 o
  windows-1252. Fechas día/mes/año.
- Una fila mala no tumba el archivo: va a `detalleErrores` con su número de fila y se importa lo demás.
- Varias filas con el mismo folio (una por producto) se suman en una nota. Las de **contado** se omiten.
- Sin vencimiento en el archivo: fecha + plazo (de la fila, del cliente o el de configuración).
- Una nota con abonos no cambia de monto ni de cliente desde la importación (sería error de esa fila). Las canceladas no se tocan.
- Clientes y rutas nuevos se dan de alta (ruta en gris, nombre "Ruta X"). Al cliente nuevo no se le activa WhatsApp.
- `?simular=true` hace todo y no guarda nada: sirve para revisar errores antes.
- Límite: 10 MB y 20,000 filas. Ejemplo para probar: `ejemplos/ventas-ejemplo.csv`.

## Pagos

`POST /api/v1/pagos` con `{ clienteId, monto, metodo, origen }` y opcionales `fechaPago`, `registradoPorId`,
`referencia`, `comentario`, `uuidDispositivo` y `aplicaciones: [{ notaId, monto }]`.

- Sin `aplicaciones` el pago se reparte solo: primero la nota que vence antes. Con `aplicaciones`, la suma no puede
  pasar del monto; si es menor, lo que sobra se reparte solo entre las demás notas. Se aceptan pagos parciales; la nota queda `PARCIAL` o `PAGADA`.
- **Saldo a favor:** lo que sobra de un pago (pago mayor a la deuda, anticipo de un cliente que no debe nada, o
  aplicaciones que no suman todo el monto) queda a favor del cliente. No hay tabla aparte: es la parte de cada pago que
  no está aplicada a ninguna nota, así se sabe de qué pago salió cada peso. Se usa **sola** en cuanto el cliente tiene
  una nota nueva (importación o captura manual) o una nota vuelve a deber (al revertir un pago), primero el pago más
  viejo. Cancelar una nota con abonos regresa esos abonos como saldo a favor. Aparece como `saldoAFavor` en clientes,
  pagos y dashboard.
- `origen = RUTA` exige `registradoPorId`. Mismo `uuidDispositivo` = reintento del celular: regresa el pago existente con 200.
- Las notas del cliente se bloquean (`SELECT ... FOR UPDATE`) mientras se registra: dos cobros simultáneos no se pisan.
- Nota que queda `PAGADA` → sus recordatorios `PENDIENTE` pasan a `CANCELADO`. `DELETE /pagos/{id}` revierte el pago
  (incluido el saldo a favor que haya dejado) y reactiva los recordatorios cancelados que aún no llegan a su fecha.
- "Vencida" y los rangos de fechas usan el *hoy* de la zona de configuración, no el del servidor (UTC).

## Ayax (riesgo de impago)

Modelo **BASE** (reglas en `MotorAyax`, sin IA), score 0–100:

| Señal | Puntos |
|---|---|
| Días promedio de retraso (lo pagado + lo vencido, ponderado por monto; 30+ = máximo) | hasta 35 |
| Atraso actual de la nota más vieja (60+ días = máximo) | hasta 25 |
| Uso del crédito (0 hasta 50 %; máximo al 120 %) | hasta 20 |
| Deuda creciendo en 30 días (+100 % = máximo) | hasta 10 |
| Dejó de comprar pero debe (más del doble de su frecuencia sin comprar) | 10 |

Nivel: BAJO < 35 ≤ MEDIO < 65 ≤ ALTO. Acción: ALTO → `SUSPENDER_CREDITO` (si pasa de 30 días o del límite) o
`REDUCIR_CREDITO`; con vencido → `COBRAR_PROXIMA_VISITA`; MEDIO sin vencido → `OFRECER_DESCUENTO_PRONTO_PAGO`.
Ayax **recomienda**, no cambia el crédito solo. Si hay IA configurada, los 100 clientes con deuda más riesgosos se mandan
también a la IA (lotes de 20) y su opinión se guarda como modelo `IA`; si falla, queda BASE. La evaluación "vigente" es la
del día más reciente, y ese día la de IA si existe.

## Lista de cobro y prioridad

Prioridad 0–100 (`PrioridadCobro`, la misma en lista de cobro, mapa y dashboard): días de atraso (hasta 35), monto
exigible en escala logarítmica (hasta 30, $10,000 = máximo), score de Ayax (hasta 25) y "vence en los próximos días" (10).
La lista incluye a quien tiene algo vencido o que vence hoy (`montoACobrar`) o en los próximos `diasPorVencer` días.

## Dashboard

Foto de hoy: cartera total/vencida/% vencido, antigüedad (`AL_CORRIENTE`, `VENCIDO_1_30`, `VENCIDO_31_60`,
`VENCIDO_MAS_60`), top deudores, cartera por ruta y clientes por nivel de riesgo. Del periodo: cobrado, pagos,
**recuperado por recordatorios** (abonos a notas que recibieron un WhatsApp en los 7 días anteriores al pago),
recordatorios enviados y días promedio de cobro (venta → pago, ponderado por monto).

## Recordatorios por WhatsApp

Cada corrida (sola a la `hora_envio_recordatorios` de configuración, revisando cada 10 min; o con
`POST /recordatorios/ejecutar`): genera los `PENDIENTE` (nota viva × regla activa, sin duplicar), cancela lo que ya no
aplica (nota pagada/cancelada, más de 2 días de retraso, 3 intentos fallidos) y, si `whatsapp_habilitado = true`, manda
los de hoy. Respeta `acepta_whatsapp`. Guarda el mensaje, el teléfono, el SID o el error. Para el sandbox de Twilio cada
celular debe unirse primero mandando `join <palabra>` al número del sandbox. En Render gratis el servidor se duerme y el
reloj no corre: para la demo usar el endpoint.

## Datos de demo

Con `SPRING_PROFILES_ACTIVE=demo` y la base vacía, al arrancar se cargan 3 rutas con color, 40 tienditas de Torreón
(`src/main/resources/demo/tienditas.csv`, 5 sin ubicación), 5 usuarios, ~4 meses de notas y pagos según el perfil de cada
tienda (puntual, regular, tardío, moroso, nuevo), recordatorios históricos y una evaluación de Ayax. Las fechas son
relativas a hoy. **Las coordenadas son aproximadas por colonia**: si un pin cae mal, corrígelo en el CSV (o con
`PATCH /clientes/{id}/ubicacion`). La carga tarda alrededor de un minuto contra Neon.
