package com.vera.contactos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vera.error.ExcepcionNegocio;

/**
 * La URL del webhook la escribe un cliente y la llamada sale del servidor de Vera. Sin este
 * filtro, cualquiera podría usar a Vera de puente para alcanzar la red interna.
 */
class DestinoWebhookTest {

	private static void validar(String url) {
		DestinoWebhook.exigirValido(url, false);
	}

	// TEST-NET-3, reservado para documentacion: es publica y se resuelve sin DNS, asi que la
	// prueba vale igual con la maquina desconectada.
	private static final String PUBLICA = "https://203.0.113.10/vera";

	@Test
	@DisplayName("una direccion publica normal se acepta")
	void laPublicaPasa() {
		assertThatCode(() -> validar(PUBLICA)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("un servidor que no resuelve se rechaza al guardarlo, con su motivo")
	void elQueNoResuelve() {
		assertThatThrownBy(() -> validar("https://esto-no-existe-en-ningun-dns.invalid/x"))
				.isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("resolver");
	}

	@Test
	@DisplayName("vacio o nulo no es un error: significa que no quiere webhook")
	void vacioNoEsError() {
		assertThatCode(() -> validar(null)).doesNotThrowAnyException();
		assertThatCode(() -> validar("   ")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("localhost no, en ninguna de sus formas")
	void nadaDeLocalhost() {
		assertThatThrownBy(() -> validar("http://localhost:9000/x"))
				.isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("interna");
		assertThatThrownBy(() -> validar("http://127.0.0.1/x")).isInstanceOf(ExcepcionNegocio.class);
		assertThatThrownBy(() -> validar("http://[::1]/x")).isInstanceOf(ExcepcionNegocio.class);
		assertThatThrownBy(() -> validar("http://0.0.0.0/x")).isInstanceOf(ExcepcionNegocio.class);
	}

	@Test
	@DisplayName("la red privada tampoco: 10.x, 192.168.x, 172.16.x")
	void nadaDeRedPrivada() {
		assertThatThrownBy(() -> validar("http://10.0.0.5/x")).isInstanceOf(ExcepcionNegocio.class);
		assertThatThrownBy(() -> validar("http://192.168.1.1/x")).isInstanceOf(ExcepcionNegocio.class);
		assertThatThrownBy(() -> validar("http://172.16.0.9/x")).isInstanceOf(ExcepcionNegocio.class);
	}

	@Test
	@DisplayName("el 169.254.169.254 de la nube, que sirve las credenciales de la maquina")
	void nadaDeMetadatos() {
		assertThatThrownBy(() -> validar("http://169.254.169.254/latest/meta-data/"))
				.isInstanceOf(ExcepcionNegocio.class);
	}

	@Test
	@DisplayName("solo http y https: nada de file://, gopher:// ni ftp://")
	void soloHttp() {
		assertThatThrownBy(() -> validar("file:///etc/passwd")).isInstanceOf(ExcepcionNegocio.class)
				.hasMessageContaining("http");
		assertThatThrownBy(() -> validar("ftp://ejemplo.com/x")).isInstanceOf(ExcepcionNegocio.class);
	}

	@Test
	@DisplayName("una URL sin servidor no vale")
	void sinServidor() {
		assertThatThrownBy(() -> validar("https:///sin-host")).isInstanceOf(ExcepcionNegocio.class);
	}

	@Test
	@DisplayName("en desarrollo se puede permitir lo local a proposito")
	void enDesarrolloSiSePermite() {
		assertThatCode(() -> DestinoWebhook.exigirValido("http://localhost:9000/x", true))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("la segunda comprobacion, la de justo antes de llamar, dice lo mismo")
	void laComprobacionDeEnvio() {
		assertThat(DestinoWebhook.apuntaAdentro("http://127.0.0.1/x", false)).isTrue();
		assertThat(DestinoWebhook.apuntaAdentro(PUBLICA, false)).isFalse();
		assertThat(DestinoWebhook.apuntaAdentro("http://127.0.0.1/x", true)).isFalse();
	}

	@Test
	@DisplayName("lo que no se puede resolver no se llama")
	void loDudosoNoSeLlama() {
		assertThat(DestinoWebhook.apuntaAdentro("no-es-una-url", false)).isTrue();
	}

}
