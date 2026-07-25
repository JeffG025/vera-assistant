package com.vera.tenancy;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ArranqueTenant {

	// Ya corrieron las migraciones: de aqui en adelante ninguna conexion trabaja con privilegios de dueno.
	@EventListener(ApplicationReadyEvent.class)
	public void alEstarLista() {
		DataSourceConTenant.activarBajaDePrivilegios();
	}

}
