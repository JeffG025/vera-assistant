package com.vera.evaluacion;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vera.dominio.CasoEval;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/evaluacion/casos")
@PreAuthorize("hasAnyRole('OWNER','AGENT')")
public class EvaluacionController {

	private final EvaluacionService evaluacion;

	public EvaluacionController(EvaluacionService evaluacion) {
		this.evaluacion = evaluacion;
	}

	public record NuevoCaso(@NotBlank String pregunta, String respuestaEsperada, String fuenteEsperada,
			Boolean debeSaber, Boolean esencial) {
	}

	public record CambioCaso(Boolean esencial, Boolean activo) {
	}

	public record Caso(String id, String pregunta, String respuestaEsperada, String fuenteEsperada,
			boolean debeSaber, boolean esencial, boolean activo) {
	}

	@GetMapping
	public List<Caso> listar() {
		return evaluacion.listar().stream().map(EvaluacionController::mapear).toList();
	}

	@PostMapping
	public ResponseEntity<Caso> crear(@Valid @RequestBody NuevoCaso nuevo) {
		CasoEval caso = evaluacion.crear(nuevo.pregunta(), nuevo.respuestaEsperada(), nuevo.fuenteEsperada(),
				nuevo.debeSaber() == null || nuevo.debeSaber(),
				nuevo.esencial() != null && nuevo.esencial());
		return ResponseEntity.status(HttpStatus.CREATED).body(mapear(caso));
	}

	@PatchMapping("/{id}")
	public Caso actualizar(@PathVariable UUID id, @RequestBody CambioCaso cambio) {
		return mapear(evaluacion.actualizar(id, cambio.esencial(), cambio.activo()));
	}

	@PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Map<String, Integer> importar(@RequestParam("archivo") MultipartFile archivo) {
		return Map.of("importados", evaluacion.importarCsv(archivo));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
		evaluacion.eliminar(id);
		return ResponseEntity.noContent().build();
	}

	private static Caso mapear(CasoEval c) {
		return new Caso(c.getId().toString(), c.getPregunta(), c.getRespuestaEsperada(),
				c.getFuenteEsperada(), c.isDebeSaber(), c.isEsencial(), c.isActivo());
	}

}
