package com.vera.dominio;

import java.math.BigDecimal;
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
@Table(name = "corrida_eval")
@Getter
@Setter
public class CorridaEval {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private int total;

	// Cada metrica guarda aciertos y base: un 8/8 y un 80/80 no valen lo mismo y el
	// porcentaje solo esconderia esa diferencia.
	@Column(name = "recuperacion_ok", nullable = false)
	private int recuperacionOk;

	@Column(name = "recuperacion_base", nullable = false)
	private int recuperacionBase;

	@Column(name = "fidelidad_ok", nullable = false)
	private int fidelidadOk;

	@Column(name = "fidelidad_base", nullable = false)
	private int fidelidadBase;

	@Column(name = "abstencion_ok", nullable = false)
	private int abstencionOk;

	@Column(name = "abstencion_base", nullable = false)
	private int abstencionBase;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal puntaje;

	@Column(length = 60)
	private String modelo;

	@Column(precision = 5, scale = 3)
	private BigDecimal umbral;

	@Column(nullable = false)
	private int tokens;

	// Casos que no se pudieron ejecutar. Cuentan como fallo: un examen a medias no es un buen examen.
	@Column(nullable = false)
	private int errores;

	@Column(name = "creado_en", nullable = false)
	private OffsetDateTime creadoEn;

	@PrePersist
	void alCrear() {
		this.creadoEn = OffsetDateTime.now();
	}

}
