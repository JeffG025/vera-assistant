package com.vera.contactos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vera.dominio.Contacto;
import com.vera.dominio.ContactoRepository;
import com.vera.dominio.Tenant;
import com.vera.dominio.TenantRepository;
import com.vera.error.ExcepcionNegocio;
import com.vera.tenancy.TenantContext;

@Service
public class ContactoService {

	private final ContactoRepository contactos;
	private final TenantRepository tenants;
	private final boolean permitirLocales;

	// Mapper propio: Boot 4 no expone un ObjectMapper como bean, y este solo arma el aviso
	// que se manda al CRM del cliente. Mismo criterio que en EmbeddingsGemini.
	private final ObjectMapper json = new ObjectMapper();

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			// Un webhook que responde con un redirect a la red interna esquivaria el filtro.
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	// El visitante no tiene que esperar a que el CRM del cliente conteste para ver su "gracias".
	private final ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();

	public ContactoService(ContactoRepository contactos, TenantRepository tenants,
			@Value("${vera.webhook.permitir-locales:false}") boolean permitirLocales) {
		this.contactos = contactos;
		this.tenants = tenants;
		this.permitirLocales = permitirLocales;
	}

	public boolean permiteLocales() {
		return permitirLocales;
	}

	public Contacto registrar(String nombre, String email, String telefono, String pregunta,
			UUID conversacionId) {
		UUID inquilino = TenantContext.actual();
		Tenant negocio = tenants.findById(inquilino)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("negocio no encontrado"));

		Contacto contacto = new Contacto();
		contacto.setTenantId(inquilino);
		contacto.setConversacionId(conversacionId);
		contacto.setNombre(nombre.trim());
		contacto.setEmail(email.trim());
		contacto.setTelefono(telefono == null || telefono.isBlank() ? null : telefono.trim());
		contacto.setPregunta(pregunta);

		String destino = negocio.getWebhookUrl();
		boolean hayWebhook = destino != null && !destino.isBlank();
		contacto.setWebhookEstado(hayWebhook ? "PENDIENTE" : "SIN_WEBHOOK");
		Contacto guardado = contactos.save(contacto);

		if (hayWebhook) {
			hilos.execute(() -> avisar(inquilino, guardado.getId(), destino, negocio.getNombre(),
					guardado));
		}
		return guardado;
	}

	/** Envía el aviso y deja escrito qué pasó: un webhook silencioso no se puede depurar. */
	private void avisar(UUID inquilino, UUID contactoId, String destino, String negocio,
			Contacto datos) {
		String estado;
		String detalle;
		try {
			// Se vuelve a comprobar aqui, no solo al guardarlo: entre una cosa y otra el DNS
			// del destino puede haber cambiado a una direccion interna.
			if (DestinoWebhook.apuntaAdentro(destino, permitirLocales)) {
				estado = "FALLO";
				detalle = "el destino resuelve a una direccion interna";
			} else {
				String cuerpo = json.writeValueAsString(new Aviso(contactoId.toString(), negocio,
						datos.getNombre(), datos.getEmail(), datos.getTelefono(),
						datos.getPregunta(), datos.getCreadoEn().toString()));

				HttpResponse<String> respuesta = http.send(
						HttpRequest.newBuilder(URI.create(destino))
								.timeout(Duration.ofSeconds(10))
								.header("Content-Type", "application/json")
								.header("User-Agent", "Vera/1.0")
								.POST(HttpRequest.BodyPublishers.ofString(cuerpo))
								.build(),
						HttpResponse.BodyHandlers.ofString());

				boolean bien = respuesta.statusCode() >= 200 && respuesta.statusCode() < 300;
				estado = bien ? "ENVIADO" : "FALLO";
				detalle = bien ? null : "el destino respondio " + respuesta.statusCode();
			}
		} catch (Exception e) {
			estado = "FALLO";
			detalle = motivoLegible(e);
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}

		// El hilo nuevo no hereda el inquilino, y sin el RLS no deja ni leer el contacto.
		final String estadoFinal = estado;
		final String detalleFinal = detalle;
		TenantContext.establecer(inquilino);
		try {
			contactos.findById(contactoId).ifPresent(c -> {
				c.setWebhookEstado(estadoFinal);
				c.setWebhookDetalle(detalleFinal);
				contactos.save(c);
			});
		} finally {
			TenantContext.limpiar();
		}
	}

	/**
	 * Quien lee esto en el panel es el dueño de una tienda, no un programador. Un
	 * "ConnectException: null" no le dice si el problema es suyo o nuestro.
	 */
	private static String motivoLegible(Exception e) {
		if (e instanceof java.net.ConnectException) {
			return "no se pudo conectar con esa direccion";
		}
		if (e instanceof java.net.http.HttpTimeoutException) {
			return "el destino tardo demasiado en contestar";
		}
		if (e instanceof java.net.UnknownHostException) {
			return "esa direccion no existe";
		}
		String mensaje = e.getMessage();
		return mensaje == null || mensaje.isBlank() ? e.getClass().getSimpleName()
				: e.getClass().getSimpleName() + ": " + mensaje;
	}

	/** Lo que recibe el CRM del cliente. Plano y estable: es un contrato con terceros. */
	public record Aviso(String id, String negocio, String nombre, String email, String telefono,
			String pregunta, String creadoEn) {
	}

}
