package com.vera.fuentes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Embeddings de bolsa de palabras con hashing. Son deterministas y no necesitan clave, asi que
 * dejan correr el pipeline y las pruebas sin secretos.
 *
 * NO son semanticos: reconocen palabras repetidas, no sinonimos. Sirven para verificar que el
 * troceado, el guardado y la busqueda vectorial funcionan, jamas para medir calidad de respuestas.
 */
public class EmbeddingsLocales implements ProveedorEmbeddings {

	@Override
	public String modelo() {
		return "local-hashing-v1";
	}

	@Override
	public List<float[]> generar(List<String> textos) {
		List<float[]> salida = new ArrayList<>(textos.size());
		for (String texto : textos) {
			salida.add(vectorizar(texto));
		}
		return salida;
	}

	private float[] vectorizar(String texto) {
		float[] vector = new float[DIMENSIONES];
		for (String palabra : texto.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
			if (palabra.isBlank()) {
				continue;
			}
			int posicion = Math.floorMod(palabra.hashCode(), DIMENSIONES);
			vector[posicion] += 1f;
		}
		return normalizar(vector);
	}

	// Sin normalizar, un texto largo quedaria "lejos" de todo solo por ser largo.
	private float[] normalizar(float[] vector) {
		double suma = 0;
		for (float v : vector) {
			suma += v * v;
		}
		float norma = (float) Math.sqrt(suma);
		if (norma == 0) {
			vector[0] = 1f;
			return vector;
		}
		for (int i = 0; i < vector.length; i++) {
			vector[i] /= norma;
		}
		return vector;
	}

}
