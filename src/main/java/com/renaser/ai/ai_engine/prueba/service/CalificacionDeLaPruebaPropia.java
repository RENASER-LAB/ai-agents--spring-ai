package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos;
import com.renaser.ai.ai_engine.prueba.entity.CriterioBancoEntregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableCubrePregunta;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.NotaCriterioPrueba;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.CriterioBancoEntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableCubrePreguntaRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.NotaCriterioPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La nota de la prueba técnica escrita en el editor (V67), calculada al leer.
 *
 * <p><b>Es la única cuenta y la usan todos</b>: el editor (balance y lo que frena publicar),
 * la entrega (¿falta algo por responder?), el cierre de la calificación, la ficha, el
 * ranking, el Excel y el cambio de puntos. Si cada uno sumara por su cuenta, la ficha y el
 * ranking dirían cifras distintas.
 *
 * <p>Las reglas (puntos 2 y 8 de la spec):
 * <ul>
 *   <li>Cada criterio tiene una <b>parte automática</b> —la suma de lo que sacó en sus
 *       cerradas, que el sistema cuenta con los puntos que tenga hoy la versión— y una <b>parte
 *       calificada</b> —sus puntos, que la IA o una persona ponen al criterio entero—.
 *   <li>Las abiertas y los entregables <b>no llevan puntos</b>: se miran para calificar la
 *       parte calificada. Por eso esto NO es {@link CalificacionPorPuntos#calcular}, que
 *       califica pregunta a pregunta; de aquella solo se reutiliza la cuenta de una cerrada.
 *   <li>Un criterio con parte calificada sin nota está <b>pendiente</b>, aunque su parte
 *       automática ya exista. La nota de la etapa solo existe con todos los criterios enteros.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CalificacionDeLaPruebaPropia {

    public static final String IA = "IA";
    public static final String PERSONA = "PERSONA";

    private final VersionBancoRepository versionesBanco;
    private final CriterioBancoRepository criteriosBanco;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final EntregableRequeridoRepository entregables;
    private final CriterioBancoEntregableRepository miradas;
    private final RespuestaPruebaRepository respuestas;
    private final NotaCriterioPruebaRepository notas;
    private final com.renaser.ai.ai_engine.prueba.repository.EntregableRepository subidas;
    private final EntregableCubrePreguntaRepository cubiertas;

    // ============================== Lo que sale ==============================

    /** Una pregunta con lo que respondió y lo que sacó (en una cerrada). */
    public record PreguntaDeLaPrueba(Pregunta pregunta, List<Opcion> opciones,
                                     RespuestaPrueba respuesta, int maximo, BigDecimal obtenido,
                                     boolean respondida) {

        public boolean esAbierta() {
            return ReglasDePuntos.ABIERTA.equals(pregunta.getTipo());
        }
    }

    /** Un criterio, con de dónde sale cada punto. */
    public record CriterioDeLaPrueba(CriterioBanco criterio, List<PreguntaDeLaPrueba> preguntas,
                                     List<EntregableRequerido> entregables, int sistemaMaximo,
                                     BigDecimal sistema, int calificadaMaximo, String calificador,
                                     NotaCriterioPrueba nota) {

        /**
         * Lo que puede sacar: los de sus cerradas más los de su parte calificada. Es su total,
         * salvo que sus cerradas pasen de él (una falta que frena publicar).
         */
        public int maximo() {
            return sistemaMaximo + calificadaMaximo;
        }

        /**
         * Lo que vale el criterio (V69): el total que escribió quien arma la prueba, o, en una
         * versión de antes, sus cerradas más su parte calificada.
         */
        public int puntosDelCriterio() {
            Integer total = criterio.getPuntosDelCriterio();
            return total == null ? maximo() : total;
        }

        /** Si sus cerradas pasan de lo que vale el criterio (V69): no se publica así. */
        public boolean cerradasPorEncima() {
            return sistemaMaximo > puntosDelCriterio();
        }

        public boolean tieneParteCalificada() {
            return calificadaMaximo > 0;
        }

        public boolean esDePersona() {
            return PERSONA.equals(calificador);
        }

        public boolean esDeIa() {
            return IA.equals(calificador);
        }

        /** La nota de la parte calificada, o nula si nadie la puso todavía. */
        public BigDecimal calificada() {
            return nota == null ? null : nota.getPuntaje();
        }

        /** Con parte calificada y sin nota: pendiente, aunque la automática ya exista. */
        public boolean pendiente() {
            return tieneParteCalificada() && nota == null;
        }

        /** Nula mientras esté pendiente: un criterio a medias no enseña nota parcial. */
        public BigDecimal notaDelCriterio() {
            if (pendiente()) {
                return null;
            }
            BigDecimal calificada = nota == null || !tieneParteCalificada()
                    ? BigDecimal.ZERO : nota.getPuntaje();
            return sistema.add(calificada);
        }

        public List<PreguntaDeLaPrueba> abiertas() {
            return preguntas.stream().filter(PreguntaDeLaPrueba::esAbierta).toList();
        }
    }

    /** Todo lo que sale de una prueba (con o sin candidato). */
    public record Resultado(VersionBanco version, List<CriterioDeLaPrueba> criterios,
                            List<PreguntaDeLaPrueba> sinCriterio,
                            List<EntregableRequerido> entregables,
                            /* Las preguntas que cubre cada entregable general de alcance
                               PREGUNTAS (V68), por el id del entregable. */
                            Map<Long, List<Long>> cubre) {

        public Resultado(VersionBanco version, List<CriterioDeLaPrueba> criterios,
                         List<PreguntaDeLaPrueba> sinCriterio, List<EntregableRequerido> entregables) {
            this(version, criterios, sinCriterio, entregables, Map.of());
        }

        /** Las preguntas que cubre un entregable general; vacío si no cubre ninguna. */
        public List<Long> cubreDe(Long entregableId) {
            return cubre == null ? List.of() : cubre.getOrDefault(entregableId, List.of());
        }

        public List<PreguntaDeLaPrueba> todas() {
            List<PreguntaDeLaPrueba> todas = new ArrayList<>();
            criterios.forEach(c -> todas.addAll(c.preguntas()));
            todas.addAll(sinCriterio);
            return todas;
        }

        /**
         * Lo que suma la versión: lo que vale cada criterio (sus cerradas y su parte calificada;
         * V69, lo escrito aunque sus cerradas pasen de ahí) y las cerradas sueltas. Tiene que
         * dar 100.
         */
        public int total() {
            int deLosCriterios = criterios.stream().mapToInt(CriterioDeLaPrueba::puntosDelCriterio).sum();
            int sueltas = sinCriterio.stream().filter(p -> !p.esAbierta())
                    .mapToInt(PreguntaDeLaPrueba::maximo).sum();
            return deLosCriterios + sueltas;
        }

        /** Sin entregables es un cuestionario, sin que nadie lo elija (decisión 1). */
        public boolean esCuestionario() {
            return entregables.isEmpty();
        }

        public boolean completo() {
            return criterios.stream().noneMatch(CriterioDeLaPrueba::pendiente);
        }

        /** La nota de la etapa sobre 100, o nula mientras falte algún criterio. */
        public BigDecimal nota() {
            if (!completo()) {
                return null;
            }
            BigDecimal suma = criterios.stream().map(CriterioDeLaPrueba::notaDelCriterio)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            return suma.add(sinCriterio.stream().filter(p -> !p.esAbierta())
                    .map(PreguntaDeLaPrueba::obtenido).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        /** Los criterios que faltan por calificar, por su nombre. */
        public List<String> pendientes() {
            return criterios.stream().filter(CriterioDeLaPrueba::pendiente)
                    .map(c -> c.criterio().getNombre()).toList();
        }

        /** Las posiciones (desde 1) de las preguntas sin responder, en el orden en que se ven. */
        public List<Integer> sinResponder() {
            List<Integer> faltan = new ArrayList<>();
            List<PreguntaDeLaPrueba> todas = todas();
            for (int i = 0; i < todas.size(); i++) {
                if (!todas.get(i).respondida()) {
                    faltan.add(i + 1);
                }
            }
            return faltan;
        }
    }

    // ============================== Lo que falta para entregar ==============================

    /**
     * Lo que le falta para poder entregar: las preguntas sin responder (por su posición) y
     * los entregables obligatorios sin subir (por su nombre). Vacío = puede entregar.
     */
    public record LoQueFalta(List<Integer> preguntas, List<String> entregables) {

        public boolean nada() {
            return preguntas.isEmpty() && entregables.isEmpty();
        }

        /** «le faltaron 3 preguntas», «le faltó «Tablero.xlsx»», o las dos cosas. */
        public String dicho() {
            List<String> partes = new ArrayList<>();
            if (!preguntas.isEmpty()) {
                partes.add(preguntas.size() == 1 ? "1 pregunta" : preguntas.size() + " preguntas");
            }
            entregables.forEach(e -> partes.add("«" + e + "»"));
            if (partes.isEmpty()) {
                return "";
            }
            boolean una = partes.size() == 1 && (preguntas.size() == 1 || preguntas.isEmpty());
            String lista = partes.size() == 1 ? partes.get(0)
                    : String.join(", ", partes.subList(0, partes.size() - 1)) + " y "
                            + partes.get(partes.size() - 1);
            return (una ? "le faltó " : "le faltaron ") + lista;
        }
    }

    /**
     * Lo que le falta a un intento para poder entregarse (decisión 11).
     *
     * @param subidos los entregables que subió ese intento
     */
    public static LoQueFalta loQueFalta(Resultado r,
                                        Collection<com.renaser.ai.ai_engine.prueba.entity.Entregable> subidos) {
        Set<Long> conAlgo = subidos.stream()
                .map(com.renaser.ai.ai_engine.prueba.entity.Entregable::getEntregableRequeridoId)
                .collect(Collectors.toSet());
        List<String> entregablesQueFaltan = r.entregables().stream()
                .filter(EntregableRequerido::isEsObligatorio)
                .filter(e -> !conAlgo.contains(e.getId()))
                .map(EntregableRequerido::getNombre)
                .toList();
        return new LoQueFalta(r.sinResponder(), entregablesQueFaltan);
    }

    /** Lo que le faltó a cada intento de una tanda, en bloque (la lista «No completaron»). */
    public Map<Long, LoQueFalta> loQueFaltaTanda(Collection<IntentoPrueba> intentos) {
        Map<Long, Resultado> resultados = calcularTanda(intentos);
        if (resultados.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<com.renaser.ai.ai_engine.prueba.entity.Entregable>> porIntento = subidas
                .findByIntentoPruebaIdIn(resultados.keySet()).stream()
                .collect(Collectors.groupingBy(
                        com.renaser.ai.ai_engine.prueba.entity.Entregable::getIntentoPruebaId));
        Map<Long, LoQueFalta> salida = new HashMap<>();
        resultados.forEach((intentoId, r) ->
                salida.put(intentoId, loQueFalta(r, porIntento.getOrDefault(intentoId, List.of()))));
        return salida;
    }

    // ============================== Calcular ==============================

    /** La versión sin candidato: es como se enseña en el editor y como se valida. */
    public Resultado estructura(Long versionBancoId) {
        VersionBanco version = versionesBanco.findById(versionBancoId)
                .orElseThrow(() -> new IllegalStateException(
                        "La versión " + versionBancoId + " de la prueba ya no existe"));
        return calcularTanda(Map.of(version.getId(), version), List.of()).estructuras()
                .get(version.getId());
    }

    /** La prueba de un candidato, con lo que respondió y las notas puestas. */
    public Resultado calcular(IntentoPrueba intento) {
        if (intento.getVersionBancoId() == null) {
            throw new IllegalStateException("Esta prueba no es del editor: se califica por su rúbrica");
        }
        VersionBanco version = versionesBanco.findById(intento.getVersionBancoId())
                .orElseThrow(() -> new IllegalStateException(
                        "La versión de esta prueba ya no existe"));
        return calcularTanda(Map.of(version.getId(), version), List.of(intento)).porIntento()
                .get(intento.getId());
    }

    /**
     * La prueba de una tanda entera, en bloque: unas ocho consultas para toda la tanda,
     * nunca unas por fila. Es lo que usan el ranking y el Excel.
     *
     * @return el resultado de cada intento, por su id. Los que no sean del editor no salen.
     */
    public Map<Long, Resultado> calcularTanda(Collection<IntentoPrueba> intentos) {
        List<IntentoPrueba> delEditor = intentos.stream()
                .filter(i -> i.getVersionBancoId() != null).toList();
        if (delEditor.isEmpty()) {
            return Map.of();
        }
        Map<Long, VersionBanco> versiones = versionesBanco.findAllById(delEditor.stream()
                        .map(IntentoPrueba::getVersionBancoId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(VersionBanco::getId, Function.identity()));
        return calcularTanda(versiones, delEditor).porIntento();
    }

    private record Tanda(Map<Long, Resultado> estructuras, Map<Long, Resultado> porIntento) {
    }

    private Tanda calcularTanda(Map<Long, VersionBanco> versiones, List<IntentoPrueba> intentos) {
        List<Long> versionIds = List.copyOf(versiones.keySet());
        Map<Long, List<CriterioBanco>> criteriosPorVersion = criteriosBanco
                .findByVersionBancoIdIn(versionIds).stream()
                .sorted(Comparator.comparing((CriterioBanco c) -> c.getOrden() == null
                        ? Integer.MAX_VALUE : c.getOrden()).thenComparing(CriterioBanco::getId))
                .collect(Collectors.groupingBy(CriterioBanco::getVersionBancoId,
                        LinkedHashMap::new, Collectors.toList()));
        List<Pregunta> todasLasPreguntas = preguntas.findByVersionBancoIdIn(versionIds);
        Map<Long, List<Opcion>> opcionesPorPregunta = todasLasPreguntas.isEmpty() ? Map.of()
                : opciones.findByPreguntaIdIn(todasLasPreguntas.stream().map(Pregunta::getId).toList())
                        .stream().collect(Collectors.groupingBy(Opcion::getPreguntaId));
        Map<Long, List<EntregableRequerido>> entregablesPorVersion = entregables
                .findByVersionBancoIdIn(versionIds).stream()
                .sorted(Comparator.comparing((EntregableRequerido e) -> e.getOrden() == null
                        ? Integer.MAX_VALUE : e.getOrden()).thenComparing(EntregableRequerido::getId))
                .collect(Collectors.groupingBy(EntregableRequerido::getVersionBancoId,
                        LinkedHashMap::new, Collectors.toList()));
        Set<Long> criterioIds = criteriosPorVersion.values().stream().flatMap(List::stream)
                .map(CriterioBanco::getId).collect(Collectors.toSet());
        Map<Long, List<Long>> miraPorCriterio = criterioIds.isEmpty() ? Map.of()
                : miradas.findByCriterioBancoIdIn(criterioIds).stream()
                        .collect(Collectors.groupingBy(CriterioBancoEntregable::getCriterioBancoId,
                                Collectors.mapping(CriterioBancoEntregable::getEntregableRequeridoId,
                                        Collectors.toList())));
        Map<Long, List<Pregunta>> preguntasPorVersion = todasLasPreguntas.stream()
                .collect(Collectors.groupingBy(Pregunta::getVersionBancoId));
        Set<Long> generales = entregablesPorVersion.values().stream().flatMap(List::stream)
                .filter(e -> EntregableRequerido.PREGUNTAS.equals(e.getAlcance()))
                .map(EntregableRequerido::getId).collect(Collectors.toSet());
        Map<Long, List<Long>> cubre = generales.isEmpty() ? Map.of()
                : cubiertas.findByEntregableRequeridoIdIn(generales).stream()
                        .collect(Collectors.groupingBy(EntregableCubrePregunta::getEntregableRequeridoId,
                                Collectors.mapping(EntregableCubrePregunta::getPreguntaId,
                                        Collectors.toList())));
        Mirados mirados = new Mirados(miraPorCriterio, cubre);

        Map<Long, Resultado> estructuras = new HashMap<>();
        for (VersionBanco v : versiones.values()) {
            estructuras.put(v.getId(), armar(v, criteriosPorVersion.getOrDefault(v.getId(), List.of()),
                    preguntasPorVersion.getOrDefault(v.getId(), List.of()), opcionesPorPregunta,
                    entregablesPorVersion.getOrDefault(v.getId(), List.of()), mirados,
                    Map.of(), Map.of()));
        }

        Map<Long, Resultado> porIntento = new HashMap<>();
        if (!intentos.isEmpty()) {
            List<Long> intentoIds = intentos.stream().map(IntentoPrueba::getId).toList();
            Map<Long, List<RespuestaPrueba>> respuestasPorIntento = respuestas
                    .findByIntentoPruebaIdIn(intentoIds).stream()
                    .filter(r -> r.getPreguntaId() != null)
                    .collect(Collectors.groupingBy(RespuestaPrueba::getIntentoPruebaId));
            Map<Long, List<NotaCriterioPrueba>> notasPorIntento = notas
                    .findByIntentoPruebaIdIn(intentoIds).stream()
                    .collect(Collectors.groupingBy(NotaCriterioPrueba::getIntentoPruebaId));
            for (IntentoPrueba intento : intentos) {
                VersionBanco v = versiones.get(intento.getVersionBancoId());
                if (v == null) {
                    continue;
                }
                Map<Long, RespuestaPrueba> respuestaPorPregunta = respuestasPorIntento
                        .getOrDefault(intento.getId(), List.of()).stream()
                        .collect(Collectors.toMap(RespuestaPrueba::getPreguntaId,
                                Function.identity(), (a, b) -> a));
                Map<Long, NotaCriterioPrueba> notaPorCriterio = notasPorIntento
                        .getOrDefault(intento.getId(), List.of()).stream()
                        .collect(Collectors.toMap(NotaCriterioPrueba::getCriterioBancoId,
                                Function.identity(), (a, b) -> a));
                porIntento.put(intento.getId(), armar(v,
                        criteriosPorVersion.getOrDefault(v.getId(), List.of()),
                        preguntasPorVersion.getOrDefault(v.getId(), List.of()), opcionesPorPregunta,
                        entregablesPorVersion.getOrDefault(v.getId(), List.of()), mirados,
                        respuestaPorPregunta, notaPorCriterio));
            }
        }
        return new Tanda(estructuras, porIntento);
    }

    /**
     * De dónde sale lo que mira cada criterio: lo marcado a mano en las versiones de antes de
     * la V68 y, en las demás, las preguntas que cubre cada entregable general.
     */
    private record Mirados(Map<Long, List<Long>> aMano, Map<Long, List<Long>> cubre) {
    }

    /**
     * Si un criterio mira un entregable (punto 8 de la spec). Se deduce del alcance: el archivo
     * de una de sus preguntas, un general de toda la prueba o uno que cubre alguna de sus
     * preguntas. Un entregable de antes de la V68 (sin alcance) conserva lo marcado a mano.
     */
    public static boolean loMira(EntregableRequerido e, Set<Long> preguntasDelCriterio,
                                 Collection<Long> marcadosAMano, Collection<Long> cubiertas) {
        String alcance = e.getAlcance();
        if (alcance == null) {
            return marcadosAMano.contains(e.getId());
        }
        return switch (alcance) {
            case EntregableRequerido.PREGUNTA -> preguntasDelCriterio.contains(e.getPreguntaId());
            case EntregableRequerido.TODA_LA_PRUEBA -> true;
            default -> cubiertas.stream().anyMatch(preguntasDelCriterio::contains);
        };
    }

    private static Resultado armar(VersionBanco version, List<CriterioBanco> criterios,
                                   List<Pregunta> suyas, Map<Long, List<Opcion>> opcionesPorPregunta,
                                   List<EntregableRequerido> entregablesDeLaVersion,
                                   Mirados mirados,
                                   Map<Long, RespuestaPrueba> respuestaPorPregunta,
                                   Map<Long, NotaCriterioPrueba> notaPorCriterio) {
        Map<Long, List<PreguntaDeLaPrueba>> porCriterio = new LinkedHashMap<>();
        criterios.forEach(c -> porCriterio.put(c.getId(), new ArrayList<>()));
        List<PreguntaDeLaPrueba> sinCriterio = new ArrayList<>();
        for (Pregunta p : suyas) {
            PreguntaDeLaPrueba calculada = calcularPregunta(p,
                    opcionesPorPregunta.getOrDefault(p.getId(), List.of()),
                    respuestaPorPregunta.get(p.getId()));
            List<PreguntaDeLaPrueba> destino = p.getCriterioBancoId() == null ? null
                    : porCriterio.get(p.getCriterioBancoId());
            (destino == null ? sinCriterio : destino).add(calculada);
        }
        Comparator<PreguntaDeLaPrueba> enOrden = Comparator
                .comparing((PreguntaDeLaPrueba pc) -> pc.pregunta().getOrden() == null
                        ? Integer.MAX_VALUE : pc.pregunta().getOrden())
                .thenComparing(pc -> pc.pregunta().getId(), Comparator.nullsLast(Comparator.naturalOrder()));
        sinCriterio.sort(enOrden);

        Map<Long, List<Long>> cubreDeLaVersion = new HashMap<>();
        entregablesDeLaVersion.forEach(e -> {
            List<Long> suyasCubiertas = mirados.cubre().get(e.getId());
            if (suyasCubiertas != null) {
                cubreDeLaVersion.put(e.getId(), suyasCubiertas);
            }
        });
        List<CriterioDeLaPrueba> calculados = new ArrayList<>();
        for (CriterioBanco c : criterios) {
            List<PreguntaDeLaPrueba> deEste = porCriterio.get(c.getId());
            deEste.sort(enOrden);
            int sistemaMaximo = 0;
            BigDecimal sistema = BigDecimal.ZERO;
            for (PreguntaDeLaPrueba pc : deEste) {
                if (!pc.esAbierta()) {
                    sistemaMaximo += pc.maximo();
                    sistema = sistema.add(pc.obtenido() == null ? BigDecimal.ZERO : pc.obtenido());
                }
            }
            // Los que mira, en el orden de los entregables de la versión.
            Set<Long> aMano = Set.copyOf(mirados.aMano().getOrDefault(c.getId(), List.of()));
            Set<Long> susPreguntas = deEste.stream().map(pc -> pc.pregunta().getId())
                    .collect(Collectors.toSet());
            List<EntregableRequerido> loQueMira = entregablesDeLaVersion.stream()
                    .filter(e -> loMira(e, susPreguntas, aMano,
                            cubreDeLaVersion.getOrDefault(e.getId(), List.of())))
                    .toList();
            int calificada = parteCalificada(c, sistemaMaximo);
            calculados.add(new CriterioDeLaPrueba(c, deEste, loQueMira, sistemaMaximo, sistema,
                    calificada, calificada > 0 ? c.getCalificador() : null,
                    calificada > 0 ? notaPorCriterio.get(c.getId()) : null));
        }
        return new Resultado(version, calculados, sinCriterio, entregablesDeLaVersion,
                cubreDeLaVersion);
    }

    /**
     * La parte calificada de un criterio (V69): lo que vale menos lo que suman sus cerradas,
     * nunca por debajo de 0. Una versión de antes, sin total, la lee tal cual se guardó: así
     * sus notas no cambian.
     */
    public static int parteCalificada(CriterioBanco c, int cerradas) {
        if (c.getPuntosDelCriterio() != null) {
            return Math.max(0, c.getPuntosDelCriterio() - cerradas);
        }
        return c.getPuntosCalificados() == null ? 0 : c.getPuntosCalificados();
    }

    /**
     * Una pregunta de la prueba: en las cerradas, lo que sacó; en todas, si está respondida.
     *
     * <p>La cuenta de una cerrada es la misma de las preguntas propias
     * ({@link CalificacionPorPuntos#calcularPregunta}): la respuesta de la prueba se traduce a
     * la forma que aquella lee, sin guardarla.
     */
    public static PreguntaDeLaPrueba calcularPregunta(Pregunta p, List<Opcion> suyas,
                                                      RespuestaPrueba r) {
        Respuesta comoEvaluacion = r == null ? null : Respuesta.builder()
                .id(r.getId())
                .preguntaId(r.getPreguntaId())
                .opcionId(r.getOpcionId())
                .texto(r.getTexto())
                .detalle(r.getDetalle())
                .build();
        CalificacionPorPuntos.PreguntaCalculada calculada =
                CalificacionPorPuntos.calcularPregunta(p, suyas, comoEvaluacion, null);
        boolean abierta = ReglasDePuntos.ABIERTA.equals(p.getTipo());
        return new PreguntaDeLaPrueba(p, calculada.opciones(), r,
                abierta ? 0 : calculada.maximo(),
                abierta ? null : calculada.obtenido(),
                estaRespondida(p, calculada.opciones(), r));
    }

    /**
     * Si cuenta como respondida para poder entregar (decisión 11): un texto en blanco no
     * cuenta, una de opción necesita una opción suya marcada, y una múltiple al menos una.
     */
    public static boolean estaRespondida(Pregunta p, List<Opcion> suyas, RespuestaPrueba r) {
        if (r == null) {
            return false;
        }
        if (ReglasDePuntos.ABIERTA.equals(p.getTipo())) {
            return r.getTexto() != null && !r.getTexto().isBlank();
        }
        Set<Long> ids = suyas.stream().map(Opcion::getId).collect(Collectors.toSet());
        if (ReglasDePuntos.OPCION_MULTIPLE.equals(p.getTipo())) {
            Respuesta comoEvaluacion = Respuesta.builder().detalle(r.getDetalle()).build();
            return CalificacionPorPuntos.marcadasDe(comoEvaluacion).stream().anyMatch(ids::contains);
        }
        return r.getOpcionId() != null && ids.contains(r.getOpcionId());
    }
}
