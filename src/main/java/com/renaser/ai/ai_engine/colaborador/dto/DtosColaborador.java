package com.renaser.ai.ai_engine.colaborador.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Los contratos de la gestión de personas (V64).
 *
 * <p>⚠️ <b>El sueldo no viaja a quien no tiene {@code ver_sueldos}</b>: sus campos van con
 * {@code NON_NULL} y el servicio los deja en null, así que la clave ni aparece en el JSON. No
 * basta con ocultarlo en pantalla.
 */
public final class DtosColaborador {

    private DtosColaborador() {}

    /** Los filtros de la lista. Los vacíos no filtran. */
    public record Filtros(java.util.Collection<String> estados, Long sedeId, Long areaId,
                          Long cargoId, boolean porVencer, String busqueda) {}

    public record Opcion(String codigo, String nombre) {}

    public record OpcionConId(Long id, String nombre) {}

    // ============ La lista ============

    /** Una fila de la lista. Nunca lleva sueldo, tenga quien mire el permiso o no. */
    public record FilaColaborador(Long id, String nombreCompleto, String tipoDocumento,
                                  String tipoDocumentoNombre, String numeroDocumento,
                                  String cargo, String area, String sede, Long jefeId, String jefe,
                                  LocalDate fechaIngreso, LocalDate finContrato, LocalDate fechaCese,
                                  String estado) {}

    /**
     * Una página de la lista.
     *
     * @param total            cuántas cumplen los filtros: el «N colaboradores»
     * @param hayColaboradores si la empresa tiene alguno, con o sin filtros. Separa «todavía
     *                         no hay nadie» de «ningún colaborador cumple estos filtros»
     */
    public record PaginaColaboradores(List<FilaColaborador> filas, long total, int pagina,
                                      int tamano, boolean hayColaboradores) {}

    /** Lo que ofrecen los formularios, en el momento de pedirlo. */
    public record OpcionesColaborador(List<Opcion> tiposDocumento, List<Opcion> sexos,
                                      List<Opcion> estadosCiviles, List<Opcion> nivelesEducativos,
                                      List<OpcionConId> sedes, List<OpcionConId> areas,
                                      List<OpcionConId> cargos, List<Opcion> tiposContrato,
                                      List<String> contratosConFin, String contratoIndeterminado,
                                      List<Opcion> regimenes, List<Opcion> monedas,
                                      List<Opcion> motivosCambio, List<Opcion> motivosCese,
                                      boolean puedeEditar, boolean puedeVerSueldos) {}

    // ============ Contratados pendientes de alta ============

    public record ContratadoPendiente(Long postulacionId, String nombre, Long vacanteId,
                                      String vacante, Instant contratadoEn) {}

    public record NoDarDeAlta(@NotBlank String motivo) {}

    /**
     * El alta de un contratado, precargada con lo que ya se sabe de él.
     *
     * <p>Apellidos van enteros en {@code apellidoPaterno}: selección guarda un solo campo y
     * separarlo a ciegas rompe los compuestos («De la Cruz Pérez»). RR.HH. lo separa a mano.
     */
    public record Precarga(Long postulacionId, String vacante, String nombres,
                           String apellidoPaterno, String correoPersonal, String celular,
                           Long cargoId, Long areaId,
                           @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal sueldoBase,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String moneda,
                           Long colaboradorId) {}

    // ============ Escribir ============

    /** Identidad, contacto, domicilio y formación: lo que se corrige sin historial. */
    public record DatosPersonales(
            @NotBlank String tipoDocumento,
            @NotBlank @Size(max = 30) String numeroDocumento,
            @NotBlank @Size(max = 120) String nombres,
            @NotBlank @Size(max = 120) String apellidoPaterno,
            @Size(max = 120) String apellidoMaterno,
            @NotNull LocalDate fechaNacimiento,
            @NotBlank String sexo,
            String estadoCivil,
            @Size(max = 60) String nacionalidad,
            @Size(max = 30) String celular,
            @Size(max = 160) String correoPersonal,
            @Size(max = 160) String correoCorporativo,
            @Size(max = 300) String direccion,
            String provinciaUbigeo,
            String nivelEducativoCodigo) {}

