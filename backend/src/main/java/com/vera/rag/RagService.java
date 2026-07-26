package com.vera.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.vera.dominio.Conversacion;
import com.vera.dominio.ConversacionRepository;
import com.vera.dominio.Mensaje;
import com.vera.dominio.MensajeRepository;
import com.vera.dominio.Tenant;
import com.vera.dominio.TenantRepository;
import com.vera.error.ExcepcionNegocio;
import com.vera.tenancy.TenantContext;

@Service
public class RagService {

	private static final int FRAGMENTOS = 5;

	private final RecuperadorService recuperador;
	private final ProveedorChat chat;
	private final TenantRepository tenants;
	private final ConversacionRepository conversaciones;
	private final MensajeRepository mensajes;
	private final com.vera.negocio.CuotaService cuotas;
	private final com.vera.negocio.UsoService uso;
	private final CacheRespuestas cache;
	private final LimitadorTasa limitador;

	public RagService(RecuperadorService recuperador, ProveedorChat chat, TenantRepository tenants,
			ConversacionRepository conversaciones, MensajeRepository mensajes,
			com.vera.negocio.CuotaService cuotas, com.vera.negocio.UsoService uso,
			CacheRespuestas cache, LimitadorTasa limitador) {
		this.recuperador = recuperador;
		this.chat = chat;
		this.tenants = tenants;
		this.conversaciones = conversaciones;
		this.mensajes = mensajes;
		this.cuotas = cuotas;
		this.uso = uso;
		this.cache = cache;
		this.limitador = limitador;
	}

	public record Fuente(int numero, String titulo, String url, double distancia) {
	}

	public record Resultado(String conversacionId, String respuesta, List<Fuente> fuentes,
			boolean sinInformacion, int tokens, String modelo, String consultaUsada) {
	}

	public Resultado responder(String pregunta, String conversacionId) {
		return procesar(pregunta, conversacionId, null);
	}

	public Resultado responderEnStream(String pregunta, String conversacionId, Consumer<String> alRecibir) {
		return procesar(pregunta, conversacionId, alRecibir);
	}

	/**
	 * Responde sin dejar rastro: sin caché, sin límite de tasa, sin cuota y sin guardar la
	 * conversación. Lo usa la evaluación, que no debe ensuciar el consumo del negocio ni
	 * medir respuestas cacheadas de una versión anterior del prompt.
	 */
	public Resultado responderSinRastro(String pregunta) {
		Tenant negocio = tenants.findById(TenantContext.actual())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("negocio no encontrado"));

		List<RecuperadorService.Recuperado> recuperados = recuperador.recuperar(pregunta, FRAGMENTOS);
		if (recuperados.isEmpty()) {
			return new Resultado(null, sinInformacion(negocio), List.of(), true, 0, "ninguno", pregunta);
		}

		ProveedorChat.Respuesta salida = chat.responder(instrucciones(negocio, numerar(recuperados)),
				List.of(), pregunta);

