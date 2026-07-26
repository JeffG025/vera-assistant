import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'

export default function Layout() {
  const { usuario, negocio, salir } = useAuth()

  return (
    <div className="marco">
      <aside className="barra">
        <div className="marca">
          <strong>Vera</strong>
          <span className="tenue">{negocio?.nombre ?? '—'}</span>
        </div>

        <nav>
          <NavLink to="/" end>
            Panel
          </NavLink>
          <NavLink to="/fuentes">Fuentes</NavLink>
          <NavLink to="/ajustes">Ajustes</NavLink>
        </nav>

        <div className="usuario">
          <div>
            <div>{usuario?.nombre}</div>
            <span className="tenue">{usuario?.rol}</span>
          </div>
          <button className="boton-texto" onClick={salir}>
            Salir
          </button>
        </div>
      </aside>

      <main className="contenido">
        <Outlet />
      </main>
    </div>
  )
}
