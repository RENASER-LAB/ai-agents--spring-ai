package com.renaser.ai.ai_engine.organizacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// @DynamicUpdate (V81): la fila la escriben a la vez el alta y la suspensión de la plataforma,
// las banderas de personalización y ahora la presentación del panel. Con el UPDATE de la fila
// entera, el último en confirmar devolvía a lo que leyó las columnas del otro.
@Entity
@Table(name = "organizacion")
@org.hibernate.annotations.DynamicUpdate
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

    // ---------- La presentación (V81) ----------
    // Lo que la empresa cuenta de sí misma en «Perfil de la empresa». Todo opcional: vacío es
    // nulo. Las reglas viven en ReglasDeLaPresentacion; los beneficios y las redes, en sus
    // tablas (OrganizacionBeneficio, OrganizacionRedSocial).

    /** Por qué existe la empresa. No es {@code vacante.proposito}, que es el del puesto. */
    private String propositoEmpresa;
    private String quienesSomos;
    private String sector;
    /** Uno de {@link com.renaser.ai.ai_engine.organizacion.service.TamanoDeEmpresa}. */
    private String tamano;
    private Integer anioFundacion;
    /** El enlace de YouTube o Vimeo tal como se pegó. */
    private String videoPresentacion;
    private String paginaWeb;
    private Long logoArchivoId;
    private Long portadaArchivoId;
}
