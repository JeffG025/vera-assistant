package com.vera.error;

import org.springframework.http.HttpStatus;

public class ExcepcionNegocio extends RuntimeException {

	private final HttpStatus estado;

	public ExcepcionNegocio(HttpStatus estado, String mensaje) {
		super(mensaje);
		this.estado = estado;
	}

	public HttpStatus getEstado() {
		return estado;
	}

	public static ExcepcionNegocio noEncontrado(String mensaje) {
		return new ExcepcionNegocio(HttpStatus.NOT_FOUND, mensaje);
	}

	public static ExcepcionNegocio conflicto(String mensaje) {
		return new ExcepcionNegocio(HttpStatus.CONFLICT, mensaje);
	}

	public static ExcepcionNegocio prohibido(String mensaje) {
		return new ExcepcionNegocio(HttpStatus.FORBIDDEN, mensaje);
	}

	public static ExcepcionNegocio noAutorizado(String mensaje) {
		return new ExcepcionNegocio(HttpStatus.UNAUTHORIZED, mensaje);
	}

}
