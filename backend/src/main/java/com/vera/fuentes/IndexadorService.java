package com.vera.fuentes;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.vera.dominio.Documento;

@Service
public class IndexadorService {

	private static final Logger log = LoggerFactory.getLogger(IndexadorService.class);

	private final Troceador troceador;
	private final ProveedorEmbeddings embeddings;
	private final FragmentoDao fragmentos;

	public IndexadorService(Troceador troceador, ProveedorEmbeddings embeddings, FragmentoDao fragmentos) {
		this.troceador = troceador;
		this.embeddings = embeddings;
		this.fragmentos = fragmentos;
	}

	/** Reindexa un documento entero. Borra primero para que no queden fragmentos huérfanos del texto viejo. */
	public int indexar(Documento documento) {
		fragmentos.borrarPorDocumento(documento.getId());

		List<String> trozos = troceador.trocear(documento.getContenido());
		if (trozos.isEmpty()) {
			return 0;
		}

		List<float[]> vectores = embeddings.generar(trozos);
		for (int i = 0; i < trozos.size(); i++) {
			fragmentos.insertar(documento.getTenantId(), documento.getFuenteId(), documento.getId(), i,
					trozos.get(i), documento.getTitulo(), documento.getUrl(), vectores.get(i),
					embeddings.modelo());
		}
		log.debug("documento {} indexado en {} fragmentos", documento.getId(), trozos.size());
		return trozos.size();
	}

	public List<FragmentoDao.Resultado> buscar(String consulta, int cuantos) {
		return fragmentos.buscar(embeddings.generarUno(consulta), cuantos);
	}

	public List<FragmentoDao.Trozo> fragmentosDe(java.util.UUID fuenteId, int limite) {
		return fragmentos.porFuente(fuenteId, limite);
	}

	public String modelo() {
		return embeddings.modelo();
	}

}
