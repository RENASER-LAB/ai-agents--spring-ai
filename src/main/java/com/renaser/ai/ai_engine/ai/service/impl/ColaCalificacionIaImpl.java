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
            AgenteEvidenciaCv.CODIGO, AgenteEvaluador.CODIGO, AgentePotencialRiesgo.CODIGO);

    /**
     * La fila de la primera pasada. Empieza sacando los datos del candidato —que no cuestan
     * casi nada y son lo que hace legible la tabla— y no pasa por el evaluador, porque en
     * una criba nadie ha respondido todavía.
     */
    private static final List<String> ORDEN_RAPIDA = List.of(
            AgenteDatosCv.CODIGO, AgenteEvidenciaCv.CODIGO, AgentePotencialRiesgo.CODIGO);

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
    public void encolarPerfilIntegral(Long postulacionId) {
        if (!habilitada) {
            log.warn("La calificación con IA está apagada por configuración: la postulación {} "
                    + "se queda en PERFIL_CALIFICANDO", postulacionId);
            return;
        }
        encolar(postulacionId, ORDEN.get(0), FINA);
    }

    @Override
    public void encolarCribaCv(Long postulacionId) {
        // Arranca por el mismo sitio: la diferencia no la decide quien llama, la decide el
        // candidato. Si no hay evaluación entregada, la fila se salta sola al evaluador.
        encolarPerfilIntegral(postulacionId);
    }

    @Override
    public void encolarCribaRapida(Long postulacionId) {
        if (apagada(postulacionId)) {
            return;
        }
        encolar(postulacionId, ORDEN_RAPIDA.get(0), RAPIDA);
    }

    @Override
    public void encolarCribaFina(Long postulacionId) {
        if (apagada(postulacionId)) {
            return;
        }
        encolar(postulacionId, ORDEN.get(0), FINA);
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
    private String comoVan(List<TrabajoIa> suyos) {
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
        // Terminó cuando el último de la fila terminó. No se cuentan los trabajos: una
        // criba de currículum tiene dos y no tres, porque el evaluador no tenía nada que
        // puntuar, y contar diría «en curso» para siempre.
        //
        // Y esto se mira ANTES que los fallos, no después: quien falló y luego salió bien
        // al reintentar arrastra su fila fallida para siempre, y preguntar primero por el
        // fallo dejaría marcado como fallido a un candidato que sí tiene su retrato.
        String ultimo = ORDEN.get(ORDEN.size() - 1);
        if (suyos.stream().anyMatch(t -> ultimo.equals(t.getAgenteCodigo())
                && "TERMINADO".equals(t.getEstado()))) {
            return "TERMINADA";
        }
        if (suyos.stream().anyMatch(t -> "FALLIDO".equals(t.getEstado()))) {
            return "FALLIDA";
        }
        return "EN_CURSO";
    }

    @Override
    public String pasadaDe(Long postulacionId) {
        return pasadaDe(trabajos.findByPostulacionIdOrderByIdAsc(postulacionId));
    }

    private String pasadaDe(List<TrabajoIa> suyos) {
        String ultimo = ORDEN.get(ORDEN.size() - 1);
        List<TrabajoIa> hechos = suyos.stream()
                .filter(t -> ultimo.equals(t.getAgenteCodigo()) && "TERMINADO".equals(t.getEstado()))
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
     * El siguiente de la fila.
     *
     * <p>Si el que acaba de terminar era el último, no hay nada que encolar: la postulación
     * ya está en {@code PERFIL_POR_CONFIRMAR}, esperando a una persona.
     */
    private void encolarSiguiente(TrabajoIa terminado) {
        List<String> orden = ordenDe(terminado.getModo());
        int posicion = orden.indexOf(terminado.getAgenteCodigo());
        if (posicion < 0 || posicion == orden.size() - 1) {
            return;
        }
        String siguiente = orden.get(posicion + 1);

        // El evaluador solo tiene sentido si hay respuestas. En una criba de currículum no
        // las hay, y llamarlo gastaría una petición al modelo para no puntuar nada: se
        // salta y el Perfil de Talento se arma con lo que dejó el lector del currículum.
        if (AgenteEvaluador.CODIGO.equals(siguiente)
                && !puente.tieneEvaluacionEntregada(terminado.getPostulacionId())) {
            log.info("La postulación {} no tiene evaluación entregada: el evaluador se salta",
                    terminado.getPostulacionId());
            // Si el evaluador fuera el último de la fila no habría a quién saltar: se
            // acabó el recorrido. Hoy va en medio, pero la fila puede crecer y salirse
            // del índice aquí sería un fallo que solo aparecería ese día.
            if (posicion + 2 >= orden.size()) {
                return;
            }
            siguiente = orden.get(posicion + 2);
        }
        encolar(terminado.getPostulacionId(), siguiente, terminado.getModo());
    }

    private void encolar(Long postulacionId, String agenteCodigo, String modo) {
        Long organizacionId = puente.organizacionDe(postulacionId);
        registro.crearSiHaceFalta(organizacionId, postulacionId, agenteCodigo, modo)
                .ifPresent(trabajo -> publicador.publicar(trabajo.getId()));
    }

    private String mensaje(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
