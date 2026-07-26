package com.vera.rag;

import java.util.List;

import org.springframework.stereotype.Service;

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

	public RagService(RecuperadorService recuperador, ProveedorChat chat, TenantRepository tenants) {
		this.recuperador = recuperador;
		this.chat = chat;
		this.tenants = tenants;
	}

	public record Fuente(int numero, String titulo, String url, double distancia) {
	}

	public record Resultado(String respuesta, List<Fuente> fuentes, boolean sinInformacion, int tokens,
			String modelo) {
	}

	public Resultado responder(String pregunta) {
		Tenant negocio = tenants.findById(TenantContext.actual())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("negocio no encontrado"));

		List<RecuperadorService.Recuperado> recuperados = recuperador.recuperar(pregunta, FRAGMENTOS);

		// Nada relevante: se responde sin gastar una sola llamada al modelo. Es la defensa
		// mas barata contra inventar, y ademas ahorra dinero en cada pregunta fuera de tema.
		if (recuperados.isEmpty()) {
			return new Resultado(sinInformacion(negocio), List.of(), true, 0, "ninguno");
		}

		String contexto = numerar(recuperados);
		ProveedorChat.Respuesta salida = chat.responder(instrucciones(negocio, contexto), pregunta);

		List<Fuente> fuentes = new java.util.ArrayList<>();
		for (int i = 0; i < recuperados.size(); i++) {
			RecuperadorService.Recuperado r = recuperados.get(i);
			fuentes.add(new Fuente(i + 1, r.titulo(), r.url(), r.distancia()));
		}
		return new Resultado(salida.texto(), fuentes, false, salida.tokens(), chat.modelo());
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
