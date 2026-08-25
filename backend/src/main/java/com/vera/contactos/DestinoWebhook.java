package com.vera.contactos;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

import com.vera.error.ExcepcionNegocio;

/**
 * Comprueba a dónde se puede llamar cuando entra un contacto.
 *
 * <p>La URL la escribe el dueño de un negocio, y desde el servidor de Vera. Sin este filtro,
 * cualquier cliente podría apuntarla a la red interna — {@code localhost}, la 10.x, o el
 * 169.254.169.254 que en la nube sirve las credenciales de la máquina — y usar a Vera de
 * puente para leer lo que él no alcanza. Es un SSRF de manual.
 */
public final class DestinoWebhook {

	private DestinoWebhook() {
	}

	/**
	 * @param permitirLocales solo en desarrollo, para poder apuntar a un servidor de la propia
	 *                        máquina. En producción va apagado.
	 */
	public static void exigirValido(String url, boolean permitirLocales) {
		if (url == null || url.isBlank()) {
			return;
		}
		URI uri;
		try {
			uri = URI.create(url.trim());
		} catch (IllegalArgumentException e) {
			throw ExcepcionNegocio.conflicto("la direccion del webhook no es una URL valida");
		}
		String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!esquema.equals("http") && !esquema.equals("https")) {
			throw ExcepcionNegocio.conflicto("el webhook tiene que empezar por http:// o https://");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw ExcepcionNegocio.conflicto("la direccion del webhook no tiene servidor");
		}
		if (permitirLocales) {
			return;
		}
		InetAddress destino;
		try {
			destino = InetAddress.getByName(uri.getHost());
		} catch (UnknownHostException e) {
			throw ExcepcionNegocio.conflicto("no se pudo resolver el servidor del webhook");
		}
		if (esInterna(destino)) {
			throw ExcepcionNegocio
					.conflicto("el webhook no puede apuntar a una direccion interna o local");
		}
	}

	/** Se comprueba también justo antes de llamar: entre guardar y enviar, un DNS puede cambiar. */
	public static boolean apuntaAdentro(String url, boolean permitirLocales) {
		if (permitirLocales) {
			return false;
		}
		try {
			return esInterna(InetAddress.getByName(URI.create(url).getHost()));
		} catch (RuntimeException | UnknownHostException e) {
			// Lo que no se puede comprobar no se llama.
			return true;
		}
	}

	private static boolean esInterna(InetAddress destino) {
		return destino.isLoopbackAddress() || destino.isAnyLocalAddress()
				|| destino.isLinkLocalAddress() || destino.isSiteLocalAddress()
				|| destino.isMulticastAddress();
	}

}
