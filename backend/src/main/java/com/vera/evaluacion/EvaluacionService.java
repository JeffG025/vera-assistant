package com.vera.evaluacion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vera.dominio.CasoEval;
import com.vera.dominio.CasoEvalRepository;
import com.vera.error.ExcepcionNegocio;
import com.vera.tenancy.TenantContext;

@Service
public class EvaluacionService {

	private final CasoEvalRepository casos;

	public EvaluacionService(CasoEvalRepository casos) {
		this.casos = casos;
	}

	public List<CasoEval> listar() {
		return casos.findAllByOrderByCreadoEn();
	}

	public CasoEval crear(String pregunta, String respuestaEsperada, String fuenteEsperada, boolean debeSaber) {
		CasoEval caso = new CasoEval();
		caso.setTenantId(TenantContext.actual());
		caso.setPregunta(pregunta.strip());
		caso.setRespuestaEsperada(vacioANulo(respuestaEsperada));
		caso.setFuenteEsperada(vacioANulo(fuenteEsperada));
		caso.setDebeSaber(debeSaber);
		return casos.save(caso);
	}

	public void eliminar(UUID id) {
		CasoEval caso = casos.findById(id)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("caso no encontrado"));
		casos.delete(caso);
	}

	/**
	 * Importa un CSV con columnas: pregunta, respuesta_esperada, fuente_esperada, debe_saber.
	 * Formato deliberadamente simple para que el dueño del negocio lo arme en una hoja de cálculo.
	 */
	public int importarCsv(MultipartFile archivo) {
		int importados = 0;
		try (BufferedReader lector = new BufferedReader(
				new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
			String linea;
			boolean primera = true;
			while ((linea = lector.readLine()) != null) {
				if (linea.isBlank()) {
					continue;
				}
				List<String> campos = partir(linea);
				// Se salta el encabezado si viene, comparando la primera columna.
				if (primera) {
					primera = false;
					if (campos.get(0).equalsIgnoreCase("pregunta")) {
						continue;
					}
				}
				if (campos.get(0).isBlank()) {
					continue;
				}
				boolean debeSaber = campos.size() < 4 || !campos.get(3).strip().equalsIgnoreCase("no");
				crear(campos.get(0), campos.size() > 1 ? campos.get(1) : null,
						campos.size() > 2 ? campos.get(2) : null, debeSaber);
				importados++;
			}
		} catch (IOException e) {
			throw ExcepcionNegocio.conflicto("no se pudo leer el CSV: " + e.getMessage());
		}
		if (importados == 0) {
			throw ExcepcionNegocio.conflicto("el CSV no traia ningun caso valido");
		}
		return importados;
	}

	// Partidor mínimo que respeta comillas dobles: basta para un CSV escrito a mano.
	private List<String> partir(String linea) {
		List<String> campos = new ArrayList<>();
		StringBuilder actual = new StringBuilder();
		boolean entreComillas = false;
		for (char c : linea.toCharArray()) {
			if (c == '"') {
				entreComillas = !entreComillas;
			} else if (c == ',' && !entreComillas) {
				campos.add(actual.toString().strip());
				actual.setLength(0);
			} else {
				actual.append(c);
			}
		}
		campos.add(actual.toString().strip());
		return campos;
	}

	private String vacioANulo(String valor) {
		return valor == null || valor.isBlank() ? null : valor.strip();
	}

}
