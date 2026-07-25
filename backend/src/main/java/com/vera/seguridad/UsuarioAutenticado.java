package com.vera.seguridad;

import java.util.UUID;

// Lo que viaja en el token y basta para autorizar sin volver a la base en cada peticion.
public record UsuarioAutenticado(UUID id, UUID tenantId, String email, String rol) {
}
