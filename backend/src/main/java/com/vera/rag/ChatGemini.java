package com.vera.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ChatGemini implements ProveedorChat {

	private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	private final ObjectMapper json = new ObjectMapper();
	private final String clave;
	private final String modelo;

	public ChatGemini(String clave, String modelo) {
		this.clave = clave;
		this.modelo = modelo;
	}

	@Override
	public String modelo() {
		return modelo;
	}

	@Override
	public Respuesta responder(String sistema, List<Turno> historial, String pregunta) {
		HttpResponse<String> respuesta = enviar(sistema, historial, pregunta, false,
				HttpResponse.BodyHandlers.ofString());
		try {
			JsonNode raiz = json.readTree(respuesta.body());
			String texto = extraerTexto(raiz);
			if (texto.isBlank()) {
				throw new IllegalStateException("Gemini no devolvio texto (posible filtro de seguridad)");
			}
			return new Respuesta(texto.strip(), raiz.path("usageMetadata").path("totalTokenCount").asInt(0));
		} catch (IOException e) {
			throw new IllegalStateException("respuesta ilegible de Gemini: " + e.getMessage(), e);
		}
	}

	@Override
	public Respuesta responderEnStream(String sistema, List<Turno> historial, String pregunta,
			Consumer<String> alRecibir) {
		HttpResponse<Stream<String>> respuesta = enviar(sistema, historial, pregunta, true,
				HttpResponse.BodyHandlers.ofLines());

		StringBuilder completo = new StringBuilder();
		int[] tokens = { 0 };
		// El formato SSE manda una linea "data: {json}" por pedazo; lo demas son lineas vacias.
		respuesta.body().filter(linea -> linea.startsWith("data:")).forEach(linea -> {
			try {
				JsonNode raiz = json.readTree(linea.substring(5).strip());
				String pedazo = extraerTexto(raiz);
				if (!pedazo.isEmpty()) {
					completo.append(pedazo);
					alRecibir.accept(pedazo);
				}
				int total = raiz.path("usageMetadata").path("totalTokenCount").asInt(0);
				if (total > 0) {
					tokens[0] = total;
				}
			} catch (IOException e) {
				// Un pedazo ilegible no debe tumbar la respuesta entera.
			}
		});

		if (completo.isEmpty()) {
			throw new IllegalStateException("Gemini no devolvio texto en el stream");
		}
		return new Respuesta(completo.toString().strip(), tokens[0]);
	}

	private <T> HttpResponse<T> enviar(String sistema, List<Turno> historial, String pregunta,
			boolean stream, HttpResponse.BodyHandler<T> manejador) {
		try {
			ObjectNode cuerpo = json.createObjectNode();
			cuerpo.putObject("systemInstruction").putArray("parts").addObject().put("text", sistema);

			ArrayNode contenidos = cuerpo.putArray("contents");
			for (Turno turno : historial) {
				ObjectNode nodo = contenidos.addObject();
				nodo.put("role", turno.rol());
				nodo.putArray("parts").addObject().put("text", turno.texto());
			}
			ObjectNode ultimo = contenidos.addObject();
			ultimo.put("role", "user");
			ultimo.putArray("parts").addObject().put("text", pregunta);

			// Temperatura baja: aqui no se quiere creatividad, se quiere fidelidad al contexto.
			ObjectNode config = cuerpo.putObject("generationConfig");
			config.put("temperature", 0.2);
			config.put("maxOutputTokens", 1200);

			String ruta = stream ? ":streamGenerateContent?alt=sse&key=" : ":generateContent?key=";
			HttpRequest peticion = HttpRequest.newBuilder(URI.create(BASE + modelo + ruta + clave))
					.header("Content-Type", "application/json")
					.timeout(Duration.ofSeconds(90))
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
					.build();

			HttpResponse<T> respuesta = http.send(peticion, manejador);
			if (respuesta.statusCode() >= 400) {
				throw new IllegalStateException(
						"Gemini respondio " + respuesta.statusCode() + " con el modelo " + modelo);
			}
			return respuesta;
		} catch (IOException e) {
			throw new IllegalStateException("fallo de red al hablar con Gemini: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrumpido al generar la respuesta", e);
		}
	}

	private String extraerTexto(JsonNode raiz) {
		StringBuilder texto = new StringBuilder();
		for (JsonNode parte : raiz.path("candidates").path(0).path("content").path("parts")) {
			texto.append(parte.path("text").asText(""));
		}
		return texto.toString();
	}

}
