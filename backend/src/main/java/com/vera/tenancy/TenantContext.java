package com.vera.tenancy;

import java.util.UUID;
import java.util.function.Supplier;

// Guarda el inquilino de la peticion en curso; el DataSource lo aplica a cada conexion.
public final class TenantContext {

	private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> SISTEMA = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void establecer(UUID tenantId) {
		TENANT.set(tenantId);
	}

	public static UUID actual() {
		return TENANT.get();
	}

	public static boolean esModoSistema() {
		return Boolean.TRUE.equals(SISTEMA.get());
	}

	// Modo sistema: unicamente para login y alta de negocios, que por definicion cruzan inquilinos.
	public static <T> T comoSistema(Supplier<T> accion) {
		boolean previo = esModoSistema();
		SISTEMA.set(true);
		try {
			return accion.get();
		} finally {
			SISTEMA.set(previo);
		}
	}

	public static void ejecutarComoSistema(Runnable accion) {
		comoSistema(() -> {
			accion.run();
			return null;
		});
	}

	public static void limpiar() {
		TENANT.remove();
		SISTEMA.remove();
	}

}
