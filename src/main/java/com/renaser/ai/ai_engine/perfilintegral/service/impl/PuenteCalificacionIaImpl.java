package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.AfirmacionIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.AlertaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.CriterioConPeso;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.HallazgoIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.AbiertaPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoRespuestas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaCriterioIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaRespuestaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.RespuestaAbierta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.Alerta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.DatosDeLaVacanteParaIa;
import com.renaser.ai.ai_engine.perfilintegral.entity.HallazgoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.repository.AfirmacionCvRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.AlertaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.HallazgoPerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.FormulasCazatalentos;
import com.renaser.ai.ai_engine.perfilintegral.service.LectorBancoCazatalentos;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.service.RetratoTerminado;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion;
import com.renaser.ai.ai_engine.pesos.entity.PesoComponentePerfil;
import com.renaser.ai.ai_engine.pesos.repository.PesoComponentePerfilRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EnlaceCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.postulacion.service.ServicioTextoCv;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ver {@link PuenteCalificacionIa}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuenteCalificacionIaImpl implements PuenteCalificacionIa {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal CUATRO = BigDecimal.valueOf(4);
    private static final String ETAPA = "PERFIL_INTEGRAL";

    // Los tipos de hallazgo que la base admite. La Regla 1 del doc 03 prohíbe mezclarlos,
    // así que un tipo que el modelo se invente se descarta en vez de guardarse mal.
    private static final List<String> TIPOS_HALLAZGO = List.of(
            "FORTALEZA", "RIESGO_CRITICO", "RIESGO_DESARROLLABLE", "PREFERENCIA", "FALTA_EVIDENCIA");
    private static final List<String> CLASIFICACIONES = List.of(
            "DEMOSTRADA", "DECLARADA", "CONTRADICHA", "FALTA_INFO");
    private static final List<String> TIPOS_ALERTA = List.of("CONTRADICCION", "DEMASIADO_IDEAL");

    private final PostulacionRepository postulaciones;
    private final com.renaser.ai.ai_engine.perfil.service.ServicioPropuestaPerfil propuestaPerfil;
    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;
    private final CvRepository cvs;
    private final DatoCvRepository datosCv;
    private final EnlaceCvRepository enlaces;
    private final RespuestaRepository respuestas;
    private final PreguntaRepository preguntas;
    private final PreguntaDimensionRepository preguntaDimensiones;
    private final CriterioRepository criterios;
    private final PesoCriterioRepository pesosCriterio;
    private final PesoComponentePerfilRepository pesosComponente;
    private final NotaCriterioRepository notasCriterio;
    private final NotaRespuestaRepository notasRespuesta;
    private final NotaEtapaRepository notasEtapa;
    private final AfirmacionCvRepository afirmaciones;
    private final AlertaRepository alertas;
    private final PerfilTalentoRepository perfiles;
    private final HallazgoPerfilRepository hallazgos;
    private final ServicioTextoCv textoCv;
    private final ServicioCalificacion calificacion;
    /** Para avisar de que el retrato terminó, sin llamar a quien avanza. Ver RetratoTerminado. */
    private final org.springframework.context.ApplicationEventPublisher avisos;
    private final ServicioParametros parametros;
    private final MaquinaEstados maquina;
    private final CalificacionCriterios calificacionCriterios;
    private final CalificacionCuestionarioTecnico calificacionTecnica;
    // Las preguntas propias de la vacante (método PUNTOS, V66)
    private final EvaluacionRepository evaluaciones;
    private final CalificacionPorPuntos porPuntos;
    private final DatosDeLaVacanteParaIa datosDeLaVacante;

    @Override
    public Long organizacionDe(Long postulacionId) {
        return postulacion(postulacionId).getOrganizacionId();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean tieneEvaluacionEntregada(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        return postulacion.getEvaluacionId() != null
                && !respuestas.findByEvaluacionId(postulacion.getEvaluacionId()).isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean tieneFichaCv(Long postulacionId) {
        return datosCv.findByPostulacionId(postulacionId).isPresent();
    }

    // ==================== DATOS_CV ====================

    @Override
    @Transactional
    public InsumoDatos insumoDatos(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        Puesto puesto = puesto(vacante(postulacion));
        // El mismo texto recortado que ven los demás agentes: sin foto, edad, sexo ni
        // estado civil. Este no tiene forma de pedir el original, igual que los otros.
        return new InsumoDatos(puesto.getNombre(), textoCv.prepararParaIa(postulacionId));
    }

    @Override
    @Transactional
    public void guardarDatos(Long postulacionId, Long ejecucionIaId, ResultadoDatos resultado) {
        if (resultado == null) {
            throw new IllegalStateException(
                    "El agente DATOS_CV no devolvió nada: no se guarda una ficha vacía");
        }
        DatoCv fila = datosCv.findByPostulacionId(postulacionId)
                .orElseGet(() -> DatoCv.builder()
                        .postulacionId(postulacionId)
                        .creadoEn(Instant.now())
                        .build());
        fila.setNombre(recortar(resultado.nombre(), 200));
        fila.setEmail(recortar(resultado.email(), 200));
        fila.setTelefono(recortar(resultado.telefono(), 60));
        fila.setPerfilResumen(recortar(resultado.perfilResumen(), 500));
        // Cinco como mucho, unidas por «|». Si el modelo devuelve quince, sobran diez: la
        // instrucción pide las más relevantes y una lista larga no se lee de un vistazo.
        // Vacio se guarda como null y no como cadena vacia, igual que el resto de textos:
        // «no dijo ninguna habilidad» y «dijo la cadena vacia» no son lo mismo, y una
        // pantalla que pinte lo que haya no debe enseñar un hueco donde no hay dato.
        String habilidades = lista(resultado.habilidades()).stream()
                .filter(h -> !esVacio(h))
                .limit(5)
                .collect(Collectors.joining(" | "));
        fila.setHabilidades(habilidades.isEmpty() ? null : habilidades);
        fila.setExperienciaMesesTotal(mesesValidos(resultado.experienciaMesesTotal()));
        fila.setUltimoPuesto(recortar(resultado.ultimoPuesto(), 200));
        fila.setUltimaEmpresa(recortar(resultado.ultimaEmpresa(), 200));
        fila.setUltimaMesesDuracion(mesesValidos(resultado.ultimaMesesDuracion()));
        fila.setEducacionMaxima(recortar(resultado.educacionMaxima(), 120));
        fila.setEjecucionIaId(ejecucionIaId);
        fila.setActualizadoEn(Instant.now());
        datosCv.save(fila);

        // Lo mismo que se guardo aqui se PROPONE al perfil del candidato. Propone, no
        // escribe encima: lo que la persona corrigio o confirmo no se toca (RF-159).
        // Misma transaccion a proposito — si el perfil fallara, la ficha tampoco se
        // daria por guardada y el trabajo se reintentaria entero.
        propuestaPerfil.proponer(postulacionId, resultado);

        log.info("DATOS_CV: ficha de la postulación {} guardada ({})",
                postulacionId, fila.getNombre() == null ? "sin nombre" : fila.getNombre());
    }

    /**
     * Meses que se pueden creer.
     *
     * <p>Un negativo es un error de cuenta del modelo y 900 meses son setenta y cinco años
     * de carrera. Ninguno de los dos se guarda: vale más un hueco que un dato falso, porque
     * el hueco se ve y el dato falso se cree.
     */
    private Integer mesesValidos(Integer meses) {
        return meses == null || meses < 0 || meses > 720 ? null : meses;
    }

    private String recortar(String texto, int tope) {
        if (esVacio(texto)) return null;
        String limpio = texto.trim();
        return limpio.length() <= tope ? limpio : limpio.substring(0, tope);
    }

    // ==================== EVIDENCIA_CV ====================

    @Override
    @Transactional
    public InsumoCv insumoCv(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        Vacante vacante = vacante(postulacion);
        Puesto puesto = puesto(vacante);

        String curriculum = textoCv.prepararParaIa(postulacionId);
        Cv cv = cvs.findByPostulacionId(postulacionId).orElseThrow();

        return new InsumoCv(
                puesto.getNombre(),
                puesto.getNivelPuestoCodigo(),
                queBusca(vacante),
                curriculum,
                cv.getResultadoOrgulloso(),
                enlaces.findByCvId(cv.getId()).stream().map(e -> e.getTipo() + ": " + e.getUrl()).toList(),
                criteriosConPeso(vacante, puesto));
    }

    @Override
    @Transactional
    public void guardarEvidenciaCv(Long postulacionId, Long ejecucionIaId, ResultadoCv resultado) {
        Postulacion postulacion = postulacion(postulacionId);
        Vacante vacante = vacante(postulacion);
        Puesto puesto = puesto(vacante);

        Map<String, Criterio> porCodigo = criteriosDelCurriculum().stream()
                .collect(Collectors.toMap(Criterio::getCodigo, Function.identity()));

        int guardadas = 0;
        for (NotaCriterioIa nota : lista(resultado == null ? null : resultado.criterios())) {
            Criterio criterio = porCodigo.get(nota.codigo());
            if (criterio == null) {
                log.warn("El agente devolvió un criterio que no existe: {}", nota.codigo());
                continue;
            }
            // Una nota sin explicación no se guarda (RF-150). No se pone un cero en su
            // lugar: se queda sin nota, que es distinto de valer cero.
            if (nota.puntaje() == null || esVacio(nota.explicacion())) {
                log.warn("Nota del criterio {} descartada: llegó sin puntaje o sin explicación",
                        nota.codigo());
                continue;
            }

            NotaCriterio fila = notasCriterio
                    .findByPostulacionIdAndCriterioId(postulacionId, criterio.getId())
                    .orElseGet(() -> NotaCriterio.builder()
                            .postulacionId(postulacionId)
                            .criterioId(criterio.getId())
                            .creadoEn(Instant.now())
                            .build());
            // Si una persona ya la ajustó a mano, la IA no la pisa: el ajuste manda.
            if (fila.getAjustadaPorUsuarioId() != null) {
                continue;
            }
            fila.setPuntaje(acotar(nota.puntaje(), CIEN));
            fila.setExplicacion(conEvidencia(nota.explicacion(), nota.evidencia()));
            fila.setOrigen("AGENTE");
            fila.setConfianza(acotar(resultado.confianza(), CIEN));
            fila.setEjecucionIaId(ejecucionIaId);
            notasCriterio.save(fila);
            guardadas++;
        }

        guardarAfirmaciones(postulacionId, ejecucionIaId, resultado);
        log.info("EVIDENCIA_CV: {} de {} criterios guardados para la postulación {} (nivel {})",
                guardadas, porCodigo.size(), postulacionId, puesto.getNivelPuestoCodigo());
    }

    private void guardarAfirmaciones(Long postulacionId, Long ejecucionIaId, ResultadoCv resultado) {
        Cv cv = cvs.findByPostulacionId(postulacionId).orElse(null);
        if (cv == null) return;

        // Un reintento no debe dejar la lista duplicada: se rehace entera.
        afirmaciones.deleteByCvId(cv.getId());
        for (AfirmacionIa afirmacion : lista(resultado == null ? null : resultado.afirmaciones())) {
            if (esVacio(afirmacion.texto()) || !CLASIFICACIONES.contains(afirmacion.clasificacion())) {
                continue;
            }
            afirmaciones.save(AfirmacionCv.builder()
                    .cvId(cv.getId())
                    .texto(afirmacion.texto())
                    .clasificacion(afirmacion.clasificacion())
                    .preguntaValidacion(afirmacion.preguntaValidacion())
                    .ejecucionIaId(ejecucionIaId)
                    .creadoEn(Instant.now())
                    .build());
        }
    }

    // ==================== EVALUADOR ====================

    @Override
    @Transactional
    public InsumoRespuestas insumoRespuestas(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        Puesto puesto = puesto(vacante(postulacion));
        // El método del banco viaja en el insumo: es lo que le dice al agente con qué
        // formato responder — criterios en CRITERIOS, puntaje directo en el resto.
        return new InsumoRespuestas(puesto.getNombre(), puesto.getNivelPuestoCodigo(),
                calificacionCriterios.metodoDe(postulacion),
                abiertas(postulacion.getEvaluacionId()));
    }

    /**
     * Lo mismo, pero del cuestionario técnico de la vacante (etapa 2).
     *
     * <p>El único cambio es de qué examen se leen las respuestas. El método es siempre
     * CRITERIOS —lo fija la V42 al crear el banco de la vacante— y la guía de cada pregunta
     * (C3, C4 y la señal de 0) viaja igual, porque vive en la propia {@code pregunta}.
     */
    @Override
    @Transactional(readOnly = true)
    public InsumoRespuestas insumoRespuestasTecnicas(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        Puesto puesto = puesto(vacante(postulacion));
        return new InsumoRespuestas(puesto.getNombre(), puesto.getNivelPuestoCodigo(),
                CalificacionCriterios.METODO,
                abiertas(postulacion.getEvaluacionTecnicaId()));
    }

    /**
     * Las abiertas son las que tienen texto y puntúan.
     *
     * <p>Las cerradas quedan fuera porque ya las puntuó el código contra la clave, y el
     * modelo generativo no puede tocarlas (RF-147). Las de estilo y consistencia tampoco
     * están: no suman nota por diseño.
     */
    private List<RespuestaAbierta> abiertas(Long evaluacionId) {
        if (evaluacionId == null) {
            return List.of();
        }
        List<Respuesta> suyas = respuestas.findByEvaluacionId(evaluacionId).stream()
                .filter(r -> r.getOpcionId() == null && !esVacio(r.getTexto()))
                .toList();
        if (suyas.isEmpty()) {
            return List.of();
        }

        List<Long> ids = suyas.stream().map(Respuesta::getPreguntaId).toList();
        Map<Long, Pregunta> porId = preguntas.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        Map<Long, List<String>> dimensiones = preguntaDimensiones.findByPreguntaIdIn(ids).stream()
                .collect(Collectors.groupingBy(PreguntaDimension::getPreguntaId,
                        Collectors.mapping(PreguntaDimension::getDimensionCodigo, Collectors.toList())));

        List<RespuestaAbierta> salida = new ArrayList<>();
        for (Respuesta r : suyas) {
            Pregunta p = porId.get(r.getPreguntaId());
            if (p == null || !p.isEsPuntuable()) {
                continue;
            }
            salida.add(new RespuestaAbierta(r.getId(), p.getTipo(), p.getEnunciado(),
                    p.getSituacion(), dimensiones.getOrDefault(p.getId(), List.of()), r.getTexto(),
                    p.getC3Esperado(), p.getC4Esperado(), p.getSenalDeCero()));
        }
        return salida;
    }

    @Override
    @Transactional
    public void guardarNotasAbiertas(Long postulacionId, Long ejecucionIaId,
                                     ResultadoEvaluador resultado) {
        Postulacion postulacion = postulacion(postulacionId);
        String metodo = calificacionCriterios.metodoDe(postulacion);
        // Una evaluación por puntos que llegara por aquí se guarda con SU red, no con la del
        // 0–4: acotarla a 4 le quitaría a una abierta de 20 casi toda su escala.
        if (CalificacionPorPuntos.METODO.equals(metodo)) {
            InsumoPorPuntos insumo = insumoPorPuntos(postulacionId);
            guardarNotasPorPuntos(postulacionId, ejecucionIaId, resultado,
                    insumo == null ? 1 : insumo.versionGuia(), false);
            return;
        }
        guardarNotasDe(postulacion, postulacion.getEvaluacionId(), ejecucionIaId, resultado,
                CalificacionCriterios.METODO.equals(metodo), false);
    }

    // ==================== EVALUADOR · preguntas propias (PUNTOS) ====================

    @Override
    @Transactional(readOnly = true)
    public InsumoPorPuntos insumoPorPuntos(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        Evaluacion evaluacion = postulacion.getEvaluacionId() == null ? null
                : evaluaciones.findById(postulacion.getEvaluacionId()).orElse(null);
        VersionBanco version = evaluacion == null ? null
                : porPuntos.versionPorPuntos(evaluacion.getVersionBancoNivelId()).orElse(null);
        if (version == null) {
            return null;
        }
        CalificacionPorPuntos.Resultado cuenta = porPuntos.calcular(version.getId(),
                evaluacion.getId());

        // Solo las que tienen puntos y algo escrito: una de 0 puntos no puede sumar nada y
        // una en blanco vale 0 sin preguntarle a nadie. Y sin las ajustadas a mano: esas ya
        // no las toca la IA, así que mandarlas sería pagar por una nota que no se guarda.
        List<AbiertaPorPuntos> abiertas = new ArrayList<>();
        for (CalificacionPorPuntos.CriterioCalculado c : cuenta.criterios()) {
            for (CalificacionPorPuntos.PreguntaCalculada p : c.preguntas()) {
                if (seLeManda(p)) {
                    abiertas.add(comoAbierta(p, c.criterio().getNombre(),
                            c.criterio().getQueEvalua()));
                }
            }
        }
        for (CalificacionPorPuntos.PreguntaCalculada p : cuenta.sinCriterio()) {
            if (seLeManda(p)) {
                abiertas.add(comoAbierta(p, null, null));
            }
        }

        Vacante vacante = vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException(
                        "La vacante de esta postulación ya no existe"));
        return new InsumoPorPuntos(version.getVersionGuia() == null ? 1 : version.getVersionGuia(),
                version.getGuiaCalificacion(), datosDeLaVacante.de(vacante), abiertas);
    }

    private static boolean seLeManda(CalificacionPorPuntos.PreguntaCalculada p) {
        return p.vaALaIa()
                && (p.nota() == null || p.nota().getAjustadaPorUsuarioId() == null);
    }

    private static AbiertaPorPuntos comoAbierta(CalificacionPorPuntos.PreguntaCalculada p,
                                                String criterio, String queEvalua) {
        return new AbiertaPorPuntos(p.respuesta().getId(), p.pregunta().getEnunciado(),
                p.maximo(), p.pregunta().getQueDebeTener(), criterio, queEvalua,
                p.respuesta().getTexto());
    }

    @Override
    @Transactional
    public boolean guardarNotasPorPuntos(Long postulacionId, Long ejecucionIaId,
                                         ResultadoEvaluador resultado, int versionGuia,
                                         boolean recalificacion) {
        Postulacion postulacion = postulacion(postulacionId);
        Evaluacion evaluacion = postulacion.getEvaluacionId() == null ? null
                : evaluaciones.findById(postulacion.getEvaluacionId()).orElse(null);
        VersionBanco version = evaluacion == null ? null
                : porPuntos.versionPorPuntos(evaluacion.getVersionBancoNivelId()).orElse(null);
        if (version == null) {
            throw new IllegalStateException("La evaluación de la postulación " + postulacionId
                    + " no es de preguntas propias: sus notas no se guardan por puntos");
        }

        // Calculada con una guía anterior: se descarta entera y el evaluador la vuelve a pedir
        // con la de ahora. Así, quien se estaba calificando justo al cambiar la guía no se
        // queda medido con la vieja.
        int vigente = version.getVersionGuia() == null ? 1 : version.getVersionGuia();
        if (vigente != versionGuia) {
            log.info("EVALUADOR: las notas de la postulación {} se calcularon con la guía {} y "
                    + "la vigente es la {}; se descartan", postulacionId, versionGuia, vigente);
            return false;
        }

        CalificacionPorPuntos.Resultado cuenta = porPuntos.calcular(version.getId(),
                evaluacion.getId());
        Map<Long, CalificacionPorPuntos.PreguntaCalculada> mias = new HashMap<>();
        cuenta.todas().stream()
                .filter(CalificacionPorPuntos.PreguntaCalculada::vaALaIa)
                .forEach(p -> mias.put(p.respuesta().getId(), p));

        // La red de seguridad: lo que llega del modelo se filtra aquí, no en el agente.
        Map<Long, NotaRespuesta> aGuardar = new java.util.LinkedHashMap<>();
        for (NotaRespuestaIa nota : lista(resultado == null ? null : resultado.notas())) {
            CalificacionPorPuntos.PreguntaCalculada suya =
                    nota.respuestaId() == null ? null : mias.get(nota.respuestaId());
            if (suya == null) {
                log.warn("El agente devolvió una nota para una respuesta que no es de esta "
                        + "evaluación: {}", nota.respuestaId());
                continue;
            }
            if (esVacio(nota.explicacion())) {
                log.warn("Nota de la respuesta {} descartada: sin explicación", nota.respuestaId());
                continue;
            }
            if (nota.puntaje() == null) {
                log.warn("Nota de la respuesta {} descartada: sin puntaje", nota.respuestaId());
                continue;
            }
            NotaRespuesta fila = suya.nota();
            if (fila != null && fila.getAjustadaPorUsuarioId() != null) {
                continue;   // la ajustó una persona: la IA ya no la toca
            }
            if (fila == null) {
                fila = NotaRespuesta.builder()
                        .respuestaId(nota.respuestaId())
                        .creadoEn(Instant.now())
                        .build();
            }
            fila.setPuntaje(acotar(nota.puntaje(), BigDecimal.valueOf(suya.maximo())));
            fila.setExplicacion(nota.explicacion());
            fila.setEvidenciaCitada(nota.evidenciaCitada());
            fila.setConfianza(acotar(nota.confianza(), CIEN));
            fila.setEjecucionIaId(ejecucionIaId);
            fila.setVersionGuia(versionGuia);
            aGuardar.put(nota.respuestaId(), fila);
        }

        if (recalificacion) {
            // Las notas nuevas de una persona se aplican juntas: nunca unas abiertas con la
            // guía vieja y otras con la nueva. Tiene que haber llegado cada abierta que ya
            // tenía nota de la IA; si falta alguna, no se guarda nada y la cola lo reintenta.
            List<Long> faltan = mias.values().stream()
                    .filter(p -> p.nota() != null && p.nota().getAjustadaPorUsuarioId() == null)
                    .map(p -> p.respuesta().getId())
                    .filter(id -> !aGuardar.containsKey(id))
                    .toList();
            if (!faltan.isEmpty()) {
                throw new IllegalStateException("La recalificación dejó " + faltan.size()
                        + " abiertas sin nota nueva (" + faltan + "): las notas de una persona "
                        + "se aplican juntas o ninguna, se reintenta");
            }
        }

        notasRespuesta.saveAll(aGuardar.values());
        log.info("EVALUADOR (puntos): {} de {} abiertas calificadas en la postulación {} con la "
                + "guía {}", aGuardar.size(), mias.size(), postulacionId, versionGuia);

        if (recalificacion) {
            // Como el ajuste a mano: se recalcula todo sin mover a nadie de etapa. La nota se
            // recalculaba en cerrarPerfilIntegral, que aquí no corre (movería a la persona).
            recalcularSinMover(postulacionId);
        }
        return true;
    }

    @Override
    @Transactional
    public void recalcularSinMover(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        Vacante vacante = vacante(postulacion);
        Puesto puesto = puesto(vacante);
        BigDecimal nota = recalcularNotaDeLaEtapa(postulacionId, vacante, puesto);

        // El grupo necesita saber si hay un riesgo crítico. Tras un ajuste no hay resultado
        // de la IA en la mano: se leen los hallazgos que dejó el retrato.
        PerfilTalento perfil = perfiles.findByPostulacionId(postulacionId).orElse(null);
        boolean riesgoCritico = perfil != null && hallazgos.findByPerfilTalentoId(perfil.getId())
                .stream().anyMatch(h -> "RIESGO_CRITICO".equals(h.getTipo()));
        postulacion.setGrupoPrioridad(grupoDe(postulacion.getOrganizacionId(), nota,
                perfil == null ? null : perfil.getPotencial(), riesgoCritico));
        postulaciones.save(postulacion);
        log.info("La postulación {} se recalculó sin moverla de etapa: nota {} y grupo {}",
                postulacionId, nota, postulacion.getGrupoPrioridad());
    }

    /**
     * Las notas del cuestionario técnico.
     *
     * <p>Mismo cuerpo, otro examen, y al final otra nota de etapa: la de PRUEBA_PUESTO en vez
     * de la del perfil integral.
     */
    @Override
    @Transactional
    public void guardarNotasTecnicas(Long postulacionId, Long ejecucionIaId,
                                     ResultadoEvaluador resultado) {
        Postulacion postulacion = postulacion(postulacionId);
        guardarNotasDe(postulacion, postulacion.getEvaluacionTecnicaId(), ejecucionIaId,
                resultado, true, true);
    }

    /** Poner la nota de etapa del cuestionario técnico sin pasar por el modelo. */
    @Override
    @Transactional
    public void cerrarNotaTecnica(Long postulacionId) {
        calificacionTecnica.calificarEtapa(postulacion(postulacionId));
    }

    private void guardarNotasDe(Postulacion postulacion, Long evaluacionId, Long ejecucionIaId,
                                ResultadoEvaluador resultado, boolean porCriterios,
                                boolean tecnico) {
        Long postulacionId = postulacion.getId();
        List<Long> mias = abiertas(evaluacionId).stream()
                .map(RespuestaAbierta::respuestaId).toList();

        // En un banco CRITERIOS el agente no trae puntaje: trae los criterios, y el número
        // lo cuenta el código. Para la regla dura (R11) hace falta saber de qué pregunta es
        // cada respuesta, así que se resuelve el mapa una vez.
        Map<Long, Pregunta> preguntaDeRespuesta = porCriterios
                ? preguntaPorRespuesta(evaluacionId) : Map.of();

        // Para saber al final si quedó todo cubierto: lo que se guardó ahora más lo que ya
        // estaba ajustado a mano (eso no se pisa, pero cuenta como calificado).
        java.util.Set<Long> cubiertas = new java.util.HashSet<>();
        int guardadas = 0;
        for (NotaRespuestaIa nota : lista(resultado == null ? null : resultado.notas())) {
            if (nota.respuestaId() == null || !mias.contains(nota.respuestaId())) {
                log.warn("El agente devolvió una nota para una respuesta que no es de esta "
                        + "postulación: {}", nota.respuestaId());
                continue;
            }
            // La base exige explicación y el documento exige evidencia citada (RF-56).
            if (esVacio(nota.explicacion())) {
                log.warn("Nota de la respuesta {} descartada: sin explicación", nota.respuestaId());
                continue;
            }
            BigDecimal puntaje;
            if (porCriterios) {
                if (nota.cumpleSenalCero() == null || nota.c1Episodio() == null
                        || nota.c2Autoria() == null || nota.c3Dato() == null
                        || nota.c4Incomodidad() == null) {
                    log.warn("Nota de la respuesta {} descartada: en un banco CRITERIOS el "
                            + "agente declara los cuatro criterios y la señal, no un número",
                            nota.respuestaId());
                    continue;
                }
                Pregunta pregunta = preguntaDeRespuesta.get(nota.respuestaId());
                puntaje = BigDecimal.valueOf(FormulasCazatalentos.puntaje(
                        nota.cumpleSenalCero(), nota.c1Episodio(), nota.c2Autoria(),
                        nota.c3Dato(), nota.c4Incomodidad(),
                        pregunta == null ? null
                                : LectorBancoCazatalentos.topeSinDato(pregunta.getLogicaInterna())));
            } else if (nota.puntaje() == null) {
                log.warn("Nota de la respuesta {} descartada: sin puntaje", nota.respuestaId());
                continue;
            } else {
                puntaje = acotar(nota.puntaje(), CUATRO);
            }
            exigirEscalaDeCuatro(nota.respuestaId(), puntaje);

            NotaRespuesta fila = notasRespuesta.findByRespuestaId(nota.respuestaId())
                    .orElseGet(() -> NotaRespuesta.builder()
                            .respuestaId(nota.respuestaId())
                            .creadoEn(Instant.now())
                            .build());
            if (fila.getAjustadaPorUsuarioId() != null) {
                cubiertas.add(nota.respuestaId());      // calificada por una persona: no se pisa
                continue;
            }
            fila.setPuntaje(puntaje);
            fila.setExplicacion(nota.explicacion());
            fila.setEvidenciaCitada(nota.evidenciaCitada());
            fila.setConfianza(acotar(nota.confianza(), CIEN));
            fila.setEjecucionIaId(ejecucionIaId);
            if (porCriterios) {
                fila.setCumpleSenalCero(nota.cumpleSenalCero());
                fila.setC1Episodio(nota.c1Episodio());
                fila.setC2Autoria(nota.c2Autoria());
                fila.setC3Dato(nota.c3Dato());
                fila.setC4Incomodidad(nota.c4Incomodidad());
            }
            notasRespuesta.save(fila);
            cubiertas.add(nota.respuestaId());
            guardadas++;
        }
        log.info("{}: {} de {} respuestas calificadas en la postulación {}",
                tecnico ? "EVALUADOR_TECNICO" : "EVALUADOR", guardadas, mias.size(), postulacionId);

        if (porCriterios) {
            // En un banco CRITERIOS media rúbrica no es una nota, y la cola solo reintenta
            // lo FALLIDO: si el agente omitió respuestas o devolvió notas inservibles, dar
            // el trabajo por terminado dejaría la postulación sin nota de etapa para
            // siempre. Se revienta —la transacción deshace lo guardado— y el reintento
            // vuelve a pedir la tanda completa.
            List<Long> sinNota = mias.stream().filter(id -> !cubiertas.contains(id)).toList();
            if (!sinNota.isEmpty()) {
                throw new IllegalStateException("El evaluador dejó " + sinNota.size() + " de "
                        + mias.size() + " respuestas sin calificar (" + sinNota
                        + "): sin la tanda completa no hay nota de etapa, se reintenta");
            }
            // La nota de etapa sale de estas calificaciones, así que se recalcula aquí.
            if (tecnico) {
                calificacionTecnica.calificarEtapa(postulacion);
            } else {
                calificacionCriterios.calificarEtapa(postulacion);
            }
        }
    }

    /**
     * El 0–4 de los métodos de siempre (NULL y CRITERIOS), exigido al guardar.
     *
     * <p>Hasta la V66 lo exigía la base: {@code nota_respuesta.puntaje} tenía un CHECK 0..4.
     * Las preguntas propias necesitan hasta 100 y la tabla no sabe de qué método es cada
     * nota, así que el CHECK pasó a 0..100 y la guarda del 0–4 vive aquí. <b>Fuera de rango
     * se rechaza, no se acota</b> (AC-23): es lo que hacía la base, y protege a quien está
     * rindiendo el banco de RENASER. El guardado falla entero y la cola lo reintenta.
     */
    static void exigirEscalaDeCuatro(Long respuestaId, BigDecimal puntaje) {
        if (puntaje == null || puntaje.signum() < 0 || puntaje.compareTo(CUATRO) > 0) {
            throw new IllegalStateException("La nota " + puntaje + " de la respuesta "
                    + respuestaId + " está fuera del 0–4 de este banco: no se guarda");
        }
    }

    /** De qué pregunta es cada respuesta de un examen. */
    private Map<Long, Pregunta> preguntaPorRespuesta(Long evaluacionId) {
        List<Respuesta> suyas = respuestas.findByEvaluacionId(evaluacionId);
        Map<Long, Pregunta> porId = preguntas.findByIdIn(
                        suyas.stream().map(Respuesta::getPreguntaId).toList()).stream()
                .collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        return suyas.stream()
                .filter(r -> porId.containsKey(r.getPreguntaId()))
                .collect(Collectors.toMap(Respuesta::getId, r -> porId.get(r.getPreguntaId())));
    }

    // ==================== POTENCIAL_RIESGO ====================

    @Override
    @Transactional
    public InsumoPerfil insumoPerfil(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        Vacante vacante = vacante(postulacion);
        Puesto puesto = puesto(vacante);

        ServicioCalificacion.ResumenCerrado cerrado = calificacion.resumenDeLoCerrado(postulacionId);
        List<RespuestaAbierta> abiertas = abiertas(postulacion.getEvaluacionId());
        List<NotaRespuestaIa> notasAbiertas = notasDeLoAbierto(abiertas);

        // Las preguntas propias (V66) no se miden en 0–4: cada abierta vale lo suyo. Al
        // retrato se le lleva todo a las escalas que conoce —lo cerrado y lo abierto en
        // porcentaje de su máximo, y cada abierta a 0–4— para que no lea un 12 de 20 como
        // un 12 de 4.
        ServicioCalificacion.NotaDelBanco banco = calificacion.notaDelBancoPorPuntos(postulacionId);
        if (banco != null) {
            List<NotaRespuestaIa> escaladas = notasAbiertas.stream()
                    .filter(n -> banco.abiertasSobreCuatro().containsKey(n.respuestaId()))
                    .map(n -> new NotaRespuestaIa(n.respuestaId(),
                            banco.abiertasSobreCuatro().get(n.respuestaId()), n.explicacion(),
                            n.evidenciaCitada(), n.confianza(), null, null, null, null, null))
                    .toList();
            return new InsumoPerfil(
                    puesto.getNombre(),
                    puesto.getNivelPuestoCodigo(),
                    queBusca(vacante),
                    notaCurriculum(postulacionId, vacante, puesto),
                    notasDelCurriculum(postulacionId),
                    banco.cerradasSobreCien(),
                    banco.cerradas(),
                    banco.abiertasSobreCien(),
                    escaladas,
                    alertas.findByPostulacionId(postulacionId).stream()
                            .map(Alerta::getDescripcion).toList());
        }

        return new InsumoPerfil(
                puesto.getNombre(),
                puesto.getNivelPuestoCodigo(),
                queBusca(vacante),
                notaCurriculum(postulacionId, vacante, puesto),
                notasDelCurriculum(postulacionId),
                cerrado.nota(),
                cerrado.preguntas(),
                promedioAbiertas(notasAbiertas),
                notasAbiertas,
                alertas.findByPostulacionId(postulacionId).stream().map(Alerta::getDescripcion).toList());
    }

    @Override
    @Transactional
    public void cerrarPerfilIntegral(Long postulacionId, Long ejecucionIaId,
                                     ResultadoPerfil resultado) {
        Postulacion postulacion = postulacion(postulacionId);
        Vacante vacante = vacante(postulacion);
        Puesto puesto = puesto(vacante);

        if (resultado == null || resultado.confianzaEvidencia() == null) {
            // La base la exige NOT NULL, y con razón: es lo que le dice al equipo cuánto
            // fiarse del resto del perfil. Sin ella el perfil no vale, así que se reintenta.
            throw new IllegalStateException(
                    "El agente no devolvió la confianza de la evidencia: sin ella el Perfil de "
                            + "Talento no se guarda");
        }

        PerfilTalento perfil = perfiles.findByPostulacionId(postulacionId)
                .orElseGet(() -> PerfilTalento.builder()
                        .postulacionId(postulacionId)
                        .creadoEn(Instant.now())
                        .build());
        perfil.setAdecuacion(acotar(resultado.adecuacion(), CIEN));
        perfil.setPotencial(acotar(resultado.potencial(), CIEN));
        perfil.setAltoRendimiento(acotar(resultado.altoRendimiento(), CIEN));
        perfil.setConfianzaEvidencia(acotar(resultado.confianzaEvidencia(), CIEN));
        perfil.setResumen(resultado.resumen());
        perfil.setVersionPesosId(vacante.getVersionPesosId());
        perfil.setEjecucionIaId(ejecucionIaId);
        perfil.setActualizadoEn(Instant.now());
        perfil = perfiles.save(perfil);

        guardarHallazgos(perfil, resultado);
        guardarAlertas(postulacionId, ejecucionIaId, resultado);

        BigDecimal nota = recalcularNotaDeLaEtapa(postulacionId, vacante, puesto);
        postulacion.setGrupoPrioridad(grupoDe(postulacion.getOrganizacionId(), nota, perfil, resultado));
        postulaciones.save(postulacion);

        // Lo último, y solo por la máquina: nunca se escribe estado_codigo a mano.
        //
        // ⚠️ Y solo si sigue en el Perfil Integral. Entre que este trabajo empieza y termina
        // pasan minutos, y en ese rato una persona puede haberla avanzado desde el panel:
        // mover a «por confirmar» sin mirar la devolvía a la bandeja anterior, le quitaba al
        // candidato el turno que ya le habían dado y dejaba su intento de la prueba creado
        // pero fuera de etapa —desde ahí, volver a avanzarla chocaba contra la clave única y
        // el panel solo sabía decir «ya existe un registro con postulacion_id X». Lo
        // calificado se guarda igual: lo que no se hace es mandarla donde ya no está.
        if (maquina.sigueEnLaEtapa(postulacion, "PERFIL_INTEGRAL")) {
            maquina.transicionar(postulacion, "PERFIL_POR_CONFIRMAR", null, null, true, false, null);
            // Y si la vacante califica y avanza sola, alguien tiene que enterarse de que esto
            // ya terminó. Se avisa y no se llama: avanzar pasa por crear lo que el candidato
            // va a rendir, y ese camino vuelve hasta aquí cerrando un círculo con el que
            // Spring no arranca. Ver RetratoTerminado.
            avisos.publishEvent(new RetratoTerminado(postulacionId));
        } else {
            log.info("POTENCIAL_RIESGO: la postulación {} ya salió del Perfil Integral (está en "
                    + "{}): se guarda su perfil y su nota, pero no se la mueve",
                    postulacionId, postulacion.getEstadoCodigo());
        }

        log.info("POTENCIAL_RIESGO: postulación {} calificada con {} y grupo {}",
                postulacionId, nota, postulacion.getGrupoPrioridad());
    }

    private void guardarHallazgos(PerfilTalento perfil, ResultadoPerfil resultado) {
        hallazgos.deleteByPerfilTalentoId(perfil.getId());
        for (HallazgoIa hallazgo : lista(resultado.hallazgos())) {
            if (esVacio(hallazgo.descripcion()) || !TIPOS_HALLAZGO.contains(hallazgo.tipo())) {
                log.warn("Hallazgo descartado: tipo «{}» no es uno de los cinco", hallazgo.tipo());
                continue;
            }
            hallazgos.save(HallazgoPerfil.builder()
                    .perfilTalentoId(perfil.getId())
                    .tipo(hallazgo.tipo())
                    .descripcion(hallazgo.descripcion())
                    .evidencia(hallazgo.evidencia())
                    .esCanalizable(Boolean.TRUE.equals(hallazgo.esCanalizable()))
                    .sugerencia(hallazgo.sugerencia())
                    .creadoEn(Instant.now())
                    .build());
        }
    }

    /**
     * Las alertas que ve la IA, que son las de «demasiado ideal».
     *
     * <p>Las de contradicción las levanta el código comparando dos números, no el modelo. Por
     * eso aquí solo se guardan las que no existan ya con la misma descripción: un reintento
     * no debe llenar la ficha de alertas repetidas.
     */
    private void guardarAlertas(Long postulacionId, Long ejecucionIaId, ResultadoPerfil resultado) {
        List<String> yaEstan = alertas.findByPostulacionId(postulacionId).stream()
                .map(Alerta::getDescripcion).toList();
        for (AlertaIa alerta : lista(resultado.alertas())) {
            if (esVacio(alerta.descripcion()) || !TIPOS_ALERTA.contains(alerta.tipo())
                    || yaEstan.contains(alerta.descripcion())) {
                continue;
            }
            alertas.save(Alerta.builder()
                    .postulacionId(postulacionId)
                    .tipo(alerta.tipo())
                    .descripcion(alerta.descripcion())
                    .ejecucionIaId(ejecucionIaId)
                    .creadoEn(Instant.now())
                    .build());
        }
    }

    // ==================== Las cuentas ====================

    /**
     * La nota del currículum sobre 100: cada criterio por su peso del nivel.
     *
     * <p>Los pesos de RF-43 suman 100 en los tres niveles, así que el resultado ya viene en
     * esa escala. Se divide entre los pesos <b>de los criterios que sí tienen nota</b>, no
     * entre 100 fijo: si la IA no pudo puntuar uno, lo justo es repartir, no restar.
     *
     * <p><b>Solo entran los criterios del currículum</b>, y hay que ser explícito porque la
     * versión de pesos ya no es solo suya: desde que existen la simulación y la validación,
     * la misma versión trae también los diez criterios de una y los nueve de la otra. Sumar
     * «todo lo que tenga peso» hacía que la nota del currículum cambiara en cuanto un
     * facilitador calificaba una simulación, y dos currículums idénticos mostraban notas
     * distintas sin que nadie hubiera tocado el currículum.
     */
    private BigDecimal notaCurriculum(Long postulacionId, Vacante vacante, Puesto puesto) {
        Set<Long> delCurriculum = criterios
                .findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(ETAPA).stream()
                .map(Criterio::getId)
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> pesos = pesosCriterio
                .findByVersionPesosIdAndNivelPuestoCodigo(
                        vacante.getVersionPesosId(), puesto.getNivelPuestoCodigo()).stream()
                .filter(pc -> delCurriculum.contains(pc.getCriterioId()))
                .collect(Collectors.toMap(PesoCriterio::getCriterioId, PesoCriterio::getPeso,
                        (a, b) -> a));

        BigDecimal suma = BigDecimal.ZERO;
        BigDecimal pesoTotal = BigDecimal.ZERO;
        for (NotaCriterio nota : notasCriterio.findByPostulacionId(postulacionId)) {
            BigDecimal peso = pesos.get(nota.getCriterioId());
            if (peso == null || nota.getPuntaje() == null) continue;
            suma = suma.add(nota.getPuntaje().multiply(peso));
            pesoTotal = pesoTotal.add(peso);
        }
        return pesoTotal.compareTo(BigDecimal.ZERO) == 0
                ? null
                : suma.divide(pesoTotal, 2, RoundingMode.HALF_UP);
    }

    /**
     * La nota de la etapa del Perfil Integral: el currículum y la evaluación, con el reparto
     * que dice {@code peso_componente_perfil} de la versión de pesos de <b>la vacante</b>.
     *
     * <p>Hasta ahora esta fila guardaba solo lo cerrado, porque era lo único que existía.
     * Ahora que la IA ya puntuó lo demás, se rehace con todo: currículum (12 puntos en la v2),
     * psicométrico (0, que aún no existe) y evaluación (28). El componente psicométrico se
     * ignora sin más; su peso está en cero justamente para eso.
     */
    private BigDecimal recalcularNotaDeLaEtapa(Long postulacionId, Vacante vacante, Puesto puesto) {
        /*
         * ⚠️ **Las preguntas propias a medias no dan nota, ni con el currículum solo ni con
         * un cero provisional** (V66, AC-09). Mientras falte la nota de alguna abierta con
         * puntos, la del banco no existe, y sin ella la del Perfil Integral tampoco: se quita
         * la que hubiera —una criba del currículum a solas, de antes de entregar— y no se
         * escribe nada. En cuanto llega la última (la IA, o una persona a mano), se calcula
         * como siempre.
         */
        ServicioCalificacion.NotaDelBanco banco = calificacion.notaDelBancoPorPuntos(postulacionId);
        if (banco != null && !banco.completa()) {
            notasEtapa.findByPostulacionIdAndEtapaCodigo(postulacionId, ETAPA)
                    .ifPresent(notasEtapa::delete);
            log.info("La postulación {} no tiene nota de Perfil Integral: a sus preguntas "
                    + "propias les falta la nota de alguna abierta", postulacionId);
            return null;
        }
        Map<String, BigDecimal> pesos = pesosComponente
                .findByVersionPesosId(vacante.getVersionPesosId()).stream()
                .collect(Collectors.toMap(PesoComponentePerfil::getComponente,
                        PesoComponentePerfil::getPeso, (a, b) -> a));

        BigDecimal notaCv = notaCurriculum(postulacionId, vacante, puesto);
        BigDecimal notaEvaluacion = banco != null ? banco.nota() : notaEvaluacion(postulacionId);

        BigDecimal suma = BigDecimal.ZERO;
        BigDecimal pesoTotal = BigDecimal.ZERO;
        Map<String, BigDecimal> partes = new HashMap<>();
        partes.put("CV", notaCv);
        partes.put("EVALUACION", notaEvaluacion);
        for (Map.Entry<String, BigDecimal> parte : partes.entrySet()) {
            BigDecimal peso = pesos.getOrDefault(parte.getKey(), BigDecimal.ZERO);
            if (parte.getValue() == null || peso.compareTo(BigDecimal.ZERO) == 0) continue;
            suma = suma.add(parte.getValue().multiply(peso));
            pesoTotal = pesoTotal.add(peso);
        }

        NotaEtapa fila = notasEtapa.findByPostulacionIdAndEtapaCodigo(postulacionId, ETAPA)
                .orElseGet(() -> NotaEtapa.builder()
                        .postulacionId(postulacionId)
                        .etapaCodigo(ETAPA)
                        .creadoEn(Instant.now())
                        .build());
        /*
         * ⚠️ **Sin ningún componente con peso NO hay nota que calcular, y aquí no se toca
         * nada de nada.**
         *
         * Antes se guardaba `BigDecimal.ZERO` cuando la cuenta no salía. Un cero se lee como
         * «lo hizo malísimo» y ordena como tal; lo que de verdad pasa es que no hay con qué
         * calcular —una vacante con el banco apagado y todo el peso en la evaluación, por
         * ejemplo—. El 08/09/2026 eso mandó al fondo del ranking a once candidatos con
         * currículums de entre 50 y 86: el ranking ordena primero por grupo de prioridad, y
         * el cero los dejaba a todos en NO_PRIORIZADO, por debajo de gente con notas de 16.
         *
         * <p>Se sale ANTES de escribir, y eso cubre los dos casos de golpe:
         *
         * <ul>
         *   <li><b>Sin nota previa</b>, la fila no se crea. No se escribe un nulo porque
         *       {@code puntaje} es NOT NULL desde la V12; la ausencia de fila es la forma que
         *       el resto del sistema ya sabe leer —el ranking y el Excel pintan un hueco— y
         *       la misma que usa la ruta hermana ({@code CalificacionPorCriterio}: sin pilar
         *       no se escribe).
         *   <li><b>Con nota previa</b>, se conserva intacta y sin volver a firmarla. Esto es
         *       lo que se escapaba: la nota vieja se guardaba otra vez con la versión de
         *       pesos NUEVA y la fecha de hoy, o sea que quedaba escrito que se calculó con
         *       una versión que no le da peso a nada. Dos candidatos idénticos ordenaban al
         *       revés —el recién llegado sin nota al final, el antiguo con su 72 intacto— y
         *       la versión de pesos dejaba de servir para reconstruir la decisión.
         * </ul>
         */
        if (pesoTotal.compareTo(BigDecimal.ZERO) == 0) {
            log.info("La postulación {} se queda con la nota de perfil integral que tuviera "
                    + "({}): la versión de pesos {} no le da peso a ningún componente que se "
                    + "pueda llenar, así que no hay nada que recalcular",
                    postulacionId, fila.getPuntaje(), vacante.getVersionPesosId());
            return fila.getPuntaje();
        }

        BigDecimal nota = suma.divide(pesoTotal, 2, RoundingMode.HALF_UP);
        fila.setPuntaje(nota);
        fila.setVersionPesosId(vacante.getVersionPesosId());
        fila.setCalculadaEn(Instant.now());
        notasEtapa.save(fila);
        return nota;
    }

    /**
     * La nota de la evaluación entera, sobre 100: lo cerrado y lo abierto juntos.
     *
     * <p>Se ponderan por cuántas preguntas produjo cada mitad. Es lo más parecido a haberlas
     * puntuado todas de una vez, y evita que tres preguntas abiertas pesen tanto como veinte
     * cerradas. <b>Ningún documento del cliente dice cómo combinar las dos mitades</b>: esto
     * es una interpretación nuestra y está anotada como pregunta pendiente.
     */
    private BigDecimal notaEvaluacion(Long postulacionId) {
        ServicioCalificacion.ResumenCerrado cerrado = calificacion.resumenDeLoCerrado(postulacionId);
        List<NotaRespuestaIa> abiertas = notasDeLoAbierto(abiertas(postulacion(postulacionId).getEvaluacionId()));
        // La cuenta vive en ServicioCalificacion.notaCombinada: si la interpretacion de
        // como mezclar las mitades cambia, cambia a la vez aqui y en el desglose del panel.
        return ServicioCalificacion.notaCombinada(cerrado,
                abiertas.stream().map(NotaRespuestaIa::puntaje).toList());
    }

    /** El 0-4 de las abiertas llevado a 0-100, para poder mezclarlo con el resto. */
    private BigDecimal promedioAbiertas(List<NotaRespuestaIa> notas) {
        if (notas.isEmpty()) return null;
        BigDecimal suma = notas.stream().map(NotaRespuestaIa::puntaje).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.multiply(CIEN)
                .divide(CUATRO.multiply(BigDecimal.valueOf(notas.size())), 2, RoundingMode.HALF_UP);
    }

    /**
     * En cuál de los cuatro grupos cae (RF-68).
     *
     * <p>Los umbrales son parámetros editables, no números en el código: los sembró la
     * migración V14 a partir de las bandas del Banco Maestro y <b>Renaser todavía no los ha
     * confirmado</b>.
     *
     * <p>La regla: llega a alta prioridad quien saca la nota y no arrastra ningún riesgo
     * crítico. Quien saca la nota pero sí lo arrastra, o quien se queda corto en nota pero
     * tiene potencial alto, es «alto potencial con riesgo» — y eso no es un descarte, es un
     * aviso de qué conversar. {@code INCOMPATIBLE} no lo pone nunca la IA: sale de los
     * requisitos objetivos, que se comprueban al postular.
     */
    private String grupoDe(Long organizacionId, BigDecimal nota, PerfilTalento perfil,
                           ResultadoPerfil resultado) {
        /*
         * ⚠️ **Sin nota no hay grupo**, y el hueco se deja a la vista.
         *
         * Los tres grupos son cortes sobre la nota: sin ella, cualquiera de los tres sería
         * inventado — y el peor sería NO_PRIORIZADO, que es justo donde caería un candidato
         * al que no se ha podido puntuar. El ranking ya sabe pintar a quien no tiene grupo:
         * lo manda al final con su estado escrito, en vez de mezclarlo con los descartados.
         */
        if (nota == null) {
            return null;
        }
        boolean riesgoCritico = lista(resultado.hallazgos()).stream()
                .anyMatch(h -> "RIESGO_CRITICO".equals(h.tipo()));
        return grupoDe(organizacionId, nota, perfil.getPotencial(), riesgoCritico);
    }

    /**
     * La misma regla, con el riesgo y el potencial ya leídos. La usa el recálculo sin IA
     * (ajuste a mano, cambio de puntos, recalificación), que no tiene el resultado del modelo
     * en la mano y lee los hallazgos guardados.
     */
    private String grupoDe(Long organizacionId, BigDecimal nota, BigDecimal potencialLeido,
                           boolean riesgoCritico) {
        if (nota == null) {
            return null;
        }
        int alta = parametros.entero(organizacionId, "umbral_grupo_alta", 80);
        int priorizado = parametros.entero(organizacionId, "umbral_grupo_priorizado", 65);
        BigDecimal potencial = potencialLeido == null ? BigDecimal.ZERO : potencialLeido;

        if (nota.compareTo(BigDecimal.valueOf(alta)) >= 0 && !riesgoCritico) {
            return "ALTA";
        }
        if (nota.compareTo(BigDecimal.valueOf(priorizado)) >= 0
                || potencial.compareTo(BigDecimal.valueOf(alta)) >= 0) {
            return "POTENCIAL_CON_RIESGO";
        }
        return "NO_PRIORIZADO";
    }

    // ==================== Apoyo ====================

    private List<Criterio> criteriosDelCurriculum() {
        return criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(ETAPA);
    }

    private List<CriterioConPeso> criteriosConPeso(Vacante vacante, Puesto puesto) {
        Map<Long, BigDecimal> pesos = pesosCriterio
                .findByVersionPesosIdAndNivelPuestoCodigo(
                        vacante.getVersionPesosId(), puesto.getNivelPuestoCodigo()).stream()
                .collect(Collectors.toMap(PesoCriterio::getCriterioId, PesoCriterio::getPeso,
                        (a, b) -> a));
        List<CriterioConPeso> salida = criteriosDelCurriculum().stream()
                .map(c -> new CriterioConPeso(c.getCodigo(), c.getNombre(), c.getDescripcion(),
                        pesos.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .toList();
        if (salida.isEmpty()) {
            throw new IllegalStateException(
                    "No hay criterios de currículum configurados: no se puede puntuar nada");
        }
        return salida;
    }

    private List<NotaCriterioIa> notasDelCurriculum(Long postulacionId) {
        Map<Long, String> codigos = criteriosDelCurriculum().stream()
                .collect(Collectors.toMap(Criterio::getId, Criterio::getCodigo));
        return notasCriterio.findByPostulacionId(postulacionId).stream()
                .filter(n -> codigos.containsKey(n.getCriterioId()))
                .map(n -> new NotaCriterioIa(codigos.get(n.getCriterioId()), n.getPuntaje(),
                        n.getExplicacion(), null))
                .toList();
    }

    private List<NotaRespuestaIa> notasDeLoAbierto(List<RespuestaAbierta> abiertas) {
        if (abiertas.isEmpty()) return List.of();
        return notasRespuesta.findByRespuestaIdIn(
                        abiertas.stream().map(RespuestaAbierta::respuestaId).toList()).stream()
                .map(n -> new NotaRespuestaIa(n.getRespuestaId(), n.getPuntaje(), n.getExplicacion(),
                        n.getEvidenciaCitada(), n.getConfianza(), n.getCumpleSenalCero(),
                        n.getC1Episodio(), n.getC2Autoria(), n.getC3Dato(), n.getC4Incomodidad()))
                .toList();
    }

    private Postulacion postulacion(Long id) {
        return postulaciones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", id));
    }

    private Vacante vacante(Postulacion postulacion) {
        Vacante vacante = vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException("La vacante de esta postulación ya no existe"));
        if (vacante.getVersionPesosId() == null) {
            throw new IllegalStateException(
                    "La vacante no tiene versión de pesos: sin ella la nota no se puede atar a nada");
        }
        return vacante;
    }

    private Puesto puesto(Vacante vacante) {
        return puestos.findById(vacante.getPuestoId())
                .orElseThrow(() -> new IllegalStateException("La vacante apunta a un puesto que no existe"));
    }

    private String queBusca(Vacante vacante) {
        return String.join("\n", List.of(
                        texto(vacante.getTitulo()), texto(vacante.getProposito()),
                        texto(vacante.getResponsabilidades()), texto(vacante.getRequisitos())))
                .trim();
    }

    private String conEvidencia(String explicacion, String evidencia) {
        return esVacio(evidencia) ? explicacion : explicacion + "\nEvidencia: " + evidencia;
    }

    /** Un modelo puede devolver 120 sobre 100 o un negativo. Se acota en vez de fallar. */
    private BigDecimal acotar(BigDecimal valor, BigDecimal maximo) {
        if (valor == null) return null;
        if (valor.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        return valor.compareTo(maximo) > 0 ? maximo : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> List<T> lista(List<T> valor) {
        return valor == null ? List.of() : valor;
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
