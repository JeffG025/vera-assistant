package com.vera.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vera.dominio.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	public static final String TIPO_ACCESO = "acceso";
	public static final String TIPO_REFRESCO = "refresco";

	private final SecretKey clave;
	private final Duration duracionAcceso;
	private final Duration duracionRefresco;

	public JwtService(
			@Value("${vera.jwt.secreto}") String secreto,
			@Value("${vera.jwt.minutos-acceso:30}") long minutosAcceso,
			@Value("${vera.jwt.dias-refresco:7}") long diasRefresco) {
		this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
		this.duracionAcceso = Duration.ofMinutes(minutosAcceso);
		this.duracionRefresco = Duration.ofDays(diasRefresco);
	}

	public String generarAcceso(Usuario usuario) {
		return generar(usuario, TIPO_ACCESO, duracionAcceso);
	}

	public String generarRefresco(Usuario usuario) {
		return generar(usuario, TIPO_REFRESCO, duracionRefresco);
	}

	private String generar(Usuario usuario, String tipo, Duration duracion) {
		Instant ahora = Instant.now();
		UUID tenantId = usuario.getTenant() != null ? usuario.getTenant().getId() : null;
		return Jwts.builder()
				.subject(usuario.getId().toString())
				.claim("tid", tenantId != null ? tenantId.toString() : null)
				.claim("rol", usuario.getRol().getCodigo())
				.claim("email", usuario.getEmail())
				.claim("tipo", tipo)
				.issuedAt(Date.from(ahora))
				.expiration(Date.from(ahora.plus(duracion)))
				.signWith(clave)
				.compact();
	}

	// Lanza si la firma no cuadra o el token vencio: el filtro lo traduce a 401.
	public Claims leer(String token) {
		return Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
	}

	public long segundosDeAcceso() {
		return duracionAcceso.toSeconds();
	}

}
