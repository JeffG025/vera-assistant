package com.vera.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "fuente")
@Getter
@Setter
public class Fuente {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoFuente tipo;

	@Column(nullable = false, length = 200)
	private String nombre;

	// De donde salio: nombre del archivo subido o la URL rastreada.
	@Column
	private String origen;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoFuente estado = EstadoFuente.PENDIENTE;

	@Column
	private String contenido;

	@Column(nullable = false)
	private int caracteres;

	@Column
	private String error;

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
