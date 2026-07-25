package com.vera.seguridad;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDTO {

	private AuthDTO() {
	}

	public record PeticionLogin(
			@NotBlank @Email String email,
			@NotBlank String password,
			// Solo hace falta si el mismo correo existe en varios negocios.
			String negocio) {
	}

	public record PeticionRegistro(
			@NotBlank @Size(max = 120) String negocio,
			@NotBlank @Size(max = 120) String nombre,
			@NotBlank @Email @Size(max = 160) String email,
			@NotBlank @Size(min = 8, max = 72) String password) {
	}

	public record PeticionRefresco(@NotBlank String refresco) {
	}

	public record RespuestaAuth(String acceso, String refresco, long expiraEn, DatosUsuario usuario) {
	}

	public record DatosUsuario(String id, String email, String nombre, String rol, DatosNegocio negocio) {
	}

	public record DatosNegocio(String id, String nombre, String slug, String plan, String clavePublica) {
	}

}
