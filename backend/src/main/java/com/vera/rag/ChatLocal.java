package com.vera.rag;

/**
 * Suplente sin clave: devuelve el fragmento recuperado tal cual, sin redactar.
 * Deja la app funcionando y las pruebas corriendo sin secretos, pero no es un asistente.
 */
public class ChatLocal implements ProveedorChat {

	@Override
	public String modelo() {
		return "local-sin-modelo";
	}

	@Override
	public Respuesta responder(String sistema, String pregunta) {
		return new Respuesta(
				"[modo sin modelo] No hay clave de IA configurada, así que no puedo redactar una respuesta. "
						+ "Los fragmentos relevantes van en las fuentes de esta respuesta.",
				0);
	}

}
