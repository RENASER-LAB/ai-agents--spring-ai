package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoVacanteRepository;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.GuardarVacante;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.VacantePanel;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * De dónde salen las preguntas de una vacante (V66): el banco PROPIO de la empresa para su
 * nivel, las preguntas propias de la vacante, o ninguna. Toda vacante nueva nace con sus
 * preguntas propias; el de RENASER ya no se presta a las vacantes nuevas de otras empresas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("De dónde salen las preguntas de la vacante")
class OrigenDePreguntasTest {

    private static final Long ORGANIZACION = 2L;
    private static final Long VACANTE = 40L;
    private static final Long PUESTO = 5L;
    private static final String NIVEL = "EJECUCION";
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private RequisitoObjetivoRepository requisitos;
    @Mock private SolicitudTalentoRepository solicitudes;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private PlantillaEvaluacionRepository plantillas;
    @Mock private VersionPlantillaPruebaRepository versionesPrueba;
    @Mock private com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository plantillasPrueba;
    @Mock private PlantillaCorreoRepository plantillasCorreo;
    @Mock private PlantillaCorreoVacanteRepository plantillasPorVacante;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository evaluaciones;
    @Mock private ServicioAuditoria auditoria;
    @Mock private com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento dueno;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository postulaciones;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private com.renaser.ai.ai_engine.postulacion.service.PostulacionesEnCarrera enCarrera;
    @Mock private com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados maquina;
    @Mock private com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal avisos;
    @Mock private com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante alcance;
    @Mock private com.renaser.ai.ai_engine.seguridad.service.Permisos permisos;
    @Mock private com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil catalogos;

