package com.vera.evaluacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import com.vera.dominio.Tenant;
import com.vera.dominio.TenantRepository;
import com.vera.fuentes.FuenteService;
import com.vera.tenancy.TenantContext;

/**
 * Siembra un negocio desde cero con el corpus y los casos versionados en {@code docs/evaluacion/}.
 * Que el examen se arme desde archivos del repo y no desde la base de un laptop es lo que permite
 * que la misma prueba corra en CI y que el conjunto sea revisable en un diff.
 */
@SpringBootTest
abstract class ExamenBase {

	protected static final String SLUG = "ci-examen-test";
	protected static final int CASOS_ESENCIALES = 6;

	private static final Path CORPUS = Path.of("..", "docs", "evaluacion", "corpus-patitas.txt");
	private static final Path CASOS = Path.of("..", "docs", "evaluacion", "casos-patitas-felices.csv");

	@Autowired
	private TenantRepository tenants;

	@Autowired
	private FuenteService fuentes;

	@Autowired
	private EvaluacionService evaluacion;

	@Autowired
	protected EvaluadorService evaluador;

	@BeforeEach
	void sembrar() throws IOException {
		TenantContext.limpiar();
		UUID[] negocio = new UUID[1];
		TenantContext.ejecutarComoSistema(() -> {
			// Restos de una corrida que se cayó a la mitad: sin esto el slug único la bloquearía.
			tenants.findBySlug(SLUG).ifPresent(tenants::delete);
			Tenant t = new Tenant();
			t.setNombre("Negocio de CI");
			t.setSlug(SLUG);
			t.setClavePublica("pk_test_ci_examen");
			negocio[0] = tenants.save(t).getId();
		});
		TenantContext.establecer(negocio[0]);
		sembrarCorpus();
		evaluacion.importarCsv(new MockMultipartFile("archivo", "casos.csv", "text/csv",
				Files.readAllBytes(CASOS)));
	}

	@AfterEach
	void limpiar() {
		TenantContext.ejecutarComoSistema(() -> tenants.findBySlug(SLUG).ifPresent(tenants::delete));
		TenantContext.limpiar();
	}

	// El corpus es un solo archivo con bloques "== titulo ==". El título importa: es contra el
	// que el examen compara la fuente que se recuperó.
	private void sembrarCorpus() throws IOException {
		String titulo = null;
		StringBuilder cuerpo = new StringBuilder();
		for (String linea : Files.readString(CORPUS, StandardCharsets.UTF_8).split("\\R")) {
			if (linea.startsWith("== ") && linea.endsWith(" ==")) {
				guardar(titulo, cuerpo);
				titulo = linea.substring(3, linea.length() - 3).strip();
				cuerpo.setLength(0);
			} else {
				cuerpo.append(linea).append("\n");
			}
		}
		guardar(titulo, cuerpo);
	}

	private void guardar(String titulo, StringBuilder cuerpo) {
		if (titulo != null && !cuerpo.toString().isBlank()) {
			fuentes.crearDesdeTexto(titulo, cuerpo.toString().strip());
		}
	}

}