    /**
     * Dónde y en qué condiciones. Sin {@code ver_sueldos}, el sueldo que llegue se ignora: en
     * un alta queda vacío y en un cambio pasa igual que estaba.
     */
    public record DatosSituacion(
            @NotNull Long sedeId,
            @NotNull Long areaId,
            @NotNull Long cargoId,
            Long jefeId,
            @NotBlank String tipoContrato,
            LocalDate finContrato,
            LocalDate finPeriodoPrueba,
            @NotBlank String regimenLaboral,
            BigDecimal sueldoBase,
            String moneda) {}

    public record AltaColaborador(@NotNull @Valid DatosPersonales persona,
                                  @NotNull LocalDate fechaIngreso,
                                  @NotNull @Valid DatosSituacion situacion,
                                  Long postulacionId) {}

    public record RegistrarCambio(@NotNull LocalDate vigenteDesde,
                                  @NotBlank String tipoMotivo,
                                  @Size(max = 500) String detalle,
                                  @NotNull @Valid DatosSituacion situacion) {}

    public record Anulacion(@NotBlank @Size(max = 500) String motivo) {}

    public record RegistrarCese(@NotNull LocalDate fechaCese, @NotBlank String motivoCodigo,
                                @Size(max = 500) String observacion) {}

    public record Reingreso(@NotNull LocalDate fechaIngreso, @NotNull @Valid DatosSituacion situacion,
                            Long postulacionId) {}

    public record Creado(Long id) {}

    // ============ La ficha ============

    public record Perfil(String tipoDocumento, String tipoDocumentoNombre, String numeroDocumento,
                         String nombres, String apellidoPaterno, String apellidoMaterno,
                         LocalDate fechaNacimiento, String sexo, String sexoNombre,
                         String estadoCivil, String estadoCivilNombre, String nacionalidad,
                         String celular, String correoPersonal, String correoCorporativo,
                         String direccion, String provinciaUbigeo, String provinciaNombre,
                         String nivelEducativoCodigo, String nivelEducativoNombre) {}

    /**
     * Una situación laboral, tal como se pinta. Lo desactivado sale marcado —«(inactivo)»,
     * «(cesado)»— y no desaparece: quien ya lo tenía lo conserva.
     */
    public record Situacion(Long id, LocalDate vigenteDesde, LocalDate vigenteHasta,
                            Long sedeId, String sede, boolean sedeActiva,
                            Long areaId, String area, boolean areaActiva,
                            Long cargoId, String cargo, boolean cargoActivo,
                            Long jefeId, String jefe, boolean jefeCesado,
                            String tipoContrato, String tipoContratoNombre, LocalDate finContrato,
                            LocalDate finPeriodoPrueba, String regimenLaboral, String regimenNombre,
                            @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal sueldoBase,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String moneda,
                            String tipoMotivo, String tipoMotivoNombre, String detalleMotivo,
                            String registradoPor, Instant registradoEn) {}

    public record Periodo(Long id, LocalDate fechaIngreso, LocalDate fechaCese, String motivoCese,
                          String motivoCeseNombre, String observacionCese) {}

    public record Reporte(Long id, String nombre) {}

    /**
     * La ficha entera.
     *
     * @param situacion   la vigente hoy; la primera si todavía no entró, la última si cesó
     * @param programados los cambios con fecha futura, que todavía no rigen
     * @param reportes    quién lo tiene hoy como jefe directo: el cese los nombra
     * @param base        de dónde parte un cambio o un reingreso: la última del periodo
     */
    public record FichaColaborador(Long id, String nombreCompleto, String estado, Perfil perfil,
                                   Long vacanteId, String vacante, Situacion situacion,
                                   Periodo periodo, List<Situacion> programados,
                                   Situacion base, List<Reporte> reportes,
                                   boolean puedeEditar, boolean puedeVerSueldos,
                                   boolean puedeAnularCese) {}

    // ============ El historial ============

    public record CambioDeCampo(String campo, String antes, String despues) {}

    /**
     * Una línea del historial.
     *
     * @param tipo {@code INGRESO}, {@code REINGRESO}, {@code CAMBIO}, {@code CESE} o
     *             {@code CESE_ANULADO}
     */
    public record EntradaHistorial(String tipo, LocalDate fecha, String titulo,
                                   List<CambioDeCampo> cambios, String motivo, String detalle,
                                   String registradoPor, Instant registradoEn,
                                   boolean programado, boolean anulado, String anuladoPor,
                                   Instant anuladoEn, String motivoAnulacion) {}

    // ============ La carga por Excel ============

    public record ErrorDeCarga(int fila, String columna, String valor, String mensaje) {}

    public record ResultadoCarga(int altas, int actualizados) {}
}
