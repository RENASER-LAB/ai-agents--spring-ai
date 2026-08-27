package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoDimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La nota de etapa de un banco CRITERIOS: los puntajes por pregunta, agregados por pilar
 * y ponderados con los pesos de la vacante.
 *
 * <p>Es el equivalente de {@code guardarNota} en {@code ServicioCalificacionImpl}, para el
 * otro motor: allí la nota sale de puntuar lo cerrado contra su clave; aquí sale de las
 * notas que el EVALUADOR dejó en {@code nota_respuesta}, y por eso corre <b>después</b> del
 * agente, no al entregar el examen.
 *
 * <p>Dos reglas heredadas que aquí también mandan:
 * <ul>
 *   <li><b>media rúbrica no es una nota</b>: si a alguna pregunta puntuable le falta su
 *       calificación, no se escribe nada y se deja dicho en el log — el reintento de la cola
 *       completará lo que falte;</li>
 *   <li><b>los pesos se leen de {@code peso_dimension} filtrando por pilar</b>, nunca
 *       sumando notas sin filtrar: la lección del 675 sobre 100.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalificacionCriterios {

    /** El discriminador que la versión del banco declara. */
    public static final String METODO = "CRITERIOS";

    private static final String ETAPA = "PERFIL_INTEGRAL";
    private static final String PREFIJO_PILAR = "PIL_";

    private final EvaluacionRepository evaluaciones;
    private final VersionBancoRepository versionesBanco;
    private final RespuestaRepository respuestas;
    private final PreguntaRepository preguntas;
    private final PreguntaDimensionRepository preguntaDimensiones;
    private final NotaRespuestaRepository notasRespuesta;
    private final PesoDimensionRepository pesosDimension;
    private final NotaEtapaRepository notasEtapa;
    private final VacanteRepository vacantes;

    /** El método de calificación del banco con que rindió esta postulación, o null. */
    public String metodoDe(Postulacion postulacion) {
        if (postulacion.getEvaluacionId() == null) {
            return null;
        }
        return evaluaciones.findById(postulacion.getEvaluacionId())
                .map(Evaluacion::getVersionBancoNivelId)
                .flatMap(versionesBanco::findById)
                .map(VersionBanco::getMetodoCalificacion)
                .orElse(null);
    }

    /**
     * Calcula y guarda la nota de la etapa, si ya están todas las calificaciones.
     * No hace nada —y lo dice en el log— si el banco no es CRITERIOS o falta alguna nota.
     */
    @Transactional
    public void calificarEtapa(Postulacion postulacion) {
        Evaluacion evaluacion = postulacion.getEvaluacionId() == null ? null
                : evaluaciones.findById(postulacion.getEvaluacionId()).orElse(null);
        VersionBanco banco = evaluacion == null ? null
                : versionesBanco.findById(evaluacion.getVersionBancoNivelId()).orElse(null);
        if (banco == null || !METODO.equals(banco.getMetodoCalificacion())) {
            return;
        }

        List<Pregunta> puntuables = preguntas
                .findByVersionBancoIdOrderByOrden(banco.getId()).stream()
                .filter(Pregunta::isEsPuntuable)
                .toList();
        List<Long> preguntaIds = puntuables.stream().map(Pregunta::getId).toList();

        Map<Long, Respuesta> respuestaPorPregunta = respuestas
                .findByEvaluacionId(evaluacion.getId()).stream()
                .collect(Collectors.toMap(Respuesta::getPreguntaId, Function.identity(),
                        (a, b) -> a));
        Map<Long, NotaRespuesta> notaPorRespuesta = notasRespuesta
                .findByRespuestaIdIn(respuestaPorPregunta.values().stream()
                        .map(Respuesta::getId).toList()).stream()
                .collect(Collectors.toMap(NotaRespuesta::getRespuestaId, Function.identity()));

        // El pilar de cada pregunta. Solo cuentan las dimensiones PIL_*: una pregunta puede
        // medir además otras cosas sin que eso mueva el índice.
        Map<Long, String> pilarPorPregunta = preguntaDimensiones
                .findByPreguntaIdIn(preguntaIds).stream()
                .filter(pd -> pd.getDimensionCodigo().startsWith(PREFIJO_PILAR))
                .collect(Collectors.toMap(PreguntaDimension::getPreguntaId,
                        PreguntaDimension::getDimensionCodigo, (a, b) -> a));

        // Media rúbrica no es una nota: sin todas las calificaciones no se escribe etapa.
        Map<String, BigDecimal> obtenidosPorPilar = new HashMap<>();
        Map<String, Integer> pesosPorPilar = new HashMap<>();
        for (Pregunta p : puntuables) {
            Respuesta r = respuestaPorPregunta.get(p.getId());
            NotaRespuesta nota = r == null ? null : notaPorRespuesta.get(r.getId());
            if (nota == null || nota.getPuntaje() == null) {
                log.info("CRITERIOS: a {} le falta su calificación en la postulación {}; la "
                        + "nota de etapa espera a que estén todas", p.getCodigo(),
                        postulacion.getId());
                return;
            }
            String pilar = pilarPorPregunta.get(p.getId());
            if (pilar == null) {
                log.warn("CRITERIOS: la pregunta {} no tiene pilar; no puede entrar al índice",
                        p.getCodigo());
                return;
            }
            int peso = p.getPeso() == null ? 1 : p.getPeso();
            // En BigDecimal: un ajuste humano puede traer decimales (2.5) y truncarlos
            // perdería media unidad del pilar en silencio.
            obtenidosPorPilar.merge(pilar,
                    nota.getPuntaje().multiply(BigDecimal.valueOf(peso)), BigDecimal::add);
            pesosPorPilar.merge(pilar, peso, Integer::sum);
        }

        Vacante vacante = vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException(
                        "La vacante de esta postulación ya no existe"));
        Map<String, BigDecimal> pesoDelIndice = pesosDimension
                .findByVersionPesosId(vacante.getVersionPesosId()).stream()
                .filter(pd -> banco.getNivelPuestoCodigo().equals(pd.getNivelPuestoCodigo()))
                .filter(pd -> pd.getDimensionCodigo().startsWith(PREFIJO_PILAR))
                .collect(Collectors.toMap(PesoDimension::getDimensionCodigo,
                        PesoDimension::getPeso));
        if (pesoDelIndice.isEmpty()) {
            // Vacante con pesos de otro instrumento: mejor sin nota que con una inventada.
            log.warn("CRITERIOS: la versión de pesos {} no trae pesos de pilar para {}; la "
                    + "postulación {} queda sin nota de etapa", vacante.getVersionPesosId(),
                    banco.getNivelPuestoCodigo(), postulacion.getId());
            return;
        }

        Map<String, BigDecimal> puntajePorPilar = new HashMap<>();
        for (Map.Entry<String, Integer> pilar : pesosPorPilar.entrySet()) {
            puntajePorPilar.put(pilar.getKey(), FormulasCazatalentos.puntajePilar(
                    obtenidosPorPilar.getOrDefault(pilar.getKey(), BigDecimal.ZERO),
                    pilar.getValue()));
        }
        // Integridad se puntúa pero no pondera: es eliminatoria. Al índice entran solo los
        // pilares que la versión de pesos declara.
        BigDecimal indice = FormulasCazatalentos.indice(puntajePorPilar, pesoDelIndice);

        NotaEtapa fila = notasEtapa
                .findByPostulacionIdAndEtapaCodigo(postulacion.getId(), ETAPA)
                .orElseGet(() -> NotaEtapa.builder()
                        .postulacionId(postulacion.getId())
                        .etapaCodigo(ETAPA)
                        .creadoEn(Instant.now())
                        .build());
        fila.setPuntaje(indice);
        fila.setVersionPesosId(vacante.getVersionPesosId());
        fila.setCalculadaEn(Instant.now());
        notasEtapa.save(fila);
        log.info("CRITERIOS: índice {} para la postulación {} ({} preguntas, pesos {})",
                indice, postulacion.getId(), puntuables.size(), vacante.getVersionPesosId());
    }
}
