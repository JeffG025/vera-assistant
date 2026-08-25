package com.vera.rag;

import org.springframework.http.HttpStatus;

import com.vera.error.ExcepcionNegocio;

/** Tope de cuota del proveedor. La diaria no se arregla esperando: quien la reciba debe parar. */
public class ExcepcionCuotaLlm extends ExcepcionNegocio {

	private final boolean diaria;

	public ExcepcionCuotaLlm(boolean diaria, String mensaje) {
		super(HttpStatus.SERVICE_UNAVAILABLE, mensaje);
		this.diaria = diaria;
	}

	public boolean esDiaria() {
		return diaria;
	}

}
