package com.vera.dominio;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

	Optional<Tenant> findBySlug(String slug);

	Optional<Tenant> findByClavePublica(String clavePublica);

	boolean existsBySlug(String slug);

}
