package com.vera.fuentes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vera.dominio.Documento;
import com.vera.dominio.DocumentoRepository;
import com.vera.dominio.EstadoFuente;
import com.vera.dominio.Fuente;
import com.vera.dominio.FuenteRepository;
import com.vera.dominio.Tenant;
import com.vera.dominio.TenantRepository;
import com.vera.dominio.TipoFuente;
import com.vera.error.ExcepcionNegocio;
import com.vera.negocio.CuotaService;
import com.vera.tenancy.TenantContext;

@Service
public class FuenteService {

	private final FuenteRepository fuentes;
	private final DocumentoRepository documentos;
	private final TenantRepository tenants;
	private final CuotaService cuotas;
	private final RastreadorService rastreador;

	public FuenteService(FuenteRepository fuentes, DocumentoRepository documentos, TenantRepository tenants,
			CuotaService cuotas, RastreadorService rastreador) {
		this.fuentes = fuentes;
		this.documentos = documentos;
		this.tenants = tenants;
		this.cuotas = cuotas;
		this.rastreador = rastreador;
	}

	public record ResumenRastreo(int nuevas, int actualizadas, int sinCambio, int eliminadas) {
	}

	public List<Fuente> listar() {
		return fuentes.findAllByOrderByCreadoEnDesc();
	}

	public Fuente crearDesdeTexto(String nombre, String texto) {
		exigirCupo();
		Fuente fuente = fuentes.save(nueva(TipoFuente.TEXTO, nombre, "pegado en el panel"));
		return guardarUnico(fuente, texto, null, nombre);
	}

	public Fuente crearDesdeArchivo(MultipartFile archivo) {
		exigirCupo();
		String nombreArchivo = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "archivo";
		TipoFuente tipo = detectar(nombreArchivo);
		Fuente fuente = fuentes.save(nueva(tipo, nombreArchivo, nombreArchivo));

		try {
			String texto = tipo == TipoFuente.PDF ? extraerPdf(archivo.getBytes())
					: new String(archivo.getBytes(), StandardCharsets.UTF_8);
			return guardarUnico(fuente, texto, null, nombreArchivo);
		} catch (IOException e) {
			return marcarError(fuente, "no se pudo leer el archivo: " + e.getMessage());
		}
	}

	/** El rastreo va fuera de transacción a propósito: son segundos de red que no deben bloquear la base. */
	public Fuente crearDesdeSitio(String url, int maxPaginas, int maxProfundidad) {
		exigirCupo();
		List<RastreadorService.Pagina> paginas = rastreador.rastrear(url, maxPaginas, maxProfundidad);

		Fuente fuente = fuentes.save(nueva(TipoFuente.URL, nombreDeSitio(url), url));
		UUID tenantId = TenantContext.actual();
		int caracteres = 0;
		for (RastreadorService.Pagina pagina : paginas) {
			if (pagina.texto().isBlank()) {
				continue;
			}
			documentos.save(construir(tenantId, fuente.getId(), pagina.url(), pagina.titulo(), pagina.texto()));
			caracteres += pagina.texto().length();
		}
		return cerrar(fuente, (int) documentos.countByFuenteId(fuente.getId()), caracteres);
	}

	public ResumenRastreo rastrearDeNuevo(UUID fuenteId) {
		Fuente fuente = fuentes.findById(fuenteId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("fuente no encontrada"));
		if (fuente.getTipo() != TipoFuente.URL) {
			throw ExcepcionNegocio.conflicto("solo se puede volver a rastrear una fuente de tipo sitio");
		}

		List<RastreadorService.Pagina> paginas = rastreador.rastrear(fuente.getOrigen(), 0, 0);

		Map<String, Documento> existentes = new HashMap<>();
		for (Documento d : documentos.findByFuenteId(fuenteId)) {
			existentes.put(d.getUrl(), d);
		}

		int nuevas = 0, actualizadas = 0, sinCambio = 0, caracteres = 0;
		List<String> vistas = new ArrayList<>();

		for (RastreadorService.Pagina pagina : paginas) {
			if (pagina.texto().isBlank()) {
				continue;
			}
			vistas.add(pagina.url());
			caracteres += pagina.texto().length();
			Documento previo = existentes.get(pagina.url());
			String huella = huella(pagina.texto());

			if (previo == null) {
				documentos.save(construir(TenantContext.actual(), fuenteId, pagina.url(), pagina.titulo(),
						pagina.texto()));
				nuevas++;
			} else if (huella.equals(previo.getHash())) {
				// Contenido idéntico: no se toca nada y en el 2.3 tampoco se reindexará.
				sinCambio++;
			} else {
				previo.setTitulo(recortar(pagina.titulo(), 300));
				previo.setContenido(pagina.texto());
				previo.setHash(huella);
				previo.setCaracteres(pagina.texto().length());
				documentos.save(previo);
				actualizadas++;
			}
		}

		int eliminadas = 0;
		for (Map.Entry<String, Documento> entrada : existentes.entrySet()) {
			if (!vistas.contains(entrada.getKey())) {
				documentos.delete(entrada.getValue());
				eliminadas++;
			}
		}

		cerrar(fuente, (int) documentos.countByFuenteId(fuenteId), caracteres);
		return new ResumenRastreo(nuevas, actualizadas, sinCambio, eliminadas);
	}

