package com.vera.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vera.dominio.ResultadoEval;
import com.vera.evaluacion.EvaluadorService.Veredicto;

/**
 * De este veredicto cuelga la comparación entre corridas: si clasifica mal un caso, el panel
 * dirá que un cambio de prompt mejoró cuando en realidad rompió algo.
 */
class VeredictoTest {

	private ResultadoEval caso(boolean debeSaber, boolean sinInformacion, Boolean recuperacion,
			Boolean fidelidad, String motivo) {
		ResultadoEval r = new ResultadoEval();
		r.setPregunta("pregunta");
		r.setDebeSaber(debeSaber);
		r.setSinInformacion(sinInformacion);
		r.setRecuperacionOk(recuperacion);
		r.setFidelidadOk(fidelidad);
		r.setMotivo(motivo);
		return r;
	}

	@Test
	@DisplayName("positivo con recuperacion y fidelidad en verde es acierto")
	void positivoBien() {
		assertThat(EvaluadorService.veredicto(caso(true, false, true, true, "dice lo mismo")))
				.isEqualTo(Veredicto.ACIERTO);
	}

	@Test
	@DisplayName("positivo que trajo la fuente pero contesto otra cosa es fallo")
	void positivoConFidelidadMala() {
		assertThat(EvaluadorService.veredicto(caso(true, false, true, false, "no coincide")))
				.isEqualTo(Veredicto.FALLO);
	}

	@Test
	@DisplayName("positivo que no encontro la fuente es fallo aunque el juez lo aprobara")
	void positivoConRecuperacionMala() {
		assertThat(EvaluadorService.veredicto(caso(true, false, false, true, "dice lo mismo")))
				.isEqualTo(Veredicto.FALLO);
	}

	@Test
	@DisplayName("negativo que se calla es acierto; el que contesta algo, invento")
	void negativos() {
		assertThat(EvaluadorService.veredicto(caso(false, true, null, null, "admitio no saber")))
				.isEqualTo(Veredicto.ACIERTO);
		assertThat(EvaluadorService.veredicto(caso(false, false, null, null, "respondio algo")))
				.isEqualTo(Veredicto.FALLO);
	}

	@Test
	@DisplayName("un caso caido o que no llego a correr es ERROR, no un fallo del asistente")
	void casosCaidos() {
		assertThat(EvaluadorService.veredicto(caso(true, false, null, null, "no se pudo evaluar: 503")))
				.isEqualTo(Veredicto.ERROR);
		assertThat(EvaluadorService.veredicto(
				caso(true, false, null, null, "no se ejecuto: la corrida se corto al agotarse la cuota diaria")))
				.isEqualTo(Veredicto.ERROR);
	}

	@Test
	@DisplayName("una metrica que no aplica (null) no cuenta como fallo")
	void metricaQueNoAplica() {
		assertThat(EvaluadorService
				.veredicto(caso(true, false, true, null, "sin respuesta esperada: no se puede juzgar")))
				.isEqualTo(Veredicto.ACIERTO);
	}

}
