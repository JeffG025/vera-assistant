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
@Table(name = "tenant")
@Getter
@Setter
public class Tenant {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(nullable = false, length = 60, unique = true)
	private String slug;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlanTenant plan = PlanTenant.TRIAL;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoTenant estado = EstadoTenant.ACTIVO;

	// Viaja en el <script> del sitio del cliente: identifica al negocio, no autoriza el panel.
	@Column(name = "clave_publica", nullable = false, length = 64, unique = true)
	private String clavePublica;

	@Column(name = "dominios_permitidos")
	private String dominiosPermitidos;

	// Personalidad del asistente; el negocio la ajusta sin tocar codigo.
	@Column(name = "asistente_nombre", nullable = false, length = 60)
	private String asistenteNombre = "Vera";

	@Column(name = "asistente_idioma", nullable = false, length = 20)
	private String asistenteIdioma = "espanol";

	@Column(name = "asistente_tono")
	private String asistenteTono;

	@Column(name = "asistente_saludo")
	private String asistenteSaludo;

	// Apariencia en el sitio del cliente; la base la valida con un CHECK.
	@Column(name = "asistente_color", nullable = false, length = 7)
	private String asistenteColor = "#4f46e5";

	@Column(name = "asistente_avatar", nullable = false, length = 8)
	private String asistenteAvatar = "\uD83D\uDCAC";

	@Column(name = "asistente_posicion", nullable = false, length = 10)
	private String asistentePosicion = "derecha";

	// Pedir el correo sin permiso es intrusivo: cada negocio decide si lo quiere.
	@Column(name = "capturar_contactos", nullable = false)
	private boolean capturarContactos;

	@Column(name = "webhook_url")
	private String webhookUrl;

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
