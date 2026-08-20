package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import tools.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.EntregaResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.EvaluacionCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.OpcionCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.PreguntaCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.Responder;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.OrdenPregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OrdenPreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.service.ValidadorDetalleV3;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La evaluación del candidato.
 *
 * <p>Dos decisiones que atraviesan toda la clase:
 *
 * <p><b>El banco no es el examen.</b> El candidato no responde las 90 preguntas del banco: la
 * plantilla dice cuántas de cada tipo le tocan, y de ahí se eligen al azar. Esa selección
 * concreta se congela en {@code orden_pregunta}, junto con el barajado de las opciones. Sin esa
 * tabla no se podría reproducir el examen tal como lo vio, que es un requisito de auditoría.
 *
 * <p><b>La clave nunca sale de aquí.</b> Los DTO de {@code DtosEvaluacion} no tienen campo para
 * el puntaje ni para la lógica interna, así que no hay forma de que viajen al portal por
 * descuido. Si viajan, el banco entero queda inutilizado.
 */
@Service
@RequiredArgsConstructor
public class ServicioEvaluacionImpl implements ServicioEvaluacion {

    /**
     * Cuántos días tiene el candidato para responder, y de dónde sale ese número.
     *
     * <p>Estuvo escrito aquí como constante hasta la V22. Cambiarlo obligaba a recompilar y
     * desplegar, y es justo la clase de número que se mueve: entre que se reabre una tanda y
     * que sale la invitación pasan días, y cada uno de esos días se le come al candidato.
     *
     * <p>El valor de verdad vive en el parámetro {@code dias_plazo_evaluacion} y Renaser lo
     * cambia desde el panel. Este es solo el respaldo para una base anterior a la V22, para
     * que ninguna evaluación nazca sin plazo.
     */
    public static final String PARAMETRO_PLAZO = "dias_plazo_evaluacion";
    static final int DIAS_DE_PLAZO_POR_DEFECTO = 14;

    private final EvaluacionRepository evaluaciones;
    private final PlantillaEvaluacionRepository plantillas;
    private final VersionBancoRepository versionesBanco;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final OrdenPreguntaRepository ordenes;
    private final RespuestaRepository respuestas;
    private final PostulacionRepository postulaciones;
    private final MaquinaEstados maquina;
    private final ServicioCalificacion calificacion;
    private final ColaCalificacionIa colaIa;
    private final ServicioParametros parametros;

    private final SecureRandom azar = new SecureRandom();

    // Jackson 3 (tools.jackson), que es el que trae Spring Boot 4 — no el com.fasterxml de
    // siempre. Propio y no inyectado: serializar un mapa no necesita la configuración de nadie.
    private static final ObjectMapper JSON = new ObjectMapper();

    // ============ Crear ============

    /**
     * Los días de plazo que rigen ahora mismo para esta organización.
     *
     * <p>Se lee en cada llamada y no se guarda: si Renaser lo cambia en el panel, la siguiente
     * evaluación ya nace con el plazo nuevo, sin reiniciar nada.
     */
    int diasDePlazo(Long organizacionId) {
        return parametros.entero(organizacionId, PARAMETRO_PLAZO, DIAS_DE_PLAZO_POR_DEFECTO);
    }

    @Override
    @Transactional
    public Long crearAlPostular(Long organizacionId, Long usuarioId, Long plantillaEvaluacionId,
                                String nivelPuestoCodigo) {
        PlantillaEvaluacion plantilla = plantillas.findById(plantillaEvaluacionId)
                .orElseThrow(() -> new IllegalStateException(
                        "La vacante apunta a una plantilla de evaluación que no existe"));

        // La versión del banco se fija AHORA. Si mañana se publica otra, este candidato sigue
        // atado a la suya: su nota nunca cambia sola después (RF-138).
        VersionBanco banco = versionesBanco
                .findFirstByTipoBancoAndNivelPuestoCodigoAndEstadoOrderByPublicadaEnDesc(
                        "NIVEL", nivelPuestoCodigo, "PUBLICADA")
                .orElseThrow(() -> new IllegalStateException(
                        "No hay un banco de preguntas publicado para el nivel " + nivelPuestoCodigo));

        Evaluacion evaluacion = evaluaciones.save(Evaluacion.builder()
                .organizacionId(organizacionId)
                .usuarioId(usuarioId)
                .plantillaEvaluacionId(plantilla.getId())
                .versionBancoNivelId(banco.getId())
                .estado("PENDIENTE")
                .venceEn(Instant.now().plus(diasDePlazo(organizacionId), ChronoUnit.DAYS))
                .vigenteHasta(Instant.now().plus(
                        plantilla.getVigenciaMeses() * 30L, ChronoUnit.DAYS))
                .creadoEn(Instant.now())
                .build());
        return evaluacion.getId();
    }

