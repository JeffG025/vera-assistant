package com.vera.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.vera.dominio.Conversacion;
import com.vera.dominio.ConversacionRepository;
import com.vera.dominio.Mensaje;
import com.vera.dominio.MensajeFuente;
import com.vera.dominio.MensajeFuenteRepository;
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
	private final MensajeFuenteRepository mensajeFuentes;

	// El modelo cita con [1], [2]... Es la misma marca que el widget usa para desplegar fuentes.
	private static final Pattern MARCA_DE_CITA = Pattern.compile("\\[(\\d+)\\]");
	private final com.vera.negocio.CuotaService cuotas;
	private final com.vera.negocio.UsoService uso;
	private final CacheRespuestas cache;
	private final LimitadorTasa limitador;

	public RagService(RecuperadorService recuperador, ProveedorChat chat, TenantRepository tenants,
			ConversacionRepository conversaciones, MensajeRepository mensajes,
			MensajeFuenteRepository mensajeFuentes,
			com.vera.negocio.CuotaService cuotas, com.vera.negocio.UsoService uso,
			CacheRespuestas cache, LimitadorTasa limitador) {
		this.recuperador = recuperador;
		this.chat = chat;
		this.tenants = tenants;
		this.conversaciones = conversaciones;
		this.mensajes = mensajes;
		this.mensajeFuentes = mensajeFuentes;
		this.cuotas = cuotas;
		this.uso = uso;
		this.cache = cache;
		this.limitador = limitador;
	}

	public record Fuente(int numero, String titulo, String url, double distancia) {
	}

	public record Resultado(String conversacionId, String mensajeId, String respuesta,
			List<Fuente> fuentes, boolean sinInformacion, int tokens, String modelo,
			String consultaUsada) {
	}

	// El canal se guarda con la conversacion: la bandeja del bloque 6 necesita distinguir al
	// visitante que escribio desde el widget de una prueba hecha en el panel.
	public Resultado responder(String pregunta, String conversacionId, String canal) {
		return procesar(pregunta, conversacionId, null, canal);
	}

	public Resultado responderEnStream(String pregunta, String conversacionId, Consumer<String> alRecibir,
			String canal) {
		return procesar(pregunta, conversacionId, alRecibir, canal);
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
			return new Resultado(null, null, sinInformacion(negocio), List.of(), true, 0, "ninguno",
					pregunta);
		}

		ProveedorChat.Respuesta salida = chat.responder(instrucciones(negocio, numerar(recuperados)),
				List.of(), pregunta);

		List<Fuente> fuentes = new ArrayList<>();
		for (int i = 0; i < recuperados.size(); i++) {
			RecuperadorService.Recuperado r = recuperados.get(i);
			fuentes.add(new Fuente(i + 1, r.titulo(), r.url(), r.distancia()));
		}
		return new Resultado(null, null, salida.texto(), fuentes, false, salida.tokens(), chat.modelo(),
				pregunta);
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

	private Resultado procesar(String pregunta, String conversacionId, Consumer<String> alRecibir,
			String canal) {
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
				return desdeCache(guardada.get(), pregunta, alRecibir, canal);
			}
		}

		Conversacion conversacion = resolver(conversacionId, canal);

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
			Mensaje vacio = guardar(conversacion, "ASISTENTE", texto, 0, true);
			if (primerTurno) {
				cache.guardar(inquilino, pregunta,
						new CacheRespuestas.Guardada(texto, List.of(), List.of(), true));
			}
			return new Resultado(conversacion.getId().toString(), vacio.getId().toString(), texto,
					List.of(), true, 0, "ninguno", consulta);
		}

		String instrucciones = instrucciones(negocio, numerar(recuperados));
		List<ProveedorChat.Turno> turnos = aTurnos(previos);
		ProveedorChat.Respuesta salida = alRecibir == null
				? chat.responder(instrucciones, turnos, pregunta)
				: chat.responderEnStream(instrucciones, turnos, pregunta, alRecibir);

		Mensaje respondido = guardar(conversacion, "ASISTENTE", salida.texto(), salida.tokens(), false);
		conversaciones.save(conversacion);

		List<Fuente> fuentes = new ArrayList<>();
		for (int i = 0; i < recuperados.size(); i++) {
			RecuperadorService.Recuperado r = recuperados.get(i);
			fuentes.add(new Fuente(i + 1, r.titulo(), r.url(), r.distancia()));
		}
		List<String> textos = recuperados.stream().map(RecuperadorService.Recuperado::texto).toList();
		guardarFuentes(respondido, fuentes, textos, salida.texto());

		if (primerTurno) {
			cache.guardar(inquilino, pregunta,
					new CacheRespuestas.Guardada(salida.texto(), fuentes, textos, false));
		}
		return new Resultado(conversacion.getId().toString(), respondido.getId().toString(),
				salida.texto(), fuentes, false, salida.tokens(), chat.modelo(), consulta);
	}

	// Un acierto de caché ahorra la llamada al modelo, no la contabilidad: la conversación
	// y sus mensajes se crean igual, con 0 tokens porque de verdad no se gastó ninguno.
	private Resultado desdeCache(CacheRespuestas.Guardada guardada, String pregunta,
			Consumer<String> alRecibir, String canal) {
		Conversacion conversacion = resolver(null, canal);
		guardar(conversacion, "USUARIO", pregunta, 0, false);
		Mensaje respondido = guardar(conversacion, "ASISTENTE", guardada.respuesta(), 0,
				guardada.sinInformacion());
		guardarFuentes(respondido, guardada.fuentes(), guardada.textos(), guardada.respuesta());
		if (alRecibir != null) {
			alRecibir.accept(guardada.respuesta());
		}
		return new Resultado(conversacion.getId().toString(), respondido.getId().toString(),
				guardada.respuesta(), guardada.fuentes(), guardada.sinInformacion(), 0, "cache", pregunta);
	}

	private Conversacion resolver(String id, String canal) {
		if (id != null && !id.isBlank()) {
			return conversaciones.findById(UUID.fromString(id))
					.orElseThrow(() -> ExcepcionNegocio.noEncontrado("conversacion no encontrada"));
		}
		Conversacion nueva = new Conversacion();
		nueva.setTenantId(TenantContext.actual());
		nueva.setCanal(canal);
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

	private Mensaje guardar(Conversacion conversacion, String rol, String texto, int tokens,
			boolean sinInfo) {
		Mensaje mensaje = new Mensaje();
		mensaje.setTenantId(conversacion.getTenantId());
		mensaje.setConversacionId(conversacion.getId());
		mensaje.setRol(rol);
		mensaje.setTexto(texto);
		mensaje.setTokens(tokens);
		mensaje.setSinInformacion(sinInfo);
		return mensajes.save(mensaje);
	}

	/**
	 * Deja constancia de con que se respondio. Lo que el modelo cito con su [n] se marca aparte:
	 * saber si recupero cinco y usó dos separa un fallo de busqueda de uno de redaccion.
	 */
	private void guardarFuentes(Mensaje mensaje, List<Fuente> fuentes, List<String> textos,
			String respuesta) {
		Set<Integer> citadas = new HashSet<>();
		Matcher marcas = MARCA_DE_CITA.matcher(respuesta);
		while (marcas.find()) {
			citadas.add(Integer.parseInt(marcas.group(1)));
		}
		List<MensajeFuente> filas = new ArrayList<>();
		for (int i = 0; i < fuentes.size(); i++) {
			Fuente f = fuentes.get(i);
			MensajeFuente fila = new MensajeFuente();
			fila.setTenantId(mensaje.getTenantId());
			fila.setMensajeId(mensaje.getId());
			fila.setNumero((short) f.numero());
			fila.setTitulo(f.titulo());
			fila.setUrl(f.url());
			fila.setTexto(textos.get(i));
			fila.setDistancia((float) f.distancia());
			fila.setCitada(citadas.contains(f.numero()));
			filas.add(fila);
		}
		mensajeFuentes.saveAll(filas);
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
