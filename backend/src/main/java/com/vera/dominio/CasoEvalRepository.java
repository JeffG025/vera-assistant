package com.vera.dominio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CasoEvalRepository extends JpaRepository<CasoEval, UUID> {

	List<CasoEval> findByActivoTrueOrderByCreadoEn();

	List<CasoEval> findByActivoTrueAndEsencialTrueOrderByCreadoEn();

	List<CasoEval> findAllByOrderByCreadoEn();

}
