package com.renaser.ai.ai_engine.postulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cada cambio de estado, para siempre. La base impide UPDATE y DELETE con un trigger,
// y exige motivo cuando el cambio no lo hizo el sistema.
@Entity
@Table(name = "transicion_estado")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TransicionEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    // Null en la primera transición
    private String estadoAnteriorCodigo;
    private String estadoNuevoCodigo;
    // Null si lo hizo el sistema
    private Long usuarioId;
    // Con qué rol actuó: alguien con dos roles pudo actuar con uno u otro
    private Long rolId;
    private boolean esSistema;
    private boolean esPorLote;
    private String motivo;
    /**
     * Cómo se enteró el candidato (V70): CORREO, NINGUNO o POR_LA_CAMPANA. Vacío en las
     * transiciones anteriores a la V70 y en la primera, la de nacer en POSTULADA.
     *
     * <p>Es lo que deja a los recordatorios saber si el turno se abrió avisando: a quien se movió
     * «sin avisar» no se le recuerda nada, porque el recordatorio le contaría lo que el equipo
     * decidió no contarle.
     */
    private String avisoAlCandidato;
    private Instant ocurridaEn;
    private Instant creadoEn;
}
