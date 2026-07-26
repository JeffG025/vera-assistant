package com.vera.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vera.fuentes.FragmentoDao;
import com.vera.fuentes.ProveedorEmbeddings;

/**
 * Recupera los fragmentos que responden una pregunta combinando dos búsquedas:
 * la vectorial entiende sinónimos, la textual encuentra nombres propios y claves exactas.
 */
@Service
public class RecuperadorService {

	// Constante estándar de Reciprocal Rank Fusion: amortigua el peso de los primeros puestos.
	private static final int K_RRF = 60;

	private final FragmentoDao fragmentos;
	private final ProveedorEmbeddings embeddings;
	private final double umbral;

	public RecuperadorService(FragmentoDao fragmentos, ProveedorEmbeddings embeddings,
			@Value("${vera.rag.umbral-distancia:0.32}") double umbral) {
		this.fragmentos = fragmentos;
		this.embeddings = embeddings;
		this.umbral = umbral;
	}

	public record Recuperado(String texto, String titulo, String url, double distancia) {
	}

	public List<Recuperado> recuperar(String pregunta, int cuantos) {
		int ancho = cuantos * 3;
		List<FragmentoDao.Candidato> vectorial = fragmentos.buscarVectorial(embeddings.generarUno(pregunta), ancho);
		List<FragmentoDao.Candidato> textual = fragmentos.buscarTextual(pregunta, ancho);

		// Sin nada suficientemente cerca y sin coincidencia exacta de términos, no hay respuesta
		// posible: mejor devolver vacío que dejar que el modelo improvise con ruido.
		boolean hayCerca = !vectorial.isEmpty() && vectorial.get(0).distancia() <= umbral;
		if (!hayCerca && textual.isEmpty()) {
			return List.of();
		}

		Map<String, Double> puntajes = new LinkedHashMap<>();
		Map<String, FragmentoDao.Candidato> porId = new LinkedHashMap<>();
		acumular(puntajes, porId, vectorial);
		acumular(puntajes, porId, textual);

		List<Recuperado> salida = new ArrayList<>();
		puntajes.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(cuantos)
				.forEach(e -> {
					FragmentoDao.Candidato c = porId.get(e.getKey());
					salida.add(new Recuperado(c.texto(), c.titulo(), c.url(), c.distancia()));
				});
		return salida;
	}

	private void acumular(Map<String, Double> puntajes, Map<String, FragmentoDao.Candidato> porId,
			List<FragmentoDao.Candidato> lista) {
		for (int i = 0; i < lista.size(); i++) {
			FragmentoDao.Candidato c = lista.get(i);
			puntajes.merge(c.id(), 1.0 / (K_RRF + i + 1), Double::sum);
			porId.putIfAbsent(c.id(), c);
		}
	}

	public double umbral() {
		return umbral;
	}

}
