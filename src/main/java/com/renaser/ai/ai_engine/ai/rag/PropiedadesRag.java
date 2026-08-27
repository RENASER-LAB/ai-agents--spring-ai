package com.renaser.ai.ai_engine.ai.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * De dónde puede leer PDFs la ingesta del RAG.
 *
 * <p><b>Sin valor, la ingesta por ruta queda apagada</b>, y es lo correcto por defecto: hoy no
 * hay ningún directorio de PDFs en el servidor —el Dockerfile solo copia el jar— ni ningún
 * cliente que llame al endpoint. Un valor por defecto cómodo aquí es lo que convirtió
 * {@code POST /api/v1/rag/ingest} en una lectura de ficheros del servidor: la ruta llegaba sin
 * atar a ningún sitio.
 */
@Component
@ConfigurationProperties(prefix = "renaser.rag")
@Data
public class PropiedadesRag {

    /** Directorio dentro del cual tienen que estar los PDFs. Vacío = ingesta apagada. */
    private String directorioBase;
}
