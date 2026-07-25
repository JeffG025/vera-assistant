package com.vera.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorErrores {

	@ExceptionHandler(ExcepcionNegocio.class)
	public ResponseEntity<Map<String, Object>> negocio(ExcepcionNegocio e) {
		return ResponseEntity.status(e.getEstado()).body(cuerpo(e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
		Map<String, Object> campos = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
		Map<String, Object> cuerpo = cuerpo("datos invalidos");
		cuerpo.put("campos", campos);
		return ResponseEntity.badRequest().body(cuerpo);
	}

	// JSON ilegible: 400 con el motivo, en vez del reenvio a /error que confunde con un 401.
	@ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> ilegible(
			org.springframework.http.converter.HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest().body(cuerpo("el cuerpo de la peticion no se pudo leer"));
	}

	// Choque contra un indice unico: es un conflicto de negocio, no un error 500.
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo("el dato ya existe o viola una regla"));
	}

	private Map<String, Object> cuerpo(String mensaje) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("error", mensaje);
		return cuerpo;
	}

}
