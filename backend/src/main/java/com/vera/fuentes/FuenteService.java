package com.vera.fuentes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
	private final TenantRepository tenants;
	private final CuotaService cuotas;

	public FuenteService(FuenteRepository fuentes, TenantRepository tenants, CuotaService cuotas) {
		this.fuentes = fuentes;
		this.tenants = tenants;
		this.cuotas = cuotas;
	}

	public List<Fuente> listar() {
		return fuentes.findAllByOrderByCreadoEnDesc();
	}

	public Fuente crearDesdeTexto(String nombre, String texto) {
		exigirCupo();
		Fuente fuente = nueva(TipoFuente.TEXTO, nombre, "pegado en el panel");
		return guardarConContenido(fuente, texto);
	}

	public Fuente crearDesdeArchivo(MultipartFile archivo) {
		exigirCupo();
		String nombreArchivo = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "archivo";
		TipoFuente tipo = detectar(nombreArchivo);
		Fuente fuente = nueva(tipo, nombreArchivo, nombreArchivo);

		try {
			String texto = tipo == TipoFuente.PDF ? extraerPdf(archivo.getBytes())
					: new String(archivo.getBytes(), StandardCharsets.UTF_8);
			return guardarConContenido(fuente, texto);
		} catch (IOException e) {
			return guardarConError(fuente, "no se pudo leer el archivo: " + e.getMessage());
		}
	}

	public void eliminar(UUID id) {
		Fuente fuente = fuentes.findById(id)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("fuente no encontrada"));
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

	private Fuente guardarConContenido(Fuente fuente, String texto) {
		String limpio = texto == null ? "" : texto.strip();
		if (limpio.isEmpty()) {
			return guardarConError(fuente, fuente.getTipo() == TipoFuente.PDF
					? "el PDF no trae capa de texto: parece un escaneo, hara falta OCR"
					: "el contenido llego vacio");
		}
		fuente.setContenido(limpio);
		fuente.setCaracteres(limpio.length());
		fuente.setEstado(EstadoFuente.LISTA);
		fuente.setError(null);
		return fuentes.save(fuente);
	}

	private Fuente guardarConError(Fuente fuente, String mensaje) {
		fuente.setEstado(EstadoFuente.ERROR);
		fuente.setError(mensaje);
		fuente.setCaracteres(0);
		return fuentes.save(fuente);
	}

	private String extraerPdf(byte[] datos) throws IOException {
		try (PDDocument documento = Loader.loadPDF(datos)) {
			return new PDFTextStripper().getText(documento);
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
		return valor.length() <= maximo ? valor : valor.substring(0, maximo);
	}

}
