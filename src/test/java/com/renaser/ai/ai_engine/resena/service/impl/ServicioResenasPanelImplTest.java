package com.renaser.ai.ai_engine.resena.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.EscribirResena;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.LaResenaDeMiEmpresa;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.Reportar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenasDeLaPostulacion;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.RespuestaParaLaAutora;
import com.renaser.ai.ai_engine.resena.entity.ReporteResena;
import com.renaser.ai.ai_engine.resena.entity.Resena;
import com.renaser.ai.ai_engine.resena.entity.RespuestaResena;
import com.renaser.ai.ai_engine.resena.exception.ContratacionFueraDeAlcanceException;
import com.renaser.ai.ai_engine.resena.repository.ReporteResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.ResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.RespuestaResenaRepository;
import com.renaser.ai.ai_engine.resena.service.AvisosDeResenas;
import com.renaser.ai.ai_engine.resena.service.LectorDeResenas;
import com.renaser.ai.ai_engine.resena.service.RelojDeResenas;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La reseña de la empresa a quien contrató, desde la ficha del panel.
 *
 * <p>Con el guardián de alcance DE VERDAD —{@link AlcanceSobreLaVacante} sobre dobles de sus
 * repositorios— y un reloj que se mueve: los plazos de 30 días se prueban moviendo la hora,
 * sin fechas quemadas, que caducan.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Panel · La reseña de la empresa a quien contrató")
class ServicioResenasPanelImplTest {

    private static final long EMPRESA = 1L;
    private static final long OTRA_EMPRESA = 2L;
    private static final long POSTULACION = 50L;
    private static final long VACANTE = 7L;
    private static final long USUARIO_CANDIDATO = 100L;
    private static final long PERSONA = 200L;
    private static final long RESPONSABLE = 10L;
    private static final String OPINION =
            "Muy responsable con los plazos y con el equipo de obra en todo momento.";

    private final Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private Permisos permisos;
    @Mock private TransicionEstadoRepository transiciones;
    @Mock private UsuarioRepository usuarios;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private NombresDeUsuarios nombres;
    @Mock private ResenaRepository resenas;
    @Mock private RespuestaResenaRepository respuestas;
    @Mock private ReporteResenaRepository reportes;
    @Mock private AvisosDeResenas avisos;
    @Mock private ServicioAuditoria auditoria;

    private RelojMovil reloj;
    private ServicioResenasPanelImpl servicio;
    /** La reseña viva de la contratación, la que devuelve la base mientras no se borre. */
    private Resena viva;
    private RespuestaResena respuestaViva;
    private final List<ReporteResena> reportesGuardados = new ArrayList<>();

