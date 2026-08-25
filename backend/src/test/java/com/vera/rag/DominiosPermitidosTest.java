package com.vera.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Lo unico que impide que alguien pegue el script de otro negocio en su propio sitio. */
class DominiosPermitidosTest {

	private static boolean desde(String lista, String origen) {
		return DominiosPermitidos.permite(lista, origen, null);
	}

	@Nested
	@DisplayName("lo que debe dejar pasar")
	class Permite {

		@Test
		@DisplayName("el dominio dado de alta, con o sin puerto")
		void elDominioDeAlta() {
			assertThat(desde("mitienda.com", "https://mitienda.com")).isTrue();
			assertThat(desde("mitienda.com", "https://mitienda.com:8443")).isTrue();
			assertThat(desde("mitienda.com", "http://mitienda.com")).isTrue();
		}

		@Test
		@DisplayName("cualquiera de la lista, sin pelearse con los espacios")
		void cualquieraDeLaLista() {
			assertThat(desde("uno.com,  www.mitienda.com , tres.com", "https://www.mitienda.com")).isTrue();
		}

		@Test
		@DisplayName("mayusculas: los dominios no las distinguen")
		void sinDistinguirMayusculas() {
			assertThat(desde("MiTienda.com", "https://MITIENDA.COM")).isTrue();
		}

		@Test
		@DisplayName("lo que la gente pega de verdad: con esquema, con barra final")
		void loQueLaGentePega() {
			assertThat(desde("https://mitienda.com/", "https://mitienda.com")).isTrue();
		}

		@Test
		@DisplayName("el comodin cubre subdominios y tambien el dominio pelado")
		void elComodin() {
			assertThat(desde("*.mitienda.com", "https://tienda.mitienda.com")).isTrue();
			assertThat(desde("*.mitienda.com", "https://a.b.mitienda.com")).isTrue();
			assertThat(desde("*.mitienda.com", "https://mitienda.com")).isTrue();
		}

		@Test
		@DisplayName("localhost siempre, aunque no haya nada dado de alta: si no, no se puede ni probar")
		void localhostSiempre() {
			assertThat(desde(null, "http://localhost:5178")).isTrue();
			assertThat(desde("", "http://127.0.0.1:3000")).isTrue();
		}

		@Test
		@DisplayName("sin Origin ni Referer no hay navegador que bloquear: de eso se ocupan cuota y limite")
		void sinCabeceras() {
			assertThat(DominiosPermitidos.permite("mitienda.com", null, null)).isTrue();
			assertThat(DominiosPermitidos.permite(null, null, null)).isTrue();
		}

		@Test
		@DisplayName("si no vino Origin se mira el Referer")
		void caeAlReferente() {
			assertThat(DominiosPermitidos.permite("mitienda.com", null, "https://mitienda.com/productos/1"))
					.isTrue();
		}

	}

	@Nested
	@DisplayName("lo que debe frenar")
	class Frena {

		@Test
		@DisplayName("un sitio ajeno con la clave copiada")
		void elSitioAjeno() {
			assertThat(desde("mitienda.com", "https://elladron.com")).isFalse();
		}

		@Test
		@DisplayName("sin dominios de alta no responde en ningun sitio publico")
		void listaVacia() {
			assertThat(desde(null, "https://mitienda.com")).isFalse();
			assertThat(desde("   ", "https://mitienda.com")).isFalse();
		}

		@Test
		@DisplayName("el subdominio no entra si solo se dio de alta el dominio pelado")
		void subdominioNoImplicito() {
			assertThat(desde("mitienda.com", "https://blog.mitienda.com")).isFalse();
		}

		@Test
		@DisplayName("el truco del sufijo: elladron-mitienda.com no es mitienda.com")
		void elTrucoDelSufijo() {
			assertThat(desde("mitienda.com", "https://elladron-mitienda.com")).isFalse();
			assertThat(desde("*.mitienda.com", "https://elladronmitienda.com")).isFalse();
		}

		@Test
		@DisplayName("el truco del prefijo: mitienda.com.elladron.net tampoco")
		void elTrucoDelPrefijo() {
			assertThat(desde("mitienda.com", "https://mitienda.com.elladron.net")).isFalse();
			assertThat(desde("*.mitienda.com", "https://mitienda.com.elladron.net")).isFalse();
		}

		@Test
		@DisplayName("un origen opaco (sandbox, file://) llega como la cadena \"null\"")
		void origenOpaco() {
			assertThat(desde("mitienda.com", "null")).isFalse();
		}

		@Test
		@DisplayName("localhost dentro de un dominio ajeno no cuela")
		void localhostFalsificado() {
			assertThat(desde("mitienda.com", "https://localhost.elladron.com")).isFalse();
		}

	}

}
