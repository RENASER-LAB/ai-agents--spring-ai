package com.renaser.ai.ai_engine.colaborador.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * La ficha de quien trabaja en la empresa (V64).
 *
 * <p>Independiente de {@code persona} y del perfil del portal, a propósito: el borrado de la
 * Ley 29733 anonimiza a la persona candidata y esta ficha se conserva, porque la relación
 * laboral tiene su propia base legal. Editar estos datos es corregir: no deja historial, deja
 * una fila en la auditoría.
 */
@Entity
@Table(name = "colaborador")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Colaborador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String tipoDocumento;
    private String numeroDocumento;
    private String nombres;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private LocalDate fechaNacimiento;
    private String sexo;
    private String estadoCivil;
    private String nacionalidad;
    private String celular;
    private String correoPersonal;
    private String correoCorporativo;
    private String direccion;
    private String provinciaUbigeo;
    private String nivelEducativoCodigo;
    private Long postulacionId;
    private Long creadoPorUsuarioId;
    private Instant creadoEn;
    private Instant actualizadoEn;

    /** «Rivas Sánchez, Ana María»: como se ordena y se busca en una lista de personal. */
    public String nombreCompleto() {
        String apellidos = apellidoMaterno == null || apellidoMaterno.isBlank()
                ? apellidoPaterno : apellidoPaterno + " " + apellidoMaterno;
        return apellidos + ", " + nombres;
    }
}
