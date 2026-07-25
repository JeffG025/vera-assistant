package com.vera.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auditoria")
@Getter
@Setter
public class Auditoria {

	@Id
	@GeneratedValue
	private UUID id;

	// Ids sueltos, sin relacion: la bitacora sobrevive aunque el usuario se borre.
	@Column(name = "tenant_id")
	private UUID tenantId;

	@Column(name = "usuario_id")
	private UUID usuarioId;

	@Column(nullable = false, length = 60)
	private String accion;

	@Column
	private String detalle;

	@Column(name = "creado_en", nullable = false)
	private OffsetDateTime creadoEn;

	@PrePersist
	void alCrear() {
		this.creadoEn = OffsetDateTime.now();
	}

}
