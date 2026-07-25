import { useEffect, useState } from 'react'
import { api } from '../api/cliente.js'
import { useAuth } from '../auth/AuthContext.jsx'

const ETIQUETAS = {
  UP: { texto: 'Operativo', clase: 'ok' },
  DEGRADED: { texto: 'Degradado', clase: 'aviso' },
  DOWN: { texto: 'Caído', clase: 'error' },
}

export default function Panel() {
  const { negocio, usuario } = useAuth()
  const [salud, setSalud] = useState(null)

  useEffect(() => {
    api.salud().then(setSalud).catch(() => setSalud({ status: 'DOWN' }))
  }, [])

  const estado = salud ? (ETIQUETAS[salud.status] ?? ETIQUETAS.DOWN) : null

  return (
    <>
      <header className="encabezado">
        <h1>Hola, {usuario?.nombre?.split(' ')[0]}</h1>
        <p>
          {negocio?.nombre} · plan {negocio?.plan}
        </p>
      </header>

      <div className="rejilla">
        <section className="tarjeta">
          <h2>Estado del sistema</h2>
          {!salud && <p className="tenue">Consultando…</p>}
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
            </>
          )}
        </section>

        <section className="tarjeta">
          <h2>Siguientes pasos</h2>
          <ol className="pasos">
            <li className="hecho">Negocio creado</li>
            <li>Cargar tu conocimiento (bloque 2)</li>
            <li>Probar el asistente (bloque 3)</li>
            <li>Instalar el widget en tu sitio (bloque 5)</li>
          </ol>
        </section>
      </div>
    </>
  )
}
