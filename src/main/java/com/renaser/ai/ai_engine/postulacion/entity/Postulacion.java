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
    /**
     * Cuánto dijo que quiere ganar al postular a ESTA vacante (V54).
     *
     * <p>Un monto único y no una banda: el perfil guarda su expectativa general como rango,
     * y aquí se concreta en el número que se puede poner de frente contra el presupuesto de
     * la vacante. Se prellena con lo del perfil y se confirma o se corrige.
     *
     * <p>⚠️ <b>Vacío no significa que no quisiera decirlo.</b> Significa que la vacante tenía
     * el sueldo oculto —y entonces no se le exigió, por la simetría del trato— o que postuló
     * antes de que esto existiera. Quien lo pinte tiene que decir cuál de las dos, nunca un
     * guion a secas.
     *
     * <p>⚠️ <b>Se trata como la pretensión del perfil: solo con el permiso
     * {@code ver_pretension}.</b> Es el mismo dato y merece el mismo cuidado — quien negocia
     * el sueldo no debería saberlo antes de tiempo.
     */
    private java.math.BigDecimal pretensionMonto;
    private String pretensionMoneda;
    private Instant pretensionDeclaradaEn;
    // Cuándo cambió de estado por última vez: alimenta el cierre por inactividad
    private Instant movidoEn;
    private Instant creadoEn;
}
