package com.vera.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.vera.dominio.CorridaEval;

/**
 * El examen que corre en cada push, sin clave de IA.
 *
 * <p><b>Qué vigila:</b> que el conjunto esencial se pueda sembrar desde el repo y que el examen
 * corra de principio a fin sin dejar casos sin evaluar. Si alguien rompe una migración, el
 * troceado, el sembrado o el propio evaluador, el build se cae aquí.
 *
 * <p><b>Qué NO vigila:</b> la calidad de las respuestas. Sin clave, los embeddings no son
 * semánticos y la búsqueda recupera 0 de 4 — medido, no supuesto. Poner un umbral de calidad
 * sobre eso sería un candado que no cierra nada. La calidad la mide {@link ExamenRealTest}.
 */
@TestPropertySource(properties = {
		// Sin clave, aunque la máquina del desarrollador tenga una: esta prueba mide el mecanismo,
		// no el modelo, y debe dar lo mismo aquí que en CI.
		"vera.gemini.api-key=",
		// Las pausas existen para no toparse con el free tier; sin API externa no hay a quién cuidar.
		"vera.eval.pausa-ms=0",
})
class ExamenEnCiTest extends ExamenBase {

	@Test
	@DisplayName("el conjunto esencial se siembra desde el repo y el examen corre entero")
	void elExamenCorreEntero() {
		CorridaEval corrida = evaluador.ejecutar("esencial");

		assertThat(corrida.getModelo() == null || corrida.getModelo().startsWith("local"))
				.as("esta prueba debe correr sin clave: con modelo de pago mediría otra cosa")
				.isTrue();

		assertThat(corrida.getConjunto()).isEqualTo("esencial");
		assertThat(corrida.getTotal()).isEqualTo(CASOS_ESENCIALES);

		// Un examen a medias no es un examen: es lo primero que hay que exigir siempre.
		assertThat(corrida.getErrores()).as("casos que no se pudieron evaluar").isZero();

		// Cada caso deja una nota: los positivos de fidelidad, los negativos de abstención.
		assertThat(corrida.getFidelidadBase() + corrida.getAbstencionBase())
				.as("casos que dejaron alguna nota")
				.isEqualTo(CASOS_ESENCIALES);
	}

}
