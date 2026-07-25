package com.vera.fuentes;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vera.dominio.Fuente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/fuentes")
public class FuenteController {

	private final FuenteService fuentes;

	public FuenteController(FuenteService fuentes) {
		this.fuentes = fuentes;
	}

	public record Resumen(String id, String tipo, String nombre, String origen, String estado, int documentos,
			int caracteres, String error, String creadoEn) {
	}

	public record TextoPegado(@NotBlank @Size(max = 200) String nombre, @NotBlank String texto) {
	}

	// Enteros envueltos, no primitivos: omitirlos en el JSON es legítimo y con `int` reventaría.
	public record Sitio(@NotBlank String url, Integer maxPaginas, Integer profundidad) {
	}

	@GetMapping
	public List<Resumen> listar() {
		return fuentes.listar().stream().map(FuenteController::mapear).toList();
	}

	@PostMapping("/texto")
	@PreAuthorize("hasAnyRole('OWNER','AGENT')")
	public ResponseEntity<Resumen> desdeTexto(@Valid @RequestBody TextoPegado peticion) {
		Fuente creada = fuentes.crearDesdeTexto(peticion.nombre(), peticion.texto());
		return ResponseEntity.status(HttpStatus.CREATED).body(mapear(creada));
	}

	@PostMapping(value = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('OWNER','AGENT')")
	public ResponseEntity<Resumen> desdeArchivo(@RequestParam("archivo") MultipartFile archivo) {
		Fuente creada = fuentes.crearDesdeArchivo(archivo);
		return ResponseEntity.status(HttpStatus.CREATED).body(mapear(creada));
	}

	@PostMapping("/sitio")
	@PreAuthorize("hasAnyRole('OWNER','AGENT')")
	public ResponseEntity<Resumen> desdeSitio(@Valid @RequestBody Sitio peticion) {
		Fuente creada = fuentes.crearDesdeSitio(peticion.url(),
				peticion.maxPaginas() == null ? 0 : peticion.maxPaginas(),
				peticion.profundidad() == null ? 0 : peticion.profundidad());
		return ResponseEntity.status(HttpStatus.CREATED).body(mapear(creada));
	}

	// Vuelve a leer el sitio y solo toca lo que cambió.
	@PostMapping("/{id}/rastrear")
	@PreAuthorize("hasAnyRole('OWNER','AGENT')")
	public FuenteService.ResumenRastreo rastrear(@PathVariable UUID id) {
		return fuentes.rastrearDeNuevo(id);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
		fuentes.eliminar(id);
		return ResponseEntity.noContent().build();
	}

	// El contenido completo no se devuelve en la lista: puede pesar megas.
	private static Resumen mapear(Fuente f) {
		return new Resumen(f.getId().toString(), f.getTipo().name(), f.getNombre(), f.getOrigen(),
				f.getEstado().name(), f.getDocumentos(), f.getCaracteres(), f.getError(),
				f.getCreadoEn().toString());
	}

}
