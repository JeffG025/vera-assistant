package com.vera.dominio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {

	List<Documento> findByFuenteId(UUID fuenteId);

	long countByFuenteId(UUID fuenteId);

}
