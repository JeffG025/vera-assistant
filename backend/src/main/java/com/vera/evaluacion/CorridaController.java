package com.vera.evaluacion;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vera.dominio.CorridaEval;
import com.vera.dominio.ResultadoEval;

@RestController
@RequestMapping("/api/evaluacion/corridas")
@PreAuthorize("hasAnyRole('OWNER','AGENT')")
public class CorridaController {

	private final EvaluadorService evaluador;

	public CorridaController(EvaluadorService evaluador) {
		this.evaluador = evaluador;
	}

	public record Resumen(String id, int total, String puntaje, String recuperacion, String fidelidad,
			String abstencion, int errores, boolean completa, String modelo, int tokens, String fecha) {
	}

	public record Detalle(String pregunta, String esperada, String obtenida, boolean debeSaber,
			boolean sinInformacion, Boolean recuperacionOk, Boolean fidelidadOk, String fuenteEsperada,
			String fuentesObtenidas, String motivo) {
	}

	@PostMapping
	public Resumen ejecutar() {
		return mapear(evaluador.ejecutar());
	}

	@GetMapping
	public List<Resumen> historial() {
		return evaluador.historial().stream().map(CorridaController::mapear).toList();
	}

	@GetMapping("/{id}")
	public List<Detalle> detalle(@PathVariable UUID id) {
		return evaluador.detalle(id).stream()
				.map(r -> new Detalle(r.getPregunta(), r.getRespuestaEsperada(), r.getRespuestaObtenida(),
						r.isDebeSaber(), r.isSinInformacion(), r.getRecuperacionOk(), r.getFidelidadOk(),
						r.getFuenteEsperada(), r.getFuentesObtenidas(), r.getMotivo()))
				.toList();
	}

	private static Resumen mapear(CorridaEval c) {
		return new Resumen(c.getId().toString(), c.getTotal(), c.getPuntaje() + "%",
				c.getRecuperacionOk() + "/" + c.getRecuperacionBase(),
				c.getFidelidadOk() + "/" + c.getFidelidadBase(),
				c.getAbstencionOk() + "/" + c.getAbstencionBase(),
				c.getErrores(), c.getErrores() == 0,
				c.getModelo(), c.getTokens(), c.getCreadoEn().toString());
	}

}
