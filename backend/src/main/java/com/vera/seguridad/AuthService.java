package com.vera.seguridad;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.vera.dominio.Rol;
import com.vera.dominio.RolRepository;
import com.vera.dominio.Tenant;
import com.vera.dominio.TenantRepository;
import com.vera.dominio.Usuario;
import com.vera.dominio.UsuarioRepository;
import com.vera.error.ExcepcionNegocio;
import com.vera.tenancy.TenantContext;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@Service
public class AuthService {

	private final UsuarioRepository usuarios;
	private final TenantRepository tenants;
	private final RolRepository roles;
	private final PasswordEncoder codificador;
	private final JwtService jwt;
	private final com.vera.negocio.AuditoriaService auditoria;

	public AuthService(UsuarioRepository usuarios, TenantRepository tenants, RolRepository roles,
			PasswordEncoder codificador, JwtService jwt, com.vera.negocio.AuditoriaService auditoria) {
		this.usuarios = usuarios;
		this.tenants = tenants;
		this.roles = roles;
		this.codificador = codificador;
		this.jwt = jwt;
		this.auditoria = auditoria;
	}

	public AuthDTO.RespuestaAuth login(AuthDTO.PeticionLogin peticion) {
		// Buscar por correo cruza negocios por definicion: va con bypass explicito y nada mas.
		List<Usuario> candidatos = TenantContext.comoSistema(() -> usuarios.buscarPorEmail(peticion.email()))
				.stream().filter(Usuario::isActivo).toList();

		Usuario usuario = elegir(candidatos, peticion.negocio());
		if (!codificador.matches(peticion.password(), usuario.getPasswordHash())) {
			throw ExcepcionNegocio.noAutorizado("correo o contrasena incorrectos");
		}

		usuario.setUltimoAcceso(OffsetDateTime.now());
		Usuario guardado = TenantContext.comoSistema(() -> usuarios.save(usuario));
		auditoria.registrar(guardado.getTenant() != null ? guardado.getTenant().getId() : null,
				guardado.getId(), com.vera.negocio.AuditoriaService.LOGIN, guardado.getEmail());
		return responder(guardado);
	}

	public AuthDTO.RespuestaAuth refrescar(String tokenRefresco) {
		Claims datos;
		try {
			datos = jwt.leer(tokenRefresco);
		} catch (JwtException | IllegalArgumentException e) {
			throw ExcepcionNegocio.noAutorizado("token de refresco invalido o vencido");
		}
		if (!JwtService.TIPO_REFRESCO.equals(datos.get("tipo", String.class))) {
			throw ExcepcionNegocio.noAutorizado("ese token no es de refresco");
		}
		UUID id = UUID.fromString(datos.getSubject());
		Usuario usuario = TenantContext.comoSistema(() -> usuarios.findByIdAndActivoTrue(id))
				.orElseThrow(() -> ExcepcionNegocio.noAutorizado("la cuenta ya no esta activa"));
		return responder(usuario);
	}

	public AuthDTO.RespuestaAuth registrar(AuthDTO.PeticionRegistro peticion) {
		return TenantContext.comoSistema(() -> {
			if (!usuarios.buscarPorEmail(peticion.email()).isEmpty()) {
				throw ExcepcionNegocio.conflicto("ese correo ya tiene una cuenta");
			}
			Rol owner = roles.findByCodigo("OWNER")
					.orElseThrow(() -> ExcepcionNegocio.noEncontrado("falta el rol OWNER"));

			Tenant negocio = new Tenant();
			negocio.setNombre(peticion.negocio());
			negocio.setSlug(slugUnico(peticion.negocio()));
			negocio.setClavePublica(nuevaClavePublica());
			Tenant creado = tenants.save(negocio);

			Usuario duenio = new Usuario();
			duenio.setTenant(creado);
			duenio.setEmail(peticion.email());
			duenio.setNombre(peticion.nombre());
			duenio.setPasswordHash(codificador.encode(peticion.password()));
			duenio.setRol(owner);

			Usuario guardado = usuarios.save(duenio);
			auditoria.registrar(creado.getId(), guardado.getId(),
					com.vera.negocio.AuditoriaService.REGISTRO, creado.getNombre());
			return responder(guardado);
		});
	}

	public String regenerarClavePublica(UUID tenantId) {
		Tenant negocio = tenants.findById(tenantId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("negocio no encontrado"));
		negocio.setClavePublica(nuevaClavePublica());
		return tenants.save(negocio).getClavePublica();
	}

	private Usuario elegir(List<Usuario> candidatos, String negocio) {
		if (candidatos.isEmpty()) {
			throw ExcepcionNegocio.noAutorizado("correo o contrasena incorrectos");
		}
		if (candidatos.size() == 1) {
			return candidatos.get(0);
		}
		if (negocio == null || negocio.isBlank()) {
			throw ExcepcionNegocio.conflicto("ese correo existe en varios negocios: indica cual");
		}
		return candidatos.stream()
				.filter(u -> u.getTenant() != null && negocio.equalsIgnoreCase(u.getTenant().getSlug()))
				.findFirst()
				.orElseThrow(() -> ExcepcionNegocio.noAutorizado("correo o contrasena incorrectos"));
	}

	private AuthDTO.RespuestaAuth responder(Usuario usuario) {
		Tenant t = usuario.getTenant();
		AuthDTO.DatosNegocio negocio = t == null ? null
				: new AuthDTO.DatosNegocio(t.getId().toString(), t.getNombre(), t.getSlug(),
						t.getPlan().name(), t.getClavePublica());
		AuthDTO.DatosUsuario datos = new AuthDTO.DatosUsuario(usuario.getId().toString(), usuario.getEmail(),
				usuario.getNombre(), usuario.getRol().getCodigo(), negocio);
		return new AuthDTO.RespuestaAuth(jwt.generarAcceso(usuario), jwt.generarRefresco(usuario),
				jwt.segundosDeAcceso(), datos);
	}

	private String nuevaClavePublica() {
		return "pk_" + UUID.randomUUID().toString().replace("-", "");
	}

	private String slugUnico(String nombre) {
		String base = Normalizer.normalize(nombre, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-+)|(-+$)", "");
		if (base.isBlank()) {
			base = "negocio";
		}
		if (base.length() > 50) {
			base = base.substring(0, 50).replaceAll("-+$", "");
		}
		String candidato = base;
		int intento = 2;
		while (tenants.existsBySlug(candidato)) {
			candidato = base + "-" + intento++;
		}
		return candidato;
	}

}
