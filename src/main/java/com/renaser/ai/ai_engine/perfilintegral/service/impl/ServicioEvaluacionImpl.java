package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.CampoCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.EntregaResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.EvaluacionCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.OpcionCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.PreguntaCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.Responder;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.CampoCaso;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.OrdenPregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CampoCasoRepository;
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
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
@Slf4j
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

    /** Los dos propósitos de un examen: el banco por nivel, o el cuestionario de una vacante (V44). */
    public static final String PERFIL_INTEGRAL = "PERFIL_INTEGRAL";
    public static final String CUESTIONARIO_TECNICO = "CUESTIONARIO_TECNICO";

    private final EvaluacionRepository evaluaciones;
    private final PlantillaEvaluacionRepository plantillas;
    private final VersionBancoRepository versionesBanco;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final OrdenPreguntaRepository ordenes;
    private final RespuestaRepository respuestas;
    private final CampoCasoRepository campos;
    private final PostulacionRepository postulaciones;
    private final MaquinaEstados maquina;
    private final ServicioCalificacion calificacion;
    private final ColaCalificacionIa colaIa;
    private final ServicioParametros parametros;
    private final DuenoDelInstrumento dueno;

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
        // atado a la suya: su nota nunca cambia sola después (RF-138). De quién es el banco
        // lo contesta el resolutor: sin él, una empresa evaluaría con el banco de cualquiera
        // — el selector viejo elegía la publicada más reciente SIN mirar la organización.
        VersionBanco banco = versionesBanco
                .laPublicadaDelNivel(dueno.duenoDe(organizacionId, Instrumento.BANCO),
                        "NIVEL", nivelPuestoCodigo)
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

    /**
     * El examen de la etapa técnica, cuando la vacante rinde el cuestionario CAZATALENTOS.
     *
     * <p>Hermano de {@link #crearAlPostular}, y las diferencias son las que separan a los dos
     * instrumentos:
     *
     * <ul>
     *   <li><b>Sin plantilla.</b> Un cuestionario de vacante no tiene ninguna: nace para esa
     *       vacante, dura lo que ella diga y no se reutiliza. La V44 lo permite y su CHECK
     *       sigue exigiéndosela al perfil integral.
     *   <li><b>El banco es el de la vacante</b>, no el publicado del nivel. Se fija ahora,
     *       igual que allí: si mañana el dueño regenera el cuestionario, este candidato sigue
     *       atado al suyo (RF-138).
     *   <li><b>Sin {@code vigenteHasta}.</b> La vigencia existe para reutilizar un examen del
     *       banco en otra postulación de la misma persona; este no se reutiliza jamás.
     *   <li><b>Los minutos se congelan aquí.</b> {@code Evaluacion} no sabe de qué vacante
     *       viene, y resolverlos al pintar movería el reloj de quien ya está respondiendo si
     *       alguien edita la vacante a mitad de tanda.
     * </ul>
     *
     * <p>Se crea al ENTRAR en la etapa y no al postular, como el intento de la prueba del
     * puesto: hasta que el equipo no lo avanza, no hay nada que rendir.
     */
    @Override
    @Transactional
    public Long crearTecnicaAlEntrar(Long organizacionId, Long usuarioId, Long vacanteId,
                                     Integer minutosDeLaVacante) {
        VersionBanco cuestionario = versionesBanco
                .findFirstByVacanteIdAndEstado(vacanteId, "PUBLICADA")
                .orElseThrow(() -> new IllegalStateException(
                        "Esta vacante rinde el cuestionario técnico y no tiene ninguno "
                                + "publicado: no hay con qué armar el examen"));

        Evaluacion evaluacion = evaluaciones.save(Evaluacion.builder()
                .organizacionId(organizacionId)
                .usuarioId(usuarioId)
                .versionBancoNivelId(cuestionario.getId())
                .proposito(CUESTIONARIO_TECNICO)
                // Lo que diga la vacante, o nada: sin cronómetro rige el plazo de días de
                // siempre. Cuando entre la rama que le pone minutos al banco (V43), aquí se
                // encadena su valor como respaldo y esta línea es el único sitio que cambia.
                .minutosObjetivo(minutosDeLaVacante)
                .estado("PENDIENTE")
                .venceEn(Instant.now().plus(diasDePlazo(organizacionId), ChronoUnit.DAYS))
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

    // ============ El cuestionario técnico de la vacante (etapa 2) ============
    //
    // Los cuatro verbos del portal, contra el otro examen de la misma postulación. Delegan
    // en el mismo cuerpo que la evaluación del banco: `pintar`, `armarOrden` —que ya filtra
    // la pregunta PRESENCIAL, y su comentario dice que el filtro se puso para esto—,
    // `guardarLaRespuesta` con su carrera resuelta y `exigirAbierta`. Lo único que cambia es
    // de qué columna de la postulación se saca el examen, y adónde va al entregarse.

    @Override
    public EvaluacionCandidato verTecnico(ContextoUsuario quien, UUID uuidPostulacion) {
        return pintar(laMia(quien, uuidPostulacion, CUESTIONARIO_TECNICO).getRight());
    }

    /**
     * Empezar el cuestionario técnico: aquí es donde arranca el reloj.
     *
     * <p>⚠️ <b>El cronómetro se congela al empezar, no al crear.</b> {@code venceEn} nació
     * con el plazo de días de siempre; si la vacante fijó minutos, desde este instante manda
     * el más cercano de los dos. Es el mismo gesto que {@code ServicioPruebaImpl.iniciar}, y
     * el motivo es el mismo: el tiempo cuenta desde que la persona abre el examen, no desde
     * que el equipo la avanzó.
     */
    @Override
    @Transactional
    public EvaluacionCandidato iniciarTecnico(ContextoUsuario quien, UUID uuidPostulacion) {
        Evaluacion evaluacion = laMia(quien, uuidPostulacion, CUESTIONARIO_TECNICO).getRight();
        exigirAbierta(evaluacion);

        if (evaluacion.getIniciadaEn() == null) {
            armarOrden(evaluacion);
            Instant ahora = Instant.now();
            evaluacion.setIniciadaEn(ahora);
            evaluacion.setEstado("EN_CURSO");
            if (evaluacion.getMinutosObjetivo() != null) {
                Instant porElReloj = ahora.plus(evaluacion.getMinutosObjetivo(), ChronoUnit.MINUTES);
                if (evaluacion.getVenceEn() == null || porElReloj.isBefore(evaluacion.getVenceEn())) {
                    evaluacion.setVenceEn(porElReloj);
                }
            }
            evaluaciones.save(evaluacion);
        }
        return pintar(evaluacion);
    }

    @Override
    @Transactional
    public void responderTecnico(ContextoUsuario quien, UUID uuidPostulacion, Long preguntaId,
                                 Responder datos) {
        responderEn(laMia(quien, uuidPostulacion, CUESTIONARIO_TECNICO).getRight(),
                preguntaId, datos);
    }

    @Override
    @Transactional
    public void responder(ContextoUsuario quien, UUID uuidPostulacion, Long preguntaId,
                          Responder datos) {
        responderEn(laMia(quien, uuidPostulacion).getRight(), preguntaId, datos);
    }

    /**
     * Guardar una respuesta en el examen que sea.
     *
     * <p>Lo comparten los dos instrumentos: las reglas de qué se puede responder y cómo son
     * del formato de la pregunta, no de para qué etapa es el examen.
     */
    private void responderEn(Evaluacion evaluacion, Long preguntaId, Responder datos) {
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
        } else if ("ABIERTA".equals(pregunta.getTipo())) {
            // Una ABIERTA se responde escribiendo, y solo escribiendo. Aceptar una opción
            // aquí contaría como respondida para entregar, pero el evaluador —que solo mira
            // texto— nunca la calificaría, y la postulación se quedaría sin nota de etapa.
            if (datos.opcionId() != null) {
                throw new IllegalArgumentException(
                        "Esta pregunta es de respuesta abierta: no lleva opciones");
            }
            if (datos.texto() == null || datos.texto().isBlank()) {
                throw new IllegalArgumentException("Hay que escribir una respuesta");
            }
        } else if (datos.opcionId() == null && (datos.texto() == null || datos.texto().isBlank())) {
            throw new IllegalArgumentException("Hay que elegir una opción o escribir una respuesta");
        }

        guardarLaRespuesta(evaluacion.getId(), preguntaId, datos);
    }

    /**
     * Guarda la respuesta, exista ya o no.
     *
     * <p>Se busca y, si no esta, se crea. Eso funciona hasta que llegan dos peticiones de la
     * misma pregunta a la vez: las dos miran, las dos no encuentran nada, las dos insertan, y
     * la segunda choca contra la clave unica de {@code (evaluacion_id, pregunta_id)}.
     *
     * <p><b>Y llegan a la vez.</b> El portal reintenta los guardados que no confirmo, y ademas
     * manda lo escrito al cambiar de pregunta sin esperar al temporizador. Le paso a una
     * candidata en la pregunta 49 de 50: su respuesta estaba guardada —por eso la clave estaba
     * repetida— pero ella veia un error, que en ese momento del examen invita a recargar o a
     * insistir en bucle.
     *
     * <p>Se resuelve dejando que la base decida quien gana y releyendo despues. Reintentar es
     * seguro porque el segundo guardado escribe exactamente lo mismo que el primero: es la
     * misma respuesta del mismo candidato a la misma pregunta.
     */
    private void guardarLaRespuesta(Long evaluacionId, Long preguntaId, Responder datos) {
        try {
            respuestas.saveAndFlush(conLoQueMando(
                    respuestas.findByEvaluacionIdAndPreguntaId(evaluacionId, preguntaId)
                            .orElseGet(() -> Respuesta.builder()
                                    .evaluacionId(evaluacionId)
                                    .preguntaId(preguntaId)
                                    .creadoEn(Instant.now())
                                    .build()),
                    datos));
        } catch (DataIntegrityViolationException carrera) {
            // Otra peticion la creo entre nuestro «no existe» y nuestro insert. La fila que
            // vale es la suya: se relee y se escribe encima.
            log.debug("Dos guardados a la vez de la pregunta {} en la evaluacion {}; se actualiza",
                    preguntaId, evaluacionId);
            Respuesta suya = respuestas
                    .findByEvaluacionIdAndPreguntaId(evaluacionId, preguntaId)
                    .orElseThrow(() -> carrera);
            respuestas.saveAndFlush(conLoQueMando(suya, datos));
        }
    }

    /** Lo que el candidato acaba de mandar, puesto sobre la fila que toque. */
    private Respuesta conLoQueMando(Respuesta respuesta, Responder datos) {
        respuesta.setOpcionId(datos.opcionId());
        respuesta.setTexto(datos.texto());
        respuesta.setDetalle(comoJson(datos.detalle()));
        respuesta.setSegundos(datos.segundos());
        respuesta.setRespondidaEn(Instant.now());
        return respuesta;
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

    /**
     * Entregar el cuestionario técnico.
     *
     * <p>Mismo gesto que la entrega del banco, con tres diferencias que son las que separan
     * a las dos etapas: va a {@code PRUEBA_CALIFICANDO} y no a {@code PERFIL_CALIFICANDO},
     * no hay nada cerrado que puntuar —todas sus preguntas son abiertas— y encola al
     * evaluador técnico, que corre por su propio carril.
     */
    @Override
    @Transactional
    public EntregaResponse entregarTecnico(ContextoUsuario quien, UUID uuidPostulacion) {
        var par = laMia(quien, uuidPostulacion, CUESTIONARIO_TECNICO);
        Postulacion postulacion = par.getLeft();
        Evaluacion evaluacion = par.getRight();
        exigirAbierta(evaluacion);

        int total = ordenes.findByEvaluacionIdOrderByPosicion(evaluacion.getId()).size();
        int respondidas = respuestas.findByEvaluacionId(evaluacion.getId()).size();
        if (total == 0) {
            throw new IllegalStateException("Este cuestionario todavía no se ha empezado");
        }
        if (respondidas < total) {
            throw new IllegalArgumentException(
                    "Faltan " + (total - respondidas) + " preguntas por responder");
        }
        cerrarYEncolarTecnico(postulacion, evaluacion);
        return new EntregaResponse("TERMINADA", respondidas, total);
    }

    /**
     * Cerrar el cuestionario técnico y mandarlo a calificar.
     *
     * <p>Lo comparten la entrega del candidato y el barrido del reloj. El barrido cierra lo
     * que haya: quien no contestó una pregunta se lleva un cero por ella, que es lo que dice
     * el método —no contestar es un cero, no media prueba.
     */
    private void cerrarYEncolarTecnico(Postulacion postulacion, Evaluacion evaluacion) {
        evaluacion.setEstado("TERMINADA");
        evaluacion.setTerminadaEn(Instant.now());
        evaluaciones.save(evaluacion);

        // Transición del sistema: no hay persona detrás y por eso no lleva motivo escrito.
        maquina.transicionar(postulacion, "PRUEBA_CALIFICANDO", null, null, true, false, null);

        // Nada que puntuar aquí: las doce preguntas son abiertas y las califica el modelo.
        colaIa.encolarCuestionarioTecnico(postulacion.getId());
    }

    /**
     * Los cuestionarios técnicos a los que se les acabó el tiempo se entregan solos.
     *
     * <p>⚠️ <b>Sin esto el cronómetro no existe.</b> La pantalla enseñaría el reloj en cero y
     * el examen seguiría abierto en el servidor para siempre; es la clase de fallo que no da
     * ninguna señal. Gemelo de {@code ServicioPrueba.entregarVencidos}, y como él se entrega
     * <b>incompleto</b>: lo que no contestó cuenta cero.
     */
    @Override
    @Transactional
    public void entregarTecnicasVencidas() {
        for (Evaluacion evaluacion : evaluaciones.findByPropositoAndEstadoInAndVenceEnBefore(
                CUESTIONARIO_TECNICO, List.of("EN_CURSO"), Instant.now())) {
            postulaciones.findByEvaluacionTecnicaId(evaluacion.getId()).ifPresent(postulacion -> {
                // Quien se retiró o quedó fuera deja su cuestionario sin entregar, y ese
                // cuestionario vence igual: sin esta comprobación se intentaría mover una
                // postulación ya cerrada.
                if (maquina.yaTermino(postulacion)) {
                    return;
                }
                log.info("Se acabó el tiempo del cuestionario técnico {}: se entrega como esté",
                        evaluacion.getId());
                cerrarYEncolarTecnico(postulacion, evaluacion);
            });
        }
    }

    @Override
    @Transactional
    public void cerrarVencidas() {
        // ⚠️ Solo las del perfil integral. Las técnicas tienen su propio barrido, que las
        // ENTREGA en vez de darlas por vencidas: si cayeran aquí, se marcarían VENCIDA, su
        // `findByEvaluacionId` no encontraría postulación —cuelgan de la otra columna— y el
        // candidato se quedaría sin nota y sin cierre, en silencio.
        for (Evaluacion evaluacion : evaluaciones.findByPropositoAndEstadoInAndVenceEnBefore(
                PERFIL_INTEGRAL, List.of("PENDIENTE", "EN_CURSO"), Instant.now())) {
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
        // La muestra de trabajo PRESENCIAL jamás viaja en un formulario: regala el
        // diagnóstico del negocio a todo el que postule. Hoy ningún examen apunta a un
        // banco de vacante, pero este es el único sitio que arma exámenes y el filtro
        // vive aquí para que reutilizar el portal (ciclo 2) no la sirva por accidente.
        List<Pregunta> finales = preguntas.findByVersionBancoIdOrderByOrden(
                        evaluacion.getVersionBancoNivelId()).stream()
                .filter(p -> !p.isPresencial())
                .toList();
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
        // Los campos de cada CD, agrupados conservando su orden. Solo orden y etiqueta
        // viajan: la regla de validación se queda dentro, como la lógica interna.
        Map<Long, List<CampoCandidato>> camposPorPregunta = campos
                .findByPreguntaIdInOrderByPreguntaIdAscOrdenAsc(ids).stream()
                .collect(Collectors.groupingBy(CampoCaso::getPreguntaId,
                        Collectors.mapping(c -> new CampoCandidato(c.getOrden(), c.getEtiqueta()),
                                Collectors.toList())));
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
                    // Solo los CD los traen; en el resto van en nulo o vacío y el portal los ignora.
                    p.getCasosPedidos(),
                    camposPorPregunta.getOrDefault(p.getId(), List.of()),
                    enSuOrden(opcionesPorPregunta.getOrDefault(p.getId(), List.of()),
                            o.getOrdenOpciones()),
                    r == null ? null : r.getTexto(),
                    r == null ? null : r.getOpcionId(),
                    r == null ? null : soloLoQueEscribio(p.getTipo(), r)));
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

    /** La postulación y su evaluación del perfil integral, si ambas son de quien pregunta. */
    private Par laMia(ContextoUsuario quien, UUID uuid) {
        return laMia(quien, uuid, PERFIL_INTEGRAL);
    }

    /**
     * La postulación y el examen que se le pide, comprobando que ambos son de quien pregunta.
     *
     * <p>Una postulación puede tener dos: el del banco por nivel (etapa 1) y el cuestionario
     * técnico de su vacante (etapa 2), en columnas distintas desde la V44. El propósito elige
     * de cuál se habla; lo demás —incluido el 404 de «no es tuya», que no distingue entre no
     * existir y ser de otro— es idéntico para los dos.
     */
    private Par laMia(ContextoUsuario quien, UUID uuid, String proposito) {
        Postulacion postulacion = postulaciones.findByUuid(uuid)
                .filter(p -> p.getUsuarioId().equals(quien.usuarioId()))
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "código", uuid));
        Long evaluacionId = CUESTIONARIO_TECNICO.equals(proposito)
                ? postulacion.getEvaluacionTecnicaId()
                : postulacion.getEvaluacionId();
        if (evaluacionId == null) {
            throw new ResourceNotFoundException("Evaluación", "postulación", uuid);
        }
        Evaluacion evaluacion = evaluaciones.findById(evaluacionId)
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

    /**
     * Le devuelve su propia respuesta al candidato, para que pueda retomar el examen.
     *
     * <p>Son 190 ítems: nadie los responde de una sentada. Si al volver al día siguiente los
     * viera en blanco, o rehace todo o entrega a medias, y las respuestas estaban guardadas
     * desde el principio.
     *
     * <p><b>Solo salen las claves de su formato</b>, las que llena él mismo al responder (ver
     * {@link ValidadorDetalleV3#clavesDe}). El campo es {@code jsonb} y admite cualquier cosa,
     * así que devolverlo entero sería confiar en que nadie guarde ahí nunca un puntaje. Un
     * filtro cuesta tres líneas; la clave del banco filtrada al navegador no tiene arreglo
     * (RF-53).
     *
     * <p>Un detalle ilegible no tumba la lectura: se registra y esa pregunta vuelve sin él,
     * como si no estuviera respondida. Es preferible a dejar al candidato sin ver su examen.
     *
     * @return lo que escribió, o {@code null} si no hay nada — nunca un mapa vacío, que en el
     *         portal se confundiría con «esta la respondí»
     */
    private Map<String, Object> soloLoQueEscribio(String tipo, Respuesta r) {
        if (r.getDetalle() == null || r.getDetalle().isBlank()) {
            return null;
        }
        Set<String> permitidas = ValidadorDetalleV3.clavesDe(tipo);
        if (permitidas.isEmpty()) {
            return null;
        }
        Map<String, Object> guardado;
        try {
            guardado = JSON.readValue(r.getDetalle(), new TypeReference<Map<String, Object>>() { });
        } catch (RuntimeException e) {
            log.warn("El detalle de la respuesta {} no se pudo leer: {}", r.getId(), e.getMessage());
            return null;
        }
        Map<String, Object> suyo = new LinkedHashMap<>();
        for (String clave : permitidas) {
            Object valor = guardado.get(clave);
            if (valor != null) {
                suyo.put(clave, valor);
            }
        }
        return suyo.isEmpty() ? null : suyo;
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
