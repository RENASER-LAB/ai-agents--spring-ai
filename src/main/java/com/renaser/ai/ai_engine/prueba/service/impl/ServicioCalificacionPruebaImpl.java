package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.CalificacionIaEncolada;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.DefinirPlazoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.NotaCriterioResponse;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.PlazoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.PonerNotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.RespuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPrueba;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import static com.renaser.ai.ai_engine.vacante.service.impl.ServicioVacantesPanelImpl.CUESTIONARIO_TECNICO;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioCalificacionPruebaImpl implements ServicioCalificacionPrueba {

    private static final String ETAPA = "PRUEBA_PUESTO";

    private final PostulacionRepository postulaciones;
    private final AlcanceSobreLaVacante alcance;
    private final IntentoPruebaRepository intentos;
    private final CriterioRepository criterios;
    private final PreguntaVersionPlantillaRepository preguntasElegidas;
    private final PreguntaPruebaRepository preguntasCatalogo;
    private final RespuestaPruebaRepository respuestas;
    private final NotaCriterioRepository notasCriterio;
    private final VersionPesosRepository versionesPesos;
    private final ColaCalificacionIa cola;
    private final ServicioAuditoria auditoria;
    private final com.renaser.ai.ai_engine.vacante.repository.VacanteRepository vacantes;
    private final com.renaser.ai.ai_engine.perfilintegral.service.impl
            .CalificacionCuestionarioTecnico cuestionarioTecnico;
    private final com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository notasEtapa;
    private final CalificacionPorCriterio calificacion;

    @Override
    public List<NotaCriterioResponse> verNotas(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ajustar_nota");
        List<Criterio> rubrica = laRubricaDe(postulacion);
        Map<Long, NotaCriterio> notasPorCriterio = notasCriterio.findByPostulacionId(postulacionId).stream()
                .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity()));

        return rubrica.stream().map(c -> {
            NotaCriterio n = notasPorCriterio.get(c.getId());
            return new NotaCriterioResponse(c.getId(), c.getNombre(),
                    c.getPuntos() == null ? null : c.getPuntos().doubleValue(),
                    n == null || n.getPuntaje() == null ? null : n.getPuntaje().doubleValue(),
                    n == null ? null : n.getExplicacion(),
                    n == null ? null : n.getOrigen());
        }).toList();
    }

    /**
     * Si esta postulación rinde el cuestionario técnico en vez de la prueba del puesto.
     *
     * <p>Lo dice su vacante (V43), y de ahí salen las cuatro bifurcaciones de este servicio:
     * sin ellas, las cuatro pantallas del panel responden 404 sobre un {@code intento_prueba}
     * que no existe, y el equipo se queda sin poder leer ni recalificar lo que su candidato
     * escribió.
     */
    private boolean rindeElCuestionario(Postulacion postulacion) {
        return vacantes.findById(postulacion.getVacanteId())
                .map(v -> CUESTIONARIO_TECNICO.equals(v.getInstrumentoEtapaTecnica()))
                .orElse(false);
    }

    @Override
    public List<RespuestaDePrueba> verRespuestas(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "abrir_ficha_candidato");
        if (rindeElCuestionario(postulacion)) {
            // El mismo contrato: para quien lee, son preguntas con lo que contestó. Que unas
            // vengan de una plantilla de prueba y otras del cuestionario de la vacante es
            // cosa nuestra, no de la pantalla.
            return cuestionarioTecnico.respuestasDe(postulacion).stream()
                    .map(r -> new RespuestaDePrueba(r.preguntaId(), r.codigo(), r.orden(),
                            "ABIERTA", r.enunciado(), r.texto(), r.respondidaEn()))
                    .toList();
        }
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba del puesto", "postulación", postulacionId));

        // Las preguntas de SU versión de la plantilla, en el orden en que las vio. Salen de
        // ahí y no del catálogo entero: una versión publicada después puede llevar otras, y
        // lo que hay que enseñar es lo que se le puso delante a esta persona.
        List<Long> ids = preguntasElegidas
                .findByVersionPlantillaPruebaIdOrderByOrden(intento.getVersionPlantillaPruebaId())
                .stream().map(PreguntaVersionPlantilla::getPreguntaPruebaId).toList();
        Map<Long, PreguntaPrueba> porId = preguntasCatalogo.findByIdIn(ids).stream()
                .collect(Collectors.toMap(PreguntaPrueba::getId, Function.identity()));
        Map<Long, RespuestaPrueba> suyas = respuestas.findByIntentoPruebaId(intento.getId())
                .stream().collect(Collectors.toMap(RespuestaPrueba::getPreguntaPruebaId,
                        Function.identity(), (a, b) -> a));

        List<RespuestaDePrueba> salida = new ArrayList<>();
        for (Long id : ids) {
            PreguntaPrueba pregunta = porId.get(id);
            if (pregunta == null) continue;
            RespuestaPrueba respuesta = suyas.get(id);
            // Se emite también la que dejó en blanco: saber que no contestó la cuarta es
            // parte de lo que se revisa, y omitirla la haría invisible.
            salida.add(new RespuestaDePrueba(
                    pregunta.getId(), pregunta.getCodigo(), pregunta.getOrden(),
                    pregunta.getTipo(), pregunta.getEnunciado(),
                    respuesta == null ? null : respuesta.getTexto(),
                    respuesta == null ? null : respuesta.getRespondidaEn()));
        }
        return salida;
    }

    @Override
    public CalificacionIaEncolada calificarConIa(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ajustar_nota");

        // ⚠️ Con el cuestionario técnico, este botón es la ÚNICA forma de recuperar una
        // calificación que no salió: si la IA estaba apagada al entregar, o el modelo devolvió
        // notas inservibles, la postulación se queda en PRUEBA_CALIFICANDO y su etapa —el 30%
        // de la decisión— desaparecería en silencio del puntaje final.
        if (rindeElCuestionario(postulacion)) {
            if (postulacion.getEvaluacionTecnicaId() == null) {
                throw new IllegalStateException("Esta persona todavía no tiene cuestionario "
                        + "técnico: se le crea al avanzarla a la etapa de la prueba");
            }
            if (!cola.encolarCuestionarioTecnico(postulacionId)) {
                return new CalificacionIaEncolada("SIN_CAMBIOS",
                        "No se pidió nada: o ya lo calificó el agente, o hay un trabajo en "
                                + "marcha ahora mismo, o la calificación con IA está apagada.");
            }
            return new CalificacionIaEncolada("ENCOLADA",
                    "La calificación del cuestionario técnico quedó en cola. Tarda decenas de "
                            + "segundos: vuelve a consultar la nota para verla.");
        }

        // Lo indispensable, dicho aquí y no tres reintentos después. El agente se plantaría
        // igual al pedir el insumo, pero entonces el mensaje se quedaría en el registro en
        // vez de llegar a quien apretó el botón.
        IntentoPrueba intento = intentos.findByPostulacionId(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba del puesto", "postulación", postulacionId));
        if (intento.getEntregadoEn() == null) {
            throw new IllegalStateException(
                    "Esta prueba todavía no está entregada: calificarla ahora daría una nota "
                            + "de lo que el candidato lleve escrito a medias");
        }

        // Si la rúbrica no le reserva nada al agente, encolar solo gastaría una vuelta de
        // cola para que el agente descubra lo mismo y termine sin hacer nada.
        long paraElAgente = laRubricaDe(postulacion).stream()
                .filter(c -> "AGENTE".equals(c.getMetodoVerificacion()))
                .count();
        if (paraElAgente == 0) {
            return new CalificacionIaEncolada("SIN_CAMBIOS",
                    "La rúbrica de esta prueba no tiene ningún criterio marcado para el agente: "
                            + "la califica una persona entera.");
        }

        if (!cola.encolarPruebaPuesto(postulacionId)) {
            return new CalificacionIaEncolada("SIN_CAMBIOS",
                    "No se pidió nada: o ya está calificada por el agente, o hay un trabajo en "
                            + "marcha ahora mismo.");
        }
        return new CalificacionIaEncolada("ENCOLADA",
                "La calificación quedó en cola. Tarda decenas de segundos: vuelve a consultar "
                        + "las notas para verla.");
    }

    @Override
    @Transactional
    public void ponerNota(ContextoUsuario quien, Long postulacionId, Long criterioId, PonerNotaCriterio datos) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ajustar_nota");
        Criterio criterio = criterios.findById(criterioId)
                .orElseThrow(() -> new ResourceNotFoundException("Criterio", "id", criterioId));
        if (!laRubricaDe(postulacion).contains(criterio)) {
            throw new IllegalArgumentException("Ese criterio no pertenece a la rúbrica de esta prueba");
        }
        BigDecimal maximo = criterio.getPuntos();
        BigDecimal puntaje = BigDecimal.valueOf(datos.puntaje());
        if (puntaje.compareTo(BigDecimal.ZERO) < 0 || (maximo != null && puntaje.compareTo(maximo) > 0)) {
            throw new IllegalArgumentException(
                    "El puntaje de «%s» tiene que estar entre 0 y %s".formatted(criterio.getNombre(), maximo));
        }

        NotaCriterio fila = notasCriterio.findByPostulacionIdAndCriterioId(postulacionId, criterioId)
                .orElseGet(() -> NotaCriterio.builder()
                        .postulacionId(postulacionId)
                        .criterioId(criterioId)
                        .creadoEn(Instant.now())
                        .build());
        boolean yaExistia = fila.getId() != null;
        fila.setPuntaje(puntaje);
        fila.setExplicacion(datos.explicacion());
        fila.setOrigen("PERSONA");
        fila.setCalificadaPorUsuarioId(quien.usuarioId());
        if (yaExistia) {
            fila.setAjustadaPorUsuarioId(quien.usuarioId());
            fila.setMotivoAjuste(datos.explicacion());
            fila.setAjustadaEn(Instant.now());
        }
        notasCriterio.save(fila);
    }

    @Override
    @Transactional
    public BigDecimal calcularNotaEtapa(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ajustar_nota");
        // El cuestionario técnico no se pondera por rúbrica: su nota es el índice sobre las
        // calificaciones de sus respuestas. Recalcularlo aquí le da al equipo la misma
        // palanca que tiene con la prueba del puesto — pedirlo cuando ya están las notas.
        if (rindeElCuestionario(postulacion)) {
            cuestionarioTecnico.calificarEtapa(postulacion);
            return notasEtapa.findByPostulacionIdAndEtapaCodigo(postulacion.getId(), ETAPA)
                    .map(n -> n.getPuntaje())
                    .orElseThrow(() -> new IllegalStateException(
                            "Todavía no se puede poner la nota: falta la calificación de "
                                    + "alguna respuesta del cuestionario"));
        }
        // Se delega en la versión compartida a propósito. Aquí hubo una copia de la misma
        // suma, y la copia se desvió: sumaba TODAS las notas de criterio de la postulación
        // en vez de las de esta rúbrica. Como `nota_criterio` es una sola tabla para las tres
        // etapas que puntúan por criterio, a la nota de la prueba se le pegaban las del
        // perfil: un candidato con 50 sobre 100 salió con 675. Una rúbrica bien acotada no
        // basta si quien suma no la mira.
        return calificacion.calcularNotaEtapa(postulacion, ETAPA, laRubricaDe(postulacion));
    }

    @Override
    @Transactional
    public PlazoPrueba definirPlazo(ContextoUsuario quien, Long postulacionId,
                                    DefinirPlazoPrueba datos) {
        Postulacion postulacion = laVisible(quien, postulacionId, "mover_postulacion");
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba del puesto", "postulación", postulacionId));
        if (intento.getEntregadoEn() != null) {
            throw new IllegalStateException(
                    "Esta prueba ya se entregó: cambiarle el plazo ahora no cambia nada");
        }
        // Una fecha ya pasada se la entregaría sola en el siguiente barrido. Si de verdad se
        // le quiere cerrar, hay una transición para eso y deja dicho por qué.
        if (datos.venceEn().isBefore(Instant.now())) {
            throw new IllegalArgumentException(
                    "Esa fecha ya pasó: al candidato se le entregaría la prueba sola");
        }

        Instant anterior = intento.getVenceEn();
        intento.setVenceEn(datos.venceEn());
        // Queda marcado como suyo: si después se mueve la fecha de la convocatoria, a esta
        // persona no se la toca. Sin la marca, «más horas para este candidato» se perdería
        // en el siguiente cambio de la vacante y nadie lo notaría (V32).
        intento.setPlazoPropio(true);
        intentos.save(intento);

        auditoria.registrar(quien.organizacionId(), quien, "definir_plazo_prueba",
                "intento_prueba", intento.getId(),
                anterior == null ? null : Map.of("venceEn", anterior.toString()),
                Map.of("venceEn", datos.venceEn().toString()), datos.motivo());

        return new PlazoPrueba(postulacionId, intento.getVenceEn(),
                intento.getIniciadoEn() != null);
    }

    // ============ Apoyo ============

    /**
     * La rúbrica con la que se puntúa esta prueba.
     *
     * <p>⚠️ El cuestionario técnico <b>no tiene</b>: se califica pregunta a pregunta contando
     * criterios, no repartiendo cien puntos entre unos cuantos apartados. Devolver vacío en vez
     * de reventar es lo que deja que la pantalla de notas se abra igual y enseñe lo que sí hay.
     */
    private List<Criterio> laRubricaDe(Postulacion postulacion) {
        if (rindeElCuestionario(postulacion)) {
            return List.of();
        }
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Prueba del puesto", "postulación", postulacion.getId()));
        return criterios.findByVersionPlantillaPruebaId(intento.getVersionPlantillaPruebaId());
    }

    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId, String permiso) {
        return alcance.laPostulacionVisible(quien, postulacionId, permiso);
    }
}
