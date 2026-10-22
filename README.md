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
| `nota`, `pago`, `recordatorio`, `importacion`, `riesgo` | Entidades y repositorios (servicios y endpoints pendientes) |

Pendiente: `GET /mapa/clientes` (V3 ya trae las columnas) y, en el detalle de cliente, `notasAbiertas` y `riesgo`.
