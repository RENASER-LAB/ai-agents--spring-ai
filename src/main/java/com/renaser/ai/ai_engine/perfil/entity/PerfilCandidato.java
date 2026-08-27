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
    private Instant actualizadoEn;
    private Instant creadoEn;
}