    @BeforeEach
    void armar() {
        reloj = new RelojMovil(ahora);
        AlcanceSobreLaVacante alcance = new AlcanceSobreLaVacante(postulaciones, vacantes, permisos);
        LectorDeResenas lector = new LectorDeResenas(resenas, respuestas, organizaciones);
        servicio = new ServicioResenasPanelImpl(postulaciones, vacantes, alcance, transiciones,
                usuarios, organizaciones, nombres, resenas, respuestas, reportes, lector, avisos,
                auditoria, new RelojDeResenas(reloj));

        Vacante vacante = Vacante.builder().id(VACANTE).organizacionId(EMPRESA)
                .titulo("Desarrollador Backend").responsableUsuarioId(RESPONSABLE).build();
        lenient().when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante));
        lenient().when(vacantes.findByIdAndOrganizacionId(VACANTE, EMPRESA))
                .thenReturn(Optional.of(vacante));
        lenient().when(usuarios.findById(USUARIO_CANDIDATO)).thenReturn(Optional.of(
                Usuario.builder().id(USUARIO_CANDIDATO).personaId(PERSONA).build()));
        lenient().when(organizaciones.findById(EMPRESA)).thenReturn(Optional.of(
                Organizacion.builder().id(EMPRESA).nombre("Constructora Andina").build()));
        lenient().when(nombres.de(USUARIO_CANDIDATO)).thenReturn("Luis Perez");
        lenient().when(resenas.findByPostulacionIdAndBorradaEnIsNull(POSTULACION))
                .thenAnswer(i -> Optional.ofNullable(viva).filter(r -> r.getBorradaEn() == null));
        lenient().when(resenas.saveAndFlush(any(Resena.class))).thenAnswer(i -> {
            Resena r = i.getArgument(0);
            if (r.getId() == null) {
                r.setId(900L);
            }
            viva = r;
            return r;
        });
        lenient().when(respuestas.findByResenaIdAndBorradaEnIsNull(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(respuestaViva)
                        .filter(r -> r.getBorradaEn() == null));
        lenient().when(reportes.findByResenaIdInOrderByReportadoEnDesc(anyCollection()))
                .thenAnswer(i -> reportesGuardados.stream()
                        .sorted((a, b) -> b.getReportadoEn().compareTo(a.getReportadoEn()))
                        .toList());
        lenient().when(reportes.saveAndFlush(any(ReporteResena.class))).thenAnswer(i -> {
            ReporteResena r = i.getArgument(0);
            r.setId(700L + reportesGuardados.size());
            reportesGuardados.add(r);
            return r;
        });
    }

    // ============ Cuándo se abre ============

    @Nested
    @DisplayName("Cuándo se puede escribir")
    class Cuando {

        @Test
        @DisplayName("AC-01: contratado hace 31 días, el Responsable de su vacante ve el formulario y publica")
        void aLos31DiasSePublica() {
            contratadoHace(31);

            LaResenaDeMiEmpresa antes = servicio.ver(responsable(), POSTULACION).miResena();
            assertThat(antes.estado()).isEqualTo(LaResenaDeMiEmpresa.SE_PUEDE_ESCRIBIR);
            assertThat(antes.empresa()).isEqualTo("Constructora Andina");
            assertThat(antes.puesto()).isEqualTo("Desarrollador Backend");

            LaResenaDeMiEmpresa despues = servicio.publicar(responsable(), POSTULACION,
                    escribir("4", "   " + OPINION + "  "));

            assertThat(despues.estado()).isEqualTo(LaResenaDeMiEmpresa.EDITABLE);
            assertThat(viva.getEstrellas()).isEqualTo(4);
            assertThat(viva.getTexto()).isEqualTo(OPINION);
            assertThat(viva.getOrganizacionId()).isEqualTo(EMPRESA);
            assertThat(viva.getPersonaId()).isEqualTo(PERSONA);
            assertThat(viva.getEscritaPorUsuarioId()).isEqualTo(RESPONSABLE);
            assertThat(viva.getPublicadaEn()).isEqualTo(ahora);
            assertThat(despues.resena().editableHasta()).isEqualTo(ahora.plus(Duration.ofDays(30)));
            // AC-11: el aviso a la campana, con el nombre de la empresa, a su cuenta.
            verify(avisos).publicada(viva, "Constructora Andina", USUARIO_CANDIDATO);
        }

        @Test
        @DisplayName("AC-02: contratado hace 10 días, «desde el [contratación + 30]», sin formulario, y la API lo rechaza")
        void aLos10DiasAunNoToca() {
            Instant contratado = contratadoHace(10);

            LaResenaDeMiEmpresa bloque = servicio.ver(responsable(), POSTULACION).miResena();

            assertThat(bloque.estado()).isEqualTo(LaResenaDeMiEmpresa.AUN_NO_TOCA);
            assertThat(bloque.abreEn()).isEqualTo(contratado.plus(Duration.ofDays(30)));
            assertThat(bloque.resena()).isNull();
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.publicar(responsable(), POSTULACION,
                            escribir("5", OPINION)))
                    .withMessageContaining("Todavía no se puede reseñar");
            verify(resenas, never()).saveAndFlush(any());
            verify(avisos, never()).publicada(any(), any(), any());
        }

        @Test
        @DisplayName("Al cumplirse justo el mes ya se puede: manda el reloj del servidor")
        void justoAlMesSePuede() {
            contratadoHace(30);

            assertThat(servicio.ver(responsable(), POSTULACION).miResena().estado())
                    .isEqualTo(LaResenaDeMiEmpresa.SE_PUEDE_ESCRIBIR);
        }

        @Test
        @DisplayName("Cuenta la transición a CONTRATADO aunque venga del camino manual, sin decisión")
        void cuentaLaTransicionManual() {
            // No hay nada que mirar en la decisión: lo que manda es el estado y la fecha de su
            // transición, venga de donde venga.
            Instant contratado = contratadoHace(45);

            assertThat(servicio.ver(responsable(), POSTULACION).miResena().contratadoEn())
                    .isEqualTo(contratado);
        }

        @Test
        @DisplayName("AC-03: en cualquier estado distinto de CONTRATADO, ni bloque ni publicación")
        void soloAQuienSeContrato() {
            postulacion("VALIDACION_EN_CURSO");

            ResenasDeLaPostulacion vista = servicio.ver(responsable(), POSTULACION);

            assertThat(vista.puedeResenar()).isFalse();
            assertThat(vista.miResena()).isNull();
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.publicar(responsable(), POSTULACION,
                            escribir("5", OPINION)))
                    .withMessageContaining("Contratado");
        }
    }

    // ============ Quién ============

    @Nested
    @DisplayName("Quién puede")
    class Quien {

        @Test
        @DisplayName("AC-04: la empresa B recibe el 404 de siempre, sin confirmar que la postulación existe")
        void otraEmpresaRecibeElDeSiempre() {
            contratadoHace(31);
            ContextoUsuario deB = new ContextoUsuario(30L, 31L, OTRA_EMPRESA, "EQUIPO",
                    List.of(5L), Map.of("resenar_contratado", "TODO",
                            "ver_resenas_candidato", "TODO"));

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> servicio.publicar(deB, POSTULACION, escribir("5", OPINION)))
                    .isNotInstanceOf(ContratacionFueraDeAlcanceException.class);
            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> servicio.ver(deB, POSTULACION))
                    .isNotInstanceOf(ContratacionFueraDeAlcanceException.class);
            verify(resenas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Un Responsable de OTRA vacante de su empresa recibe un 404 que dice «fuera de tu alcance»")
        void responsableDeOtraVacante() {
            contratadoHace(31);
            ContextoUsuario otroResponsable = new ContextoUsuario(11L, 12L, EMPRESA, "EQUIPO",
                    List.of(3L), Map.of("resenar_contratado", "SUS_VACANTES",
                            "ver_resenas_candidato", "SUS_VACANTES"));

            assertThatExceptionOfType(ContratacionFueraDeAlcanceException.class)
                    .isThrownBy(() -> servicio.publicar(otroResponsable, POSTULACION,
                            escribir("5", OPINION)))
                    .withMessageContaining("fuera de tu alcance");
            assertThatExceptionOfType(ContratacionFueraDeAlcanceException.class)
                    .isThrownBy(() -> servicio.ver(otroResponsable, POSTULACION));
        }

        @Test
        @DisplayName("AC-05: sin resenar_contratado no hay bloque de autora, y la API lo rechaza")
        void sinPermisoNoHayFormulario() {
            contratadoHace(31);
            ContextoUsuario soloLee = new ContextoUsuario(10L, 11L, EMPRESA, "EQUIPO",
                    List.of(3L), Map.of("ver_resenas_candidato", "TODO"));

            ResenasDeLaPostulacion vista = servicio.ver(soloLee, POSTULACION);

            assertThat(vista.puedeVerResenas()).isTrue();
            assertThat(vista.puedeResenar()).isFalse();
            assertThat(vista.miResena()).isNull();
            assertThatExceptionOfType(ContratacionFueraDeAlcanceException.class)
                    .isThrownBy(() -> servicio.publicar(soloLee, POSTULACION,
                            escribir("5", OPINION)));
        }

        @Test
        @DisplayName("AC-27: sin ver_resenas_candidato, el DTO no trae ni el resumen ni la lista")
        void sinVerNoViajanLosDatos() {
            contratadoHace(31);
            ContextoUsuario soloEscribe = new ContextoUsuario(10L, 11L, EMPRESA, "EQUIPO",
                    List.of(3L), Map.of("resenar_contratado", "TODO"));

            ResenasDeLaPostulacion vista = servicio.ver(soloEscribe, POSTULACION);

            assertThat(vista.puedeVerResenas()).isFalse();
            assertThat(vista.resumen()).isNull();
            assertThat(vista.resenas()).isNull();
            assertThat(vista.puedeResenar()).isTrue();
        }
    }

    // ============ Qué se escribe ============

    @Nested
    @DisplayName("Lo que se publica")
    class LoQueSePublica {

        @Test
        @DisplayName("AC-06: sin estrellas, con 4,5, con menos de 30 o más de 1000 caracteres, no se guarda nada")
        void laValidacionEsLaMismaQueEnLaPantalla() {
            contratadoHace(31);

            assertThatIllegalArgumentException().isThrownBy(() ->
                    servicio.publicar(responsable(), POSTULACION, new EscribirResena(null, OPINION)));
            assertThatIllegalArgumentException().isThrownBy(() ->
                    servicio.publicar(responsable(), POSTULACION, escribir("4.5", OPINION)));
            assertThatIllegalArgumentException().isThrownBy(() ->
                    servicio.publicar(responsable(), POSTULACION, escribir("4", "Muy bien.")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                    servicio.publicar(responsable(), POSTULACION, escribir("4", "a".repeat(1001))));
            assertThatIllegalArgumentException().isThrownBy(() ->
                    servicio.publicar(responsable(), POSTULACION, escribir("4", " ".repeat(40))));
            verify(resenas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("AC-07: si otra persona de la empresa ya publicó, se rechaza con un 409")
        void unaPorContratacion() {
            contratadoHace(31);
            viva = resenaPublicadaHace(1);

            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.publicar(responsable(), POSTULACION,
                            escribir("3", OPINION)))
                    .withMessageContaining("ya tiene una reseña");
        }

        @Test
        @DisplayName("AC-07: y si las dos publican a la vez, la base decide y el segundo recibe el mismo 409")
        void laCarreraLaGanaLaBase() {
            contratadoHace(31);
            when(resenas.saveAndFlush(any(Resena.class)))
                    .thenThrow(new DataIntegrityViolationException("resena_una_viva_por_contratacion"));

            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.publicar(responsable(), POSTULACION,
                            escribir("3", OPINION)))
                    .withMessageContaining("ya tiene una reseña");
            verify(avisos, never()).publicada(any(), any(), any());
        }

        @Test
        @DisplayName("La auditoría guarda que hubo reseña y sus estrellas, NUNCA el texto")
        void laAuditoriaNoCopiaElTexto() {
            contratadoHace(31);

            servicio.publicar(responsable(), POSTULACION, escribir("5", OPINION));

            ArgumentCaptor<Object> nuevo = ArgumentCaptor.forClass(Object.class);
            verify(auditoria).registrar(eq(EMPRESA), any(), eq("publicar_resena"), eq("resena"),
                    eq(900L), isNull(), nuevo.capture(), isNull());
            assertThat(nuevo.getValue().toString()).doesNotContain(OPINION);
        }
    }

    // ============ Editar y borrar ============

    @Nested
    @DisplayName("Editar y borrar")
    class EditarYBorrar {

        @Test
        @DisplayName("AC-08: publicada hace 5 días, se edita y sale «Editada»")
        void seEditaDentroDelPlazo() {
            contratadoHace(60);
            viva = resenaPublicadaHace(5);

            LaResenaDeMiEmpresa bloque = servicio.editar(responsable(), POSTULACION,
                    escribir("2", OPINION + " Mejoró."));

            assertThat(viva.getEstrellas()).isEqualTo(2);
            assertThat(viva.getEditadaEn()).isEqualTo(ahora);
            assertThat(bloque.resena().editada()).isTrue();
            // Editarla no alarga su plazo: sigue contando desde la primera publicación.
            assertThat(bloque.resena().editableHasta())
                    .isEqualTo(viva.getPublicadaEn().plus(Duration.ofDays(30)));
            verify(avisos, never()).editadaYaRespondida(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Guardar lo mismo no es editar: ni «Editada» ni aviso")
        void guardarLoMismoNoEsEditar() {
            contratadoHace(60);
            viva = resenaPublicadaHace(5);

            servicio.editar(responsable(), POSTULACION, escribir("4", OPINION));

            assertThat(viva.getEditadaEn()).isNull();
        }

        @Test
        @DisplayName("AC-09: publicada hace 31 días, queda fija y la API rechaza editar y borrar")
        void pasadoElPlazoQuedaFija() {
            contratadoHace(90);
            viva = resenaPublicadaHace(31);

            assertThat(servicio.ver(responsable(), POSTULACION).miResena().estado())
                    .isEqualTo(LaResenaDeMiEmpresa.FIJA);
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.editar(responsable(), POSTULACION,
                            escribir("1", OPINION + " Otra.")))
                    .withMessageContaining("Ya no se puede cambiar");
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.borrar(responsable(), POSTULACION))
                    .withMessageContaining("Ya no se puede cambiar");
        }

        @Test
        @DisplayName("La ventana vence con el formulario abierto: manda el reloj del servidor")
        void venceConElFormularioAbierto() {
            contratadoHace(60);
            viva = resenaPublicadaHace(29);
            assertThat(servicio.ver(responsable(), POSTULACION).miResena().estado())
                    .isEqualTo(LaResenaDeMiEmpresa.EDITABLE);

            reloj.avanzar(Duration.ofDays(2));

            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.editar(responsable(), POSTULACION,
                            escribir("1", OPINION + " Otra.")));
        }

        @Test
        @DisplayName("AC-10: borrada dentro del plazo, la contratación queda libre; su respuesta y el reporte pendiente se van con ella")
        void borrarLaDejaLibre() {
            contratadoHace(60);
            viva = resenaPublicadaHace(3);
            respuestaViva = respuestaPublicadaHace(2, 28);
            reportesGuardados.add(reporte(ReporteResena.RESENA, null, ReporteResena.PENDIENTE,
                    ahora.minus(Duration.ofDays(1))));

            LaResenaDeMiEmpresa bloque = servicio.borrar(responsable(), POSTULACION);

            assertThat(bloque.estado()).isEqualTo(LaResenaDeMiEmpresa.SE_PUEDE_ESCRIBIR);
            assertThat(viva.getBorradaEn()).isEqualTo(ahora);
            assertThat(respuestaViva.getBorradaEn()).isEqualTo(ahora);
            assertThat(reportesGuardados.get(0).getEstado()).isEqualTo(ReporteResena.RETIRADA);
            assertThat(reportesGuardados.get(0).getResueltoEn()).isEqualTo(ahora);
        }

        @Test
        @DisplayName("AC-37: editar una reseña ya respondida le da a la persona 30 días desde esa edición, y se le avisa")
        void editarLaRespondidaReabreElPlazo() {
            contratadoHace(90);
            viva = resenaPublicadaHace(28);
            respuestaViva = respuestaPublicadaHace(27, 3);

            servicio.editar(responsable(), POSTULACION, escribir("3", OPINION + " Cambió."));

            Instant hasta = ahora.plus(Duration.ofDays(30));
            assertThat(respuestaViva.getEditableHasta()).isEqualTo(hasta);
            verify(avisos).editadaYaRespondida(viva, "Constructora Andina", USUARIO_CANDIDATO,
                    hasta);
        }

        @Test
        @DisplayName("Ocultada por la plataforma: atenuada con la nota, sin respuesta, y no se puede tocar")
        void laOcultadaNoSeToca() {
            contratadoHace(60);
            viva = resenaPublicadaHace(5);
            viva.setOcultadaEn(ahora.minus(Duration.ofDays(1)));
            viva.setNotaOcultacion("Revela datos de salud");
            respuestaViva = respuestaPublicadaHace(4, 26);

            LaResenaDeMiEmpresa bloque = servicio.ver(responsable(), POSTULACION).miResena();

            assertThat(bloque.estado()).isEqualTo(LaResenaDeMiEmpresa.OCULTADA);
            assertThat(bloque.resena().notaOcultacion()).isEqualTo("Revela datos de salud");
            assertThat(bloque.resena().respuesta()).isNull();
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.borrar(responsable(), POSTULACION));
        }
    }

    // ============ La respuesta, vista y reportada por la autora ============

    @Nested
    @DisplayName("La respuesta, desde la empresa autora")
    class LaRespuesta {

        @Test
        @DisplayName("Sin reportes se puede reportar; pendiente dice «en revisión»")
        void seReportaUnaVez() {
            contratadoHace(60);
            viva = resenaPublicadaHace(10);
            respuestaViva = respuestaPublicadaHace(9, 21);

            assertThat(respuestaDeLaAutora().puedeReportar()).isTrue();

            LaResenaDeMiEmpresa bloque = servicio.reportarRespuesta(responsable(), POSTULACION,
                    new Reportar("FALSA", null));

            ReporteResena guardado = reportesGuardados.get(0);
            assertThat(guardado.getObjeto()).isEqualTo(ReporteResena.RESPUESTA);
            assertThat(guardado.getRespuestaId()).isEqualTo(respuestaViva.getId());
            assertThat(guardado.getOrganizacionReportanteId()).isEqualTo(EMPRESA);
            assertThat(guardado.getReportadoPorUsuarioId()).isEqualTo(RESPONSABLE);
            assertThat(bloque.resena().respuesta().reporte()).isEqualTo("EN_REVISION");
            assertThat(bloque.resena().respuesta().puedeReportar()).isFalse();
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.reportarRespuesta(responsable(), POSTULACION,
                            new Reportar("FALSA", null)))
                    .withMessageContaining("revisando");
        }

        @Test
        @DisplayName("Con «Otro motivo» y sin comentario no se envía")
        void otroMotivoPideComentario() {
            contratadoHace(60);
            viva = resenaPublicadaHace(10);
            respuestaViva = respuestaPublicadaHace(9, 21);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> servicio.reportarRespuesta(responsable(), POSTULACION,
                            new Reportar("OTRO", "  ")));
            assertThat(reportesGuardados).isEmpty();
        }

        @Test
        @DisplayName("Mantenida: dice la nota y no se reporta otra vez… salvo que la persona la edite después")
        void mantenidaSeReportaOtraVezSiSeEdita() {
            contratadoHace(60);
            viva = resenaPublicadaHace(10);
            respuestaViva = respuestaPublicadaHace(9, 21);
            ReporteResena mantenido = reporte(ReporteResena.RESPUESTA, respuestaViva.getId(),
                    ReporteResena.MANTENIDA, ahora.minus(Duration.ofDays(5)));
            mantenido.setResueltoEn(ahora.minus(Duration.ofDays(4)));
            mantenido.setNotaRevision("No incumple las normas");
            reportesGuardados.add(mantenido);

            RespuestaParaLaAutora antes = respuestaDeLaAutora();
            assertThat(antes.reporte()).isEqualTo(ReporteResena.MANTENIDA);
            assertThat(antes.notaReporte()).isEqualTo("No incumple las normas");
            assertThat(antes.puedeReportar()).isFalse();
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.reportarRespuesta(responsable(), POSTULACION,
                            new Reportar("FALSA", null)));

            respuestaViva.setEditadaEn(ahora.minus(Duration.ofDays(1)));

            assertThat(respuestaDeLaAutora().puedeReportar()).isTrue();
        }

        @Test
        @DisplayName("Ocultada: atenuada con la nota de la plataforma, sin reportar otra vez")
        void ocultadaVaAtenuada() {
            contratadoHace(60);
            viva = resenaPublicadaHace(10);
            respuestaViva = respuestaPublicadaHace(9, 21);
            respuestaViva.setOcultadaEn(ahora.minus(Duration.ofDays(1)));
            respuestaViva.setNotaOcultacion("Insulta a la empresa");

            RespuestaParaLaAutora respuesta = respuestaDeLaAutora();

            assertThat(respuesta.ocultada()).isTrue();
            assertThat(respuesta.reporte()).isEqualTo(ReporteResena.OCULTADA);
            assertThat(respuesta.notaReporte()).isEqualTo("Insulta a la empresa");
            assertThat(respuesta.puedeReportar()).isFalse();
        }

        @Test
        @DisplayName("AC-40: la empresa B no puede reportarla: su postulación no existe para ella")
        void laEmpresaBNoLaReporta() {
            contratadoHace(60);
            viva = resenaPublicadaHace(10);
            respuestaViva = respuestaPublicadaHace(9, 21);
            ContextoUsuario deB = new ContextoUsuario(30L, 31L, OTRA_EMPRESA, "EQUIPO",
                    List.of(5L), Map.of("resenar_contratado", "TODO"));

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> servicio.reportarRespuesta(deB, POSTULACION,
                            new Reportar("FALSA", null)));
            assertThat(reportesGuardados).isEmpty();
        }
    }

    // ============ La lectura entre empresas ============

    @Test
    @DisplayName("AC-26: con ver_resenas_candidato se leen las visibles de TODAS las empresas, sin las ocultadas")
    void seLeenLasDeTodasLasEmpresas() {
        contratadoHace(60);
        Resena deOtra = Resena.builder().id(801L).postulacionId(60L).organizacionId(OTRA_EMPRESA)
                .personaId(PERSONA).estrellas(5).texto(OPINION)
                .publicadaEn(ahora.minus(Duration.ofDays(2))).build();
        Resena propia = Resena.builder().id(802L).postulacionId(POSTULACION)
                .organizacionId(EMPRESA).personaId(PERSONA).estrellas(4).texto(OPINION)
                .publicadaEn(ahora.minus(Duration.ofDays(9))).build();
        Resena ocultada = Resena.builder().id(803L).postulacionId(61L)
                .organizacionId(OTRA_EMPRESA).personaId(PERSONA).estrellas(1).texto(OPINION)
                .publicadaEn(ahora.minus(Duration.ofDays(1))).ocultadaEn(ahora)
                .notaOcultacion("Ofensiva").build();
        when(resenas.findByPersonaIdAndBorradaEnIsNull(PERSONA))
                .thenReturn(List.of(propia, ocultada, deOtra));
        when(organizaciones.findAllById(any())).thenReturn(List.of(
                Organizacion.builder().id(EMPRESA).nombre("Constructora Andina").build(),
                Organizacion.builder().id(OTRA_EMPRESA).nombre("Acme").build()));

        ResenasDeLaPostulacion vista = servicio.ver(talento(), POSTULACION);

        assertThat(vista.persona()).isEqualTo("Luis Perez");
        assertThat(vista.resenas()).extracting(r -> r.id()).containsExactly(801L, 802L);
        assertThat(vista.resenas()).extracting(r -> r.empresa())
                .containsExactly("Acme", "Constructora Andina");
        assertThat(vista.resumen().promedio()).isEqualByComparingTo("4.5");
        assertThat(vista.resumen().cantidad()).isEqualTo(2);
    }

    // ============ Ayudantes ============

    private RespuestaParaLaAutora respuestaDeLaAutora() {
        return servicio.ver(responsable(), POSTULACION).miResena().resena().respuesta();
    }

    private ContextoUsuario responsable() {
        return new ContextoUsuario(RESPONSABLE, 11L, EMPRESA, "EQUIPO", List.of(3L),
                Map.of("resenar_contratado", "SUS_VACANTES",
                        "ver_resenas_candidato", "SUS_VACANTES"));
    }

    private ContextoUsuario talento() {
        return new ContextoUsuario(20L, 21L, EMPRESA, "EQUIPO", List.of(2L),
                Map.of("resenar_contratado", "TODO", "ver_resenas_candidato", "TODO"));
    }

    private Postulacion postulacion(String estado) {
        Postulacion p = Postulacion.builder().id(POSTULACION).organizacionId(EMPRESA)
                .vacanteId(VACANTE).usuarioId(USUARIO_CANDIDATO).estadoCodigo(estado)
                .movidoEn(ahora.minus(Duration.ofDays(400)))
                .build();
        lenient().when(postulaciones.findByIdAndOrganizacionId(POSTULACION, EMPRESA))
                .thenReturn(Optional.of(p));
        return p;
    }

    /** Una contratación de hace tantos días: la transición a CONTRATADO se inserta con esa fecha. */
    private Instant contratadoHace(int dias) {
        postulacion("CONTRATADO");
        Instant cuando = ahora.minus(Duration.ofDays(dias));
        lenient().when(transiciones.findFirstByPostulacionIdAndEstadoNuevoCodigoOrderByOcurridaEnDesc(
                        POSTULACION, "CONTRATADO"))
                .thenReturn(Optional.of(TransicionEstado.builder().postulacionId(POSTULACION)
                        .estadoNuevoCodigo("CONTRATADO").ocurridaEn(cuando).build()));
        return cuando;
    }

    private Resena resenaPublicadaHace(int dias) {
        return Resena.builder().id(900L).postulacionId(POSTULACION).organizacionId(EMPRESA)
                .personaId(PERSONA).estrellas(4).texto(OPINION).escritaPorUsuarioId(RESPONSABLE)
                .publicadaEn(ahora.minus(Duration.ofDays(dias))).build();
    }

    private RespuestaResena respuestaPublicadaHace(int dias, int quedanDias) {
        return RespuestaResena.builder().id(950L).resenaId(900L).usuarioId(USUARIO_CANDIDATO)
                .texto("Gracias por la oportunidad, aprendí mucho con el equipo.")
                .publicadaEn(ahora.minus(Duration.ofDays(dias)))
                .editableHasta(ahora.plus(Duration.ofDays(quedanDias))).build();
    }

    private ReporteResena reporte(String objeto, Long respuestaId, String estado,
                                  Instant cuando) {
        return ReporteResena.builder().id(600L + reportesGuardados.size()).resenaId(900L)
                .respuestaId(respuestaId).objeto(objeto).motivo("FALSA").estado(estado)
                .reportadoEn(cuando).build();
    }

    private static EscribirResena escribir(String estrellas, String texto) {
        return new EscribirResena(new BigDecimal(estrellas), texto);
    }

    /** Un reloj que se puede adelantar: los plazos se prueban moviendo la hora. */
    static final class RelojMovil extends Clock {

        private Instant ahora;

        RelojMovil(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
