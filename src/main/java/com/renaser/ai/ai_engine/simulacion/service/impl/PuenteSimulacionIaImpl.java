package com.renaser.ai.ai_engine.simulacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.entity.Alerta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.repository.AfirmacionCvRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.AlertaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.HallazgoPerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.Contradiccion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.DijoEnElCurriculum;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.InsumoConversacion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.LoQueEscribio;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.LoQueSeVio;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.MomentoDeLaSesion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.NotaDeLaSimulacion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.PreguntaIa;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.ResultadoConversacion;
import com.renaser.ai.ai_engine.simulacion.entity.InscripcionSesion;
import com.renaser.ai.ai_engine.simulacion.entity.MarcaTiempoSimulacion;
import com.renaser.ai.ai_engine.simulacion.entity.PreguntaGenerada;
import com.renaser.ai.ai_engine.simulacion.entity.SesionSimulacion;
import com.renaser.ai.ai_engine.simulacion.repository.InscripcionSesionRepository;
import com.renaser.ai.ai_engine.simulacion.repository.MarcaTiempoSimulacionRepository;
import com.renaser.ai.ai_engine.simulacion.repository.PreguntaGeneradaRepository;
import com.renaser.ai.ai_engine.simulacion.repository.SesionSimulacionRepository;
import com.renaser.ai.ai_engine.simulacion.service.PuenteSimulacionIa;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.service.ContextoDeLaVacante;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ver {@link PuenteSimulacionIa}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuenteSimulacionIaImpl implements PuenteSimulacionIa {

    private static final String ETAPA = "SIMULACION";

    /**
     * Cuántas preguntas caben en la conversación.
     *
     * <p>Quince minutos, tres o cuatro preguntas con su repregunta. Más no es más
     * información: es la misma conversación corriendo, en la que ninguna respuesta se llega
     * a rascar. La instrucción ya pide entre tres y cinco; esto es lo que pasa si el modelo
     * se emociona igual.
     */
    private static final int TOPE = 5;

    private static final DateTimeFormatter RELOJ = DateTimeFormatter.ofPattern("HH:mm");

    private final PostulacionRepository postulaciones;
    private final PerfilTalentoRepository perfiles;
    private final HallazgoPerfilRepository hallazgos;
    private final AlertaRepository alertas;
    private final CvRepository cvs;
    private final AfirmacionCvRepository afirmaciones;
    private final NotaCriterioRepository notasCriterio;
    private final CalificacionPorCriterio calificacion;
    private final InscripcionSesionRepository inscripciones;
    private final SesionSimulacionRepository sesiones;
    private final MarcaTiempoSimulacionRepository marcas;
    private final IntentoPruebaRepository intentos;
    private final RespuestaPruebaRepository respuestasPrueba;
    private final PreguntaPruebaRepository preguntasPrueba;
    private final PreguntaGeneradaRepository preguntas;
    private final ContextoDeLaVacante contexto;

    // ==================== Lo que se le manda al agente ====================

    @Override
    @Transactional(readOnly = true)
    public InsumoConversacion insumoConversacion(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        Puesto puesto = contexto.puestoDe(postulacion);

        PerfilTalento perfil = perfiles.findByPostulacionId(postulacionId).orElse(null);
        List<LoQueSeVio> loVisto = hallazgosDe(perfil);
        List<Contradiccion> contradicciones = alertasDe(postulacionId);
        List<NotaDeLaSimulacion> notas = notasDeLaSimulacion(postulacionId);
        List<MomentoDeLaSesion> lineaDeTiempo = lineaDeTiempo(postulacionId);

        // Sin nada de esto el modelo solo podría escribir preguntas de manual —«cuéntame de
        // una vez que fallaste»—, que no aportan nada que no estuviera ya en el currículum.
        // Vale más decir que falta información que pagar una llamada para producirlas.
        if (loVisto.isEmpty() && contradicciones.isEmpty() && notas.isEmpty()
                && lineaDeTiempo.isEmpty()) {
            throw new IllegalStateException(
                    "La postulación " + postulacionId + " no tiene todavía nada de lo que "
                            + "preguntar: ni Perfil de Talento, ni notas de la simulación, ni "
                            + "eventos marcados durante la sesión");
        }

        return new InsumoConversacion(
                puesto.getNombre(),
                puesto.getNivelPuestoCodigo(),
                contexto.queBuscaLaVacanteDe(postulacion),
                perfil == null ? null : perfil.getResumen(),
                loVisto,
                contradicciones,
                afirmacionesDelCurriculum(postulacionId),
                notas,
                lineaDeTiempo,
                loQueEscribioEnLaPrueba(postulacionId));
    }

    private List<LoQueSeVio> hallazgosDe(PerfilTalento perfil) {
        if (perfil == null) {
            return List.of();
        }
        return hallazgos.findByPerfilTalentoId(perfil.getId()).stream()
                .map(h -> new LoQueSeVio(h.getTipo(), h.getDescripcion(), h.getEvidencia()))
                .toList();
    }

    private List<Contradiccion> alertasDe(Long postulacionId) {
        return alertas.findByPostulacionId(postulacionId).stream()
                .map(a -> new Contradiccion(a.getId(), a.getTipo(), a.getDescripcion()))
                .toList();
    }

    /**
     * Lo que dijo en el currículum, ya clasificado.
     *
     * <p>Solo lo que no está demostrado. Una afirmación demostrada no da una buena pregunta
     * —ya se comprobó, preguntarla otra vez es hacerle repetir lo que ya enseñó—; las
     * declaradas, contradichas y las que faltan por comprobar son justamente el hueco que la
     * conversación existe para cerrar.
     */
    private List<DijoEnElCurriculum> afirmacionesDelCurriculum(Long postulacionId) {
        Cv cv = cvs.findByPostulacionId(postulacionId).orElse(null);
        if (cv == null) {
            return List.of();
        }
        return afirmaciones.findByCvId(cv.getId()).stream()
                .filter(a -> !"DEMOSTRADA".equals(a.getClasificacion()))
                .map(a -> new DijoEnElCurriculum(a.getTexto(), a.getClasificacion()))
                .toList();
    }

    /** Las notas de los diez criterios de la simulación, con lo que escribió el facilitador. */
    private List<NotaDeLaSimulacion> notasDeLaSimulacion(Long postulacionId) {
        List<Criterio> rubrica = calificacion.rubricaGlobalDe(ETAPA);
        if (rubrica.isEmpty()) {
            return List.of();
        }
        Map<Long, NotaCriterio> puestas = notasCriterio.findByPostulacionId(postulacionId).stream()
                .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity(),
                        (a, b) -> a));
        Map<Long, BigDecimal> maximos = calificacion.maximosDe(postulacionId, rubrica);

        List<NotaDeLaSimulacion> salida = new ArrayList<>();
        for (Criterio criterio : rubrica) {
            NotaCriterio nota = puestas.get(criterio.getId());
            if (nota == null || nota.getPuntaje() == null) {
                continue;
            }
            salida.add(new NotaDeLaSimulacion(criterio.getNombre(), nota.getPuntaje(),
                    maximos.get(criterio.getId()), nota.getExplicacion()));
        }
        return salida;
    }

    /**
     * Las horas de la sesión, tal como se marcaron.
     *
     * <p>Van con dos formas del mismo dato y no es redundancia. La <b>hora de reloj</b> es la
     * que el candidato reconoce —él estaba ahí a las 10:41— y es la que hace que la pregunta
     * suene a hecho y no a acusación. El <b>minuto de la sesión</b> es el que se puede
     * comparar entre dos candidatos distintos, porque no depende de a qué hora empezó su
     * grupo.
     */
    private List<MomentoDeLaSesion> lineaDeTiempo(Long postulacionId) {
        InscripcionSesion inscripcion = inscripciones
                .findByPostulacionIdAndEsVigenteTrue(postulacionId).orElse(null);
        if (inscripcion == null) {
            return List.of();
        }
        Instant empezo = sesiones.findById(inscripcion.getSesionSimulacionId())
                .map(SesionSimulacion::getFechaHora)
                .orElse(null);

        return marcas.findByInscripcionSesionIdOrderByOcurridaEn(inscripcion.getId()).stream()
                .map(m -> new MomentoDeLaSesion(m.getEvento(), reloj(m.getOcurridaEn()),
                        minutoDe(empezo, m)))
                .toList();
    }

    private String reloj(Instant cuando) {
        return cuando == null ? null : RELOJ.format(cuando.atZone(ZoneId.systemDefault()));
    }

    private Integer minutoDe(Instant empezo, MarcaTiempoSimulacion marca) {
        if (empezo == null || marca.getOcurridaEn() == null) {
            return null;
        }
        long minutos = Duration.between(empezo, marca.getOcurridaEn()).toMinutes();
        return minutos < 0 ? null : (int) minutos;
    }

    /**
     * Lo que escribió en la prueba del puesto.
     *
     * <p>Es de donde salen las contradicciones más útiles: alguien que escribió «lo primero
     * que hago es preguntar el objetivo» y luego, en la sesión, arrancó a producir en el
     * minuto tres sin preguntar nada.
     */
    private List<LoQueEscribio> loQueEscribioEnLaPrueba(Long postulacionId) {
        IntentoPrueba intento = intentos.findByPostulacionId(postulacionId).orElse(null);
        if (intento == null) {
            return List.of();
        }
        List<RespuestaPrueba> suyas = respuestasPrueba.findByIntentoPruebaId(intento.getId())
                .stream()
                .filter(r -> !esVacio(r.getTexto()))
                .toList();
        if (suyas.isEmpty()) {
            return List.of();
        }
        Map<Long, PreguntaPrueba> porId = preguntasPrueba
                .findByIdIn(suyas.stream().map(RespuestaPrueba::getPreguntaPruebaId).toList())
                .stream()
                .collect(Collectors.toMap(PreguntaPrueba::getId, Function.identity()));

        return suyas.stream()
                .filter(r -> porId.containsKey(r.getPreguntaPruebaId()))
                .map(r -> new LoQueEscribio(porId.get(r.getPreguntaPruebaId()).getEnunciado(),
                        r.getTexto()))
                .toList();
    }

    // ==================== Lo que se guarda de lo que contesta ====================

    /**
     * Guarda las preguntas nuevas sin tocar las que ya se hicieron.
     *
     * <p><b>Lo que se dijo en la sala no se borra.</b> Una pregunta ya respondida es un
     * hecho ocurrido: alguien se la leyó al candidato y él contestó. Volver a pedir las
     * preguntas —porque se ajustó una nota, o porque la primera vez salieron flojas— no
     * puede hacer desaparecer eso. Las que siguen sin respuesta sí se rehacen enteras: nadie
     * las usó todavía, y dejarlas mezcladas con las nuevas daría una lista de diez preguntas
     * para una conversación de quince minutos.
     */
    @Override
    @Transactional
    public void guardarPreguntas(Long postulacionId, Long ejecucionIaId,
                                 ResultadoConversacion resultado) {
        if (resultado == null) {
            throw new IllegalStateException(
                    "El agente SIMULACION no devolvió nada: no se guarda una lista vacía");
        }
        postulacion(postulacionId);

        List<PreguntaGenerada> yaEstan = preguntas.findByPostulacionIdOrderByOrden(postulacionId);
        List<PreguntaGenerada> respondidas = yaEstan.stream()
                .filter(p -> !esVacio(p.getRespuesta()))
                .toList();
        yaEstan.stream()
                .filter(p -> esVacio(p.getRespuesta()))
                .forEach(preguntas::delete);

        Set<Long> alertasSuyas = alertas.findByPostulacionId(postulacionId).stream()
                .map(Alerta::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> repetidas = respondidas.stream()
                .map(PreguntaGenerada::getTexto)
                .collect(Collectors.toCollection(HashSet::new));

        int orden = respondidas.size();
        int guardadas = 0;
        for (PreguntaIa pregunta : lista(resultado.preguntas())) {
            if (guardadas >= TOPE - respondidas.size()) {
                break;
            }
            if (esVacio(pregunta.texto()) || !repetidas.add(pregunta.texto().trim())) {
                continue;
            }
            // Un alertaId inventado dejaría la pregunta apuntando a una contradicción de
            // otro candidato, o a ninguna. Vale más sin alerta que con una equivocada.
            Long alertaId = alertasSuyas.contains(pregunta.alertaId()) ? pregunta.alertaId() : null;
            if (pregunta.alertaId() != null && alertaId == null) {
                log.warn("SIMULACION devolvió el alertaId {}, que no es de la postulación {}",
                        pregunta.alertaId(), postulacionId);
            }

            preguntas.save(PreguntaGenerada.builder()
                    .postulacionId(postulacionId)
                    .texto(pregunta.texto().trim())
                    .motivo(pregunta.motivo())
                    .alertaId(alertaId)
                    .ejecucionIaId(ejecucionIaId)
                    .orden(++orden)
                    .creadoEn(Instant.now())
                    .build());
            guardadas++;
        }

        log.info("SIMULACION: {} preguntas nuevas para la postulación {}; {} ya respondidas se "
                + "quedaron como estaban", guardadas, postulacionId, respondidas.size());
    }

    // ==================== Apoyo ====================

    private Postulacion postulacion(Long postulacionId) {
        return postulaciones.findById(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static <T> List<T> lista(List<T> valor) {
        return valor == null ? List.of() : valor;
    }
}
