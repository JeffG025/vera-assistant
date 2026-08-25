package com.vera.dominio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContactoRepository extends JpaRepository<Contacto, UUID> {

	// RLS ya recorta al negocio; el orden es el que importa: lo mas reciente primero.
	List<Contacto> findAllByOrderByCreadoEnDesc();

	@Query("select count(c) from Contacto c where c.creadoEn >= :desde")
	long contarDesde(OffsetDateTime desde);

}
