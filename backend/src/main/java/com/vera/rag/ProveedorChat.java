package com.vera.rag;

import java.util.List;
import java.util.function.Consumer;

public interface ProveedorChat {

	String modelo();

	Respuesta responder(String sistema, List<Turno> historial, String pregunta);

	/** Igual que responder, pero entrega el texto por pedazos conforme llega. */
	Respuesta responderEnStream(String sistema, List<Turno> historial, String pregunta,
			Consumer<String> alRecibir);

	// rol: "user" o "model", que es como los espera la API de Gemini.
	record Turno(String rol, String texto) {
	}

	record Respuesta(String texto, int tokens) {
	}

}
