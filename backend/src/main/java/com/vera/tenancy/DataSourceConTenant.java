package com.vera.tenancy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Aplica el inquilino actual a cada conexion que se toma del pool y lo borra al devolverla.
 * Es lo que alimenta a las politicas RLS de Postgres.
 */
public class DataSourceConTenant extends DelegatingDataSource {

	private static final String SET_CONFIG = "SELECT set_config(?, ?, false)";

	/**
	 * Las migraciones necesitan al dueno de las tablas; el trafico normal no. Mientras la app
	 * arranca se conserva el usuario original y, en cuanto esta lista, toda conexion baja a vera_app,
	 * que es la unica forma de que Postgres aplique RLS (a un superusuario lo ignora).
	 */
	private static volatile boolean bajarPrivilegios = false;

	public DataSourceConTenant(DataSource objetivo) {
		super(objetivo);
	}

	public static void activarBajaDePrivilegios() {
		bajarPrivilegios = true;
	}

	@Override
	public Connection getConnection() throws SQLException {
		return envolver(obtenerDelegada().getConnection());
	}

	@Override
	public Connection getConnection(String usuario, String contrasena) throws SQLException {
		return envolver(obtenerDelegada().getConnection(usuario, contrasena));
	}

	private DataSource obtenerDelegada() {
		DataSource delegada = getTargetDataSource();
		if (delegada == null) {
			throw new IllegalStateException("DataSource delegada no configurada");
		}
		return delegada;
	}

	private Connection envolver(Connection real) throws SQLException {
		aplicar(real, TenantContext.actual(), TenantContext.esModoSistema());
		InvocationHandler manejador = (proxy, metodo, args) -> {
			// Al devolver la conexion al pool se limpia el contexto: sin esto el siguiente en tomarla
			// heredaria el inquilino del anterior.
			if ("close".equals(metodo.getName())) {
				limpiar(real);
				real.close();
				return null;
			}
			try {
				return metodo.invoke(real, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		};
		return (Connection) Proxy.newProxyInstance(
				Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, manejador);
	}

	private void aplicar(Connection conexion, UUID tenantId, boolean sistema) throws SQLException {
		ajustar(conexion, "vera.tenant_id", tenantId != null ? tenantId.toString() : "");
		ajustar(conexion, "vera.bypass", sistema ? "on" : "");
		if (bajarPrivilegios) {
			ejecutar(conexion, "SET ROLE vera_app");
		}
	}

	private void limpiar(Connection conexion) throws SQLException {
		if (conexion.isClosed()) {
			return;
		}
		// Primero recuperar el rol original: la conexion vuelve al pool tal como salio.
		if (bajarPrivilegios) {
			ejecutar(conexion, "RESET ROLE");
		}
		ajustar(conexion, "vera.tenant_id", "");
		ajustar(conexion, "vera.bypass", "");
	}

	private void ejecutar(Connection conexion, String sentencia) throws SQLException {
		try (java.sql.Statement st = conexion.createStatement()) {
			st.execute(sentencia);
		}
	}

	private void ajustar(Connection conexion, String clave, String valor) throws SQLException {
		try (PreparedStatement ps = conexion.prepareStatement(SET_CONFIG)) {
			ps.setString(1, clave);
			ps.setString(2, valor);
			ps.execute();
		}
	}

}