		List<Fuente> fuentes = new ArrayList<>();
		for (int i = 0; i < recuperados.size(); i++) {
			RecuperadorService.Recuperado r = recuperados.get(i);
			fuentes.add(new Fuente(i + 1, r.titulo(), r.url(), r.distancia()));
		}
		return new Resultado(null, salida.texto(), fuentes, false, salida.tokens(), chat.modelo(), pregunta);
	}

	/** Juez de fidelidad: compara la respuesta obtenida contra la esperada. */
	public String juzgar(String pregunta, String esperada, String obtenida) {
		String sistema = """
				Eres un evaluador estricto. Te doy una pregunta, la respuesta correcta y la respuesta
				que dio un asistente. Decide si la respuesta del asistente dice lo mismo que la correcta
				en el dato esencial. Ignora diferencias de redaccion, cortesia o citas.

				Responde en una sola linea con este formato exacto:
				SI|motivo breve   o   NO|motivo breve
				""";
		String entrada = "PREGUNTA: %s\nRESPUESTA CORRECTA: %s\nRESPUESTA DEL ASISTENTE: %s"
				.formatted(pregunta, esperada, obtenida);
		return chat.responder(sistema, List.of(), entrada).texto().strip();
	}

	private Resultado procesar(String pregunta, String conversacionId, Consumer<String> alRecibir) {
		UUID inquilino = TenantContext.actual();
		Tenant negocio = tenants.findById(inquilino)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("negocio no encontrado"));

		limitador.exigirCupo(inquilino.toString());
		cuotas.exigirMensajesDisponibles(negocio.getPlan(), (int) uso.preguntasDelMes());

		boolean primerTurno = conversacionId == null || conversacionId.isBlank();

		// El caché solo aplica al primer turno: con historial la respuesta depende del hilo.
		if (primerTurno) {
			var guardada = cache.buscar(inquilino, pregunta);
			if (guardada.isPresent()) {
				return desdeCache(guardada.get(), pregunta, alRecibir);
			}
		}

		Conversacion conversacion = resolver(conversacionId);

		List<Mensaje> previos = historial(conversacion.getId());
		guardar(conversacion, "USUARIO", pregunta, 0, false);

		// "¿y ese cuánto cuesta?" no se puede buscar tal cual: sin el hilo previo no significa nada.
		String consulta = previos.isEmpty() ? pregunta : reescribir(pregunta, previos);

		List<RecuperadorService.Recuperado> recuperados = recuperador.recuperar(consulta, FRAGMENTOS);

		// Nada relevante: se responde sin gastar una sola llamada al modelo. Es la defensa mas
		// barata contra inventar y ademas ahorra dinero en cada pregunta fuera de tema.
		if (recuperados.isEmpty()) {
			String texto = sinInformacion(negocio);
			if (alRecibir != null) {
				alRecibir.accept(texto);
			}
			guardar(conversacion, "ASISTENTE", texto, 0, true);
			if (primerTurno) {
				cache.guardar(inquilino, pregunta, new CacheRespuestas.Guardada(texto, List.of(), true));
			}
			return new Resultado(conversacion.getId().toString(), texto, List.of(), true, 0, "ninguno", consulta);
		}

		String instrucciones = instrucciones(negocio, numerar(recuperados));
		List<ProveedorChat.Turno> turnos = aTurnos(previos);
		ProveedorChat.Respuesta salida = alRecibir == null
				? chat.responder(instrucciones, turnos, pregunta)
				: chat.responderEnStream(instrucciones, turnos, pregunta, alRecibir);

		guardar(conversacion, "ASISTENTE", salida.texto(), salida.tokens(), false);
		conversaciones.save(conversacion);

		List<Fuente> fuentes = new ArrayList<>();
		for (int i = 0; i < recuperados.size(); i++) {
			RecuperadorService.Recuperado r = recuperados.get(i);
			fuentes.add(new Fuente(i + 1, r.titulo(), r.url(), r.distancia()));
		}
		if (primerTurno) {
			cache.guardar(inquilino, pregunta, new CacheRespuestas.Guardada(salida.texto(), fuentes, false));
		}
		return new Resultado(conversacion.getId().toString(), salida.texto(), fuentes, false,
				salida.tokens(), chat.modelo(), consulta);
	}

	// Un acierto de caché ahorra la llamada al modelo, no la contabilidad: la conversación
	// y sus mensajes se crean igual, con 0 tokens porque de verdad no se gastó ninguno.
	private Resultado desdeCache(CacheRespuestas.Guardada guardada, String pregunta,
			Consumer<String> alRecibir) {
		Conversacion conversacion = resolver(null);
		guardar(conversacion, "USUARIO", pregunta, 0, false);
		guardar(conversacion, "ASISTENTE", guardada.respuesta(), 0, guardada.sinInformacion());
		if (alRecibir != null) {
			alRecibir.accept(guardada.respuesta());
		}
		return new Resultado(conversacion.getId().toString(), guardada.respuesta(), guardada.fuentes(),
				guardada.sinInformacion(), 0, "cache", pregunta);
	}

	private Conversacion resolver(String id) {
		if (id != null && !id.isBlank()) {
			return conversaciones.findById(UUID.fromString(id))
					.orElseThrow(() -> ExcepcionNegocio.noEncontrado("conversacion no encontrada"));
		}
		Conversacion nueva = new Conversacion();
		nueva.setTenantId(TenantContext.actual());
		nueva.setCanal("PANEL");
		return conversaciones.save(nueva);
	}

	private List<Mensaje> historial(UUID conversacionId) {
		List<Mensaje> ultimos = new ArrayList<>(mensajes.findTop8ByConversacionIdOrderByCreadoEnDesc(conversacionId));
		Collections.reverse(ultimos);
		return ultimos;
	}

	private List<ProveedorChat.Turno> aTurnos(List<Mensaje> previos) {
		List<ProveedorChat.Turno> turnos = new ArrayList<>();
		for (Mensaje m : previos) {
			turnos.add(new ProveedorChat.Turno("USUARIO".equals(m.getRol()) ? "user" : "model", m.getTexto()));
		}
		return turnos;
	}

	private void guardar(Conversacion conversacion, String rol, String texto, int tokens, boolean sinInfo) {
		Mensaje mensaje = new Mensaje();
		mensaje.setTenantId(conversacion.getTenantId());
		mensaje.setConversacionId(conversacion.getId());
		mensaje.setRol(rol);
		mensaje.setTexto(texto);
		mensaje.setTokens(tokens);
		mensaje.setSinInformacion(sinInfo);
		mensajes.save(mensaje);
	}

	// Una llamada extra al modelo, corta y barata, que evita recuperar basura en las repreguntas.
	private String reescribir(String pregunta, List<Mensaje> previos) {
		try {
			StringBuilder hilo = new StringBuilder();
			for (Mensaje m : previos.subList(Math.max(0, previos.size() - 4), previos.size())) {
				hilo.append("USUARIO".equals(m.getRol()) ? "Cliente: " : "Asistente: ")
						.append(m.getTexto()).append("\n");
			}
			String sistema = """
					Reescribe la ultima pregunta del cliente para que se entienda por si sola,
					sin necesitar el historial. Conserva el idioma y no agregues informacion nueva.
					Devuelve UNICAMENTE la pregunta reescrita, sin comillas ni explicaciones.

					HISTORIAL:
					%s
					""".formatted(hilo.toString().strip());
			String reescrita = chat.responder(sistema, List.of(), pregunta).texto().strip();
			return reescrita.isBlank() ? pregunta : reescrita;
		} catch (RuntimeException e) {
			// Si la reescritura falla, se busca con la pregunta original: peor recuperacion, no un error.
			return pregunta;
		}
	}

	private String numerar(List<RecuperadorService.Recuperado> recuperados) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < recuperados.size(); i++) {
			RecuperadorService.Recuperado r = recuperados.get(i);
			sb.append("[").append(i + 1).append("] ");
			if (r.titulo() != null) {
				sb.append(r.titulo()).append(" — ");
			}
			sb.append(r.texto()).append("\n\n");
		}
		return sb.toString().strip();
	}

	private String instrucciones(Tenant negocio, String contexto) {
		String tono = negocio.getAsistenteTono() != null && !negocio.getAsistenteTono().isBlank()
				? negocio.getAsistenteTono()
				: "cercano y directo";
		return """
				Eres %s, el asistente de atencion de %s.

				REGLAS QUE NO PUEDES ROMPER:
				1. Responde UNICAMENTE con lo que diga el CONTEXTO de abajo.
				2. Si el CONTEXTO no tiene la respuesta, di que no cuentas con esa informacion y
				   ofrece pasar la conversacion con una persona. No adivines.
				3. Nunca inventes precios, plazos, politicas ni datos de contacto.
				4. Cita la fuente al final de cada afirmacion con su numero entre corchetes, asi: [1].
				5. Responde en %s, en tono %s, y se breve: dos o tres frases bastan.

				CONTEXTO:
				%s
				""".formatted(negocio.getAsistenteNombre(), negocio.getNombre(),
				negocio.getAsistenteIdioma(), tono, contexto);
	}

	private String sinInformacion(Tenant negocio) {
		return "No tengo esa informacion en lo que %s me compartio. "
				.formatted(negocio.getNombre())
				+ "Si quieres, te paso con una persona del equipo.";
	}

}
