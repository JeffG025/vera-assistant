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

/** Una persona que dejó sus datos porque el asistente no supo responderle. */
@Entity
@Table(name = "contacto")
@Getter
@Setter
public class Contacto {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "conversacion_id")
	private UUID conversacionId;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(nullable = false, length = 180)
	private String email;

	@Column(length = 40)
	private String telefono;

	/** Lo que preguntó y no se le pudo contestar: es por donde empieza la llamada de vuelta. */
	@Column
	private String pregunta;

	@Column(name = "creado_en", nullable = false)
	private OffsetDateTime creadoEn;

	@Column(name = "webhook_estado", nullable = false, length = 12)
	private String webhookEstado = "SIN_WEBHOOK";

	@Column(name = "webhook_detalle")
	private String webhookDetalle;

	@PrePersist
	void alCrear() {
		this.creadoEn = OffsetDateTime.now();
	}

}
