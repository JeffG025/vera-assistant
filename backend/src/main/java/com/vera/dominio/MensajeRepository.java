package com.vera.dominio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MensajeRepository extends JpaRepository<Mensaje, UUID> {

	// Ventana acotada: mandar la conversacion entera al modelo encarece cada turno sin mejorarlo.
	List<Mensaje> findTop8ByConversacionIdOrderByCreadoEnDesc(UUID conversacionId);

	List<Mensaje> findByConversacionIdOrderByCreadoEn(UUID conversacionId);

	// RLS ya recorta al inquilino actual; aqui solo se acota el periodo.
	@Query("select count(m) from Mensaje m where m.rol = 'USUARIO' and m.creadoEn >= :desde")
	long contarPreguntasDesde(OffsetDateTime desde);

	@Query("select coalesce(sum(m.tokens), 0) from Mensaje m where m.creadoEn >= :desde")
	long sumarTokensDesde(OffsetDateTime desde);

	@Query("select count(m) from Mensaje m where m.sinInformacion = true and m.creadoEn >= :desde")
	long contarSinRespuestaDesde(OffsetDateTime desde);

}
