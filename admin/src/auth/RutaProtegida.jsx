import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext.jsx'

export default function RutaProtegida({ children, rol }) {
  const { sesion, usuario } = useAuth()
  const ubicacion = useLocation()

  if (!sesion) {
    return <Navigate to="/entrar" replace state={{ desde: ubicacion.pathname }} />
  }
  // Un rol insuficiente no manda al login: ya está autenticado, simplemente no le toca.
  if (rol && usuario?.rol !== rol) {
    return <Navigate to="/" replace />
  }
  return children
}
