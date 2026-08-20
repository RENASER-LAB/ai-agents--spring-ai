package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.messaging.TrabajoIaPublisher;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ver {@link ColaCalificacionIa}. */
@Service
@Slf4j
public class ColaCalificacionIaImpl implements ColaCalificacionIa {

    /**
     * El orden de la fila. Es una lista y no un campo en la base porque es una decisión de
     * diseño, no de configuración: el Perfil de Talento no puede armarse antes de que estén
     * las notas que resume, y eso no lo cambia nadie desde un panel.
     */
    private static final List<String> ORDEN = List.of(
            AgenteDatosCv.CODIGO_AGENTE, AgenteEvidenciaCv.CODIGO_AGENTE,
            AgenteEvaluador.CODIGO_AGENTE, AgentePotencialRiesgo.CODIGO_AGENTE);

    /**
     * La fila de la primera pasada. Empieza sacando los datos del candidato —que no cuestan
     * casi nada y son lo que hace legible la tabla— y no pasa por el evaluador, porque en
     * una criba nadie ha respondido todavía.
     */
    private static final List<String> ORDEN_RAPIDA = List.of(
            AgenteDatosCv.CODIGO_AGENTE, AgenteEvidenciaCv.CODIGO_AGENTE, AgentePotencialRiesgo.CODIGO_AGENTE);

    /**
     * El que cierra la etapa, y por eso el que dice si la calificación llegó al final.
     *
     * <p>Es el mismo en las dos filas y tiene que seguir siéndolo: es quien arma el Perfil de
     * Talento y mueve la postulación a {@code PERFIL_POR_CONFIRMAR}.
     */
    private static final String ULTIMO = AgentePotencialRiesgo.CODIGO_AGENTE;

    /**
     * Los dos que no van en fila con nadie.
     *
     * <p>Cada uno atiende una etapa posterior —la prueba del puesto y la simulación— y se
     * pide a mano, así que su «fila» tiene un solo paso. Se escriben igual que las otras dos
     * listas para que {@code encolar} no necesite un camino aparte.
     */
    private static final List<String> SOLO_LA_PRUEBA = List.of(AgentePruebaPuesto.CODIGO_AGENTE);
    private static final List<String> SOLO_LAS_PREGUNTAS = List.of(AgenteSimulacion.CODIGO_AGENTE);

    public static final String RAPIDA = "RAPIDA";
    public static final String FINA = "FINA";

    private final TrabajoIaRepository trabajos;
    private final RegistroTrabajosIa registro;
    private final TrabajoIaPublisher publicador;
    private final PuenteCalificacionIa puente;
    private final Map<String, AgenteSeleccion> agentes;
    private final boolean habilitada;
    private final int maxIntentos;
    private final Duration limiteColgado;

    public ColaCalificacionIaImpl(TrabajoIaRepository trabajos,
                                  RegistroTrabajosIa registro,
                                  TrabajoIaPublisher publicador,
                                  PuenteCalificacionIa puente,
                                  List<AgenteSeleccion> agentes,
                                  @Value("${renaser.ai.calificacion.habilitada:true}") boolean habilitada,
                                  @Value("${renaser.ai.calificacion.max-intentos:3}") int maxIntentos,
                                  @Value("${renaser.ai.calificacion.minutos-colgado:15}") int minutosColgado) {
        this.trabajos = trabajos;
        this.registro = registro;
        this.publicador = publicador;
        this.puente = puente;
        this.agentes = agentes.stream()
                .collect(Collectors.toMap(AgenteSeleccion::codigo, Function.identity()));
        this.habilitada = habilitada;
        this.maxIntentos = maxIntentos;
        this.limiteColgado = Duration.ofMinutes(minutosColgado);
    }

    @Override
    public boolean encolarPerfilIntegral(Long postulacionId) {
        if (!habilitada) {
            log.warn("La calificación con IA está apagada por configuración: la postulación {} "
                    + "se queda en PERFIL_CALIFICANDO", postulacionId);
            return false;
        }
        return encolar(postulacionId, ORDEN, FINA, 0, null);
    }

    @Override
    public boolean encolarCribaCv(Long postulacionId) {
        // Arranca por el mismo sitio: la diferencia no la decide quien llama, la decide el
        // candidato. Si no hay evaluación entregada, la fila se salta sola al evaluador.
        return encolarPerfilIntegral(postulacionId);
    }

