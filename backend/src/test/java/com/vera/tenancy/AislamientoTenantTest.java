package com.vera.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.vera.dominio.Rol;
import com.vera.dominio.RolRepository;
import com.vera.dominio.Tenant;
import com.vera.dominio.TenantRepository;
import com.vera.dominio.Usuario;
import com.vera.dominio.UsuarioRepository;

/**
 * Prueba la promesa central del producto: un negocio no puede ver ni tocar los datos de otro,
 * incluso usando un repositorio que no filtra por inquilino.
 */
@SpringBootTest
class AislamientoTenantTest {

	@Autowired
	private TenantRepository tenants;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private RolRepository roles;

	private UUID idA;
	private UUID idB;

	@BeforeEach
	void preparar() {
		TenantContext.limpiar();
		TenantContext.ejecutarComoSistema(() -> {
			Rol owner = roles.findByCodigo("OWNER").orElseThrow();
			idA = crearNegocio("Negocio A", "negocio-a-test", "ana@a.test", owner);
			idB = crearNegocio("Negocio B", "negocio-b-test", "beto@b.test", owner);
		});
	}

	@AfterEach
	void limpiar() {
		TenantContext.ejecutarComoSistema(() -> {
			usuarios.deleteAll(usuarios.buscarPorEmail("ana@a.test"));
			usuarios.deleteAll(usuarios.buscarPorEmail("beto@b.test"));
			tenants.findBySlug("negocio-a-test").ifPresent(tenants::delete);
			tenants.findBySlug("negocio-b-test").ifPresent(tenants::delete);
		});
		TenantContext.limpiar();
	}

	@Test
	@DisplayName("cada negocio ve solo sus propios usuarios")
	void cadaNegocioVeLoSuyo() {
		TenantContext.establecer(idA);
		List<Usuario> deA = usuarios.findAll();
		assertThat(deA).extracting(Usuario::getEmail).containsExactly("ana@a.test");

		TenantContext.establecer(idB);
		List<Usuario> deB = usuarios.findAll();
		assertThat(deB).extracting(Usuario::getEmail).containsExactly("beto@b.test");
	}

	@Test
	@DisplayName("buscar por email no cruza la frontera entre negocios")
	void buscarPorEmailNoCruza() {
		TenantContext.establecer(idA);
		// El repositorio busca por email en toda la tabla; RLS es lo unico que lo detiene.
		assertThat(usuarios.buscarPorEmail("beto@b.test")).isEmpty();
		assertThat(usuarios.buscarPorEmail("ana@a.test")).hasSize(1);
	}

	@Test
	@DisplayName("sin inquilino en contexto no se ve ningun dato de negocio")
	void sinContextoNoSeVeNada() {
		TenantContext.limpiar();
		assertThat(usuarios.findAll()).isEmpty();
		assertThat(tenants.findAll()).isEmpty();
	}

	@Test
	@DisplayName("un negocio no puede escribir datos dentro de otro")
	void noSePuedeEscribirEnOtroNegocio() {
		Tenant b = TenantContext.comoSistema(() -> tenants.findById(idB).orElseThrow());
		Rol owner = TenantContext.comoSistema(() -> roles.findByCodigo("OWNER").orElseThrow());

		TenantContext.establecer(idA);
		Usuario intruso = new Usuario();
		intruso.setTenant(b);
		intruso.setEmail("intruso@a.test");
		intruso.setPasswordHash("x");
		intruso.setNombre("Intruso");
		intruso.setRol(owner);

		assertThatThrownBy(() -> usuarios.save(intruso)).isInstanceOf(RuntimeException.class);
	}

	private UUID crearNegocio(String nombre, String slug, String email, Rol rol) {
		Tenant t = new Tenant();
		t.setNombre(nombre);
		t.setSlug(slug);
		Tenant guardado = tenants.save(t);

		Usuario u = new Usuario();
		u.setTenant(guardado);
		u.setEmail(email);
		u.setPasswordHash("x");
		u.setNombre(nombre);
		u.setRol(rol);
		usuarios.save(u);

		return guardado.getId();
	}

}
