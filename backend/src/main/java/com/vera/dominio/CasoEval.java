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
@Table(name = "caso_eval")
@Getter
@Setter
public class CasoEval {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private String pregunta;

	// Null en los casos negativos: ahi lo correcto es que el asistente admita no saber.
	@Column(name = "respuesta_esperada")
	private String respuestaEsperada;

	@Column(name = "fuente_esperada", length = 200)
	private String fuenteEsperada;

	@Column(name = "debe_saber", nullable = false)
	private boolean debeSaber = true;

	@Column(nullable = false)
	private boolean activo = true;

	@Column(name = "creado_en", nullable = false)
	private OffsetDateTime creadoEn;

	@PrePersist
	void alCrear() {
		this.creadoEn = OffsetDateTime.now();
	}

}
