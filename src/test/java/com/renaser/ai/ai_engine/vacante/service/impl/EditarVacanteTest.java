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
import com.renaser.ai.ai_engine.postulacion.service.PostulacionesEnCarrera;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.GuardarVacante;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.RemuneracionDeLaVacante;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.VacanteActualizadaResponse;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.VacantePanel;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guardar el formulario de una vacante que ya existe.
 *
 * <p>Lo que se protege aquí es que <b>corregir un texto no sea una notificación masiva por
 * accidente, y que un cambio de verdad no pase en silencio</b>. Entre las dos cosas hay
 * cuatro formas de equivocarse, y las cuatro están aquí:
 *
 * <ul>
 *   <li>avisar de un cambio que no ocurrió —el panel manda el formulario entero, y quien
 *       entra a mirar y pulsa guardar llega exactamente aquí—;
 *   <li>avisar dos veces del mismo guardado, que es lo que pasaba cuando el sueldo tenía que
 *       cambiarse en una llamada aparte;
 *   <li>contarle a la gente lo que no le importa: quién lleva el proceso, cuántas plazas hay;
 *   <li>guardar medio formulario porque el sueldo no validaba.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Editar la vacante desde el panel")
class EditarVacanteTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long VACANTE = 40L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            7L, 3L, ORGANIZACION, "EQUIPO", List.of(1L), Map.of("editar_vacante", "TODO"));

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
    @Mock private ServicioAvisosPortal avisos;
    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private Permisos permisos;

    private ServicioVacantesPanelImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioVacantesPanelImpl(vacantes, puestos, requisitos, solicitudes,
                versionesPesos, plantillas, versionesPrueba, plantillasPrueba, plantillasCorreo,
                plantillasPorVacante, intentos, evaluaciones, versionesBanco,
                auditoria, dueno, postulaciones, enCarrera, avisos, alcance, permisos);
    }

    // ---------- el escenario ----------

    /** La vacante tal como está guardada, con su sueldo publicado. */
    private Vacante laVacante(String estado) {
        Vacante v = Vacante.builder()
                .id(VACANTE)
                .organizacionId(ORGANIZACION)
                .solicitudTalentoId(30L)
                .puestoId(5L)
                .titulo("Coordinador de sede")
                .descripcion("Lleva la operación de la sede")
                .horario("L-V de 9 a 6")
                .ubicacion("Lima")
                .tipoCierre("PERMANENTE")
                .responsableUsuarioId(7L)
                .estado(estado)
                .remuneracionTipo("FIJA")
                .remuneracionMin(new BigDecimal("3000"))
                .remuneracionMoneda("PEN")
                .build();
        when(alcance.laVacanteVisible(QUIEN, VACANTE, "editar_vacante")).thenReturn(v);
        return v;
    }

    /**
     * El formulario entero, con lo que se le diga cambiado.
     *
     * <p>Lleva siempre la remuneración actual: el cuerpo del PUT es el formulario completo,
     * y omitirla significaría «no publicar el sueldo», que en una vacante publicada es
     * justamente lo que no se puede hacer.
     */
    private GuardarVacante formulario(String titulo, String descripcion, String horario,
                                      String ubicacion) {
        return new GuardarVacante(30L, 5L, titulo, descripcion, null, null, null, null,
                horario, ubicacion,
                new RemuneracionDeLaVacante("FIJA", new BigDecimal("3000"), null, "PEN"),
                "PERMANENTE", null, null, null, 7L, null);
    }

    private GuardarVacante elMismoFormulario() {
        return formulario("Coordinador de sede", "Lleva la operación de la sede",
                "L-V de 9 a 6", "Lima");
    }

    private void hayEnCarrera(int cuantos) {
        lenient().when(enCarrera.deLaVacante(VACANTE)).thenReturn(
                java.util.stream.IntStream.rangeClosed(1, cuantos)
                        .mapToObj(i -> Postulacion.builder()
                                .id((long) i).organizacionId(ORGANIZACION).vacanteId(VACANTE)
                                .usuarioId(100L + i).estadoCodigo("PERFIL_TURNO_CANDIDATO")
                                .build())
                        .toList());
        lenient().when(avisos.publicar(anyLong(), anyLong(), anyString(), anyString(),
                        anyString(), anyLong(), anyLong()))
                .thenAnswer(invocacion -> AvisoPortal.builder().id(1L).build());
    }

    // ---------- sin cambios ----------

    @Test
    @DisplayName("guardar el mismo formulario no toca nada: ni base, ni auditoría, ni avisos")
    void guardarLoMismoNoHaceNada() {
        laVacante("PUBLICADA");
        hayEnCarrera(2);

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE, elMismoFormulario());

        assertThat(respuesta.huboCambios()).isFalse();
        assertThat(respuesta.postulantesAvisados()).isZero();
        verify(vacantes, never()).save(any());
        verifyNoInteractions(auditoria, avisos);
    }

    /**
     * La regresión del hallazgo QA-01, del lado del servicio.
     *
     * <p>Una vacante con fecha de apertura y con plazas que su forma de cierre no usa: el
     * formulario no enseña ninguna de las dos, así que el cuerpo no las trae. Leer esa
     * ausencia como un nulo las borraba, lo auditaba y el panel decía «Cambios guardados»
     * sobre un guardado que nadie pidió.
     */
    @Test
    @DisplayName("lo que el formulario no enseña no se borra al guardar sin tocar nada")
    void loQueElFormularioNoEnsenaNoSeBorra() {
        Vacante v = laVacante("PUBLICADA");
        v.setAbreEn(Instant.parse("2026-09-23T03:35:54Z"));
        v.setPlazas(5);
        hayEnCarrera(2);

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE, elMismoFormulario());

        assertThat(respuesta.huboCambios()).isFalse();
        assertThat(v.getAbreEn()).isEqualTo(Instant.parse("2026-09-23T03:35:54Z"));
        assertThat(v.getPlazas()).isEqualTo(5);
        verify(vacantes, never()).save(any());
        verifyNoInteractions(auditoria, avisos);
    }

    /**
     * La regresión del hallazgo QA-02, del lado del servicio: una vacante que cierra a una
     * hora concreta no puede perderla porque el formulario solo enseñe el día.
     */
    @Test
    @DisplayName("guardar sin tocar la fecha de cierre no le quita la hora a la que cerraba")
    void laHoraDelCierreNoSePierde() {
        Vacante v = laVacante("PUBLICADA");
        v.setTipoCierre("FECHA");
        v.setCierraEn(Instant.parse("2026-12-01T18:30:00Z"));
        hayEnCarrera(2);

        GuardarVacante comoLoMandaElPanel = new GuardarVacante(30L, 5L, "Coordinador de sede",
                "Lleva la operación de la sede", null, null, null, null, "L-V de 9 a 6",
                "Lima", new RemuneracionDeLaVacante("FIJA", new BigDecimal("3000"), null, "PEN"),
                "FECHA", null, null, Instant.parse("2026-12-01T00:00:00Z"), 7L, null);

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE, comoLoMandaElPanel);

        assertThat(respuesta.huboCambios()).isFalse();
        assertThat(v.getCierraEn()).isEqualTo(Instant.parse("2026-12-01T18:30:00Z"));
        verify(vacantes, never()).save(any());
        verifyNoInteractions(auditoria, avisos);
    }

    @Test
    @DisplayName("cambiar el día de cierre sí se guarda, y sigue sin avisar a nadie")
    void cambiarElDiaDeCierreSeGuarda() {
        Vacante v = laVacante("PUBLICADA");
        v.setTipoCierre("FECHA");
        v.setCierraEn(Instant.parse("2026-12-01T18:30:00Z"));
        hayEnCarrera(2);

        GuardarVacante otroDia = new GuardarVacante(30L, 5L, "Coordinador de sede",
                "Lleva la operación de la sede", null, null, null, null, "L-V de 9 a 6",
                "Lima", new RemuneracionDeLaVacante("FIJA", new BigDecimal("3000"), null, "PEN"),
                "FECHA", null, null, Instant.parse("2026-12-15T00:00:00Z"), 7L, null);

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE, otroDia);

        assertThat(respuesta.huboCambios()).isTrue();
        assertThat(respuesta.postulantesAvisados()).isZero();
        assertThat(v.getCierraEn()).isEqualTo(Instant.parse("2026-12-15T00:00:00Z"));
        verify(vacantes).save(v);
        verifyNoInteractions(avisos);
    }

    @Test
    @DisplayName("cambiar la forma de cierre sí limpia lo que ya no rige, y se guarda auditado")
    void cambiarLaFormaDeCierreLimpiaLoQueSobra() {
        Vacante v = laVacante("PUBLICADA");
        v.setTipoCierre("PLAZAS");
        v.setPlazas(5);
        v.setAbreEn(Instant.parse("2026-09-23T03:35:54Z"));
        hayEnCarrera(2);

        GuardarVacante aPermanente = new GuardarVacante(30L, 5L, "Coordinador de sede",
                "Lleva la operación de la sede", null, null, null, null, "L-V de 9 a 6",
                "Lima", new RemuneracionDeLaVacante("FIJA", new BigDecimal("3000"), null, "PEN"),
                "PERMANENTE", null, null, null, 7L, null);

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE, aPermanente);

        assertThat(respuesta.huboCambios()).isTrue();
        assertThat(respuesta.postulantesAvisados()).as("la forma de cierre es interna").isZero();
        assertThat(v.getPlazas()).isNull();
        // Y la apertura sigue intacta: no se limpia lo que no se enseñó.
        assertThat(v.getAbreEn()).isEqualTo(Instant.parse("2026-09-23T03:35:54Z"));
        verify(vacantes).save(v);
        verifyNoInteractions(avisos);
    }

    @Test
    @DisplayName("y los espacios de más tampoco son un cambio: reenviar la misma edición es inofensivo")
    void losEspaciosNoSonUnCambio() {
        laVacante("PUBLICADA");
        hayEnCarrera(2);

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE,
                formulario("  Coordinador de sede ", "Lleva la operación de la sede  ",
                        " L-V de 9 a 6", "Lima "));

        assertThat(respuesta.huboCambios()).isFalse();
        verifyNoInteractions(auditoria, avisos);
    }

    // ---------- lo que se avisa ----------

    @Test
    @DisplayName("cambiar el horario y la descripción deja UN aviso a cada persona en carrera")
    void unSoloAvisoPorPersona() {
        laVacante("PUBLICADA");
        hayEnCarrera(2);

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE,
                formulario("Coordinador de sede", "Otra descripción", "Turnos rotativos", "Lima"));

        assertThat(respuesta.huboCambios()).isTrue();
        assertThat(respuesta.postulantesAvisados()).isEqualTo(2);

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(avisos, times(2)).publicar(eq(ORGANIZACION), anyLong(),
                eq(AvisoPortal.VACANTE_ACTUALIZADA), eq("Se actualizó la vacante «Coordinador de sede»"),
                cuerpo.capture(), anyLong(), eq(VACANTE));
        assertThat(cuerpo.getValue())
                .contains("Horario: L-V de 9 a 6 → Turnos rotativos")
                .contains("Se actualizó la descripción")
                .endsWith("Tu postulación sigue su curso y no tienes que hacer nada.");
    }

    @Test
    @DisplayName("el sueldo y el texto en el mismo guardado salen en un solo aviso")
    void elSueldoYElTextoVanJuntos() {
        Vacante v = laVacante("PUBLICADA");
        hayEnCarrera(1);

        GuardarVacante datos = new GuardarVacante(30L, 5L, "Coordinador de sede",
                "Lleva la operación de la sede", null, null, null, null, "L-V de 9 a 6",
                "Arequipa",
                new RemuneracionDeLaVacante("FIJA", new BigDecimal("3500"), null, "PEN"),
                "PERMANENTE", null, null, null, 7L, "Se ajustó la banda");

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE, datos);

        assertThat(respuesta.postulantesAvisados()).isEqualTo(1);
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(avisos).publicar(anyLong(), anyLong(), eq(AvisoPortal.VACANTE_ACTUALIZADA),
                anyString(), cuerpo.capture(), anyLong(), anyLong());
        assertThat(cuerpo.getValue())
                .contains("Ubicación: Lima → Arequipa")
                .contains("Remuneración: S/ 3 000 → S/ 3 500");

        assertThat(v.getRemuneracionMin()).isEqualByComparingTo("3500");
        // La marca del sueldo solo se mueve cuando el sueldo cambia de verdad.
        assertThat(v.getRemuneracionActualizadaEn()).isNotNull();
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("editar_vacante"),
                eq("vacante"), eq(VACANTE), any(), any(), eq("Se ajustó la banda"));
    }

    @Test
    @DisplayName("lo interno se guarda y se audita, y no le llega a nadie")
    void loInternoNoSeAvisa() {
        Vacante v = laVacante("PUBLICADA");
        hayEnCarrera(2);

        GuardarVacante datos = new GuardarVacante(30L, 5L, "Coordinador de sede",
                "Lleva la operación de la sede", null, null, null, null, "L-V de 9 a 6", "Lima",
                new RemuneracionDeLaVacante("FIJA", new BigDecimal("3000"), null, "PEN"),
                "PLAZAS", 3, null, null, 9L, null);

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE, datos);

        assertThat(respuesta.huboCambios()).isTrue();
        assertThat(respuesta.postulantesAvisados()).isZero();
        verifyNoInteractions(avisos);
        verify(vacantes).save(v);
        assertThat(v.getPlazas()).isEqualTo(3);
        assertThat(v.getResponsableUsuarioId()).isEqualTo(9L);
        verify(auditoria).registrar(anyLong(), any(), eq("editar_vacante"), anyString(),
                anyLong(), any(), any(), eq(null));
    }

    @Test
    @DisplayName("en borrador se guarda igual, y no hay a quién avisar")
    void enBorradorNoSeAvisa() {
        Vacante v = laVacante("BORRADOR");

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE,
                formulario("Coordinador de sede", "Otra descripción", "L-V de 9 a 6", "Lima"));

        assertThat(respuesta.huboCambios()).isTrue();
        assertThat(respuesta.postulantesAvisados()).isZero();
        verifyNoInteractions(avisos);
        assertThat(v.getDescripcion()).isEqualTo("Otra descripción");
    }

    @Test
    @DisplayName("que a uno no se le pueda avisar no deshace el cambio ni deja sin aviso a los demás")
    void unFalloNoTumbaElResto() {
        Vacante v = laVacante("PUBLICADA");
        hayEnCarrera(2);
        when(avisos.publicar(anyLong(), eq(101L), anyString(), anyString(), anyString(),
                anyLong(), anyLong())).thenThrow(new RuntimeException("la base se cayó"));

        VacanteActualizadaResponse respuesta = servicio.editar(QUIEN, VACANTE,
                formulario("Coordinador de sede", "Lleva la operación de la sede",
                        "Turnos rotativos", "Lima"));

        assertThat(respuesta.postulantesAvisados()).isEqualTo(1);
        assertThat(v.getHorario()).isEqualTo("Turnos rotativos");
        verify(vacantes).save(v);
    }

    // ---------- lo que no se puede ----------

    @Test
    @DisplayName("una vacante cerrada no se edita")
    void laCerradaNoSeEdita() {
        laVacante("CERRADA");

        assertThatThrownBy(() -> servicio.editar(QUIEN, VACANTE,
                formulario("Otro título", "Otra descripción", "L-V de 9 a 6", "Lima")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cerrada no se edita");

        verify(vacantes, never()).save(any());
        verifyNoInteractions(auditoria, avisos);
    }

    @Test
    @DisplayName("un sueldo imposible no deja el resto del formulario guardado")
    void elSueldoInvalidoNoGuardaNada() {
        Vacante v = laVacante("PUBLICADA");

        GuardarVacante datos = new GuardarVacante(30L, 5L, "Coordinador de sede senior",
                "Lleva la operación de la sede", null, null, null, null, "L-V de 9 a 6", "Lima",
                new RemuneracionDeLaVacante("RANGO", new BigDecimal("4000"),
                        new BigDecimal("3000"), "PEN"),
                "PERMANENTE", null, null, null, 7L, "Se abre la banda");

        assertThatThrownBy(() -> servicio.editar(QUIEN, VACANTE, datos))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(v.getTitulo()).isEqualTo("Coordinador de sede");
        verify(vacantes, never()).save(any());
        verifyNoInteractions(auditoria, avisos);
    }

    @Test
    @DisplayName("una vacante publicada que enseña el sueldo no puede esconderlo desde el formulario")
    void noSePuedeEsconderElSueldoDesdeElFormulario() {
        Vacante v = laVacante("PUBLICADA");

        GuardarVacante datos = new GuardarVacante(30L, 5L, "Otro título",
                "Lleva la operación de la sede", null, null, null, null, "L-V de 9 a 6", "Lima",
                RemuneracionDeLaVacante.OCULTA, "PERMANENTE", null, null, null, 7L, "Lo ocultamos");

        assertThatThrownBy(() -> servicio.editar(QUIEN, VACANTE, datos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sin dar nada a cambio");

        assertThat(v.getTitulo()).isEqualTo("Coordinador de sede");
        verify(vacantes, never()).save(any());
        verifyNoInteractions(auditoria, avisos);
    }

    @Test
    @DisplayName("cambiar el sueldo de una publicada sin decir por qué se rechaza")
    void elSueldoDeUnaPublicadaPideMotivo() {
        laVacante("PUBLICADA");

        GuardarVacante datos = new GuardarVacante(30L, 5L, "Coordinador de sede",
                "Lleva la operación de la sede", null, null, null, null, "L-V de 9 a 6", "Lima",
                new RemuneracionDeLaVacante("FIJA", new BigDecimal("3500"), null, "PEN"),
                "PERMANENTE", null, null, null, 7L, "   ");

        assertThatThrownBy(() -> servicio.editar(QUIEN, VACANTE, datos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Di por qué cambia el sueldo");

        verify(vacantes, never()).save(any());
        verifyNoInteractions(auditoria, avisos);
    }

    @Test
    @DisplayName("en borrador no se pide motivo: no hay a quién explicárselo")
    void enBorradorElSueldoNoPideMotivo() {
        Vacante v = laVacante("BORRADOR");

        GuardarVacante datos = new GuardarVacante(30L, 5L, "Coordinador de sede",
                "Lleva la operación de la sede", null, null, null, null, "L-V de 9 a 6", "Lima",
                new RemuneracionDeLaVacante("FIJA", new BigDecimal("3500"), null, "PEN"),
                "PERMANENTE", null, null, null, 7L, null);

        assertThat(servicio.editar(QUIEN, VACANTE, datos).huboCambios()).isTrue();
        assertThat(v.getRemuneracionMin()).isEqualByComparingTo("3500");
    }

    @Test
    @DisplayName("fuera del alcance del rol, la vacante no existe: 404 y no 403")
    void fueraDelAlcanceEs404() {
        when(alcance.laVacanteVisible(QUIEN, VACANTE, "editar_vacante"))
                .thenThrow(new ResourceNotFoundException("Vacante", "id", VACANTE));

        assertThatThrownBy(() -> servicio.editar(QUIEN, VACANTE, elMismoFormulario()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(vacantes, never()).save(any());
    }

    // ---------- lo que la lista enseña ----------

    @Test
    @DisplayName("la lista trae el texto entero, la gente en carrera y si quien mira puede editar")
    void laListaTraeLoQueElLapizNecesita() {
        Vacante publicada = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                .titulo("Coordinador de sede").descripcion("Lleva la operación de la sede")
                .horario("L-V de 9 a 6").estado("PUBLICADA").responsableUsuarioId(7L)
                .remuneracionTipo("OCULTA").build();
        Vacante cerrada = Vacante.builder().id(41L).organizacionId(ORGANIZACION)
                .titulo("Analista").estado("CERRADA").responsableUsuarioId(7L)
                .remuneracionTipo("OCULTA").build();
        when(vacantes.findByOrganizacionIdAndArchivadaEnIsNullOrderByCreadoEnDesc(ORGANIZACION))
                .thenReturn(List.of(publicada, cerrada));
        when(enCarrera.cuantasPorVacante(ORGANIZACION)).thenReturn(Map.of(VACANTE, 3));
        when(permisos.alcanceDe("editar_vacante"))
                .thenReturn(FiltroAlcance.desde("TODO", QUIEN.usuarioId()));
        when(alcance.alcanzaALaVacante(eq(QUIEN), any(), any())).thenReturn(true);

        List<VacantePanel> lista = servicio.listar(QUIEN, false);

        assertThat(lista.get(0).postulantesEnCarrera()).isEqualTo(3);
        assertThat(lista.get(0).descripcion()).isEqualTo("Lleva la operación de la sede");
        assertThat(lista.get(0).horario()).isEqualTo("L-V de 9 a 6");
        assertThat(lista.get(0).puedeEditar()).isTrue();
        // La cerrada no se edita —lo hace cumplir `editar`—, así que el lápiz no se pinta:
        // un botón que siempre contesta 409 es una promesa rota.
        assertThat(lista.get(1).puedeEditar()).isFalse();
        assertThat(lista.get(1).postulantesEnCarrera()).isZero();
    }

    @Test
    @DisplayName("sin el permiso de editar, ninguna fila ofrece el lápiz")
    void sinPermisoNoHayLapiz() {
        ContextoUsuario mirón = new ContextoUsuario(8L, 4L, ORGANIZACION, "EQUIPO",
                List.of(2L), Map.of("ver_vacantes", "TODO"));
        Vacante publicada = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                .titulo("Coordinador de sede").estado("PUBLICADA").responsableUsuarioId(7L)
                .remuneracionTipo("OCULTA").build();
        when(vacantes.findByOrganizacionIdAndArchivadaEnIsNullOrderByCreadoEnDesc(ORGANIZACION))
                .thenReturn(List.of(publicada));
        when(enCarrera.cuantasPorVacante(ORGANIZACION)).thenReturn(Map.of());

        assertThat(servicio.listar(mirón, false).get(0).puedeEditar()).isFalse();
        // Y no se le pregunta por un alcance que no tiene: `alcanceDe` lanzaría.
        verifyNoInteractions(permisos);
    }
}
