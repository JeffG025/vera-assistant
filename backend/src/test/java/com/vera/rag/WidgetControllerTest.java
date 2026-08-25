package com.vera.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.vera.error.ExcepcionNegocio;

/**
 * De este booleano cuelga lo que el visitante ve tras un fallo: un boton de reintento o una
 * entrada cerrada. La cuota diaria no se puede agotar a proposito para probarlo de verdad,
 * asi que el mapeo se comprueba aqui.
 */
class WidgetControllerTest {

	@Test
	@DisplayName("la cuota diaria del proveedor no se reintenta")
	void laCuotaDiariaNoSeReintenta() {
		assertThat(WidgetController.esReintentable(new ExcepcionCuotaLlm(true, "se agoto la cuota diaria")))
				.isFalse();
	}

	@Test
	@DisplayName("el tope por minuto si se reintenta: se arregla esperando")
	void elTopePorMinutoSiSeReintenta() {
		assertThat(WidgetController.esReintentable(new ExcepcionCuotaLlm(false, "espera un momento")))
				.isTrue();
	}

	@Test
	@DisplayName("cualquier otro fallo se reintenta: no hay motivo para cerrarle la puerta al visitante")
	void elRestoSeReintenta() {
		assertThat(WidgetController.esReintentable(
				new ExcepcionNegocio(HttpStatus.TOO_MANY_REQUESTS, "demasiadas preguntas seguidas")))
						.isTrue();
		assertThat(WidgetController.esReintentable(new IllegalStateException("Gemini no devolvio texto")))
				.isTrue();
	}

}
