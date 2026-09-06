package com.renaser.ai.ai_engine.perfil.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// El perfil de la persona: unico, transversal a organizaciones y de su dueño. Cuelga de
// persona y no de usuario a proposito — el usuario existe una vez por organizacion, y un
// perfil por usuario obligaria al candidato a llenarlo una vez por empresa.
//
// NO puntua: ninguna nota ni ranking lee de aqui. Sirve para leer a un candidato sin abrir
// su curriculum.
@Entity
@Table(name = "perfil_candidato")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PerfilCandidato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long personaId;
    private String titular;
    private String resumen;
    // Como en dato_cv: separadas por «|». No se consultan por habilidad, se enseñan.
    private String habilidades;
    private Integer experienciaMeses;
    private String ubicacion;
    private String disponibilidad;
    // Un rango con moneda, o nada: lo impone un CHECK en la base. Solo la ve quien tenga
    // el permiso ver_pretension, y nunca viaja en listas ni rankings.
    private BigDecimal pretensionMin;
    private BigDecimal pretensionMax;
    private String pretensionMoneda;

    // La foto. ⚠️ SOLO la ve el candidato en su portal: no entra en el DTO del panel ni en
    // el texto que lee la IA (RF-41). Decidido el 05/09/2026; ensenarsela a quien decide
    // necesita un RF nuevo y otro texto de consentimiento, no un campo mas en un mapper.
    private Long fotoArchivoId;
    // O la suya o una del catalogo de la casa, nunca las dos: lo impone un CHECK.
    private Long portadaArchivoId;
    private String portadaGaleria;
    // Su curriculum, el ultimo (RF-162). Al postular se COPIA a la organizacion de la
    // vacante en vez de compartirse: un archivo sellado con otra organizacion no se abre
    // desde el panel de esa empresa, que es lo que arreglo la V48.
    private Long cvArchivoId;
    private Instant cvActualizadoEn;

    private Instant actualizadoEn;
    private Instant creadoEn;
}
