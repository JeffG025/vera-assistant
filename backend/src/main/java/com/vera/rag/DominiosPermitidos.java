package com.vera.rag;

import java.net.URI;
import java.util.Locale;

/**
 * Decide si la clave publica de un negocio puede usarse desde el sitio que la esta pidiendo.
 *
 * <p>No es autenticacion y no pretende serlo: la clave viaja en el HTML de una pagina publica y
 * quien quiera puede llamar a la API sin navegador y sin cabeceras. Lo que esto evita es lo que
 * de verdad pasa \u2014 que alguien copie el script ajeno y lo pegue en su sitio, gastando la cuota
 * del negocio dueno de la clave.
 */
public final class DominiosPermitidos {

	private DominiosPermitidos() {
	}

	/**
	 * @param lista  dominios del negocio, separados por comas; vacia significa ninguno
	 * @param origen cabecera {@code Origin}, o {@code null} si no vino
	 * @param referente cabecera {@code Referer}, que se usa solo si no hubo {@code Origin}
	 */
	public static boolean permite(String lista, String origen, String referente) {
		String cabecera = origen != null && !origen.isBlank() ? origen : referente;

		// "null" no es la ausencia de la cabecera: es lo que manda un navegador desde un iframe
		// con sandbox o una pagina file://. Hay navegador, pero su origen no se puede comprobar.
		if (cabecera != null && cabecera.trim().equalsIgnoreCase("null")) {
			return false;
		}
		String anfitrion = anfitrionDe(cabecera);

		// Sin ninguna de las dos cabeceras no hay navegador al otro lado, asi que no hay sitio
		// ajeno que bloquear: es una llamada directa, y de esas ya se encargan cuota y limite.
		if (anfitrion == null) {
			return true;
		}
		// El desarrollo y las pruebas locales no deberian obligar a dar de alta nada.
		if (esLocal(anfitrion)) {
			return true;
		}
		if (lista == null || lista.isBlank()) {
			return false;
		}
		for (String entrada : lista.split(",")) {
			if (coincide(entrada.trim(), anfitrion)) {
				return true;
			}
		}
		return false;
	}

	private static boolean coincide(String patron, String anfitrion) {
		// Se acepta lo que la gente pega de verdad: "https://mitienda.com/", "mitienda.com:443"...
		String limpio = anfitrionDe(patron);
		if (limpio == null || limpio.isEmpty()) {
			return false;
		}
		if (limpio.startsWith("*.")) {
			String raiz = limpio.substring(2);
			// El comodin cubre tambien el dominio pelado: quien escribe "*.mitienda.com" no
			// espera que "mitienda.com" se quede fuera.
			return anfitrion.equals(raiz) || anfitrion.endsWith("." + raiz);
		}
		return anfitrion.equals(limpio);
	}

	private static boolean esLocal(String anfitrion) {
		return anfitrion.equals("localhost") || anfitrion.equals("127.0.0.1")
				|| anfitrion.equals("[::1]") || anfitrion.endsWith(".localhost");
	}

	/** Se queda con el anfitrion, venga como origen completo o como dominio pelado. */
	private static String anfitrionDe(String valor) {
		if (valor == null || valor.isBlank() || valor.equals("null")) {
			return null;
		}
		String texto = valor.trim().toLowerCase(Locale.ROOT);
		try {
			URI uri = URI.create(texto.contains("://") ? texto : "x://" + texto);
			String anfitrion = uri.getHost();
			if (anfitrion != null) {
				return anfitrion;
			}
		} catch (IllegalArgumentException e) {
			// Cae al camino de abajo: un patron con comodin no es una URI valida.
		}
		// URI.getHost() devuelve null con "*.mitienda.com", asi que se recorta a mano.
		String sinEsquema = texto.contains("://") ? texto.substring(texto.indexOf("://") + 3) : texto;
		int corte = sinEsquema.indexOf('/');
		if (corte >= 0) {
			sinEsquema = sinEsquema.substring(0, corte);
		}
		corte = sinEsquema.lastIndexOf(':');
		if (corte > 0 && sinEsquema.indexOf(']') < corte) {
			sinEsquema = sinEsquema.substring(0, corte);
		}
		return sinEsquema.isBlank() ? null : sinEsquema;
	}

}
