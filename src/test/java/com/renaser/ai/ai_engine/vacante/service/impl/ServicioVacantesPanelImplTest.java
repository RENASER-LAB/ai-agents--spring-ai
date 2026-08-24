package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreo;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreoVacante;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoVacanteRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.AsignarPlantillaCorreo;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.DefinirCierrePrueba;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que cada vacante configura para sí sola: si aplica el banco (V30), qué textos de correo
 * manda (V31) y cuándo cierra su prueba (V32).
 *
 * <p>Lo que se protege aquí es el orden de los avisos: cada regla tiene que fallar en la
 * cara de quien configura, nunca en la del candidato.
 *
 * <ul>
 *   <li><b>Publicar sin plantilla de evaluación</b> solo puede pasar si la evaluación está
 *       apagada. Si está encendida y falta la plantilla, el primer candidato chocaría al
 *       postular; el error tiene que salir al publicar.
 *   <li><b>Volver a encender la evaluación</b> en una vacante publicada exige que la
 *       plantilla ya esté elegida, por el mismo motivo.
 *   <li><b>Los pesos de una vacante</b> solo se cambian a una versión publicada (RF-114):
 *       una en borrador todavía puede no sumar 100.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Lo que cada vacante configura para sí sola")
class ServicioVacantesPanelImplTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long VACANTE = 40L;
    private static final Long VERSION_PESOS = 9L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private RequisitoObjetivoRepository requisitos;
    @Mock private SolicitudTalentoRepository solicitudes;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private PlantillaEvaluacionRepository plantillas;
    @Mock private VersionPlantillaPruebaRepository versionesPrueba;
    @Mock private PlantillaCorreoRepository plantillasCorreo;
    @Mock private PlantillaCorreoVacanteRepository plantillasPorVacante;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private ServicioAuditoria auditoria;

    private ServicioVacantesPanelImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioVacantesPanelImpl(vacantes, puestos, requisitos, solicitudes,
                versionesPesos, plantillas, versionesPrueba, plantillasCorreo,
                plantillasPorVacante, intentos, auditoria);
    }

    private Vacante vacante(String estado, boolean aplicaEvaluacion, Long plantillaEvaluacionId) {
        Vacante v = Vacante.builder()
                .id(VACANTE)
                .organizacionId(ORGANIZACION)
                .estado(estado)
                .aplicaEvaluacion(aplicaEvaluacion)
                .plantillaEvaluacionId(plantillaEvaluacionId)
                .versionPlantillaPruebaId(31L)
                .build();
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(v));
        return v;
    }

    // ============ Publicar ============

    @Test
    @DisplayName("con la evaluación apagada se publica sin plantilla de evaluación")
    void sinEvaluacionSePublicaSinPlantilla() {
        Vacante v = vacante("BORRADOR", false, null);

        servicio.publicar(QUIEN, VACANTE);

        assertThat(v.getEstado()).isEqualTo("PUBLICADA");
        verify(vacantes).save(v);
    }

    @Test
    @DisplayName("con la evaluación encendida, publicar sin plantilla sigue frenado")
    void conEvaluacionLaPlantillaSigueSiendoObligatoria() {
        vacante("BORRADOR", true, null);

        assertThatThrownBy(() -> servicio.publicar(QUIEN, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plantilla de evaluación");
    }

    // ============ El interruptor ============

    @Test
    @DisplayName("apagar la evaluación queda guardado y auditado")
    void apagarLaEvaluacionSeGuarda() {
        Vacante v = vacante("PUBLICADA", true, 7L);

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, false);

        assertThat(v.isAplicaEvaluacion()).isFalse();
        verify(vacantes).save(v);
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "definir_aplicacion_evaluacion",
                "vacante", VACANTE, Map.of("aplicaEvaluacion", true),
                Map.of("aplicaEvaluacion", false), null);
    }

    @Test
    @DisplayName("volver a encenderla en una vacante publicada exige plantilla elegida")
    void encenderlaPublicadaSinPlantillaAvisaAqui() {
        vacante("PUBLICADA", false, null);

        assertThatThrownBy(() -> servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plantilla de evaluación");
    }

    @Test
    @DisplayName("una vacante cerrada no cambia de interruptor")
    void unaCerradaNoSeToca() {
        vacante("CERRADA", true, 7L);

        assertThatThrownBy(() -> servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cerrada");
    }

    // ============ Los pesos de la vacante ============

    @Test
    @DisplayName("los pesos solo se cambian a una versión publicada")
    void losPesosExigenVersionPublicada() {
        vacante("PUBLICADA", false, null);
        when(versionesPesos.findByIdAndOrganizacionId(VERSION_PESOS, ORGANIZACION))
                .thenReturn(Optional.of(VersionPesos.builder()
                        .id(VERSION_PESOS).estado("BORRADOR").build()));

        assertThatThrownBy(() -> servicio.asignarVersionPesos(QUIEN, VACANTE, VERSION_PESOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("borrador");
    }

    @Test
    @DisplayName("asignar una versión publicada queda guardado y auditado")
    void asignarUnaPublicadaSeGuarda() {
        Vacante v = vacante("PUBLICADA", false, null);
        v.setVersionPesosId(2L);
        when(versionesPesos.findByIdAndOrganizacionId(VERSION_PESOS, ORGANIZACION))
                .thenReturn(Optional.of(VersionPesos.builder()
                        .id(VERSION_PESOS).estado("PUBLICADA").build()));

        servicio.asignarVersionPesos(QUIEN, VACANTE, VERSION_PESOS);

        assertThat(v.getVersionPesosId()).isEqualTo(VERSION_PESOS);
        verify(vacantes).save(v);
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "asignar_version_pesos",
                "vacante", VACANTE, Map.of("versionPesosId", "2"),
                Map.of("versionPesosId", String.valueOf(VERSION_PESOS)), null);
    }

    // ============ Cuándo cierra la prueba de la vacante ============

    @Test
    @DisplayName("fijar la fecha mueve a los que ya están dentro, no solo a los que entren")
    void laFechaMueveALosQueYaEstanDentro() {
        Vacante v = vacante("PUBLICADA", false, null);
        // Relativa al reloj y no escrita a mano: definirCierrePrueba rechaza una fecha ya
        // pasada, así que un literal futuro caduca solo y revienta el día que le llega.
        Instant domingo = Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS);
        IntentoPrueba heredado = IntentoPrueba.builder()
                .id(1L).postulacionId(11L)
                .venceEn(domingo.plus(5, java.time.temporal.ChronoUnit.DAYS))
                .plazoPropio(false).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(heredado));

        var salida = servicio.definirCierrePrueba(QUIEN, VACANTE,
                new DefinirCierrePrueba(domingo, "Cierre único de la convocatoria"));

        assertThat(v.getPruebaCierraEn()).isEqualTo(domingo);
        assertThat(heredado.getVenceEn())
                .as("si solo valiera para los que entran después, media tanda cerraría otro día")
                .isEqualTo(domingo);
        assertThat(salida.intentosMovidos()).isEqualTo(1);
        verify(intentos).save(heredado);
    }

    @Test
    @DisplayName("a quien le dieron más horas a mano no se le tocan")
    void aQuienTienePlazoPropioNoSeLeToca() {
        vacante("PUBLICADA", false, null);
        // La suya vence más tarde que el cierre de la convocatoria: eso es exactamente lo que
        // significa «más horas a mano». Las dos relativas al reloj, que la fecha que entra a
        // definirCierrePrueba tiene que ser futura y un literal deja de serlo con el tiempo.
        Instant cierre = Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS);
        Instant suya = cierre.plus(2, java.time.temporal.ChronoUnit.DAYS);
        IntentoPrueba conLoSuyo = IntentoPrueba.builder()
                .id(2L).postulacionId(12L).venceEn(suya).plazoPropio(true).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(conLoSuyo));

        var salida = servicio.definirCierrePrueba(QUIEN, VACANTE,
                new DefinirCierrePrueba(cierre, "Cierre único"));

        assertThat(conLoSuyo.getVenceEn())
                .as("perder «más horas para esta persona» al mover la convocatoria sería silencioso")
                .isEqualTo(suya);
        assertThat(salida.intentosMovidos()).isZero();
        assertThat(salida.intentosConPlazoPropio()).isEqualTo(1);
        verify(intentos, org.mockito.Mockito.never()).save(conLoSuyo);
    }

    @Test
    @DisplayName("quitar la fecha NO deja sin vencimiento a quien ya empezó")
    void quitarLaFechaNoDejaSinVencimientoAQuienYaEmpezo() {
        Vacante v = vacante("PUBLICADA", false, null);
        v.setPruebaCierraEn(Instant.parse("2026-08-24T05:00:00Z"));
        Instant empezo = Instant.parse("2026-08-20T10:00:00Z");
        IntentoPrueba corriendo = IntentoPrueba.builder()
                .id(4L).postulacionId(14L).versionPlantillaPruebaId(31L)
                .iniciadoEn(empezo).venceEn(Instant.parse("2026-08-24T05:00:00Z"))
                .plazoPropio(false).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(corriendo));
        when(versionesPrueba.findById(31L)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(31L)
                        .modalidad("PLAZO_ABIERTO").plazoDias(7).build()));

        servicio.definirCierrePrueba(QUIEN, VACANTE, new DefinirCierrePrueba(null, "Se quita"));

        assertThat(corriendo.getVenceEn())
                .as("dejárselo vacío lo dejaría sin vencimiento PARA SIEMPRE: empezar ya no "
                        + "vuelve a pasarle, y el barrido nunca casa contra un nulo")
                .isEqualTo(empezo.plus(7, java.time.temporal.ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("una fecha ya pasada se rechaza: entregaría sola la tanda entera")
    void unaFechaPasadaSeRechaza() {
        vacante("PUBLICADA", false, null);

        assertThatThrownBy(() -> servicio.definirCierrePrueba(QUIEN, VACANTE,
                new DefinirCierrePrueba(Instant.parse("2020-01-01T00:00:00Z"), "error de tecleo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya pasó");
    }

    @Test
    @DisplayName("sobre una prueba cronometrada no se fija fecha: anularía el reloj")
    void sobreUnaCronometradaNoSeFijaFecha() {
        vacante("PUBLICADA", false, null);
        when(versionesPrueba.findById(31L)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(31L)
                        .modalidad("CRONOMETRADA").duracionMinutos(90).build()));

        assertThatThrownBy(() -> servicio.definirCierrePrueba(QUIEN, VACANTE,
                new DefinirCierrePrueba(Instant.now().plusSeconds(86400), "cierre único")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cronometrada");
    }

    @Test
    @DisplayName("quitar la fecha devuelve a contar los días de la plantilla")
    void quitarLaFechaVuelveALosDias() {
        Vacante v = vacante("PUBLICADA", false, null);
        v.setPruebaCierraEn(Instant.parse("2026-08-24T05:00:00Z"));
        IntentoPrueba sinEmpezar = IntentoPrueba.builder()
                .id(3L).postulacionId(13L).venceEn(Instant.parse("2026-08-24T05:00:00Z"))
                .plazoPropio(false).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(sinEmpezar));

        servicio.definirCierrePrueba(QUIEN, VACANTE, new DefinirCierrePrueba(null, "Se amplía"));

        assertThat(v.getPruebaCierraEn()).isNull();
        assertThat(sinEmpezar.getVenceEn())
                .as("sin fecha, al empezar se vuelven a contar los días de la plantilla")
                .isNull();
    }

    // ============ Los textos de correo de la vacante ============

    @Test
    @DisplayName("elegir un texto que no existe se rechaza al configurarlo, no al mandarlo")
    void unTextoInexistenteSeRechazaAqui() {
        vacante("PUBLICADA", false, null);
        when(plantillasCorreo.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                ORGANIZACION, "NO_EXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarPlantillaCorreo(QUIEN, VACANTE,
                new AsignarPlantillaCorreo("PRUEBA_DISPONIBLE", "NO_EXISTE")))
                .as("si se dejara pasar, el fallo saldría semanas después: un candidato "
                        + "avanzaría y su correo no saldría, sin que nada avisara")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_EXISTE");
        verify(plantillasPorVacante, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("sustituir un aviso por sí mismo no es un reemplazo")
    void sustituirlaPorSiMismaNoVale() {
        vacante("PUBLICADA", false, null);

        assertThatThrownBy(() -> servicio.asignarPlantillaCorreo(QUIEN, VACANTE,
                new AsignarPlantillaCorreo("PRUEBA_DISPONIBLE", "PRUEBA_DISPONIBLE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("con la plantilla activa, queda guardado y auditado")
    void elTextoPropioSeGuarda() {
        vacante("PUBLICADA", false, null);
        when(plantillasCorreo.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                ORGANIZACION, "PRUEBA_DISPONIBLE_ADMINISTRADOR"))
                .thenReturn(Optional.of(PlantillaCorreo.builder()
                        .id(4L).codigo("PRUEBA_DISPONIBLE_ADMINISTRADOR").esActiva(true).build()));
        when(plantillasPorVacante.findByVacanteIdAndAvisoCodigo(VACANTE, "PRUEBA_DISPONIBLE"))
                .thenReturn(Optional.empty());
        when(plantillasPorVacante.save(org.mockito.ArgumentMatchers.any(PlantillaCorreoVacante.class)))
                .thenAnswer(i -> i.getArgument(0));

        servicio.asignarPlantillaCorreo(QUIEN, VACANTE,
                new AsignarPlantillaCorreo("PRUEBA_DISPONIBLE", "PRUEBA_DISPONIBLE_ADMINISTRADOR"));

        verify(plantillasPorVacante).save(org.mockito.ArgumentMatchers.any(PlantillaCorreoVacante.class));
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "asignar_plantilla_correo_vacante",
                "vacante", VACANTE, null,
                Map.of("PRUEBA_DISPONIBLE", "PRUEBA_DISPONIBLE_ADMINISTRADOR"), null);
    }

    @Test
    @DisplayName("una versión de pesos de otra organización no existe para quien pregunta")
    void losPesosDeOtraOrganizacionNoExisten() {
        vacante("PUBLICADA", false, null);
        when(versionesPesos.findByIdAndOrganizacionId(VERSION_PESOS, ORGANIZACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarVersionPesos(QUIEN, VACANTE, VERSION_PESOS))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
