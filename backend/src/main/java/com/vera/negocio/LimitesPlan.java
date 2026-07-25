package com.vera.negocio;

import com.vera.dominio.PlanTenant;

/**
 * Techos por plan. Viven en codigo y no en la base porque cambian con la oferta comercial,
 * no con los datos del cliente.
 */
public record LimitesPlan(int fuentes, int mensajesMes, long tokensMes) {

	public static LimitesPlan de(PlanTenant plan) {
		return switch (plan) {
			case TRIAL -> new LimitesPlan(3, 100, 200_000L);
			case BASICO -> new LimitesPlan(25, 2_000, 3_000_000L);
			case PRO -> new LimitesPlan(150, 10_000, 20_000_000L);
			case ESCALA -> new LimitesPlan(1_000, 100_000, 200_000_000L);
		};
	}

}
