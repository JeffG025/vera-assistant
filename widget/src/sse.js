/**
 * Lee un flujo de eventos SSE de una respuesta fetch. No se usa EventSource porque solo hace
 * GET y no deja mandar cabeceras, y aquí hace falta un POST con la clave pública.
 *
 * Todos los eventos traen JSON, así que el dato llega en una sola línea; aun así se juntan las
 * líneas `data:` por si el servidor decide partirlas.
 */
export async function leerEventos(cuerpo, alEvento) {
  const lector = cuerpo.pipeThrough(new TextDecoderStream()).getReader()
  let resto = ''

  for (;;) {
    const { value, done } = await lector.read()
    if (done) break
    resto += value

    // Un evento termina en línea en blanco; lo que sobra espera al siguiente pedazo de red.
    for (;;) {
      const corte = resto.search(/\r?\n\r?\n/)
      if (corte === -1) break
      const separador = resto.slice(corte).match(/^\r?\n\r?\n/)[0]
      despachar(resto.slice(0, corte), alEvento)
      resto = resto.slice(corte + separador.length)
    }
  }
  if (resto.trim()) despachar(resto, alEvento)
}

function despachar(bloque, alEvento) {
  let nombre = 'message'
  const datos = []
  for (const linea of bloque.split(/\r?\n/)) {
    if (linea.startsWith('event:')) nombre = linea.slice(6).trim()
    else if (linea.startsWith('data:')) datos.push(linea.slice(5).replace(/^ /, ''))
  }
  if (datos.length) alEvento(nombre, datos.join('\n'))
}
