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
@Table(name = "mensaje")
@Getter
@Setter
public class Mensaje {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "conversacion_id", nullable = false)
	private UUID conversacionId;

	@Column(nullable = false, length = 12)
	private String rol;

	@Column(nullable = false)
	private String texto;

	@Column(nullable = false)
	private int tokens;

	// Marca las preguntas que el asistente no supo: es la metrica mas util del bloque 6.
	@Column(name = "sin_informacion", nullable = false)
	private boolean sinInformacion;

	// Lo que opino el visitante: 1 sirvio, -1 no sirvio, nulo si no dijo nada (lo normal).
	@Column
	private Short calificacion;

	@Column(name = "creado_en", nullable = false)
	private OffsetDateTime creadoEn;

	@PrePersist
	void alCrear() {
		this.creadoEn = OffsetDateTime.now();
	}

}
