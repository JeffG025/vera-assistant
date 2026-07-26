package com.vera.fuentes;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acceso directo por JDBC porque el tipo `vector` de pgvector no lo mapea Hibernate.
 * Sigue pasando por el DataSource con inquilino, asi que RLS aplica igual.
 */
@Repository
public class FragmentoDao {

	private final JdbcTemplate jdbc;

	public FragmentoDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Resultado(String texto, String titulo, String url, double distancia) {
	}

	// Se expone el id para poder fusionar las dos busquedas sin comparar textos.
	public record Candidato(String id, String texto, String titulo, String url, double distancia) {
	}

	public List<Candidato> buscarVectorial(float[] consulta, int cuantos) {
		String vector = literal(consulta);
		return jdbc.query("""
				SELECT id::text AS id, texto, titulo, url, embedding <=> ?::vector AS distancia
				FROM fragmento
				ORDER BY embedding <=> ?::vector
				LIMIT ?
				""", this::mapear, vector, vector, cuantos);
	}

	// plainto_tsquery ignora la puntuacion y une los terminos con AND, que es lo que espera
	// alguien escribiendo una pregunta normal.
	public List<Candidato> buscarTextual(String consulta, int cuantos) {
		return jdbc.query("""
				SELECT id::text AS id, texto, titulo, url,
				       1 - ts_rank(busqueda, plainto_tsquery('spanish'::regconfig, ?)) AS distancia
				FROM fragmento
				WHERE busqueda @@ plainto_tsquery('spanish'::regconfig, ?)
				ORDER BY ts_rank(busqueda, plainto_tsquery('spanish'::regconfig, ?)) DESC
				LIMIT ?
				""", this::mapear, consulta, consulta, consulta, cuantos);
	}

	private Candidato mapear(java.sql.ResultSet fila, int i) throws java.sql.SQLException {
		return new Candidato(fila.getString("id"), fila.getString("texto"), fila.getString("titulo"),
				fila.getString("url"), fila.getDouble("distancia"));
	}

	public void borrarPorDocumento(UUID documentoId) {
		jdbc.update("DELETE FROM fragmento WHERE documento_id = ?", documentoId);
	}

	public void insertar(UUID tenantId, UUID fuenteId, UUID documentoId, int orden, String texto,
			String titulo, String url, float[] embedding, String modelo) {
		jdbc.update("""
				INSERT INTO fragmento (tenant_id, fuente_id, documento_id, orden, texto, titulo, url, embedding, modelo)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector, ?)
				""", tenantId, fuenteId, documentoId, orden, texto, titulo, url, literal(embedding), modelo);
	}

	public List<Resultado> buscar(float[] consulta, int cuantos) {
		String vector = literal(consulta);
		return jdbc.query("""
				SELECT texto, titulo, url, embedding <=> ?::vector AS distancia
				FROM fragmento
				ORDER BY embedding <=> ?::vector
				LIMIT ?
				""",
				(fila, i) -> new Resultado(fila.getString("texto"), fila.getString("titulo"),
						fila.getString("url"), fila.getDouble("distancia")),
				vector, vector, cuantos);
	}

	// Vista previa para el panel: deja ver como quedo troceado el documento.
	public List<Trozo> porFuente(UUID fuenteId, int limite) {
		return jdbc.query("""
				SELECT orden, titulo, url, left(texto, 400) AS extracto, length(texto) AS largo
				FROM fragmento
				WHERE fuente_id = ?
				ORDER BY documento_id, orden
				LIMIT ?
				""",
				(fila, i) -> new Trozo(fila.getInt("orden"), fila.getString("titulo"), fila.getString("url"),
						fila.getString("extracto"), fila.getInt("largo")),
				fuenteId, limite);
	}

	public record Trozo(int orden, String titulo, String url, String extracto, int largo) {
	}

	public long contar() {
		Long total = jdbc.queryForObject("SELECT count(*) FROM fragmento", Long.class);
		return total == null ? 0 : total;
	}

	private String literal(float[] vector) {
		StringJoiner sj = new StringJoiner(",", "[", "]");
		for (float valor : vector) {
			sj.add(Float.toString(valor));
		}
		return sj.toString();
	}

}
