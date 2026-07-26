package com.vera.rag;

import java.util.List;
import java.util.function.Consumer;

/**
 * Suplente sin clave: no redacta nada. Deja la app funcionando y las pruebas corriendo
 * sin secretos, pero no es un asistente.
 */
public class ChatLocal implements ProveedorChat {

	private static final String AVISO = "[modo sin modelo] No hay clave de IA configurada, "
			+ "asi que no puedo redactar una respuesta. Los fragmentos relevantes van en las fuentes.";

	@Override
	public String modelo() {
		return "local-sin-modelo";
	}

	@Override
	public Respuesta responder(String sistema, List<Turno> historial, String pregunta) {
		return new Respuesta(AVISO, 0);
	}

	@Override
	public Respuesta responderEnStream(String sistema, List<Turno> historial, String pregunta,
			Consumer<String> alRecibir) {
		alRecibir.accept(AVISO);
		return new Respuesta(AVISO, 0);
	}

}
