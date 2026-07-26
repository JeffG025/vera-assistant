package com.vera.rag;

public interface ProveedorChat {

	String modelo();

	Respuesta responder(String sistema, String pregunta);

	record Respuesta(String texto, int tokens) {
	}

}
