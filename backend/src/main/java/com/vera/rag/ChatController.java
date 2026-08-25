package com.vera.rag;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vera.tenancy.TenantContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final RagService rag;

	// Hilos virtuales: cada respuesta en streaming ocupa un hilo durante segundos y no
	// conviene amarrar los del servidor mientras el modelo escribe.
	private final ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();

	public ChatController(RagService rag) {
		this.rag = rag;
	}

	public record Pregunta(@NotBlank @Size(max = 1000) String texto, String conversacionId) {
	}

	@PostMapping
	public RagService.Resultado preguntar(@Valid @RequestBody Pregunta pregunta) {
		return rag.responder(pregunta.texto(), pregunta.conversacionId(), "PANEL");
	}

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter preguntarEnStream(@Valid @RequestBody Pregunta pregunta) {
		SseEmitter emisor = new SseEmitter(120_000L);
		// El hilo nuevo no hereda el inquilino de la peticion: hay que pasarlo a mano.
		UUID inquilino = TenantContext.actual();

		hilos.execute(() -> {
			try {
				TenantContext.establecer(inquilino);
				RagService.Resultado resultado = rag.responderEnStream(pregunta.texto(),
						pregunta.conversacionId(),
						pedazo -> enviar(emisor, "pedazo", java.util.Map.of("texto", pedazo)), "PANEL");
				enviar(emisor, "fin", resultado);
				emisor.complete();
			} catch (RuntimeException e) {
				enviar(emisor, "error", java.util.Map.of("error",
						java.util.Objects.requireNonNullElse(e.getMessage(),
								"no pude responder en este momento"),
						"reintentable", WidgetController.esReintentable(e)));
				emisor.complete();
			} finally {
				TenantContext.limpiar();
			}
		});
		return emisor;
	}

	// Siempre JSON, nunca texto pelado: un salto de linea dentro del dato parte el evento SSE
	// en dos y el pedazo se pierde. Serializado, el salto viaja escapado.
	private void enviar(SseEmitter emisor, String evento, Object datos) {
		try {
			emisor.send(SseEmitter.event().name(evento).data(datos));
		} catch (Exception e) {
			// El visitante cerro la pestana a media respuesta: no es un error del servidor.
			emisor.completeWithError(e);
		}
	}

}
