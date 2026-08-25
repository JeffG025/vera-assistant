package com.vera.rag;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caché de preguntas repetidas. En una tienda, "cuánto cuesta el envío" llega decenas de veces
 * al día y cada una cuesta una llamada al modelo.
 *
 * Solo se cachea el PRIMER turno de una conversación: en cuanto hay historial la respuesta
 * depende del hilo y dos personas con la misma pregunta ya no esperan lo mismo.
 */
@Component
public class CacheRespuestas {

	/**
	 * Solo el contenido de la respuesta: la conversación y sus mensajes se crean siempre nuevos.
	 * Los textos van emparejados por posición con las fuentes; sin ellos, una respuesta servida
	 * desde caché aparecería en la bandeja sin los fragmentos con los que se redactó.
	 */
	public record Guardada(String respuesta, java.util.List<RagService.Fuente> fuentes,
			java.util.List<String> textos, boolean sinInformacion) {
	}

	private record Entrada(Guardada valor, long expiraEn) {
	}

	private final Map<String, Entrada> cache = new ConcurrentHashMap<>();
	private final long vidaMs;

	public CacheRespuestas(@Value("${vera.rag.cache-minutos:30}") long minutos) {
		this.vidaMs = minutos * 60_000;
	}

	public Optional<Guardada> buscar(UUID tenantId, String pregunta) {
		String clave = clave(tenantId, pregunta);
		Entrada entrada = cache.get(clave);
		if (entrada == null) {
			return Optional.empty();
		}
		if (System.currentTimeMillis() > entrada.expiraEn()) {
			cache.remove(clave);
			return Optional.empty();
		}
		return Optional.of(entrada.valor());
	}

	public void guardar(UUID tenantId, String pregunta, Guardada valor) {
		cache.put(clave(tenantId, pregunta), new Entrada(valor, System.currentTimeMillis() + vidaMs));
	}

	// Al cambiar las fuentes las respuestas viejas dejan de valer.
	public void invalidar(UUID tenantId) {
		cache.keySet().removeIf(k -> k.startsWith(tenantId.toString()));
	}

	private String clave(UUID tenantId, String pregunta) {
		return tenantId + "|" + pregunta.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
	}

}
