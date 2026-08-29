package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.FormulasCazatalentos;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La nota de la etapa técnica cuando la vacante rinde el cuestionario CAZATALENTOS.
 *
 * <p>Es el hermano de {@link CalificacionCriterios} para la etapa 2, y la fórmula del método
 * es más simple que la de la etapa 1:
 *
 * <pre>Índice técnico = puntos obtenidos ÷ (4 × nº de preguntas) × 100</pre>
 *
 * <p><b>Sin pilares.</b> El banco por nivel reparte sus preguntas en los siete pilares y los
 * pondera; un cuestionario de vacante no: sus doce preguntas hablan todas del mismo puesto y
 * pesan igual. Por eso aquí no se lee {@code peso_dimension} ni hace falta que el REDACTOR
 * escriba filas de {@code pregunta_dimension} — inventarlas sería fabricar una estructura
 * para satisfacer a un lector que no existe.
 *
 * <p>⚠️ <b>Y por eso es una clase aparte y no un parámetro de la otra.</b> El filtro por pilar
 * es la invariante entera de {@code CalificacionCriterios} —la que evitó el «675 sobre 100»—
 * y hoy decide notas de gente real. Pero hay una diferencia de fondo, no solo de forma:
 *
 * <dl>
 *   <dt>Allí, una respuesta sin calificar detiene la nota</dt>
 *   <dd>porque el examen se entrega completo o no se entrega.</dd>
 *   <dt>Aquí, una pregunta <b>sin responder</b> vale cero y sigue contando en el
 *       denominador</dt>
 *   <dd>porque el reloj entrega lo que haya: no contestar es un cero, no media prueba.</dd>
 * </dl>
 *
 * <p>Lo que sí se comparte es la regla de esperar: si hay respuesta pero todavía no tiene
 * calificación, no se escribe nada y la cola reintenta. Media rúbrica nunca es una nota.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalificacionCuestionarioTecnico {

    /** La etapa que ocupa el cuestionario técnico: la misma que la prueba del puesto. */
    public static final String ETAPA = "PRUEBA_PUESTO";

    private final EvaluacionRepository evaluaciones;
    private final VersionBancoRepository versionesBanco;
    private final PreguntaRepository preguntas;
    private final RespuestaRepository respuestas;
    private final NotaRespuestaRepository notasRespuesta;
    private final NotaEtapaRepository notasEtapa;
    private final VacanteRepository vacantes;

    /**
     * Escribe la nota de la etapa técnica de esta postulación, si ya se puede.
     *
     * <p>No hace nada cuando la vacante no rinde el cuestionario técnico: esa etapa la
     * califica {@code ServicioCalificacionPruebaImpl} con su rúbrica, y dos servicios
     * escribiendo la misma fila se pisarían.
     */
    @Transactional
    public void calificarEtapa(Postulacion postulacion) {
        if (postulacion.getEvaluacionTecnicaId() == null) {
            return;
        }
        Vacante vacante = vacantes.findById(postulacion.getVacanteId()).orElse(null);
        if (vacante == null) {
            return;
        }
        Evaluacion evaluacion = evaluaciones.findById(postulacion.getEvaluacionTecnicaId())
                .orElse(null);
        VersionBanco cuestionario = evaluacion == null ? null
                : versionesBanco.findById(evaluacion.getVersionBancoNivelId()).orElse(null);
        if (cuestionario == null) {
            return;
        }

        // `es_puntuable` ya deja fuera la muestra de trabajo PRESENCIAL: nunca se le envió al
        // candidato, así que no puede contar ni en el numerador ni en el denominador.
        List<Pregunta> puntuables = preguntas
                .findByVersionBancoIdOrderByOrden(cuestionario.getId()).stream()
                .filter(Pregunta::isEsPuntuable)
                .toList();
        if (puntuables.isEmpty()) {
            log.warn("El cuestionario técnico {} no tiene preguntas puntuables: la postulación "
                    + "{} queda sin nota de etapa", cuestionario.getId(), postulacion.getId());
            return;
        }

        Map<Long, Respuesta> respuestaPorPregunta = respuestas
                .findByEvaluacionId(evaluacion.getId()).stream()
                .collect(Collectors.toMap(Respuesta::getPreguntaId, Function.identity(),
                        (a, b) -> a));
        Map<Long, NotaRespuesta> notaPorRespuesta = notasRespuesta
                .findByRespuestaIdIn(respuestaPorPregunta.values().stream()
                        .map(Respuesta::getId).toList()).stream()
                .collect(Collectors.toMap(NotaRespuesta::getRespuestaId, Function.identity()));

        BigDecimal obtenidos = BigDecimal.ZERO;
        int sumaDePesos = 0;
        for (Pregunta p : puntuables) {
            int peso = p.getPeso() == null ? 1 : p.getPeso();
            sumaDePesos += peso;

            Respuesta r = respuestaPorPregunta.get(p.getId());
            if (r == null) {
                // Se entregó sin contestar esta. Cuenta cero y su peso sigue en el
                // denominador: dejarla fuera subiría la nota por no haber trabajado.
                continue;
            }
            NotaRespuesta nota = notaPorRespuesta.get(r.getId());
            if (nota == null || nota.getPuntaje() == null) {
                log.info("TÉCNICO: a {} le falta su calificación en la postulación {}; la nota "
                        + "de etapa espera a que estén todas", p.getCodigo(), postulacion.getId());
                return;
            }
            obtenidos = obtenidos.add(nota.getPuntaje().multiply(BigDecimal.valueOf(peso)));
        }

        // La misma fórmula que un pilar del banco, con un solo grupo: obtenidos ÷ (4 × Σpesos)
        // × 100. Con todos los pesos a 1 —como los escribe el REDACTOR— es exactamente el
        // «puntos ÷ (4 × nº de preguntas) × 100» del documento de la clienta.
        BigDecimal indice = FormulasCazatalentos.puntajePilar(obtenidos, sumaDePesos);

        NotaEtapa fila = notasEtapa
                .findByPostulacionIdAndEtapaCodigo(postulacion.getId(), ETAPA)
                .orElseGet(() -> NotaEtapa.builder()
                        .postulacionId(postulacion.getId())
                        .etapaCodigo(ETAPA)
                        .creadoEn(Instant.now())
                        .build());
        fila.setPuntaje(indice);
        // La versión de pesos de la vacante, no la última publicada: una decisión vieja se
        // tiene que poder reconstruir tal como se tomó (RF-114).
        fila.setVersionPesosId(vacante.getVersionPesosId());
        fila.setCalculadaEn(Instant.now());
        notasEtapa.save(fila);
        log.info("TÉCNICO: índice {} para la postulación {} ({} preguntas, pesos {})",
                indice, postulacion.getId(), puntuables.size(), vacante.getVersionPesosId());
    }
}
