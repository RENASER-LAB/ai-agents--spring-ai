package com.renaser.ai.ai_engine.postulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Los datos del candidato sacados del currículum por el agente DATOS_CV. No puntúan nada:
// existen para poder mirar una tanda entera sin abrir diez PDF uno por uno.
//
// No hay edad, sexo ni estado civil, y no es un olvido: el agente lee la versión recortada
// del currículum, donde esos datos ya no están (RF-41).
@Entity
@Table(name = "dato_cv")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DatoCv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private String nombre;
    private String email;
    private String telefono;
    private String perfilResumen;
    // Hasta cinco, separadas por «|». No se consultan por habilidad, se enseñan.
    private String habilidades;
    private Integer experienciaMesesTotal;
    private String ultimoPuesto;
    private String ultimaEmpresa;
    private Integer ultimaMesesDuracion;
    private String educacionMaxima;
    private Long ejecucionIaId;
    private Instant actualizadoEn;
    private Instant creadoEn;
}
