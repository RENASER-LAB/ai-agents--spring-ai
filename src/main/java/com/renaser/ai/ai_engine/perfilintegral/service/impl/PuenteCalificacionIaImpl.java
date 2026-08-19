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
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
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
    private final ServicioParametros parametros;
    private final MaquinaEstados maquina;

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
        return new InsumoRespuestas(puesto.getNombre(), puesto.getNivelPuestoCodigo(),
                abiertas(postulacion));
    }

    /**
     * Las abiertas son las que tienen texto y puntúan.
     *
     * <p>Las cerradas quedan fuera porque ya las puntuó el código contra la clave, y el
     * modelo generativo no puede tocarlas (RF-147). Las de estilo y consistencia tampoco
     * están: no suman nota por diseño.
     */
    private List<RespuestaAbierta> abiertas(Postulacion postulacion) {
        if (postulacion.getEvaluacionId() == null) {
            return List.of();
        }
        List<Respuesta> suyas = respuestas.findByEvaluacionId(postulacion.getEvaluacionId()).stream()
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
                    p.getSituacion(), dimensiones.getOrDefault(p.getId(), List.of()), r.getTexto()));
        }
        return salida;
    }

    @Override
    @Transactional
    public void guardarNotasAbiertas(Long postulacionId, Long ejecucionIaId,
                                     ResultadoEvaluador resultado) {
        Postulacion postulacion = postulacion(postulacionId);
        List<Long> mias = abiertas(postulacion).stream().map(RespuestaAbierta::respuestaId).toList();

        int guardadas = 0;
        for (NotaRespuestaIa nota : lista(resultado == null ? null : resultado.notas())) {
            if (nota.respuestaId() == null || !mias.contains(nota.respuestaId())) {
                log.warn("El agente devolvió una nota para una respuesta que no es de esta "
                        + "postulación: {}", nota.respuestaId());
                continue;
            }
            // La base exige explicación y el documento exige evidencia citada (RF-56).
            if (nota.puntaje() == null || esVacio(nota.explicacion())) {
                log.warn("Nota de la respuesta {} descartada: sin puntaje o sin explicación",
                        nota.respuestaId());
                continue;
            }

            NotaRespuesta fila = notasRespuesta.findByRespuestaId(nota.respuestaId())
                    .orElseGet(() -> NotaRespuesta.builder()
                            .respuestaId(nota.respuestaId())
                            .creadoEn(Instant.now())
                            .build());
            if (fila.getAjustadaPorUsuarioId() != null) {
                continue;
            }
            fila.setPuntaje(acotar(nota.puntaje(), CUATRO));
            fila.setExplicacion(nota.explicacion());
            fila.setEvidenciaCitada(nota.evidenciaCitada());
            fila.setConfianza(acotar(nota.confianza(), CIEN));
            fila.setEjecucionIaId(ejecucionIaId);
            notasRespuesta.save(fila);
            guardadas++;
        }
        log.info("EVALUADOR: {} de {} respuestas abiertas calificadas en la postulación {}",
                guardadas, mias.size(), postulacionId);
    }

    // ==================== POTENCIAL_RIESGO ====================

    @Override
    @Transactional
    public InsumoPerfil insumoPerfil(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        Vacante vacante = vacante(postulacion);
        Puesto puesto = puesto(vacante);

        ServicioCalificacion.ResumenCerrado cerrado = calificacion.resumenDeLoCerrado(postulacionId);
        List<RespuestaAbierta> abiertas = abiertas(postulacion);
        List<NotaRespuestaIa> notasAbiertas = notasDeLoAbierto(abiertas);

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
        maquina.transicionar(postulacion, "PERFIL_POR_CONFIRMAR", null, null, true, false, null);

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
     */
    private BigDecimal notaCurriculum(Long postulacionId, Vacante vacante, Puesto puesto) {
        Map<Long, BigDecimal> pesos = pesosCriterio
                .findByVersionPesosIdAndNivelPuestoCodigo(
                        vacante.getVersionPesosId(), puesto.getNivelPuestoCodigo()).stream()
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
        Map<String, BigDecimal> pesos = pesosComponente
                .findByVersionPesosId(vacante.getVersionPesosId()).stream()
                .collect(Collectors.toMap(PesoComponentePerfil::getComponente,
                        PesoComponentePerfil::getPeso, (a, b) -> a));

        BigDecimal notaCv = notaCurriculum(postulacionId, vacante, puesto);
        BigDecimal notaEvaluacion = notaEvaluacion(postulacionId);

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
        BigDecimal nota = pesoTotal.compareTo(BigDecimal.ZERO) == 0
                ? fila.getPuntaje()
                : suma.divide(pesoTotal, 2, RoundingMode.HALF_UP);
        if (nota == null) {
            nota = BigDecimal.ZERO;
        }
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
        List<NotaRespuestaIa> abiertas = notasDeLoAbierto(abiertas(postulacion(postulacionId)));
        BigDecimal notaAbiertas = promedioAbiertas(abiertas);

        BigDecimal suma = BigDecimal.ZERO;
        int total = 0;
        if (cerrado.preguntas() > 0) {
            suma = suma.add(cerrado.nota().multiply(BigDecimal.valueOf(cerrado.preguntas())));
            total += cerrado.preguntas();
        }
        if (notaAbiertas != null) {
            suma = suma.add(notaAbiertas.multiply(BigDecimal.valueOf(abiertas.size())));
            total += abiertas.size();
        }
        return total == 0 ? null : suma.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
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
        int alta = parametros.entero(organizacionId, "umbral_grupo_alta", 80);
        int priorizado = parametros.entero(organizacionId, "umbral_grupo_priorizado", 65);

        boolean riesgoCritico = lista(resultado.hallazgos()).stream()
                .anyMatch(h -> "RIESGO_CRITICO".equals(h.tipo()));
        BigDecimal potencial = perfil.getPotencial() == null ? BigDecimal.ZERO : perfil.getPotencial();

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
                        n.getEvidenciaCitada(), n.getConfianza()))
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
