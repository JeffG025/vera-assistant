package com.vera.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario {

	@Id
	@GeneratedValue
	private UUID id;

	// Null solo para usuarios de plataforma (SUPERADMIN), que no pertenecen a ningun negocio.
	// EAGER a proposito: el negocio se necesita en el login y al armar el token, siempre fuera de transaccion.
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "tenant_id")
	private Tenant tenant;

	@Column(nullable = false, length = 160)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false, length = 120)
	private String nombre;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "rol_id", nullable = false)
	private Rol rol;

	@Column(nullable = false)
	private boolean activo = true;

	@Column(name = "creado_en", nullable = false)
	private OffsetDateTime creadoEn;

	@Column(name = "ultimo_acceso")
	private OffsetDateTime ultimoAcceso;

	@PrePersist
	void alCrear() {
		this.creadoEn = OffsetDateTime.now();
	}

}
