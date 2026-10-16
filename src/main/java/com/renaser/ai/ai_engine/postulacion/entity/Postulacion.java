package com.renaser.ai.ai_engine.postulacion.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

// Un usuario en una vacante. Un solo estado a la vez, nunca dos.
//
// ⚠️ @DynamicUpdate: cada guardado escribe SOLO las columnas que cambió (QA-04, QA-05 de
// specs/pesos-con-candidatos-y-fin-del-prestamo.md). La fila la escriben a la vez caminos que
// tocan columnas distintas: la máquina de estados (estado, movido_en, motivo_cierre) y el
// recálculo del Perfil Integral (grupo_prioridad), que un cambio de pesos corre para toda la
// vacante en una sola transacción. Con el UPDATE de la fila entera, el último en confirmar
// reescribía la columna del otro con lo que leyó antes: la entrega que esperaba al cambio le
// devolvía a la persona el grupo de la versión vieja, y el cambio devolvía a su etapa de antes
// a quien el equipo había movido o descartado mientras corría.
//
// Lo que se calcula después de esperar al cerrojo de los pesos parte además de la fila releída
// (CerrojoDeLosPesos#bloquearParaCalificarYReleer): con la foto de antes, un grupo que vuelve a
// coincidir con ella no se escribiría. No hay triggers sobre postulacion, y las escrituras por
// consulta no dependen de reescribir la fila entera.
@Entity
@DynamicUpdate
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
     * Cuánto dijo que quiere ganar al postular a ESTA vacante (V55).
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
