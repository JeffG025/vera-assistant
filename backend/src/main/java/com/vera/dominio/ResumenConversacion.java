package com.vera.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila de la bandeja. Se arma en la propia consulta y no cargando cada conversación con sus
 * mensajes: la lista solo necesita contar, y traer los hilos enteros para descartarlos costaría
 * una consulta por fila.
 */
public interface ResumenConversacion {

	UUID getId();

	String getCanal();

	OffsetDateTime getCreadoEn();

	OffsetDateTime getActualizadoEn();

	long getPreguntas();

	long getSinRespuesta();

	long getPositivas();

	long getNegativas();

}
