package com.renaser.ai.ai_engine.perfil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que entra y sale del perfil del candidato.
 *
 * <p>Cada elemento de lista viaja con su {@code origen} (PERSONA o CURRICULUM) y su
 * {@code confirmado}: la pantalla tiene que poder distinguir lo que la persona escribió de
 * lo que un modelo dedujo de su currículum. Un dato CURRICULUM sin confirmar no lo ha
 * dicho la persona.
 *
 * <p>La pretensión salarial solo aparece donde corresponde: en el portal siempre (es suya);
 * en el panel, únicamente con el permiso {@code ver_pretension} — y entonces el DTO del
 * panel la lleva; sin permiso el campo va en null y no se serializa.
 */
public final class DtosPerfil {

    private DtosPerfil() {
    }

    // ---------- lo que sale ----------

    public record PerfilCompleto(
            String titular,
            String resumen,
            List<String> habilidades,
            Integer experienciaMeses,
            String ubicacion,
            String disponibilidad,
            // Sin permiso de verla no viaja NI COMO null: el nombre del campo en el JSON ya
            // diria que existe una pretension que no puedes ver.
            @com.fasterxml.jackson.annotation.JsonInclude(
                    com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            Pretension pretension,
            List<ExperienciaItem> experiencia,
            List<EducacionItem> educacion,
            List<IdiomaItem> idiomas,
            List<CertificacionItem> certificaciones,
            List<EnlaceItem> enlaces,
            LecturaCv lecturaCv) {
    }

    public record Pretension(BigDecimal min, BigDecimal max, String moneda) {
    }

    public record ExperienciaItem(Long id, String puesto, String empresa, LocalDate desde,
                                  LocalDate hasta, String descripcion, String origen,
                                  boolean confirmado) {
    }

    public record EducacionItem(Long id, String titulo, String institucion, String nivelCodigo,
                                LocalDate desde, LocalDate hasta, boolean enCurso, String origen,
                                boolean confirmado) {
    }

    public record IdiomaItem(Long id, String idioma, String nivelCodigo, String origen,
                             boolean confirmado) {
    }

    public record CertificacionItem(Long id, String nombre, String entidad, LocalDate emitidaEn,
                                    LocalDate venceEn, String origen, boolean confirmado) {
    }

    public record EnlaceItem(Long id, String tipo, String url) {
    }

    /** En qué punto está la lectura del último currículum. {@code NO_LEGIBLE} no es un
     * error: el sistema prefirió no leer nada antes que inventarse datos. */
    public record LecturaCv(String estado, Instant actualizadoEn) {
    }

    public record OpcionCatalogo(String codigo, String nombre) {
    }

    // ---------- lo que entra ----------

    public record EditarCabecera(
            @Size(max = 200) String titular,
            @Size(max = 2000) String resumen,
            List<String> habilidades,
            // El mismo tope que aplica la lectura del curriculum: sesenta anos de carrera
            // se creen, setenta y cinco no. Sin esto, el portal aceptaba meses negativos.
            @jakarta.validation.constraints.Min(0)
            @jakarta.validation.constraints.Max(720)
            Integer experienciaMeses,
            @Size(max = 200) String ubicacion,
            @Size(max = 200) String disponibilidad,
            Pretension pretension) {
    }

    public record EditarExperiencia(
            @NotBlank @Size(max = 200) String puesto,
            @NotBlank @Size(max = 200) String empresa,
            @NotNull LocalDate desde,
            LocalDate hasta,
            @Size(max = 2000) String descripcion) {
    }

    public record EditarEducacion(
            @NotBlank @Size(max = 200) String titulo,
            @NotBlank @Size(max = 200) String institucion,
            String nivelCodigo,
            LocalDate desde,
            LocalDate hasta,
            boolean enCurso) {
    }

    public record EditarIdioma(
            @NotBlank @Size(max = 100) String idioma,
            @NotBlank String nivelCodigo) {
    }

    public record EditarCertificacion(
            @NotBlank @Size(max = 200) String nombre,
            @Size(max = 200) String entidad,
            LocalDate emitidaEn,
            LocalDate venceEn) {
    }

    public record EditarEnlace(
            @NotBlank String tipo,
            @NotBlank @Size(max = 500) String url) {
    }

    public record Reordenar(@NotNull List<Long> ids) {
    }
}
