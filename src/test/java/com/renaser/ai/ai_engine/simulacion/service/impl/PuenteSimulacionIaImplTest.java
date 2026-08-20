package com.renaser.ai.ai_engine.simulacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.Alerta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.HallazgoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.repository.AfirmacionCvRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.AlertaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.HallazgoPerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.Contradiccion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.DijoEnElCurriculum;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.InsumoConversacion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.LoQueEscribio;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.LoQueSeVio;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.MomentoDeLaSesion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.NotaDeLaSimulacion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.PreguntaIa;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.ResultadoConversacion;
import com.renaser.ai.ai_engine.simulacion.entity.InscripcionSesion;
import com.renaser.ai.ai_engine.simulacion.entity.MarcaTiempoSimulacion;
import com.renaser.ai.ai_engine.simulacion.entity.PreguntaGenerada;
import com.renaser.ai.ai_engine.simulacion.entity.SesionSimulacion;
import com.renaser.ai.ai_engine.simulacion.repository.InscripcionSesionRepository;
import com.renaser.ai.ai_engine.simulacion.repository.MarcaTiempoSimulacionRepository;
import com.renaser.ai.ai_engine.simulacion.repository.PreguntaGeneradaRepository;
import com.renaser.ai.ai_engine.simulacion.repository.SesionSimulacionRepository;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.service.ContextoDeLaVacante;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lo que el agente de la conversación final puede escribir, y lo que no puede tocar.
 *
 * <p>La regla que más importa es la primera: <b>lo que se dijo en la sala no se borra</b>.
 * Las preguntas se pueden volver a pedir —porque se ajustó una nota, o porque la primera
 * tanda salió floja—, y una pregunta que ya se le leyó al candidato y él contestó es un
 * hecho ocurrido. Si una segunda pasada la barriera, quedaría una respuesta huérfana o, peor,
 * desaparecería del expediente la única prueba de que ese riesgo se aclaró.
 *
 * <p>La otra mitad es lo que se le manda al agente, y ahí lo que se prueba es que
 * <b>ningún hecho se pierda por el camino</b>. Este puente junta cosas de seis sitios
 * distintos —el retrato, las alertas, el currículum, las notas del facilitador, las horas de
 * la sesión y lo que escribió en la prueba— y la contradicción que da la buena pregunta vive
 * <i>entre</i> dos de ellas. Si una se cae en silencio, no falla nada: sale una pregunta de
 * manual, se paga la llamada igual, y nadie se entera de que la buena nunca se pudo escribir.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El puente entre la IA y la conversación final")
class PuenteSimulacionIaImplTest {

    private static final long POSTULACION = 55L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private PerfilTalentoRepository perfiles;
    @Mock private HallazgoPerfilRepository hallazgos;
    @Mock private AlertaRepository alertas;
    @Mock private CvRepository cvs;
    @Mock private AfirmacionCvRepository afirmaciones;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private CalificacionPorCriterio calificacion;
    @Mock private InscripcionSesionRepository inscripciones;
    @Mock private SesionSimulacionRepository sesiones;
    @Mock private MarcaTiempoSimulacionRepository marcas;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private RespuestaPruebaRepository respuestasPrueba;
    @Mock private PreguntaPruebaRepository preguntasPrueba;
    @Mock private PreguntaGeneradaRepository preguntas;
    @Mock private ContextoDeLaVacante contexto;

    @InjectMocks
    private PuenteSimulacionIaImpl puente;

    // ============ Lo que se le manda al agente ============