    // ============ Leer y responder ============

    @Override
    public EvaluacionCandidato ver(ContextoUsuario quien, UUID uuidPostulacion) {
        return pintar(laMia(quien, uuidPostulacion).getRight());
    }

    @Override
    @Transactional
    public EvaluacionCandidato iniciar(ContextoUsuario quien, UUID uuidPostulacion) {
        Evaluacion evaluacion = laMia(quien, uuidPostulacion).getRight();
        exigirAbierta(evaluacion);

        if (evaluacion.getIniciadaEn() == null) {
            armarOrden(evaluacion);
            evaluacion.setIniciadaEn(Instant.now());
            evaluacion.setEstado("EN_CURSO");
            evaluaciones.save(evaluacion);
        }
        return pintar(evaluacion);
    }

    @Override
    @Transactional
    public void responder(ContextoUsuario quien, UUID uuidPostulacion, Long preguntaId,
                          Responder datos) {
        Evaluacion evaluacion = laMia(quien, uuidPostulacion).getRight();
        exigirAbierta(evaluacion);
        if (evaluacion.getIniciadaEn() == null) {
            throw new IllegalStateException("Hay que empezar la evaluación antes de responder");
        }

        // Solo se puede responder una pregunta que de verdad le tocó: la base también lo
        // impide con una clave foránea, pero el error debe ser legible.
        boolean leToca = ordenes.findByEvaluacionIdOrderByPosicion(evaluacion.getId()).stream()
                .anyMatch(o -> o.getPreguntaId().equals(preguntaId));
        if (!leToca) {
            throw new ResourceNotFoundException("Pregunta", "id", preguntaId);
        }
        Pregunta pregunta = preguntas.findById(preguntaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta", "id", preguntaId));

        // Los formatos del banco v3 no se responden con una sola opción, así que traen su
        // detalle. Y como ese detalle se guarda en jsonb, la base no comprueba nada de él:
        // esta llamada es lo único que impide que una respuesta con mala forma acabe
        // convertida en una nota. Ver ValidadorDetalleV3 y la migración V21.
        boolean conDetalle = ValidadorDetalleV3.necesitaDetalle(pregunta.getTipo());
        if (conDetalle) {
            Set<Long> suyas = opciones.findByPreguntaIdOrderByLetra(preguntaId).stream()
                    .map(Opcion::getId).collect(Collectors.toSet());
            ValidadorDetalleV3.validar(pregunta.getTipo(), suyas, datos.detalle());
        } else if (datos.opcionId() == null && (datos.texto() == null || datos.texto().isBlank())) {
            throw new IllegalArgumentException("Hay que elegir una opción o escribir una respuesta");
        }

        Respuesta respuesta = respuestas
                .findByEvaluacionIdAndPreguntaId(evaluacion.getId(), preguntaId)
                .orElseGet(() -> Respuesta.builder()
                        .evaluacionId(evaluacion.getId())
                        .preguntaId(preguntaId)
                        .creadoEn(Instant.now())
                        .build());
        respuesta.setOpcionId(datos.opcionId());
        respuesta.setTexto(datos.texto());
        respuesta.setDetalle(comoJson(datos.detalle()));
        respuesta.setSegundos(datos.segundos());
        respuesta.setRespondidaEn(Instant.now());
        respuestas.save(respuesta);
    }

    @Override
    @Transactional
    public EntregaResponse entregar(ContextoUsuario quien, UUID uuidPostulacion) {
        var par = laMia(quien, uuidPostulacion);
        Postulacion postulacion = par.getLeft();
        Evaluacion evaluacion = par.getRight();
        exigirAbierta(evaluacion);

        int total = ordenes.findByEvaluacionIdOrderByPosicion(evaluacion.getId()).size();
        int respondidas = respuestas.findByEvaluacionId(evaluacion.getId()).size();
        if (total == 0) {
            throw new IllegalStateException("Esta evaluación todavía no se ha empezado");
        }
        if (respondidas < total) {
            throw new IllegalArgumentException(
                    "Faltan " + (total - respondidas) + " preguntas por responder");
        }

        evaluacion.setEstado("TERMINADA");
        evaluacion.setTerminadaEn(Instant.now());
        evaluaciones.save(evaluacion);

        // Ahora le toca a la máquina. Es transición del sistema: no hay persona detrás y por
        // eso no lleva motivo escrito.
        maquina.transicionar(postulacion, "PERFIL_CALIFICANDO", null, null, true, false, null);

        // Lo cerrado se puntúa aquí mismo: es aritmética contra una clave, tarda milisegundos
        // y no depende de que ningún modelo responda.
        calificacion.calificarLoCerrado(postulacion.getId());

        // Lo que sí necesita IA —el currículum, las respuestas abiertas y el Perfil de
        // Talento— se hace en segundo plano: tarda decenas de segundos y depende de un
        // servicio externo, así que no puede colgarse de la petición del candidato. La
        // postulación se queda en PERFIL_CALIFICANDO hasta que haya resultado, y si la IA
        // falla se reintenta sola: nunca se inventa una nota (Regla 3 del doc 03).
        colaIa.encolarPerfilIntegral(postulacion.getId());

        return new EntregaResponse("TERMINADA", respondidas, total);
    }

    @Override
    @Transactional
    public void cerrarVencidas() {
        for (Evaluacion evaluacion : evaluaciones.findByEstadoInAndVenceEnBefore(
                List.of("PENDIENTE", "EN_CURSO"), Instant.now())) {
            evaluacion.setEstado("VENCIDA");
            evaluaciones.save(evaluacion);

            // Solo se cierra lo que sigue vivo. Un candidato que se retiró deja su
            // evaluación sin responder, y esa evaluación vence igual: sin esta comprobación
            // se intentaría cerrar una postulación ya cerrada y se le mandaría un segundo
            // correo diciéndole algo que ya sabe.
            postulaciones.findByEvaluacionId(evaluacion.getId())
                    .filter(postulacion -> !maquina.yaTermino(postulacion))
                    .ifPresent(postulacion ->
                            maquina.transicionar(postulacion, "CERRADA", null, null,
                                    true, false, "PLAZO_VENCIDO"));
        }
    }

    // ============ Armar el examen ============

    /**
     * Arma el examen del candidato: el banco entero de su nivel, en el orden del documento.
     *
     * <p><b>El banco v3 no se muestrea, se aplica completo.</b> Sus 85, 55 o 50 ítems son el
     * examen, y de ahí salen los máximos que el cliente declara —288, 186 y 168—: si se
     * aplicara solo una parte, ese máximo no sería alcanzable y los umbrales que deciden el
     * nivel (I, II o III) dejarían de significar nada.
     *
     * <p>Por eso ya no se usan las cuotas de la plantilla, que servían para el banco v0.1:
     * aquel era un repositorio del que cada vacante elegía una muestra. La plantilla sigue
     * mandando en lo suyo —el tiempo objetivo y la vigencia—, pero no en qué preguntas caen.
     *
     * <p><b>Y el orden no se baraja.</b> El del documento no es arbitrario: agrupa los ítems
     * por bloque y coloca cada par de consistencia bien lejos de su pareja —al menos quince
     * ítems— para que el candidato no las vea juntas y las cuadre. Barajar rompería eso.
     *
     * <p>Se hace una sola vez, la primera vez que entra. A partir de ahí el examen es el suyo
     * y no cambia, aunque vuelva a entrar tres días después.
     */
    private void armarOrden(Evaluacion evaluacion) {
        List<Pregunta> finales = preguntas.findByVersionBancoIdOrderByOrden(
                evaluacion.getVersionBancoNivelId());
        if (finales.isEmpty()) {
            throw new IllegalStateException(
                    "El banco de preguntas de este nivel no tiene ninguna pregunta: "
                            + "no hay con qué armar el examen");
        }

        Map<Long, List<Opcion>> porPregunta = opciones
                .findByPreguntaIdIn(finales.stream().map(Pregunta::getId).toList()).stream()
                .collect(Collectors.groupingBy(Opcion::getPreguntaId));

        List<OrdenPregunta> filas = new ArrayList<>();
        for (int i = 0; i < finales.size(); i++) {
            Pregunta p = finales.get(i);
            List<Opcion> suyas = new ArrayList<>(porPregunta.getOrDefault(p.getId(), List.of()));
            barajar(suyas);
            filas.add(OrdenPregunta.builder()
                    .evaluacionId(evaluacion.getId())
                    .preguntaId(p.getId())
                    .posicion(i + 1)
                    .ordenOpciones(suyas.stream().map(Opcion::getLetra)
                            .collect(Collectors.joining(",")))
                    .creadoEn(Instant.now())
                    .build());
        }
        ordenes.saveAll(filas);
    }

    // ============ Pintar ============

    private EvaluacionCandidato pintar(Evaluacion evaluacion) {
        List<OrdenPregunta> orden = ordenes.findByEvaluacionIdOrderByPosicion(evaluacion.getId());
        Integer minutos = plantillas.findById(evaluacion.getPlantillaEvaluacionId())
                .map(PlantillaEvaluacion::getMinutosObjetivo).orElse(null);

        if (orden.isEmpty()) {
            // Todavía no ha empezado: no hay preguntas que enseñar
            return new EvaluacionCandidato(evaluacion.getId(), evaluacion.getEstado(),
                    evaluacion.getVenceEn(), evaluacion.getIniciadaEn(), evaluacion.getTerminadaEn(),
                    minutos, 0, 0, List.of());
        }

        List<Long> ids = orden.stream().map(OrdenPregunta::getPreguntaId).toList();
        Map<Long, Pregunta> porId = preguntas.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        Map<Long, List<Opcion>> opcionesPorPregunta = opciones.findByPreguntaIdIn(ids).stream()
                .collect(Collectors.groupingBy(Opcion::getPreguntaId));
        Map<Long, Respuesta> respuestaPorPregunta = respuestas
                .findByEvaluacionId(evaluacion.getId()).stream()
                .collect(Collectors.toMap(Respuesta::getPreguntaId, Function.identity()));

        List<PreguntaCandidato> lista = new ArrayList<>();
        for (OrdenPregunta o : orden) {
            Pregunta p = porId.get(o.getPreguntaId());
            if (p == null) continue;
            Respuesta r = respuestaPorPregunta.get(o.getPreguntaId());
            lista.add(new PreguntaCandidato(
                    p.getId(), o.getPosicion(), p.getTipo(), p.getEnunciado(), p.getSituacion(),
                    enSuOrden(opcionesPorPregunta.getOrDefault(p.getId(), List.of()),
                            o.getOrdenOpciones()),
                    r == null ? null : r.getTexto(),
                    r == null ? null : r.getOpcionId()));
        }

        return new EvaluacionCandidato(evaluacion.getId(), evaluacion.getEstado(),
                evaluacion.getVenceEn(), evaluacion.getIniciadaEn(), evaluacion.getTerminadaEn(),
                minutos, orden.size(), respuestaPorPregunta.size(), lista);
    }

    /** Las opciones en el orden barajado que se le guardó, no en el del banco. */
    private List<OpcionCandidato> enSuOrden(List<Opcion> opcionesDeLaPregunta, String ordenGuardado) {
        if (ordenGuardado == null || ordenGuardado.isBlank()) {
            return opcionesDeLaPregunta.stream()
                    .map(o -> new OpcionCandidato(o.getId(), o.getLetra(), o.getTexto())).toList();
        }
        List<String> letras = List.of(ordenGuardado.split(","));
        return opcionesDeLaPregunta.stream()
                .sorted(Comparator.comparingInt(o -> {
                    int i = letras.indexOf(o.getLetra());
                    return i < 0 ? Integer.MAX_VALUE : i;
                }))
                .map(o -> new OpcionCandidato(o.getId(), o.getLetra(), o.getTexto()))
                .toList();
    }

    // ============ Apoyo ============

    /** La postulación y su evaluación, comprobando que ambas son de quien pregunta. */
    private Par laMia(ContextoUsuario quien, UUID uuid) {
        Postulacion postulacion = postulaciones.findByUuid(uuid)
                .filter(p -> p.getUsuarioId().equals(quien.usuarioId()))
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "código", uuid));
        if (postulacion.getEvaluacionId() == null) {
            throw new ResourceNotFoundException("Evaluación", "postulación", uuid);
        }
        Evaluacion evaluacion = evaluaciones.findById(postulacion.getEvaluacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Evaluación", "postulación", uuid));
        return new Par(postulacion, evaluacion);
    }

    private void exigirAbierta(Evaluacion evaluacion) {
        if ("TERMINADA".equals(evaluacion.getEstado())) {
            throw new IllegalStateException("Esta evaluación ya fue entregada");
        }
        if ("VENCIDA".equals(evaluacion.getEstado())) {
            throw new IllegalStateException("El plazo para responder esta evaluación ya pasó");
        }
        if (evaluacion.getVenceEn() != null && Instant.now().isAfter(evaluacion.getVenceEn())) {
            throw new IllegalStateException("El plazo para responder esta evaluación ya pasó");
        }
    }

    /** El detalle viaja como objeto y se guarda como jsonb; null si la pregunta no lo usa. */
    private String comoJson(Map<String, Object> detalle) {
        if (detalle == null || detalle.isEmpty()) {
            return null;
        }
        return JSON.writeValueAsString(detalle);
    }

    private void barajar(List<?> lista) {
        java.util.Collections.shuffle(lista, azar);
    }

    /** Par de postulación y evaluación. Existe para no devolver un array de Object. */
    private record Par(Postulacion left, Evaluacion right) {
        Postulacion getLeft() { return left; }
        Evaluacion getRight() { return right; }
    }
}
