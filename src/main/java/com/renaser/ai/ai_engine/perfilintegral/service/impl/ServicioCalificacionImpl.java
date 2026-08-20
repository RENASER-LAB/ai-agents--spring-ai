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
import com.renaser.ai.ai_engine.perfilintegral.service.FormulasBancoV3;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.Collection;
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
    /** Lo máximo que da un ítem antes de aplicarle su peso. Lo fija el documento del v3. */
    private static final BigDecimal TOPE_DEL_ITEM = BigDecimal.valueOf(3);
    private static final ObjectMapper JSON = new ObjectMapper();

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
     * La nota de lo cerrado, sobre 100, con las reglas del banco v3.
     *
     * <p>Cada ítem da entre 0 y 3 según la fórmula de su formato ({@link FormulasBancoV3}),
     * y eso se multiplica por su peso: 1 vale hasta 3 puntos y 2 hasta 6. La nota es lo
     * obtenido sobre lo máximo alcanzable, que es la suma de los pesos por 3.
     *
     * <p>Así una evaluación se puede comparar con otra aunque tengan distinto número de ítems.
     * Los de peso 0 —los pares de consistencia y los eliminatorios— no entran ni arriba ni
     * abajo: no suman ni cambian el máximo.
     *
     * <p><b>Un ítem sin responder no es un cero, es un ítem que falta.</b> No se cuenta contra
     * el máximo: inventarle un cero sería castigar dos veces al que no llegó a terminar.
     */
    private ResumenCerrado puntuar(List<Respuesta> suyas, Map<Long, Pregunta> porId) {
        BigDecimal obtenido = BigDecimal.ZERO;
        BigDecimal maximo = BigDecimal.ZERO;
        int contadas = 0;

        for (Respuesta r : suyas) {
            Pregunta p = porId.get(r.getPreguntaId());
            if (p == null || p.getPeso() == null || p.getPeso() == 0) {
                continue;   // no puntúa: par de consistencia o eliminatorio
            }
            BigDecimal delItem = puntuarItem(p, r);
            if (delItem == null) {
                continue;   // no se pudo leer la respuesta: no se inventa un cero
            }
            obtenido = obtenido.add(FormulasBancoV3.conPeso(delItem, p.getPeso().intValue()));
            maximo = maximo.add(FormulasBancoV3.conPeso(TOPE_DEL_ITEM, p.getPeso().intValue()));
            contadas++;
        }

        if (maximo.compareTo(BigDecimal.ZERO) == 0) {
            return new ResumenCerrado(BigDecimal.ZERO, 0);
        }
        return new ResumenCerrado(
                obtenido.multiply(CIEN).divide(maximo, 2, RoundingMode.HALF_UP), contadas);
    }

    /**
     * Lo que vale un ítem, de 0 a 3, según su formato. {@code null} si la respuesta no se
     * puede leer: eso no es un cero, es que no hay con qué puntuar.
     */
    private BigDecimal puntuarItem(Pregunta p, Respuesta r) {
        Map<String, Object> detalle = leerDetalle(r);
        List<Opcion> suyas = opciones.findByPreguntaIdOrderByLetra(p.getId());

        return switch (p.getTipo()) {
            case "EF-4" -> {
                Long mas = id(detalle.get("mas"));
                Long menos = id(detalle.get("menos"));
                Integer vMas = valorDe(suyas, mas);
                Integer vMenos = valorDe(suyas, menos);
                yield (vMas == null || vMenos == null) ? null : FormulasBancoV3.ef4(vMas, vMenos);
            }
            case "SJT-R" -> {
                Map<String, Integer> dadas = calificaciones(detalle);
                Map<String, Integer> claves = new HashMap<>();
                suyas.stream().filter(o -> o.getPuntaje() != null).forEach(
                        o -> claves.put(String.valueOf(o.getId()), o.getPuntaje().intValue()));
                yield claves.isEmpty() ? null : FormulasBancoV3.sjtR(dadas, claves);
            }
            case "SEC" -> {
                List<Integer> orden = ids(detalle.get("orden"));
                List<Integer> correcto = suyas.stream()
                        .filter(o -> o.getOrdenCorrecto() != null)
                        .sorted(Comparator.comparing(Opcion::getOrdenCorrecto))
                        .map(o -> o.getId().intValue())
                        .toList();
                yield correcto.isEmpty() ? null : FormulasBancoV3.sec(orden, correcto);
            }
            case "INV" -> {
                List<Integer> marcadas = ids(detalle.get("marcadas"));
                long realesTotales = suyas.stream().filter(o -> !o.isEsDistractor()).count();
                long realesMarcadas = suyas.stream()
                        .filter(o -> !o.isEsDistractor() && marcadas.contains(o.getId().intValue()))
                        .count();
                long falsas = suyas.stream()
                        .filter(o -> o.isEsDistractor() && marcadas.contains(o.getId().intValue()))
                        .count();
                yield realesTotales == 0 ? null
                        : FormulasBancoV3.inv((int) realesMarcadas, (int) realesTotales, (int) falsas);
            }
            case "DE" -> {
                List<Integer> marcadas = ids(detalle.get("marcadas"));
                long buenas = suyas.stream()
                        .filter(o -> !o.isEsDistractor() && marcadas.contains(o.getId().intValue()))
                        .count();
                long malas = suyas.stream()
                        .filter(o -> o.isEsDistractor() && marcadas.contains(o.getId().intValue()))
                        .count();
                yield FormulasBancoV3.de((int) buenas, (int) malas);
            }
            case "CD" -> {
                Object campos = detalle.get("campos");
                if (!(campos instanceof Map<?, ?> m) || m.isEmpty()) {
                    yield null;
                }
                // Un campo cuenta como válido si viene con algo dentro. Comprobar rangos y
                // coherencia entre campos es lo que falta del motor: hoy solo se mira que esté.
                long llenos = m.values().stream()
                        .filter(v -> v != null && !String.valueOf(v).isBlank()).count();
                int totales = p.getCasosPedidos() != null ? p.getCasosPedidos().intValue() : m.size();
                yield FormulasBancoV3.cd((int) llenos, totales);
            }
            // V se puntúa con la tabla de tramos del propio ítem, que necesita interpretar el
            // dato que escribió el candidato. Todavía no está: se deja sin puntuar antes que
            // darle un valor inventado.
            default -> null;
        };
    }

    private Map<String, Object> leerDetalle(Respuesta r) {
        if (r.getDetalle() == null || r.getDetalle().isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(r.getDetalle(), new TypeReference<Map<String, Object>>() { });
        } catch (RuntimeException e) {
            log.warn("El detalle de la respuesta {} no se pudo leer: {}", r.getId(), e.getMessage());
            return Map.of();
        }
    }

    private static Integer valorDe(List<Opcion> suyas, Long opcionId) {
        return suyas.stream()
                .filter(o -> o.getId().equals(opcionId) && o.getValor() != null)
                .map(o -> o.getValor().intValue())
                .findFirst().orElse(null);
    }

    private static Map<String, Integer> calificaciones(Map<String, Object> detalle) {
        Map<String, Integer> dadas = new HashMap<>();
        if (detalle.get("calificaciones") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                Long nota = id(v);
                if (nota != null) {
                    dadas.put(String.valueOf(k), nota.intValue());
                }
            });
        }
        return dadas;
    }

    private static List<Integer> ids(Object valor) {
        if (!(valor instanceof Collection<?> c)) {
            return List.of();
        }
        return c.stream().map(ServicioCalificacionImpl::id)
                .filter(java.util.Objects::nonNull).map(Long::intValue).toList();
    }

    private static Long id(Object valor) {
        if (valor instanceof Number n) {
            return n.longValue();
        }
        if (valor instanceof String s) {
            try {
                return Long.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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
            // Los pares del v3 no traen diferencia_maxima: su regla es la penalización del
            // -5% sobre el global, que es del motor pendiente, no de esta comparación v0.1.
            // Sin esta guardia, el primer par v3 cuyas dos preguntas se respondan con
            // opcionId revienta con un NPE al entregar la evaluación.
            if (par.getDiferenciaMaxima() == null) {
                continue;
            }
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
