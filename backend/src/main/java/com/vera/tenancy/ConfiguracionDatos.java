package com.vera.tenancy;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * Envuelve el DataSource que crea Spring Boot, sin construirlo a mano: asi el aislamiento
 * se aplica a todo el mundo (JPA, Flyway, JdbcTemplate) sin depender de clases internas de Boot.
 */
@Configuration
public class ConfiguracionDatos implements BeanPostProcessor {

	@Override
	public Object postProcessAfterInitialization(Object bean, String nombre) {
		if (bean instanceof DataSource fuente && !(bean instanceof DataSourceConTenant)) {
			return new DataSourceConTenant(fuente);
		}
		return bean;
	}

}
