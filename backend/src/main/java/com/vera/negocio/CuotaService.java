package com.vera.negocio;

import org.springframework.stereotype.Service;

import com.vera.dominio.PlanTenant;
import com.vera.error.ExcepcionNegocio;

/**
 * Punto unico donde se corta por cuota. Los contadores reales llegan con las fuentes (bloque 2)
 * y los mensajes (bloque 3); aqui vive la regla para que ningun modulo la reinvente.
 */
@Service
public class CuotaService {

	public void exigirEspacioParaFuente(PlanTenant plan, int fuentesActuales) {
		int techo = LimitesPlan.de(plan).fuentes();
		if (fuentesActuales >= techo) {
			throw ExcepcionNegocio.conflicto(
					"tu plan permite " + techo + " fuentes; libera una o sube de plan");
		}
	}

	public void exigirMensajesDisponibles(PlanTenant plan, int mensajesDelMes) {
		int techo = LimitesPlan.de(plan).mensajesMes();
		if (mensajesDelMes >= techo) {
			throw ExcepcionNegocio.conflicto(
					"tu plan permite " + techo + " mensajes al mes; el asistente queda en pausa hasta el proximo ciclo");
		}
	}

}
