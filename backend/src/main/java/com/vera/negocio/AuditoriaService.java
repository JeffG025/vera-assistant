package com.vera.negocio;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.vera.dominio.Auditoria;
import com.vera.dominio.AuditoriaRepository;
import com.vera.tenancy.TenantContext;

@Service
public class AuditoriaService {

	public static final String REGISTRO = "registro";
	public static final String LOGIN = "login";
	public static final String NEGOCIO_ACTUALIZADO = "negocio.actualizado";
	public static final String CLAVE_REGENERADA = "clave.regenerada";

	private final AuditoriaRepository auditorias;

	public AuditoriaService(AuditoriaRepository auditorias) {
		this.auditorias = auditorias;
	}

	// Se escribe en modo sistema: la bitacora no debe fallar por el contexto de quien la dispara.
	public void registrar(UUID tenantId, UUID usuarioId, String accion, String detalle) {
		Auditoria entrada = new Auditoria();
		entrada.setTenantId(tenantId);
		entrada.setUsuarioId(usuarioId);
		entrada.setAccion(accion);
		entrada.setDetalle(detalle);
		TenantContext.ejecutarComoSistema(() -> auditorias.save(entrada));
	}

}
