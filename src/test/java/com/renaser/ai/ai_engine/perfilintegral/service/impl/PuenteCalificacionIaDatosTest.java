package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.ServicioTextoCv;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que el agente DATOS_CV puede escribir en la ficha del candidato, y lo que no.
 *
 * <p>Esta ficha no puntúa nada: existe para poder mirar una tanda entera sin abrir diez PDF
 * uno por uno. Justo por eso lo que aquí se guarda mal es peor que un hueco: <b>el hueco se
 * ve y el dato falso se cree</b>. Un modelo puede devolver quince habilidades, un teléfono
 * de trescientos caracteres o novecientos meses de experiencia, y ninguna de las tres cosas
 * puede llegar a la pantalla tal cual.
 *
 * <p>Se montan solo las piezas que estos dos métodos usan; las demás entran en nulo a
 * propósito, porque una ficha de datos que de pronto necesite consultar notas o pesos sería
 * un cambio que hay que ver, no uno que pase inadvertido.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El puente entre la IA y los datos del currículum")
class PuenteCalificacionIaDatosTest {

    private static final long POSTULACION = 55L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private DatoCvRepository datosCv;
    @Mock private ServicioTextoCv textoCv;

    @InjectMocks
    private PuenteCalificacionIaImpl puente;

    // ============ Lo que se le manda al agente ============

