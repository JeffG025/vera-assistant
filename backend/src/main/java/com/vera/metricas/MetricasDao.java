package com.vera.metricas;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Los números del negocio. Van en SQL directo porque son agregados y ventanas, que en JPQL
 * quedan ilegibles o directamente no se pueden escribir. Pasa por el mismo DataSource, así que
 * RLS recorta al inquilino igual que en el resto.
 */
@Repository
public class MetricasDao {

	private final JdbcTemplate jdbc;

	public MetricasDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Recuento(String etiqueta, long cuantas) {
	}

	public record PorDia(String dia, long preguntas, long sinRespuesta) {
	}

	/**
	 * Las preguntas que se quedaron sin respuesta, con su texto. Es el dato más valioso del
	 * panel: dice literalmente qué le preguntan al negocio que su conocimiento no cubre.
	 *
	 * <p>Una pregunta se quedó sin respuesta si el mensaje siguiente del mismo hilo es una
	 * abstención. Eso es {@code lead()} sobre la conversación, que es justo lo que una ventana
	 * hace bien y una subconsulta correlacionada haría mal.
	 */
	public List<Recuento> preguntasSinRespuesta(OffsetDateTime desde, int cuantas) {
		return jdbc.query("""
				SELECT pregunta AS etiqueta, count(*) AS cuantas
				  FROM (
				       SELECT m.texto AS pregunta, m.rol,
				              lead(m.sin_informacion) OVER (
				                  PARTITION BY m.conversacion_id ORDER BY m.creado_en) AS fallo
				         FROM mensaje m
				        WHERE m.creado_en >= ?
				       ) t
				 WHERE t.rol = 'USUARIO' AND t.fallo IS TRUE
				 GROUP BY pregunta
				 ORDER BY cuantas DESC, pregunta
				 LIMIT ?
				""", (fila, i) -> new Recuento(fila.getString("etiqueta"), fila.getLong("cuantas")),
				desde, cuantas);
	}

	/**
	 * De qué habla la gente, medido por el documento que acabó resolviendo cada respuesta. No es
	 * un agrupamiento por significado, pero sale de datos que ya existen y no cuesta ni una
	 * llamada al modelo: si el 60 % de las respuestas salen de «Horarios», el negocio ya sabe
	 * qué le preguntan.
	 */
	public List<Recuento> fuentesMasCitadas(OffsetDateTime desde, int cuantas) {
		return jdbc.query("""
				SELECT coalesce(f.titulo, 'sin titulo') AS etiqueta, count(*) AS cuantas
				  FROM mensaje_fuente f
				  JOIN mensaje m ON m.id = f.mensaje_id
				 WHERE f.citada IS TRUE AND m.creado_en >= ?
				 GROUP BY 1
				 ORDER BY cuantas DESC, etiqueta
				 LIMIT ?
				""", (fila, i) -> new Recuento(fila.getString("etiqueta"), fila.getLong("cuantas")),
				desde, cuantas);
	}

	/** Actividad diaria, con los días vacíos incluidos: un hueco también es información. */
	public List<PorDia> porDia(OffsetDateTime desde) {
		return jdbc.query("""
				SELECT to_char(d.dia, 'YYYY-MM-DD') AS dia,
				       count(m.id) FILTER (WHERE m.rol = 'USUARIO')          AS preguntas,
				       count(m.id) FILTER (WHERE m.sin_informacion IS TRUE)  AS sin_respuesta
				  FROM generate_series(date_trunc('day', ?::timestamptz),
				                       date_trunc('day', now()), interval '1 day') AS d(dia)
				  LEFT JOIN mensaje m ON date_trunc('day', m.creado_en) = d.dia
				 GROUP BY d.dia
				 ORDER BY d.dia
				""", (fila, i) -> new PorDia(fila.getString("dia"), fila.getLong("preguntas"),
						fila.getLong("sin_respuesta")), desde);
	}

	/** Totales de un tirón: son cuatro cuentas sobre la misma tabla y no merecen cuatro viajes. */
	public Totales totales(OffsetDateTime desde) {
		return jdbc.queryForObject("""
				SELECT
				  (SELECT count(*) FROM conversacion WHERE creado_en >= ?)                    AS conversaciones,
				  (SELECT count(*) FROM mensaje WHERE rol = 'USUARIO' AND creado_en >= ?)     AS preguntas,
				  (SELECT count(*) FROM mensaje WHERE sin_informacion IS TRUE
				                                  AND creado_en >= ?)                          AS sin_respuesta,
				  (SELECT count(*) FROM mensaje WHERE calificacion = 1 AND creado_en >= ?)    AS positivas,
				  (SELECT count(*) FROM mensaje WHERE calificacion = -1 AND creado_en >= ?)   AS negativas,
				  (SELECT count(*) FROM contacto WHERE creado_en >= ?)                        AS contactos
				""", (fila, i) -> new Totales(fila.getLong("conversaciones"), fila.getLong("preguntas"),
						fila.getLong("sin_respuesta"), fila.getLong("positivas"),
						fila.getLong("negativas"), fila.getLong("contactos")),
				desde, desde, desde, desde, desde, desde);
	}

	public record Totales(long conversaciones, long preguntas, long sinRespuesta, long positivas,
			long negativas, long contactos) {
	}

}
