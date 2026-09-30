package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La nota de las preguntas propias de una vacante (método PUNTOS), calculada al leer.
 *
 * <p><b>Es la única cuenta, y la usan todos</b>: la nota de lo cerrado al entregar, la nota
 * del Perfil Integral, el desglose de la ficha, el insumo del evaluador y el reescalado al
 * cambiar puntos. Nada de ella se guarda por respuesta: lo cerrado se recalcula con los
 * puntos que tenga la versión en ese momento, así que cambiar una clave mal puesta mueve la
 * nota de todos a la vez sin reescribir nada.
 *
 * <p>Las reglas (punto 5 de la spec):
 * <ul>
 *   <li>La nota de un criterio es la suma de lo que sacó en sus preguntas.
 *   <li>La del banco es la suma de los criterios, de 0 a 100. <b>No</b> se reparte entre
 *       cerradas y abiertas por cuántas hay, como hace {@code notaCombinada}.
 *   <li>Solo existe cuando todas sus preguntas con puntos tienen nota. Una abierta sin
 *       calificar deja el criterio pendiente, y el banco sin nota.
 *   <li>Una pregunta de 0 puntos no suma y no espera a nadie. Una abierta en blanco vale 0 y
 *       tampoco se manda a la IA.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalificacionPorPuntos {

    public static final String METODO = "PUNTOS";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final VersionBancoRepository versionesBanco;
    private final CriterioBancoRepository criteriosBanco;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final RespuestaRepository respuestas;
    private final NotaRespuestaRepository notasRespuesta;

    /** Una pregunta con lo que respondió y lo que sacó. */
    public record PreguntaCalculada(Pregunta pregunta, List<Opcion> opciones, Respuesta respuesta,
                                    NotaRespuesta nota, int maximo, BigDecimal obtenido,
                                    boolean pendiente) {

        public boolean esAbierta() {
            return ReglasDePuntos.ABIERTA.equals(pregunta.getTipo());
        }

        /** Una abierta que hay que mandar a la IA: con puntos y con algo escrito. */
        public boolean vaALaIa() {
            return esAbierta() && maximo > 0 && respuesta != null
                    && respuesta.getTexto() != null && !respuesta.getTexto().isBlank();
        }
    }

    /** Un criterio, con de dónde sale cada punto. */
    public record CriterioCalculado(CriterioBanco criterio, int maximo, BigDecimal sistema,
                                    int sistemaMaximo, BigDecimal ia, int iaMaximo,
                                    boolean pendiente, List<PreguntaCalculada> preguntas) {

        /** Nula mientras falte alguna abierta: un criterio a medias no enseña nota parcial. */
        public BigDecimal nota() {
            return pendiente ? null : sistema.add(ia);
        }
    }

    /** Todo lo que sale de una evaluación de este método. */
    public record Resultado(VersionBanco version, List<CriterioCalculado> criterios,
                            List<PreguntaCalculada> sinCriterio, BigDecimal total,
                            boolean completo) {

        public List<PreguntaCalculada> todas() {
            List<PreguntaCalculada> todas = new ArrayList<>();
            criterios.forEach(c -> todas.addAll(c.preguntas()));
            todas.addAll(sinCriterio);
            return todas;
        }

        /** La nota del banco sobre 100, o nula mientras falte alguna abierta. */
        public BigDecimal nota() {
            return completo ? total : null;
        }

        public BigDecimal cerradasObtenido() {
            return todas().stream().filter(p -> !p.esAbierta())
                    .map(PreguntaCalculada::obtenido).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public int cerradasMaximo() {
            return todas().stream().filter(p -> !p.esAbierta())
                    .mapToInt(PreguntaCalculada::maximo).sum();
        }

        public int cerradasConPuntos() {
            return (int) todas().stream().filter(p -> !p.esAbierta() && p.maximo() > 0).count();
        }
    }

    /** Si esta versión del banco se califica por puntos. */
    public boolean esPorPuntos(Long versionBancoId) {
        return versionBancoId != null && versionesBanco.findById(versionBancoId)
                .map(v -> METODO.equals(v.getMetodoCalificacion()))
                .orElse(false);
    }

    /**
     * La cuenta entera de una evaluación. {@code evaluacionId} puede ser nulo: sale la
     * versión con todo a cero, que es como se enseña antes de que nadie responda.
     */
    public Resultado calcular(Long versionBancoId, Long evaluacionId) {
        VersionBanco version = versionesBanco.findById(versionBancoId)
                .orElseThrow(() -> new IllegalStateException(
                        "La versión " + versionBancoId + " de las preguntas ya no existe"));
        List<CriterioBanco> criterios = criteriosBanco
                .findByVersionBancoIdOrderByOrdenAscIdAsc(versionBancoId);
        List<Pregunta> suyas = preguntas.findByVersionBancoIdOrderByOrden(versionBancoId);
        Map<Long, List<Opcion>> opcionesPorPregunta = opcionesDe(suyas);

        Map<Long, Respuesta> respuestaPorPregunta = evaluacionId == null ? Map.of()
                : respuestas.findByEvaluacionId(evaluacionId).stream()
                        .collect(Collectors.toMap(Respuesta::getPreguntaId, Function.identity(),
                                (a, b) -> a));
        Map<Long, NotaRespuesta> notaPorRespuesta = respuestaPorPregunta.isEmpty() ? Map.of()
                : notasRespuesta.findByRespuestaIdIn(respuestaPorPregunta.values().stream()
                                .map(Respuesta::getId).toList()).stream()
                        .collect(Collectors.toMap(NotaRespuesta::getRespuestaId,
                                Function.identity(), (a, b) -> a));

        Map<Long, List<PreguntaCalculada>> porCriterio = new LinkedHashMap<>();
        criterios.forEach(c -> porCriterio.put(c.getId(), new ArrayList<>()));
        List<PreguntaCalculada> sinCriterio = new ArrayList<>();

        for (Pregunta p : suyas) {
            Respuesta r = respuestaPorPregunta.get(p.getId());
            PreguntaCalculada calculada = calcularPregunta(p,
                    opcionesPorPregunta.getOrDefault(p.getId(), List.of()), r,
                    r == null ? null : notaPorRespuesta.get(r.getId()));
            List<PreguntaCalculada> destino = p.getCriterioBancoId() == null ? null
                    : porCriterio.get(p.getCriterioBancoId());
            (destino == null ? sinCriterio : destino).add(calculada);
        }

        List<CriterioCalculado> calculados = new ArrayList<>();
        for (CriterioBanco c : criterios) {
            List<PreguntaCalculada> deEste = porCriterio.get(c.getId());
            deEste.sort(Comparator.comparing(pc -> ordenDe(pc.pregunta())));
            calculados.add(sumar(c, deEste));
        }
        sinCriterio.sort(Comparator.comparing(pc -> ordenDe(pc.pregunta())));

        List<PreguntaCalculada> todas = new ArrayList<>(sinCriterio);
        calculados.forEach(c -> todas.addAll(c.preguntas()));
        boolean completo = todas.stream().noneMatch(PreguntaCalculada::pendiente);
        BigDecimal total = todas.stream().map(PreguntaCalculada::obtenido)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Resultado(version, calculados, sinCriterio, total, completo);
    }

    private static int ordenDe(Pregunta p) {
        return p.getOrden() == null ? Integer.MAX_VALUE : p.getOrden();
    }

    private static CriterioCalculado sumar(CriterioBanco c, List<PreguntaCalculada> deEste) {
        BigDecimal sistema = BigDecimal.ZERO;
        BigDecimal ia = BigDecimal.ZERO;
        int sistemaMaximo = 0;
        int iaMaximo = 0;
        boolean pendiente = false;
        for (PreguntaCalculada pc : deEste) {
            if (pc.esAbierta()) {
                iaMaximo += pc.maximo();
                if (pc.pendiente()) {
                    pendiente = true;
                } else if (pc.obtenido() != null) {
                    ia = ia.add(pc.obtenido());
                }
            } else {
                sistemaMaximo += pc.maximo();
                sistema = sistema.add(pc.obtenido());
            }
        }
        return new CriterioCalculado(c, sistemaMaximo + iaMaximo, sistema, sistemaMaximo, ia,
                iaMaximo, pendiente, deEste);
    }

    /** Lo que sacó en una pregunta. Ver las reglas en la cabecera de la clase. */
    public static PreguntaCalculada calcularPregunta(Pregunta p, List<Opcion> suyas,
                                                     Respuesta r, NotaRespuesta nota) {
        int maximo = p.getPuntos() == null ? 0 : p.getPuntos();
        List<Opcion> ordenadas = suyas.stream()
                .sorted(Comparator.comparing((Opcion o) -> o.getOrden() == null
                        ? Integer.MAX_VALUE : o.getOrden()).thenComparing(Opcion::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (ReglasDePuntos.ABIERTA.equals(p.getTipo())) {
            boolean enBlanco = r == null || r.getTexto() == null || r.getTexto().isBlank();
            if (maximo == 0 || enBlanco) {
                // No suma, o no hay nada que calificar: vale 0 y no espera a la IA.
                return new PreguntaCalculada(p, ordenadas, r, nota, maximo, BigDecimal.ZERO,
                        false);
            }
            if (nota == null) {
                return new PreguntaCalculada(p, ordenadas, r, null, maximo, null, true);
            }
            return new PreguntaCalculada(p, ordenadas, r, nota, maximo, nota.getPuntaje(), false);
        }

        BigDecimal obtenido;
        if (r == null) {
            obtenido = BigDecimal.ZERO;
        } else if (ReglasDePuntos.OPCION_MULTIPLE.equals(p.getTipo())) {
            Collection<Long> marcadas = marcadasDe(r);
            obtenido = ReglasDePuntos.puntosDeCerrada(p.getTipo(), maximo, null,
                    ordenadas.stream().filter(o -> marcadas.contains(o.getId()))
                            .map(Opcion::getPuntaje).toList());
        } else {
            BigDecimal elegida = ordenadas.stream()
                    .filter(o -> o.getId().equals(r.getOpcionId()))
                    .map(Opcion::getPuntaje).filter(Objects::nonNull)
                    .findFirst().orElse(null);
            obtenido = ReglasDePuntos.puntosDeCerrada(p.getTipo(), maximo, elegida, List.of());
        }
        return new PreguntaCalculada(p, ordenadas, r, null, maximo, obtenido, false);
    }

    /** Los ids que marcó en una opción múltiple ({@code detalle.marcadas}). */
    public static List<Long> marcadasDe(Respuesta r) {
        if (r == null || r.getDetalle() == null || r.getDetalle().isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> detalle = JSON.readValue(r.getDetalle(),
                    new TypeReference<Map<String, Object>>() { });
            Object marcadas = detalle.get("marcadas");
            if (!(marcadas instanceof Collection<?> c)) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            for (Object v : c) {
                if (v instanceof Number n) {
                    ids.add(n.longValue());
                } else if (v instanceof String s) {
                    try {
                        ids.add(Long.valueOf(s.trim()));
                    } catch (NumberFormatException ignorada) {
                        // un id ilegible no marca nada
                    }
                }
            }
            return ids;
        } catch (RuntimeException e) {
            log.warn("El detalle de la respuesta {} no se pudo leer: {}", r.getId(), e.getMessage());
            return List.of();
        }
    }

    private Map<Long, List<Opcion>> opcionesDe(List<Pregunta> suyas) {
        if (suyas.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Opcion>> porPregunta = new HashMap<>();
        opciones.findByPreguntaIdIn(suyas.stream().map(Pregunta::getId).toList())
                .forEach(o -> porPregunta.computeIfAbsent(o.getPreguntaId(), k -> new ArrayList<>())
                        .add(o));
        return porPregunta;
    }

    /** La versión de una evaluación, si es de este método. */
    public Optional<VersionBanco> versionPorPuntos(Long versionBancoId) {
        if (versionBancoId == null) {
            return Optional.empty();
        }
        return versionesBanco.findById(versionBancoId)
                .filter(v -> METODO.equals(v.getMetodoCalificacion()));
    }
}
