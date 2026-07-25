package com.vera.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "rol")
@Getter
@Setter
public class Rol {

	// Catalogo global sembrado por Flyway: 1 SUPERADMIN, 2 OWNER, 3 AGENT.
	@Id
	private Short id;

	@Column(nullable = false, length = 20, unique = true)
	private String codigo;

	@Column(nullable = false, length = 60)
	private String nombre;

}
