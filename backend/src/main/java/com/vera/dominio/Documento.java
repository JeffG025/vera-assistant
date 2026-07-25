package com.vera.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "documento")
@Getter
@Setter
public class Documento {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "fuente_id", nullable = false)
	private UUID fuenteId;

	@Column
	private String url;

	@Column(length = 300)
	private String titulo;

	@Column(nullable = false)
	private String contenido;

	// Huella del contenido: si no cambia entre rastreos, no hay que reindexar nada.
	@Column(nullable = false, length = 64)
	private String hash;

	@Column(nullable = false)
	private int caracteres;

	@Column(name = "creado_en", nullable = false)
	private OffsetDateTime creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private OffsetDateTime actualizadoEn;

	@PrePersist
	void alCrear() {
		OffsetDateTime ahora = OffsetDateTime.now();
		this.creadoEn = ahora;
		this.actualizadoEn = ahora;
	}

	@PreUpdate
	void alActualizar() {
		this.actualizadoEn = OffsetDateTime.now();
	}

}
