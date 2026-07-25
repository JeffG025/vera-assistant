# Vera — Plan de Trabajo

**Qué es:** plataforma multi-tenant de asistentes IA. Un negocio se registra, carga su conocimiento (sitio web, PDFs, catálogo, FAQs), y obtiene un `<script>` para embeber un asistente que responde con **sus** datos reales, con citas a la fuente y sin inventar.

**Doble propósito:** pieza principal del portafolio (SaaS real desplegado) + producto vendible con setup y mensualidad.

**Fecha del plan:** 2026-07-24

---

## Decisiones tomadas

| Decisión | Elección | Motivo |
|---|---|---|
| Nombre | **Vera** | Elección del usuario. Suena a asistente, fácil de pronunciar para clientes de EE.UU. |
| Backend | **Spring Boot 4.x (Java 21+)** | Tu stack más fuerte. "Java + IA" es más raro y más empleable que "Python + IA" |
| Base de datos | **PostgreSQL 17 + pgvector** (Docker) | pgvector es el estándar para RAG. Además suma Docker al portafolio, que es un hueco actual |
| LLM | **Gemini** (free tier) tras interfaz `LlmProvider` | Sin costo y ya tienes API key de FutBot. La interfaz permite cambiar a Claude sin tocar el dominio |
| Frontend | **React + Vite**, dos apps: `admin/` y `widget/` | El widget debe pesar poco y aislarse del CSS de la página anfitriona |
| Aislamiento multi-tenant | **`tenant_id` + filtro Hibernate + RLS de Postgres** | Doble barrera: si un query se escapa del filtro, la base lo bloquea igual |
| Puerto backend | **8090** | Ocupados: 8080 bancobienestar, 8085 mercaditolibre, 8086 futbot |
| Nicho de arranque | **Tiendas de mascotas US** (inquilino demo #1) | Ya hay 6 prospectos verificados sin chat en vivo y mensajes de outreach listos |
| Segundo demo | **Distribuidor B2B con catálogo PDF** | Dos nichos opuestos en la misma plataforma prueban que sirve para cualquier negocio |

> El nicho **no afecta el código**. Solo define datos de demo, set de evaluación y discurso de venta. Revisable en el Bloque 5.

---

## Arquitectura

```
Sitio del cliente (cualquier web)
   └── <script src=".../vera.js" data-key="pk_live_..."> 
          │
   [Widget React · Shadow DOM]  burbuja → ventana de chat
          │  POST /api/chat (streaming SSE)
          ▼
   [Backend Spring Boot :8090]
      ├── TenantFilter          → resuelve el inquilino en cada request
      ├── ChatService           → orquesta el RAG
      │      ├── Retriever      → búsqueda híbrida (vectorial + texto completo)
      │      ├── LlmProvider    → Gemini | Claude | OpenAI (intercambiable)
      │      └── Guardrails     → responder solo con contexto; si no hay, decir "no sé"
      ├── IngestService         → crawler / PDF / texto → chunks → embeddings
      ├── EvalService           → golden set por inquilino → métricas de calidad
      └── UsageMeter            → tokens y costo por inquilino, con tope
          │
          ▼
   [PostgreSQL + pgvector]   tenants · sources · chunks(embedding) · conversations · evals
```

**Flujo de una pregunta:** el visitante escribe → el widget envía con la clave pública → el backend resuelve el inquilino y valida el dominio → recupera los fragmentos más relevantes de *ese* inquilino → el LLM redacta usando solo ese contexto → responde en streaming con las fuentes citadas → se registra la conversación y el consumo.

---

## Bloques

| | Bloque | Contenido | Semanas | Estado |
|--|-------|-----------|:--:|:--:|
| **0** | **Cimientos** | Monorepo, Spring Boot, Docker Compose (Postgres+pgvector), esquema base, CI | 1 | 🟡 |
| **1** | **Núcleo multi-tenant** | Aislamiento de datos, auth JWT + roles, alta de inquilino, panel base | 2 | ✅ |
| **2** | **Ingesta de conocimiento** | Fuentes (PDF/texto/URL), crawler, troceado, embeddings, panel de fuentes | 2 | 🟡 |
| **3** | **Motor de respuesta (RAG)** | Recuperación híbrida, generación con citas, streaming, memoria, control de costo | 2 | ⬜ |
| **4** | **Evaluación de calidad** | Golden set por inquilino, métricas de acierto y fidelidad, corrida en CI | 1 | ⬜ |
| **5** | **Widget embebible** | Script de embebido, Shadow DOM, theming por inquilino, dominios permitidos | 1.5 | ⬜ |
| **6** | **Bandeja y humanos** | Historial, escalamiento, captura de leads, métricas de negocio | 1.5 | ⬜ |
| **7** | **Canales** | WhatsApp Cloud API y/o Telegram sobre el mismo motor | 1 | ⬜ |
| **8** | **Comercialización** | Planes + Stripe, sitio público, inquilinos demo sembrados, despliegue | 1.5 | ⬜ |
| **9** | **Cierre de portafolio** | README en inglés, caso de estudio, video de 60s, documento de arquitectura | 0.5 | ⬜ |

Leyenda: ✅ hecho · 🟡 en curso · ⬜ pendiente

**Total estimado:** ~14 semanas de trabajo constante. Los bloques 0–5 ya producen un producto vendible; 6–9 lo vuelven negocio.

> **Regla de despliegue:** se despliega desde el Bloque 0, aunque no haga nada. Nunca hay un "falta subirlo".

---

## Bloque 0 — Cimientos

**Incremento 0.1 — Estructura y base de datos** ✅ (verificado: pgvector 0.8.5 sobre PostgreSQL 17.10)
- [x] Monorepo: `backend/` + `admin/` + `widget/` + `infra/` + `database/` + `docs/`
- [x] `infra/docker-compose.yml`: PostgreSQL 17 con pgvector, healthcheck y volumen persistente en el puerto 5432
- [x] `infra/.env.example` versionado; el `.env` real excluido en `.gitignore`
- [x] Verificado: contenedor `healthy`, extensión `vector` 0.8.5 cargada y **distancia coseno ordenando correcto** (idéntico 0.0000 · casi-idéntico 0.0061 · ortogonal 1.0000)
- [x] `V1__base.sql`: `rol`, `tenant`, `usuario` con UUIDs, `CHECK` en plan/estado/slug, email único por inquilino e índice parcial para usuarios de plataforma (movido en el 0.2 a `backend/src/main/resources/db/migration/`)

> ⚠️ **Rutina de arranque de Docker en esta máquina:** Docker Desktop debe abrirse **como administrador** (`com.docker.service` está en Manual). Si falla con error del *Inference manager*, renombrar `%LOCALAPPDATA%\Docker\run`.

**Incremento 0.2 — Esqueleto del backend** ✅ (verificado: Flyway v1 aplicada, health UP/DOWN correcto, 4 restricciones del esquema probadas)
- [x] Spring Boot 4.1.0 (Java 21) en el puerto 8090 con webmvc, data-jpa, validation, Flyway, Lombok
- [x] Credenciales por variable de entorno con valores de desarrollo por defecto (nada quemado en el repo)
- [x] Flyway es el único dueño del esquema (`ddl-auto=validate`, `open-in-view=false`); `V1__base.sql` vive en `backend/src/main/resources/db/migration/`
- [x] Verificado: Flyway creó su historial y aplicó **v1 en 82 ms** → tablas `rol`, `tenant`, `usuario` + los 3 roles sembrados
- [x] `GET /api/health` consulta la base de verdad y expone versión de Postgres y de pgvector
- [x] Verificado el camino de falla: con el contenedor apagado responde **503 `DOWN`**, y vuelve a `UP` al levantarlo
- [x] Verificadas 4 restricciones del esquema con transacción revertida: mismo email en negocios distintos **sí**, duplicado dentro del mismo negocio **no** (incluso cambiando mayúsculas), slug con formato inválido **no**, plan inexistente **no**

> **Desviación del plan:** Spring Security se movió al incremento 1.2. En 0.2 solo habría añadido un `permitAll` temporal que 1.2 reescribe.
>
> **Aprendizaje de Boot 4:** la autoconfiguración vive en módulos separados. Declarar `org.flywaydb:flyway-core` compila y empaqueta la librería pero **no activa nada** — hay que usar `spring-boot-starter-flyway`. Mismo patrón para el resto de integraciones.

**Incremento 0.3 — Esqueleto del frontend** ✅ (verificado en navegador: panel con datos reales y widget compilado montándose en página ajena)
- [x] `admin/` React 19.2.8 + Vite 8.1.5 en el puerto **5177**, con proxy `/api` → `:8090`
- [x] Pantalla de estado que consulta `/api/health` y pinta Postgres y pgvector reales
- [x] `widget/` en el puerto **5178**, modo librería IIFE → **un único `dist/vera.js`**, sin CSS aparte (estilos en línea)
- [x] `demo.html`: página anfitriona con estilos ajenos que solo carga el `<script>` compilado
- [x] Entradas `vera-admin` y `vera-widget` añadidas a `.claude/launch.json` (el 5175 ya lo usaba futbot)
- [x] Verificado: panel muestra **Operativo · PostgreSQL 17.10 · pgvector 0.8.5** sin errores de consola
- [x] Verificado: el widget compilado se monta solo en `demo.html`, abre al hacer clic y recibe la clave pública del `data-vera-key`

> 🐞 **Error atrapado antes de tiempo:** en modo librería Vite **no** sustituye `process.env.NODE_ENV`. Quedaba literal en el bundle → `process is not defined` en cualquier página de cliente, y se empaquetaba el React de desarrollo. Con `define` explícito: **580 kB → 191 kB** (173 → **58.3 kB** gzip).

**Incremento 0.4 — Repo y despliegue temprano** 🟡 (lo local está; falta lo que necesita cuenta del usuario)
- [x] Git inicializado, `.gitattributes` para finales de línea, commits por incremento
- [x] Verificado que no se versiona `node_modules/`, `target/`, `dist/` ni `.env`
- [x] GitHub Actions: backend contra un Postgres+pgvector real y los dos frontends en matriz
- [x] `backend/Dockerfile` multi-etapa (compila con Maven, corre sobre JRE alpine) y `.dockerignore`
- [x] `backend/fly.toml` con health check real a `/api/health` y credenciales por secretos
- [ ] **Pendiente (requiere tu acción):** crear el repo en GitHub — `gh` no está instalado
- [ ] **Pendiente (requiere tu visto bueno):** `flyctl deploy` — provisiona recursos de pago en tu cuenta

---

## Bloque 1 — Núcleo multi-tenant

**Incremento 1.1 — Modelo y aislamiento** ✅ (4 pruebas de aislamiento en verde)
- [x] Entidades `Tenant`, `Usuario`, `Rol` con UUID + repositorios
- [x] `TenantContext` por hilo + `DataSource` que aplica el inquilino a cada conexión y lo limpia al devolverla
- [x] **RLS de Postgres** con `FORCE` y bypass explícito solo para login y alta
- [x] Verificado: A no ve usuarios de B ni buscando por correo; sin contexto no se ve nada; escribir en otro negocio revienta

> 🔴 **Trampa que costó caro:** el usuario de la imagen Docker es **superusuario**, y Postgres **ignora RLS para superusuarios** — `FORCE` no lo evita. Había RLS que no protegía nada. La app baja a un rol `vera_app` sin privilegios (V3) en cuanto terminan las migraciones.
>
> **Cambio de diseño:** se descartó el filtro de Hibernate. RLS es estrictamente más fuerte y dos mecanismos que deben coincidir son una fuente de bugs.

**Incremento 1.2 — Autenticación y roles** ✅
- [x] JWT de acceso (30 min) y refresco (7 días) con `SUPERADMIN` / `OWNER` / `AGENT`
- [x] Filtro que autentica, fija el inquilino y **lo limpia siempre** al soltar el hilo
- [x] `POST /api/auth/login`, `/refresco`, `GET /api/auth/me`
- [x] Errores en JSON: 401 y 403 diferenciados, validaciones por campo
- [x] Verificado: token de refresco **no** sirve como token de acceso

**Incremento 1.3 — Alta de inquilino (self-service)** ✅
- [x] `POST /api/auth/registro`: crea negocio, dueño y clave pública `pk_...`
- [x] Slug derivado del nombre, sin acentos, con desempate automático
- [x] `GET/PATCH /api/negocio` + rotación de clave pública
- [x] Verificado end-to-end: **17 comportamientos** (aislamiento, validaciones, duplicados, rotación)

**Incremento 1.4 — Panel base (React)** ✅ (verificado en navegador)
- [x] Cliente HTTP central: JWT, refresco automático en 401 y cierre de sesión si tampoco pasa
- [x] Contexto de auth, rutas protegidas por sesión y por rol, layout con navegación
- [x] Páginas: Login, Registro, Panel y Ajustes (con fragmento de instalación del widget)
- [x] Verificado: entrar → panel con el negocio real → guardar dominios → confirmado en la base

**Incremento 1.5 — Cuotas y auditoría** ✅
- [x] `LimitesPlan` por plan (fuentes, mensajes/mes, tokens/mes), expuesto en `GET /api/negocio`
- [x] Bitácora de acciones sensibles con RLS propio + `GET /api/negocio/auditoria` (solo OWNER)
- [x] `CuotaService` con el punto único de corte por cuota
- [x] Verificado: A ve sus 3 eventos y B solo el suyo; los conteos cuadran con la base
- [x] ✅ **Corte por cuota verificado en el incremento 2.1**: la cuarta fuente de un plan TRIAL (techo 3) responde 409, y borrar una libera el cupo

---

## Bloque 2 — Ingesta de conocimiento

**Incremento 2.1 — Fuentes manuales** ✅ (11 comprobaciones en verde)
- [x] Entidad `Fuente` con tipo, origen, estado (PENDIENTE/PROCESANDO/LISTA/ERROR), caracteres y error, con RLS propio
- [x] Subida de PDF/TXT/MD (techo 20 MB) + pegado de texto libre
- [x] Extracción de PDF con PDFBox; un PDF sin capa de texto queda en ERROR con el motivo, no revienta
- [x] `GET/POST/DELETE /api/fuentes` — alta para OWNER y AGENT, borrado solo OWNER
- [x] Verificado con un PDF real (el protocolo de Belkis): **21 301 caracteres** extraídos
- [x] Verificado: formato no soportado → 409; cuota TRIAL agotada a la cuarta → 409; borrar libera cupo
- [x] Verificado: el negocio B no ve ninguna fuente del negocio A

**Incremento 2.2 — Crawler del sitio** ✅ (15 comprobaciones en verde contra un sitio de prueba local)
- [x] **Cambio de modelo:** una fuente ahora tiene N `documento` (un PDF = 1; un sitio = 1 por página). Los fragmentos del 2.3 colgarán de ahí. Migración V7 mudó el contenido que vivía en `fuente`
- [x] Rastreo con frenos duros: mismo host, tope de 40 páginas y 3 niveles, 10 s por petición, 2 MB por página
- [x] `robots.txt` respetado (grupo `User-agent: *`) y `sitemap.xml` usado como semilla
- [x] Limpieza de HTML: fuera `script`, `style`, `nav`, `header`, `footer`, `aside`, `form`; se prefiere `main`/`article`
- [x] Re-rastreo con huella SHA-256 por página: informa nuevas / actualizadas / sin cambio / eliminadas
- [x] `POST /api/fuentes/sitio` y `POST /api/fuentes/{id}/rastrear`
- [x] Verificado: 3 páginas indexadas, la vetada por robots **no**, menú y pie **fuera** del índice, contenido útil dentro
- [x] Verificado: re-rastreo sin tocar nada → 3 sin cambio; tras editar un precio → 1 actualizada, 2 sin cambio

> 🐞 **Dos errores encadenados, cazados aquí:** el record del endpoint usaba `int` primitivo, así que omitir los campos opcionales reventaba el JSON. Y ese fallo de parseo **se disfrazaba de 401 "se requiere iniciar sesión"**, porque Spring reenvía a `/error` y ahí el filtro JWT ya no corre. Arreglado con `Integer` envuelto, `/error` en la lista abierta y un manejador que devuelve 400 con el motivo.

**Incremento 2.3 — Troceado y embeddings** 🟡 (pipeline verificado 6/6; falta probar Gemini de verdad)
- [x] Troceado de 1000 con solape de 150, cortando por párrafos y frases, nunca a media palabra
- [x] Cada fragmento guarda título y URL copiados del documento, para poder citar sin otro JOIN
- [x] `ProveedorEmbeddings` intercambiable: Gemini (`text-embedding-004`, por lotes de 100, reintentos con espera creciente ante 429) o **embeddings locales** deterministas sin clave
- [x] Almacenamiento en `vector(768)` con índice **HNSW** de distancia coseno; acceso por JDBC porque Hibernate no mapea `vector`
- [x] Reindexado automático: al crear una fuente y, en el re-rastreo, **solo** los documentos que cambiaron
- [x] `POST /api/fuentes/buscar` — endpoint de depuración que muestra fragmentos y distancias (adelanto del 3.1)
- [x] Verificado: PDF de 21 301 caracteres → **25 trozos** de 744–1000 (media 952), ninguno se pasa del tope, y los 24 pares consecutivos solapan
- [x] Verificado: la pregunta de envíos recupera la página de Envíos y la de precios la de Precios; el otro negocio no recupera nada
- [ ] ⚠️ **Sin verificar:** la calidad **semántica**. Lo anterior se probó con embeddings locales de hashing, que reconocen palabras repetidas, no sinónimos. Pendiente: poner la clave en `application-local.properties` y repetir con Gemini
- [ ] Versión de índice para reindexar sin dejar al asistente sin servicio

**Incremento 2.4 — Panel de fuentes** ⬜
- [ ] Lista con estado (pendiente/indexando/listo/error), progreso y conteo de fragmentos
- [ ] Reindexar, eliminar, previsualizar fragmentos extraídos
- [ ] Verificado E2E: subir PDF → indexar → ver fragmentos

---

## Bloque 3 — Motor de respuesta (RAG)

**Incremento 3.1 — Recuperación** ⬜
- [ ] Búsqueda vectorial por inquilino + búsqueda de texto completo, fusionadas
- [ ] Umbral de relevancia: si nada supera el mínimo, no se llama al LLM
- [ ] Endpoint de depuración que muestra qué fragmentos se recuperaron y con qué puntaje

**Incremento 3.2 — Generación con guardarraíles** ⬜
- [ ] Interfaz `LlmProvider` + implementación Gemini (Claude detrás de la misma interfaz)
- [ ] Prompt de sistema: responder **solo** con el contexto; si no está, decirlo y ofrecer contacto humano
- [ ] Citas a la fuente en cada respuesta (título + enlace o página)
- [ ] Personalidad configurable por inquilino (tono, idioma, mensaje de bienvenida)

**Incremento 3.3 — Streaming y conversación** ⬜
- [ ] Respuesta en streaming (SSE) token por token
- [ ] Sesiones y memoria de conversación con ventana acotada
- [ ] Reescritura de la pregunta con contexto previo ("¿y ese cuánto cuesta?")

**Incremento 3.4 — Costo y abuso** ⬜
- [ ] Medidor de tokens y costo estimado por inquilino y por conversación
- [ ] Límite de tasa por IP y por clave pública; caché de preguntas frecuentes
- [ ] Corte al agotar la cuota del plan

---

## Bloque 4 — Evaluación de calidad *(el diferenciador)*

**Incremento 4.1 — Golden set** ⬜
- [ ] Entidad `EvalCase` por inquilino: pregunta + respuesta esperada + fuente esperada
- [ ] Carga masiva desde CSV y creación desde conversaciones reales
- [ ] 50 casos para el inquilino demo de mascotas

**Incremento 4.2 — Corrida y métricas** ⬜
- [ ] Ejecutar el set completo y registrar cada resultado
- [ ] Métricas: **acierto de recuperación** (¿trajo el fragmento correcto?), **fidelidad** (¿se apegó al contexto?), **cobertura** (¿cuántas dijo "no sé" de más?)
- [ ] Juez automático con LLM + revisión manual de los dudosos

**Incremento 4.3 — Reporte y regresión** ⬜
- [ ] Página de calidad en el panel: puntaje global e historial por corrida
- [ ] Comparar dos corridas para ver si un cambio de prompt o índice mejoró o empeoró
- [ ] Corrida en CI sobre un set fijo; el build falla si baja del umbral

---

## Bloque 5 — Widget embebible

**Incremento 5.1 — Embebido y aislamiento** ⬜
- [ ] Un solo `<script>` con `data-key`; se monta solo, sin tocar el HTML anfitrión
- [ ] **Shadow DOM** para que el CSS del sitio no rompa el widget ni al revés
- [ ] Peso objetivo por debajo de 60 KB comprimido — ⚠️ el esqueleto **vacío** ya pesa 58.3 KB gzip con React. La UI real de chat lo revienta: evaluar alias a **Preact** (`react`/`react-dom` → `preact/compat`), que baja el runtime a ~4 KB

**Incremento 5.2 — Interfaz del chat** ⬜
- [ ] Burbuja flotante, ventana, respuestas en streaming, citas desplegables
- [ ] Móvil, teclado, accesibilidad (foco visible, lectores de pantalla)
- [ ] Estados: escribiendo, error, sin conexión, fuera de cuota

**Incremento 5.3 — Personalización y seguridad** ⬜
- [ ] Colores, avatar, posición, saludo e idioma por inquilino desde el panel
- [ ] Validación de dominio de origen (la clave pública solo funciona en dominios dados de alta)
- [ ] Página de instalación con el fragmento listo para copiar

---

## Bloque 6 — Bandeja y humanos

**Incremento 6.1 — Historial de conversaciones** ⬜
- [ ] Lista y detalle con los fragmentos que usó cada respuesta
- [ ] Filtros por fecha, con o sin respuesta, calificación del visitante

**Incremento 6.2 — Escalamiento a humano** ⬜
- [ ] Botón "hablar con una persona" y detección de frustración
- [ ] Aviso por correo al dueño con el contexto de la conversación

**Incremento 6.3 — Captura de leads** ⬜
- [ ] Pedir nombre y correo en el momento oportuno; exportar a CSV
- [ ] Webhook saliente para conectar con el CRM del cliente

**Incremento 6.4 — Métricas de negocio** ⬜
- [ ] Panel: conversaciones, tasa de resolución, **preguntas sin respuesta** (oro puro: dice qué falta documentar), temas frecuentes
- [ ] Reporte mensual automático por correo — la pieza que justifica la mensualidad

---

## Bloque 7 — Canales adicionales

- [ ] **7.1** WhatsApp Cloud API: webhook, verificación, mismo motor de respuesta
- [ ] **7.2** Telegram (más barato de probar, sirve de ensayo)
- [ ] **7.3** Identidad unificada: la misma persona en web y WhatsApp comparte historial

---

## Bloque 8 — Comercialización

- [ ] **8.1** Planes y suscripción con Stripe (prueba gratis, alta y baja, webhooks)
- [ ] **8.2** Sitio público de Vera en inglés: propuesta, demo en vivo, precios
- [ ] **8.3** Dos inquilinos demo sembrados y pulidos (mascotas + distribuidor B2B)
- [ ] **8.4** Despliegue de producción con respaldos, dominio propio y monitoreo

---

## Bloque 9 — Cierre de portafolio

- [ ] **9.1** README en inglés: problema, arquitectura, capturas, demo viva, cómo correrlo
- [ ] **9.2** Caso de estudio: problema → solución → resultado, con las métricas de calidad reales
- [ ] **9.3** Video de 60 segundos: instalar el widget en un sitio y verlo responder
- [ ] **9.4** Documento de arquitectura con las decisiones y sus porqués (multi-tenancy, RAG, evaluación)

---

## Fuera de alcance (por ahora)

- App móvil nativa
- Voz
- Entrenar modelos propios (se usan APIs)
- Traducción automática del contenido indexado

---

## Proyecto paralelo

**Máquina de prospección con IA** — repo aparte, plan propio. Arranca al cerrar el Bloque 3, para que mientras Vera se termina ya esté llenando la lista de a quién venderle.

---

## Metodología

Incrementos pequeños y **verificados** antes de avanzar. Trabajo **inline y frugal** (sin orquestaciones multi-agente). Cada incremento cierra con: funciona, está probado, está desplegado.

---

## Registro de avance

- **2026-07-24** — Plan creado. Nombre y nicho de arranque definidos.
- **2026-07-24** — **Incremento 0.1 ✅** — monorepo, Docker Compose con Postgres 17 + pgvector 0.8.5 verificado, esquema base `V1__base.sql`. Se resolvió de paso un arranque roto de Docker Desktop (sockets huérfanos con error 1920, irreparables sin elevación → renombrar la carpeta `run`).
- **2026-07-24** — **Incremento 0.2 ✅** — backend Spring Boot 4.1.0 en el 8090, Flyway aplicando v1, `/api/health` con verificación real de la base. La carpeta `database/` se eliminó: la migración vive dentro del backend para no tener dos fuentes de verdad.
- **2026-07-24** — **Incremento 0.3 ✅** — panel (5177) y widget (5178) en React 19 + Vite 8, ambos verificados en navegador. El widget ya compila a un único archivo de 58.3 KB gzip que se auto-monta en una página ajena.

  **Puertos del proyecto:** backend 8090 · panel 5177 · widget 5178 · Postgres 5432.
- **2026-07-24** — **BLOQUE 1 COMPLETO ✅** (1.1–1.5). Aislamiento por RLS verificado, autenticación JWT, alta self-service, panel con sesión y bitácora por negocio. Cuenta de trabajo local: `ana@patitas.test` / `secreta123`.
- **2026-07-24** — **Incremento 2.1 ✅** — fuentes manuales (PDF/TXT/MD y texto pegado) con extracción real, estados y cuota aplicada. De paso quedó verificado el corte por cuota que el 1.5 había dejado pendiente.
- **2026-07-24** — **Incremento 2.2 ✅** — crawler con robots.txt, sitemap, limpieza de HTML y detección de cambios por huella. Se introdujo `documento` como unidad real de contenido (V7).
- **2026-07-24** — **Incremento 2.3 🟡** — troceado con solape, embeddings intercambiables, pgvector con HNSW y reindexado solo de lo que cambió. El pipeline entero está verificado con embeddings locales; **falta repetirlo con Gemini** (clave en `application-local.properties`).
- **2026-07-24** — **0.4 a medias**: repo local, CI y artefactos de despliegue listos; falta crear el repo en GitHub (`gh` no instalado) y `flyctl deploy` (provisiona recursos de pago, pendiente del visto bueno del usuario).
