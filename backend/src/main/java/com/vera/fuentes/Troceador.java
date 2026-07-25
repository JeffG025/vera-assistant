package com.vera.fuentes;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Parte un documento en trozos que quepan en el contexto del modelo. Corta por párrafos, no a la
 * mitad de una frase, y deja solape para que una idea partida en dos siga siendo recuperable.
 */
@Component
public class Troceador {

	public static final int TAMANO = 1000;
	public static final int SOLAPE = 150;

	public List<String> trocear(String texto) {
		return trocear(texto, TAMANO, SOLAPE);
	}

	public List<String> trocear(String texto, int tamano, int solape) {
		List<String> trozos = new ArrayList<>();
		if (texto == null || texto.isBlank()) {
			return trozos;
		}

		String limpio = texto.strip().replaceAll("[ \\t]+", " ");
		StringBuilder actual = new StringBuilder();

		for (String parrafo : limpio.split("\\R{2,}|\\R")) {
			String p = parrafo.strip();
			if (p.isEmpty()) {
				continue;
			}
			// Un párrafo más largo que el trozo se corta por frases antes de rendirse.
			if (p.length() > tamano) {
				cerrar(trozos, actual);
				for (String frase : p.split("(?<=[.!?])\\s+")) {
					if (actual.length() + frase.length() > tamano) {
						cerrar(trozos, actual);
						arrastrarSolape(trozos, actual, solape);
					}
					anexar(actual, frase);
				}
				continue;
			}
			if (actual.length() + p.length() > tamano) {
				cerrar(trozos, actual);
				arrastrarSolape(trozos, actual, solape);
			}
			anexar(actual, p);
		}
		cerrar(trozos, actual);
		return trozos;
	}

	private void anexar(StringBuilder actual, String pieza) {
		if (!actual.isEmpty()) {
			actual.append(' ');
		}
		actual.append(pieza);
	}

	private void cerrar(List<String> trozos, StringBuilder actual) {
		String texto = actual.toString().strip();
		if (!texto.isEmpty()) {
			trozos.add(texto);
		}
		actual.setLength(0);
	}

	// El final del trozo anterior abre el siguiente: así una idea a caballo no se pierde.
	private void arrastrarSolape(List<String> trozos, StringBuilder actual, int solape) {
		if (trozos.isEmpty() || solape <= 0) {
			return;
		}
		String previo = trozos.get(trozos.size() - 1);
		String cola = previo.length() <= solape ? previo : previo.substring(previo.length() - solape);
		int espacio = cola.indexOf(' ');
		actual.append(espacio > 0 ? cola.substring(espacio + 1) : cola);
	}

}
