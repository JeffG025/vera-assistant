package com.vera.dominio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeRepository extends JpaRepository<Mensaje, UUID> {

	// Ventana acotada: mandar la conversacion entera al modelo encarece cada turno sin mejorarlo.
	List<Mensaje> findTop8ByConversacionIdOrderByCreadoEnDesc(UUID conversacionId);

	List<Mensaje> findByConversacionIdOrderByCreadoEn(UUID conversacionId);

}
