package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoVacanteRepository;
import com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.postulacion.service.PostulacionesEnCarrera;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.ActualizarRemuneracion;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.GuardarVacante;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.RemuneracionDeLaVacante;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.VacantePanel;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Archivar una vacante cerrada, y lo que deja de poder hacerse con ella.
 *
 * <p>Lo que se protege aquí son las dos mitades de la misma decisión:
 *
 * <ul>
 *   <li><b>Cuándo se puede archivar.</b> Solo una cerrada, solo sin nadie en carrera, y las
 *       dos cosas <b>se vuelven a mirar al confirmar</b>: el modal del panel es una foto de
 *       hace un minuto, y en ese minuto cabe una postulación nueva o el compañero de al lado.
 *   <li><b>Qué deja de poder hacerse.</b> Una archivada se lee entera y no se mueve. Esa
 *       mitad no se puede sostener escondiendo botones: el formulario que alguien dejó
 *       abierto antes del archivo sigue sabiendo la URL del PUT.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Archivar y desarchivar una vacante")
class ArchivarVacanteTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long VACANTE = 40L;

    /** Quien puede cerrar —y por tanto archivar— y además editar. */
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            7L, 3L, ORGANIZACION, "EQUIPO", List.of(1L),
            Map.of("editar_vacante", "TODO", "cerrar_vacante", "TODO", "ver_vacantes", "TODO"));

    /** Quien solo mira: ve las vacantes y no puede archivar ninguna. */
    private static final ContextoUsuario SOLO_MIRA = new ContextoUsuario(
            8L, 4L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of("ver_vacantes", "TODO"));

    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private RequisitoObjetivoRepository requisitos;
    @Mock private SolicitudTalentoRepository solicitudes;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private PlantillaEvaluacionRepository plantillas;
    @Mock private VersionPlantillaPruebaRepository versionesPrueba;
    @Mock private PlantillaPruebaRepository plantillasPrueba;
    @Mock private PlantillaCorreoRepository plantillasCorreo;
    @Mock private PlantillaCorreoVacanteRepository plantillasPorVacante;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private EvaluacionRepository evaluaciones;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private ServicioAuditoria auditoria;
    @Mock private DuenoDelInstrumento dueno;
    @Mock private PostulacionRepository postulaciones;
    @Mock private PostulacionesEnCarrera enCarrera;
    @Mock private MaquinaEstados maquina;
    @Mock private ServicioAvisosPortal avisos;
    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private Permisos permisos;
    @Mock private com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil catalogos;

    private ServicioVacantesPanelImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioVacantesPanelImpl(vacantes, puestos, requisitos, solicitudes,
                versionesPesos, plantillas, versionesPrueba, plantillasPrueba, plantillasCorreo,
                plantillasPorVacante, intentos, evaluaciones, versionesBanco,
                auditoria, dueno, postulaciones, enCarrera, maquina, avisos, alcance, permisos, catalogos);
    }

    // ---------- el escenario ----------

    private Vacante vacante(String estado, Instant archivadaEn) {
        return Vacante.builder()
                .id(VACANTE)
                .organizacionId(ORGANIZACION)
                .solicitudTalentoId(30L)
                .puestoId(5L)
                .titulo("Coordinador de sede")
                .descripcion("Lleva la operación de la sede")
                .tipoCierre("PERMANENTE")
                .responsableUsuarioId(7L)
                .estado(estado)
                .archivadaEn(archivadaEn)
                .remuneracionTipo("OCULTA")
                .build();
    }

    /** La que el guardián de `cerrar_vacante` devuelve. */
    private Vacante laQueSeArchiva(String estado, Instant archivadaEn) {
        Vacante v = vacante(estado, archivadaEn);
        when(alcance.laVacanteVisible(QUIEN, VACANTE, "cerrar_vacante")).thenReturn(v);
        return v;
    }

    /** La que devuelve el guardián de `editar_vacante`, para las entradas de edición. */
    private Vacante laQueSeEdita(Instant archivadaEn) {
        Vacante v = vacante("CERRADA", archivadaEn);
        when(alcance.laVacanteVisible(QUIEN, VACANTE, "editar_vacante")).thenReturn(v);
        return v;
    }

    /** La que devuelve la búsqueda por organización, para el resto de entradas. */
    private Vacante laDeLaOrganizacion(String estado, Instant archivadaEn) {
        Vacante v = vacante(estado, archivadaEn);
        when(vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(VACANTE, ORGANIZACION)).thenReturn(Optional.of(v));
        return v;
    }

    private GuardarVacante elFormulario() {
        return new GuardarVacante(30L, 5L, "Otro título", "Otra descripción", null, null, null,
                null, null, null, null, RemuneracionDeLaVacante.OCULTA, "PERMANENTE", null, null,
                null, 7L, null);
    }

    // ================= archivar =================

    @Nested
    @DisplayName("Archivar")
    class Archivar {

        @Test
        @DisplayName("una cerrada sin nadie en carrera se archiva con su fecha, y queda auditada")
        void laCerradaSinNadieSeArchiva() {
            Vacante v = laQueSeArchiva("CERRADA", null);
            when(enCarrera.cuantasEnLaVacante(VACANTE)).thenReturn(0);
            when(vacantes.archivarSiNoLoEstaba(eq(VACANTE), any())).thenReturn(1);
            Instant antesDeLlamar = Instant.now();

            servicio.archivar(QUIEN, VACANTE);

            // La fecha se escribe con la condición dentro del UPDATE, no con un
            // `save()` sobre la entidad leída: ver `archivarSiNoLoEstaba`.
            ArgumentCaptor<Instant> cuando = ArgumentCaptor.forClass(Instant.class);
            verify(vacantes).archivarSiNoLoEstaba(eq(VACANTE), cuando.capture());
            assertThat(cuando.getValue()).isAfterOrEqualTo(antesDeLlamar);
            assertThat(v.getEstado())
                    .as("archivar no es un estado: sigue CERRADA")
                    .isEqualTo("CERRADA");
            verify(vacantes, never()).save(any());
            verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("archivar_vacante"),
                    eq("vacante"), eq(VACANTE), any(), any(), eq(null));
            // Archivar no es una noticia para nadie: en su proceso no ha cambiado nada.
            verify(avisos, never()).publicar(anyLong(), anyLong(), anyString(), anyString(),
                    anyString(), anyLong(), anyLong());
        }

        /**
         * La carrera del doble clic, vista desde el servicio.
         *
         * <p>Las dos peticiones pasan las cuatro comprobaciones —las dos leyeron «no
         * archivada»— y solo una gana el UPDATE condicional. La que pierde recibe cero filas
         * y tiene que rechazar <b>sin auditar</b>: una fila de más diciendo «de no archivada a
         * archivada» deja sin respuesta «¿cuándo se archivó esto?».
         */
        @Test
        @DisplayName("si otra petición se adelanta, la segunda se rechaza sin auditar")
        void laSegundaDelDobleClicNoAudita() {
            laQueSeArchiva("CERRADA", null);
            when(enCarrera.cuantasEnLaVacante(VACANTE)).thenReturn(0);
            // Cero filas: entre el `select` y el `update`, otra transacción archivó.
            when(vacantes.archivarSiNoLoEstaba(eq(VACANTE), any())).thenReturn(0);

            assertThatThrownBy(() -> servicio.archivar(QUIEN, VACANTE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ya está archivada");

            verify(auditoria, never()).registrar(anyLong(), any(), anyString(), anyString(),
                    anyLong(), any(), any(), any());
        }

        @Test
        @DisplayName("con gente en carrera se rechaza diciendo cuánta, y no se archiva nada")
        void conGenteEnCarreraNoSeArchiva() {
            Vacante v = laQueSeArchiva("CERRADA", null);
            when(enCarrera.cuantasEnLaVacante(VACANTE)).thenReturn(1);

            assertThatThrownBy(() -> servicio.archivar(QUIEN, VACANTE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Quedan 1 postulantes en carrera")
                    .hasMessageContaining("antes de archivarla");

            assertThat(v.getArchivadaEn()).isNull();
            verify(vacantes, never()).save(any());
            verify(auditoria, never()).registrar(anyLong(), any(), anyString(), anyString(),
                    anyLong(), any(), any(), any());
        }

        /**
         * El conteo se pregunta <b>al confirmar</b> y no se hereda del modal.
         *
         * <p>Es el caso que la spec llama «aunque el conteo cambiara después de abrirlo»: el
         * panel pintó cero personas y, cuando alguien pulsó, ya había una. Se comprueba
         * mirando que la llamada existe y decide, porque no hay otra forma de saber que el
         * servidor no se fía de lo que le contaron.
         */
        @Test
        @DisplayName("el conteo se vuelve a mirar al confirmar, no se hereda del modal")
        void elConteoSeRevalidaAlConfirmar() {
            laQueSeArchiva("CERRADA", null);
            when(enCarrera.cuantasEnLaVacante(VACANTE)).thenReturn(2);

            assertThatThrownBy(() -> servicio.archivar(QUIEN, VACANTE))
                    .hasMessageContaining("Quedan 2 postulantes");

            verify(enCarrera).cuantasEnLaVacante(VACANTE);
            verify(vacantes, never()).save(any());
        }

        @Test
        @DisplayName("una publicada no se archiva, y tampoco se cierra de rebote para poder hacerlo")
        void laPublicadaNoSeArchivaNiSeCierraSola() {
            Vacante v = laQueSeArchiva("PUBLICADA", null);

            assertThatThrownBy(() -> servicio.archivar(QUIEN, VACANTE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Solo se archiva una vacante cerrada")
                    .hasMessageContaining("publicada");

            assertThat(v.getEstado()).isEqualTo("PUBLICADA");
            assertThat(v.getArchivadaEn()).isNull();
            verify(vacantes, never()).save(any());
        }

        @Test
        @DisplayName("archivar una ya archivada se rechaza sin efectos duplicados")
        void archivarDosVecesNoDuplicaNada() {
            Instant yaArchivada = Instant.parse("2026-09-01T10:00:00Z");
            Vacante v = laQueSeArchiva("CERRADA", yaArchivada);

            assertThatThrownBy(() -> servicio.archivar(QUIEN, VACANTE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ya está archivada");

            assertThat(v.getArchivadaEn())
                    .as("la fecha del primer archivo no se pisa")
                    .isEqualTo(yaArchivada);
            verify(vacantes, never()).save(any());
        }

        @Test
        @DisplayName("fuera del alcance del rol es 404, no 403")
        void fueraDelAlcanceEs404() {
            when(alcance.laVacanteVisible(QUIEN, VACANTE, "cerrar_vacante"))
                    .thenThrow(new ResourceNotFoundException("Vacante", "id", VACANTE));

            assertThatThrownBy(() -> servicio.archivar(QUIEN, VACANTE))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(vacantes, never()).save(any());
        }
    }

    // ================= desarchivar =================

    @Nested
    @DisplayName("Desarchivar")
    class Desarchivar {

        @Test
        @DisplayName("vuelve a la lista habitual CERRADA, sin tocar ninguna postulación")
        void vuelveALaListaComoCerrada() {
            Vacante v = laQueSeArchiva("CERRADA", Instant.parse("2026-09-01T10:00:00Z"));
            when(vacantes.desarchivarSiLoEstaba(VACANTE)).thenReturn(1);

            servicio.desarchivar(QUIEN, VACANTE);

            assertThat(v.getEstado())
                    .as("desarchivar no reabre la convocatoria")
                    .isEqualTo("CERRADA");
            verify(vacantes).desarchivarSiLoEstaba(VACANTE);
            verify(vacantes, never()).save(any());
            verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("desarchivar_vacante"),
                    eq("vacante"), eq(VACANTE), any(), any(), eq(null));
            verify(avisos, never()).publicar(anyLong(), anyLong(), anyString(), anyString(),
                    anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("desarchivar una que no lo está se rechaza sin escribir nada")
        void desarchivarLaQueNoLoEsta() {
            laQueSeArchiva("CERRADA", null);

            assertThatThrownBy(() -> servicio.desarchivar(QUIEN, VACANTE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no está archivada");

            verify(vacantes, never()).save(any());
        }
    }

    // ================= una archivada se lee, no se toca =================

    @Nested
    @DisplayName("Lo que una archivada ya no admite")
    class EnLectura {

        private static final Instant ARCHIVADA = Instant.parse("2026-09-01T10:00:00Z");

        @Test
        @DisplayName("editar el formulario se rechaza sin guardar ni avisar")
        void noSeEdita() {
            Vacante v = laQueSeEdita(ARCHIVADA);

            assertThatThrownBy(() -> servicio.editar(QUIEN, VACANTE, elFormulario()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("archivada");

            assertThat(v.getTitulo())
                    .as("ni una coma del formulario se aplica")
                    .isEqualTo("Coordinador de sede");
            verify(vacantes, never()).save(any());
            verify(avisos, never()).publicar(anyLong(), anyLong(), anyString(), anyString(),
                    anyString(), anyLong(), anyLong());
        }

        /**
         * La edición empezada ANTES del archivo, que es el caso que de verdad ocurre: el
         * formulario se abrió con la vacante viva y, mientras se escribía, otro la archivó.
         * El panel no puede saberlo; el servidor sí, y lo rechaza entero.
         */
        @Test
        @DisplayName("el formulario abierto antes del archivo tampoco se guarda")
        void elFormularioAbiertoAntesTampoco() {
            laQueSeEdita(ARCHIVADA);

            assertThatThrownBy(() -> servicio.editar(QUIEN, VACANTE, elFormulario()))
                    .hasMessageContaining("Para volver a moverla, desarchívala");

            verify(vacantes, never()).save(any());
            verify(auditoria, never()).registrar(anyLong(), any(), anyString(), anyString(),
                    anyLong(), any(), any(), any());
        }

        @Test
        @DisplayName("cambiar el sueldo desde la tarjeta se rechaza igual que el formulario")
        void noCambiaDeSueldo() {
            laQueSeEdita(ARCHIVADA);

            assertThatThrownBy(() -> servicio.actualizarRemuneracion(QUIEN, VACANTE,
                    new ActualizarRemuneracion(RemuneracionDeLaVacante.OCULTA, "porque sí")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("archivada");

            verify(vacantes, never()).save(any());
        }

        @Test
        @DisplayName("publicar una archivada se rechaza sin cambiar su estado")
        void noSePublica() {
            Vacante v = laDeLaOrganizacion("BORRADOR", ARCHIVADA);

            assertThatThrownBy(() -> servicio.publicar(QUIEN, VACANTE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("archivada");

            assertThat(v.getEstado()).isEqualTo("BORRADOR");
            verify(vacantes, never()).save(any());
        }

        @Test
        @DisplayName("reconfigurarla —el interruptor del banco— se rechaza igual")
        void noSeReconfigura() {
            Vacante v = laDeLaOrganizacion("CERRADA", ARCHIVADA);

            assertThatThrownBy(() -> servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("archivada");

            assertThat(v.isAplicaEvaluacion()).isTrue();
            verify(vacantes, never()).save(any());
        }

        @Test
        @DisplayName("cerrarla otra vez se rechaza por archivada, no por su estado")
        void noSeCierra() {
            laDeLaOrganizacion("CERRADA", ARCHIVADA);

            assertThatThrownBy(() -> servicio.cerrar(QUIEN, VACANTE, "por si acaso"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("archivada");

            verify(vacantes, never()).save(any());
        }

        @Test
        @DisplayName("añadirle un requisito se rechaza")
        void noAdmiteRequisitos() {
            laDeLaOrganizacion("CERRADA", ARCHIVADA);

            assertThatThrownBy(() -> servicio.agregarRequisito(QUIEN, VACANTE,
                    new com.renaser.ai.ai_engine.vacante.dto.DtosVacante.GuardarRequisito(
                            "Título profesional", "TIENE_TITULO")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("archivada");

            verify(requisitos, never()).save(any());
        }

        @Test
        @DisplayName("su detalle sí se lee, con la fecha de archivo dentro")
        void elDetalleSeLee() {
            laDeLaOrganizacion("CERRADA", ARCHIVADA);
            when(enCarrera.cuantasEnLaVacante(VACANTE)).thenReturn(0);
            when(permisos.alcanceDe("cerrar_vacante"))
                    .thenReturn(FiltroAlcance.desde("TODO", QUIEN.usuarioId()));
            lenient().when(permisos.alcanceDe("editar_vacante"))
                    .thenReturn(FiltroAlcance.desde("TODO", QUIEN.usuarioId()));
            when(alcance.alcanzaALaVacante(eq(QUIEN), any(), any())).thenReturn(true);

            VacantePanel detalle = servicio.detalle(QUIEN, VACANTE);

            assertThat(detalle.archivadaEn()).isEqualTo(ARCHIVADA);
            assertThat(detalle.puedeEditar()).isFalse();
            assertThat(detalle.puedeArchivar()).isFalse();
            assertThat(detalle.puedeDesarchivar()).isTrue();
        }
    }

    // ================= las dos listas =================

    @Nested
    @DisplayName("La lista habitual y la de archivadas")
    class LasDosListas {

        @Test
        @DisplayName("por defecto se piden solo las no archivadas: no es un filtro de pantalla")
        void laListaHabitualNoLasPide() {
            Vacante viva = vacante("PUBLICADA", null);
            when(vacantes.findByOrganizacionIdAndArchivadaEnIsNullAndEliminadaEnIsNullOrderByCreadoEnDesc(ORGANIZACION))
                    .thenReturn(List.of(viva));
            when(enCarrera.cuantasPorVacante(ORGANIZACION)).thenReturn(Map.of());
            when(permisos.alcanceDe(anyString()))
                    .thenReturn(FiltroAlcance.desde("TODO", QUIEN.usuarioId()));
            when(alcance.alcanzaALaVacante(eq(QUIEN), any(), any())).thenReturn(true);

            List<VacantePanel> lista = servicio.listar(QUIEN, false);

            assertThat(lista).hasSize(1);
            assertThat(lista.get(0).archivadaEn()).isNull();
            verify(vacantes, never())
                    .findByOrganizacionIdAndArchivadaEnIsNotNullAndEliminadaEnIsNullOrderByArchivadaEnDesc(anyLong());
        }

        @Test
        @DisplayName("la vista de archivadas trae su fecha y el botón de desarchivar")
        void laVistaDeArchivadas() {
            Instant archivada = Instant.parse("2026-09-01T10:00:00Z");
            when(vacantes.findByOrganizacionIdAndArchivadaEnIsNotNullAndEliminadaEnIsNullOrderByArchivadaEnDesc(
                    ORGANIZACION)).thenReturn(List.of(vacante("CERRADA", archivada)));
            when(enCarrera.cuantasPorVacante(ORGANIZACION)).thenReturn(Map.of());
            when(permisos.alcanceDe(anyString()))
                    .thenReturn(FiltroAlcance.desde("TODO", QUIEN.usuarioId()));
            when(alcance.alcanzaALaVacante(eq(QUIEN), any(), any())).thenReturn(true);

            List<VacantePanel> lista = servicio.listar(QUIEN, true);

            assertThat(lista.get(0).archivadaEn()).isEqualTo(archivada);
            assertThat(lista.get(0).puedeDesarchivar()).isTrue();
            assertThat(lista.get(0).puedeArchivar()).isFalse();
            assertThat(lista.get(0).puedeEditar())
                    .as("una archivada no ofrece el lápiz")
                    .isFalse();
        }

        /**
         * El icono de archivar aparece aunque queden personas en carrera, y no es un
         * descuido: es su modal el que dice cuántas quedan y por dónde se decide cada una.
         * Escondiéndolo, quien mira la fila no tendría forma de saber por qué esa vacante no
         * se puede guardar.
         */
        @Test
        @DisplayName("el icono de archivar no depende del conteo: lo explica su modal")
        void elIconoNoDependeDelConteo() {
            when(vacantes.findByOrganizacionIdAndArchivadaEnIsNullAndEliminadaEnIsNullOrderByCreadoEnDesc(ORGANIZACION))
                    .thenReturn(List.of(vacante("CERRADA", null)));
            when(enCarrera.cuantasPorVacante(ORGANIZACION)).thenReturn(Map.of(VACANTE, 3));
            when(permisos.alcanceDe(anyString()))
                    .thenReturn(FiltroAlcance.desde("TODO", QUIEN.usuarioId()));
            when(alcance.alcanzaALaVacante(eq(QUIEN), any(), any())).thenReturn(true);

            VacantePanel fila = servicio.listar(QUIEN, false).get(0);

            assertThat(fila.puedeArchivar()).isTrue();
            assertThat(fila.postulantesEnCarrera()).isEqualTo(3);
        }

        @Test
        @DisplayName("sin «cerrar_vacante» ninguna fila ofrece archivar ni desarchivar")
        void sinPermisoNoHayArchivo() {
            when(vacantes.findByOrganizacionIdAndArchivadaEnIsNotNullAndEliminadaEnIsNullOrderByArchivadaEnDesc(
                    ORGANIZACION)).thenReturn(
                            List.of(vacante("CERRADA", Instant.parse("2026-09-01T10:00:00Z"))));
            when(enCarrera.cuantasPorVacante(ORGANIZACION)).thenReturn(Map.of());

            VacantePanel fila = servicio.listar(SOLO_MIRA, true).get(0);

            assertThat(fila.puedeArchivar()).isFalse();
            assertThat(fila.puedeDesarchivar()).isFalse();
            // Consultar Archivadas usa el permiso de lectura: no se le pregunta por un
            // alcance que no tiene, porque `alcanceDe` lanzaría.
            verify(permisos, never()).alcanceDe("cerrar_vacante");
        }

        @Test
        @DisplayName("el contador cuenta en el servidor, sobre el mismo universo que la lista")
        void elContador() {
            when(vacantes.countByOrganizacionIdAndArchivadaEnIsNotNullAndEliminadaEnIsNull(ORGANIZACION)).thenReturn(7L);

            assertThat(servicio.contarArchivadas(SOLO_MIRA).archivadas()).isEqualTo(7L);
        }
    }

    // ================= la auditoría dice qué pasó =================

    @Test
    @DisplayName("la auditoría del archivo guarda el antes y el después con la fecha")
    void laAuditoriaGuardaLaFecha() {
        laQueSeArchiva("CERRADA", null);
        when(enCarrera.cuantasEnLaVacante(VACANTE)).thenReturn(0);
        when(vacantes.archivarSiNoLoEstaba(eq(VACANTE), any())).thenReturn(1);

        servicio.archivar(QUIEN, VACANTE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> nuevo = ArgumentCaptor.forClass(Map.class);
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("archivar_vacante"),
                eq("vacante"), eq(VACANTE), any(), nuevo.capture(), eq(null));

        assertThat(nuevo.getValue()).containsEntry("archivada", true);
        assertThat(nuevo.getValue()).containsKey("archivadaEn");
    }
}
