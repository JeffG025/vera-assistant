package com.vera.negocio;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vera.dominio.MensajeRepository;
import com.vera.dominio.PlanTenant;

@Service
public class UsoService {

	private final MensajeRepository mensajes;
	private final double usdPorMillon;

	public UsoService(MensajeRepository mensajes,
			@Value("${vera.costo.usd-por-millon-tokens:0.30}") double usdPorMillon) {
		this.mensajes = mensajes;
		this.usdPorMillon = usdPorMillon;
	}

	public record Uso(String periodo, long preguntas, int limitePreguntas, long tokens, long limiteTokens,
			long sinRespuesta, double costoEstimadoUsd) {
	}

	public Uso calcular(PlanTenant plan) {
		OffsetDateTime inicioDeMes = OffsetDateTime.now(ZoneOffset.UTC)
				.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1);
		LimitesPlan limites = LimitesPlan.de(plan);

		long preguntas = mensajes.contarPreguntasDesde(inicioDeMes);
		long tokens = mensajes.sumarTokensDesde(inicioDeMes);
		long sinRespuesta = mensajes.contarSinRespuestaDesde(inicioDeMes);

		// Estimacion, no factura: el precio por millon depende del modelo y cambia.
		double costo = Math.round((tokens / 1_000_000.0) * usdPorMillon * 10_000) / 10_000.0;

		return new Uso(inicioDeMes.toLocalDate().toString().substring(0, 7), preguntas, limites.mensajesMes(),
				tokens, limites.tokensMes(), sinRespuesta, costo);
	}

	public long preguntasDelMes() {
		OffsetDateTime inicioDeMes = OffsetDateTime.now(ZoneOffset.UTC)
				.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1);
		return mensajes.contarPreguntasDesde(inicioDeMes);
	}

}
