package com.vera.contactos;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vera.dominio.Contacto;
import com.vera.dominio.ContactoRepository;

@RestController
@RequestMapping("/api/contactos")
public class ContactoController {

	private static final String CABECERA_CSV = "fecha,nombre,email,telefono,pregunta,aviso";

	private final ContactoRepository contactos;

	public ContactoController(ContactoRepository contactos) {
		this.contactos = contactos;
	}

	public record EnLista(String id, String nombre, String email, String telefono, String pregunta,
			String creadoEn, String webhookEstado, String webhookDetalle) {
	}

	@GetMapping
	public List<EnLista> lista() {
		return contactos.findAllByOrderByCreadoEnDesc().stream()
				.map(c -> new EnLista(c.getId().toString(), c.getNombre(), c.getEmail(),
						c.getTelefono(), c.getPregunta(), c.getCreadoEn().toString(),
						c.getWebhookEstado(), c.getWebhookDetalle()))
				.toList();
	}

	/** El negocio se lleva sus contactos a su hoja de cálculo o a su CRM. */
	@GetMapping("/csv")
	public ResponseEntity<byte[]> csv() {
		StringBuilder salida = new StringBuilder();
		// BOM: sin él, Excel en Windows abre el UTF-8 como si fuera de la página local y los
		// acentos salen rotos. Es el motivo por el que "José" llega como "JosÃ©".
		salida.append('﻿').append(CABECERA_CSV).append("\r\n");

		for (Contacto c : contactos.findAllByOrderByCreadoEnDesc()) {
			salida.append(campo(c.getCreadoEn().toString())).append(',')
					.append(campo(c.getNombre())).append(',')
					.append(campo(c.getEmail())).append(',')
					.append(campo(c.getTelefono())).append(',')
					.append(campo(c.getPregunta())).append(',')
					.append(campo(c.getWebhookEstado())).append("\r\n");
		}

		byte[] cuerpo = salida.toString().getBytes(StandardCharsets.UTF_8);
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contactos.csv\"")
				.body(cuerpo);
	}

	/**
	 * Un campo de CSV, escapado dos veces por dos motivos distintos.
	 *
	 * <p>Las comillas y los saltos de línea rompen el formato. Y un valor que empieza por
	 * {@code = + - @} lo abre Excel como fórmula: estos campos los escribe un visitante anónimo,
	 * así que {@code =HYPERLINK(...)} en el nombre se ejecutaría en la máquina del dueño del
	 * negocio al abrir el archivo. Un apóstrofo delante lo desactiva sin cambiar lo que se lee.
	 */
	static String campo(String valor) {
		if (valor == null || valor.isEmpty()) {
			return "";
		}
		String limpio = valor;
		if ("=+-@\t\r".indexOf(limpio.charAt(0)) >= 0) {
			limpio = "'" + limpio;
		}
		return '"' + limpio.replace("\"", "\"\"") + '"';
	}

}
