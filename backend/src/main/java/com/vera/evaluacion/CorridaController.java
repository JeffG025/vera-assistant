package com.vera.evaluacion;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

	public record Resumen(String id, String conjunto, int total, int evaluados, String puntaje,
			String recuperacion, String fidelidad, String abstencion, int errores, boolean completa,
			String modelo, int tokens, String fecha) {
	}

	public record Detalle(String pregunta, String esperada, String obtenida, boolean debeSaber,
			boolean sinInformacion, Boolean recuperacionOk, Boolean fidelidadOk, String fuenteEsperada,
			String fuentesObtenidas, String motivo) {
	}

	// Por omision corre el conjunto esencial; el completo hay que pedirlo a proposito porque
	// no cabe en el free tier.
	@PostMapping
	public Resumen ejecutar(@RequestParam(defaultValue = "esencial") String conjunto) {
		return mapear(evaluador.ejecutar(conjunto));
	}

	@GetMapping
	public List<Resumen> historial() {
		return evaluador.historial().stream().map(CorridaController::mapear).toList();
	}

	public record CambioCaso(String pregunta, String antes, String despues, String motivo) {
	}

	public record Comparacion(Resumen base, Resumen nueva, String veredicto, String deltaPuntaje,
			int mejoraron, int empeoraron, int iguales, List<CambioCaso> cambios) {
	}

	// La corrida del path es la nueva; 'contra' es la que sirve de referencia.
	@GetMapping("/{id}/comparar")
	public Comparacion comparar(@PathVariable UUID id, @RequestParam UUID contra) {
		EvaluadorService.Comparacion c = evaluador.comparar(id, contra);
		return new Comparacion(mapear(c.base()), mapear(c.nueva()), c.veredicto(),
				(c.deltaPuntaje().signum() > 0 ? "+" : "") + c.deltaPuntaje() + "%",
				c.mejoraron(), c.empeoraron(), c.iguales(),
				c.cambios().stream()
						.map(x -> new CambioCaso(x.pregunta(), nombre(x.antes()), nombre(x.despues()),
								x.motivo()))
						.toList());
	}

	private static String nombre(EvaluadorService.Veredicto v) {
		return v == null ? null : v.name();
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
		// Cada caso deja una nota de fidelidad (si debia saber), una de abstencion (si debia
		// callarse) o un error. Si la suma no llega al total, la corrida no examino todo lo que
		// dice examinar, aunque no haya registrado errores. Peca de desconfiada a proposito:
		// vale mas marcar de mas que volver a enseñar un 100% que no se corrio entero.
		int evaluados = c.getFidelidadBase() + c.getAbstencionBase() + c.getErrores();
		return new Resumen(c.getId().toString(), c.getConjunto(), c.getTotal(), evaluados,
				c.getPuntaje() + "%",
				c.getRecuperacionOk() + "/" + c.getRecuperacionBase(),
				c.getFidelidadOk() + "/" + c.getFidelidadBase(),
				c.getAbstencionOk() + "/" + c.getAbstencionBase(),
				c.getErrores(), c.getErrores() == 0 && evaluados >= c.getTotal(),
				c.getModelo(), c.getTokens(), c.getCreadoEn().toString());
	}

}
