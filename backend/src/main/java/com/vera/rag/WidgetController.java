package com.vera.rag;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vera.dominio.EstadoTenant;
import com.vera.dominio.Mensaje;
import com.vera.dominio.MensajeRepository;
import com.vera.dominio.Tenant;
import com.vera.dominio.TenantRepository;
import com.vera.error.ExcepcionNegocio;
import com.vera.tenancy.TenantContext;

import com.vera.contactos.ContactoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * La puerta del widget: lo único que se sirve a la web de un cliente, sin sesión. El visitante
 * no tiene cuenta y la clave pública no autoriza nada del panel — solo dice de qué negocio es
 * el asistente. Lo que protege el gasto es lo mismo de siempre: límite de tasa y cuota mensual.
 */
@RestController
@RequestMapping("/api/widget")
public class WidgetController {

	private final RagService rag;
	private final TenantRepository tenants;
	private final MensajeRepository mensajes;
	private final ContactoService contactos;
	private final LimitadorTasa limitador;

	// Hilos virtuales: cada respuesta en streaming ocupa un hilo durante segundos.
	private final ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();

	public WidgetController(RagService rag, TenantRepository tenants, MensajeRepository mensajes,
			ContactoService contactos, LimitadorTasa limitador) {
		this.rag = rag;
		this.tenants = tenants;
		this.mensajes = mensajes;
		this.contactos = contactos;
		this.limitador = limitador;
	}

	public record Config(String asistente, String negocio, String saludo, String idioma, String color,
			String avatar, String posicion, boolean pideContacto) {
	}

	/** Lo que el widget necesita para pintarse antes de la primera pregunta. */
	@GetMapping("/config")
	public Config config(@RequestHeader(value = "X-Vera-Key", required = false) String clave,
			@RequestHeader(value = "Origin", required = false) String origen,
			@RequestHeader(value = "Referer", required = false) String referente) {
		Tenant negocio = resolver(clave, origen, referente);
		String saludo = negocio.getAsistenteSaludo() != null && !negocio.getAsistenteSaludo().isBlank()
				? negocio.getAsistenteSaludo()
				: "Hola, soy " + negocio.getAsistenteNombre() + ". ¿En que te ayudo?";
		return new Config(negocio.getAsistenteNombre(), negocio.getNombre(), saludo,
				negocio.getAsistenteIdioma(), negocio.getAsistenteColor(),
				negocio.getAsistenteAvatar(), negocio.getAsistentePosicion(),
				negocio.isCapturarContactos());
	}

	@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter preguntar(@RequestHeader(value = "X-Vera-Key", required = false) String clave,
			@RequestHeader(value = "Origin", required = false) String origen,
			@RequestHeader(value = "Referer", required = false) String referente,
			@Valid @RequestBody ChatController.Pregunta pregunta) {
		UUID inquilino = resolver(clave, origen, referente).getId();
		SseEmitter emisor = new SseEmitter(120_000L);

		hilos.execute(() -> {
			try {
				// El hilo nuevo no hereda el inquilino de la peticion: hay que pasarlo a mano.
				TenantContext.establecer(inquilino);
				RagService.Resultado resultado = rag.responderEnStream(pregunta.texto(),
						pregunta.conversacionId(),
						pedazo -> enviar(emisor, "pedazo", Map.of("texto", pedazo)), "WEB");
				enviar(emisor, "fin", resultado);
				emisor.complete();
			} catch (RuntimeException e) {
				// El visitante merece un motivo entendible: sin cuota, saturado o caido.
				enviar(emisor, "error", Map.of("error",
						Objects.requireNonNullElse(e.getMessage(), "no pude responder en este momento"),
						// La cuota diaria no se arregla esperando: el widget deja de ofrecer reintento.
						"reintentable", esReintentable(e)));
				emisor.complete();
			} finally {
				TenantContext.limpiar();
			}
		});
		return emisor;
	}

	public record Calificacion(short valor) {
	}

	public record DatosDeContacto(@NotBlank @Size(max = 120) String nombre,
			@NotBlank @Email @Size(max = 180) String email, @Size(max = 40) String telefono,
			@Size(max = 2000) String pregunta, String conversacionId) {
	}

