package com.vera.fuentes;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.vera.error.ExcepcionNegocio;

/**
 * Rastrea el sitio de un cliente con limites duros: mismo host, tope de paginas y de profundidad,
 * y respeto a robots.txt. Sin esos frenos un rastreo puede no terminar nunca.
 */
@Service
public class RastreadorService {

	private static final Logger log = LoggerFactory.getLogger(RastreadorService.class);

	public static final int MAX_PAGINAS = 40;
	public static final int MAX_PROFUNDIDAD = 3;

	private static final String AGENTE = "VeraBot/0.1";
	private static final Duration ESPERA = Duration.ofSeconds(10);
	private static final int MAX_BYTES = 2_000_000;

	private final HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(ESPERA)
			.build();

	public record Pagina(String url, String titulo, String texto) {
	}

	public List<Pagina> rastrear(String urlInicial, int maxPaginas, int maxProfundidad) {
		URI inicio = normalizar(urlInicial);
		if (inicio == null || inicio.getHost() == null) {
			throw ExcepcionNegocio.conflicto("la URL no es válida");
		}
		if (!inicio.getScheme().startsWith("http")) {
			throw ExcepcionNegocio.conflicto("solo se aceptan direcciones http o https");
		}

		int topePaginas = Math.min(maxPaginas <= 0 ? MAX_PAGINAS : maxPaginas, MAX_PAGINAS);
		int topeProfundidad = Math.min(maxProfundidad <= 0 ? MAX_PROFUNDIDAD : maxProfundidad, MAX_PROFUNDIDAD);

		List<String> vetados = leerRobots(inicio);
		Set<String> vistas = new LinkedHashSet<>();
		List<Pagina> paginas = new ArrayList<>();

		Deque<Object[]> cola = new ArrayDeque<>();
		cola.add(new Object[] { inicio.toString(), 0 });
		// El sitemap ahorra descubrir a ciegas: lo que declare el sitio entra como semilla.
		for (String url : leerSitemap(inicio)) {
			cola.add(new Object[] { url, 1 });
		}

		while (!cola.isEmpty() && paginas.size() < topePaginas) {
			Object[] actual = cola.poll();
			String url = (String) actual[0];
			int profundidad = (int) actual[1];

			if (!vistas.add(url) || profundidad > topeProfundidad) {
				continue;
			}
			if (estaVetada(url, inicio, vetados)) {
				log.debug("robots.txt veta {}", url);
				continue;
			}

			String html = descargar(url);
			if (html == null) {
				continue;
			}

			Document doc = Jsoup.parse(html, url);
			paginas.add(extraer(doc, url));

			if (profundidad < topeProfundidad) {
				for (var enlace : doc.select("a[href]")) {
					URI candidata = normalizar(enlace.absUrl("href"));
					if (candidata != null && mismoHost(candidata, inicio) && !vistas.contains(candidata.toString())) {
						cola.add(new Object[] { candidata.toString(), profundidad + 1 });
					}
				}
			}
		}

		if (paginas.isEmpty()) {
			throw ExcepcionNegocio.conflicto("no se pudo leer ninguna página de ese sitio");
		}
		return paginas;
	}

	private Pagina extraer(Document doc, String url) {
		// Fuera todo lo que se repite en cada pagina: si no, el indice se llena de menus.
		doc.select("script, style, noscript, nav, header, footer, aside, form, svg, iframe").remove();
		var principal = doc.selectFirst("main, article, [role=main]");
		String texto = (principal != null ? principal : doc.body() != null ? doc.body() : doc).text();
		return new Pagina(url, doc.title(), texto.strip());
	}

	private String descargar(String url) {
		try {
			HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", AGENTE)
					.timeout(ESPERA)
					.GET()
					.build();
			HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
			if (respuesta.statusCode() >= 400) {
				return null;
			}
			String tipo = respuesta.headers().firstValue("content-type").orElse("");
			if (!tipo.toLowerCase(Locale.ROOT).contains("html")) {
				return null;
			}
			String cuerpo = respuesta.body();
			return cuerpo.length() > MAX_BYTES ? cuerpo.substring(0, MAX_BYTES) : cuerpo;
		} catch (IOException | InterruptedException | IllegalArgumentException e) {
			log.debug("no se pudo descargar {}: {}", url, e.getMessage());
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return null;
		}
	}

	// Parser deliberadamente simple: solo el grupo User-agent: * y sus Disallow.
	private List<String> leerRobots(URI inicio) {
		List<String> vetados = new ArrayList<>();
		String cuerpo = descargarTexto(inicio.resolve("/robots.txt"));
		if (cuerpo == null) {
			return vetados;
		}
		boolean aplica = false;
		for (String linea : cuerpo.split("\\R")) {
			String limpia = linea.trim();
			int comentario = limpia.indexOf('#');
			if (comentario >= 0) {
				limpia = limpia.substring(0, comentario).trim();
			}
			if (limpia.isEmpty()) {
				continue;
			}
			String bajo = limpia.toLowerCase(Locale.ROOT);
			if (bajo.startsWith("user-agent:")) {
				aplica = limpia.substring(11).trim().equals("*");
			} else if (aplica && bajo.startsWith("disallow:")) {
				String ruta = limpia.substring(9).trim();
				if (!ruta.isEmpty()) {
					vetados.add(ruta);
				}
			}
		}
		return vetados;
	}

	private List<String> leerSitemap(URI inicio) {
		List<String> urls = new ArrayList<>();
		String xml = descargarTexto(inicio.resolve("/sitemap.xml"));
		if (xml == null) {
			return urls;
		}
		var patron = java.util.regex.Pattern.compile("<loc>\\s*(.*?)\\s*</loc>", java.util.regex.Pattern.DOTALL);
		var buscador = patron.matcher(xml);
		while (buscador.find() && urls.size() < MAX_PAGINAS) {
			URI candidata = normalizar(buscador.group(1));
			if (candidata != null && mismoHost(candidata, inicio)) {
				urls.add(candidata.toString());
			}
		}
		return urls;
	}

	private String descargarTexto(URI url) {
		try {
			HttpRequest peticion = HttpRequest.newBuilder(url)
					.header("User-Agent", AGENTE)
					.timeout(ESPERA)
					.GET()
					.build();
			HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
			return respuesta.statusCode() < 400 ? respuesta.body() : null;
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return null;
		}
	}

	private boolean estaVetada(String url, URI inicio, List<String> vetados) {
		String ruta = URI.create(url).getPath();
		if (ruta == null || ruta.isEmpty()) {
			ruta = "/";
		}
		for (String veto : vetados) {
			if (veto.equals("/") || ruta.startsWith(veto)) {
				return true;
			}
		}
		return false;
	}

	private boolean mismoHost(URI candidata, URI inicio) {
		return candidata.getHost() != null && candidata.getHost().equalsIgnoreCase(inicio.getHost());
	}

	// Sin ancla y sin barra final: si no, la misma pagina entra varias veces.
	private URI normalizar(String crudo) {
		try {
			if (crudo == null || crudo.isBlank()) {
				return null;
			}
			URI uri = new URI(crudo.trim()).normalize();
			if (uri.getScheme() == null || uri.getHost() == null) {
				return null;
			}
			String ruta = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
			if (ruta.length() > 1 && ruta.endsWith("/")) {
				ruta = ruta.substring(0, ruta.length() - 1);
			}
			return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), ruta, uri.getQuery(), null);
		} catch (URISyntaxException e) {
			return null;
		}
	}

}
