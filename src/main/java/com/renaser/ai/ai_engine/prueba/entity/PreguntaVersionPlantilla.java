package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

// Cuáles preguntas eligió esta versión de plantilla. Clave compuesta, sin columna id,
// igual que orden_pregunta en el hito 2.
@Entity
@Table(name = "pregunta_version_plantilla")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@IdClass(PreguntaVersionPlantilla.Clave.class)
public class PreguntaVersionPlantilla {

    @Id
    private Long versionPlantillaPruebaId;
    @Id
    private Long preguntaPruebaId;

    private Integer orden;
    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long versionPlantillaPruebaId;
        private Long preguntaPruebaId;
    }
}
