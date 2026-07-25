package com.vera.seguridad;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.vera.tenancy.TenantContext;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtService jwt;

	public JwtAuthFilter(JwtService jwt) {
		this.jwt = jwt;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		try {
			String cabecera = peticion.getHeader("Authorization");
			if (cabecera != null && cabecera.startsWith("Bearer ")) {
				autenticar(cabecera.substring(7));
			}
			cadena.doFilter(peticion, respuesta);
		} finally {
			// Imprescindible: el hilo vuelve al pool de Tomcat y no debe llevarse el inquilino puesto.
			TenantContext.limpiar();
			SecurityContextHolder.clearContext();
		}
	}

	private void autenticar(String token) {
		try {
			Claims datos = jwt.leer(token);
			// Un token de refresco no sirve para entrar a los endpoints normales.
			if (!JwtService.TIPO_ACCESO.equals(datos.get("tipo", String.class))) {
				return;
			}
			String tid = datos.get("tid", String.class);
			UsuarioAutenticado usuario = new UsuarioAutenticado(
					UUID.fromString(datos.getSubject()),
					tid != null ? UUID.fromString(tid) : null,
					datos.get("email", String.class),
					datos.get("rol", String.class));

			var autenticacion = new UsernamePasswordAuthenticationToken(
					usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol())));
			SecurityContextHolder.getContext().setAuthentication(autenticacion);

			if (usuario.tenantId() != null) {
				TenantContext.establecer(usuario.tenantId());
			}
		} catch (JwtException | IllegalArgumentException e) {
			// Token invalido o vencido: se sigue sin autenticar y Security responde 401.
			SecurityContextHolder.clearContext();
		}
	}

}
