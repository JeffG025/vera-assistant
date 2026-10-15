# Vera

Plataforma multi-tenant de asistentes IA. Un negocio carga su conocimiento (sitio web, PDFs, catálogo, FAQs) y obtiene un `<script>` para embeber un asistente que responde con **sus** datos reales, con citas a la fuente.

> English version: [README.md](README.md). Caso de estudio pendiente: bloque 9 del [plan de trabajo](PLAN-DE-TRABAJO.md).

## Estructura

| Carpeta | Qué es |
|---|---|
| `backend/` | API en Spring Boot 4.1 (Java 21), puerto **8090** |
| `admin/` | Panel del negocio, React 19 + Vite, puerto **5177** |
| `widget/` | Widget embebible, compila a un único `vera.js`, puerto **5178** |
| `infra/` | Docker Compose con PostgreSQL 17 + pgvector, puerto **5432** |

## Levantar en local

```bash
# 1. Base de datos
cd infra && docker compose up -d

# 2. Backend (aplica las migraciones de Flyway al arrancar)
cd backend && ./mvnw spring-boot:run

# 3. Panel
cd admin && npm install && npm run dev

# 4. Widget
cd widget && npm install && npm run dev
```

Comprobación rápida: `curl http://localhost:8090/api/health` debe responder `UP` con las versiones de Postgres y pgvector.

## Notas

- Flyway es el único dueño del esquema; Hibernate solo valida (`ddl-auto=validate`).
- Las credenciales se leen de variables de entorno con valores de desarrollo por defecto. Copia `infra/.env.example` a `infra/.env` para cambiarlas.
