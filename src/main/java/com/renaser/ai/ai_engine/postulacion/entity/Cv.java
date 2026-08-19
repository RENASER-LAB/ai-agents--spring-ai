package com.renaser.ai.ai_engine.postulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// El currículum de una postulación. archivo_original se borra al anonimizar;
// la versión anonimizada (hito 2) es lo único que verá la máquina.
@Entity
@Table(name = "cv")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Cv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private Long archivoOriginalId;
    private Long archivoAnonimizadoId;
    private String textoExtraido;
    // La única versión que ve la IA: sin foto, edad, sexo ni estado civil (RF-41)
    private String textoAnonimizado;
    private Instant anonimizadoEn;
    // El texto obligatorio del formulario: «cuéntanos qué cambió gracias a tu trabajo».
    // Vive aquí porque es evidencia 1:1 con la postulación y se vacía al anonimizar.
    private String resultadoOrgulloso;
    private Instant creadoEn;
}
