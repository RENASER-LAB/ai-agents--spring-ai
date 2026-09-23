package com.renaser.ai.ai_engine.usuario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// El enlace para elegir una contraseña nueva (V61). Cuelga de la cuenta y no del correo:
// un mismo correo puede tener cuenta de equipo en dos empresas, y cada enlace cambia solo
// la suya. Se guarda el SHA-256 del token, nunca el token, y vale una sola vez.
@Entity
@Table(name = "recuperacion_clave")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RecuperacionClave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;
    private String tokenHash;
    private Instant creadoEn;
    private Instant venceEn;
    private Instant usadoEn;
    private Instant invalidadoEn;

    /**
     * Sirve = ni usado, ni reemplazado por otro más nuevo, ni vencido. Los tres casos que no
     * sirven contestan lo mismo hacia fuera: decir cuál fue le contaría a quien prueba
     * enlaces si alguno de los suyos estuvo vivo.
     */
    public boolean sirve(Instant ahora) {
        return usadoEn == null && invalidadoEn == null && venceEn.isAfter(ahora);
    }
}