    @Override
    public boolean encolarCribaRapida(Long postulacionId) {
        if (apagada(postulacionId)) {
            return false;
        }
        return encolar(postulacionId, ORDEN_RAPIDA, RAPIDA, 0, null);
    }

    @Override
    public boolean encolarCribaFina(Long postulacionId) {
        if (apagada(postulacionId)) {
            return false;
        }
        return encolar(postulacionId, ORDEN, FINA, 0, null);
    }

    @Override
    public boolean encolarPruebaPuesto(Long postulacionId) {
        if (apagada(postulacionId)) {
            return false;
        }
        return encolar(postulacionId, SOLO_LA_PRUEBA, FINA, 0, null);
    }

    @Override
    public boolean encolarPreguntasSimulacion(Long postulacionId) {
        if (apagada(postulacionId)) {
            return false;
        }
        return encolar(postulacionId, SOLO_LAS_PREGUNTAS, FINA, 0, null);
    }

    private boolean apagada(Long postulacionId) {
        if (!habilitada) {
            log.warn("La calificación con IA está apagada por configuración: la postulación {} "
                    + "no se encola", postulacionId);
            return true;
        }
        return false;
    }

    /** Qué fila sigue este trabajo, según de qué pasada sea. */
    private List<String> ordenDe(String modo) {
        return RAPIDA.equals(modo) ? ORDEN_RAPIDA : ORDEN;
    }

    @Override
    public void ejecutar(Long trabajoIaId) {
        Optional<TrabajoIa> tomado = registro.tomar(trabajoIaId);
        if (tomado.isEmpty()) {
            return;
        }
        TrabajoIa trabajo = tomado.get();
        AgenteSeleccion agente = agentes.get(trabajo.getAgenteCodigo());
        if (agente == null) {
            registro.fallar(trabajoIaId, 0,
                    "No hay ningún agente que sepa atender " + trabajo.getAgenteCodigo());
            return;
        }

        try {
            agente.ejecutar(trabajo);
            registro.terminar(trabajoIaId);
            encolarSiguiente(trabajo);
        } catch (RuntimeException e) {
            // Nunca se guarda una nota inventada ni se mueve la postulación: solo se anota
            // el fallo y, si queda intento, se vuelve a poner en la cola.
            if (registro.fallar(trabajoIaId, maxIntentos, mensaje(e))) {
                publicador.publicar(trabajoIaId);
            }
        }
    }

