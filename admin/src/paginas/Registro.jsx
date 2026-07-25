import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'

export default function Registro() {
  const { sesion, registrarse } = useAuth()
  const navegar = useNavigate()
  const [datos, setDatos] = useState({ negocio: '', nombre: '', email: '', password: '' })
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  if (sesion) return <Navigate to="/" replace />

  const cambiar = (e) => setDatos({ ...datos, [e.target.name]: e.target.value })

  async function enviar(e) {
    e.preventDefault()
    setEnviando(true)
    setError(null)
    try {
      await registrarse(datos)
      navegar('/', { replace: true })
    } catch (e) {
      setError(e.message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="pantalla-acceso">
      <form className="tarjeta formulario" onSubmit={enviar}>
        <h1>Crea tu negocio</h1>

        <label>
          Nombre del negocio
          <input name="negocio" value={datos.negocio} onChange={cambiar} required autoFocus />
        </label>

        <label>
          Tu nombre
          <input name="nombre" value={datos.nombre} onChange={cambiar} required />
        </label>

        <label>
          Correo
          <input name="email" type="email" value={datos.email} onChange={cambiar} required />
        </label>

        <label>
          Contraseña
          <input
            name="password"
            type="password"
            minLength={8}
            value={datos.password}
            onChange={cambiar}
            required
          />
          <span className="tenue">Mínimo 8 caracteres</span>
        </label>

        {error && (
          <p className="mensaje-error" role="alert">
            {error}
          </p>
        )}

        <button className="boton" disabled={enviando}>
          {enviando ? 'Creando…' : 'Crear negocio'}
        </button>

        <p className="tenue">
          ¿Ya tienes cuenta? <Link to="/entrar">Entrar</Link>
        </p>
      </form>
    </div>
  )
}
