import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'

export default function Login() {
  const { sesion, entrar } = useAuth()
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const [datos, setDatos] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  if (sesion) return <Navigate to="/" replace />

  const cambiar = (e) => setDatos({ ...datos, [e.target.name]: e.target.value })

  async function enviar(e) {
    e.preventDefault()
    setEnviando(true)
    setError(null)
    try {
      await entrar(datos)
      navegar(ubicacion.state?.desde ?? '/', { replace: true })
    } catch (e) {
      setError(e.message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="pantalla-acceso">
      <form className="tarjeta formulario" onSubmit={enviar}>
        <h1>Entrar a Vera</h1>

        <label>
          Correo
          <input name="email" type="email" value={datos.email} onChange={cambiar} required autoFocus />
        </label>

        <label>
          Contraseña
          <input name="password" type="password" value={datos.password} onChange={cambiar} required />
        </label>

        {error && (
          <p className="mensaje-error" role="alert">
            {error}
          </p>
        )}

        <button className="boton" disabled={enviando}>
          {enviando ? 'Entrando…' : 'Entrar'}
        </button>

        <p className="tenue">
          ¿No tienes cuenta? <Link to="/registro">Crea tu negocio</Link>
        </p>
      </form>
    </div>
  )
}