    @Override
    public Map<Long, Estado> estadoDe(List<Long> postulacionIds) {
        if (postulacionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<TrabajoIa>> porPostulacion =
                trabajos.findByPostulacionIdInOrderByIdAsc(postulacionIds).stream()
                        .collect(Collectors.groupingBy(TrabajoIa::getPostulacionId));

        Map<Long, Estado> salida = new HashMap<>();
        for (Long id : postulacionIds) {
            List<TrabajoIa> suyos = porPostulacion.getOrDefault(id, List.of());
            // Quien no tiene trabajos entra igual, con SIN_EMPEZAR: el ranking no puede
            // dejar fuera a un candidato porque nadie haya pedido calificarlo todavía.
            salida.put(id, new Estado(comoVan(suyos), pasadaDe(suyos)));
        }
        return salida;
    }

    @Override
    public String comoVa(Long postulacionId) {
        return comoVan(trabajos.findByPostulacionIdOrderByIdAsc(postulacionId));
    }

    /** La cuenta, ya con los trabajos delante. La comparten el uno y la tanda entera. */
    private String comoVan(List<TrabajoIa> todos) {
        // Solo los del retrato. Desde que existen los agentes de la prueba y de la
        // conversación final, una misma postulación puede tener trabajos de tres etapas
        // distintas, y esta pregunta es siempre la misma: cómo va SU retrato. Sin este
        // filtro, pedir las preguntas de la simulación dejaría el ranking entero diciendo
        // «en curso» por un trabajo que no tiene nada que ver con la nota que enseña.
        List<TrabajoIa> suyos = todos.stream()
                .filter(t -> ORDEN.contains(t.getAgenteCodigo()))
                .toList();
        if (suyos.isEmpty()) {
            return "SIN_EMPEZAR";
        }
        // Un trabajo vivo manda sobre todo lo demás: mientras quede uno, la calificación no
        // ha terminado, aunque los anteriores hayan salido bien.
        boolean vivo = suyos.stream()
                .anyMatch(t -> "PENDIENTE".equals(t.getEstado()) || "EN_CURSO".equals(t.getEstado()));
        if (vivo) {
            return "EN_CURSO";
        }

        // Solo cuenta la última pasada, y esto es lo importante de aquí. Un candidato puede
        // tener la rápida terminada y la fina fallida: mirarlas juntas encontraría el agente
        // que cierra la etapa TERMINADO en la rápida y diría «terminada», el contador de
        // fallidos marcaría cero, y unas notas provisionales se presentarían como
        // definitivas. Lo que vale es cómo fue el último intento; con qué pasada está
        // calificado ahora mismo lo dice pasadaDe, que es otra pregunta.
        String ultimoModo = suyos.get(suyos.size() - 1).getModo();
        List<TrabajoIa> tanda = suyos.stream()
                .filter(t -> Objects.equals(ultimoModo, t.getModo()))
                .toList();

        // Terminó cuando el que cierra la etapa terminó. No se cuentan los trabajos: una
        // criba de currículum tiene menos, porque el evaluador no tenía nada que puntuar, y
        // contar diría «en curso» para siempre.
        //
        // Y esto se mira ANTES que los fallos, no después: quien falló y luego salió bien
        // al reintentar arrastra su fila fallida para siempre, y preguntar primero por el
        // fallo dejaría marcado como fallido a un candidato que sí tiene su retrato.
        if (tanda.stream().anyMatch(t -> ULTIMO.equals(t.getAgenteCodigo())
                && "TERMINADO".equals(t.getEstado()))) {
            return "TERMINADA";
        }
        if (tanda.stream().anyMatch(t -> "FALLIDO".equals(t.getEstado()))) {
            return "FALLIDA";
        }
        return "EN_CURSO";
    }

    @Override
    public String pasadaDe(Long postulacionId) {
        return pasadaDe(trabajos.findByPostulacionIdOrderByIdAsc(postulacionId));
    }

    private String pasadaDe(List<TrabajoIa> suyos) {
        List<TrabajoIa> hechos = suyos.stream()
                .filter(t -> ULTIMO.equals(t.getAgenteCodigo()) && "TERMINADO".equals(t.getEstado()))
                .toList();
        // La fina manda aunque la rápida sea posterior: es la que pisa las notas.
        if (hechos.stream().anyMatch(t -> FINA.equals(t.getModo()))) {
            return FINA;
        }
        return hechos.isEmpty() ? null : RAPIDA;
    }

    @Override
    public void reintentarAtascados() {
        if (!habilitada) {
            return;
        }
        Instant limite = Instant.now().minus(limiteColgado);

        // Pendientes que llevan demasiado ahí: el mensaje se perdió, o RabbitMQ estaba caído
        // cuando se publicó. Volver a publicarlos es barato y el trabajo es idempotente.
        for (TrabajoIa trabajo : trabajos.findByEstadoAndCreadoEnBefore("PENDIENTE", limite)) {
            log.warn("El trabajo {} lleva pendiente desde {}: se vuelve a encolar",
                    trabajo.getId(), trabajo.getCreadoEn());
            publicador.publicar(trabajo.getId());
        }

        // En curso colgados: alguien lo tomó y no volvió. Pasa si el proceso murió a mitad.
        for (TrabajoIa trabajo : trabajos.findByEstadoAndTomadoEnBefore("EN_CURSO", limite)) {
            log.warn("El trabajo {} lleva en curso desde {} sin terminar: se devuelve a la cola",
                    trabajo.getId(), trabajo.getTomadoEn());
            registro.devolverAPendiente(trabajo.getId());
            publicador.publicar(trabajo.getId());
        }
    }

    /**
     * El siguiente de la fila, cuando uno acaba de terminar.
     *
     * <p>Si no queda ninguno, no hay nada que encolar: la postulación ya está en
     * {@code PERFIL_POR_CONFIRMAR}, esperando a una persona.
     */
    private void encolarSiguiente(TrabajoIa terminado) {
        List<String> orden = ordenDe(terminado.getModo());
        int posicion = orden.indexOf(terminado.getAgenteCodigo());
        if (posicion < 0) {
            return;
        }
        // Se pasa el id del que acaba de terminar: lo que venga después y ya estuviera hecho
        // de una vuelta anterior se calculó sin este resultado, así que se rehace.
        encolar(terminado.getPostulacionId(), orden, terminado.getModo(),
                posicion + 1, terminado.getId());
    }

    /**
     * Pone en la cola el paso que toca, mirando la fila desde {@code desde}.
     *
     * @return true si algo quedó encolado de verdad
     */
    private boolean encolar(Long postulacionId, List<String> orden, String modo,
                            int desde, Long alimentadoPor) {
        Optional<String> agente = pasoQueToca(postulacionId, orden, modo, desde, alimentadoPor);
        if (agente.isEmpty()) {
            return false;
        }
        Long organizacionId = puente.organizacionDe(postulacionId);
        Optional<TrabajoIa> creado = registro.crearSiHaceFalta(
                organizacionId, postulacionId, agente.get(), modo, alimentadoPor);
        creado.ifPresent(trabajo -> publicador.publicar(trabajo.getId()));
        return creado.isPresent();
    }

    /**
     * Cuál es el primer paso de la fila que de verdad hay que hacer.
     *
     * <p><b>Recorre la fila en vez de mirar solo el primero</b>, y eso arregla el fallo más
     * caro que tenía esto: un candidato ya cribado que después entregaba su evaluación se
     * quedaba en «calificando» para siempre. Se pedía la calificación, el primer paso ya
     * estaba TERMINADO de la criba, no se encolaba nada, y ningún botón lo rescataba.
     *
     * <p>Se detiene en cuanto encuentra un paso vivo: si hay uno en marcha la fila sigue
     * sola, y adelantarse pagaría dos veces por el mismo candidato.
     */
    private Optional<String> pasoQueToca(Long postulacionId, List<String> orden, String modo,
                                         int desde, Long alimentadoPor) {
        for (int i = desde; i < orden.size(); i++) {
            String agente = orden.get(i);
            if (seSalta(postulacionId, agente)) {
                continue;
            }
            Optional<TrabajoIa> ultimo = trabajos
                    .findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                            postulacionId, agente, modo);
            if (ultimo.isEmpty()) {
                return Optional.of(agente);
            }
            String estado = ultimo.get().getEstado();
            if ("PENDIENTE".equals(estado) || "EN_CURSO".equals(estado)) {
                return Optional.empty();
            }
            if ("FALLIDO".equals(estado)) {
                return Optional.of(agente);
            }
            // TERMINADO: solo se rehace si se hizo antes que aquello de lo que depende.
            if (alimentadoPor != null && ultimo.get().getId() < alimentadoPor) {
                return Optional.of(agente);
            }
        }
        return Optional.empty();
    }

    /**
     * Los dos pasos que a veces no tienen nada que hacer, y entonces no se pagan.
     *
     * <ul>
     *   <li><b>El evaluador</b> sin respuestas entregadas: en una criba de currículum nadie
     *       ha respondido, y llamarlo gastaría una petición al modelo para no puntuar nada.
     *       El Perfil de Talento se arma con lo que dejó el lector del currículum.
     *   <li><b>La ficha de datos</b> si ya está sacada. Son datos copiados del currículum, no
     *       notas: no cambian salvo que cambie el currículum, y quien lo reemplaza borra la
     *       ficha para que se vuelva a sacar. Así la ficha existe se llegue por donde se
     *       llegue, sin pagarla dos veces.
     * </ul>
     */
    private boolean seSalta(Long postulacionId, String agente) {
        if (AgenteEvaluador.CODIGO_AGENTE.equals(agente)
                && !puente.tieneEvaluacionEntregada(postulacionId)) {
            log.info("La postulación {} no tiene evaluación entregada: el evaluador se salta",
                    postulacionId);
            return true;
        }
        return AgenteDatosCv.CODIGO_AGENTE.equals(agente) && puente.tieneFichaCv(postulacionId);
    }

    private String mensaje(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
