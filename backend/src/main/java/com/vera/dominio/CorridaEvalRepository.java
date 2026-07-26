package com.vera.dominio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CorridaEvalRepository extends JpaRepository<CorridaEval, UUID> {

	List<CorridaEval> findTop20ByOrderByCreadoEnDesc();

}
