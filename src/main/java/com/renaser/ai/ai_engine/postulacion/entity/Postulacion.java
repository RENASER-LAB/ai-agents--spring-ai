package com.renaser.ai.ai_engine.postulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// Un usuario en una vacante. Un solo estado a la vez, nunca dos.
@Entity
@Table(name = "postulacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Postulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    // El identificador que se muestra al candidato: no expone el id interno secuencial
    private UUID uuid;
    private Long usuarioId;
    private Long vacanteId;
    private String estadoCodigo;
    private String grupoPrioridad;
    // Solo en los estados finales de cierre
    private String motivoCierre;
    // Columna suelta: la tabla evaluacion llega en el hito 2
    private Long evaluacionId;
    // El examen de la etapa técnica cuando la vacante usa el cuestionario CAZATALENTOS.
    // Vacío con la prueba del puesto de siempre, que va por intento_prueba (V43)
    private Long evaluacionTecnicaId;
    private Integer rondasEvidenciaUsadas;
    // Cuándo cambió de estado por última vez: alimenta el cierre por inactividad
    private Instant movidoEn;
    private Instant creadoEn;
}
