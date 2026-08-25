package com.vera.dominio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversacionRepository extends JpaRepository<Conversacion, UUID> {

	List<Conversacion> findTop50ByOrderByActualizadoEnDesc();

	/**
	 * La bandeja. Los filtros no van como nulos sino con un valor que significa "todo": cadena
	 * vacía para el canal y 0 para la calificación, que solo vale -1 o 1. Postgres no sabe de
	 * qué tipo es un parámetro que solo aparece en un {@code ? is null}, y la consulta revienta
	 * en ejecución; así cada parámetro se compara siempre contra algo tipado.
	 *
	 * <p>RLS ya recorta al negocio, así que aquí no aparece el inquilino por ningún lado.
	 */
	@Query("""
			select c.id as id, c.canal as canal, c.creadoEn as creadoEn,
			       c.actualizadoEn as actualizadoEn,
			       (select count(p) from Mensaje p
			         where p.conversacionId = c.id and p.rol = 'USUARIO') as preguntas,
			       (select count(s) from Mensaje s
			         where s.conversacionId = c.id and s.sinInformacion = true) as sinRespuesta,
			       (select count(a) from Mensaje a
			         where a.conversacionId = c.id and a.calificacion = 1) as positivas,
			       (select count(n) from Mensaje n
			         where n.conversacionId = c.id and n.calificacion = -1) as negativas
			  from Conversacion c
			 where c.creadoEn >= :desde
			   and c.creadoEn < :hasta
			   and (:canal = '' or c.canal = :canal)
			   and (:soloSinRespuesta = false or exists (
			         select 1 from Mensaje m
			          where m.conversacionId = c.id and m.sinInformacion = true))
			   and (:calificacion = 0 or exists (
			         select 1 from Mensaje m
			          where m.conversacionId = c.id and m.calificacion = :calificacion))
			 order by c.actualizadoEn desc
			""")
	Page<ResumenConversacion> bandeja(@Param("desde") OffsetDateTime desde,
			@Param("hasta") OffsetDateTime hasta, @Param("canal") String canal,
			@Param("soloSinRespuesta") boolean soloSinRespuesta,
			@Param("calificacion") short calificacion, Pageable pagina);

}
