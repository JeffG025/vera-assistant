package com.vera.fuentes;

import java.util.List;

public interface ProveedorEmbeddings {

	// Fijado por el tipo de columna vector(768) de la migración V8.
	int DIMENSIONES = 768;

	String modelo();

	List<float[]> generar(List<String> textos);

	default float[] generarUno(String texto) {
		return generar(List.of(texto)).get(0);
	}

}
