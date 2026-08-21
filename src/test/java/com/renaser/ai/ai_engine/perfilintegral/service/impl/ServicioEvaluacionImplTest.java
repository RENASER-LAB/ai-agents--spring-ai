package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.EvaluacionCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.PreguntaCandidato;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.OrdenPregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OrdenPreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que ve el candidato cuando vuelve a entrar a su examen.
 *
 * <p>El banco v3 son 190 ítems y 162 de ellos no se responden con una sola opción: se responden
 * con un detalle —un SJT-R califica cada opción, un SEC ordena sus pasos—. Ese detalle se
 * guardaba bien pero no volvía al leer, así que quien respondía cuarenta preguntas y volvía al
 * día siguiente las veía todas en blanco aunque estuvieran guardadas.
 *
 * <p>Aquí se comprueban las dos caras de eso: que su respuesta vuelve, y que <b>solo vuelve la
 * suya</b>. La columna es {@code jsonb} y admite cualquier cosa; si un puntaje llegara al
 * navegador, el banco entero quedaría inutilizado (RF-53).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La evaluación que el candidato retoma")
class ServicioEvaluacionImplTest {

    @Mock private EvaluacionRepository evaluaciones;
    @Mock private PlantillaEvaluacionRepository plantillas;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRepository opciones;
    @Mock private OrdenPreguntaRepository ordenes;
    @Mock private RespuestaRepository respuestas;
    @Mock private PostulacionRepository postulaciones;
    @Mock private MaquinaEstados maquina;
    @Mock private ServicioCalificacion calificacion;
    @Mock private ColaCalificacionIa colaIa;
    @Mock private ServicioParametros parametros;

    @InjectMocks
    private ServicioEvaluacionImpl servicio;

    private static final UUID CODIGO = UUID.randomUUID();
    private static final ContextoUsuario CANDIDATA =
            new ContextoUsuario(9L, 9L, 1L, "CANDIDATO", List.of(), Map.of());

    @Test
    @DisplayName("Lo que respondió con detalle vuelve tal cual, para poder retomar")
    void elDetalleGuardadoVuelve() {
        prepararExamen(
                List.of(Pregunta.builder().id(1L).tipo("SJT-R").enunciado("¿Qué haces?").build()),
                List.of(Respuesta.builder().id(70L).evaluacionId(60L).preguntaId(1L)
                        .detalle("{\"calificaciones\":{\"11\":5,\"12\":2}}").build()));

        PreguntaCandidato pregunta = servicio.ver(CANDIDATA, CODIGO).preguntas().get(0);

        assertThat(pregunta.respuestaDetalle()).containsOnlyKeys("calificaciones");
        assertThat(pregunta.respuestaDetalle().get("calificaciones"))
                .isEqualTo(Map.of("11", 5, "12", 2));
    }

    @Test
    @DisplayName("Cada formato devuelve lo suyo: el orden de un SEC, las marcas de un INV")
    void cadaFormatoDevuelveLoSuyo() {
        prepararExamen(
                List.of(Pregunta.builder().id(1L).tipo("SEC").enunciado("Ordena").build(),
                        Pregunta.builder().id(2L).tipo("INV").enunciado("Marca").build()),
                List.of(Respuesta.builder().id(70L).evaluacionId(60L).preguntaId(1L)
                                .detalle("{\"orden\":[14,12,13]}").build(),
                        Respuesta.builder().id(71L).evaluacionId(60L).preguntaId(2L)
                                .detalle("{\"marcadas\":[21,23]}").build()));

        List<PreguntaCandidato> vistas = servicio.ver(CANDIDATA, CODIGO).preguntas();

        assertThat(vistas.get(0).respuestaDetalle()).containsEntry("orden", List.of(14, 12, 13));
        assertThat(vistas.get(1).respuestaDetalle()).containsEntry("marcadas", List.of(21, 23));
    }

    @Test
    @DisplayName("Una pregunta sin detalle vuelve en nulo, no con un mapa vacío")
    void sinDetalleEsNuloYNoUnMapaVacio() {
        prepararExamen(
                List.of(Pregunta.builder().id(1L).tipo("SJT").enunciado("Elige").build(),
                        Pregunta.builder().id(2L).tipo("SEC").enunciado("Ordena").build()),
                // La primera respondida con opción —no lleva detalle— y la segunda sin responder
                List.of(Respuesta.builder().id(70L).evaluacionId(60L).preguntaId(1L)
                        .opcionId(11L).build()));

        List<PreguntaCandidato> vistas = servicio.ver(CANDIDATA, CODIGO).preguntas();

        // Un mapa vacío en el portal se leería como «esta ya la respondí», y no la respondió.
        assertThat(vistas.get(0).respuestaDetalle()).isNull();
        assertThat(vistas.get(0).respuestaOpcionId()).isEqualTo(11L);
        assertThat(vistas.get(1).respuestaDetalle()).isNull();
        assertThat(vistas.get(1).respuestaOpcionId()).isNull();
    }

    @Test
    @DisplayName("Ningún puntaje se cuela por el detalle, aunque esté guardado en él")
    void ningunPuntajeSeCuela() {
        // El peor caso: alguien guardó en esa columna cosas que el candidato nunca escribió.
        // Es jsonb, así que la base lo admite sin protestar. Al salir se filtra por formato.
        prepararExamen(
                List.of(Pregunta.builder().id(1L).tipo("SEC").enunciado("Ordena").build()),
                List.of(Respuesta.builder().id(70L).evaluacionId(60L).preguntaId(1L)
                        .detalle("""
                                {"orden":[14,12,13],"puntaje":8.5,"ordenCorrecto":[12,13,14],
                                 "valor":3,"esDistractor":true,"logicaInterna":"VEL vs CRI"}""")
                        .build()));

        Map<String, Object> devuelto = servicio.ver(CANDIDATA, CODIGO).preguntas()
                .get(0).respuestaDetalle();

        assertThat(devuelto).containsOnlyKeys("orden");
        assertThat(devuelto.toString())
                .doesNotContain("puntaje", "ordenCorrecto", "valor", "esDistractor", "VEL vs");
    }