    private ServicioVacantesPanelImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioVacantesPanelImpl(vacantes, puestos, requisitos, solicitudes,
                versionesPesos, plantillas, versionesPrueba, plantillasPrueba, plantillasCorreo,
                plantillasPorVacante, intentos, evaluaciones, versionesBanco,
                auditoria, dueno, postulaciones, enCarrera, maquina, avisos, alcance, permisos, catalogos,
                new com.renaser.ai.ai_engine.prueba.service.FechaLimiteDeLaVacante(vacantes, intentos,
                        versionesPrueba, versionesBanco));
        lenient().when(puestos.findByIdAndOrganizacionId(PUESTO, ORGANIZACION))
                .thenReturn(Optional.of(Puesto.builder().id(PUESTO).organizacionId(ORGANIZACION)
                        .nivelPuestoCodigo(NIVEL).familiaCodigo("OPERACIONES").esActivo(true).build()));
    }

    private void conBancoPropio(boolean tiene) {
        lenient().when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(tiene ? Optional.of(VersionBanco.builder().id(15L).build()) : Optional.empty());
    }

    private Vacante vacante(String estado, boolean aplica, String origen) {
        Vacante v = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION).estado(estado)
                .aplicaEvaluacion(aplica).origenPreguntas(origen).puestoId(PUESTO)
                .versionPlantillaPruebaId(31L).build();
        when(vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(v));
        return v;
    }

    private Vacante crear() {
        when(solicitudes.findByIdAndOrganizacionId(30L, ORGANIZACION)).thenReturn(Optional.of(
                SolicitudTalento.builder().id(30L).organizacionId(ORGANIZACION).puestoId(PUESTO)
                        .estado("ABIERTA").build()));
        when(dueno.duenoDe(any(), any())).thenReturn(1L);
        when(versionesPesos.findFirstByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(1L, "PUBLICADA"))
                .thenReturn(Optional.of(VersionPesos.builder().id(9L).build()));
        when(vacantes.save(any())).thenAnswer(i -> i.getArgument(0));
        servicio.crear(QUIEN, new GuardarVacante(30L, null, "Analista", "Descripción", null, null,
                null, null, null, null, null, null, "MANUAL", 1, null, null, QUIEN.usuarioId(), null));
        ArgumentCaptor<Vacante> guardada = ArgumentCaptor.forClass(Vacante.class);
        verify(vacantes).save(guardada.capture());
        return guardada.getValue();
    }

    @Test
    @DisplayName("Sin banco propio, la vacante nueva nace con preguntas propias (AC-01)")
    void sinBancoPropioNaceConPreguntasPropias() {
        conBancoPropio(false);
        assertThat(crear().getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_VACANTE);
    }

    @Test
    @DisplayName("Con banco propio publicado para el nivel, también nace con preguntas propias (AC-01b)")
    void conBancoPropioTambienNaceConPreguntasPropias() {
        conBancoPropio(true);
        Vacante nueva = crear();
        assertThat(nueva.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_VACANTE);
        assertThat(nueva.isAplicaEvaluacion()).isTrue();
    }

    @Test
    @DisplayName("Con banco propio, el banco del nivel se sigue pudiendo elegir después (AC-01b)")
    void conBancoPropioSePuedeElegirElDelNivel() {
        conBancoPropio(true);
        Vacante v = vacante("BORRADOR", true, Vacante.ORIGEN_VACANTE);

        servicio.elegirOrigenDePreguntas(QUIEN, VACANTE, "NIVEL");

        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_NIVEL);
        assertThat(v.isAplicaEvaluacion()).isTrue();
        verify(vacantes).save(v);
    }

    @Test
    @DisplayName("Elegir el banco del nivel sin tener uno propio se rechaza (AC-01)")
    void elegirElNivelSinBancoPropioSeRechaza() {
        conBancoPropio(false);
        vacante("BORRADOR", true, Vacante.ORIGEN_VACANTE);

        assertThatThrownBy(() -> servicio.elegirOrigenDePreguntas(QUIEN, VACANTE, "NIVEL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene un banco propio");
        verify(vacantes, never()).save(any());
    }

    @Test
    @DisplayName("El banco prestado, al cambiarlo, desaparece y no se puede volver a elegir (AC-01c)")
    void elPrestadoDesapareceAlCambiarlo() {
        conBancoPropio(false);
        Vacante v = vacante("BORRADOR", true, Vacante.ORIGEN_NIVEL);

        servicio.elegirOrigenDePreguntas(QUIEN, VACANTE, "SIN_EVALUACION");
        assertThat(v.isAplicaEvaluacion()).isFalse();
        // No se queda guardado como NIVEL: si no, reaparecería como opción
        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_VACANTE);

        assertThatThrownBy(() -> servicio.elegirOrigenDePreguntas(QUIEN, VACANTE, "NIVEL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Con postulantes, de dónde salen las preguntas no se cambia: 409 (AC-16)")
    void conPostulantesNoSeCambia() {
        conBancoPropio(true);
        vacante("PUBLICADA", true, Vacante.ORIGEN_NIVEL);
        when(postulaciones.countByVacanteId(VACANTE)).thenReturn(3L);

        assertThatThrownBy(() -> servicio.elegirOrigenDePreguntas(QUIEN, VACANTE, "VACANTE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("postulantes");
        verify(vacantes, never()).save(any());
    }

    @Test
    @DisplayName("Con preguntas propias sin publicar, la vacante no se publica (AC-17)")
    void sinPreguntasPublicadasNoSePublica() {
        Vacante v = vacante("BORRADOR", true, Vacante.ORIGEN_VACANTE);
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.publicar(QUIEN, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("preguntas propias");
        assertThat(v.getEstado()).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Con sus preguntas propias publicadas, se publica sin mirar el banco del nivel")
    void conPreguntasPublicadasSePublica() {
        Vacante v = vacante("BORRADOR", true, Vacante.ORIGEN_VACANTE);
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA"))
                .thenReturn(Optional.of(VersionBanco.builder().id(70L).build()));

        servicio.publicar(QUIEN, VACANTE);

        assertThat(v.getEstado()).isEqualTo("PUBLICADA");
        verify(versionesBanco, never()).laPublicadaDelNivel(any(), any(), any());
    }

    // ------------------------------------------------------------------ La lista

    private static Vacante deLaLista(Long id, boolean aplica, String origen, Long puestoId) {
        return Vacante.builder().id(id).organizacionId(ORGANIZACION).estado("BORRADOR")
                .aplicaEvaluacion(aplica).origenPreguntas(origen).puestoId(puestoId).build();
    }

    private static VersionBanco propias(Long vacanteId, String estado) {
        return VersionBanco.builder().vacanteId(vacanteId).estado(estado)
                .proposito("PERFIL_INTEGRAL").organizacionId(ORGANIZACION).build();
    }

    private void enLaLista(Vacante... filas) {
        when(vacantes.findByOrganizacionIdAndArchivadaEnIsNullAndEliminadaEnIsNullOrderByCreadoEnDesc(
                ORGANIZACION)).thenReturn(List.of(filas));
    }

    @Test
    @DisplayName("La lista dice en qué punto están las preguntas propias, sin consultar fila a fila")
    void laListaDiceElEstadoDeLasPropias() {
        enLaLista(deLaLista(1L, true, Vacante.ORIGEN_VACANTE, PUESTO),
                deLaLista(2L, true, Vacante.ORIGEN_VACANTE, PUESTO),
                deLaLista(3L, true, Vacante.ORIGEN_VACANTE, PUESTO),
                deLaLista(4L, true, Vacante.ORIGEN_NIVEL, PUESTO),
                deLaLista(5L, false, Vacante.ORIGEN_VACANTE, PUESTO));
        // La 1 tiene publicada y un borrador abierto (llega primero): manda la publicada
        when(versionesBanco.propiasEnCursoDe(ORGANIZACION)).thenReturn(List.of(
                propias(1L, "BORRADOR"), propias(1L, "PUBLICADA"), propias(2L, "BORRADOR"),
                propias(5L, "PUBLICADA")));

        var filas = servicio.listar(QUIEN, false);

        assertThat(filas).extracting(VacantePanel::estadoPreguntasPropias)
                .containsExactly("PUBLICADAS", "BORRADOR", "SIN_PREGUNTAS", null, "PUBLICADAS");
        verify(versionesBanco, never()).preguntasPropiasDe(any(), any());
    }

    @Test
    @DisplayName("La lista dice si el banco del nivel es el de la empresa o el prestado de RENASER")
    void laListaDiceSiElBancoEsPrestado() {
        Long sinBanco = 6L;
        when(puestos.findByOrganizacionIdOrderByNombre(ORGANIZACION)).thenReturn(List.of(
                Puesto.builder().id(PUESTO).organizacionId(ORGANIZACION).nivelPuestoCodigo(NIVEL).build(),
                Puesto.builder().id(sinBanco).organizacionId(ORGANIZACION)
                        .nivelPuestoCodigo("SUPERVISION").build()));
        when(versionesBanco.nivelesConBancoPublicado(ORGANIZACION)).thenReturn(List.of(NIVEL));
        enLaLista(deLaLista(1L, true, Vacante.ORIGEN_NIVEL, PUESTO),
                deLaLista(2L, true, Vacante.ORIGEN_NIVEL, sinBanco),
                deLaLista(3L, false, Vacante.ORIGEN_NIVEL, sinBanco),
                deLaLista(4L, true, Vacante.ORIGEN_NIVEL, 99L));

        var filas = servicio.listar(QUIEN, false);

        assertThat(filas).extracting(VacantePanel::bancoDelNivelPropio)
                .containsExactly(true, false, false, false);
        // La apagada no rinde ningún banco; un puesto que no es de la empresa no le da uno propio
        assertThat(filas).extracting(VacantePanel::bancoPrestado)
                .containsExactly(false, true, false, true);
        verify(versionesBanco, never()).laPublicadaDelNivel(any(), any(), any());
        verify(puestos, never()).findByIdAndOrganizacionId(any(), any());
    }

    @Test
    @DisplayName("El detalle no trae el estado de las propias: lo lee del editor, con sus puntos")
    void elDetalleNoTraeElEstadoDeLasPropias() {
        conBancoPropio(true);
        vacante("BORRADOR", true, Vacante.ORIGEN_VACANTE);

        var detalle = servicio.detalle(QUIEN, VACANTE);

        assertThat(detalle.estadoPreguntasPropias()).isNull();
        assertThat(detalle.bancoDelNivelPropio()).isTrue();
        verify(versionesBanco, never()).propiasEnCursoDe(any());
    }
}
