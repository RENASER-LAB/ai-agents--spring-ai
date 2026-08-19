package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.entity.Alerta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.ParConsistencia;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.repository.AlertaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ParConsistenciaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La calificación que hace el código, no el modelo.
 *
 * <p>Dos trabajos distintos:
 *
 * <p><b>Puntuar lo cerrado.</b> Cada opción trae su puntaje de 0 a 4 en una clave versionada.
 * Se suma lo obtenido, se divide entre lo máximo posible y sale una nota sobre 100. Quedan
 * fuera las de estilo, que dibujan un perfil y el cliente prohíbe usar como filtro, y las de
 * consistencia, que generan alertas.
 *
 * <p><b>Detectar contradicciones.</b> Si dos preguntas que miden lo mismo se responden con
 * más diferencia de la tolerada, sale una alerta. <b>Una alerta no descarta a nadie</b>: es
 * una pregunta para la conversación final.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioCalificacionImpl implements ServicioCalificacion {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final PostulacionRepository postulaciones;
    private final EvaluacionRepository evaluaciones;
    private final RespuestaRepository respuestas;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final ParConsistenciaRepository pares;
    private final AlertaRepository alertas;
    private final NotaEtapaRepository notasEtapa;
    private final VersionPesosRepository versionesPesos;
    private final VacanteRepository vacantes;

    @Override
    @Transactional
    public BigDecimal calificarLoCerrado(Long postulacionId) {
        Postulacion postulacion = postulaciones.findById(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        Evaluacion evaluacion = evaluaciones.findById(postulacion.getEvaluacionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Evaluación", "postulación", postulacionId));

        List<Respuesta> suyas = respuestas.findByEvaluacionId(evaluacion.getId());
        Map<Long, Pregunta> porId = preguntas
                .findByIdIn(suyas.stream().map(Respuesta::getPreguntaId).toList()).stream()
                .collect(Collectors.toMap(Pregunta::getId, Function.identity()));

        ResumenCerrado resumen = puntuar(suyas, porId);
        detectarContradicciones(postulacion, evaluacion, suyas, porId);
        guardarNota(postulacion, resumen.nota());
        return resumen.nota();
    }

    /**
     * La misma cuenta, sin guardar nada.
     *
     * <p>Existe para que el Perfil de Talento no tenga que reimplementar la aritmética de la
     * clave: si hubiera dos copias, un día darían resultados distintos y nadie sabría cuál
     * es la buena.
     */
    @Override
    public ResumenCerrado resumenDeLoCerrado(Long postulacionId) {
        Postulacion postulacion = postulaciones.findById(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        if (postulacion.getEvaluacionId() == null) {
            return new ResumenCerrado(BigDecimal.ZERO, 0);
        }
        List<Respuesta> suyas = respuestas.findByEvaluacionId(postulacion.getEvaluacionId());
        if (suyas.isEmpty()) {
            return new ResumenCerrado(BigDecimal.ZERO, 0);
        }
        Map<Long, Pregunta> porId = preguntas
                .findByIdIn(suyas.stream().map(Respuesta::getPreguntaId).toList()).stream()
                .collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        return puntuar(suyas, porId);
    }

    /**
     * La nota de lo cerrado, sobre 100.
     *
     * <p>Se calcula como proporción de lo obtenido sobre lo máximo alcanzable en las preguntas
     * que de verdad puntúan. Así una evaluación de 12 preguntas puntuables y otra de 20 se
     * pueden comparar entre sí.
     */
    private ResumenCerrado puntuar(List<Respuesta> suyas, Map<Long, Pregunta> porId) {
        List<Respuesta> conOpcion = suyas.stream()
                .filter(r -> r.getOpcionId() != null)
                .filter(r -> {
                    Pregunta p = porId.get(r.getPreguntaId());
                    return p != null && p.isEsPuntuable();
                })
                .toList();
        if (conOpcion.isEmpty()) {
            return new ResumenCerrado(BigDecimal.ZERO, 0);
        }

        BigDecimal obtenido = BigDecimal.ZERO;
        BigDecimal maximo = BigDecimal.ZERO;
        for (Respuesta r : conOpcion) {
            Opcion elegida = opciones.findById(r.getOpcionId()).orElse(null);
            if (elegida == null || elegida.getPuntaje() == null) {
                continue;   // sin clave no se puntúa: no se inventa un cero
            }
            obtenido = obtenido.add(elegida.getPuntaje());
            maximo = maximo.add(opciones.findByPreguntaIdOrderByLetra(r.getPreguntaId()).stream()
                    .map(Opcion::getPuntaje)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO));
        }
        if (maximo.compareTo(BigDecimal.ZERO) == 0) {
            return new ResumenCerrado(BigDecimal.ZERO, 0);
        }
        return new ResumenCerrado(obtenido.multiply(CIEN).divide(maximo, 2, RoundingMode.HALF_UP),
                conOpcion.size());
    }

    /**
     * Compara los pares de consistencia y levanta una alerta si se separan demasiado.
     *
     * <p>La diferencia se mide sobre el puntaje de la opción elegida, que es lo que el par
     * está diseñado para comparar. Si a alguna de las dos no respondió, no hay nada que
     * comparar: la ausencia de respuesta no es una contradicción.
     */
    private void detectarContradicciones(Postulacion postulacion, Evaluacion evaluacion,
                                         List<Respuesta> suyas, Map<Long, Pregunta> porId) {
        List<ParConsistencia> configurados = pares.findByVersionBancoId(
                evaluacion.getVersionBancoNivelId());
        if (configurados.isEmpty()) {
            return;   // el cliente todavía no dijo qué preguntas se comparan con cuáles
        }

        Map<Long, BigDecimal> puntajePorPregunta = suyas.stream()
                .filter(r -> r.getOpcionId() != null)
                .collect(Collectors.toMap(Respuesta::getPreguntaId,
                        r -> opciones.findById(r.getOpcionId())
                                .map(Opcion::getPuntaje).orElse(BigDecimal.ZERO),
                        (a, b) -> a));

        for (ParConsistencia par : configurados) {
            BigDecimal a = puntajePorPregunta.get(par.getPreguntaAId());
            BigDecimal b = puntajePorPregunta.get(par.getPreguntaBId());
            if (a == null || b == null) {
                continue;
            }
            BigDecimal diferencia = a.subtract(b).abs();
            if (diferencia.compareTo(par.getDiferenciaMaxima()) <= 0) {
                continue;
            }
            alertas.save(Alerta.builder()
                    .postulacionId(postulacion.getId())
                    .tipo("CONTRADICCION")
                    .descripcion(("Respondió de forma muy distinta a dos preguntas que miden lo "
                            + "mismo: «%s» y «%s». La diferencia fue de %s puntos y lo tolerado "
                            + "es %s. No descarta a nadie: es algo que conviene preguntar.")
                            .formatted(recorte(porId.get(par.getPreguntaAId())),
                                    recorte(porId.get(par.getPreguntaBId())),
                                    diferencia.stripTrailingZeros().toPlainString(),
                                    par.getDiferenciaMaxima().stripTrailingZeros().toPlainString()))
                    .preguntaAId(par.getPreguntaAId())
                    .preguntaBId(par.getPreguntaBId())
                    .creadoEn(Instant.now())
                    .build());
        }
    }

    /**
     * Guarda la nota de la etapa atada a la versión de pesos con la que se calculó.
     *
     * <p>Esa versión es la que la <b>vacante</b> tiene fijada (RF-114: "la vacante debe tener
     * una versión de pesos aprobada antes de que empiecen los candidatos. Nunca se
     * redistribuyen pesos a mano por persona"), no la última que exista publicada en la
     * organización — que puede ser de otra vacante y haber cambiado el reparto de puntos.
     * Esa atadura es lo que permite reconstruir una decisión vieja: las notas históricas no
     * se recalculan aunque después se publique otra versión.
     */
    private void guardarNota(Postulacion postulacion, BigDecimal nota) {
        Vacante vacante = vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException("La vacante de esta postulación ya no existe"));
        VersionPesos version = versionesPesos.findById(vacante.getVersionPesosId())
                .orElseThrow(() -> new IllegalStateException(
                        "La versión de pesos de esta vacante ya no existe"));

        NotaEtapa fila = notasEtapa
                .findByPostulacionIdAndEtapaCodigo(postulacion.getId(), "PERFIL_INTEGRAL")
                .orElseGet(() -> NotaEtapa.builder()
                        .postulacionId(postulacion.getId())
                        .etapaCodigo("PERFIL_INTEGRAL")
                        .creadoEn(Instant.now())
                        .build());
        fila.setPuntaje(nota);
        fila.setVersionPesosId(version.getId());
        fila.setCalculadaEn(Instant.now());
        notasEtapa.save(fila);
    }

    private String recorte(Pregunta pregunta) {
        if (pregunta == null) return "una pregunta";
        String texto = pregunta.getEnunciado();
        return texto.length() <= 60 ? texto : texto.substring(0, 57) + "…";
    }
}
