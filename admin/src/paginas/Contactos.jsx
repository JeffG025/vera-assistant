import { useEffect, useState } from 'react'
import { api, descargarCsv } from '../api/cliente.js'

// Estado del aviso al CRM. El que importa que se vea es FALLO: un webhook mudo no se arregla.
const AVISOS = {
  ENVIADO: { clase: 'ok', texto: 'avisado' },
  FALLO: { clase: 'error', texto: 'el aviso falló' },
  PENDIENTE: { clase: 'aviso', texto: 'avisando…' },
  SIN_WEBHOOK: null,
}

function fecha(iso) {
  return new Date(iso).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' })
}

export default function Contactos() {
  const [lista, setLista] = useState(null)
  const [error, setError] = useState(null)
  const [bajando, setBajando] = useState(false)

  useEffect(() => {
    api.contactos().then(setLista).catch((e) => setError(e.message))
  }, [])

  async function bajarCsv() {
    setBajando(true)
    setError(null)
    try {
      await descargarCsv('/contactos/csv', 'contactos.csv')
    } catch (e) {
      setError(e.message)
    } finally {
      setBajando(false)
    }
  }

  return (
    <>
      <header className="encabezado">
        <h1>Contactos</h1>
        <p>Gente que dejó sus datos porque Vera no supo responderle</p>
      </header>

      {error && <p className="mensaje-error">{error}</p>}
      {!lista && !error && <p className="tenue">Cargando…</p>}

      {lista && lista.length === 0 && (
        <section className="tarjeta">
          <p className="tenue">
            Todavía no hay ninguno. El formulario aparece solo cuando Vera no sabe responder algo, y
            únicamente si lo activaste en Ajustes.
          </p>
        </section>
      )}

      {lista && lista.length > 0 && (
        <section className="tarjeta">
          <div className="fila-acciones">
            <p className="tenue">
              {lista.length} {lista.length === 1 ? 'contacto' : 'contactos'}
            </p>
            <button type="button" className="boton" onClick={bajarCsv} disabled={bajando}>
              {bajando ? 'Preparando…' : 'Descargar CSV'}
            </button>
          </div>

          <div className="listado">
            {lista.map((c) => {
              const aviso = AVISOS[c.webhookEstado]
              return (
                <div className="fila" key={c.id}>
                  <div className="fila-datos">
                    <div>
                      <strong>{c.nombre}</strong>
                      {aviso && <span className={`insignia ${aviso.clase}`}>{aviso.texto}</span>}
                    </div>
                    <p className="tenue">
                      <a href={`mailto:${c.email}`}>{c.email}</a>
                      {c.telefono && ` · ${c.telefono}`} · {fecha(c.creadoEn)}
                    </p>
                    {c.pregunta && (
                      <p>
                        <span className="tenue">Preguntó: </span>
                        {c.pregunta}
                      </p>
                    )}
                    {c.webhookEstado === 'FALLO' && c.webhookDetalle && (
                      <p className="tenue">Motivo del fallo: {c.webhookDetalle}</p>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </section>
      )}
    </>
  )
}
