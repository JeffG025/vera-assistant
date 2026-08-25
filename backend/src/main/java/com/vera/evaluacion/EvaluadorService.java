package com.vera.evaluacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vera.dominio.CasoEval;
import com.vera.dominio.CasoEvalRepository;
import com.vera.dominio.CorridaEval;
import com.vera.dominio.CorridaEvalRepository;
import com.vera.dominio.ResultadoEval;
import com.vera.dominio.ResultadoEvalRepository;
import com.vera.error.ExcepcionNegocio;
import com.vera.rag.ExcepcionCuotaLlm;
import com.vera.rag.RagService;
import com.vera.rag.RecuperadorService;
import com.vera.tenancy.TenantContext;

@Service
public class EvaluadorService {

	private final CasoEvalRepository casos;
	private final CorridaEvalRepository corridas;
	private final ResultadoEvalRepository resultados;
	private final RagService rag;
	private final RecuperadorService recuperador;

	// El free tier permite pocas llamadas por minuto y cada caso gasta dos. Un examen es
	// un proceso por lotes: mas vale que tarde tres minutos a que reporte un puntaje falso.
	@org.springframework.beans.factory.annotation.Value("${vera.eval.pausa-ms:12000}")
	private long pausaMs;

	public EvaluadorService(CasoEvalRepository casos, CorridaEvalRepository corridas,
			ResultadoEvalRepository resultados, RagService rag, RecuperadorService recuperador) {
		this.casos = casos;
		this.corridas = corridas;
		this.resultados = resultados;
		this.rag = rag;
		this.recuperador = recuperador;
	}

	public CorridaEval ejecutar(String conjunto) {
		// Por omision se corre el conjunto esencial: es el unico que cabe entero en el free tier.
		boolean soloEsenciales = !"completo".equalsIgnoreCase(conjunto);
		List<CasoEval> activos = soloEsenciales
				? casos.findByActivoTrueAndEsencialTrueOrderByCreadoEn()
				: casos.findByActivoTrueOrderByCreadoEn();
		if (activos.isEmpty()) {
			throw ExcepcionNegocio.conflicto(soloEsenciales
					? "no hay casos marcados como esenciales; marca los que deban correr en cada examen"
					: "no hay casos de evaluacion cargados");
		}

		// La corrida se persiste al final, ya con sus metricas: guardarla vacia dejaria una
		// fila incompleta en la base si el examen se cae a la mitad.
		List<ResultadoEval> filas = new java.util.ArrayList<>();
		int recOk = 0, recBase = 0, fidOk = 0, fidBase = 0, absOk = 0, absBase = 0, tokens = 0, errores = 0;
		String modelo = null;
		boolean cortada = false;
		int indice = 0;

		for (; indice < activos.size(); indice++) {
			CasoEval caso = activos.get(indice);
			// El examen dispara dos llamadas por caso y el free tier corta por minuto.
			// Un examen es un proceso por lotes: puede permitirse ir despacio.
			if (indice > 0) {
				pausar();
			}

			RagService.Resultado r;
			try {
				r = rag.responderSinRastro(caso.getPregunta());
			} catch (RuntimeException e) {
				// Un caso caido no debe tirar el examen entero: se marca y se sigue.
				filas.add(fallo(caso, "no se pudo evaluar: " + e.getMessage()));
				errores++;
				if (esCuotaDiaria(e)) {
					cortada = true;
					break;
				}
				continue;
			}
			tokens += r.tokens();
			if (r.modelo() != null && !"ninguno".equals(r.modelo())) {
				modelo = r.modelo();
			}

			ResultadoEval fila = new ResultadoEval();
			fila.setTenantId(TenantContext.actual());
			fila.setPregunta(caso.getPregunta());
			fila.setRespuestaObtenida(r.respuesta());
			fila.setRespuestaEsperada(caso.getRespuestaEsperada());
			fila.setFuenteEsperada(caso.getFuenteEsperada());
			fila.setDebeSaber(caso.isDebeSaber());
			fila.setSinInformacion(r.sinInformacion());
			fila.setFuentesObtenidas(r.fuentes().stream().map(RagService.Fuente::titulo)
					.filter(t -> t != null).collect(Collectors.joining(", ")));

			if (!caso.isDebeSaber()) {
				// Caso negativo: acertar es callarse. Si contesta algo, invento.
				absBase++;
				boolean bien = r.sinInformacion();
				if (bien) {
					absOk++;
				}
				fila.setMotivo(bien ? "admitio no saber, como se esperaba"
						: "respondio algo cuando no deberia: posible invencion");
			} else {
				// Recuperacion: ¿aparecio la fuente esperada entre las recuperadas?
				if (caso.getFuenteEsperada() != null) {
					recBase++;
					boolean trajo = r.fuentes().stream()
							.anyMatch(f -> caso.getFuenteEsperada().equalsIgnoreCase(f.titulo()));
					fila.setRecuperacionOk(trajo);
					if (trajo) {
						recOk++;
					}
				}
				// Fidelidad: la juzga el modelo contra la respuesta esperada.
				fidBase++;
				if (r.sinInformacion()) {
					fila.setFidelidadOk(false);
					fila.setMotivo("dijo no saber algo que si esta documentado");
				} else if (caso.getRespuestaEsperada() == null) {
					fila.setFidelidadOk(null);
					fidBase--;
					fila.setMotivo("sin respuesta esperada: no se puede juzgar");
				} else {
					// El juez es otra llamada al modelo y tambien puede toparse con el limite.
					pausar();
					try {
						String veredicto = rag.juzgar(caso.getPregunta(), caso.getRespuestaEsperada(),
								r.respuesta());
						boolean bien = veredicto.toUpperCase(Locale.ROOT).startsWith("SI");
						fila.setFidelidadOk(bien);
						fila.setMotivo(veredicto.contains("|")
								? veredicto.substring(veredicto.indexOf('|') + 1).strip()
								: veredicto);
						if (bien) {
							fidOk++;
						}
					} catch (RuntimeException e) {
						fila.setFidelidadOk(null);
						fidBase--;
						errores++;
						fila.setMotivo("no se pudo juzgar: " + e.getMessage());
						cortada = esCuotaDiaria(e);
					}
				}
			}
			filas.add(fila);
			if (cortada) {
				break;
			}
		}

		// Sin cuota no queda examen que aplicar. Los casos que se quedaron sin correr entran
		// igual como error: si no, una corrida cortada al tercer caso se veria como un examen
		// completo de tres preguntas.
		if (cortada) {
			for (CasoEval pendiente : activos.subList(Math.min(indice + 1, activos.size()), activos.size())) {
				filas.add(fallo(pendiente, "no se ejecuto: la corrida se corto al agotarse la cuota diaria"));
				errores++;
			}
		}

		CorridaEval guardada = new CorridaEval();
		guardada.setTenantId(TenantContext.actual());
		guardada.setConjunto(soloEsenciales ? "esencial" : "completo");
		guardada.setUmbral(BigDecimal.valueOf(recuperador.umbral()));
		guardada.setTotal(activos.size());
		guardada.setRecuperacionOk(recOk);
		guardada.setRecuperacionBase(recBase);
		guardada.setFidelidadOk(fidOk);
		guardada.setFidelidadBase(fidBase);
		guardada.setAbstencionOk(absOk);
		guardada.setAbstencionBase(absBase);
		guardada.setTokens(tokens);
		guardada.setModelo(modelo);
		guardada.setErrores(errores);
		// Los errores entran al denominador: si no, medio examen caido se veria como 100%.
		guardada.setPuntaje(puntaje(recOk + fidOk + absOk, recBase + fidBase + absBase + errores));
		CorridaEval persistida = corridas.save(guardada);

		filas.forEach(f -> f.setCorridaId(persistida.getId()));
		resultados.saveAll(filas);
		return persistida;
	}

