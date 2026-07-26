package com.vera.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionChat {

	private static final Logger log = LoggerFactory.getLogger(ConfiguracionChat.class);

	@Bean
	public ProveedorChat proveedorChat(@Value("${vera.gemini.api-key:}") String clave,
			@Value("${vera.gemini.modelo-chat:gemini-flash-latest}") String modelo) {
		if (clave == null || clave.isBlank()) {
			log.warn("SIN clave de Gemini: el asistente no redacta respuestas, solo devuelve fragmentos.");
			return new ChatLocal();
		}
		// Por defecto un alias, no una version fija: los modelos con numero se retiran y dejan 404.
		log.info("Chat con Gemini activado, modelo {}", modelo);
		return new ChatGemini(clave, modelo);
	}

}
