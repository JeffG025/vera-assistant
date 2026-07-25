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
