import { useEffect, useRef, useState } from 'react'
import { api } from '../api/cliente.js'
import { useAuth } from '../auth/AuthContext.jsx'

const ESTADOS = {
  PENDIENTE: 'aviso',
  PROCESANDO: 'aviso',
  LISTA: 'ok',
  ERROR: 'error',
}

export default function Fuentes() {
  const { usuario } = useAuth()
  const esDuenio = usuario?.rol === 'OWNER'

  const [fuentes, setFuentes] = useState(null)
  const [limite, setLimite] = useState(null)
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const [abierta, setAbierta] = useState(null)
  const [trozos, setTrozos] = useState([])
  const [texto, setTexto] = useState({ nombre: '', texto: '' })
  const [sitio, setSitio] = useState('')
  const archivoRef = useRef(null)

  useEffect(() => {
    recargar()
    api.negocio().then((n) => setLimite(n.limites?.fuentes ?? null)).catch(() => {})
  }, [])

  async function recargar() {
    try {
      setFuentes(await api.fuentes())
    } catch (e) {
      setError(e.message)
    }
  }

  // Envoltura única: cualquier alta bloquea la UI, limpia mensajes y recarga al terminar.
  async function ejecutar(accion, mensajeOk) {
    setOcupado(true)
    setError(null)
    setAviso(null)
    try {
      await accion()
      setAviso(mensajeOk)
      await recargar()
    } catch (e) {
      setError(e.message)
    } finally {
      setOcupado(false)
    }
  }

  async function verFragmentos(fuente) {
    if (abierta === fuente.id) {
      setAbierta(null)
      return
    }
    setAbierta(fuente.id)
    setTrozos([])
    try {
      setTrozos(await api.fragmentosDe(fuente.id))
    } catch (e) {
      setError(e.message)
    }
  }

  if (!fuentes) return <p className="tenue">Cargando…</p>

  const sinCupo = limite != null && fuentes.length >= limite

  return (
    <>
      <header className="encabezado">
        <h1>Fuentes</h1>
        <p>
          De aquí saca Vera sus respuestas
          {limite != null && ` · ${fuentes.length} de ${limite} de tu plan`}
        </p>
      </header>

      {aviso && <p className="mensaje-ok">{aviso}</p>}
      {error && <p className="mensaje-error">{error}</p>}
      {sinCupo && <p className="mensaje-error">Llegaste al tope de tu plan. Borra una fuente o sube de plan.</p>}

      <div className="rejilla">
        <section className="tarjeta formulario">
          <h2>Subir un archivo</h2>
          <p className="tenue">PDF, TXT o Markdown, hasta 20 MB.</p>
          <input type="file" ref={archivoRef} accept=".pdf,.txt,.md,.markdown" disabled={ocupado || sinCupo} />
          <button
            className="boton"
            disabled={ocupado || sinCupo}
            onClick={() => {
              const archivo = archivoRef.current?.files?.[0]
              if (!archivo) return setError('Elige un archivo primero')
              ejecutar(() => api.fuenteArchivo(archivo), `«${archivo.name}» procesado`)
            }}
          >
            Subir
          </button>
        </section>

        <section className="tarjeta formulario">
          <h2>Rastrear un sitio</h2>
          <p className="tenue">Recorre las páginas del sitio respetando su robots.txt.</p>
          <input
            value={sitio}
            onChange={(e) => setSitio(e.target.value)}
            placeholder="https://mitienda.com"
            disabled={ocupado || sinCupo}
          />
          <button
            className="boton"
            disabled={ocupado || sinCupo || !sitio.trim()}
            onClick={() => ejecutar(() => api.fuenteSitio({ url: sitio.trim() }), 'Sitio rastreado')}
          >
            Rastrear
          </button>
        </section>

        <section className="tarjeta formulario">
          <h2>Pegar texto</h2>
          <input
            value={texto.nombre}
            onChange={(e) => setTexto({ ...texto, nombre: e.target.value })}
            placeholder="Nombre, p. ej. Preguntas frecuentes"
            disabled={ocupado || sinCupo}
          />
          <textarea
            rows={5}
            value={texto.texto}
            onChange={(e) => setTexto({ ...texto, texto: e.target.value })}
            placeholder="Pega aquí las preguntas frecuentes, políticas, precios…"
            disabled={ocupado || sinCupo}
          />
          <button
            className="boton"
            disabled={ocupado || sinCupo || !texto.nombre.trim() || !texto.texto.trim()}
            onClick={() =>
              ejecutar(async () => {
                await api.fuenteTexto(texto)
                setTexto({ nombre: '', texto: '' })
              }, 'Texto indexado')
            }
          >
            Guardar
          </button>
        </section>
      </div>

      <section className="tarjeta listado">
        <h2>Lo que Vera ya leyó</h2>

        {fuentes.length === 0 && <p className="tenue">Todavía no hay nada. Sube algo arriba para empezar.</p>}

        {fuentes.map((f) => (
          <article key={f.id} className="fila">
            <div className="fila-datos">
              <div>
                <strong>{f.nombre}</strong>
                <span className={`insignia ${ESTADOS[f.estado] ?? 'aviso'}`}>{f.estado}</span>
                <span className="insignia">{f.tipo}</span>
              </div>
              <p className="tenue">
                {f.documentos} {f.documentos === 1 ? 'documento' : 'documentos'} ·{' '}
                {f.caracteres.toLocaleString('es-MX')} caracteres
                {f.origen && ` · ${f.origen}`}
              </p>
              {f.error && <p className="mensaje-error">{f.error}</p>}
            </div>

            <div className="fila-acciones">
              <button className="boton-texto" onClick={() => verFragmentos(f)}>
                {abierta === f.id ? 'Ocultar trozos' : 'Ver trozos'}
              </button>
              {f.tipo === 'URL' && (
                <button
                  className="boton-texto"
                  disabled={ocupado}
                  onClick={() => ejecutar(() => api.rastrearDeNuevo(f.id), 'Sitio revisado de nuevo')}
                >
                  Volver a rastrear
                </button>
              )}
              {esDuenio && (
                <button
                  className="boton-texto peligro"
                  disabled={ocupado}
                  onClick={() => {
                    if (!confirm(`¿Borrar «${f.nombre}»? Vera dejará de usarla para responder.`)) return
                    ejecutar(() => api.borrarFuente(f.id), 'Fuente borrada')
                  }}
                >
                  Borrar
                </button>
              )}
            </div>

            {abierta === f.id && (
              <div className="trozos">
                {trozos.length === 0 && <p className="tenue">Sin trozos indexados.</p>}
                {trozos.map((t, i) => (
                  <div key={i} className="trozo">
                    <span className="tenue">
                      #{t.orden + 1} · {t.largo} caracteres
                      {t.titulo && ` · ${t.titulo}`}
                    </span>
                    <p>{t.extracto}…</p>
                  </div>
                ))}
              </div>
            )}
          </article>
        ))}
      </section>
    </>
  )
}
