package com.vera.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El unico camino que no se puede probar contra la API real sin quemar la cuota del dia:
 * distinguir el 429 diario del 429 por minuto. Los cuerpos son muestras reales de Gemini.
 */
class ChatGeminiTest {

	private static final String CUOTA_DIARIA = """
			{"error":{"code":429,"message":"You exceeded your current quota.",
			"status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
			"violations":[{"quotaMetric":"generativelanguage.googleapis.com/generate_content_free_tier_requests",
			"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}
			""";

	private static final String CUOTA_POR_MINUTO = """
			{"error":{"code":429,"message":"You exceeded your current quota.",
			"status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
			"violations":[{"quotaMetric":"generativelanguage.googleapis.com/generate_content_free_tier_requests",
			"quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier"}]},
			{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"27s"}]}}
			""";

	@Test
	@DisplayName("la cuota diaria se reconoce y obliga a parar")
	void reconoceLaCuotaDiaria() {
		assertThat(ChatGemini.esCuotaDiaria(CUOTA_DIARIA)).isTrue();
	}

	@Test
	@DisplayName("el tope por minuto no se confunde con el diario: ese si se reintenta")
	void noConfundeElTopePorMinuto() {
		assertThat(ChatGemini.esCuotaDiaria(CUOTA_POR_MINUTO)).isFalse();
	}

	@Test
	@DisplayName("un cuerpo vacio o ilegible no se toma por cuota diaria")
	void cuerpoVacioNoEsCuotaDiaria() {
		assertThat(ChatGemini.esCuotaDiaria("")).isFalse();
	}

}
