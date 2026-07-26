package com.vera.dominio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversacionRepository extends JpaRepository<Conversacion, UUID> {

	List<Conversacion> findTop50ByOrderByActualizadoEnDesc();

}
