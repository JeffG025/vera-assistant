import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: { port: 5178 },
  // El widget vive en la página de otro: cada kilobyte lo paga el cliente en tiempo de carga.
  // Preact habla la misma API que React con una fracción del runtime, y se cambia con un alias
  // sin tocar una sola línea de los componentes.
  resolve: {
    alias: {
      react: 'preact/compat',
      'react-dom': 'preact/compat',
      'react-dom/client': 'preact/compat/client',
      'react/jsx-runtime': 'preact/jsx-runtime',
    },
  },
  // En modo libreria Vite NO sustituye esto solo: sin la linea, el navegador
  // revienta con "process is not defined" y ademas se empaqueta el React de desarrollo.
  define: {
    'process.env.NODE_ENV': JSON.stringify('production'),
  },
  build: {
    // Un solo archivo IIFE con React dentro: la pagina anfitriona solo carga <script src="vera.js">.
    lib: {
      entry: 'src/main.jsx',
      name: 'Vera',
      formats: ['iife'],
      fileName: () => 'vera.js',
    },
    // Sin CSS aparte: los estilos van en linea para no romper la promesa de un solo archivo.
    cssCodeSplit: false,
    emptyOutDir: true,
  },
})
