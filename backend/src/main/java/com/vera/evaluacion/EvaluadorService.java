package com.vera.evaluacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vera.dominio.CasoEval;
import com.vera.dominio.CasoEvalRepository;
import com.vera.dominio.CorridaEval;
import com.vera.dominio.CorridaEvalRepository;
import com.vera.dominio.ResultadoEval;
import com.vera.dominio.ResultadoEvalRepository;
import com.vera.error.ExcepcionNegocio;
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

	public CorridaEval ejecutar() {
		List<CasoEval> activos = casos.findByActivoTrueOrderByCreadoEn();
		if (activos.isEmpty()) {
			throw ExcepcionNegocio.conflicto("no hay casos de evaluacion cargados");
		}

		// La corrida se persiste al final, ya con sus metricas: guardarla vacia dejaria una
		// fila incompleta en la base si el examen se cae a la mitad.
		List<ResultadoEval> filas = new java.util.ArrayList<>();
		int recOk = 0, recBase = 0, fidOk = 0, fidBase = 0, absOk = 0, absBase = 0, tokens = 0, errores = 0;
		String modelo = null;

		for (CasoEval caso : activos) {
			// El examen dispara dos llamadas por caso y el free tier corta por minuto.
			// Un examen es un proceso por lotes: puede permitirse ir despacio.
			pausar();

			RagService.Resultado r;
			try {
				r = rag.responderSinRastro(caso.getPregunta());
			} catch (RuntimeException e) {
				// Un caso caido no debe tirar el examen entero: se marca y se sigue.
				ResultadoEval falla = new ResultadoEval();
				falla.setTenantId(TenantContext.actual());
				falla.setPregunta(caso.getPregunta());
				falla.setRespuestaEsperada(caso.getRespuestaEsperada());
				falla.setFuenteEsperada(caso.getFuenteEsperada());
				falla.setDebeSaber(caso.isDebeSaber());
				falla.setSinInformacion(false);
				falla.setMotivo("no se pudo evaluar: " + e.getMessage());
				filas.add(falla);
				errores++;
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
					}
				}
			}
			filas.add(fila);
		}

		CorridaEval guardada = new CorridaEval();
		guardada.setTenantId(TenantContext.actual());
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

	public List<ResultadoEval> detalle(java.util.UUID corridaId) {
		return resultados.findByCorridaIdOrderByCreadoEn(corridaId);
	}

}
