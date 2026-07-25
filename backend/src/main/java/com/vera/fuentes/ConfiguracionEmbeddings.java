package com.vera.fuentes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionEmbeddings {

	private static final Logger log = LoggerFactory.getLogger(ConfiguracionEmbeddings.class);

	// Sin clave la app arranca igual y el pipeline se puede probar; con clave usa Gemini de verdad.
	@Bean
	public ProveedorEmbeddings proveedorEmbeddings(@Value("${vera.gemini.api-key:}") String clave) {
		if (clave == null || clave.isBlank()) {
			log.warn("SIN clave de Gemini: se usan embeddings locales deterministas. "
					+ "Sirven para desarrollo y pruebas, NO son semanticos. "
					+ "Pon vera.gemini.api-key en application-local.properties para usar el modelo real.");
			return new EmbeddingsLocales();
		}
		log.info("Embeddings con Gemini activados");
		return new EmbeddingsGemini(clave);
	}

}
