package com.renaser.ai.ai_engine.notificacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// A quién, cuándo y QUÉ decía: el cuerpo ya armado, no la plantilla. Guarda código y
// versión como valores, sin FK: el dato no debe cambiar bajo los pies.
@Entity
@Table(name = "correo_enviado")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CorreoEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;
    private String plantillaCorreoCodigo;
    private Integer versionPlantilla;
    private String asunto;
    private String cuerpo;
    private String canal;
    private String estadoEntrega;
    private Instant enviadoEn;
    private Instant creadoEn;
}
