package com.renaser.ai.ai_engine.simulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

// Quién conduce la sesión. RF-91 habla de "responsables" en plural, y quién la creó no es lo
// mismo que quién la conduce: esta tabla es lo que deja constancia de quién facilitó.
//
// Qué roles PUEDEN facilitar no se decide aquí ni en el código: vive en el parámetro
// roles_facilitador_simulacion, editable desde el panel.
@Entity
@Table(name = "sesion_responsable")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@IdClass(SesionResponsable.Clave.class)
public class SesionResponsable {

    @Id
    private Long sesionSimulacionId;
    @Id
    private Long usuarioId;

    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long sesionSimulacionId;
        private Long usuarioId;
    }
}
