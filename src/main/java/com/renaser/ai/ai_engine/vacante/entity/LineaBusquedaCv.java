package com.renaser.ai.ai_engine.vacante.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Una línea de «Qué buscar en el CV» (V80): algo concreto que la empresa busca en cada CV de la
// vacante. Privada: no sale ni en el portal ni a los agentes que escriben lo que ve el candidato.
// Editar las líneas archiva todas las vivas y crea las nuevas con la versión siguiente; un
// resultado de una versión vieja se reconoce por ese número y se descarta.
@Entity
@Table(name = "linea_busqueda_cv")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LineaBusquedaCv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vacanteId;
    private Integer versionLineas;
    private Integer orden;
    private String texto;
    private Instant creadoEn;
    private Instant archivadaEn;
}
