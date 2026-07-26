package com.vera.rag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.vera.error.ExcepcionNegocio;

/**
 * Ventana deslizante en memoria. Frena el abuso barato (alguien con un bucle) sin depender
 * de infraestructura extra. Con varias instancias haria falta moverlo a Redis.
 */
@Component
public class LimitadorTasa {

	private final Map<String, Deque<Long>> golpes = new ConcurrentHashMap<>();
	private final int porMinuto;

	public LimitadorTasa(@Value("${vera.rag.preguntas-por-minuto:20}") int porMinuto) {
		this.porMinuto = porMinuto;
	}

	public void exigirCupo(String clave) {
		long ahora = System.currentTimeMillis();
		long desde = ahora - 60_000;

		Deque<Long> ventana = golpes.computeIfAbsent(clave, k -> new ArrayDeque<>());
		synchronized (ventana) {
			while (!ventana.isEmpty() && ventana.peekFirst() < desde) {
				ventana.pollFirst();
			}
			if (ventana.size() >= porMinuto) {
				throw new ExcepcionNegocio(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
						"demasiadas preguntas seguidas; espera un momento");
			}
			ventana.addLast(ahora);
		}
	}

}
