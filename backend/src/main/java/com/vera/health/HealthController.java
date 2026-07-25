package com.vera.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

	private final JdbcTemplate jdbc;

	public HealthController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// Golpea la base de verdad: un UP que no consulta nada miente cuando la conexion se cae.
	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		try {
			String postgres = jdbc.queryForObject("SHOW server_version", String.class);
			// Subconsulta envuelta para que siempre devuelva una fila, con null si la extension falta.
			String pgvector = jdbc.queryForObject(
					"SELECT (SELECT extversion FROM pg_extension WHERE extname = 'vector')", String.class);

			cuerpo.put("status", pgvector != null ? "UP" : "DEGRADED");
			cuerpo.put("postgres", postgres);
			cuerpo.put("pgvector", pgvector);
			if (pgvector == null) {
				cuerpo.put("error", "la extension vector no esta instalada en esta base");
			}
			return ResponseEntity.ok(cuerpo);
		} catch (DataAccessException e) {
			cuerpo.put("status", "DOWN");
			cuerpo.put("error", e.getMostSpecificCause().getMessage());
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(cuerpo);
		}
	}

}
