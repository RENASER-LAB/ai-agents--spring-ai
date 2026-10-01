package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AgregarDeLaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AjustarNota;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambiarPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CorregirInstrucciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CriterioDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarCriterio;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarDatosDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarPregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.OpcionDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PreguntaDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PreguntaElegida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDeOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDePregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Recalificacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ResumenDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.TextoDe;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VacanteCopiable;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PropuestaPreguntas;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.PreguntasInvalidasException;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.OpcionAValidar;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.PreguntaAValidar;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPreguntasVacante;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ver {@link ServicioPreguntasVacante}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioPreguntasVacanteImpl implements ServicioPreguntasVacante {

    static final String BORRADOR = "BORRADOR";
    static final String PUBLICADA = "PUBLICADA";
    static final String PROPOSITO = "PERFIL_INTEGRAL";

    private static final String PERMISO_VER = "ver_vacantes";
    private static final String PERMISO_EDITAR = "editar_vacante";
    private static final String PERMISO_AJUSTAR = "ajustar_nota";

    /** «Una vacante, una versión»: el mismo porqué en todos los 409 de la vara. */
    private static final String VARA_QUIETA = "Esta vacante ya tiene postulantes y sus "
            + "preguntas no se cambian: todos sus candidatos se miden con la misma vara. Solo "
            + "se pueden cambiar los puntos y las instrucciones de la IA, y cualquiera de los "
            + "dos vuelve a calcular la nota de todos.";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AlcanceSobreLaVacante alcance;
    private final Permisos permisos;
    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;
    private final PostulacionRepository postulaciones;
    private final EvaluacionRepository evaluaciones;
    private final RespuestaRepository respuestas;
    private final NotaRespuestaRepository notasRespuesta;
    private final VersionBancoRepository versionesBanco;
    private final CriterioBancoRepository criteriosBanco;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final PropuestaPreguntasRepository propuestas;
    private final CalificacionPorPuntos porPuntos;
    private final PuenteCalificacionIa puente;
    private final ColaCalificacionIa cola;
    private final ServicioAuditoria auditoria;

    // ============================== Leer ==============================

    @Override
    @Transactional(readOnly = true)
    public EditorDePreguntas ver(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laVisible(quien, vacanteId);
        return editor(quien, vacante);
    }

    private EditorDePreguntas editor(ContextoUsuario quien, Vacante vacante) {
        VersionBanco borrador = versionesBanco.preguntasPropiasDe(vacante.getId(), BORRADOR)
                .orElse(null);
        VersionBanco publicada = versionesBanco.preguntasPropiasDe(vacante.getId(), PUBLICADA)
                .orElse(null);
        return new EditorDePreguntas(vacante.getId(), vacante.getTitulo(), nivelDe(vacante),
                vacante.getOrigenPreguntas(), vacante.isAplicaEvaluacion(),
                puedeEditar(quien, vacante), hayPostulantes(vacante),
                borrador == null ? null : comoVersion(borrador),
                publicada == null ? null : comoVersion(publicada),
                resumen(borrador, publicada),
                publicada == null ? null : recalificacionDe(vacante, publicada));
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenDePreguntas resumenDe(Long vacanteId) {
        return resumen(versionesBanco.preguntasPropiasDe(vacanteId, BORRADOR).orElse(null),
                versionesBanco.preguntasPropiasDe(vacanteId, PUBLICADA).orElse(null));
    }

    /**
     * Lo que rinde quien postula manda sobre el taller: con una versión publicada, eso es lo
     * que se enseña aunque haya un borrador abierto.
     */
    private ResumenDePreguntas resumen(VersionBanco borrador, VersionBanco publicada) {
        if (publicada != null) {
            CalificacionPorPuntos.Resultado r = porPuntos.calcular(publicada.getId(), null);
            return new ResumenDePreguntas("PUBLICADAS", totalDe(r), r.criterios().size(),
                    r.todas().size());
        }
        if (borrador != null) {
            CalificacionPorPuntos.Resultado r = porPuntos.calcular(borrador.getId(), null);
            return new ResumenDePreguntas("BORRADOR", totalDe(r), r.criterios().size(),
                    r.todas().size());
        }
        return new ResumenDePreguntas("SIN_PREGUNTAS", null, null, null);
    }

    // ============================== El borrador ==============================

    @Override
    @Transactional
    public EditorDePreguntas abrirBorrador(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        elBorrador(vacante);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas guardarDatosDelBorrador(ContextoUsuario quien, Long vacanteId,
                                                     GuardarDatosDelBorrador datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorrador(vacante);
        borrador.setGuiaCalificacion(textoONulo(datos.guiaCalificacion()));
        borrador.setMinutosObjetivo(datos.minutosObjetivo());
        versionesBanco.save(borrador);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas descartarBorrador(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        versionesBanco.preguntasPropiasDe(vacante.getId(), BORRADOR).ifPresent(this::vaciarYBorrar);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas agregarCriterio(ContextoUsuario quien, Long vacanteId,
                                            GuardarCriterio datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorrador(vacante);
        nuevoCriterio(borrador, datos.nombre(), datos.queEvalua());
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas editarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                           GuardarCriterio datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        CriterioBanco criterio = elCriterio(elBorradorQueYaExiste(vacante), criterioId);
        criterio.setNombre(datos.nombre().strip());
        criterio.setQueEvalua(textoONulo(datos.queEvalua()));
        criteriosBanco.save(criterio);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas quitarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        CriterioBanco criterio = elCriterio(borrador, criterioId);
        // Sus preguntas no se borran: quedan «sin criterio» hasta que alguien las mueva, y
        // mientras tanto la versión no se publica. Borrarlas en cascada sería perder trabajo
        // por un clic en la X del bloque.
        List<Pregunta> suyas = preguntas.findByVersionBancoIdOrderByOrden(borrador.getId()).stream()
                .filter(p -> criterioId.equals(p.getCriterioBancoId()))
                .toList();
        int siguiente = siguienteOrden(borrador.getId(), null);
        for (Pregunta p : suyas) {
            p.setCriterioBancoId(null);
            p.setOrden(siguiente++);
        }
        preguntas.saveAllAndFlush(suyas);
        criteriosBanco.delete(criterio);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas moverCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                          Mover datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        List<CriterioBanco> todos = new ArrayList<>(
                criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()));
        int i = indiceDe(todos, criterioId, CriterioBanco::getId, "Criterio");
        int j = i + paso(datos);
        if (j >= 0 && j < todos.size()) {
            renumerar(todos, i, j, CriterioBanco::setOrden);
            criteriosBanco.saveAll(todos);
        }
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas agregarPregunta(ContextoUsuario quien, Long vacanteId,
                                            GuardarPregunta datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorrador(vacante);
        exigirForma("La pregunta", datos);

        Long criterioId = datos.criterioId();
        if (criterioId != null) {
            elCriterio(borrador, criterioId);
        } else if (criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).isEmpty()) {
            // Quien no quiere pensar en criterios no está obligado: la primera pregunta sin
            // criterio crea uno «General», que después se puede renombrar (AC-03).
            criterioId = nuevoCriterio(borrador, "General", null).getId();
        }
        nuevaPregunta(borrador, datos.tipo(), datos.enunciado(), entero(datos.puntos()),
                criterioId, datos.queDebeTener(), comoOpciones(datos.opciones()));
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas editarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                           GuardarPregunta datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        Pregunta pregunta = laPregunta(borrador, preguntaId);
        exigirForma("La pregunta", datos);
        if (datos.criterioId() != null) {
            elCriterio(borrador, datos.criterioId());
        }

        // Cambiar de criterio la pone al final del nuevo, que es donde se busca al moverla.
        if (!Objects.equals(pregunta.getCriterioBancoId(), datos.criterioId())) {
            pregunta.setCriterioBancoId(datos.criterioId());
            pregunta.setOrden(siguienteOrden(borrador.getId(), datos.criterioId()));
        }
        int puntos = entero(datos.puntos());
        pregunta.setTipo(datos.tipo());
        pregunta.setEnunciado(datos.enunciado().strip());
        pregunta.setPuntos(puntos);
        pregunta.setEsPuntuable(puntos > 0);
        pregunta.setQueDebeTener(ReglasDePuntos.ABIERTA.equals(datos.tipo())
                ? textoONulo(datos.queDebeTener()) : null);
        preguntas.save(pregunta);

        // Las opciones se rehacen enteras: en un borrador ninguna respuesta apunta a ellas.
        opciones.deleteByPreguntaIdIn(List.of(pregunta.getId()));
        opciones.flush();
        guardarOpciones(pregunta, comoOpciones(datos.opciones()));
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas quitarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId) {
        Vacante vacante = laEditable(quien, vacanteId);
        Pregunta pregunta = laPregunta(elBorradorQueYaExiste(vacante), preguntaId);
        opciones.deleteByPreguntaIdIn(List.of(pregunta.getId()));
        preguntas.delete(pregunta);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas moverPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                          Mover datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        Pregunta pregunta = laPregunta(borrador, preguntaId);
        // Se mueve dentro de su criterio: pasarla a otro es el selector de la pregunta.
        List<Pregunta> hermanas = new ArrayList<>(preguntas
                .findByVersionBancoIdOrderByOrden(borrador.getId()).stream()
                .filter(p -> Objects.equals(p.getCriterioBancoId(), pregunta.getCriterioBancoId()))
                .sorted(Comparator.comparing((Pregunta p) -> p.getOrden() == null
                        ? Integer.MAX_VALUE : p.getOrden()).thenComparing(Pregunta::getId))
                .toList());
        int i = indiceDe(hermanas, preguntaId, Pregunta::getId, "Pregunta");
        int j = i + paso(datos);
        if (j >= 0 && j < hermanas.size()) {
            renumerar(hermanas, i, j, Pregunta::setOrden);
            preguntas.saveAll(hermanas);
        }
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas publicar(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = versionesBanco.preguntasPropiasDe(vacante.getId(), BORRADOR)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay borrador que publicar: escribe las preguntas primero"));

        List<String> faltas = faltasParaPublicar(porPuntos.calcular(borrador.getId(), null));
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("Las preguntas no se pueden publicar todavía: "
                    + (faltas.size() == 1 ? "falta una cosa" : "faltan " + faltas.size() + " cosas"),
                    faltas);
        }

        // Publicar reemplaza a la anterior, que se archiva (nada se borra, RF-138). Desde la
        // primera postulación, no: la vara no se mueve.
        Optional<VersionBanco> anterior = versionesBanco.preguntasPropiasDe(vacante.getId(), PUBLICADA);
        if (anterior.isPresent()) {
            if (hayPostulantes(vacante)) {
                throw new IllegalStateException(VARA_QUIETA);
            }
            VersionBanco saliente = anterior.get();
            saliente.setEstado("ARCHIVADA");
            // saveAndFlush: el índice «una publicada por vacante» no perdona que el borrador
            // pase a PUBLICADA antes de que esta se archive (Hibernate inserta y actualiza
            // en su propio orden).
            versionesBanco.saveAndFlush(saliente);
        }
        borrador.setEstado(PUBLICADA);
        borrador.setPublicadaPorUsuarioId(quien.usuarioId());
        borrador.setPublicadaEn(Instant.now());
        versionesBanco.save(borrador);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_preguntas_propias",
                "version_banco", borrador.getId(), Map.of("estado", BORRADOR),
                Map.of("estado", PUBLICADA, "vacante", vacante.getId()), null);
        return editor(quien, vacante);
    }

    // ============================== Con la versión publicada ==============================

    @Override
    @Transactional
    public CambioAplicado corregirInstrucciones(ContextoUsuario quien, Long vacanteId,
                                                CorregirInstrucciones datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco publicada = laPublicada(vacante);
        Map<Long, List<NotaRespuesta>> conNota = notasDeLaIaPorPostulacion(vacante, publicada);
        exigirQueNoHayaRecalificacionEnCurso(conNota.keySet());

        // Sin saldo o con la IA apagada NO se guarda nada: guardarlo sin recalificar dejaría
        // a unos medidos con una guía y a otros con otra.
        if (!conNota.isEmpty()) {
            String motivo = cola.porQueNoSePuedeUsarLaIa(vacante.getOrganizacionId());
            if (motivo != null) {
                throw new IllegalStateException(motivo + " No se guardó el cambio: guardarlo "
                        + "sin recalificar dejaría a unos candidatos medidos con una guía y a "
                        + "otros con otra.");
            }
        }

        Map<String, Object> antes = new LinkedHashMap<>();
        Map<String, Object> despues = new LinkedHashMap<>();
        if (datos.guiaCalificacion() != null) {
            String nueva = textoONulo(datos.guiaCalificacion());
            if (!Objects.equals(nueva, publicada.getGuiaCalificacion())) {
                antes.put("guiaCalificacion", String.valueOf(publicada.getGuiaCalificacion()));
                despues.put("guiaCalificacion", String.valueOf(nueva));
                publicada.setGuiaCalificacion(nueva);
            }
        }
        Map<Long, CriterioBanco> criterios = criteriosBanco
                .findByVersionBancoIdOrderByOrdenAscIdAsc(publicada.getId()).stream()
                .collect(Collectors.toMap(CriterioBanco::getId, Function.identity()));
        for (TextoDe t : lista(datos.criterios())) {
            CriterioBanco c = criterios.get(t.id());
            if (c == null) {
                throw new IllegalArgumentException("El criterio " + t.id()
                        + " no es de las preguntas publicadas de esta vacante");
            }
            String nuevo = textoONulo(t.texto());
            if (!Objects.equals(nuevo, c.getQueEvalua())) {
                antes.put("criterio " + c.getId() + " · qué evalúa", String.valueOf(c.getQueEvalua()));
                despues.put("criterio " + c.getId() + " · qué evalúa", String.valueOf(nuevo));
                c.setQueEvalua(nuevo);
                criteriosBanco.save(c);
            }
        }
        Map<Long, Pregunta> suyas = preguntas.findByVersionBancoIdOrderByOrden(publicada.getId())
                .stream().collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        for (TextoDe t : lista(datos.preguntas())) {
            Pregunta p = suyas.get(t.id());
            if (p == null || !ReglasDePuntos.ABIERTA.equals(p.getTipo())) {
                throw new IllegalArgumentException("La pregunta " + t.id()
                        + " no es una abierta de las preguntas publicadas de esta vacante");
            }
            String nuevo = textoONulo(t.texto());
            if (!Objects.equals(nuevo, p.getQueDebeTener())) {
                antes.put("pregunta " + p.getId() + " · qué debe tener", String.valueOf(p.getQueDebeTener()));
                despues.put("pregunta " + p.getId() + " · qué debe tener", String.valueOf(nuevo));
                p.setQueDebeTener(nuevo);
                preguntas.save(p);
            }
        }
        if (antes.isEmpty()) {
            return new CambioAplicado(0);
        }

        // Cada guía tiene su número; cada nota guarda con cuál se calculó. Un resultado que
        // llegue calculado con la anterior se descarta y se vuelve a pedir.
        int anterior = publicada.getVersionGuia() == null ? 1 : publicada.getVersionGuia();
        publicada.setVersionGuia(anterior + 1);
        versionesBanco.save(publicada);
        antes.put("versionGuia", anterior);
        despues.put("versionGuia", anterior + 1);
        auditoria.registrar(quien.organizacionId(), quien, "corregir_instrucciones_ia",
                "version_banco", publicada.getId(), antes, despues, null);

        int encoladas = 0;
        for (Long postulacionId : conNota.keySet()) {
            if (cola.recalificar(postulacionId)) {
                encoladas++;
            }
        }
        log.info("Instrucciones de la IA corregidas en la vacante {} (guía {}): {} personas a "
                + "recalificar, {} encoladas", vacante.getId(), anterior + 1, conNota.size(), encoladas);
        return new CambioAplicado(conNota.size());
    }

    @Override
    @Transactional
    public CambioAplicado reintentarRecalificacion(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco publicada = laPublicada(vacante);
        int vigente = publicada.getVersionGuia() == null ? 1 : publicada.getVersionGuia();
        Map<Long, List<NotaRespuesta>> conNota = notasDeLaIaPorPostulacion(vacante, publicada);
        Map<Long, ColaCalificacionIa.Seguimiento> seguimiento =
                cola.recalificacionDe(List.copyOf(conNota.keySet()));
        List<Long> pendientes = new ArrayList<>();
        for (Map.Entry<Long, List<NotaRespuesta>> e : conNota.entrySet()) {
            boolean viejas = e.getValue().stream().anyMatch(n -> !Objects.equals(n.getVersionGuia(), vigente));
            ColaCalificacionIa.Seguimiento s = seguimiento.get(e.getKey());
            boolean enCurso = s != null && "EN_CURSO".equals(s.estado());
            if (viejas && !enCurso) {
                pendientes.add(e.getKey());
            }
        }
        if (pendientes.isEmpty()) {
            return new CambioAplicado(0);
        }
        // Con la IA apagada, la empresa suspendida o su tope del mes agotado no se encola a
        // nadie, y se dice por qué: contestar «0 personas» a secas hacía creer que ya no
        // quedaba nadie pendiente (punto 18 de la spec).
        String motivo = cola.porQueNoSePuedeUsarLaIa(vacante.getOrganizacionId());
        if (motivo != null) {
            return new CambioAplicado(0, motivo + " No se volvió a pedir la recalificación: "
                    + personas(pendientes.size(), "sigue", "siguen")
                    + " con la nota de la guía anterior.");
        }
        int encoladas = 0;
        for (Long postulacionId : pendientes) {
            if (cola.recalificar(postulacionId)) {
                encoladas++;
            }
        }
        int sinEncolar = pendientes.size() - encoladas;
        return new CambioAplicado(encoladas, sinEncolar == 0 ? null
                : personas(sinEncolar, "ya tenía", "ya tenían")
                        + " una recalificación en marcha: no se pidió otra.");
    }

    /** «1 persona sigue», «3 personas siguen». */
    private static String personas(int cuantas, String verboUna, String verboVarias) {
        return cuantas == 1 ? "1 persona " + verboUna : cuantas + " personas " + verboVarias;
    }

    @Override
    @Transactional
    public CambioAplicado cambiarPuntos(ContextoUsuario quien, Long vacanteId, CambiarPuntos datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco publicada = laPublicada(vacante);
        exigirQueNoHayaRecalificacionEnCurso(notasDeLaIaPorPostulacion(vacante, publicada).keySet());

        List<Pregunta> suyas = preguntas.findByVersionBancoIdOrderByOrden(publicada.getId());
        Map<Long, Pregunta> porId = suyas.stream()
                .collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        Map<Long, List<Opcion>> opcionesDe = opciones.findByPreguntaIdIn(
                        suyas.stream().map(Pregunta::getId).toList()).stream()
                .collect(Collectors.groupingBy(Opcion::getPreguntaId));

        // Lo nuevo, puesto sobre lo que hay: lo que no viene conserva sus puntos.
        Map<Long, BigDecimal> puntosNuevos = new HashMap<>();
        Map<Long, BigDecimal> opcionesNuevas = new HashMap<>();
        for (PuntosDePregunta p : datos.preguntas()) {
            Pregunta pregunta = porId.get(p.id());
            if (pregunta == null) {
                throw new IllegalArgumentException("La pregunta " + p.id()
                        + " no es de las preguntas publicadas de esta vacante");
            }
            puntosNuevos.put(p.id(), p.puntos());
            Set<Long> suyasOpciones = opcionesDe.getOrDefault(p.id(), List.of()).stream()
                    .map(Opcion::getId).collect(Collectors.toSet());
            for (PuntosDeOpcion o : lista(p.opciones())) {
                if (!suyasOpciones.contains(o.id())) {
                    throw new IllegalArgumentException("La opción " + o.id()
                            + " no es de la pregunta " + p.id());
                }
                opcionesNuevas.put(o.id(), o.puntos());
            }
        }

        // Todo se valida antes de tocar nada: o cambia entero, o no cambia (AC-32).
        List<String> faltas = new ArrayList<>();
        int suma = 0;
        int posicion = 1;
        for (Pregunta p : enOrdenDePresentacion(publicada, suyas)) {
            BigDecimal puntos = puntosNuevos.getOrDefault(p.getId(), BigDecimal.valueOf(puntosDe(p)));
            List<OpcionAValidar> suyasOpciones = opcionesDe.getOrDefault(p.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(o -> o.getOrden() == null ? Integer.MAX_VALUE : o.getOrden()))
                    .map(o -> new OpcionAValidar(ReglasDePuntos.ESCALA.equals(p.getTipo())
                            ? null : o.getTexto(), opcionesNuevas.getOrDefault(o.getId(), o.getPuntaje())))
                    .toList();
            PreguntaAValidar aValidar = new PreguntaAValidar(p.getTipo(), p.getEnunciado(), puntos,
                    null, suyasOpciones);
            String donde = ReglasDePuntos.nombreDe(posicion++, p.getEnunciado());
            faltas.addAll(ReglasDePuntos.formaDeLaPregunta(donde, aValidar));
            faltas.addAll(ReglasDePuntos.puntuacionDeLaPregunta(donde, aValidar));
            if (ReglasDePuntos.esEntero(puntos)) {
                suma += puntos.intValue();
            }
        }
        String total = ReglasDePuntos.faltaDelTotal(suma);
        if (total != null) {
            faltas.add(0, total);
        }
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("Los puntos no se cambiaron: "
                    + (faltas.size() == 1 ? "falta una cosa" : "faltan " + faltas.size() + " cosas"),
                    faltas);
        }

        Map<String, Object> antes = new LinkedHashMap<>();
        Map<String, Object> despues = new LinkedHashMap<>();
        Map<Long, int[]> abiertasQueCambian = new HashMap<>();   // pregunta → {antes, después}
        for (Map.Entry<Long, BigDecimal> e : puntosNuevos.entrySet()) {
            Pregunta p = porId.get(e.getKey());
            int viejo = puntosDe(p);
            int nuevo = e.getValue().intValue();
            if (viejo == nuevo) {
                continue;
            }
            antes.put("pregunta " + p.getId(), viejo);
            despues.put("pregunta " + p.getId(), nuevo);
            p.setPuntos(nuevo);
            p.setEsPuntuable(nuevo > 0);
            preguntas.save(p);
            if (ReglasDePuntos.ABIERTA.equals(p.getTipo())) {
                abiertasQueCambian.put(p.getId(), new int[]{viejo, nuevo});
            }
        }
        List<Opcion> opcionesCambiadas = new ArrayList<>();
        opcionesDe.values().stream().flatMap(List::stream).forEach(o -> {
            BigDecimal nuevo = opcionesNuevas.get(o.getId());
            if (nuevo != null && (o.getPuntaje() == null || o.getPuntaje().compareTo(nuevo) != 0)) {
                antes.put("opción " + o.getId(), String.valueOf(o.getPuntaje()));
                despues.put("opción " + o.getId(), nuevo.stripTrailingZeros().toPlainString());
                o.setPuntaje(nuevo.setScale(0, RoundingMode.UNNECESSARY));
                opcionesCambiadas.add(o);
            }
        });
        opciones.saveAll(opcionesCambiadas);
        if (antes.isEmpty()) {
            return new CambioAplicado(0);
        }

        List<Postulacion> rendidas = postulacionesQueEntregaron(vacante, publicada);
        // Cada abierta ya calificada se escala en proporción a su máximo nuevo: la nota que
        // vale y la que puso la IA (12 de 20 pasa a 9 de 15). Un ajuste a mano se escala
        // igual y conserva su motivo. Las cerradas no se reescriben: se calculan al leer con
        // los puntos nuevos.
        reescalarAbiertas(rendidas, abiertasQueCambian);
        auditoria.registrar(quien.organizacionId(), quien, "cambiar_puntos_preguntas_propias",
                "version_banco", publicada.getId(), antes, despues, null);

        // En la misma transacción, sin IA y sin mover a nadie de etapa.
        for (Postulacion p : rendidas) {
            puente.recalcularSinMover(p.getId());
        }
        return new CambioAplicado(rendidas.size());
    }

    private void reescalarAbiertas(List<Postulacion> rendidas, Map<Long, int[]> abiertasQueCambian) {
        if (abiertasQueCambian.isEmpty() || rendidas.isEmpty()) {
            return;
        }
        List<Long> evaluacionIds = rendidas.stream().map(Postulacion::getEvaluacionId).toList();
        List<Respuesta> afectadas = respuestas.findByEvaluacionIdIn(evaluacionIds).stream()
                .filter(r -> abiertasQueCambian.containsKey(r.getPreguntaId()))
                .toList();
        if (afectadas.isEmpty()) {
            return;
        }
        Map<Long, Long> preguntaDeRespuesta = afectadas.stream()
                .collect(Collectors.toMap(Respuesta::getId, Respuesta::getPreguntaId));
        List<NotaRespuesta> notas = notasRespuesta.findByRespuestaIdIn(List.copyOf(preguntaDeRespuesta.keySet()));
        for (NotaRespuesta n : notas) {
            int[] cambio = abiertasQueCambian.get(preguntaDeRespuesta.get(n.getRespuestaId()));
            n.setPuntaje(escalar(n.getPuntaje(), cambio[0], cambio[1]));
            if (n.getPuntajeIa() != null) {
                n.setPuntajeIa(escalar(n.getPuntajeIa(), cambio[0], cambio[1]));
            }
        }
        notasRespuesta.saveAll(notas);
    }

    /** En proporción al máximo nuevo, con dos decimales; de un máximo 0 no hay proporción. */
    static BigDecimal escalar(BigDecimal nota, int maximoAnterior, int maximoNuevo) {
        if (nota == null) {
            return null;
        }
        if (maximoAnterior <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal escalada = nota.multiply(BigDecimal.valueOf(maximoNuevo))
                .divide(BigDecimal.valueOf(maximoAnterior), 2, RoundingMode.HALF_UP);
        return ReglasDePuntos.acotar(escalada, BigDecimal.valueOf(maximoNuevo));
    }

    // ============================== Copiar de otra vacante ==============================

    @Override
    @Transactional(readOnly = true)
    public List<VacanteCopiable> copiables(ContextoUsuario quien, Long vacanteId, String buscar,
                                           String nivel) {
        Vacante destino = laVisible(quien, vacanteId);
        List<VersionBanco> publicadas = versionesBanco.propiasPublicadasDe(quien.organizacionId())
                .stream().filter(v -> !destino.getId().equals(v.getVacanteId())).toList();
        if (publicadas.isEmpty()) {
            return List.of();
        }
        Map<Long, Vacante> vacantesPorId = vacantes.findAllById(publicadas.stream()
                        .map(VersionBanco::getVacanteId).collect(Collectors.toSet())).stream()
                // Las eliminadas no: se crearon por error. Las cerradas y archivadas sí: lo
                // habitual es repetir una contratación que ya terminó.
                .filter(v -> quien.organizacionId().equals(v.getOrganizacionId())
                        && v.getEliminadaEn() == null)
                .collect(Collectors.toMap(Vacante::getId, Function.identity()));
        Map<Long, String> nivelPorPuesto = puestos.findAllById(vacantesPorId.values().stream()
                        .map(Vacante::getPuestoId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().filter(p -> quien.organizacionId().equals(p.getOrganizacionId()))
                .collect(Collectors.toMap(Puesto::getId, Puesto::getNivelPuestoCodigo));
        Map<Long, Long> criteriosPorVersion = criteriosBanco.findByVersionBancoIdIn(
                        publicadas.stream().map(VersionBanco::getId).toList()).stream()
                .collect(Collectors.groupingBy(CriterioBanco::getVersionBancoId, Collectors.counting()));
        Map<Long, Long> preguntasPorVersion = preguntas.findByVersionBancoIdIn(
                        publicadas.stream().map(VersionBanco::getId).toList()).stream()
                .collect(Collectors.groupingBy(Pregunta::getVersionBancoId, Collectors.counting()));

        String buscado = sinTildes(buscar);
        return publicadas.stream()
                .filter(v -> vacantesPorId.containsKey(v.getVacanteId()))
                .map(v -> {
                    Vacante origen = vacantesPorId.get(v.getVacanteId());
                    return new VacanteCopiable(origen.getId(), origen.getTitulo(),
                            nivelPorPuesto.get(origen.getPuestoId()), origen.getPublicadaEn(),
                            estadoDe(origen),
                            criteriosPorVersion.getOrDefault(v.getId(), 0L).intValue(),
                            preguntasPorVersion.getOrDefault(v.getId(), 0L).intValue());
                })
                .filter(c -> buscado.isEmpty() || sinTildes(c.titulo()).contains(buscado))
                .filter(c -> nivel == null || nivel.isBlank() || nivel.equals(c.nivel()))
                // Las más recientes primero; las que nunca se publicaron, al final.
                .sorted(Comparator.comparing(VacanteCopiable::publicadaEn,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(VacanteCopiable::vacanteId, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VersionDePreguntas vistaPrevia(ContextoUsuario quien, Long vacanteId,
                                          Long vacanteOrigenId) {
        laVisible(quien, vacanteId);
        return comoVersion(laPublicadaDeOtraVacante(quien, vacanteOrigenId));
    }

    @Override
    @Transactional
    public EditorDePreguntas copiar(ContextoUsuario quien, Long vacanteId, CopiarDeOtraVacante datos) {
        Vacante destino = laEditable(quien, vacanteId);
        if (destino.getId().equals(datos.vacanteOrigenId())) {
            throw new IllegalArgumentException("Se copia de otra vacante, no de esta misma");
        }
        // La lectura va con el mismo corte por empresa que la vista previa: lo ajeno es 404
        // y no se crea nada (AC-15).
        VersionBanco origen = laPublicadaDeOtraVacante(quien, datos.vacanteOrigenId());
        if (versionesBanco.preguntasPropiasDe(destino.getId(), PUBLICADA).isPresent()
                && hayPostulantes(destino)) {
            throw new IllegalStateException(VARA_QUIETA);
        }
        // El borrador que hubiera se reemplaza: el panel ya pidió confirmarlo.
        versionesBanco.preguntasPropiasDe(destino.getId(), BORRADOR).ifPresent(this::vaciarYBorrar);
        VersionBanco borrador = nuevoBorrador(destino);
        copiarContenido(origen, borrador);
        auditoria.registrar(quien.organizacionId(), quien, "copiar_preguntas_propias",
                "version_banco", borrador.getId(), null,
                Map.of("desdeVacante", datos.vacanteOrigenId(), "desdeVersion", origen.getId()), null);
        return editor(quien, destino);
    }

    // ============================== Recomendaciones por IA ==============================

    /**
     * ⚠️ <b>Sin transacción propia, a propósito.</b> La propuesta pedida se guarda y se
     * confirma ANTES de encolar: el agente la lee desde otro hilo en cuanto el mensaje llega,
     * y si leyera antes del commit no encontraría nada que proponer.
     */
    @Override
    public RecomendacionPedida pedirRecomendaciones(ContextoUsuario quien, Long vacanteId,
                                                    PedirRecomendaciones datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = versionesBanco.preguntasPropiasDe(vacante.getId(), BORRADOR)
                .orElse(null);
        VersionBanco base = borrador != null ? borrador
                : versionesBanco.preguntasPropiasDe(vacante.getId(), PUBLICADA).orElse(null);
        if (borrador == null && base != null && hayPostulantes(vacante)) {
            throw new IllegalStateException(VARA_QUIETA);
        }
        int total = base == null ? 0 : totalDe(porPuntos.calcular(base.getId(), null));
        if (total >= ReglasDePuntos.TOTAL) {
            throw new IllegalStateException("El borrador ya suma " + total + " puntos: no queda "
                    + "sitio. Quita puntos o preguntas para que la IA complete lo que falte.");
        }
        String motivo = cola.porQueNoSePuedeUsarLaIa(vacante.getOrganizacionId());
        if (motivo != null) {
            return new RecomendacionPedida(false, motivo + " No se pidieron recomendaciones.");
        }
        if ("EN_CURSO".equals(cola.comoVaElRecomendador(vacante.getId()).estado())) {
            return new RecomendacionPedida(false,
                    "Ya hay una recomendación en marcha para esta vacante: espera a que termine.");
        }
        int faltan = ReglasDePuntos.TOTAL - total;
        PropuestaPreguntas pedida = propuestas.save(PropuestaPreguntas.builder()
                .organizacionId(vacante.getOrganizacionId())
                .vacanteId(vacante.getId())
                .indicacion(textoONulo(datos == null ? null : datos.indicacion()))
                .puntosQueFaltan(faltan)
                .estado(PropuestaPreguntas.PEDIDA)
                .pedidaPorUsuarioId(quien.usuarioId())
                .creadoEn(Instant.now())
                .build());
        if (!cola.encolarRecomendador(vacante.getOrganizacionId(), vacante.getId())) {
            pedida.setEstado(PropuestaPreguntas.FALLIDA);
            pedida.setMotivoFallo("No se pudo poner en la cola: ya había una en marcha, o la IA "
                    + "está apagada.");
            pedida.setTerminadaEn(Instant.now());
            propuestas.save(pedida);
            return new RecomendacionPedida(false, pedida.getMotivoFallo());
        }
        return new RecomendacionPedida(true, "La IA completará los " + faltan
                + " puntos que faltan. Tarda unos segundos.");
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoDeLaRecomendacion comoVaLaRecomendacion(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laVisible(quien, vacanteId);
        PropuestaPreguntas ultima = propuestas.findFirstByVacanteIdOrderByIdDesc(vacante.getId())
                .orElse(null);
        if (ultima == null) {
            return new EstadoDeLaRecomendacion("SIN_PEDIR", null, null, null, null, List.of());
        }
        if (PropuestaPreguntas.LISTA.equals(ultima.getEstado())) {
            return new EstadoDeLaRecomendacion("LISTA", null, ultima.getId(),
                    ultima.getPuntosQueFaltan(), ultima.getIndicacion(), leerPropuesta(ultima));
        }
        if (PropuestaPreguntas.FALLIDA.equals(ultima.getEstado())) {
            return new EstadoDeLaRecomendacion("FALLIDA", ultima.getMotivoFallo(), ultima.getId(),
                    ultima.getPuntosQueFaltan(), ultima.getIndicacion(), List.of());
        }
        ColaCalificacionIa.Seguimiento trabajo = cola.comoVaElRecomendador(vacante.getId());
        String estado = switch (trabajo.estado()) {
            case "EN_CURSO" -> "EN_CURSO";
            case "DETENIDA" -> "FALLIDA";
            default -> "FALLIDA";
        };
        String motivo = "EN_CURSO".equals(estado) ? null
                : trabajo.motivo() != null ? trabajo.motivo()
                : "La IA terminó sin dejar ninguna propuesta.";
        return new EstadoDeLaRecomendacion(estado, motivo, ultima.getId(),
                ultima.getPuntosQueFaltan(), ultima.getIndicacion(), List.of());
    }

    @Override
    @Transactional
    public EditorDePreguntas agregarDeLaPropuesta(ContextoUsuario quien, Long vacanteId,
                                                  Long propuestaId, AgregarDeLaPropuesta datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        PropuestaPreguntas propuesta = propuestas.findByIdAndVacanteId(propuestaId, vacante.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Propuesta", "id", propuestaId));
        if (!PropuestaPreguntas.LISTA.equals(propuesta.getEstado())) {
            throw new IllegalStateException("Esa propuesta todavía no está lista");
        }
        List<CriterioPropuesto> propuestos = leerPropuesta(propuesta);
        VersionBanco borrador = elBorrador(vacante);
        Map<Long, CriterioBanco> existentes = criteriosBanco
                .findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).stream()
                .collect(Collectors.toMap(CriterioBanco::getId, Function.identity()));

        // Qué preguntas se agregan, agrupadas por su criterio propuesto. Nada se reemplaza:
        // solo se suma lo que la persona eligió.
        Map<Integer, Set<Integer>> elegidas = new LinkedHashMap<>();
        for (Integer c : lista(datos == null ? null : datos.criterios())) {
            CriterioPropuesto cp = enLaPropuesta(propuestos, c);
            Set<Integer> todas = new LinkedHashSet<>();
            for (int i = 0; i < lista(cp.preguntas()).size(); i++) {
                todas.add(i);
            }
            elegidas.computeIfAbsent(c, k -> new LinkedHashSet<>()).addAll(todas);
        }
        for (PreguntaElegida p : lista(datos == null ? null : datos.preguntas())) {
            CriterioPropuesto cp = enLaPropuesta(propuestos, p.criterio());
            if (p.pregunta() < 0 || p.pregunta() >= lista(cp.preguntas()).size()) {
                throw new IllegalArgumentException("La propuesta no tiene esa pregunta");
            }
            elegidas.computeIfAbsent(p.criterio(), k -> new LinkedHashSet<>()).add(p.pregunta());
        }
        if (elegidas.isEmpty()) {
            throw new IllegalArgumentException("Elige qué agregar de la propuesta");
        }

        for (Map.Entry<Integer, Set<Integer>> e : elegidas.entrySet()) {
            CriterioPropuesto cp = propuestos.get(e.getKey());
            Long criterioId;
            if (cp.criterioExistenteId() != null) {
                if (!existentes.containsKey(cp.criterioExistenteId())) {
                    throw new IllegalStateException("El criterio al que iban esas preguntas ya no "
                            + "está en el borrador");
                }
                criterioId = cp.criterioExistenteId();
            } else {
                criterioId = nuevoCriterio(borrador, cp.nombre(), cp.queEvalua()).getId();
            }
            for (Integer i : e.getValue()) {
                PreguntaPropuesta pp = cp.preguntas().get(i);
                List<OpcionAValidar> suyas = lista(pp.opciones()).stream()
                        .map(o -> new OpcionAValidar(o.texto(), o.puntos())).toList();
                List<String> faltas = ReglasDePuntos.formaDeLaPregunta("La pregunta propuesta",
                        new PreguntaAValidar(pp.tipo(), pp.enunciado(), pp.puntos(),
                                pp.queDebeTener(), suyas));
                if (!faltas.isEmpty()) {
                    throw new PreguntasInvalidasException("La propuesta no se puede agregar así",
                            faltas);
                }
                nuevaPregunta(borrador, pp.tipo(), pp.enunciado(), entero(pp.puntos()), criterioId,
                        pp.queDebeTener(), suyas);
            }
        }
        return editor(quien, vacante);
    }

    private static CriterioPropuesto enLaPropuesta(List<CriterioPropuesto> propuestos, Integer i) {
        if (i == null || i < 0 || i >= propuestos.size()) {
            throw new IllegalArgumentException("La propuesta no tiene ese criterio");
        }
        return propuestos.get(i);
    }

    private List<CriterioPropuesto> leerPropuesta(PropuestaPreguntas propuesta) {
        if (propuesta.getContenido() == null || propuesta.getContenido().isBlank()) {
            return List.of();
        }
        return JSON.readValue(propuesta.getContenido(), new TypeReference<List<CriterioPropuesto>>() { });
    }

    // ============================== Ajustar a mano una abierta ==============================

    @Override
    @Transactional
    public void ajustarNota(ContextoUsuario quien, Long postulacionId, Long respuestaId,
                            AjustarNota datos) {
        Postulacion postulacion = alcance.laPostulacionVisible(quien, postulacionId, PERMISO_AJUSTAR);
        alcance.exigirQueSuVacanteSigaExistiendo(postulacion);
        if (postulacion.getEvaluacionId() == null) {
            throw new ResourceNotFoundException("Respuesta", "id", respuestaId);
        }
        Evaluacion evaluacion = evaluaciones.findById(postulacion.getEvaluacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Respuesta", "id", respuestaId));
        if (porPuntos.versionPorPuntos(evaluacion.getVersionBancoNivelId()).isEmpty()) {
            throw new IllegalStateException("Aquí solo se ajustan las abiertas de las preguntas "
                    + "propias de una vacante");
        }
        Respuesta respuesta = respuestas.findById(respuestaId)
                .filter(r -> evaluacion.getId().equals(r.getEvaluacionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Respuesta", "id", respuestaId));
        Pregunta pregunta = preguntas.findById(respuesta.getPreguntaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta", "id", respuesta.getPreguntaId()));
        if (!ReglasDePuntos.ABIERTA.equals(pregunta.getTipo())) {
            throw new IllegalArgumentException("Una cerrada no se ajusta: su nota sale de los "
                    + "puntos de la versión");
        }
        int maximo = puntosDe(pregunta);
        if (maximo == 0) {
            throw new IllegalArgumentException("Esta pregunta vale 0 puntos: no hay nota que ajustar");
        }
        if (respuesta.getTexto() == null || respuesta.getTexto().isBlank()) {
            throw new IllegalArgumentException("La respuesta está en blanco: vale 0 y no se califica");
        }
        BigDecimal puntaje = datos.puntaje();
        if (puntaje.signum() < 0 || puntaje.compareTo(BigDecimal.valueOf(maximo)) > 0) {
            throw new IllegalArgumentException("La nota tiene que estar entre 0 y " + maximo);
        }
        if (puntaje.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("La nota admite hasta dos decimales");
        }
        String motivo = datos.motivo().strip();

        NotaRespuesta nota = notasRespuesta.findByRespuestaId(respuestaId).orElse(null);
        BigDecimal anterior = nota == null ? null : nota.getPuntaje();
        if (nota == null) {
            // La IA no pudo: la califica una persona. `explicacion` es NOT NULL y lleva el
            // motivo; la nota de la IA queda vacía porque no la hubo.
            nota = NotaRespuesta.builder()
                    .respuestaId(respuestaId)
                    .explicacion(motivo)
                    .creadoEn(Instant.now())
                    .build();
        } else if (nota.getAjustadaPorUsuarioId() == null) {
            // La de la IA se guarda una sola vez, en el primer ajuste, y queda a la vista.
            nota.setPuntajeIa(nota.getPuntaje());
        }
        nota.setPuntaje(puntaje.setScale(2, RoundingMode.HALF_UP));
        nota.setAjustadaPorUsuarioId(quien.usuarioId());
        nota.setMotivoAjuste(motivo);
        nota.setAjustadaEn(Instant.now());
        notasRespuesta.save(nota);
        auditoria.registrar(quien.organizacionId(), quien, "ajustar_nota_abierta", "respuesta",
                respuestaId, anterior == null ? null : Map.of("puntaje", anterior.toPlainString()),
                Map.of("puntaje", puntaje.toPlainString(), "postulacion", postulacionId), motivo);

        // Se recalculan el criterio, el banco, el Perfil Integral y el grupo, sin volver a
        // llamar a la IA y sin mover a nadie de etapa.
        puente.recalcularSinMover(postulacionId);
    }

    // ============================== Apoyo: vacante y versiones ==============================

    private Vacante laVisible(ContextoUsuario quien, Long vacanteId) {
        return alcance.laVacanteVisible(quien, vacanteId, PERMISO_VER);
    }

    private Vacante laEditable(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = alcance.laVacanteVisible(quien, vacanteId, PERMISO_EDITAR);
        if (vacante.getArchivadaEn() != null || "CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada o archivada no se edita");
        }
        return vacante;
    }

    private boolean puedeEditar(ContextoUsuario quien, Vacante vacante) {
        if (!quien.tiene(PERMISO_EDITAR)) {
            return false;
        }
        FiltroAlcance alcanceDeEdicion = permisos.alcanceDe(PERMISO_EDITAR);
        return alcanceDeEdicion != null
                && vacante.getArchivadaEn() == null
                && !"CERRADA".equals(vacante.getEstado())
                && alcance.alcanzaALaVacante(quien, alcanceDeEdicion, vacante);
    }

    private boolean hayPostulantes(Vacante vacante) {
        return postulaciones.countByVacanteId(vacante.getId()) > 0;
    }

    private String nivelDe(Vacante vacante) {
        return vacante.getPuestoId() == null ? null
                : puestos.findByIdAndOrganizacionId(vacante.getPuestoId(), vacante.getOrganizacionId())
                        .map(Puesto::getNivelPuestoCodigo).orElse(null);
    }

    private static String estadoDe(Vacante v) {
        if (v.getArchivadaEn() != null) {
            return "ARCHIVADA";
        }
        return "CERRADA".equals(v.getEstado()) ? "CERRADA" : "ACTIVA";
    }

    private VersionBanco laPublicada(Vacante vacante) {
        return versionesBanco.preguntasPropiasDe(vacante.getId(), PUBLICADA)
                .orElseThrow(() -> new IllegalStateException(
                        "Esta vacante todavía no tiene preguntas publicadas"));
    }

    /** La versión publicada de otra vacante de la MISMA empresa; lo demás es 404. */
    private VersionBanco laPublicadaDeOtraVacante(ContextoUsuario quien, Long vacanteOrigenId) {
        Vacante origen = vacantes
                .findByIdAndOrganizacionIdAndEliminadaEnIsNull(vacanteOrigenId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteOrigenId));
        return versionesBanco.preguntasPropiasDe(origen.getId(), PUBLICADA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Preguntas publicadas de la vacante", "id", vacanteOrigenId));
    }

    /**
     * El borrador de la vacante; si no hay, se abre. Con una versión publicada, el borrador
     * nace como copia suya —«abrir un borrador desde la publicada»—, y eso solo mientras
     * nadie haya postulado: después, la vara no se mueve.
     */
    private VersionBanco elBorrador(Vacante vacante) {
        Optional<VersionBanco> borrador = versionesBanco.preguntasPropiasDe(vacante.getId(), BORRADOR);
        if (borrador.isPresent()) {
            return borrador.get();
        }
        Optional<VersionBanco> publicada = versionesBanco.preguntasPropiasDe(vacante.getId(), PUBLICADA);
        if (publicada.isPresent() && hayPostulantes(vacante)) {
            throw new IllegalStateException(VARA_QUIETA);
        }
        VersionBanco nuevo = nuevoBorrador(vacante);
        publicada.ifPresent(p -> copiarContenido(p, nuevo));
        return nuevo;
    }

    private VersionBanco elBorradorQueYaExiste(Vacante vacante) {
        return versionesBanco.preguntasPropiasDe(vacante.getId(), BORRADOR)
                .orElseThrow(() -> {
                    boolean publicada = versionesBanco.preguntasPropiasDe(vacante.getId(), PUBLICADA)
                            .isPresent();
                    return new IllegalStateException(publicada && hayPostulantes(vacante)
                            ? VARA_QUIETA
                            : "No hay borrador que cambiar: abre uno agregando una pregunta o un criterio");
                });
    }

    private VersionBanco nuevoBorrador(Vacante vacante) {
        return versionesBanco.saveAndFlush(VersionBanco.builder()
                .organizacionId(vacante.getOrganizacionId())
                .tipoBanco("VACANTE")
                .nivelPuestoCodigo(nivelDe(vacante))
                .vacanteId(vacante.getId())
                .proposito(PROPOSITO)
                .metodoCalificacion(CalificacionPorPuntos.METODO)
                .etiqueta("Preguntas propias · " + vacante.getTitulo())
                .estado(BORRADOR)
                .versionGuia(1)
                .creadoEn(Instant.now())
                .build());
    }

    /**
     * Copia criterios, preguntas, opciones, guía y minutos. Es una copia, no un enlace: cambiar
     * después cualquiera de las dos no toca la otra.
     */
    private void copiarContenido(VersionBanco origen, VersionBanco destino) {
        destino.setGuiaCalificacion(origen.getGuiaCalificacion());
        destino.setMinutosObjetivo(origen.getMinutosObjetivo());
        versionesBanco.save(destino);

        Map<Long, Long> criterioNuevo = new HashMap<>();
        for (CriterioBanco c : criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(origen.getId())) {
            CriterioBanco copia = criteriosBanco.save(CriterioBanco.builder()
                    .versionBancoId(destino.getId())
                    .nombre(c.getNombre())
                    .queEvalua(c.getQueEvalua())
                    .orden(c.getOrden())
                    .creadoEn(Instant.now())
                    .build());
            criterioNuevo.put(c.getId(), copia.getId());
        }
        List<Pregunta> suyas = preguntas.findByVersionBancoIdOrderByOrden(origen.getId());
        Map<Long, List<Opcion>> opcionesDe = suyas.isEmpty() ? Map.of()
                : opciones.findByPreguntaIdIn(suyas.stream().map(Pregunta::getId).toList()).stream()
                        .collect(Collectors.groupingBy(Opcion::getPreguntaId));
        for (Pregunta p : suyas) {
            Pregunta copia = preguntas.save(Pregunta.builder()
                    .versionBancoId(destino.getId())
                    .codigo(p.getCodigo())
                    .tipo(p.getTipo())
                    .enunciado(p.getEnunciado())
                    .esPuntuable(p.isEsPuntuable())
                    .orden(p.getOrden())
                    .puntos(p.getPuntos())
                    .criterioBancoId(p.getCriterioBancoId() == null ? null
                            : criterioNuevo.get(p.getCriterioBancoId()))
                    .queDebeTener(p.getQueDebeTener())
                    .creadoEn(Instant.now())
                    .build());
            for (Opcion o : opcionesDe.getOrDefault(p.getId(), List.of())) {
                opciones.save(Opcion.builder()
                        .preguntaId(copia.getId())
                        .letra(o.getLetra())
                        .texto(o.getTexto())
                        .puntaje(o.getPuntaje())
                        .orden(o.getOrden())
                        .creadoEn(Instant.now())
                        .build());
            }
        }
    }

    /** Un borrador se descarta entero: ninguna evaluación apunta a él. */
    private void vaciarYBorrar(VersionBanco borrador) {
        List<Pregunta> suyas = preguntas.findByVersionBancoIdOrderByOrden(borrador.getId());
        if (!suyas.isEmpty()) {
            opciones.deleteByPreguntaIdIn(suyas.stream().map(Pregunta::getId).toList());
        }
        preguntas.deleteByVersionBancoId(borrador.getId());
        criteriosBanco.deleteByVersionBancoId(borrador.getId());
        versionesBanco.delete(borrador);
        versionesBanco.flush();
    }

    private CriterioBanco elCriterio(VersionBanco version, Long criterioId) {
        return criteriosBanco.findById(criterioId)
                .filter(c -> version.getId().equals(c.getVersionBancoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Criterio", "id", criterioId));
    }

    private Pregunta laPregunta(VersionBanco version, Long preguntaId) {
        return preguntas.findById(preguntaId)
                .filter(p -> version.getId().equals(p.getVersionBancoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta", "id", preguntaId));
    }

    private CriterioBanco nuevoCriterio(VersionBanco borrador, String nombre, String queEvalua) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El criterio necesita un nombre");
        }
        int orden = criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).stream()
                .map(CriterioBanco::getOrden).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        return criteriosBanco.save(CriterioBanco.builder()
                .versionBancoId(borrador.getId())
                .nombre(nombre.strip())
                .queEvalua(textoONulo(queEvalua))
                .orden(orden)
                .creadoEn(Instant.now())
                .build());
    }

    private Pregunta nuevaPregunta(VersionBanco borrador, String tipo, String enunciado, int puntos,
                                   Long criterioId, String queDebeTener,
                                   List<OpcionAValidar> suyasOpciones) {
        Pregunta pregunta = preguntas.save(Pregunta.builder()
                .versionBancoId(borrador.getId())
                .codigo(codigoNuevo(borrador))
                .tipo(tipo)
                .enunciado(enunciado.strip())
                // En el método PUNTOS decide `puntos`; esto solo evita dejar la columna
                // incoherente, y nada del método nuevo la lee.
                .esPuntuable(puntos > 0)
                .orden(siguienteOrden(borrador.getId(), criterioId))
                .puntos(puntos)
                .criterioBancoId(criterioId)
                .queDebeTener(ReglasDePuntos.ABIERTA.equals(tipo) ? textoONulo(queDebeTener) : null)
                .creadoEn(Instant.now())
                .build());
        guardarOpciones(pregunta, suyasOpciones);
        return pregunta;
    }

    /**
     * Las opciones de una cerrada, con su orden explícito y la letra que pone el servidor:
     * A, B, C… en las de opción; el número del nivel en la escala, donde el rótulo es opcional
     * y sin él se enseña el número.
     */
    private void guardarOpciones(Pregunta pregunta, List<OpcionAValidar> suyas) {
        if (!ReglasDePuntos.esCerrada(pregunta.getTipo())) {
            return;
        }
        boolean escala = ReglasDePuntos.ESCALA.equals(pregunta.getTipo());
        int n = 1;
        for (OpcionAValidar o : suyas) {
            String rotulo = o.texto() == null ? "" : o.texto().strip();
            opciones.save(Opcion.builder()
                    .preguntaId(pregunta.getId())
                    .letra(escala ? String.valueOf(n) : String.valueOf((char) ('A' + n - 1)))
                    .texto(rotulo.isEmpty() && escala ? String.valueOf(n) : rotulo)
                    .puntaje(o.puntos().setScale(0, RoundingMode.UNNECESSARY))
                    .orden(n)
                    .creadoEn(Instant.now())
                    .build());
            n++;
        }
    }

    private String codigoNuevo(VersionBanco borrador) {
        int mayor = preguntas.findByVersionBancoIdOrderByOrden(borrador.getId()).stream()
                .map(Pregunta::getCodigo)
                .filter(c -> c != null && c.matches("P\\d+"))
                .mapToInt(c -> Integer.parseInt(c.substring(1)))
                .max().orElse(0);
        return "P" + (mayor + 1);
    }

    private int siguienteOrden(Long versionId, Long criterioId) {
        return preguntas.findByVersionBancoIdOrderByOrden(versionId).stream()
                .filter(p -> Objects.equals(p.getCriterioBancoId(), criterioId))
                .map(Pregunta::getOrden).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    private void exigirForma(String donde, GuardarPregunta datos) {
        List<String> faltas = ReglasDePuntos.formaDeLaPregunta(donde,
                new PreguntaAValidar(datos.tipo(), datos.enunciado(), datos.puntos(),
                        datos.queDebeTener(), comoOpciones(datos.opciones())));
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("La pregunta no se puede guardar así", faltas);
        }
    }

    private static List<OpcionAValidar> comoOpciones(List<GuardarOpcion> suyas) {
        return lista(suyas).stream().map(o -> new OpcionAValidar(o.texto(), o.puntos())).toList();
    }

    // ============================== Apoyo: lo que se pinta ==============================

    private VersionDePreguntas comoVersion(VersionBanco v) {
        CalificacionPorPuntos.Resultado r = porPuntos.calcular(v.getId(), null);
        List<CriterioDeLaVersion> criterios = r.criterios().stream()
                .map(c -> new CriterioDeLaVersion(c.criterio().getId(), c.criterio().getNombre(),
                        c.criterio().getQueEvalua(),
                        c.criterio().getOrden() == null ? 0 : c.criterio().getOrden(),
                        c.maximo(), c.sistemaMaximo(), c.iaMaximo(),
                        c.preguntas().stream().map(ServicioPreguntasVacanteImpl::comoPregunta).toList()))
                .toList();
        return new VersionDePreguntas(v.getId(), v.getEstado(), v.getGuiaCalificacion(),
                v.getMinutosObjetivo(), v.getVersionGuia() == null ? 1 : v.getVersionGuia(),
                totalDe(r), criterios.size(), r.todas().size(), criterios,
                r.sinCriterio().stream().map(ServicioPreguntasVacanteImpl::comoPregunta).toList(),
                faltasParaPublicar(r));
    }

    private static PreguntaDeLaVersion comoPregunta(CalificacionPorPuntos.PreguntaCalculada pc) {
        Pregunta p = pc.pregunta();
        return new PreguntaDeLaVersion(p.getId(), p.getTipo(), p.getEnunciado(), pc.maximo(),
                p.getCriterioBancoId(), p.getOrden() == null ? 0 : p.getOrden(),
                p.getQueDebeTener(),
                pc.opciones().stream()
                        .map(o -> new OpcionDeLaVersion(o.getId(), o.getTexto(),
                                o.getPuntaje() == null ? 0 : o.getPuntaje().intValue(),
                                o.getOrden() == null ? 0 : o.getOrden()))
                        .toList());
    }

    private static int totalDe(CalificacionPorPuntos.Resultado r) {
        return r.todas().stream().mapToInt(CalificacionPorPuntos.PreguntaCalculada::maximo).sum();
    }

    /**
     * Todo lo que impide publicar, dicho entero (punto 8 de la spec): el total de 100, que
     * toda pregunta esté en un criterio, que ningún criterio esté vacío, las reglas de cada
     * tipo, y que no haya enunciados ni nombres vacíos.
     */
    static List<String> faltasParaPublicar(CalificacionPorPuntos.Resultado r) {
        List<String> faltas = new ArrayList<>();
        List<CalificacionPorPuntos.PreguntaCalculada> todas = new ArrayList<>();
        r.criterios().forEach(c -> todas.addAll(c.preguntas()));
        todas.addAll(r.sinCriterio());
        if (todas.isEmpty()) {
            faltas.add("Todavía no hay ninguna pregunta.");
        }
        String total = ReglasDePuntos.faltaDelTotal(totalDe(r));
        if (total != null && !todas.isEmpty()) {
            faltas.add(total);
        }
        for (CalificacionPorPuntos.CriterioCalculado c : r.criterios()) {
            if (c.criterio().getNombre() == null || c.criterio().getNombre().isBlank()) {
                faltas.add("Un criterio no tiene nombre.");
            }
            if (c.preguntas().isEmpty()) {
                faltas.add("El criterio «" + c.criterio().getNombre() + "» no tiene preguntas.");
            }
        }
        int posicion = 1;
        for (CalificacionPorPuntos.PreguntaCalculada pc : todas) {
            Pregunta p = pc.pregunta();
            String donde = ReglasDePuntos.nombreDe(posicion++, p.getEnunciado());
            if (p.getCriterioBancoId() == null) {
                faltas.add(donde + ": no está en ningún criterio.");
            }
            PreguntaAValidar aValidar = new PreguntaAValidar(p.getTipo(), p.getEnunciado(),
                    p.getPuntos() == null ? null : BigDecimal.valueOf(p.getPuntos()),
                    p.getQueDebeTener(),
                    pc.opciones().stream().map(o -> new OpcionAValidar(
                            ReglasDePuntos.ESCALA.equals(p.getTipo()) ? null : o.getTexto(),
                            o.getPuntaje())).toList());
            faltas.addAll(ReglasDePuntos.formaDeLaPregunta(donde, aValidar));
            faltas.addAll(ReglasDePuntos.puntuacionDeLaPregunta(donde, aValidar));
        }
        return faltas;
    }

    private List<Pregunta> enOrdenDePresentacion(VersionBanco version, List<Pregunta> suyas) {
        CalificacionPorPuntos.Resultado r = porPuntos.calcular(version.getId(), null);
        Map<Long, Pregunta> porId = suyas.stream().collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        return r.todas().stream().map(pc -> porId.get(pc.pregunta().getId()))
                .filter(Objects::nonNull).toList();
    }

    // ============================== Apoyo: recalificación ==============================

    /** Las postulaciones de la vacante que entregaron su evaluación sobre esta versión. */
    private List<Postulacion> postulacionesQueEntregaron(Vacante vacante, VersionBanco version) {
        List<Postulacion> conEvaluacion = postulaciones.findByVacanteIdOrderByCreadoEnDesc(vacante.getId())
                .stream().filter(p -> p.getEvaluacionId() != null).toList();
        if (conEvaluacion.isEmpty()) {
            return List.of();
        }
        Set<Long> deEstaVersion = new HashSet<>();
        evaluaciones.findAllById(conEvaluacion.stream().map(Postulacion::getEvaluacionId).toList())
                .forEach(e -> {
                    if (version.getId().equals(e.getVersionBancoNivelId())
                            && "TERMINADA".equals(e.getEstado())) {
                        deEstaVersion.add(e.getId());
                    }
                });
        return conEvaluacion.stream().filter(p -> deEstaVersion.contains(p.getEvaluacionId())).toList();
    }

    /**
     * Quién tiene abiertas calificadas por la IA (no ajustadas a mano), con esas notas.
     * Es la gente a la que una guía nueva obliga a recalificar.
     */
    private Map<Long, List<NotaRespuesta>> notasDeLaIaPorPostulacion(Vacante vacante, VersionBanco version) {
        List<Postulacion> rendidas = postulacionesQueEntregaron(vacante, version);
        if (rendidas.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> postulacionDeEvaluacion = rendidas.stream()
                .collect(Collectors.toMap(Postulacion::getEvaluacionId, Postulacion::getId, (a, b) -> a));
        List<Respuesta> suyas = respuestas.findByEvaluacionIdIn(postulacionDeEvaluacion.keySet());
        if (suyas.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> evaluacionDeRespuesta = suyas.stream()
                .collect(Collectors.toMap(Respuesta::getId, Respuesta::getEvaluacionId));
        Map<Long, List<NotaRespuesta>> salida = new LinkedHashMap<>();
        for (NotaRespuesta n : notasRespuesta.findByRespuestaIdIn(List.copyOf(evaluacionDeRespuesta.keySet()))) {
            if (n.getVersionGuia() == null || n.getAjustadaPorUsuarioId() != null) {
                continue;
            }
            Long postulacionId = postulacionDeEvaluacion.get(evaluacionDeRespuesta.get(n.getRespuestaId()));
            salida.computeIfAbsent(postulacionId, k -> new ArrayList<>()).add(n);
        }
        return salida;
    }

    private void exigirQueNoHayaRecalificacionEnCurso(Collection<Long> postulacionIds) {
        boolean enCurso = cola.recalificacionDe(List.copyOf(postulacionIds)).values().stream()
                .anyMatch(s -> "EN_CURSO".equals(s.estado()));
        if (enCurso) {
            throw new IllegalStateException("Hay una recalificación de la IA en curso: espera a "
                    + "que termine para volver a cambiar la guía o los puntos.");
        }
    }

    private Recalificacion recalificacionDe(Vacante vacante, VersionBanco publicada) {
        int vigente = publicada.getVersionGuia() == null ? 1 : publicada.getVersionGuia();
        Map<Long, List<NotaRespuesta>> conNota = notasDeLaIaPorPostulacion(vacante, publicada);
        int rindieron = postulacionesQueEntregaron(vacante, publicada).size();
        if (conNota.isEmpty()) {
            return new Recalificacion(rindieron, 0, 0, 0, 0, List.of());
        }
        Map<Long, ColaCalificacionIa.Seguimiento> seguimiento =
                cola.recalificacionDe(List.copyOf(conNota.keySet()));
        int alDia = 0;
        int recalificando = 0;
        int pendientes = 0;
        Set<String> motivos = new LinkedHashSet<>();
        for (Map.Entry<Long, List<NotaRespuesta>> e : conNota.entrySet()) {
            boolean alDiaEsta = e.getValue().stream().allMatch(n -> Objects.equals(n.getVersionGuia(), vigente));
            ColaCalificacionIa.Seguimiento s = seguimiento.get(e.getKey());
            if (alDiaEsta) {
                alDia++;
            } else if (s != null && "EN_CURSO".equals(s.estado())) {
                recalificando++;
            } else {
                pendientes++;
                motivos.add(s != null && s.motivo() != null ? s.motivo()
                        : "No se llegó a pedir su recalificación.");
            }
        }
        return new Recalificacion(rindieron, conNota.size(), alDia, recalificando, pendientes,
                List.copyOf(motivos));
    }

    // ============================== Apoyo: pequeño ==============================

    private static int paso(Mover datos) {
        return switch (datos.direccion().toUpperCase(Locale.ROOT)) {
            case "ARRIBA" -> -1;
            case "ABAJO" -> 1;
            default -> throw new IllegalArgumentException("La dirección es ARRIBA o ABAJO");
        };
    }

    private static <T> int indiceDe(List<T> lista, Long id, Function<T, Long> suId, String que) {
        for (int i = 0; i < lista.size(); i++) {
            if (id.equals(suId.apply(lista.get(i)))) {
                return i;
            }
        }
        throw new ResourceNotFoundException(que, "id", id);
    }

    /** Intercambia dos puestos y renumera todo de 1 en adelante: el orden queda sin huecos. */
    private static <T> void renumerar(List<T> lista, int i, int j,
                                      java.util.function.BiConsumer<T, Integer> ponerOrden) {
        T a = lista.get(i);
        lista.set(i, lista.get(j));
        lista.set(j, a);
        for (int k = 0; k < lista.size(); k++) {
            ponerOrden.accept(lista.get(k), k + 1);
        }
    }

    private static int puntosDe(Pregunta p) {
        return p.getPuntos() == null ? 0 : p.getPuntos();
    }

    private static int entero(BigDecimal puntos) {
        return puntos.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private static String sinTildes(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    private static <T> List<T> lista(List<T> valor) {
        return valor == null ? List.of() : valor;
    }
}
