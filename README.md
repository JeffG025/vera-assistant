# Vera

**Multi-tenant AI assistant platform.** A business uploads its knowledge (website, PDFs, catalog, FAQs) and gets a one-line `<script>` that embeds a chat assistant on any site. The assistant answers **only from that business's own data**, cites its sources, and says "I don't know" instead of making things up.

[![CI](https://github.com/JeffG025/vera-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/JeffG025/vera-assistant/actions/workflows/ci.yml)

> Versión en español: [README.es.md](README.es.md)

---

## The problem

Small businesses lose customers outside office hours: people ask about prices, schedules or policies at night and buy from whoever answers first. Generic chatbots either know nothing about the business or confidently invent answers. Vera is built around one rule: **answer from the business's real content, show where the answer came from, and admit when the answer isn't there.**

## Highlights

- **Multi-tenant by design.** Every row carries a `tenant_id`, enforced twice: a Hibernate filter in the app **and** PostgreSQL Row-Level Security in the database. If a query ever escapes the filter, the database still blocks it.
- **Retrieval-augmented generation (RAG).** Content is chunked with overlap, embedded and stored in **pgvector** (HNSW index). Retrieval is **hybrid**: vector search for meaning plus Postgres full-text search for exact names and codes, merged with Reciprocal Rank Fusion.
- **Grounded answers.** A strict system prompt (answer only from context, cite with `[n]`, never invent prices or deadlines, offer a human when unsure). If nothing relevant is retrieved, Vera answers "I don't know" **without calling the model at all**, so it costs 0 tokens.
- **Built-in quality evaluation.** Each tenant has a golden set of questions, including ones where the correct behavior is to *not* know. Runs are stored, compared and flagged when a question regresses, and an essential set runs against the real model in CI. First full run: **10/10 checks passed**.
- **Embeddable widget.** One `<script>` tag, rendered inside a **Shadow DOM** so the host page's CSS can't break it. Built with Preact via aliasing: **8.58 KB gzip**. It streams answers over SSE and only works on domains the business has allow-listed.
- **Operations for a real SaaS.** JWT auth, per-tenant usage metering with estimated cost in USD, monthly quotas, rate limiting, caching of repeated first-turn questions, a conversation inbox showing which sources were actually cited, lead capture with a CRM webhook (protected by a two-layer anti-SSRF filter) and CSV export with spreadsheet-formula injection disabled.

## Architecture

```
Customer website (any site)
   └── <script src=".../vera.js" data-key="pk_live_...">
          │
   [Widget · Preact · Shadow DOM]   bubble → chat window
          │  POST /api/widget/chat/stream  (SSE)
          ▼
   [Backend · Spring Boot 4 · Java 21 · :8090]
      ├── Tenant filter      → resolves the tenant on every request, checks the domain
      ├── Chat service       → orchestrates RAG
      │     ├── Retriever    → hybrid search (pgvector + full-text, RRF)
      │     ├── LLM provider → Gemini today, swappable behind an interface
      │     └── Guardrails   → answer only from context, otherwise "I don't know"
      ├── Ingestion          → crawler (robots.txt, sitemap) / PDF / text → chunks → embeddings
      ├── Evaluation         → golden set per tenant → quality metrics per run
      └── Usage meter        → tokens and cost per tenant, with a monthly cap
          │
          ▼
   [PostgreSQL 17 + pgvector]   tenants · sources · chunks(embedding) · conversations · evals
```

**Life of a question:** the visitor types → the widget sends it with the business's public key → the backend resolves the tenant and validates the domain → retrieves the most relevant chunks of *that* tenant → the LLM writes an answer using only that context → the answer streams back with its sources → the conversation and its token usage are recorded.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Data JPA / Hibernate, Flyway, JWT |
| Data | PostgreSQL 17, pgvector (HNSW), full-text search, Row-Level Security |
| AI | Google Gemini (chat + embeddings) behind a provider interface |
| Admin panel | React 19 + Vite |
| Widget | Preact (aliased from React), Shadow DOM, Server-Sent Events |
| Tooling | Docker Compose, GitHub Actions CI, Maven wrapper |

## Project structure

| Folder | What it is |
|---|---|
| `backend/` | REST API in Spring Boot, port **8090** |
| `admin/` | Business dashboard (React 19 + Vite), port **5177** |
| `widget/` | Embeddable widget, builds to a single `vera.js`, port **5178** |
| `infra/` | Docker Compose with PostgreSQL 17 + pgvector, port **5432** |
| `docs/evaluacion/` | Demo corpus and versioned evaluation cases (CSV) |

## Run it locally

Requirements: Docker, Java 21 and Node.js 24.

```bash
# 1. Database
cd infra && docker compose up -d

# 2. Backend (Flyway applies the migrations on startup)
cd backend && ./mvnw spring-boot:run

# 3. Admin panel
cd admin && npm ci && npm run dev

# 4. Widget
cd widget && npm ci && npm run dev
```

Quick check: `curl http://localhost:8090/api/health` should answer `UP` with the Postgres and pgvector versions.

**Gemini API key (optional).** Copy `backend/src/main/resources/application-local.properties.example` to `application-local.properties` and set `vera.gemini.api-key`. That file is git-ignored. Without a key the app still starts, but it uses local embeddings that are not semantic, so answer quality drops.

**Configuration.** Database credentials come from environment variables with development defaults; copy `infra/.env.example` to `infra/.env` to change them. Flyway is the only owner of the schema (Hibernate runs with `ddl-auto=validate`).

## Testing and CI

- `./mvnw verify` runs the backend test suite against a real PostgreSQL + pgvector instance (49 tests at last count).
- GitHub Actions builds the backend, the admin panel and the widget on every push and pull request.
- The quality exam against the real model runs on demand and weekly; it is skipped with a notice when no `GEMINI_API_KEY` secret is configured.

## Status

Core platform complete: multi-tenancy, ingestion, the RAG engine, quality evaluation, the embeddable widget and the conversation inbox. Still pending: monthly email reports (waiting on SMTP), additional channels such as WhatsApp, and a public demo deployment. The detailed plan and progress log live in [PLAN-DE-TRABAJO.md](PLAN-DE-TRABAJO.md) (Spanish).

## Author

**Ian Jeffrey González Rosas**, full-stack developer (Java / Spring Boot, React, applied AI) · [github.com/JeffG025](https://github.com/JeffG025)
