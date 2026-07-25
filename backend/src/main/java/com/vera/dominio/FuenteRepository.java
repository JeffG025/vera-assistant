package com.vera.dominio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FuenteRepository extends JpaRepository<Fuente, UUID> {

	// RLS ya recorta a las del propio negocio: no hace falta filtrar aqui.
	List<Fuente> findAllByOrderByCreadoEnDesc();

}
