package com.vera.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

	// Sin filtro por inquilino a proposito: el aislamiento lo impone RLS, no este metodo.
	@Query("select u from Usuario u where lower(u.email) = lower(:email)")
	List<Usuario> buscarPorEmail(String email);

	Optional<Usuario> findByIdAndActivoTrue(UUID id);

}
