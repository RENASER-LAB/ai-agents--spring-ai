package com.renaser.ai.ai_engine.consentimiento.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// El texto que se acepta, versionado y con su huella SHA-256. Una versión publicada
// nunca se modifica: editar crea otra.
@Entity
@Table(name = "texto_consentimiento")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TextoConsentimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    // PROCESO o FUTUROS_CONTACTOS: son dos permisos distintos y se retiran por separado
    private String tipo;
    private String version;
    private String texto;
    private String hash;
    private Instant publicadoEn;
    private Instant creadoEn;
}
