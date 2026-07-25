package com.vera.dominio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<Auditoria, UUID> {

	List<Auditoria> findTop50ByOrderByCreadoEnDesc();

}
