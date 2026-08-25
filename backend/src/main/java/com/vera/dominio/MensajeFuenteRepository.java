package com.vera.dominio;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeFuenteRepository extends JpaRepository<MensajeFuente, UUID> {

	// Se piden de golpe las de toda la conversacion: una consulta por mensaje seria un N+1
	// justo en la pantalla que mas mensajes ensena.
	List<MensajeFuente> findByMensajeIdInOrderByNumero(Collection<UUID> mensajeIds);

}
