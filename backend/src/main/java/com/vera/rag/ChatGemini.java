package com.vera.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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

			// El free tier corta por minuto. Reintentos cortos: alguien esperando en un chat no
			// aguanta esperas largas, pero un respiro de un segundo salva la mayoria de los topes.
			long espera = 700;
			for (int intento = 1; intento <= 3; intento++) {
				HttpResponse<T> respuesta = http.send(peticion, manejador);
				int codigo = respuesta.statusCode();

				if (codigo < 400) {
					return respuesta;
				}
				String cuerpoError = leerCuerpo(respuesta);

				// Un 429 por cuota diaria no se arregla esperando unos segundos: reintentar solo
				// alarga la espera y deja al que llama sin saber por que fallo de verdad.
				if (codigo == 429 && esCuotaDiaria(cuerpoError)) {
					throw new ExcepcionCuotaLlm(true,
							"se agoto la cuota diaria del free tier de Gemini para " + modelo);
				}
				if ((codigo == 429 || codigo >= 500) && intento < 3) {
					Thread.sleep(espera);
					espera *= 2;
					continue;
				}
				if (codigo == 429) {
					throw new ExcepcionCuotaLlm(false,
							"el asistente esta saturado en este momento; intenta de nuevo en unos segundos");
				}
				throw new IllegalStateException(
						"Gemini respondio " + codigo + " con el modelo " + modelo + motivo(cuerpoError));
			}
			throw new IllegalStateException("Gemini no respondio tras 3 intentos");
		} catch (IOException e) {
			throw new IllegalStateException("fallo de red al hablar con Gemini: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrumpido al generar la respuesta", e);
		}
	}

	// Los dos 429 se ven iguales salvo por el id de cuota: el diario obliga a parar, el de
	// minuto solo a esperar.
	static boolean esCuotaDiaria(String cuerpoError) {
		return cuerpoError.contains("PerDay");
	}

	// El cuerpo del error dice que cuota se topo; segun el manejador llega como texto o como lineas.
	private String leerCuerpo(HttpResponse<?> respuesta) {
		Object cuerpo = respuesta.body();
		if (cuerpo instanceof String texto) {
			return texto;
		}
		if (cuerpo instanceof Stream<?> lineas) {
			return lineas.map(String::valueOf).collect(Collectors.joining("\n"));
		}
		return "";
	}

	// Saca el mensaje que manda la API para no reportar solo un numero de error.
	private String motivo(String cuerpo) {
		try {
			String mensaje = json.readTree(cuerpo).path("error").path("message").asText("");
			return mensaje.isBlank() ? "" : ": " + mensaje;
		} catch (IOException e) {
			return "";
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
