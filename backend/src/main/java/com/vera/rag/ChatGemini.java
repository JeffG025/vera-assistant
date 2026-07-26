package com.vera.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
	public Respuesta responder(String sistema, String pregunta) {
		try {
			ObjectNode cuerpo = json.createObjectNode();
			cuerpo.putObject("systemInstruction").putArray("parts").addObject().put("text", sistema);
			ObjectNode turno = cuerpo.putArray("contents").addObject();
			turno.put("role", "user");
			turno.putArray("parts").addObject().put("text", pregunta);

			// Temperatura baja: aqui no se quiere creatividad, se quiere fidelidad al contexto.
			ObjectNode config = cuerpo.putObject("generationConfig");
			config.put("temperature", 0.2);
			config.put("maxOutputTokens", 1200);

			HttpRequest peticion = HttpRequest.newBuilder(
					URI.create(BASE + modelo + ":generateContent?key=" + clave))
					.header("Content-Type", "application/json")
					.timeout(Duration.ofSeconds(60))
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
					.build();

			HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
			if (respuesta.statusCode() >= 400) {
				// El motivo importa: "modelo retirado" y "sin cuota" se arreglan distinto.
				String motivo = json.readTree(respuesta.body()).path("error").path("message").asText("");
				throw new IllegalStateException(
						"Gemini respondio " + respuesta.statusCode() + " con el modelo " + modelo
								+ (motivo.isBlank() ? "" : ": " + motivo));
			}

			JsonNode raiz = json.readTree(respuesta.body());
			JsonNode partes = raiz.path("candidates").path(0).path("content").path("parts");
			StringBuilder texto = new StringBuilder();
			for (JsonNode parte : partes) {
				texto.append(parte.path("text").asText(""));
			}
			int tokens = raiz.path("usageMetadata").path("totalTokenCount").asInt(0);

			if (texto.isEmpty()) {
				throw new IllegalStateException("Gemini no devolvio texto (posible filtro de seguridad)");
			}
			return new Respuesta(texto.toString().strip(), tokens);
		} catch (IOException e) {
			throw new IllegalStateException("fallo de red al hablar con Gemini: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrumpido al generar la respuesta", e);
		}
	}

}
