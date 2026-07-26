package com.vera.negocio;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vera.dominio.Tenant;
import com.vera.dominio.TenantRepository;
import com.vera.error.ExcepcionNegocio;
import com.vera.seguridad.AuthService;
import com.vera.seguridad.UsuarioAutenticado;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/negocio")
public class NegocioController {

	private final TenantRepository tenants;
	private final AuthService auth;
	private final AuditoriaService auditoria;
	private final com.vera.dominio.AuditoriaRepository bitacora;
	private final UsoService usoService;

	public NegocioController(TenantRepository tenants, AuthService auth, AuditoriaService auditoria,
			com.vera.dominio.AuditoriaRepository bitacora, UsoService usoService) {
		this.tenants = tenants;
		this.auth = auth;
		this.auditoria = auditoria;
		this.bitacora = bitacora;
		this.usoService = usoService;
	}

	public record Datos(String id, String nombre, String slug, String plan, String estado, String clavePublica,
			String dominiosPermitidos, LimitesPlan limites) {
	}

	public record Movimiento(String accion, String detalle, String fecha) {
	}

	public record Cambios(@Size(max = 120) String nombre, String dominiosPermitidos) {
	}

	@GetMapping
	public Datos actual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return mapear(cargar(usuario));
	}

	@PatchMapping
	@PreAuthorize("hasRole('OWNER')")
	public Datos actualizar(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@Valid @RequestBody Cambios cambios) {
		Tenant negocio = cargar(usuario);
		if (cambios.nombre() != null && !cambios.nombre().isBlank()) {
			negocio.setNombre(cambios.nombre());
		}
		if (cambios.dominiosPermitidos() != null) {
			negocio.setDominiosPermitidos(cambios.dominiosPermitidos().isBlank() ? null
					: cambios.dominiosPermitidos().trim());
		}
		Datos guardado = mapear(tenants.save(negocio));
		auditoria.registrar(usuario.tenantId(), usuario.id(), AuditoriaService.NEGOCIO_ACTUALIZADO,
				guardado.nombre());
		return guardado;
	}

	// La bitacora ya viene filtrada por RLS: solo se ve lo del propio negocio.
	@GetMapping("/uso")
	public UsoService.Uso uso(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return usoService.calcular(cargar(usuario).getPlan());
	}

	@GetMapping("/auditoria")
	@PreAuthorize("hasRole('OWNER')")
	public java.util.List<Movimiento> auditoria() {
		return bitacora.findTop50ByOrderByCreadoEnDesc().stream()
				.map(a -> new Movimiento(a.getAccion(), a.getDetalle(), a.getCreadoEn().toString()))
				.toList();
	}

	// Rota la clave del widget: el script viejo deja de funcionar de inmediato.
	@PostMapping("/clave-publica")
	@PreAuthorize("hasRole('OWNER')")
	public Map<String, String> regenerarClave(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		String clave = auth.regenerarClavePublica(usuario.tenantId());
		auditoria.registrar(usuario.tenantId(), usuario.id(), AuditoriaService.CLAVE_REGENERADA, null);
		return Map.of("clavePublica", clave);
	}

	private Tenant cargar(UsuarioAutenticado usuario) {
		if (usuario.tenantId() == null) {
			throw ExcepcionNegocio.noEncontrado("tu cuenta no pertenece a ningun negocio");
		}
		return tenants.findById(usuario.tenantId())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("negocio no encontrado"));
	}

	private Datos mapear(Tenant t) {
		return new Datos(t.getId().toString(), t.getNombre(), t.getSlug(), t.getPlan().name(),
				t.getEstado().name(), t.getClavePublica(), t.getDominiosPermitidos(),
				LimitesPlan.de(t.getPlan()));
	}

}
