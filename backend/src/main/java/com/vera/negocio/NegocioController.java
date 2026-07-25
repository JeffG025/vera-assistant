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

	public NegocioController(TenantRepository tenants, AuthService auth) {
		this.tenants = tenants;
		this.auth = auth;
	}

	public record Datos(String id, String nombre, String slug, String plan, String estado, String clavePublica,
			String dominiosPermitidos) {
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
		return mapear(tenants.save(negocio));
	}

	// Rota la clave del widget: el script viejo deja de funcionar de inmediato.
	@PostMapping("/clave-publica")
	@PreAuthorize("hasRole('OWNER')")
	public Map<String, String> regenerarClave(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return Map.of("clavePublica", auth.regenerarClavePublica(usuario.tenantId()));
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
				t.getEstado().name(), t.getClavePublica(), t.getDominiosPermitidos());
	}

}
