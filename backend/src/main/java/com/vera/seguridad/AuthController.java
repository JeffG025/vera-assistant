package com.vera.seguridad;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService auth;

	public AuthController(AuthService auth) {
		this.auth = auth;
	}

	@PostMapping("/login")
	public AuthDTO.RespuestaAuth login(@Valid @RequestBody AuthDTO.PeticionLogin peticion) {
		return auth.login(peticion);
	}

	@PostMapping("/registro")
	public ResponseEntity<AuthDTO.RespuestaAuth> registro(@Valid @RequestBody AuthDTO.PeticionRegistro peticion) {
		return ResponseEntity.status(HttpStatus.CREATED).body(auth.registrar(peticion));
	}

	@PostMapping("/refresco")
	public AuthDTO.RespuestaAuth refresco(@Valid @RequestBody AuthDTO.PeticionRefresco peticion) {
		return auth.refrescar(peticion.refresco());
	}

	@GetMapping("/me")
	public UsuarioAutenticado me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return usuario;
	}

}
