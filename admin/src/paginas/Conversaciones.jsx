import { useEffect, useState } from 'react'
import { api } from '../api/cliente.js'

const SIN_FILTROS = { desde: '', hasta: '', canal: '', sinRespuesta: false, calificacion: '' }

function fecha(iso) {
  return new Date(iso).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' })
}

// El backend espera parámetros ausentes, no vacíos: mandar canal= filtraría por cadena vacía.
function soloLoPuesto(filtros, pagina) {
  const puestos = { pagina }
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor !== '' && valor !== false) puestos[clave] = valor
  }
  return puestos
}

export default function Conversaciones() {
  const [filtros, setFiltros] = useState(SIN_FILTROS)
  const [pagina, setPagina] = useState(0)
  const [datos, setDatos] = useState(null)
  const [abierta, setAbierta] = useState(null)
  const [detalle, setDetalle] = useState(null)
  const [fragmentosDe, setFragmentosDe] = useState(null)
  const [error, setError] = useState(null)

  // La bandera evita que una respuesta lenta de un filtro viejo pise a la del filtro actual.
  useEffect(() => {
    let vigente = true
    setDatos(null)
    api
      .bandeja(soloLoPuesto(filtros, pagina))
      .then((d) => vigente && setDatos(d))
      .catch((e) => vigente && setError(e.message))
    return () => {
      vigente = false
    }
  }, [filtros, pagina])

  async function abrir(id) {
    if (abierta === id) {
      setAbierta(null)
      return
    }
    setAbierta(id)
    setDetalle(null)
    setFragmentosDe(null)
    try {
      setDetalle(await api.conversacion(id))
    } catch (e) {
      setError(e.message)
    }
  }

  function cambiar(campo, valor) {
    setPagina(0)
    setFiltros({ ...filtros, [campo]: valor })
  }

  return (
    <>
      <header className="encabezado">
        <h1>Conversaciones</h1>
        <p>Lo que te preguntan de verdad, y con qué respondió Vera</p>
      </header>

      {error && <p className="mensaje-error">{error}</p>}

      <section className="tarjeta">
        <h2>Filtros</h2>

        <div className="campos-en-fila">
          <label>
            Desde
            <input type="date" value={filtros.desde} onChange={(e) => cambiar('desde', e.target.value)} />
          </label>
          <label>
            Hasta
            <input type="date" value={filtros.hasta} onChange={(e) => cambiar('hasta', e.target.value)} />
          </label>
          <label>
            Canal
            <select value={filtros.canal} onChange={(e) => cambiar('canal', e.target.value)}>
              <option value="">Todos</option>
              <option value="WEB">Widget del sitio</option>
              <option value="PANEL">Pruebas del panel</option>
            </select>
          </label>
          <label>
            Calificación
            <select value={filtros.calificacion} onChange={(e) => cambiar('calificacion', e.target.value)}>
              <option value="">Cualquiera</option>
              <option value="1">Sirvió</option>
              <option value="-1">No sirvió</option>
            </select>
          </label>
        </div>

        <label className="casilla">
          <input
            type="checkbox"
            checked={filtros.sinRespuesta}
            onChange={(e) => cambiar('sinRespuesta', e.target.checked)}
          />
          Solo las que se quedaron sin respuesta
        </label>

        <p className="tenue">
          Las que se quedan sin respuesta son las más valiosas: dicen qué te preguntan que tu
          conocimiento todavía no cubre.
        </p>
      </section>

      {!datos && !error && <p className="tenue">Cargando…</p>}

      {datos && datos.filas.length === 0 && (
        <p className="tenue">No hay conversaciones con esos filtros.</p>
      )}

      {datos && datos.filas.length > 0 && (
        <section className="tarjeta">
          <p className="tenue">
            {datos.total} {datos.total === 1 ? 'conversación' : 'conversaciones'} · página{' '}
            {datos.pagina + 1} de {datos.paginas}
          </p>

          <div className="listado">
            {datos.filas.map((c) => (
              <div key={c.id}>
                <div className="fila">
                  <div className="fila-datos">
                    <div>
                      <strong>{c.primeraPregunta ?? '(sin preguntas)'}</strong>
                    </div>
                    <p className="tenue">
                      {fecha(c.creadoEn)} · {c.canal} · {c.preguntas} pregunta
                      {c.preguntas === 1 ? '' : 's'}
                      {c.sinRespuesta > 0 && (
                        <span className="insignia aviso">{c.sinRespuesta} sin respuesta</span>
                      )}
                      {c.positivas > 0 && <span className="insignia ok">+{c.positivas}</span>}
                      {c.negativas > 0 && <span className="insignia error">−{c.negativas}</span>}
                    </p>
                  </div>

                  <div className="fila-acciones">
                    <button type="button" className="boton-texto" onClick={() => abrir(c.id)}>
                      {abierta === c.id ? 'Cerrar' : 'Ver el hilo'}
                    </button>
                  </div>
                </div>

                {abierta === c.id && (
                  <div className="hilo">
                    {!detalle && <p className="tenue">Cargando el hilo…</p>}
                    {detalle &&
                      detalle.mensajes.map((m) => (
                        <div key={m.id} className={`turno ${m.rol.toLowerCase()}`}>
                          <p className="tenue">
                            {m.rol === 'USUARIO' ? 'Visitante' : 'Vera'} · {fecha(m.creadoEn)}
                            {m.sinInformacion && <span className="insignia aviso">sin respuesta</span>}
                            {m.calificacion === 1 && <span className="insignia ok">le sirvió</span>}
                            {m.calificacion === -1 && <span className="insignia error">no le sirvió</span>}
                          </p>
                          <p>{m.texto}</p>

                          {m.fuentes.length > 0 && (
                            <>
                              <button
                                type="button"
                                className="boton-texto"
                                onClick={() => setFragmentosDe(fragmentosDe === m.id ? null : m.id)}
                              >
                                {fragmentosDe === m.id ? 'Ocultar' : 'Ver'} los {m.fuentes.length}{' '}
                                fragmentos que tuvo delante — citó{' '}
                                {m.fuentes.filter((f) => f.citada).length} de {m.fuentes.length}
                              </button>

                              {fragmentosDe === m.id && (
                                <div className="trozos">
                                  {m.fuentes.map((f) => (
                                    <div key={f.numero} className={f.citada ? 'trozo citado' : 'trozo'}>
                                      <p className="tenue">
                                        [{f.numero}] {f.titulo ?? 'sin título'} · distancia{' '}
                                        {f.distancia.toFixed(4)} ·{' '}
                                        {f.citada ? 'lo citó' : 'no lo usó'}
                                      </p>
                                      <p>{f.texto}</p>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </>
                          )}
                        </div>
                      ))}
                  </div>
                )}
              </div>
            ))}
          </div>

          <div className="fila-acciones">
            <button
              type="button"
              className="boton secundario"
              disabled={datos.pagina === 0}
              onClick={() => setPagina(pagina - 1)}
            >
              Anteriores
            </button>
            <button
              type="button"
              className="boton secundario"
              disabled={datos.pagina + 1 >= datos.paginas}
              onClick={() => setPagina(pagina + 1)}
            >
              Siguientes
            </button>
          </div>
        </section>
      )}
    </>
  )
}
