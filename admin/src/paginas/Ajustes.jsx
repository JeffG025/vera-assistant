import { useEffect, useState } from 'react'
import { api } from '../api/cliente.js'
import { useAuth } from '../auth/AuthContext.jsx'

export default function Ajustes() {
  const { usuario, actualizarNegocio } = useAuth()
  const esDuenio = usuario?.rol === 'OWNER'

  const [datos, setDatos] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    api.negocio().then(setDatos).catch((e) => setError(e.message))
  }, [])

  if (error && !datos) return <p className="mensaje-error">{error}</p>
  if (!datos) return <p className="tenue">Cargando…</p>

  const cambiar = (e) => setDatos({ ...datos, [e.target.name]: e.target.value })

  async function guardar(e) {
    e.preventDefault()
    setGuardando(true)
    setAviso(null)
    setError(null)
    try {
      const nuevo = await api.actualizarNegocio({
        nombre: datos.nombre,
        dominiosPermitidos: datos.dominiosPermitidos ?? '',
      })
      setDatos(nuevo)
      actualizarNegocio({ ...nuevo })
      setAviso('Cambios guardados')
    } catch (e) {
      setError(e.message)
    } finally {
      setGuardando(false)
    }
  }

  async function rotarClave() {
    if (!confirm('El script instalado en tu sitio dejará de funcionar hasta que lo reemplaces. ¿Continuar?')) return
    try {
      const { clavePublica } = await api.regenerarClave()
      const nuevo = { ...datos, clavePublica }
      setDatos(nuevo)
      actualizarNegocio(nuevo)
      setAviso('Clave regenerada: actualiza el script en tu sitio')
    } catch (e) {
      setError(e.message)
    }
  }

  const fragmento = `<script src="https://tu-dominio/vera.js" data-vera-key="${datos.clavePublica}"></script>`

  return (
    <>
      <header className="encabezado">
        <h1>Ajustes</h1>
        <p>Datos del negocio e instalación del widget</p>
      </header>

      <div className="rejilla">
        <form className="tarjeta formulario" onSubmit={guardar}>
          <h2>Negocio</h2>

          <label>
            Nombre
            <input name="nombre" value={datos.nombre} onChange={cambiar} disabled={!esDuenio} required />
          </label>

          <label>
            Identificador
            <input value={datos.slug} disabled />
            <span className="tenue">Se generó al crear el negocio y no cambia</span>
          </label>

          <label>
            Dominios permitidos
            <input
              name="dominiosPermitidos"
              value={datos.dominiosPermitidos ?? ''}
              onChange={cambiar}
              disabled={!esDuenio}
              placeholder="mitienda.com, www.mitienda.com"
            />
            <span className="tenue">Separados por comas. Vacío = ninguno autorizado todavía</span>
          </label>

          {aviso && <p className="mensaje-ok">{aviso}</p>}
          {error && <p className="mensaje-error">{error}</p>}

          {esDuenio && (
            <button className="boton" disabled={guardando}>
              {guardando ? 'Guardando…' : 'Guardar'}
            </button>
          )}
          {!esDuenio && <p className="tenue">Solo el dueño del negocio puede editar estos datos.</p>}
        </form>

        <section className="tarjeta">
          <h2>Instalación del widget</h2>
          <p className="tenue">Pega esto antes de cerrar el &lt;body&gt; de tu sitio.</p>
          <pre className="fragmento">{fragmento}</pre>

          <dl className="datos">
            <div>
              <dt>Clave pública</dt>
              <dd>
                <code>{datos.clavePublica}</code>
              </dd>
            </div>
          </dl>

          {esDuenio && (
            <button type="button" className="boton secundario" onClick={rotarClave}>
              Regenerar clave
            </button>
          )}
        </section>
      </div>
    </>
  )
}
