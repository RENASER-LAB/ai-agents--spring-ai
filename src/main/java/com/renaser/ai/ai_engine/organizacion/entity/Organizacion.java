package com.renaser.ai.ai_engine.organizacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "organizacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Organizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private String nombre;
    private boolean esActiva;
    private Instant creadoEn;

    // La dueña de la plataforma. Solo una puede serlo (índice único parcial, V37):
    // reemplaza al código 'RENASER' que estaba quemado en el código.
    private boolean esPlataforma;

    // Las banderas de personalización (pieza A). Apagada = la empresa lee el instrumento
    // de la plataforma; encendida = se le copió y es suyo. El único que las interpreta
    // es DuenoDelInstrumento: nadie más decide de quién son las filas.
    //
    // Las del banco y las pruebas (banco_propio, pruebas_puesto_propias) siguen en la tabla
    // pero ya no se mapean (V74): el banco y las pruebas de una empresa son siempre los suyos.
    private boolean pesosPropios;
    private boolean plantillasEvaluacionPropias;
}
