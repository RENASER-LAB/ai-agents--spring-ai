package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Los cambios de estado de la cola, cada uno en su propia transacción.
 *
 * <p><b>Por qué es una clase aparte.</b> La llamada al modelo tarda decenas de segundos, y
 * durante ese rato no puede haber una transacción abierta reteniendo una conexión de la
 * base. Así que quien orquesta —{@code ColaCalificacionIaImpl}— no es transaccional, y marca
 * los estados llamando aquí. Si estos métodos vivieran en la misma clase, Spring no los
 * envolvería (una llamada a un método propio no pasa por el proxy) y no habría transacción
 * ninguna.
 *
 * <p>{@code REQUIRES_NEW} en marcar el fallo: se escribe aunque lo que estuviera corriendo
 * haya dejado la transacción del negocio para deshacer. Un fallo que no queda escrito es un
 * trabajo que nadie vuelve a mirar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistroTrabajosIa {

    private final TrabajoIaRepository trabajos;

    /**
     * Crea el trabajo de un agente si de verdad hace falta.
     *
     * <p>Hace falta cuando nunca se hizo, cuando el último intento se agotó en reintentos, o
     * cuando <b>lo que ya estaba hecho quedó viejo</b>. Esto último es lo que permite que un
     * candidato ya cribado se recalifique al entregar su evaluación: el retrato existía, pero
     * se armó sin las respuestas, así que hay que rehacerlo.
     *
     * @param alimentadoPor el trabajo cuyo resultado entra en este. Si el trabajo que ya
     *                      existe es anterior a él, se hizo con información vieja y se
     *                      rehace. Va nulo cuando nadie lo alimenta —el primer paso de una
     *                      fila—, y entonces lo ya terminado se respeta.
     * @return vacío si no hace falta: ya está al día, o ya hay uno en marcha
     */
    @Transactional
    public Optional<TrabajoIa> crearSiHaceFalta(Long organizacionId, Long postulacionId,
                                                String agenteCodigo, String modo,
                                                Long alimentadoPor) {
        // La búsqueda incluye el modo: sin eso la pasada fina encontraría el trabajo que ya
        // hizo la rápida y no correría nunca, que es justo lo contrario de lo que se pide.
        Optional<TrabajoIa> existente = trabajos
                .findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                        postulacionId, agenteCodigo, modo);
        if (existente.isPresent() && !hayQueRehacerlo(existente.get(), alimentadoPor)) {
            return Optional.empty();
        }
        return Optional.of(trabajos.save(TrabajoIa.builder()
                .organizacionId(organizacionId)
                .agenteCodigo(agenteCodigo)
                .modo(modo)
                .postulacionId(postulacionId)
                .referenciaTabla("postulacion")
                .referenciaId(postulacionId)
                .estado("PENDIENTE")
                .intentos(0)
                .creadoEn(Instant.now())
                .build()));
    }

    /**
     * Crea un trabajo que cuelga de una VACANTE, no de una postulación (el REDACTOR).
     *
     * <p>Regenerar es legítimo —cada generación pedida es un trabajo nuevo—, así que lo
     * TERMINADO o FALLIDO no exime: solo frena uno VIVO, para que dos clics seguidos no
     * paguen dos llamadas por el mismo borrador.
     */
    @Transactional
    public Optional<TrabajoIa> crearParaVacante(Long organizacionId, String agenteCodigo,
                                                Long vacanteId, String modo) {
        boolean vivo = trabajos
                .findFirstByReferenciaTablaAndReferenciaIdAndAgenteCodigoOrderByIdDesc(
                        "vacante", vacanteId, agenteCodigo)
                .map(t -> "PENDIENTE".equals(t.getEstado()) || "EN_CURSO".equals(t.getEstado())
                        || "EN_ESPERA".equals(t.getEstado()))
                .orElse(false);
        if (vivo) {
            return Optional.empty();
        }
        // saveAndFlush: si dos peticiones pasaron el chequeo a la vez, el índice único
        // parcial de V42 revienta AQUÍ (dentro de esta transacción) y quien llama lo
        // convierte en «no se encoló» — no en dos llamadas pagadas.
        return Optional.of(trabajos.saveAndFlush(TrabajoIa.builder()
                .organizacionId(organizacionId)
                .agenteCodigo(agenteCodigo)
                .modo(modo)
                .referenciaTabla("vacante")
                .referenciaId(vacanteId)
                .estado("PENDIENTE")
                .intentos(0)
                .creadoEn(Instant.now())
                .build()));
    }

    /**
     * <b>La barrera.</b> Crea el trabajo que cierra la etapa, pero solo si los que corren a
     * la vez ya acabaron todos.
     *
     * <p>Los tres primeros agentes no dependen unos de otros y se encolan de golpe, así que
     * el cuarto no lo puede encolar «el siguiente de la fila»: lo tiene que disparar el
     * último en terminar. El problema es que ninguno de los tres sabe si es el último. Por
     * eso <b>preguntan los tres</b>, y quien decide es la base.
     *
     * <p><b>Por qué no se puede disparar dos veces.</b> Lo primero que hace es bloquear las
     * filas de los tres. Si dos terminan a la vez, uno entra y el otro se queda esperando en
     * esa línea; cuando el primero confirma y suelta, el segundo sigue, y la consulta de
     * {@code crearSiHaceFalta} —que es una consulta nueva, y por eso ve lo que el otro acabó
     * de guardar— encuentra el retrato ya creado y devuelve vacío. La cuenta no vive en
     * memoria a propósito: hay ocho consumidores y pueden estar en instancias distintas.
     *
     * <p><b>Un fallo no para la fila.</b> Un paso agotado en reintentos no está vivo, así que
     * no bloquea a nadie: el retrato se arma con lo que sí se pudo leer. Antes, un currículum
     * escaneado sin texto dejaba al candidato en {@code PERFIL_CALIFICANDO} para siempre, con
     * su examen de cincuenta preguntas ya calificado y sin nadie que lo resumiera.
     *
     * <p><b>Lo que sí se exige es que algo haya salido bien.</b> Con los tres fallidos no hay
     * absolutamente nada que resumir, y armar un retrato sobre la nada sería inventarse una
     * nota, que es justo lo que la Regla 3 prohíbe. En ese caso la tanda se queda fallida y
     * se ve como fallida, que es lo que hace que alguien vuelva a pedirla.
     *
     * @param aLaVez los agentes que corren a la vez en esta pasada. Son dos en la rápida y
     *               tres en la fina: el evaluador no entra donde nadie ha respondido nada
     * @return vacío si todavía queda alguno trabajando, si no hay nada de lo que armar el
     *         retrato, o si otro se adelantó y ya lo creó
     */
    @Transactional
    public Optional<TrabajoIa> crearElRetratoSiLosDemasAcabaron(
            Long organizacionId, Long postulacionId, List<String> aLaVez,
            String agenteCodigo, String modo, Long alimentadoPor) {

        // Cierra la puerta antes de mirar nada. Ver bloquearLosQueVanALaVez.
        List<TrabajoIa> tanda = trabajos.bloquearLosQueVanALaVez(postulacionId, modo, aLaVez);

        // De cada agente solo cuenta su último intento: quien falló y luego salió bien al
        // reintentar arrastra su fila fallida para siempre, y mirarla contaría un fallo que
        // ya no existe.
        Map<String, TrabajoIa> ultimoDeCada = tanda.stream()
                .collect(Collectors.toMap(TrabajoIa::getAgenteCodigo, Function.identity(),
                        (a, b) -> a.getId() >= b.getId() ? a : b));

        // EN_ESPERA también es «vivo»: un paso congelado por el tope de IA (pieza E) va a
        // correr cuando haya cupo, y armar el retrato sin él sería armarlo con la mitad.
        List<String> vivos = conEstado(ultimoDeCada, "PENDIENTE", "EN_CURSO", "EN_ESPERA");
        if (!vivos.isEmpty()) {
            log.debug("El retrato de la postulación {} todavía espera a {}", postulacionId, vivos);
            return Optional.empty();
        }

        if (conEstado(ultimoDeCada, "TERMINADO").isEmpty()) {
            log.error("La postulación {} se queda sin Perfil de Talento: no salió bien ni uno de "
                            + "los pasos que lo alimentan ({}). NO se le inventa un retrato sobre "
                            + "la nada; hay que volver a pedir la calificación",
                    postulacionId, aLaVez);
            return Optional.empty();
        }

        List<String> fallidos = conEstado(ultimoDeCada, "FALLIDO");
        if (!fallidos.isEmpty()) {
            // Queda escrito a propósito y con nombre y apellido: el retrato que salga de aquí
            // se decidió con menos evidencia de la normal. Quien lo lea después tiene que
            // poder saber qué faltaba, y en el propio Perfil de Talento eso se ve en la
            // confianza de la evidencia, que baja sola porque el insumo llega con huecos.
            log.warn("El Perfil de Talento de la postulación {} se arma SIN lo que debían dejar "
                            + "{}: esos pasos se agotaron en reintentos. Se sigue igual, con lo que "
                            + "hay, y la confianza de la evidencia sale más baja",
                    postulacionId, fallidos);
        }

        // Se llama al método de al lado sin pasar por el proxy, y aquí eso es lo correcto:
        // ya estamos dentro de la transacción que abrió la barrera, que es justo donde tiene
        // que hacerse la comprobación de «¿ya existe?» para que no se creen dos.
        return crearSiHaceFalta(organizacionId, postulacionId, agenteCodigo, modo, alimentadoPor);
    }

    /** Qué agentes de la tanda están en alguno de estos estados, por su último intento. */
    private List<String> conEstado(Map<String, TrabajoIa> ultimoDeCada, String... estados) {
        List<String> buscados = List.of(estados);
        return ultimoDeCada.entrySet().stream()
                .filter(e -> buscados.contains(e.getValue().getEstado()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * Si un trabajo que ya existe se queda corto y hay que volver a correrlo.
     *
     * <p>Dos casos, y ninguno más. Un <b>FALLIDO</b> se puede reintentar: es lo que permite
     * reencolar a mano una postulación que se colgó por un problema del proveedor. Y un
     * <b>TERMINADO anterior a quien lo alimenta</b> está viejo: se calculó antes de que
     * existiera el dato del que depende. Los ids crecen, así que «anterior» es «id menor».
     *
     * <p>Lo que está PENDIENTE o EN_CURSO no se toca nunca: hay uno vivo y duplicarlo sería
     * pagarle dos veces al proveedor por el mismo candidato.
     */
    private boolean hayQueRehacerlo(TrabajoIa existente, Long alimentadoPor) {
        if ("FALLIDO".equals(existente.getEstado())) {
            return true;
        }
        return "TERMINADO".equals(existente.getEstado())
                && alimentadoPor != null
                && existente.getId() < alimentadoPor;
    }

    /**
     * Lo pasa a EN_CURSO y suma un intento.
     *
     * @return vacío si otro ya lo tomó, o si ya estaba terminado. Es lo que hace que un
     *         mensaje entregado dos veces no califique dos veces.
     */
    @Transactional
    public Optional<TrabajoIa> tomar(Long trabajoIaId) {
        // El cambio de estado y la condición van juntos en una sola sentencia: es lo que
        // impide que dos consumidores llamen al modelo por el mismo candidato.
        if (trabajos.tomarSiEstaPendiente(trabajoIaId, Instant.now()) == 0) {
            log.info("El trabajo {} ya no estaba pendiente: lo tomó otro, o ya terminó",
                    trabajoIaId);
            return Optional.empty();
        }
        return trabajos.findById(trabajoIaId);
    }

    @Transactional
    public void terminar(Long trabajoIaId) {
        trabajos.findById(trabajoIaId).ifPresent(trabajo -> {
            trabajo.setEstado("TERMINADO");
            trabajo.setTerminadoEn(Instant.now());
            trabajos.save(trabajo);
        });
    }

    /**
     * Marca el fallo y decide si se vuelve a intentar.
     *
     * @return true si queda otro intento, y entonces el trabajo vuelve a PENDIENTE
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fallar(Long trabajoIaId, int maxIntentos, String motivo) {
        TrabajoIa trabajo = trabajos.findById(trabajoIaId).orElse(null);
        if (trabajo == null) {
            return false;
        }
        int intentos = trabajo.getIntentos() == null ? 1 : trabajo.getIntentos();
        boolean reintentar = intentos < maxIntentos;
        trabajo.setEstado(reintentar ? "PENDIENTE" : "FALLIDO");
        trabajo.setTomadoEn(null);
        if (!reintentar) {
            trabajo.setTerminadoEn(Instant.now());
            // Nadie se entera solo de que una postulación se quedó sin calificar: por eso
            // este mensaje es de error y nombra la postulación (Regla 3 del doc 03).
            log.error("El trabajo {} ({}) de la postulación {} se agotó en {} intentos. La "
                            + "postulación se queda en PERFIL_CALIFICANDO y NO se le inventa una "
                            + "nota. Último motivo: {}",
                    trabajoIaId, trabajo.getAgenteCodigo(), trabajo.getPostulacionId(),
                    intentos, motivo);
        } else {
            log.warn("El trabajo {} ({}) falló en el intento {}/{}, se reintenta. Motivo: {}",
                    trabajoIaId, trabajo.getAgenteCodigo(), intentos, maxIntentos, motivo);
        }
        trabajos.save(trabajo);
        return reintentar;
    }

    /** Devuelve un EN_CURSO colgado a PENDIENTE para que alguien lo vuelva a tomar. */
    @Transactional
    public void devolverAPendiente(Long trabajoIaId) {
        trabajos.findById(trabajoIaId).ifPresent(trabajo -> {
            if (!"EN_CURSO".equals(trabajo.getEstado())) {
                return;
            }
            trabajo.setEstado("PENDIENTE");
            trabajo.setTomadoEn(null);
            trabajos.save(trabajo);
        });
    }

    /**
     * Congela un trabajo recién creado porque su organización agotó el tope del mes
     * (pieza E). Solo se congela un PENDIENTE que nadie tomó: si un consumidor llegó
     * antes, ese trabajo ya se está pagando y frenarlo a medias costaría lo mismo sin
     * dar nada. EN_ESPERA no se publica a la cola y {@code tomarSiEstaPendiente} no lo
     * toma: está fuera del circuito hasta que alguien lo despierte.
     *
     * @return true si quedó EN_ESPERA
     */
    @Transactional
    public boolean dejarEnEspera(Long trabajoIaId) {
        TrabajoIa trabajo = trabajos.findById(trabajoIaId).orElse(null);
        if (trabajo == null || !"PENDIENTE".equals(trabajo.getEstado())) {
            return false;
        }
        trabajo.setEstado("EN_ESPERA");
        trabajos.save(trabajo);
        return true;
    }

    /**
     * Devuelve un EN_ESPERA a PENDIENTE cuando su organización recuperó cupo —tope
     * subido, o mes nuevo—. Quien lo llama publica después el aviso a la cola; el
     * candidato nunca se enteró de la espera: para él siempre estuvo «en curso».
     *
     * @return true si despertó; false si ya no estaba esperando
     */
    @Transactional
    public boolean despertar(Long trabajoIaId) {
        TrabajoIa trabajo = trabajos.findById(trabajoIaId).orElse(null);
        if (trabajo == null || !"EN_ESPERA".equals(trabajo.getEstado())) {
            return false;
        }
        trabajo.setEstado("PENDIENTE");
        // El reloj del sondeo arranca de cero: si el aviso a la cola se pierde, el
        // barrido de pendientes viejos lo vuelve a empujar como a cualquier otro.
        trabajo.setCreadoEn(Instant.now());
        trabajos.save(trabajo);
        return true;
    }
}
