package com.vera.bandeja;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vera.dominio.Conversacion;
import com.vera.dominio.ConversacionRepository;
import com.vera.dominio.Mensaje;
import com.vera.dominio.MensajeFuente;
import com.vera.dominio.MensajeFuenteRepository;
import com.vera.dominio.MensajeRepository;
import com.vera.dominio.ResumenConversacion;
import com.vera.error.ExcepcionNegocio;

/**
 * La bandeja: lo que el negocio lee para saber qué le preguntan y qué está contestando mal.
 * Todo va filtrado por RLS, así que aquí no se nombra al inquilino en ninguna consulta.
 */
@RestController
@RequestMapping("/api/conversaciones")
public class ConversacionController {

	private static final int POR_PAGINA = 20;

	// Extremos en vez de nulos: la consulta necesita parametros siempre tipados (ver el
	// repositorio). Ninguna conversacion cae fuera de este rango.
	private static final OffsetDateTime PRINCIPIO = OffsetDateTime.parse("2000-01-01T00:00:00Z");
	private static final OffsetDateTime FIN = OffsetDateTime.parse("2999-01-01T00:00:00Z");

	private final ConversacionRepository conversaciones;
	private final MensajeRepository mensajes;
	private final MensajeFuenteRepository fuentes;

	public ConversacionController(ConversacionRepository conversaciones, MensajeRepository mensajes,
			MensajeFuenteRepository fuentes) {
		this.conversaciones = conversaciones;
		this.mensajes = mensajes;
		this.fuentes = fuentes;
	}

	public record EnLista(String id, String canal, String creadoEn, String actualizadoEn,
			String primeraPregunta, long preguntas, long sinRespuesta, long positivas,
			long negativas) {
	}

	public record Bandeja(List<EnLista> filas, int pagina, int paginas, long total) {
	}

	public record FuenteDeMensaje(int numero, String titulo, String url, String texto,
			double distancia, boolean citada) {
	}

	public record MensajeDetalle(String id, String rol, String texto, boolean sinInformacion,
			Short calificacion, String creadoEn, List<FuenteDeMensaje> fuentes) {
	}

	public record Detalle(String id, String canal, String creadoEn, List<MensajeDetalle> mensajes) {
	}

	@GetMapping
	public Bandeja lista(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) String canal,
			@RequestParam(defaultValue = "false") boolean sinRespuesta,
			@RequestParam(required = false) Short calificacion,
			@RequestParam(defaultValue = "0") int pagina) {

		// "hasta" lo escribe una persona pensando en un día completo, no en su medianoche.
		Page<ResumenConversacion> pag = conversaciones.bandeja(
				desde == null ? PRINCIPIO : alInicioDelDia(desde),
				hasta == null ? FIN : alInicioDelDia(hasta.plusDays(1)),
				canal == null ? "" : canal.trim(), sinRespuesta,
				calificacion == null ? 0 : calificacion,
				PageRequest.of(Math.max(0, pagina), POR_PAGINA));

		Map<UUID, String> primeras = primerasPreguntas(pag.getContent().stream()
				.map(ResumenConversacion::getId).toList());

		List<EnLista> filas = pag.getContent().stream()
				.map(r -> new EnLista(r.getId().toString(), r.getCanal(), r.getCreadoEn().toString(),
						r.getActualizadoEn().toString(), primeras.get(r.getId()), r.getPreguntas(),
						r.getSinRespuesta(), r.getPositivas(), r.getNegativas()))
				.toList();

		return new Bandeja(filas, pag.getNumber(), pag.getTotalPages(), pag.getTotalElements());
	}

	@GetMapping("/{id}")
	public Detalle detalle(@PathVariable UUID id) {
		Conversacion conversacion = conversaciones.findById(id)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("conversacion no encontrada"));

		List<Mensaje> hilo = mensajes.findByConversacionIdOrderByCreadoEn(id);

		// Una consulta para todas las fuentes del hilo; pedirlas mensaje a mensaje seria un N+1
		// justo en la pantalla con mas mensajes.
		Map<UUID, List<FuenteDeMensaje>> porMensaje = new HashMap<>();
		for (MensajeFuente f : fuentes.findByMensajeIdInOrderByNumero(hilo.stream().map(Mensaje::getId).toList())) {
			porMensaje.computeIfAbsent(f.getMensajeId(), k -> new java.util.ArrayList<>())
					.add(new FuenteDeMensaje(f.getNumero(), f.getTitulo(), f.getUrl(), f.getTexto(),
							f.getDistancia(), f.isCitada()));
		}

		List<MensajeDetalle> detalles = hilo.stream()
				.map(m -> new MensajeDetalle(m.getId().toString(), m.getRol(), m.getTexto(),
						m.isSinInformacion(), m.getCalificacion(), m.getCreadoEn().toString(),
						porMensaje.getOrDefault(m.getId(), List.of())))
				.toList();

		return new Detalle(conversacion.getId().toString(), conversacion.getCanal(),
				conversacion.getCreadoEn().toString(), detalles);
	}

	// La primera pregunta es lo que identifica una conversacion de un vistazo; sacarla en la
	// consulta de la lista complicaba el JPQL mas de lo que ahorra.
	private Map<UUID, String> primerasPreguntas(List<UUID> ids) {
		Map<UUID, String> primeras = new HashMap<>();
		if (ids.isEmpty()) {
			return primeras;
		}
		for (Mensaje m : mensajes.preguntasDe(ids)) {
			primeras.putIfAbsent(m.getConversacionId(), m.getTexto());
		}
		return primeras;
	}

	private static OffsetDateTime alInicioDelDia(LocalDate dia) {
		return dia.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
	}

}
