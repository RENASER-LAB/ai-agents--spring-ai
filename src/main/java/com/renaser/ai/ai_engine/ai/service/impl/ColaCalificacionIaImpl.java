package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.messaging.TrabajoIaPublisher;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
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
     * Los tres que arman el retrato y <b>no dependen unos de otros</b>, así que van a la vez.
     *
     * <p>Se comprobó mirando qué lee y qué escribe cada uno: la ficha de datos y las notas
     * del currículum salen las dos del mismo texto recortado y se guardan en tablas
     * distintas —{@code dato_cv} y {@code nota_criterio}—, y el evaluador ni siquiera abre el
     * currículum: lee las respuestas de la evaluación y escribe {@code nota_respuesta}.
     * Ninguno de los tres mira lo que dejó otro.
     *
     * <p>En fila cada uno esperaba a que el anterior terminara de hablar con el proveedor, y
     * calificar a un candidato costaba ocho minutos y medio. A la vez cuesta lo que cueste el
     * más lento.
     *
     * <p>Es una lista y no un campo en la base porque es una decisión de diseño, no de
     * configuración: quién depende de quién no lo cambia nadie desde un panel.
     */
    private static final List<String> A_LA_VEZ = List.of(
            AgenteDatosCv.CODIGO_AGENTE, AgenteEvidenciaCv.CODIGO_AGENTE,
            AgenteEvaluador.CODIGO_AGENTE);

    /**
     * Los que van a la vez en la primera pasada. No entra el evaluador, porque en una criba
     * nadie ha respondido todavía.
     */
    private static final List<String> A_LA_VEZ_RAPIDA = List.of(
            AgenteDatosCv.CODIGO_AGENTE, AgenteEvidenciaCv.CODIGO_AGENTE);

    /**
     * El que cierra la etapa, y por eso el que dice si la calificación llegó al final.
     *
     * <p>Es el único que sí depende de los demás: arma el Perfil de Talento con lo que ellos
     * dejaron. Es el mismo en las dos pasadas y tiene que seguir siéndolo, porque además es
     * quien mueve la postulación a {@code PERFIL_POR_CONFIRMAR}.
     */
    private static final String ULTIMO = AgentePotencialRiesgo.CODIGO_AGENTE;

    /**
     * Los cuatro del retrato: los que van a la vez más el que cierra.
     *
     * <p>Sirve para una sola cosa —saber qué trabajos hablan del retrato y cuáles son de otra
     * etapa— y por eso incluye al evaluador aunque la pasada rápida no lo use.
     */
    private static final List<String> DEL_RETRATO = List.of(
            AgenteDatosCv.CODIGO_AGENTE, AgenteEvidenciaCv.CODIGO_AGENTE,
            AgenteEvaluador.CODIGO_AGENTE, AgentePotencialRiesgo.CODIGO_AGENTE);

    public static final String RAPIDA = "RAPIDA";
    public static final String FINA = "FINA";

    /** Qué hacer con un paso concreto cuando se mira la tanda. */
    private enum Situacion {
        /** Nunca se hizo, falló, o quedó viejo: hay que ponerlo en la cola. */
        HAY_QUE_ENCOLARLO,
        /** Está pendiente o corriendo. Ni se toca ni se da por terminado. */
        ESTA_VIVO,
        /** Ya está al día, o no tenía nada que hacer y se salta. */
        NO_HACE_FALTA
    }

    private final TrabajoIaRepository trabajos;
    private final RegistroTrabajosIa registro;
    private final TrabajoIaPublisher publicador;
    private final PuenteCalificacionIa puente;
    private final TopeMensualIa tope;
    private final OrganizacionRepository organizaciones;
    private final Map<String, AgenteSeleccion> agentes;
    private final boolean habilitada;
    private final int maxIntentos;
    private final Duration limiteColgado;

    public ColaCalificacionIaImpl(TrabajoIaRepository trabajos,
                                  RegistroTrabajosIa registro,
                                  TrabajoIaPublisher publicador,
                                  PuenteCalificacionIa puente,
                                  TopeMensualIa tope,
                                  OrganizacionRepository organizaciones,
                                  List<AgenteSeleccion> agentes,
                                  @Value("${renaser.ai.calificacion.habilitada:true}") boolean habilitada,
                                  @Value("${renaser.ai.calificacion.max-intentos:3}") int maxIntentos,
                                  @Value("${renaser.ai.calificacion.minutos-colgado:15}") int minutosColgado) {
        this.trabajos = trabajos;
        this.registro = registro;
        this.publicador = publicador;
        this.puente = puente;
        this.tope = tope;
        this.organizaciones = organizaciones;
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
        return encolarElRetrato(postulacionId, FINA);
    }

    @Override
    public boolean encolarCribaCv(Long postulacionId) {
        // Arranca por el mismo sitio: la diferencia no la decide quien llama, la decide el
        // candidato. Si no hay evaluación entregada, el evaluador se salta solo.
        return encolarPerfilIntegral(postulacionId);
    }

    @Override
    public boolean encolarCribaRapida(Long postulacionId) {
        if (apagada(postulacionId)) {
            return false;
        }
        return encolarElRetrato(postulacionId, RAPIDA);
    }

    @Override
    public boolean encolarCribaFina(Long postulacionId) {
        if (apagada(postulacionId)) {
            return false;
        }
        return encolarElRetrato(postulacionId, FINA);
    }

    @Override
    public boolean encolarPruebaPuesto(Long postulacionId) {
        if (apagada(postulacionId)) {
            return false;
        }
        return encolarSuelto(postulacionId, AgentePruebaPuesto.CODIGO_AGENTE);
    }

    @Override
    public boolean encolarPreguntasSimulacion(Long postulacionId) {
        if (apagada(postulacionId)) {
            return false;
        }
        return encolarSuelto(postulacionId, AgenteSimulacion.CODIGO_AGENTE);
    }

    private boolean apagada(Long postulacionId) {
        if (!habilitada) {
            log.warn("La calificación con IA está apagada por configuración: la postulación {} "
                    + "no se encola", postulacionId);
            return true;
        }
        return false;
    }

    /** Los que van a la vez en esta pasada. */
    private List<String> aLaVezDe(String modo) {
        return RAPIDA.equals(modo) ? A_LA_VEZ_RAPIDA : A_LA_VEZ;
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
            intentarElRetrato(trabajo);
        } catch (RuntimeException e) {
            // Nunca se guarda una nota inventada ni se mueve la postulación: solo se anota
            // el fallo y, si queda intento, se vuelve a poner en la cola.
            if (registro.fallar(trabajoIaId, maxIntentos, mensaje(e))) {
                publicador.publicar(trabajoIaId);
                return;
            }
            // Se agotaron los intentos y este paso ya no va a dar nada. Aquí está el arreglo
            // que más se nota: antes la fila se cortaba justo aquí y el candidato se quedaba
            // en PERFIL_CALIFICANDO para siempre —con su examen de cincuenta preguntas ya
            // calificado y sin nadie que armara el retrato— porque su currículum era un PDF
            // escaneado del que no sale texto. Pasa en cuatro de cada ciento dieciséis. El
            // retrato tiene que salir igual, con lo que sí se pudo leer.
            intentarElRetrato(trabajo);
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
    @Override
    public String comoVaLaLectura(Long postulacionId) {
        // Solo el ULTIMO trabajo de lectura: quien fallo y luego salio bien al reintentar
        // arrastra su fila fallida para siempre, y mirar todas diria «no se pudo leer» de un
        // curriculum que si esta leido.
        Optional<TrabajoIa> ultimo = trabajos.findByPostulacionIdOrderByIdAsc(postulacionId)
                .stream()
                .filter(t -> AgenteDatosCv.CODIGO_AGENTE.equals(t.getAgenteCodigo()))
                .reduce((a, b) -> b);
        if (ultimo.isEmpty()) {
            return "SIN_EMPEZAR";
        }
        return switch (ultimo.get().getEstado()) {
            // EN_ESPERA (tope de IA) también es «en curso» para el dueño del perfil: su
            // lectura va a salir, solo que cuando su empresa recupere cupo.
            case "PENDIENTE", "EN_CURSO", "EN_ESPERA" -> "EN_CURSO";
            case "FALLIDO" -> "FALLIDA";
            default -> "TERMINADA";
        };
    }

    private String comoVan(List<TrabajoIa> todos) {
        // Solo los del retrato. Desde que existen los agentes de la prueba y de la
        // conversación final, una misma postulación puede tener trabajos de tres etapas
        // distintas, y esta pregunta es siempre la misma: cómo va SU retrato. Sin este
        // filtro, pedir las preguntas de la simulación dejaría el ranking entero diciendo
        // «en curso» por un trabajo que no tiene nada que ver con la nota que enseña.
        List<TrabajoIa> suyos = todos.stream()
                .filter(t -> DEL_RETRATO.contains(t.getAgenteCodigo()))
                .toList();
        if (suyos.isEmpty()) {
            return "SIN_EMPEZAR";
        }
        // Un trabajo vivo manda sobre todo lo demás: mientras quede uno, la calificación no
        // ha terminado, aunque los anteriores hayan salido bien. EN_ESPERA cuenta como
        // vivo y se enseña como EN_CURSO: al candidato le es verdad —su calificación está
        // en camino—, y que su empresa agotó el tope del mes no es asunto que se le cuente
        // a él (pieza E).
        boolean vivo = suyos.stream()
                .anyMatch(t -> "PENDIENTE".equals(t.getEstado()) || "EN_CURSO".equals(t.getEstado())
                        || "EN_ESPERA".equals(t.getEstado()));
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
        // contar diría «en curso» para siempre. Desde que los tres primeros van a la vez hay
        // una razón más: uno de ellos puede haber fallado y el retrato haberse armado igual
        // con menos evidencia, y eso es una calificación terminada, no una fallida.
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

        despertarLosQueEsperan();
    }

    /**
     * Despierta los trabajos congelados cuyas organizaciones ya tienen cupo Y están
     * activas: el tope subió, empezó el mes (pieza E), o Renaser reactivó la empresa
     * (pieza F). «La cola arranca sola con lo que quedó esperando» — este barrido es ese
     * arranque, y corre con el mismo sondeo de los atascados.
     *
     * <p>Una organización suspendida NO despierta aunque le sobre cupo: suspendida es
     * congelada, y despertarle trabajos sería gastar en el modelo por una empresa a la
     * que Renaser le cerró la puerta. Sus EN_ESPERA no son zombis: el mismo barrido los
     * suelta en el primer ciclo tras la reactivación.
     *
     * <p>El cupo se pregunta UNA vez por organización, no por trabajo: la suma del mes es
     * la parte cara. Y al despertar se sueltan TODOS los de esa organización aunque el
     * primero vuelva a comerse el cupo: lo que quedó esperando ya se prometió, y el tope
     * frena lo nuevo, no lo prometido — la misma regla que el retrato.
     */
    private void despertarLosQueEsperan() {
        Map<Long, List<TrabajoIa>> esperando = trabajos.findByEstadoOrderByIdAsc("EN_ESPERA")
                .stream()
                .collect(Collectors.groupingBy(TrabajoIa::getOrganizacionId));
        esperando.forEach((organizacionId, suyos) -> {
            if (suspendida(organizacionId) || tope.sinCupo(organizacionId)) {
                return;
            }
            for (TrabajoIa trabajo : suyos) {
                if (registro.despertar(trabajo.getId())) {
                    publicador.publicar(trabajo.getId());
                }
            }
            log.info("La organización {} recuperó cupo de IA: {} trabajos despiertan",
                    organizacionId, suyos.size());
        });
    }

    /**
     * Pone en la cola la tanda del retrato: los que van a la vez, todos de golpe.
     *
     * <p><b>El que cierra no se encola aquí.</b> Lo dispara quien termine el último, mirando
     * la base. Encolarlo ahora lo armaría con la mitad de lo que va a haber.
     *
     * @return true si algo quedó encolado de verdad
     */
    private boolean encolarElRetrato(Long postulacionId, String modo) {
        boolean alguienVivo = false;
        boolean alguienIntentado = false;
        boolean alguienEncolado = false;

        for (String agente : aLaVezDe(modo)) {
            switch (situacionDe(postulacionId, agente, modo, null)) {
                case ESTA_VIVO -> alguienVivo = true;
                case HAY_QUE_ENCOLARLO -> {
                    alguienIntentado = true;
                    alguienEncolado |= crearYAvisar(postulacionId, agente, modo, null);
                }
                case NO_HACE_FALTA -> { }
            }
        }

        if (alguienVivo || alguienIntentado) {
            return alguienEncolado;
        }

        // Ninguno de los tres tiene nada que hacer y aun así alguien pidió calificar. O el
        // retrato falló, o se quedó sin hacer —una tanda de cuando esto era una fila y se
        // cortaba en el primer fallo—. La barrera lo resuelve mirando la base: si de verdad
        // falta, lo crea; y si no falta, preguntarlo no cuesta nada.
        return dispararElRetrato(postulacionId, modo, null);
    }

    /**
     * Los dos que no van con nadie: la prueba del puesto y las preguntas de la conversación.
     *
     * <p>Cada uno atiende una etapa posterior y se pide a mano. Se mira igual que los demás
     * para no pagarlos dos veces, pero detrás de ellos no va nadie.
     */
    @Override
    public boolean encolarDatosCv(Long postulacionId) {
        // El interruptor manda tambien aqui: apagada la calificacion, postular no encola
        // nada — ni en las pruebas ni cuando el proveedor este caido.
        if (!habilitada) {
            return false;
        }
        // El mismo camino de los sueltos: se mira antes de crear para no pagar dos veces,
        // y seSalta ya sabe que una postulacion con ficha leida no vuelve a leerse.
        return encolarSuelto(postulacionId, AgenteDatosCv.CODIGO_AGENTE);
    }

    private boolean encolarSuelto(Long postulacionId, String agente) {
        if (situacionDe(postulacionId, agente, FINA, null) != Situacion.HAY_QUE_ENCOLARLO) {
            return false;
        }
        return crearYAvisar(postulacionId, agente, FINA, null);
    }

    /**
     * La barrera, vista desde fuera: si el que acaba de terminar era uno de los que van a la
     * vez, se pregunta si ya se puede armar el retrato.
     *
     * <p>Se llama tanto cuando el agente sale bien como cuando se agota en reintentos, y por
     * la misma razón: en los dos casos ese paso ya no va a dar nada más, y si era el último
     * que quedaba vivo, el retrato tiene que salir.
     *
     * <p><b>Preguntan los tres, no solo el que cree ser el último</b>, porque desde aquí no
     * hay forma de saber quién es el último: los tres corren a la vez y pueden estar en
     * instancias distintas. Quien contesta es la base, y contesta que sí una sola vez.
     */
    private void intentarElRetrato(TrabajoIa acabado) {
        if (!aLaVezDe(acabado.getModo()).contains(acabado.getAgenteCodigo())) {
            // El que cierra la etapa y los dos sueltos no tienen a nadie detrás.
            return;
        }
        // La lectura de datos que dispara postular (el perfil del candidato) va SOLA: si al
        // terminar no hay ningún hermano de la tanda —ni vivo ni terminado—, nadie pidió
        // calificar todavía y armar el retrato aquí sería pagarlo antes de tiempo y sin
        // evaluación. Cuando una criba o una entrega encolen a los demás, la barrera de
        // siempre hará su trabajo.
        if (AgenteDatosCv.CODIGO_AGENTE.equals(acabado.getAgenteCodigo())) {
            boolean sinHermanos = trabajos
                    .findByPostulacionIdOrderByIdAsc(acabado.getPostulacionId()).stream()
                    .noneMatch(t -> !t.getId().equals(acabado.getId())
                            && aLaVezDe(acabado.getModo()).contains(t.getAgenteCodigo()));
            if (sinHermanos) {
                return;
            }
        }
        dispararElRetrato(acabado.getPostulacionId(), acabado.getModo(), acabado.getId());
    }

    private boolean dispararElRetrato(Long postulacionId, String modo, Long alimentadoPor) {
        Long organizacionId = puente.organizacionDe(postulacionId);
        Optional<TrabajoIa> creado = registro.crearElRetratoSiLosDemasAcabaron(
                organizacionId, postulacionId, aLaVezDe(modo), ULTIMO, modo, alimentadoPor);
        creado.ifPresent(trabajo -> publicador.publicar(trabajo.getId()));
        return creado.isPresent();
    }

    /**
     * Crea el trabajo y lo publica a la cola — o lo deja EN_ESPERA si la organización
     * agotó su tope mensual de IA (pieza E) o está suspendida (pieza F).
     *
     * <p>Este es el embudo de los trabajos NUEVOS, y por eso el tope se pregunta aquí y
     * no al ejecutar: lo ya publicado es una promesa y termina. El retrato que cierra una
     * tanda ({@code dispararElRetrato}) tampoco pasa por aquí a propósito: sus tres
     * insumos ya se pagaron, y dejarlos resumidos cuesta una llamada que no frena nada.
     *
     * <p>La suspensión congela con la misma mecánica que el tope, y por la misma razón
     * coherente: lo en vuelo (PENDIENTE/EN_CURSO ya publicado) termina, lo nuevo espera.
     * Un candidato que ya estaba dentro puede seguir entregando —su evaluación, su
     * prueba— y eso pide calificaciones nuevas; gastarlas en el modelo mientras Renaser
     * tiene la puerta cerrada sería pagar por una empresa congelada. Al reactivar, el
     * barrido las suelta solo.
     *
     * <p>El candidato no ve la espera: para {@code comoVan} un EN_ESPERA está EN_CURSO,
     * que es la verdad — se va a calificar, solo que cuando haya cupo o cambie el mes.
     */
    private boolean crearYAvisar(Long postulacionId, String agente, String modo,
                                 Long alimentadoPor) {
        Long organizacionId = puente.organizacionDe(postulacionId);
        Optional<TrabajoIa> creado = registro.crearSiHaceFalta(
                organizacionId, postulacionId, agente, modo, alimentadoPor);
        if (creado.isEmpty()) {
            return false;
        }
        // La suspensión se mira primero: es una lectura por clave primaria, y a una
        // organización congelada no se le paga ni la suma del consumo del mes.
        String freno = suspendida(organizacionId) ? "está suspendida"
                : tope.sinCupo(organizacionId) ? "agotó su tope mensual de IA" : null;
        if (freno != null && registro.dejarEnEspera(creado.get().getId())) {
            log.warn("La organización {} {}: el trabajo {} ({}) queda EN_ESPERA hasta que "
                            + "el barrido la encuentre con cupo y activa",
                    organizacionId, freno, creado.get().getId(), agente);
            return true;
        }
        publicador.publicar(creado.get().getId());
        // El aviso del 80% se dispara donde el gasto crece: al encolar con cupo. Manda
        // una sola vez por mes y jamás rompe el encolado: corre en su propia transacción
        // (REQUIRES_NEW) y cualquier tropiezo suyo se anota y se traga — la postulación
        // que lo disparó no puede caerse por una campana.
        try {
            tope.avisarSiCruzaElUmbral(organizacionId);
        } catch (RuntimeException e) {
            log.error("El aviso del 80% del tope de IA de la organización {} falló y se ignora: "
                    + "el encolado del trabajo {} sigue en pie. Motivo: {}",
                    organizacionId, creado.get().getId(), mensaje(e));
        }
        return true;
    }

    /**
     * Si la organización está suspendida (pieza F): congelada, también para la IA nueva.
     * Una organización que no existe no puede frenar nada — no debería pasar, y si pasa
     * el trabajo seguirá el camino normal y fallará donde se vea.
     */
    private boolean suspendida(Long organizacionId) {
        return organizaciones.findById(organizacionId)
                .map(organizacion -> !organizacion.isEsActiva())
                .orElse(false);
    }

    /**
     * En qué situación está un paso concreto de la tanda.
     *
     * <p><b>Se pregunta antes de crear nada</b>, y eso arregla el fallo más caro que tuvo
     * esto: un candidato ya cribado que después entregaba su evaluación se quedaba en
     * «calificando» para siempre. Ahora los pasos ya hechos se dan por hechos y el que falta
     * se encola solo, sin que ningún botón tenga que rescatarlo.
     *
     * <p>Un paso vivo tampoco se toca: hay uno en marcha y adelantarse pagaría dos veces por
     * el mismo candidato.
     */
    private Situacion situacionDe(Long postulacionId, String agente, String modo,
                                  Long alimentadoPor) {
        if (seSalta(postulacionId, agente)) {
            return Situacion.NO_HACE_FALTA;
        }
        Optional<TrabajoIa> ultimo = trabajos
                .findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                        postulacionId, agente, modo);
        if (ultimo.isEmpty()) {
            return Situacion.HAY_QUE_ENCOLARLO;
        }
        String estado = ultimo.get().getEstado();
        // EN_ESPERA está vivo: va a correr cuando su organización recupere cupo (pieza
        // E), y crearle un gemelo sería pagar dos veces al despertar.
        if ("PENDIENTE".equals(estado) || "EN_CURSO".equals(estado) || "EN_ESPERA".equals(estado)) {
            return Situacion.ESTA_VIVO;
        }
        if ("FALLIDO".equals(estado)) {
            return Situacion.HAY_QUE_ENCOLARLO;
        }
        // TERMINADO: solo se rehace si se hizo antes que aquello de lo que depende.
        return alimentadoPor != null && ultimo.get().getId() < alimentadoPor
                ? Situacion.HAY_QUE_ENCOLARLO
                : Situacion.NO_HACE_FALTA;
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