    @Test
    void elAgenteDeDatosLeeElMismoCurriculumRecortadoQueLosDemas() {
        // No tiene forma de pedir el original: si la tuviera, la edad y el estado civil
        // volverían a salir del sistema por esta puerta (RF-41).
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion()));
        when(vacantes.findById(7L)).thenReturn(Optional.of(vacante()));
        when(puestos.findById(3L)).thenReturn(Optional.of(puesto()));
        when(textoCv.prepararParaIa(POSTULACION)).thenReturn("Camila Rojas, ingeniera...");

        InsumoDatos insumo = puente.insumoDatos(POSTULACION);

        assertThat(insumo.puesto()).isEqualTo("Analista de procesos");
        assertThat(insumo.curriculum()).isEqualTo("Camila Rojas, ingeniera...");
    }

    // ============ Lo que se guarda de lo que contesta ============

    @Test
    void deQuinceHabilidadesSoloSeGuardanCinco() {
        // La instrucción pide las más relevantes. Una lista de quince no se lee de un
        // vistazo, y el único motivo de esta ficha es que se lea de un vistazo.
        DatoCv guardada = guardar(conHabilidades(
                "Excel", "SQL", "Power BI", "Lean", "Scrum",
                "Python", "R", "Tableau", "Six Sigma", "SAP",
                "Minitab", "AutoCAD", "Visio", "Jira", "Git"));

        assertThat(guardada.getHabilidades()).isEqualTo("Excel | SQL | Power BI | Lean | Scrum");
    }

    @Test
    void lasHabilidadesVaciasNoOcupanUnoDeLosCincoSitios() {
        // Si el hueco contara, un modelo que devuelve tres blancos al principio dejaría la
        // ficha con dos habilidades de verdad y tres separadores sueltos.
        DatoCv guardada = guardar(conHabilidades(
                null, "  ", "Excel", "", "SQL", "Power BI", "Lean", "Scrum", "Python"));

        assertThat(guardada.getHabilidades()).isEqualTo("Excel | SQL | Power BI | Lean | Scrum");
    }

    @Test
    void sinHabilidadesNoSeInventaNinguna() {
        DatoCv guardada = guardar(conHabilidades());

        assertThat(guardada.getHabilidades()).isNullOrEmpty();
    }

    @Test
    void losMesesImposiblesSeGuardanComoHueco() {
        // Un negativo es un error de cuenta del modelo y 900 meses son setenta y cinco años
        // de carrera. Ninguno de los dos puede aparecer en la tabla como si fuera cierto.
        DatoCv guardada = guardar(conMeses(-3, 900));

        assertThat(guardada.getExperienciaMesesTotal()).isNull();
        assertThat(guardada.getUltimaMesesDuracion()).isNull();
    }

    @Test
    void sesentaAnosDeCarreraTodaviaSeCreen() {
        // El tope son 720 meses justos: quien empezó a los dieciocho y sigue a los setenta y
        // ocho existe, y borrarle la experiencia sería inventarse un hueco.
        DatoCv guardada = guardar(conMeses(720, 0));

        assertThat(guardada.getExperienciaMesesTotal()).isEqualTo(720);
        // Y un cero es un dato, no un hueco: dice que no tiene experiencia previa.
        assertThat(guardada.getUltimaMesesDuracion()).isZero();
    }

    @Test
    void losTextosSeRecortanASuTope() {
        // La base tiene un tope por columna. Sin recortar aquí, la ficha reventaría al
        // guardar y se perdería el trabajo del agente entero por un resumen largo.
        DatoCv guardada = guardar(new ResultadoDatos(
                "N".repeat(300), "E".repeat(300), "T".repeat(100), "R".repeat(900),
                List.of("Excel"), 24, "P".repeat(300), "M".repeat(300), 12, "D".repeat(200)));

        assertThat(guardada.getNombre()).hasSize(200);
        assertThat(guardada.getEmail()).hasSize(200);
        assertThat(guardada.getTelefono()).hasSize(60);
        assertThat(guardada.getPerfilResumen()).hasSize(500);
        assertThat(guardada.getUltimoPuesto()).hasSize(200);
        assertThat(guardada.getUltimaEmpresa()).hasSize(200);
        assertThat(guardada.getEducacionMaxima()).hasSize(120);
    }

    @Test
    void loVacioSeGuardaComoHuecoYNoComoCadenaEnBlanco() {
        // El currículum de quien no puso su teléfono no tiene teléfono. Guardar espacios
        // haría que la pantalla pintara un campo con algo dentro que no dice nada.
        DatoCv guardada = guardar(new ResultadoDatos(
                "  Camila Rojas  ", "", "   ", null, List.of("Excel"), 24,
                null, "   ", null, ""));

        assertThat(guardada.getNombre()).isEqualTo("Camila Rojas");
        assertThat(guardada.getEmail()).isNull();
        assertThat(guardada.getTelefono()).isNull();
        assertThat(guardada.getPerfilResumen()).isNull();
        assertThat(guardada.getUltimaEmpresa()).isNull();
        assertThat(guardada.getEducacionMaxima()).isNull();
    }

    @Test
    void unaFichaVaciaNoSeGuarda() {
        // Si el modelo no contestó nada, dejar la ficha en blanco borraría la que ya había.
        assertThatThrownBy(() -> puente.guardarDatos(POSTULACION, 1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATOS_CV");
    }

    @Test
    void volverACalificarActualizaLaMismaFichaEnVezDeCrearOtra() {
        // Hay una ficha por postulación. Una segunda fila dejaría al ranking eligiendo al
        // azar cuál de las dos pinta.
        DatoCv laQueYaHabia = DatoCv.builder()
                .id(3L).postulacionId(POSTULACION).nombre("Camila R.")
                .creadoEn(Instant.parse("2026-08-01T10:00:00Z"))
                .build();
        when(datosCv.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(laQueYaHabia));

        puente.guardarDatos(POSTULACION, 9L, conHabilidades("Excel"));

        ArgumentCaptor<DatoCv> guardada = ArgumentCaptor.forClass(DatoCv.class);
        verify(datosCv).save(guardada.capture());
        assertThat(guardada.getValue().getId()).isEqualTo(3L);
        assertThat(guardada.getValue().getCreadoEn()).isEqualTo(Instant.parse("2026-08-01T10:00:00Z"));
        assertThat(guardada.getValue().getEjecucionIaId()).isEqualTo(9L);
    }

    // ============ Apoyo ============

    /** Guarda la ficha y devuelve lo que de verdad se mandó a la base. */
    private DatoCv guardar(ResultadoDatos resultado) {
        when(datosCv.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        puente.guardarDatos(POSTULACION, 1L, resultado);

        ArgumentCaptor<DatoCv> guardada = ArgumentCaptor.forClass(DatoCv.class);
        verify(datosCv).save(guardada.capture());
        return guardada.getValue();
    }

    private ResultadoDatos conHabilidades(String... habilidades) {
        return new ResultadoDatos("Camila Rojas", "camila@correo.com", "999888777",
                "Ingeniera industrial", Arrays.asList(habilidades), 96,
                "Analista", "Fábrica S.A.", 24, "Ingeniería industrial");
    }

    private ResultadoDatos conMeses(Integer total, Integer ultima) {
        return new ResultadoDatos("Camila Rojas", null, null, null, List.of("Excel"),
                total, null, null, ultima, null);
    }

    private Postulacion postulacion() {
        return Postulacion.builder()
                .id(POSTULACION).organizacionId(1L).vacanteId(7L).build();
    }

    private Vacante vacante() {
        return Vacante.builder().id(7L).puestoId(3L).versionPesosId(2L).build();
    }

    private Puesto puesto() {
        return Puesto.builder().id(3L).nombre("Analista de procesos").build();
    }
}
