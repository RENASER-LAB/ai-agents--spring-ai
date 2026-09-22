package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoVacanteRepository;
import com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.postulacion.service.PostulacionesEnCarrera;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.EliminarVacante;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.VacanteEliminadaResponse;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Eliminar una vacante: qué se lleva por delante y qué no puede llevarse nunca.
 *
 * <p>Es la acción más destructiva del panel y la única que no se deshace desde ahí, así que
 * lo que se protege aquí son las cuatro promesas del modal:
 *
 * <ul>
 *   <li><b>Sin motivo no pasa nada.</b> Ni marca, ni cierres, ni avisos, ni auditoría. El
 *       botón apagado del panel no cuenta: el panel es un cliente más del API.
 *   <li><b>Se cierra a quien seguía dentro, y solo a esa gente.</b> Quien ya terminó —
 *       contratado, descartado, retirado— no se toca ni recibe aviso: su proceso acabó por
 *       otra razón y reescribirlo sería falsear su historia.
 *   <li><b>La marca va antes que los cierres.</b> Es lo que ordena dos peticiones
 *       simultáneas: la segunda descubre que llegó tarde ANTES de cerrar nada.
 *   <li><b>Lo que de verdad llegó no se redondea.</b> Cerradas y avisadas se cuentan por
 *       separado; un aviso que falla no deshace la eliminación, pero tampoco se cuenta.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Eliminar una vacante")
class EliminarVacanteTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long VACANTE = 40L;
    private static final Long SOLICITUD = 30L;
    private static final String TITULO = "Coordinador de sede";
    private static final String MOTIVO = "Se creó con el puesto equivocado";

    /** Quien puede eliminar, con alcance sobre todo. */
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            7L, 3L, ORGANIZACION, "EQUIPO", List.of(1L),
            Map.of("ver_vacantes", "TODO", "eliminar_vacante", "TODO"));

    /** Quien mira y no puede eliminar nada. */
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

    private ServicioVacantesPanelImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioVacantesPanelImpl(vacantes, puestos, requisitos, solicitudes,
                versionesPesos, plantillas, versionesPrueba, plantillasPrueba, plantillasCorreo,
                plantillasPorVacante, intentos, evaluaciones, versionesBanco,
                auditoria, dueno, postulaciones, enCarrera, maquina, avisos, alcance, permisos);
    }

    // ---------- el escenario ----------

    private Vacante vacante(String estado, Instant archivadaEn) {
        return Vacante.builder()
                .id(VACANTE)
                .organizacionId(ORGANIZACION)
                .solicitudTalentoId(SOLICITUD)
                .puestoId(5L)
                .titulo(TITULO)
                .descripcion("Lleva la operación de la sede")
                .tipoCierre("PERMANENTE")
                .responsableUsuarioId(7L)
                .estado(estado)
                .archivadaEn(archivadaEn)
                .remuneracionTipo("OCULTA")
                .build();
    }

    /** La que devuelve el guardián del permiso de eliminar. */
    private Vacante laQueSeElimina(String estado) {
        Vacante v = vacante(estado, null);
        when(alcance.laVacanteVisible(QUIEN, VACANTE, "eliminar_vacante")).thenReturn(v);
        return v;
    }

    private Postulacion enCarrera(long id, long usuarioId) {
        return Postulacion.builder()
                .id(id).usuarioId(usuarioId).vacanteId(VACANTE)
                .organizacionId(ORGANIZACION).estadoCodigo("PERFIL_POR_CONFIRMAR")
                .build();
    }

    private SolicitudTalento solicitud(String estado) {
        SolicitudTalento s = SolicitudTalento.builder()
                .id(SOLICITUD).organizacionId(ORGANIZACION).estado(estado).build();
        when(solicitudes.findByIdAndOrganizacionId(SOLICITUD, ORGANIZACION))
                .thenReturn(Optional.of(s));
        return s;
    }

    /** Todos los avisos salen bien: devuelve algo distinto de nulo. */
    private void losAvisosSalen() {
        when(avisos.publicar(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                isNull(), isNull())).thenReturn(AvisoPortal.builder().id(1L).build());
    }

    // ================= el camino normal =================

    @Nested
    @DisplayName("Al confirmar con motivo")
    class AlConfirmar {

        @Test
        @DisplayName("cierra a quien seguía en carrera, con el motivo «vacante eliminada» y "
                + "sin correo")
        void cierraALosDeDentro() {
            laQueSeElimina("PUBLICADA");
            solicitud("CON_VACANTE");
            when(vacantes.eliminarSiSeguiaViva(eq(VACANTE), any())).thenReturn(1);
            when(enCarrera.deLaVacante(VACANTE))
                    .thenReturn(List.of(enCarrera(100L, 50L), enCarrera(101L, 51L)));
            losAvisosSalen();

            VacanteEliminadaResponse respuesta =
                    servicio.eliminar(QUIEN, VACANTE, new EliminarVacante(MOTIVO));

            assertThat(respuesta.postulacionesCerradas()).isEqualTo(2);
            assertThat(respuesta.postulantesAvisados()).isEqualTo(2);

            ArgumentCaptor<Postulacion> cerradas = ArgumentCaptor.forClass(Postulacion.class);
            verify(maquina, org.mockito.Mockito.times(2)).transicionar(cerradas.capture(),
                    eq("CERRADA"), eq(QUIEN), anyString(), eq(false), eq(false),
                    eq("VACANTE_ELIMINADA"),
                    // POR_LA_CAMPANA y no el booleano: el correo se calla porque la noticia
                    // sale por el aviso, no porque se haya decidido no contárselo. Con el
                    // booleano viejo el historial se habría quedado con la coletilla
                    // «sin avisar al candidato», que aquí sería falsa.
                    eq(MaquinaEstados.AvisoDeLaTransicion.POR_LA_CAMPANA));
            assertThat(cerradas.getAllValues()).extracting(Postulacion::getId)
                    .containsExactly(100L, 101L);
            // El motivo escrito viaja al historial de cada ficha, y no se inventa otro.
            verify(maquina, never()).transicionar(any(), anyString(), any(), anyString(),
                    anyBoolean(), anyBoolean(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("deja en cada campana un aviso VACANTE_ELIMINADA que no enlaza a nada")
        void avisaSinEnlace() {
            laQueSeElimina("PUBLICADA");
            solicitud("CON_VACANTE");
            when(vacantes.eliminarSiSeguiaViva(eq(VACANTE), any())).thenReturn(1);
            when(enCarrera.deLaVacante(VACANTE)).thenReturn(List.of(enCarrera(100L, 50L)));
            losAvisosSalen();

            servicio.eliminar(QUIEN, VACANTE, new EliminarVacante(MOTIVO));

            // Los dos enlaces vacíos: el proceso al que llevaría ya no se puede abrir, y un
            // aviso que termina en un 404 hace creer que se rompió algo suyo.
            verify(avisos).publicar(eq(ORGANIZACION), eq(50L), eq("VACANTE_ELIMINADA"),
                    eq("Se retiró la vacante «" + TITULO + "»"),
                    eq("La empresa retiró esta vacante y tu postulación quedó cerrada. "
                            + "No tienes que hacer nada"),
                    isNull(), isNull());
        }

        @Test
        @DisplayName("devuelve la solicitud a ABIERTA, para que respalde la vacante correcta")
        void liberaLaSolicitud() {
            laQueSeElimina("BORRADOR");
            SolicitudTalento s = solicitud("CON_VACANTE");
            when(vacantes.eliminarSiSeguiaViva(eq(VACANTE), any())).thenReturn(1);
            when(enCarrera.deLaVacante(VACANTE)).thenReturn(List.of());

            servicio.eliminar(QUIEN, VACANTE, new EliminarVacante(MOTIVO));

            assertThat(s.getEstado()).isEqualTo("ABIERTA");
            verify(solicitudes).save(s);
        }

        @Test
        @DisplayName("una solicitud que ya no estaba CON_VACANTE no se reabre")
        void noReabreLaQueYaSeCerro() {
            laQueSeElimina("CERRADA");
            SolicitudTalento s = solicitud("ANULADA");
            when(vacantes.eliminarSiSeguiaViva(eq(VACANTE), any())).thenReturn(1);
            when(enCarrera.deLaVacante(VACANTE)).thenReturn(List.of());

            servicio.eliminar(QUIEN, VACANTE, new EliminarVacante(MOTIVO));

            assertThat(s.getEstado())
                    .as("reabrirla sería deshacer una decisión que nadie pidió deshacer")
                    .isEqualTo("ANULADA");
            verify(solicitudes, never()).save(any());
        }

        @Test
        @DisplayName("sin nadie en carrera no se cierra ni se avisa a nadie, y se elimina igual")
        void sinNadieDentro() {
            laQueSeElimina("BORRADOR");
            solicitud("CON_VACANTE");
            when(vacantes.eliminarSiSeguiaViva(eq(VACANTE), any())).thenReturn(1);
            when(enCarrera.deLaVacante(VACANTE)).thenReturn(List.of());

            VacanteEliminadaResponse respuesta =
                    servicio.eliminar(QUIEN, VACANTE, new EliminarVacante(MOTIVO));

            assertThat(respuesta.postulacionesCerradas()).isZero();
            assertThat(respuesta.postulantesAvisados()).isZero();
            verify(avisos, never()).publicar(anyLong(), anyLong(), anyString(), anyString(),
                    anyString(), any(), any());
        }

        @Test
        @DisplayName("la auditoría guarda la fecha, cuántas se cerraron y el motivo escrito")
        void laAuditoria() {
            laQueSeElimina("PUBLICADA");
            solicitud("CON_VACANTE");
            when(vacantes.eliminarSiSeguiaViva(eq(VACANTE), any())).thenReturn(1);
            when(enCarrera.deLaVacante(VACANTE)).thenReturn(List.of(enCarrera(100L, 50L)));
            losAvisosSalen();

            servicio.eliminar(QUIEN, VACANTE, new EliminarVacante("  " + MOTIVO + "  "));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> nuevo = ArgumentCaptor.forClass(Map.class);
            verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("eliminar_vacante"),
                    eq("vacante"), eq(VACANTE), any(), nuevo.capture(), eq(MOTIVO));
            assertThat(nuevo.getValue()).containsEntry("eliminada", true);
            assertThat(nuevo.getValue()).containsEntry("postulacionesCerradas", "1");
            assertThat(nuevo.getValue()).containsKey("eliminadaEn");
        }

        /**
         * El orden que ordena a dos peticiones simultáneas.
         *
         * <p>Con la marca al final, las dos habrían cerrado ya las mismas postulaciones y
         * mandado dos campanas por el mismo hecho antes de que ninguna descubriera que
         * llegaba tarde. Con la marca delante, la segunda se encuentra el cero y se va.
         */
        @Test
        @DisplayName("marca la vacante ANTES de cerrar a nadie: es el punto donde dos "
                + "peticiones se ordenan")
        void marcaAntesDeCerrar() {
            laQueSeElimina("PUBLICADA");
            solicitud("CON_VACANTE");
            when(vacantes.eliminarSiSeguiaViva(eq(VACANTE), any())).thenReturn(1);
            when(enCarrera.deLaVacante(VACANTE)).thenReturn(List.of(enCarrera(100L, 50L)));
            losAvisosSalen();

            servicio.eliminar(QUIEN, VACANTE, new EliminarVacante(MOTIVO));

            InOrder orden = inOrder(vacantes, enCarrera, maquina);
            orden.verify(vacantes).eliminarSiSeguiaViva(eq(VACANTE), any());
            orden.verify(enCarrera).deLaVacante(VACANTE);
            orden.verify(maquina).transicionar(any(), eq("CERRADA"), any(), anyString(),
                    anyBoolean(), anyBoolean(), anyString(),
                    any(MaquinaEstados.AvisoDeLaTransicion.class));
        }
    }

    // ================= lo que no se puede =================

    @Nested
    @DisplayName("Lo que se rechaza")
    class LoQueSeRechaza {

        @Test
        @DisplayName("sin motivo no se elimina, no se cierra, no se avisa y no se audita")
        void sinMotivoNoPasaNada() {
            laQueSeElimina("PUBLICADA");

            assertThatThrownBy(() -> servicio.eliminar(QUIEN, VACANTE, new EliminarVacante("")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Di por qué se elimina");

            noPasoNada();
        }

        @Test
        @DisplayName("un motivo que son solo espacios tampoco vale: pasa el @NotBlank y no "
                + "contesta nada")
        void soloEspaciosTampoco() {
            laQueSeElimina("PUBLICADA");

            assertThatThrownBy(() ->
                    servicio.eliminar(QUIEN, VACANTE, new EliminarVacante("   ")))
                    .isInstanceOf(IllegalArgumentException.class);

            noPasoNada();
        }

        @Test
        @DisplayName("repetir la eliminación contesta 404 sin volver a cerrar ni avisar")
        void repetirlaEsUn404() {
            laQueSeElimina("PUBLICADA");
            // El UPDATE condicional no encuentra la fila viva: alguien se adelantó.
            when(vacantes.eliminarSiSeguiaViva(eq(VACANTE), any())).thenReturn(0);

            assertThatThrownBy(() ->
                    servicio.eliminar(QUIEN, VACANTE, new EliminarVacante(MOTIVO)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(maquina, never()).transicionar(any(), anyString(), any(), anyString(),
                    anyBoolean(), anyBoolean(), anyString(),
                    any(MaquinaEstados.AvisoDeLaTransicion.class));
            verify(avisos, never()).publicar(anyLong(), anyLong(), anyString(), anyString(),
                    anyString(), any(), any());
            verify(auditoria, never()).registrar(anyLong(), any(), eq("eliminar_vacante"),
                    anyString(), anyLong(), any(), any(), any());
            verify(solicitudes, never()).save(any());
        }

        @Test
        @DisplayName("fuera del alcance del rol, la vacante no existe: 404 y ningún efecto")
        void fueraDelAlcanceEs404() {
            when(alcance.laVacanteVisible(QUIEN, VACANTE, "eliminar_vacante"))
                    .thenThrow(new ResourceNotFoundException("Vacante", "id", VACANTE));

            assertThatThrownBy(() ->
                    servicio.eliminar(QUIEN, VACANTE, new EliminarVacante(MOTIVO)))
                    .isInstanceOf(ResourceNotFoundException.class);

            noPasoNada();
        }

        /** Ni marca, ni cierres, ni avisos, ni auditoría, ni solicitud liberada. */
        private void noPasoNada() {
            verify(vacantes, never()).eliminarSiSeguiaViva(anyLong(), any());
            verify(maquina, never()).transicionar(any(), anyString(), any(), anyString(),
                    anyBoolean(), anyBoolean(), anyString(),
                    any(MaquinaEstados.AvisoDeLaTransicion.class));
            verify(avisos, never()).publicar(anyLong(), anyLong(), anyString(), anyString(),
                    anyString(), any(), any());
            verify(auditoria, never()).registrar(anyLong(), any(), anyString(), anyString(),
                    anyLong(), any(), any(), any());
            verify(solicitudes, never()).save(any());
        }
    }

    // ================= el fallo parcial de los avisos =================

    /**
     * Un aviso que no sale no deshace la eliminación, y tampoco se cuenta.
     *
     * <p>Las dos mitades importan: revertir una eliminación porque no se pudo escribir una
     * noticia sería peor que quedarse sin la noticia, y decirle al panel que a los tres se
     * les avisó cuando solo llegaron dos es mentir sobre lo único que el candidato puede
     * comprobar.
     */
    @Test
    @DisplayName("si un aviso falla, la eliminación se queda hecha y el conteo lo dice")
    void elFalloParcialDeLosAvisos() {
        laQueSeElimina("PUBLICADA");
        solicitud("CON_VACANTE");
        when(vacantes.eliminarSiSeguiaViva(eq(VACANTE), any())).thenReturn(1);
        when(enCarrera.deLaVacante(VACANTE)).thenReturn(
                List.of(enCarrera(100L, 50L), enCarrera(101L, 51L), enCarrera(102L, 52L)));
        // El segundo no se pudo publicar —`publicar` contesta nulo— y el tercero revienta.
        when(avisos.publicar(eq(ORGANIZACION), eq(50L), anyString(), anyString(), anyString(),
                isNull(), isNull())).thenReturn(AvisoPortal.builder().id(1L).build());
        when(avisos.publicar(eq(ORGANIZACION), eq(51L), anyString(), anyString(), anyString(),
                isNull(), isNull())).thenReturn(null);
        when(avisos.publicar(eq(ORGANIZACION), eq(52L), anyString(), anyString(), anyString(),
                isNull(), isNull())).thenThrow(new IllegalStateException("la base dijo que no"));

        VacanteEliminadaResponse respuesta =
                servicio.eliminar(QUIEN, VACANTE, new EliminarVacante(MOTIVO));

        assertThat(respuesta.postulacionesCerradas())
                .as("las tres se cerraron: eso no depende de los avisos")
                .isEqualTo(3);
        assertThat(respuesta.postulantesAvisados())
                .as("y solo a una le llegó de verdad")
                .isEqualTo(1);
        // La eliminación quedó hecha y auditada pese al fallo.
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("eliminar_vacante"),
                anyString(), anyLong(), any(), any(), anyString());
    }

    // ================= la papelera de cada fila =================

    @Nested
    @DisplayName("El booleano de la fila")
    class LaPapeleraDeLaFila {

        private void laLista(Vacante... filas) {
            when(vacantes
                    .findByOrganizacionIdAndArchivadaEnIsNullAndEliminadaEnIsNullOrderByCreadoEnDesc(
                            ORGANIZACION))
                    .thenReturn(List.of(filas));
            when(enCarrera.cuantasPorVacante(ORGANIZACION)).thenReturn(Map.of());
        }

        /**
         * La papelera aparece en los cuatro estados, y es lo que la distingue del archivo: el
         * icono de archivar solo sale en una cerrada porque archivar una viva no significa
         * nada, pero una vacante mal creada se retira esté donde esté —y la más común es
         * justo el borrador que nadie llegó a ver.
         */
        @Test
        @DisplayName("con el permiso, la papelera sale en borrador, publicada y cerrada")
        void saleEnTodosLosEstados() {
            laLista(vacante("BORRADOR", null), vacante("PUBLICADA", null),
                    vacante("CERRADA", null));
            when(permisos.alcanceDe(anyString()))
                    .thenReturn(FiltroAlcance.desde("TODO", QUIEN.usuarioId()));
            when(alcance.alcanzaALaVacante(eq(QUIEN), any(), any())).thenReturn(true);

            List<VacantePanel> lista = servicio.listar(QUIEN, false);

            assertThat(lista).extracting(VacantePanel::puedeEliminar)
                    .containsExactly(true, true, true);
        }

        @Test
        @DisplayName("una archivada también se puede eliminar desde su pantalla")
        void tambienLaArchivada() {
            Instant archivada = Instant.parse("2026-09-01T10:00:00Z");
            when(vacantes
                    .findByOrganizacionIdAndArchivadaEnIsNotNullAndEliminadaEnIsNullOrderByArchivadaEnDesc(
                            ORGANIZACION))
                    .thenReturn(List.of(vacante("CERRADA", archivada)));
            when(enCarrera.cuantasPorVacante(ORGANIZACION)).thenReturn(Map.of());
            when(permisos.alcanceDe(anyString()))
                    .thenReturn(FiltroAlcance.desde("TODO", QUIEN.usuarioId()));
            when(alcance.alcanzaALaVacante(eq(QUIEN), any(), any())).thenReturn(true);

            VacantePanel fila = servicio.listar(QUIEN, true).get(0);

            assertThat(fila.puedeEliminar()).isTrue();
        }

        @Test
        @DisplayName("sin «eliminar_vacante» ninguna fila la ofrece, y no se pregunta su alcance")
        void sinPermisoNoHayPapelera() {
            laLista(vacante("PUBLICADA", null));

            VacantePanel fila = servicio.listar(SOLO_MIRA, false).get(0);

            assertThat(fila.puedeEliminar()).isFalse();
            verify(permisos, never()).alcanceDe("eliminar_vacante");
        }
    }
}
