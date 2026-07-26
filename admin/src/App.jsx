import { Navigate, Route, Routes } from 'react-router-dom'
import RutaProtegida from './auth/RutaProtegida.jsx'
import Layout from './componentes/Layout.jsx'
import Ajustes from './paginas/Ajustes.jsx'
import Fuentes from './paginas/Fuentes.jsx'
import Login from './paginas/Login.jsx'
import Panel from './paginas/Panel.jsx'
import Registro from './paginas/Registro.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/entrar" element={<Login />} />
      <Route path="/registro" element={<Registro />} />

      <Route
        element={
          <RutaProtegida>
            <Layout />
          </RutaProtegida>
        }
      >
        <Route path="/" element={<Panel />} />
        <Route path="/fuentes" element={<Fuentes />} />
        <Route path="/ajustes" element={<Ajustes />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
