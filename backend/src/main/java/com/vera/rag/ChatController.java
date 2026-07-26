package com.vera.rag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final RagService rag;

	public ChatController(RagService rag) {
		this.rag = rag;
	}

	public record Pregunta(@NotBlank @Size(max = 1000) String texto) {
	}

	// Por ahora exige sesion del panel. El endpoint publico con clave del widget llega en el bloque 5.
	@PostMapping
	public RagService.Resultado preguntar(@Valid @RequestBody Pregunta pregunta) {
		return rag.responder(pregunta.texto());
	}

}
