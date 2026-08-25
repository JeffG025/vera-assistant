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

import com.vera.contactos.DestinoWebhook;
import com.vera.dominio.Tenant;
import com.vera.dominio.TenantRepository;
import com.vera.error.ExcepcionNegocio;
import com.vera.seguridad.AuthService;
import com.vera.seguridad.UsuarioAutenticado;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/negocio")
public class NegocioController {

	private final TenantRepository tenants;
	private final AuthService auth;
	private final AuditoriaService auditoria;
	private final com.vera.dominio.AuditoriaRepository bitacora;
	private final UsoService usoService;
	private final com.vera.contactos.ContactoService contactos;

	public NegocioController(TenantRepository tenants, AuthService auth, AuditoriaService auditoria,
			com.vera.dominio.AuditoriaRepository bitacora, UsoService usoService,
			com.vera.contactos.ContactoService contactos) {
		this.tenants = tenants;
		this.auth = auth;
		this.auditoria = auditoria;
		this.bitacora = bitacora;
		this.usoService = usoService;
		this.contactos = contactos;
	}

	public record Datos(String id, String nombre, String slug, String plan, String estado, String clavePublica,
			String dominiosPermitidos, LimitesPlan limites, String asistenteNombre, String asistenteSaludo,
			String asistenteIdioma, String asistenteColor, String asistenteAvatar,
			String asistentePosicion, boolean capturarContactos, String webhookUrl) {
	}

	public record Movimiento(String accion, String detalle, String fecha) {
	}

	public record Cambios(@Size(max = 120) String nombre, String dominiosPermitidos,
			@Size(max = 60) String asistenteNombre, @Size(max = 500) String asistenteSaludo,
			@Size(max = 20) String asistenteIdioma,
			@Pattern(regexp = "^#[0-9a-fA-F]{6}$",
					message = "el color debe ser hexadecimal de 6 digitos, como #4f46e5") String asistenteColor,
			@Size(max = 8) String asistenteAvatar,
			@Pattern(regexp = "derecha|izquierda",
					message = "la posicion solo puede ser derecha o izquierda") String asistentePosicion,
			Boolean capturarContactos, @Size(max = 500) String webhookUrl) {
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
		// El saludo si se puede vaciar: sin el, el widget arma uno con el nombre del asistente.
		if (cambios.asistenteSaludo() != null) {
			negocio.setAsistenteSaludo(
					cambios.asistenteSaludo().isBlank() ? null : cambios.asistenteSaludo().trim());
		}
		aplicar(cambios.asistenteNombre(), negocio::setAsistenteNombre);
		aplicar(cambios.asistenteIdioma(), negocio::setAsistenteIdioma);
		aplicar(cambios.asistenteColor(), negocio::setAsistenteColor);
		aplicar(cambios.asistenteAvatar(), negocio::setAsistenteAvatar);
		aplicar(cambios.asistentePosicion(), negocio::setAsistentePosicion);
		if (cambios.capturarContactos() != null) {
			negocio.setCapturarContactos(cambios.capturarContactos());
		}
		if (cambios.webhookUrl() != null) {
			// Se valida antes de guardar para que el dueno vea el motivo, no un fallo silencioso
			// cada vez que entre un contacto.
			DestinoWebhook.exigirValido(cambios.webhookUrl(), contactos.permiteLocales());
			negocio.setWebhookUrl(cambios.webhookUrl().isBlank() ? null : cambios.webhookUrl().trim());
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

	// En un PATCH, null significa "esto no lo toques", no "borralo": el panel manda solo lo que
	// el formulario ensena, y estos campos no pueden quedarse vacios en la base.
	private static void aplicar(String valor, java.util.function.Consumer<String> destino) {
		if (valor != null && !valor.isBlank()) {
			destino.accept(valor.trim());
		}
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
				LimitesPlan.de(t.getPlan()), t.getAsistenteNombre(), t.getAsistenteSaludo(),
				t.getAsistenteIdioma(), t.getAsistenteColor(), t.getAsistenteAvatar(),
				t.getAsistentePosicion(), t.isCapturarContactos(), t.getWebhookUrl());
	}

}