    @Test
    @DisplayName("sin nada que preguntar no se gasta una llamada al modelo")
    void sinNadaDeLoQuePreguntarNoSeGastaUnaLlamada() {
        // Sin retrato, sin notas y sin eventos marcados, el modelo solo podría escribir
        // preguntas de manual. Esas no aportan nada que no estuviera ya en el currículum, y
        // producirlas cuesta lo mismo que producir las buenas.
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion()));
        when(contexto.puestoDe(any(Postulacion.class))).thenReturn(puesto());
        when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        when(alertas.findByPostulacionId(POSTULACION)).thenReturn(List.of());
        when(calificacion.rubricaGlobalDe("SIMULACION")).thenReturn(List.of());
        when(inscripciones.findByPostulacionIdAndEsVigenteTrue(POSTULACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.insumoConversacion(POSTULACION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nada de lo que preguntar");
    }

    @Test
    @DisplayName("una postulación que no existe es un 404, no un insumo a medias")
    void unaPostulacionQueNoExisteEsUn404() {
        // Si esto saliera como una avería del sistema, el panel enseñaría un error rojo por un
        // id mal tecleado, y nadie miraría el id.
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.insumoConversacion(POSTULACION))
                .as("un id que no está en la base no es una avería, es un no encontrado")
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("el puesto y lo que busca la vacante viajan siempre: sin ellos no hay contra qué juzgar")
    void elPuestoYLoQueBuscaLaVacanteViajanSiempre() {
        // Una misma respuesta es excelente para una vacante operativa y floja para una de
        // dirección, y esa diferencia no está en la respuesta: está en el puesto.
        montarInsumo();
        conAlgoQuePreguntar();
        when(contexto.queBuscaLaVacanteDe(any(Postulacion.class)))
                .thenReturn("Analista de procesos\nOrdenar el flujo de compras");

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.puesto()).isEqualTo("Analista de procesos");
        assertThat(insumo.nivelPuesto())
                .as("el nivel es lo que decide qué se le puede exigir a la misma respuesta")
                .isEqualTo("OPERATIVO");
        assertThat(insumo.queBuscaLaVacante()).contains("Ordenar el flujo de compras");
    }

    @Test
    @DisplayName("el retrato y cada hallazgo viajan con su evidencia, no solo con su nombre")
    void elRetratoYCadaHallazgoViajanConSuEvidencia() {
        // La evidencia es lo que convierte «avisa tarde» en una pregunta sobre un hecho de esa
        // misma mañana. Sin ella el modelo solo puede repetir la etiqueta, y una pregunta que
        // repite una etiqueta suena a acusación en vez de a dato.
        montarInsumo();
        PerfilTalento perfil = PerfilTalento.builder()
                .id(11L).postulacionId(POSTULACION).resumen("Ordena bien y avisa tarde")
                .build();
        when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(perfil));
        when(hallazgos.findByPerfilTalentoId(11L)).thenReturn(List.of(
                hallazgo("RIESGO_CRITICO", "Avisa tarde",
                        "Lo detectó a las 10:41 y lo informó a las 10:49")));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.retrato()).isEqualTo("Ordena bien y avisa tarde");
        assertThat(insumo.hallazgos())
                .extracting(LoQueSeVio::tipo, LoQueSeVio::descripcion, LoQueSeVio::evidencia)
                .as("el tipo sin la evidencia no da una pregunta que el candidato reconozca")
                .containsExactly(tuple("RIESGO_CRITICO", "Avisa tarde",
                        "Lo detectó a las 10:41 y lo informó a las 10:49"));
    }

    @Test
    @DisplayName("sin Perfil de Talento ni se buscan hallazgos ni se inventa un retrato")
    void sinPerfilDeTalentoNiSeBuscanHallazgosNiHayRetrato() {
        // Pasa de verdad: la conversación se puede pedir antes de que el retrato esté hecho.
        // Buscar hallazgos de un perfil que no existe sería una consulta con id nulo.
        montarInsumo();
        conAlgoQuePreguntar();
        when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.retrato()).isNull();
        assertThat(insumo.hallazgos()).isEmpty();
        verifyNoInteractions(hallazgos);
    }

    @Test
    @DisplayName("cada contradicción viaja con su id, que es lo que la pregunta devuelve atado")
    void cadaContradiccionViajaConSuId() {
        // El id va de ida y de vuelta. Es lo que permite ver después si ese riesgo concreto
        // quedó resuelto en la conversación o sigue abierto cuando llega la decisión.
        montarInsumo();
        when(alertas.findByPostulacionId(POSTULACION)).thenReturn(List.of(alerta(9L)));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.alertas())
                .extracting(Contradiccion::alertaId, Contradiccion::tipo,
                        Contradiccion::descripcion)
                .containsExactly(tuple(9L, "CONTRADICCION", "Dice que avisa pronto"));
    }

    @Test
    @DisplayName("una afirmación ya demostrada no se le da al agente")
    void unaAfirmacionYaDemostradaNoSeLeDaAlAgente() {
        // Preguntarla otra vez es hacerle repetir lo que ya enseñó, y gasta uno de los cinco
        // huecos de la conversación. El hueco que hay que cerrar es el de lo no comprobado.
        montarInsumo();
        conAlgoQuePreguntar();
        when(cvs.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(
                Cv.builder().id(4L).postulacionId(POSTULACION).build()));
        when(afirmaciones.findByCvId(4L)).thenReturn(List.of(
                afirmacion("Lideré un equipo de ocho", "DEMOSTRADA"),
                afirmacion("Reduje el retrabajo a la mitad", "DECLARADA"),
                afirmacion("Aviso los riesgos temprano", "CONTRADICHA"),
                afirmacion("Sé leer un tablero de indicadores", "FALTA_INFO")));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.afirmacionesDelCurriculum())
                .extracting(DijoEnElCurriculum::texto)
                .as("solo lo que sigue sin comprobarse da una pregunta que aporte algo")
                .containsExactly("Reduje el retrabajo a la mitad", "Aviso los riesgos temprano",
                        "Sé leer un tablero de indicadores");
    }

    @Test
    @DisplayName("sin currículum en la postulación no se buscan afirmaciones")
    void sinCurriculumNoSeBuscanAfirmaciones() {
        montarInsumo();
        conAlgoQuePreguntar();
        when(cvs.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.afirmacionesDelCurriculum()).isEmpty();
        verifyNoInteractions(afirmaciones);
    }

    @Test
    @DisplayName("un criterio sin nota puesta no viaja como si tuviera un cero")
    void unCriterioSinNotaPuestaNoViaja() {
        // Media rúbrica no es una nota. Mandar un criterio sin puntaje dejaría al modelo
        // preguntando por un cero que nadie puso, y el facilitador tendría que defenderlo.
        montarInsumo();
        Criterio calidad = criterio(1L, "Calidad del entregable");
        Criterio comunica = criterio(2L, "Comunicación del riesgo");
        Criterio cambio = criterio(3L, "Manejo del cambio");
        when(calificacion.rubricaGlobalDe("SIMULACION"))
                .thenReturn(List.of(calidad, comunica, cambio));
        when(notasCriterio.findByPostulacionId(POSTULACION)).thenReturn(List.of(
                nota(1L, new BigDecimal("7.5"), "Entregó completo y a tiempo"),
                nota(2L, null, "Todavía sin puntuar")));
        when(calificacion.maximosDe(POSTULACION, List.of(calidad, comunica, cambio)))
                .thenReturn(Map.of(1L, BigDecimal.TEN, 2L, BigDecimal.TEN, 3L, BigDecimal.TEN));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.notasDeLaSimulacion())
                .extracting(NotaDeLaSimulacion::criterio, NotaDeLaSimulacion::puntaje,
                        NotaDeLaSimulacion::puntosMaximos, NotaDeLaSimulacion::explicacion)
                .as("ni el criterio sin nota ni el que tiene la fila pero no el puntaje viajan")
                .containsExactly(tuple("Calidad del entregable", new BigDecimal("7.5"),
                        BigDecimal.TEN, "Entregó completo y a tiempo"));
    }

    @Test
    @DisplayName("dos notas del mismo criterio no tumban el insumo: se queda la primera")
    void dosNotasDelMismoCriterioNoTumbanElInsumo() {
        // Hoy la base no deja que pase, y por eso conviene que esté escrito: si algún día una
        // migración quita ese candado, la petición tiene que seguir saliendo con una nota
        // elegida, no reventar con «clave duplicada» el día de la sesión.
        montarInsumo();
        Criterio calidad = criterio(1L, "Calidad del entregable");
        when(calificacion.rubricaGlobalDe("SIMULACION")).thenReturn(List.of(calidad));
        when(notasCriterio.findByPostulacionId(POSTULACION)).thenReturn(List.of(
                nota(1L, new BigDecimal("7.5"), "La que se puso primero"),
                nota(1L, new BigDecimal("9.0"), "Una segunda fila del mismo criterio")));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.notasDeLaSimulacion()).singleElement()
                .as("se queda una sola, y siempre la misma: la primera")
                .satisfies(n -> assertThat(n.explicacion()).isEqualTo("La que se puso primero"));
    }

    @Test
    @DisplayName("sin rúbrica de simulación configurada no se consultan las notas")
    void sinRubricaDeSimulacionNoSeConsultanLasNotas() {
        // Las notas se buscan por postulación, no por criterio: sin rúbrica se traerían las
        // del currículum y de la prueba, que son de otra escala y de otro momento.
        montarInsumo();
        conAlgoQuePreguntar();
        when(calificacion.rubricaGlobalDe("SIMULACION")).thenReturn(List.of());

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.notasDeLaSimulacion()).isEmpty();
        verifyNoInteractions(notasCriterio);
    }

    @Test
    @DisplayName("cada momento viaja con la hora de reloj y con el minuto de la sesión")
    void cadaMomentoViajaConLaHoraDeRelojYConElMinutoDeLaSesion() {
        // No es redundancia. La hora de reloj es la que el candidato reconoce —él estaba ahí a
        // las 10:41— y hace que la pregunta suene a hecho. El minuto es el único que se puede
        // comparar entre dos candidatos, porque no depende de a qué hora empezó su grupo.
        montarInsumo();
        when(inscripciones.findByPostulacionIdAndEsVigenteTrue(POSTULACION))
                .thenReturn(Optional.of(inscripcion(30L, 12L)));
        when(sesiones.findById(12L)).thenReturn(Optional.of(SesionSimulacion.builder()
                .id(12L).fechaHora(esaManana(10, 0))
                .build()));
        when(marcas.findByInscripcionSesionIdOrderByOcurridaEn(30L)).thenReturn(List.of(
                marca("APARECE_CAMBIO", esaManana(10, 41)),
                marca("ABRE_CAMBIO", esaManana(10, 49))));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.lineaDeTiempo())
                .extracting(MomentoDeLaSesion::evento, MomentoDeLaSesion::hora,
                        MomentoDeLaSesion::minutoDeLaSesion)
                .as("de la distancia entre estas dos horas sale la mejor pregunta del día")
                .containsExactly(
                        tuple("APARECE_CAMBIO", "10:41", 41),
                        tuple("ABRE_CAMBIO", "10:49", 49));
    }

    @Test
    @DisplayName("si no se sabe a qué hora empezó la sesión se pierde el minuto, pero no la hora")
    void sinLaHoraDeInicioSePierdeElMinutoPeroNoLaHora() {
        // Pasa si la sesión se borró o se rehízo. La hora de reloj sigue siendo un hecho y la
        // pregunta se puede escribir igual: lo que no se puede es comparar con otro candidato.
        montarInsumo();
        when(inscripciones.findByPostulacionIdAndEsVigenteTrue(POSTULACION))
                .thenReturn(Optional.of(inscripcion(30L, 12L)));
        when(sesiones.findById(12L)).thenReturn(Optional.empty());
        when(marcas.findByInscripcionSesionIdOrderByOcurridaEn(30L))
                .thenReturn(List.of(marca("APARECE_CAMBIO", esaManana(10, 41))));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.lineaDeTiempo()).singleElement().satisfies(momento -> {
            assertThat(momento.hora()).isEqualTo("10:41");
            assertThat(momento.minutoDeLaSesion())
                    .as("sin hora de inicio, cualquier minuto sería inventado")
                    .isNull();
        });
    }

    @Test
    @DisplayName("una marca anterior al inicio no inventa un minuto negativo")
    void unaMarcaAnteriorAlInicioNoInventaUnMinutoNegativo() {
        // Se marcó algo antes de que la sesión arrancara oficialmente, o la fecha se corrigió
        // después. «Minuto -5 de la sesión» no significa nada y nadie sabría leerlo.
        montarInsumo();
        when(inscripciones.findByPostulacionIdAndEsVigenteTrue(POSTULACION))
                .thenReturn(Optional.of(inscripcion(30L, 12L)));
        when(sesiones.findById(12L)).thenReturn(Optional.of(SesionSimulacion.builder()
                .id(12L).fechaHora(esaManana(10, 0))
                .build()));
        when(marcas.findByInscripcionSesionIdOrderByOcurridaEn(30L))
                .thenReturn(List.of(marca("APARECE_CAMBIO", esaManana(9, 55))));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.lineaDeTiempo()).singleElement().satisfies(momento -> {
            assertThat(momento.hora()).isEqualTo("09:55");
            assertThat(momento.minutoDeLaSesion()).isNull();
        });
    }

    @Test
    @DisplayName("una marca sin hora no tumba el resto de la línea de tiempo")
    void unaMarcaSinHoraNoTumbaElRestoDeLaLineaDeTiempo() {
        // El evento ocurrió aunque nadie apuntara la hora. Que se caiga la petición entera por
        // eso dejaría al facilitador sin ninguna pregunta el día de la sesión.
        montarInsumo();
        when(inscripciones.findByPostulacionIdAndEsVigenteTrue(POSTULACION))
                .thenReturn(Optional.of(inscripcion(30L, 12L)));
        when(sesiones.findById(12L)).thenReturn(Optional.of(SesionSimulacion.builder()
                .id(12L).fechaHora(esaManana(10, 0))
                .build()));
        when(marcas.findByInscripcionSesionIdOrderByOcurridaEn(30L)).thenReturn(List.of(
                marca("APARECE_CAMBIO", null),
                marca("ABRE_CAMBIO", esaManana(10, 49))));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.lineaDeTiempo())
                .extracting(MomentoDeLaSesion::evento, MomentoDeLaSesion::hora,
                        MomentoDeLaSesion::minutoDeLaSesion)
                .containsExactly(
                        tuple("APARECE_CAMBIO", null, null),
                        tuple("ABRE_CAMBIO", "10:49", 49));
    }

    @Test
    @DisplayName("sin inscripción vigente no se buscan marcas de ninguna sesión")
    void sinInscripcionVigenteNoSeBuscanMarcas() {
        // Su sesión se canceló y todavía no eligió otra. Buscar marcas por un id nulo traería
        // las de cualquiera.
        montarInsumo();
        conAlgoQuePreguntar();
        when(inscripciones.findByPostulacionIdAndEsVigenteTrue(POSTULACION))
                .thenReturn(Optional.empty());

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.lineaDeTiempo()).isEmpty();
        verifyNoInteractions(sesiones, marcas);
    }

    @Test
    @DisplayName("lo que escribió en la prueba viaja junto al enunciado de su pregunta")
    void loQueEscribioEnLaPruebaViajaJuntoAlEnunciadoDeSuPregunta() {
        // De aquí salen las contradicciones más útiles: escribió «lo primero que hago es
        // preguntar el objetivo» y en la sesión arrancó a producir en el minuto tres. Sin el
        // enunciado al lado, la respuesta suelta no se puede contrastar con nada.
        montarInsumo();
        conAlgoQuePreguntar();
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(intento(21L)));
        when(respuestasPrueba.findByIntentoPruebaId(21L)).thenReturn(List.of(
                respuesta(1L, "Lo primero que hago es preguntar el objetivo"),
                respuesta(2L, "   "),
                respuesta(3L, null)));
        when(preguntasPrueba.findByIdIn(List.of(1L)))
                .thenReturn(List.of(preguntaDePrueba(1L, "¿Qué haces al recibir un encargo?")));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.respuestasDeLaPrueba())
                .extracting(LoQueEscribio::pregunta, LoQueEscribio::respuesta)
                .as("una respuesta en blanco no dice nada y ocuparía sitio en el insumo")
                .containsExactly(tuple("¿Qué haces al recibir un encargo?",
                        "Lo primero que hago es preguntar el objetivo"));
    }

    @Test
    @DisplayName("una respuesta cuya pregunta ya no está en el catálogo se cae en vez de viajar suelta")
    void unaRespuestaCuyaPreguntaYaNoEstaSeCae() {
        // La plantilla de la prueba se editó y esa pregunta desapareció. Mandar la respuesta
        // sin su enunciado le daría al modelo un texto del que no sabe qué se preguntaba.
        montarInsumo();
        conAlgoQuePreguntar();
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(intento(21L)));
        when(respuestasPrueba.findByIntentoPruebaId(21L))
                .thenReturn(List.of(respuesta(4L, "Una respuesta huérfana")));
        when(preguntasPrueba.findByIdIn(List.of(4L))).thenReturn(List.of());

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.respuestasDeLaPrueba()).isEmpty();
    }

    @Test
    @DisplayName("sin prueba del puesto rendida no hay nada que contrastar")
    void sinPruebaDelPuestoNoHayNadaQueContrastar() {
        // La conversación se puede pedir para alguien que llegó a la simulación por otra vía.
        montarInsumo();
        conAlgoQuePreguntar();
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.respuestasDeLaPrueba()).isEmpty();
        verifyNoInteractions(respuestasPrueba, preguntasPrueba);
    }

    @Test
    @DisplayName("con todas las respuestas en blanco ni se consulta el catálogo de preguntas")
    void conTodasLasRespuestasEnBlancoNiSeConsultaElCatalogo() {
        // Entregó la prueba sin escribir nada. Buscar enunciados sería una consulta con una
        // lista de ids vacía para no enseñar ninguna respuesta.
        montarInsumo();
        conAlgoQuePreguntar();
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(intento(21L)));
        when(respuestasPrueba.findByIntentoPruebaId(21L))
                .thenReturn(List.of(respuesta(1L, "  "), respuesta(2L, null)));

        InsumoConversacion insumo = puente.insumoConversacion(POSTULACION);

        assertThat(insumo.respuestasDeLaPrueba()).isEmpty();
        verifyNoInteractions(preguntasPrueba);
    }

    // ============ Lo que se guarda de lo que contesta ============

    @Test
    @DisplayName("lo que se dijo en la sala no se borra: solo se rehacen las que nadie llegó a hacer")
    void lasPreguntasYaContestadasSeQuedanYLasDemasSeRehacen() {
        PreguntaGenerada contestada = pregunta(1L, "¿Qué pasó en esos ocho minutos?", 1,
                "Dije que no lo vi venir");
        PreguntaGenerada sinContestar = pregunta(2L, "Una que nadie llegó a hacer", 2, null);
        montar(List.of(contestada, sinContestar), List.of());

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("Nueva pregunta", "Sale de la línea de tiempo", null))));

        verify(preguntas).delete(sinContestar);
        verify(preguntas, never()).delete(contestada);

        ArgumentCaptor<PreguntaGenerada> guardada =
                ArgumentCaptor.forClass(PreguntaGenerada.class);
        verify(preguntas).save(guardada.capture());
        // Se numera detrás de la que ya se hizo, no encima de ella.
        assertThat(guardada.getValue().getOrden()).isEqualTo(2);
        assertThat(guardada.getValue().getTexto()).isEqualTo("Nueva pregunta");
        assertThat(guardada.getValue().getMotivo()).isEqualTo("Sale de la línea de tiempo");
        assertThat(guardada.getValue().getEjecucionIaId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("una alerta que no es de este candidato se descarta en vez de guardarse")
    void unaAlertaQueNoEsDeEsteCandidatoSeDescarta() {
        // Un id inventado dejaría la pregunta apuntando a la contradicción de otra persona.
        // Vale más una pregunta sin alerta que una atada a la equivocada.
        montar(List.of(), List.of(alerta(9L)));

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("Una pregunta", "Un motivo", 404L))));

        ArgumentCaptor<PreguntaGenerada> guardada =
                ArgumentCaptor.forClass(PreguntaGenerada.class);
        verify(preguntas).save(guardada.capture());
        assertThat(guardada.getValue().getAlertaId()).isNull();
    }

    @Test
    @DisplayName("la alerta de la que sale la pregunta queda atada a ella")
    void laAlertaDeLaQueSaleQuedaAtadaALaPregunta() {
        // Es lo que permite ver después si ese riesgo concreto quedó resuelto en la
        // conversación o sigue abierto cuando llega la decisión.
        montar(List.of(), List.of(alerta(9L)));

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("Una pregunta", "Un motivo", 9L))));

        ArgumentCaptor<PreguntaGenerada> guardada =
                ArgumentCaptor.forClass(PreguntaGenerada.class);
        verify(preguntas).save(guardada.capture());
        assertThat(guardada.getValue().getAlertaId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("nunca se guardan más de cinco preguntas para una conversación de quince minutos")
    void nuncaSeGuardanMasDeCincoPreguntas() {
        // La conversación dura quince minutos. Ocho preguntas no son más información: son la
        // misma conversación corriendo, en la que ninguna respuesta se llega a rascar.
        montar(List.of(), List.of());
        List<PreguntaIa> ocho = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            ocho.add(new PreguntaIa("Pregunta " + i, "Motivo " + i, null));
        }

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(ocho));

        verify(preguntas, times(5)).save(any(PreguntaGenerada.class));
    }

    @Test
    @DisplayName("si el agente no devuelve nada no se guarda una lista vacía")
    void unResultadoVacioNoSeGuarda() {
        assertThatThrownBy(() -> puente.guardarPreguntas(POSTULACION, 77L, null))
                .isInstanceOf(IllegalStateException.class);

        verify(preguntas, never()).save(any());
    }

    @Test
    @DisplayName("tampoco se guardan preguntas de una postulación que no existe")
    void tampocoSeGuardanPreguntasDeUnaPostulacionQueNoExiste() {
        // Sin esta comprobación quedarían preguntas colgando de un id que no es de nadie:
        // filas que nadie ve nunca y que nadie sabe de quién eran.
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.guardarPreguntas(POSTULACION, 77L,
                new ResultadoConversacion(List.of(new PreguntaIa("Una", "Un motivo", null)))))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(preguntas);
    }

    @Test
    @DisplayName("una lista de preguntas nula no rompe ni borra lo que ya había")
    void unaListaDePreguntasNulaNoRompeNiBorra() {
        // El modelo puede devolver el objeto sin la lista. Si eso reventara aquí, el trabajo
        // se marcaría fallido y se reintentaría tres veces, pagando tres llamadas más.
        PreguntaGenerada contestada = pregunta(1L, "¿Qué pasó en esos ocho minutos?", 1,
                "Dije que no lo vi venir");
        montar(List.of(contestada), List.of());

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(null));

        verify(preguntas, never()).save(any());
        verify(preguntas, never()).delete(contestada);
    }

    @Test
    @DisplayName("una pregunta sin texto no se guarda: nadie puede leer un hueco en voz alta")
    void unaPreguntaSinTextoNoSeGuarda() {
        montar(List.of(), List.of());

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa(null, "Un motivo", null),
                new PreguntaIa("   ", "Otro motivo", null),
                new PreguntaIa("La única que se puede leer", "Sale de la línea de tiempo", null))));

        ArgumentCaptor<PreguntaGenerada> guardada =
                ArgumentCaptor.forClass(PreguntaGenerada.class);
        verify(preguntas, times(1)).save(guardada.capture());
        assertThat(guardada.getValue().getTexto()).isEqualTo("La única que se puede leer");
        assertThat(guardada.getValue().getOrden())
                .as("las vacías no reservan sitio en la numeración")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("el texto se guarda sin los espacios de los bordes")
    void elTextoSeGuardaSinLosEspaciosDeLosBordes() {
        // Es lo que hace que la comparación con las ya contestadas funcione, y de paso evita
        // una pregunta que se imprime con un salto de línea delante.
        montar(List.of(), List.of());

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("  ¿Qué pasó en esos ocho minutos?\n", "Un motivo", null))));

        ArgumentCaptor<PreguntaGenerada> guardada =
                ArgumentCaptor.forClass(PreguntaGenerada.class);
        verify(preguntas).save(guardada.capture());
        assertThat(guardada.getValue().getTexto()).isEqualTo("¿Qué pasó en esos ocho minutos?");
    }

    @Test
    @DisplayName("una pregunta que ya se contestó no se vuelve a proponer")
    void unaPreguntaQueYaSeContestoNoSeVuelveAProponer() {
        // La segunda pasada ve los mismos hechos y escribe casi lo mismo. Sin esto el
        // facilitador recibiría la lista con una pregunta que él ya hizo esa mañana.
        PreguntaGenerada contestada = pregunta(1L, "¿Qué pasó en esos ocho minutos?", 1,
                "Dije que no lo vi venir");
        montar(List.of(contestada), List.of());

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("¿Qué pasó en esos ocho minutos?", "Un motivo", null),
                new PreguntaIa("Una que no se hizo todavía", "Otro motivo", null))));

        ArgumentCaptor<PreguntaGenerada> guardada =
                ArgumentCaptor.forClass(PreguntaGenerada.class);
        verify(preguntas, times(1)).save(guardada.capture());
        assertThat(guardada.getValue().getTexto()).isEqualTo("Una que no se hizo todavía");
    }

    @Test
    @DisplayName("dos preguntas iguales en la misma tanda se guardan una sola vez")
    void dosPreguntasIgualesEnLaMismaTandaSeGuardanUnaSolaVez() {
        // Le pasa al modelo cuando dos hechos apuntan al mismo sitio. Repetirla gastaría dos
        // de los cinco huecos de la conversación en la misma respuesta.
        montar(List.of(), List.of());

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("¿Por qué esperaste?", "Sale de la línea de tiempo", null),
                new PreguntaIa("  ¿Por qué esperaste?  ", "Sale de una alerta", null))));

        verify(preguntas, times(1)).save(any(PreguntaGenerada.class));
    }

    @Test
    @DisplayName("con cuatro ya contestadas solo cabe una nueva")
    void conCuatroYaContestadasSoloCabeUnaNueva() {
        // El tope de cinco cuenta la conversación entera, no cada tanda. Si contara solo lo
        // nuevo, pedir las preguntas dos veces dejaría una lista de nueve.
        montar(List.of(
                pregunta(1L, "Ya hecha 1", 1, "Contestó"),
                pregunta(2L, "Ya hecha 2", 2, "Contestó"),
                pregunta(3L, "Ya hecha 3", 3, "Contestó"),
                pregunta(4L, "Ya hecha 4", 4, "Contestó")), List.of());

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("Nueva 1", "Un motivo", null),
                new PreguntaIa("Nueva 2", "Otro motivo", null),
                new PreguntaIa("Nueva 3", "Y otro", null))));

        ArgumentCaptor<PreguntaGenerada> guardada =
                ArgumentCaptor.forClass(PreguntaGenerada.class);
        verify(preguntas, times(1)).save(guardada.capture());
        assertThat(guardada.getValue().getTexto()).isEqualTo("Nueva 1");
        assertThat(guardada.getValue().getOrden())
                .as("se numera detrás de las cuatro que ya se hicieron")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("con las cinco ya contestadas no cabe ninguna nueva, y ninguna se pierde")
    void conLasCincoYaContestadasNoCabeNingunaNueva() {
        montar(List.of(
                pregunta(1L, "Ya hecha 1", 1, "Contestó"),
                pregunta(2L, "Ya hecha 2", 2, "Contestó"),
                pregunta(3L, "Ya hecha 3", 3, "Contestó"),
                pregunta(4L, "Ya hecha 4", 4, "Contestó"),
                pregunta(5L, "Ya hecha 5", 5, "Contestó")), List.of());

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("Nueva", "Un motivo", null))));

        verify(preguntas, never()).save(any());
        verify(preguntas, never()).delete(any());
    }

    // ============ Apoyo ============

    /** Deja en pie la postulación y su puesto; cada test pone encima su propia fuente. */
    private void montarInsumo() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion()));
        when(contexto.puestoDe(any(Postulacion.class))).thenReturn(puesto());
    }

    /** Una fuente cualquiera, para que el insumo no salte por «no hay nada que preguntar». */
    private void conAlgoQuePreguntar() {
        when(alertas.findByPostulacionId(POSTULACION)).thenReturn(List.of(alerta(9L)));
    }

    /**
     * Una hora de esa mañana, en la zona de quien corre el test.
     *
     * <p>Se construye desde la hora local a propósito: el puente formatea con
     * {@code ZoneId.systemDefault()}, así que así el reloj esperado sale igual en cualquier
     * máquina sin tener que repetir el formateo dentro de la afirmación.
     */
    private static Instant esaManana(int hora, int minuto) {
        return LocalDate.of(2026, 8, 20).atTime(hora, minuto)
                .atZone(ZoneId.systemDefault()).toInstant();
    }

    private void montar(List<PreguntaGenerada> yaEstan, List<Alerta> suyas) {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion()));
        when(preguntas.findByPostulacionIdOrderByOrden(POSTULACION)).thenReturn(yaEstan);
        when(alertas.findByPostulacionId(POSTULACION)).thenReturn(suyas);
    }

    private Postulacion postulacion() {
        return Postulacion.builder()
                .id(POSTULACION).organizacionId(1L).vacanteId(7L)
                .estadoCodigo("SIMULACION_POR_CONFIRMAR")
                .build();
    }

    private Puesto puesto() {
        return Puesto.builder()
                .id(3L).nombre("Analista de procesos").nivelPuestoCodigo("OPERATIVO")
                .build();
    }

    private Alerta alerta(Long id) {
        return Alerta.builder()
                .id(id).postulacionId(POSTULACION).tipo("CONTRADICCION")
                .descripcion("Dice que avisa pronto")
                .build();
    }

    private PreguntaGenerada pregunta(Long id, String texto, int orden, String respuesta) {
        return PreguntaGenerada.builder()
                .id(id).postulacionId(POSTULACION).texto(texto).orden(orden)
                .respuesta(respuesta)
                .build();
    }

    private HallazgoPerfil hallazgo(String tipo, String descripcion, String evidencia) {
        return HallazgoPerfil.builder()
                .id(1L).perfilTalentoId(11L).tipo(tipo).descripcion(descripcion)
                .evidencia(evidencia)
                .build();
    }

    private AfirmacionCv afirmacion(String texto, String clasificacion) {
        return AfirmacionCv.builder()
                .cvId(4L).texto(texto).clasificacion(clasificacion)
                .build();
    }

    private Criterio criterio(Long id, String nombre) {
        return Criterio.builder()
                .id(id).nombre(nombre).etapaCodigo("SIMULACION").orden(id.intValue())
                .build();
    }

    private NotaCriterio nota(Long criterioId, BigDecimal puntaje, String explicacion) {
        return NotaCriterio.builder()
                .postulacionId(POSTULACION).criterioId(criterioId).puntaje(puntaje)
                .explicacion(explicacion)
                .build();
    }

    private InscripcionSesion inscripcion(Long id, Long sesionId) {
        return InscripcionSesion.builder()
                .id(id).postulacionId(POSTULACION).sesionSimulacionId(sesionId).esVigente(true)
                .build();
    }

    private MarcaTiempoSimulacion marca(String evento, Instant ocurridaEn) {
        return MarcaTiempoSimulacion.builder()
                .inscripcionSesionId(30L).evento(evento).ocurridaEn(ocurridaEn)
                .build();
    }

    private IntentoPrueba intento(Long id) {
        return IntentoPrueba.builder()
                .id(id).postulacionId(POSTULACION).entregadoEn(esaManana(9, 0))
                .build();
    }

    private RespuestaPrueba respuesta(Long preguntaPruebaId, String texto) {
        return RespuestaPrueba.builder()
                .intentoPruebaId(21L).preguntaPruebaId(preguntaPruebaId).texto(texto)
                .build();
    }

    private PreguntaPrueba preguntaDePrueba(Long id, String enunciado) {
        return PreguntaPrueba.builder()
                .id(id).enunciado(enunciado).tipo("PREVIA")
                .build();
    }
}
