package com.vera.fuentes;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Cliente de la API de embeddings de Google AI Studio, por lotes y con reintentos. */
public class EmbeddingsGemini implements ProveedorEmbeddings {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingsGemini.class);

	private static final String MODELO = "text-embedding-004";
	private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/"
			+ MODELO + ":batchEmbedContents";
	private static final int LOTE = 100;
	private static final int INTENTOS = 4;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	private final String clave;
	// Mapper propio: Boot 4 no expone un ObjectMapper como bean y este solo habla con Gemini.
	private final ObjectMapper json = new ObjectMapper();

	public EmbeddingsGemini(String clave) {
		this.clave = clave;
	}

	@Override
	public String modelo() {
		return MODELO;
	}

	@Override
	public List<float[]> generar(List<String> textos) {
		List<float[]> salida = new ArrayList<>(textos.size());
		for (int desde = 0; desde < textos.size(); desde += LOTE) {
			salida.addAll(pedirLote(textos.subList(desde, Math.min(desde + LOTE, textos.size()))));
		}
		return salida;
	}

	private List<float[]> pedirLote(List<String> textos) {
		ObjectNode cuerpo = json.createObjectNode();
		ArrayNode peticiones = cuerpo.putArray("requests");
		for (String texto : textos) {
			ObjectNode peticion = peticiones.addObject();
			peticion.put("model", "models/" + MODELO);
			peticion.putObject("content").putArray("parts").addObject().put("text", texto);
		}

		// El free tier corta por minuto: se reintenta con espera creciente en vez de perder el lote.
		long espera = 1000;
		for (int intento = 1; intento <= INTENTOS; intento++) {
			try {
				HttpRequest peticion = HttpRequest.newBuilder(URI.create(URL + "?key=" + clave))
						.header("Content-Type", "application/json")
						.timeout(Duration.ofSeconds(60))
						.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
						.build();
				HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());

				if (respuesta.statusCode() == 429 || respuesta.statusCode() >= 500) {
					log.warn("Gemini respondio {} (intento {}/{})", respuesta.statusCode(), intento, INTENTOS);
					Thread.sleep(espera);
					espera *= 2;
					continue;
				}
				if (respuesta.statusCode() >= 400) {
					throw new IllegalStateException("Gemini rechazo la peticion: " + respuesta.statusCode());
				}
				return leer(respuesta.body(), textos.size());
			} catch (IOException e) {
				log.warn("fallo de red hacia Gemini (intento {}/{}): {}", intento, INTENTOS, e.getMessage());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrumpido al generar embeddings", e);
			}
		}
		throw new IllegalStateException("no se pudieron generar embeddings tras " + INTENTOS + " intentos");
	}

	private List<float[]> leer(String cuerpo, int esperados) throws IOException {
		JsonNode raiz = json.readTree(cuerpo);
		JsonNode lista = raiz.path("embeddings");
		if (!lista.isArray() || lista.size() != esperados) {
			throw new IllegalStateException("Gemini devolvio " + lista.size() + " embeddings de " + esperados);
		}
		List<float[]> salida = new ArrayList<>(esperados);
		for (JsonNode nodo : lista) {
			JsonNode valores = nodo.path("values");
			if (valores.size() != DIMENSIONES) {
				throw new IllegalStateException(
						"el modelo devolvio " + valores.size() + " dimensiones y la columna espera " + DIMENSIONES);
			}
			float[] vector = new float[DIMENSIONES];
			for (int i = 0; i < DIMENSIONES; i++) {
				vector[i] = (float) valores.get(i).asDouble();
			}
			salida.add(vector);
		}
		return salida;
	}

}
