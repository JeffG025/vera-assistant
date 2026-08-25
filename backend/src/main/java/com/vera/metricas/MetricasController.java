package com.vera.metricas;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metricas")
public class MetricasController {

	private static final int TOPE_LISTAS = 10;

	private final MetricasDao dao;

	public MetricasController(MetricasDao dao) {
		this.dao = dao;
	}

	public record Metricas(int dias, long conversaciones, long preguntas, long sinRespuesta,
			int tasaResolucion, long positivas, long negativas, Integer satisfaccion,
			long contactos, List<MetricasDao.Recuento> preguntasSinRespuesta,
			List<MetricasDao.Recuento> temas, List<MetricasDao.PorDia> porDia) {
	}

	@GetMapping
	public Metricas resumen(@RequestParam(defaultValue = "30") int dias) {
		int periodo = Math.min(Math.max(dias, 1), 365);
		OffsetDateTime desde = OffsetDateTime.now().minusDays(periodo);

		MetricasDao.Totales t = dao.totales(desde);

		// Resolución: de cada 100 preguntas, cuántas se contestaron con el conocimiento cargado.
		// Sin preguntas no es 0 % ni 100 %: es que no hay dato, y se dice así.
		int resueltas = t.preguntas() == 0 ? 0
				: (int) Math.round(100.0 * (t.preguntas() - t.sinRespuesta()) / t.preguntas());

		// La satisfacción solo significa algo si alguien votó; si no, va nula y el panel calla.
		long votos = t.positivas() + t.negativas();
		Integer satisfaccion = votos == 0 ? null : (int) Math.round(100.0 * t.positivas() / votos);

		return new Metricas(periodo, t.conversaciones(), t.preguntas(), t.sinRespuesta(),
				resueltas, t.positivas(), t.negativas(), satisfaccion, t.contactos(),
				dao.preguntasSinRespuesta(desde, TOPE_LISTAS), dao.fuentesMasCitadas(desde, TOPE_LISTAS),
				dao.porDia(desde));
	}

}
