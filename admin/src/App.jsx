import { useEffect, useState } from 'react'

const ETIQUETAS = {
  UP: { texto: 'Operativo', clase: 'ok' },
  DEGRADED: { texto: 'Degradado', clase: 'aviso' },
  DOWN: { texto: 'Caído', clase: 'error' },
}

export default function App() {
  const [salud, setSalud] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    // Verifica de punta a punta que el proxy de Vite llega al backend.
    fetch('/api/health')
      .then((r) => r.json())
      .then(setSalud)
      .catch((e) => setError(e.message))
  }, [])

  const estado = salud ? (ETIQUETAS[salud.status] ?? ETIQUETAS.DOWN) : null

  return (
    <main className="pagina">
      <header className="encabezado">
        <h1>Vera</h1>
        <p>Plataforma multi-tenant de asistentes IA</p>
      </header>

      <section className="tarjeta">
        <h2>Estado del sistema</h2>

        {!salud && !error && <p className="tenue">Consultando el backend…</p>}

        {error && (
          <p className="tenue">
            No se pudo contactar al backend: <code>{error}</code>
          </p>
        )}

        {salud && (
          <>
            <p className={`estado ${estado.clase}`}>
              <span className="punto" /> {estado.texto}
            </p>
            <dl className="datos">
              <div>
                <dt>PostgreSQL</dt>
                <dd>{salud.postgres ?? '—'}</dd>
              </div>
              <div>
                <dt>pgvector</dt>
                <dd>{salud.pgvector ?? 'no instalado'}</dd>
              </div>
            </dl>
            {salud.error && <p className="tenue">{salud.error}</p>}
          </>
        )}
      </section>

      <p className="pie">Bloque 0 — Cimientos</p>
    </main>
  )
}