	// Fila de un caso que no se pudo evaluar; sin ella el caso desapareceria del examen.
	private ResultadoEval fallo(CasoEval caso, String motivo) {
		ResultadoEval falla = new ResultadoEval();
		falla.setTenantId(TenantContext.actual());
		falla.setPregunta(caso.getPregunta());
		falla.setRespuestaEsperada(caso.getRespuestaEsperada());
		falla.setFuenteEsperada(caso.getFuenteEsperada());
		falla.setDebeSaber(caso.isDebeSaber());
		falla.setSinInformacion(false);
		falla.setMotivo(motivo);
		return falla;
	}

	// La cuota diaria no se recupera esperando: seguir preguntando solo repite el mismo error.
	private boolean esCuotaDiaria(RuntimeException e) {
		return e instanceof ExcepcionCuotaLlm cuota && cuota.esDiaria();
	}

	private void pausar() {
		try {
			Thread.sleep(pausaMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// Puntaje unico sobre todas las comprobaciones: una sola cifra que se puede enseñar.
	private BigDecimal puntaje(int aciertos, int base) {
		if (base == 0) {
			return BigDecimal.ZERO;
		}
		return BigDecimal.valueOf(aciertos * 100.0 / base).setScale(2, RoundingMode.HALF_UP);
	}

	public List<CorridaEval> historial() {
		return corridas.findTop20ByOrderByCreadoEnDesc();
	}

	public enum Veredicto {
		ACIERTO, FALLO, ERROR
	}

	public record Cambio(String pregunta, Veredicto antes, Veredicto despues, String motivo) {
	}

	public record Comparacion(CorridaEval base, CorridaEval nueva, String veredicto,
			BigDecimal deltaPuntaje, int mejoraron, int empeoraron, int iguales, List<Cambio> cambios) {
	}

	/**
	 * Compara dos corridas caso por caso. El puntaje global sube y baja por muchas razones;
	 * lo que dice si un cambio de prompt sirvió o no es qué preguntas dejaron de responderse bien.
	 */
	public Comparacion comparar(java.util.UUID idNueva, java.util.UUID idBase) {
		CorridaEval nueva = corridas.findById(idNueva)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("corrida no encontrada"));
		CorridaEval base = corridas.findById(idBase)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("corrida de referencia no encontrada"));

		// Comparar un examen de 6 preguntas contra uno de 16 daria un delta sin significado.
		if (!nueva.getConjunto().equals(base.getConjunto())) {
			throw ExcepcionNegocio.conflicto("no se comparan conjuntos distintos: la corrida es '"
					+ nueva.getConjunto() + "' y la referencia '" + base.getConjunto() + "'");
		}

		Map<String, ResultadoEval> antes = porPregunta(base.getId());
		Map<String, ResultadoEval> despues = porPregunta(nueva.getId());

		List<Cambio> empeoraron = new java.util.ArrayList<>();
		List<Cambio> mejoraron = new java.util.ArrayList<>();
		List<Cambio> sinPar = new java.util.ArrayList<>();
		int iguales = 0;

		for (Map.Entry<String, ResultadoEval> entrada : despues.entrySet()) {
			ResultadoEval previo = antes.get(entrada.getKey());
			Veredicto ahora = veredicto(entrada.getValue());
			if (previo == null) {
				sinPar.add(new Cambio(entrada.getKey(), null, ahora, "caso nuevo en el conjunto"));
				continue;
			}
			Veredicto era = veredicto(previo);
			if (era == ahora) {
				iguales++;
			} else if (ahora == Veredicto.ACIERTO) {
				mejoraron.add(new Cambio(entrada.getKey(), era, ahora, entrada.getValue().getMotivo()));
			} else {
				empeoraron.add(new Cambio(entrada.getKey(), era, ahora, entrada.getValue().getMotivo()));
			}
		}
		// Un caso que desaparecio del conjunto tiene que verse: si no, quitar las preguntas
		// dificiles subiria el puntaje y la comparacion lo aplaudiria.
		for (String pregunta : antes.keySet()) {
			if (!despues.containsKey(pregunta)) {
				sinPar.add(new Cambio(pregunta, veredicto(antes.get(pregunta)), null,
						"el caso ya no esta en el conjunto"));
			}
		}

		// Una regresion pesa mas que una mejora: es lo que hay que mirar antes de publicar.
		String veredictoGlobal = !empeoraron.isEmpty() ? "REGRESION"
				: !mejoraron.isEmpty() ? "MEJORO" : "IGUAL";

		List<Cambio> cambios = new java.util.ArrayList<>(empeoraron);
		cambios.addAll(mejoraron);
		cambios.addAll(sinPar);
		return new Comparacion(base, nueva, veredictoGlobal,
				nueva.getPuntaje().subtract(base.getPuntaje()),
				mejoraron.size(), empeoraron.size(), iguales, cambios);
	}

	private Map<String, ResultadoEval> porPregunta(java.util.UUID corridaId) {
		Map<String, ResultadoEval> mapa = new java.util.LinkedHashMap<>();
		for (ResultadoEval fila : resultados.findByCorridaIdOrderByCreadoEn(corridaId)) {
			mapa.putIfAbsent(fila.getPregunta(), fila);
		}
		return mapa;
	}

	// Un caso no deja tres notas sueltas sino una: quedo bien o no quedo bien. Es lo unico
	// que se puede comparar de una corrida a otra.
	static Veredicto veredicto(ResultadoEval r) {
		String motivo = r.getMotivo() == null ? "" : r.getMotivo();
		if (motivo.startsWith("no se pudo") || motivo.startsWith("no se ejecuto")) {
			return Veredicto.ERROR;
		}
		if (!r.isDebeSaber()) {
			return r.isSinInformacion() ? Veredicto.ACIERTO : Veredicto.FALLO;
		}
		// null es "no aplica" (por ejemplo, un caso sin respuesta esperada): no cuenta como fallo.
		boolean falloAlgo = Boolean.FALSE.equals(r.getRecuperacionOk())
				|| Boolean.FALSE.equals(r.getFidelidadOk());
		return falloAlgo ? Veredicto.FALLO : Veredicto.ACIERTO;
	}

	public List<ResultadoEval> detalle(java.util.UUID corridaId) {
		return resultados.findByCorridaIdOrderByCreadoEn(corridaId);
	}

}