    @Test
    @DisplayName("Un detalle ilegible no le tumba el examen: esa pregunta vuelve sin él")
    void unDetalleIlegibleNoTumbaElExamen() {
        prepararExamen(
                List.of(Pregunta.builder().id(1L).tipo("SEC").enunciado("Ordena").build()),
                List.of(Respuesta.builder().id(70L).evaluacionId(60L).preguntaId(1L)
                        .detalle("esto no es json").build()));

        assertThat(servicio.ver(CANDIDATA, CODIGO).preguntas().get(0).respuestaDetalle()).isNull();
    }

    @Test
    @DisplayName("Las 190 respuestas se leen de una vez, no una por pregunta")
    void lasRespuestasSeLeenEnBloque() {
        List<Pregunta> banco = new ArrayList<>();
        List<Respuesta> dadas = new ArrayList<>();
        for (long i = 1; i <= 190; i++) {
            banco.add(Pregunta.builder().id(i).tipo("SEC").enunciado("Ordena " + i).build());
            dadas.add(Respuesta.builder().id(1000 + i).evaluacionId(60L).preguntaId(i)
                    .detalle("{\"orden\":[14,12,13]}").build());
        }
        prepararExamen(banco, dadas);

        EvaluacionCandidato vista = servicio.ver(CANDIDATA, CODIGO);

        assertThat(vista.total()).isEqualTo(190);
        assertThat(vista.respondidas()).isEqualTo(190);
        // Una sola consulta para las 190: si fuera una por pregunta, un examen completo
        // costaría 190 viajes a la base cada vez que el candidato refresca la pantalla.
        verify(respuestas, times(1)).findByEvaluacionId(60L);
        verify(preguntas, times(1)).findByIdIn(anyList());
        verify(opciones, times(1)).findByPreguntaIdIn(anyList());
    }

    @Test
    @DisplayName("Un caso descompuesto dice cuántos campos pide; los demás formatos, nada")
    void elCasoDescompuestoDiceCuantosCamposPide() {
        // O03 en la base real pide 6 campos y O21 pide 4. Sin este dato el portal tenía que
        // sacarlo del «(6 campos)» del enunciado, y donde no estaba escrito pintaba un solo
        // cuadro de texto para seis datos distintos.
        prepararExamen(
                List.of(Pregunta.builder().id(1L).tipo("CD").casosPedidos((short) 6)
                                .enunciado("Tu tarea principal. (6 campos)").build(),
                        Pregunta.builder().id(2L).tipo("CD").casosPedidos((short) 4)
                                .enunciado("Un problema con un compañero. (4 campos)").build(),
                        Pregunta.builder().id(3L).tipo("SEC").enunciado("Ordena").build()),
                List.of());

        List<PreguntaCandidato> vistas = servicio.ver(CANDIDATA, CODIGO).preguntas();

        assertThat(vistas.get(0).casosPedidos()).isEqualTo((short) 6);
        assertThat(vistas.get(1).casosPedidos()).isEqualTo((short) 4);
        // Un SEC no tiene campos que llenar: mandar un número aquí haría que el portal
        // pintara casillas en una pregunta que se responde ordenando pasos.
        assertThat(vistas.get(2).casosPedidos()).isNull();
    }

    @Test
    @DisplayName("Los ítems V no traen número de campos: en la base viene vacío y vuelve nulo")
    void losItemsVNoTraenNumeroDeCampos() {
        // Los seis V del banco operativo tienen casos_pedidos en nulo. El portal parte esos
        // ítems por el «·» de su enunciado, no por una cuenta que nadie le da.
        prepararExamen(
                List.of(Pregunta.builder().id(1L).tipo("V")
                        .enunciado("Años haciendo este trabajo: ___ · En cuántas empresas: ___")
                        .build()),
                List.of());

        assertThat(servicio.ver(CANDIDATA, CODIGO).preguntas().get(0).casosPedidos()).isNull();
    }

    // ============ Apoyo ============

    /** Una evaluación empezada, con su orden congelado y las respuestas que ya dio. */
    private void prepararExamen(List<Pregunta> banco, List<Respuesta> dadas) {
        when(postulaciones.findByUuid(CODIGO)).thenReturn(Optional.of(
                Postulacion.builder().id(50L).uuid(CODIGO).usuarioId(9L).evaluacionId(60L).build()));
        when(evaluaciones.findById(60L)).thenReturn(Optional.of(
                Evaluacion.builder().id(60L).usuarioId(9L).estado("EN_CURSO")
                        .plantillaEvaluacionId(3L).build()));
        when(plantillas.findById(3L)).thenReturn(Optional.of(
                PlantillaEvaluacion.builder().id(3L).minutosObjetivo(90).build()));

        List<OrdenPregunta> orden = new ArrayList<>();
        for (int i = 0; i < banco.size(); i++) {
            orden.add(OrdenPregunta.builder().evaluacionId(60L)
                    .preguntaId(banco.get(i).getId()).posicion(i + 1).build());
        }
        when(ordenes.findByEvaluacionIdOrderByPosicion(60L)).thenReturn(orden);
        when(preguntas.findByIdIn(anyList())).thenReturn(banco);
        when(opciones.findByPreguntaIdIn(anyList())).thenReturn(List.<Opcion>of());
        when(respuestas.findByEvaluacionId(60L)).thenReturn(dadas);
    }
}