	/**
	 * El visitante deja sus datos. Solo se acepta si el negocio lo tiene activado: si no, un
	 * tercero podria llenarle la bandeja de contactos que el nunca pidio.
	 */
	@PostMapping("/contactos")
	public Map<String, String> dejarContacto(
			@RequestHeader(value = "X-Vera-Key", required = false) String clave,
			@RequestHeader(value = "Origin", required = false) String origen,
			@RequestHeader(value = "Referer", required = false) String referente,
			@Valid @RequestBody DatosDeContacto datos) {

		Tenant negocio = resolver(clave, origen, referente);
		if (!negocio.isCapturarContactos()) {
			throw ExcepcionNegocio.conflicto("este asistente no pide datos de contacto");
		}
		// El limite de tasa tambien aqui: sin el, el formulario es un buzon de spam abierto.
		limitador.exigirCupo("contacto:" + negocio.getId());

		TenantContext.establecer(negocio.getId());
		try {
			UUID conversacion = datos.conversacionId() == null || datos.conversacionId().isBlank()
					? null
					: UUID.fromString(datos.conversacionId());
			contactos.registrar(datos.nombre(), datos.email(), datos.telefono(), datos.pregunta(),
					conversacion);
			return Map.of("estado", "guardado");
		} finally {
			TenantContext.limpiar();
		}
	}

	/**
	 * El pulgar del visitante. No pide sesion porque no la hay: quien lee la respuesta no tiene
	 * cuenta. Lo unico que se comprueba es que el mensaje sea del negocio de esa clave y que sea
	 * una respuesta, no una pregunta. Puede cambiar de opinion: el ultimo voto manda.
	 */
	@PostMapping("/mensajes/{id}/calificacion")
	public void calificar(@RequestHeader(value = "X-Vera-Key", required = false) String clave,
			@RequestHeader(value = "Origin", required = false) String origen,
			@RequestHeader(value = "Referer", required = false) String referente,
			@PathVariable UUID id, @RequestBody Calificacion calificacion) {

		if (calificacion.valor() != 1 && calificacion.valor() != -1) {
			throw ExcepcionNegocio.conflicto("la calificacion solo puede ser 1 o -1");
		}
		UUID inquilino = resolver(clave, origen, referente).getId();

		TenantContext.establecer(inquilino);
		try {
			// El contexto ya recorta al negocio via RLS: un id de otro inquilino no aparece.
			Mensaje mensaje = mensajes.findById(id)
					.orElseThrow(() -> ExcepcionNegocio.noEncontrado("mensaje no encontrado"));
			if (!"ASISTENTE".equals(mensaje.getRol())) {
				throw ExcepcionNegocio.conflicto("solo se califican las respuestas");
			}
			mensaje.setCalificacion(calificacion.valor());
			mensajes.save(mensaje);
		} finally {
			TenantContext.limpiar();
		}
	}

	// El limite por minuto y el servidor caido se arreglan volviendo a intentar; la cuota diaria
	// del proveedor no, y ofrecer un boton que no puede funcionar es peor que no ofrecerlo.
	static boolean esReintentable(RuntimeException e) {
		return !(e instanceof ExcepcionCuotaLlm cuota && cuota.esDiaria());
	}

	// La clave publica identifica al negocio, y buscarla cruza inquilinos igual que el login.
	private Tenant resolver(String clave, String origen, String referente) {
		if (clave == null || clave.isBlank()) {
			// Sin la cabecera, el error por omision seria un 400 con otro formato de JSON.
			throw ExcepcionNegocio.noAutorizado("falta la clave publica del widget");
		}
		Tenant negocio = TenantContext.comoSistema(() -> tenants.findByClavePublica(clave))
				.orElseThrow(() -> ExcepcionNegocio.noAutorizado("clave publica no valida"));
		if (negocio.getEstado() != EstadoTenant.ACTIVO) {
			throw ExcepcionNegocio.noAutorizado("este asistente esta desactivado");
		}
		// 403 y no 401: la clave es buena, lo que no vale es el sitio desde donde se usa. El
		// visitante no puede arreglarlo, pero el dueno lee el motivo exacto en su panel.
		if (!DominiosPermitidos.permite(negocio.getDominiosPermitidos(), origen, referente)) {
			throw ExcepcionNegocio.prohibido("este asistente no esta autorizado en este sitio");
		}
		return negocio;
	}

	// Siempre JSON, nunca texto pelado: un salto de linea dentro del dato parte el evento SSE
	// en dos y el pedazo se pierde. Serializado, el salto viaja escapado.
	private void enviar(SseEmitter emisor, String evento, Object datos) {
		try {
			emisor.send(SseEmitter.event().name(evento).data(datos));
		} catch (Exception e) {
			// El visitante cerro la pestana a media respuesta: no es un error del servidor.
			emisor.completeWithError(e);
		}
	}

}
