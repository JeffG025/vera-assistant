package com.vera.seguridad;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain cadena(HttpSecurity http, JwtAuthFilter jwtFiltro,
			@Qualifier("corsConfigurationSource") CorsConfigurationSource origenesPermitidos)
			throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				// Se pasa la fuente a mano: con withDefaults(), Spring Security solo encuentra el
				// bean si se llama exactamente "corsConfigurationSource" y, si no, no manda ni una
				// cabecera CORS. En desarrollo no se nota porque Vite hace de proxy.
				.cors(c -> c.configurationSource(origenesPermitidos))
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(reglas -> reglas
						// /error va abierto: un JSON mal formado reenvía ahí sin filtro JWT y el
						// error real se disfrazaría de 401 "se requiere iniciar sesion".
						// El widget vive en la web de un cliente y su visitante no tiene cuenta:
						// se identifica con la clave publica, no con sesion.
						.requestMatchers("/error", "/api/health", "/api/auth/login", "/api/auth/refresco",
								"/api/auth/registro", "/api/widget/**")
						.permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(e -> e
						.authenticationEntryPoint((req, res, ex) -> responder(res, HttpStatus.UNAUTHORIZED,
								"se requiere iniciar sesion"))
						.accessDeniedHandler((req, res, ex) -> responder(res, HttpStatus.FORBIDDEN,
								"tu rol no permite esta accion")))
				.addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	// 401 y 403 diferenciados y en JSON: el panel necesita distinguir "entra de nuevo" de "no puedes".
	private void responder(jakarta.servlet.http.HttpServletResponse res, HttpStatus estado, String mensaje)
			throws java.io.IOException {
		res.setStatus(estado.value());
		res.setContentType("application/json;charset=UTF-8");
		res.getWriter().write("{\"error\":\"" + mensaje + "\"}");
	}

	@Bean
	public PasswordEncoder codificador() {
		return new BCryptPasswordEncoder();
	}

	// El nombre importa: Spring Security busca este bean por el nombre exacto, y ademas hay otro
	// CorsConfigurationSource en el contexto (el de Spring MVC) que haria ambigua la inyeccion.
	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${vera.cors.origenes}") List<String> origenes) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(origenes);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));

		// El widget se instala en el sitio de cada cliente, asi que su origen no se puede
		// conocer de antemano. Sin cookies ni sesion, abrirlo aqui no expone nada del panel.
		// ⚠️ El filtro por dominio dado de alta llega en el 5.3.
		CorsConfiguration deWidget = new CorsConfiguration();
		deWidget.setAllowedOriginPatterns(List.of("*"));
		deWidget.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
		deWidget.setAllowedHeaders(List.of("*"));

		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/api/widget/**", deWidget);
		fuente.registerCorsConfiguration("/api/**", config);
		return fuente;
	}

}