	public void eliminar(UUID id) {
		Fuente fuente = fuentes.findById(id)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("fuente no encontrada"));
		// Los documentos se van solos: la llave foránea tiene ON DELETE CASCADE.
		fuentes.delete(fuente);
	}

	// El cupo se cuenta sobre lo que RLS deja ver, o sea las fuentes de este negocio.
	private void exigirCupo() {
		Tenant negocio = tenants.findById(TenantContext.actual())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("negocio no encontrado"));
		cuotas.exigirEspacioParaFuente(negocio.getPlan(), (int) fuentes.count());
	}

	private Fuente nueva(TipoFuente tipo, String nombre, String origen) {
		Fuente fuente = new Fuente();
		fuente.setTenantId(TenantContext.actual());
		fuente.setTipo(tipo);
		fuente.setNombre(recortar(nombre, 200));
		fuente.setOrigen(origen);
		return fuente;
	}

	private Fuente guardarUnico(Fuente fuente, String texto, String url, String titulo) {
		String limpio = texto == null ? "" : texto.strip();
		if (limpio.isEmpty()) {
			return marcarError(fuente, fuente.getTipo() == TipoFuente.PDF
					? "el PDF no trae capa de texto: parece un escaneo, hará falta OCR"
					: "el contenido llegó vacío");
		}
		documentos.save(construir(fuente.getTenantId(), fuente.getId(), url, titulo, limpio));
		return cerrar(fuente, 1, limpio.length());
	}

	private Documento construir(UUID tenantId, UUID fuenteId, String url, String titulo, String texto) {
		Documento documento = new Documento();
		documento.setTenantId(tenantId);
		documento.setFuenteId(fuenteId);
		documento.setUrl(url);
		documento.setTitulo(recortar(titulo, 300));
		documento.setContenido(texto);
		documento.setHash(huella(texto));
		documento.setCaracteres(texto.length());
		return documento;
	}

	private Fuente cerrar(Fuente fuente, int cuantosDocumentos, int caracteres) {
		fuente.setDocumentos(cuantosDocumentos);
		fuente.setCaracteres(caracteres);
		fuente.setEstado(cuantosDocumentos > 0 ? EstadoFuente.LISTA : EstadoFuente.ERROR);
		if (cuantosDocumentos == 0 && fuente.getError() == null) {
			fuente.setError("no se obtuvo contenido");
		}
		return fuentes.save(fuente);
	}

	private Fuente marcarError(Fuente fuente, String mensaje) {
		fuente.setEstado(EstadoFuente.ERROR);
		fuente.setError(mensaje);
		fuente.setCaracteres(0);
		fuente.setDocumentos(0);
		return fuentes.save(fuente);
	}

	private String huella(String texto) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha.digest(texto.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

	private String extraerPdf(byte[] datos) throws IOException {
		try (PDDocument documento = Loader.loadPDF(datos)) {
			return new PDFTextStripper().getText(documento);
		}
	}

	private String nombreDeSitio(String url) {
		try {
			String host = java.net.URI.create(url).getHost();
			return host != null ? host : url;
		} catch (IllegalArgumentException e) {
			return url;
		}
	}

	private TipoFuente detectar(String nombre) {
		String bajo = nombre.toLowerCase(Locale.ROOT);
		if (bajo.endsWith(".pdf")) {
			return TipoFuente.PDF;
		}
		if (bajo.endsWith(".md") || bajo.endsWith(".markdown")) {
			return TipoFuente.MARKDOWN;
		}
		if (bajo.endsWith(".txt")) {
			return TipoFuente.TEXTO;
		}
		throw ExcepcionNegocio.conflicto("formato no soportado: se aceptan PDF, TXT y MD");
	}

	private String recortar(String valor, int maximo) {
		if (valor == null) {
			return null;
		}
		return valor.length() <= maximo ? valor : valor.substring(0, maximo);
	}

}
