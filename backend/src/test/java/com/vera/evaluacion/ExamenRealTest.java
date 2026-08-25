package com.vera.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.vera.dominio.CorridaEval;

/**
 * El examen de calidad de verdad: con el modelo real, umbral que tumba el build.
 *
 * <p>No corre en cada push a propósito — gasta cuota y tarda un par de minutos. Se prende con
 * {@code VERA_EXAMEN_REAL=1} y una clave de IA configurada: a mano antes de publicar un cambio de
 * prompt, o en un job aparte de CI (programado o manual) con la clave puesta como secreto.
 *
 * <p>Los umbrales son la corrida de referencia documentada en {@code docs/evaluacion/README.md}.
 * Si bajan, algo se rompió; si suben de forma estable, se actualizan a conciencia.
 */
@EnabledIfEnvironmentVariable(named = "VERA_EXAMEN_REAL", matches = "1")
class ExamenRealTest extends ExamenBase {

	private static final int RECUPERACION_MINIMA = 4;
	private static final int FIDELIDAD_MINIMA = 3;

	@Test
	@DisplayName("con el modelo real el examen no baja del umbral")
	void elExamenRealNoBajaDelUmbral() {
		CorridaEval corrida = evaluador.ejecutar("esencial");

		assertThat(corrida.getModelo())
				.as("sin clave configurada esta prueba no mide nada; ponla o no la prendas")
				.doesNotStartWith("local");

		assertThat(corrida.getErrores()).as("casos que no se pudieron evaluar").isZero();
		assertThat(corrida.getTotal()).isEqualTo(CASOS_ESENCIALES);

		assertThat(corrida.getRecuperacionOk())
				.as("documentos correctos recuperados de %d", corrida.getRecuperacionBase())
				.isGreaterThanOrEqualTo(RECUPERACION_MINIMA);

		assertThat(corrida.getFidelidadOk())
				.as("respuestas que dicen lo esperado, de %d", corrida.getFidelidadBase())
				.isGreaterThanOrEqualTo(FIDELIDAD_MINIMA);

		// Inventar es el peor fallo posible: aquí no se admite ni uno.
		assertThat(corrida.getAbstencionOk())
				.as("preguntas fuera del corpus donde el asistente debia callarse")
				.isEqualTo(corrida.getAbstencionBase());
	}

}
