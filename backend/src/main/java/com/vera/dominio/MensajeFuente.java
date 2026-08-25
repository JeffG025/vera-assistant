package com.vera.dominio;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Fotografia de un fragmento tal como se le paso al modelo para redactar una respuesta. No
 * apunta al fragmento original: ese se reescribe cada vez que la fuente se vuelve a rastrear,
 * y entonces la bandeja mentiria sobre con que se respondio.
 */
@Entity
@Table(name = "mensaje_fuente")
@Getter
@Setter
public class MensajeFuente {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "mensaje_id", nullable = false)
	private UUID mensajeId;

	@Column(nullable = false)
	private short numero;

	@Column(length = 300)
	private String titulo;

	@Column
	private String url;

	@Column(nullable = false)
	private String texto;

	@Column(nullable = false)
	private float distancia;

	/** Si el modelo la cito con su [n] en la respuesta, o solo la tuvo delante. */
	@Column(nullable = false)
	private boolean citada;

}
