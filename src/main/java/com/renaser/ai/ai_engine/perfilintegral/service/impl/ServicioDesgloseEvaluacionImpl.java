package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.AlineacionVista;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DesgloseEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RespuestaAbiertaVista;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.ResumenCerradas;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ResultadoAlineacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion.ResumenCerrado;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioDesgloseEvaluacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioDesgloseEvaluacionImpl implements ServicioDesgloseEvaluacion {

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final EvaluacionRepository evaluaciones;
    private final RespuestaRepository respuestas;
    private final PreguntaRepository preguntas;
    private final NotaRespuestaRepository notasRespuesta;
    private final ResultadoAlineacionRepository alineaciones;
    private final ServicioCalificacion calificacion;
    private final Permisos permisos;
    private final CalificacionCriterios calificacionCriterios;
    private final com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository notasEtapa;

    @Override
    @Transactional(readOnly = true)
    public DesgloseEvaluacion ver(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId);

        // Sin evaluación asignada no hay nada que abrir, y no es un error: la vacante
        // pudo publicarse con la evaluación del banco apagada.
        if (postulacion.getEvaluacionId() == null) {
            return new DesgloseEvaluacion(postulacionId, null, null, null,
                    new ResumenCerradas(BigDecimal.ZERO, 0), List.of(), List.of());
        }

        Evaluacion evaluacion = evaluaciones.findById(postulacion.getEvaluacionId()).orElse(null);
        List<RespuestaAbiertaVista> abiertas = abiertasDe(postulacion.getEvaluacionId());
        ResumenCerrado cerrado = calificacion.resumenDeLoCerrado(postulacionId);

        return new DesgloseEvaluacion(
                postulacionId,
                evaluacion == null ? null : evaluacion.getEstado(),
                evaluacion == null ? null : evaluacion.getTerminadaEn(),
                notaDe(postulacion, cerrado, abiertas),
                new ResumenCerradas(cerrado.nota(), cerrado.preguntas()),
                abiertas,
                alineaciones.findByEvaluacionId(postulacion.getEvaluacionId()).stream()
                        .map(a -> new AlineacionVista(a.getBloque(), a.getSemaforo(),
                                a.getExplicacion()))
                        .toList());
    }

    /**
     * Las respuestas abiertas con su nota, leídas de lo persistido.
     *
     * <p>No recalcula nada: enseña exactamente lo que la IA guardó al calificar (y el
     * ajuste humano si lo hubo). Una respuesta todavía sin nota sale con los campos de
     * nota vacíos — está respondida y pendiente, que es distinto de no estar.
     */
    private List<RespuestaAbiertaVista> abiertasDe(Long evaluacionId) {
        // Orden por id de respuesta: el de contestarlas. Sin ordenar, Postgres
        // devuelve orden de heap y dos aperturas pintan la lista distinta.
        List<Respuesta> suyas = respuestas.findByEvaluacionId(evaluacionId).stream()
                .filter(r -> r.getOpcionId() == null && r.getTexto() != null
                        && !r.getTexto().isBlank())
                .sorted(java.util.Comparator.comparing(Respuesta::getId))
                .toList();
        if (suyas.isEmpty()) {
            return List.of();
        }

        Map<Long, Pregunta> preguntaPorId = preguntas
                .findByIdIn(suyas.stream().map(Respuesta::getPreguntaId).toList()).stream()
                .collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        Map<Long, NotaRespuesta> notaPorRespuesta = notasRespuesta
                .findByRespuestaIdIn(suyas.stream().map(Respuesta::getId).toList()).stream()
                .collect(Collectors.toMap(NotaRespuesta::getRespuestaId, Function.identity()));

        return suyas.stream()
                .filter(r -> {
                    Pregunta p = preguntaPorId.get(r.getPreguntaId());
                    return p != null && p.isEsPuntuable();
                })
                .map(r -> {
                    Pregunta p = preguntaPorId.get(r.getPreguntaId());
                    NotaRespuesta n = notaPorRespuesta.get(r.getId());
                    return new RespuestaAbiertaVista(
                            p.getEnunciado(),
                            p.getTipo(),
                            r.getTexto(),
                            n == null ? null : n.getPuntaje(),
                            n == null ? null : n.getExplicacion(),
                            n == null ? null : n.getEvidenciaCitada(),
                            n == null ? null : n.getConfianza(),
                            n == null ? null : n.getMotivoAjuste());
                })
                .toList();
    }

    /**
     * La nota que se enseña arriba del desglose.
     *
     * <p>En un banco CRITERIOS es <b>el índice de la etapa</b> —pilares ponderados, el mismo
     * número con el que se ranquea—, leído de {@code nota_etapa}. Promediar los 0–4 a partes
     * iguales aquí enseñaría una nota distinta de la que decide, con los pesos de ítem y de
     * pilar perdidos. Vacía mientras el evaluador no haya terminado.
     *
     * <p>En el resto, la cuenta de siempre: vive en
     * {@link ServicioCalificacion#notaCombinada}, una sola para todos.
     */
    private BigDecimal notaDe(Postulacion postulacion, ResumenCerrado cerrado,
                              List<RespuestaAbiertaVista> abiertas) {
        if (CalificacionCriterios.METODO.equals(calificacionCriterios.metodoDe(postulacion))) {
            return notasEtapa
                    .findByPostulacionIdAndEtapaCodigo(postulacion.getId(), "PERFIL_INTEGRAL")
                    .map(com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa::getPuntaje)
                    .orElse(null);
        }
        return ServicioCalificacion.notaCombinada(cerrado,
                abiertas.stream().map(RespuestaAbiertaVista::puntaje).toList());
    }

    /**
     * El mismo control de visibilidad que el resto del panel: organización y alcance.
     *
     * <p>El alcance se pide de {@code ver_respuestas_evaluacion}, el mismo permiso que guarda
     * la ruta: si un rol lo tiene acotado a sus vacantes, aquí también.
     */
    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId) {
        Postulacion p = postulaciones.findByIdAndOrganizacionId(postulacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        FiltroAlcance alcance = permisos.alcanceDe("ver_respuestas_evaluacion");
        if (alcance.tipo() == FiltroAlcance.Tipo.SUS_VACANTES) {
            boolean esSuya = vacantes.findById(p.getVacanteId())
                    .map(v -> quien.usuarioId().equals(v.getResponsableUsuarioId()))
                    .orElse(false);
            if (!esSuya) {
                throw new ResourceNotFoundException("Postulación", "id", postulacionId);
            }
        }
        return p;
    }
}
