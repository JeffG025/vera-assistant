package com.vera.dominio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultadoEvalRepository extends JpaRepository<ResultadoEval, UUID> {

	List<ResultadoEval> findByCorridaIdOrderByCreadoEn(UUID corridaId);

}
