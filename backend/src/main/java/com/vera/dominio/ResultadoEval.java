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
@Table(name = "resultado_eval")
@Getter
@Setter
public class ResultadoEval {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "corrida_id", nullable = false)
	private UUID corridaId;

	@Column(nullable = false)
	private String pregunta;

	@Column(name = "respuesta_obtenida")
	private String respuestaObtenida;

	@Column(name = "respuesta_esperada")
	private String respuestaEsperada;

	@Column(name = "fuente_esperada", length = 200)
	private String fuenteEsperada;

	@Column(name = "fuentes_obtenidas")
	private String fuentesObtenidas;

	@Column(name = "debe_saber", nullable = false)
	private boolean debeSaber;

	@Column(name = "sin_informacion", nullable = false)
	private boolean sinInformacion;

	@Column(name = "recuperacion_ok")
	private Boolean recuperacionOk;

	@Column(name = "fidelidad_ok")
	private Boolean fidelidadOk;

	// Lo que dijo el juez: sin esto un fallo es un numero sin explicacion.
	@Column
	private String motivo;

	@Column(name = "creado_en", nullable = false)
	private OffsetDateTime creadoEn;

	@PrePersist
	void alCrear() {
		this.creadoEn = OffsetDateTime.now();
	}

}
