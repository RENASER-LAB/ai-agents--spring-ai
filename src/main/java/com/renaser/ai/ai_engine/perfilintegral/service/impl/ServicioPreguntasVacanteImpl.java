package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AgregarDeLaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AjustarNota;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambiarPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CorregirInstrucciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CriterioDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarCriterio;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarDatosDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarPregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.OpcionDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PreguntaDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PreguntaElegida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDeOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDePregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ResumenDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
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
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ver {@link ServicioPreguntasVacante}. Lo que comparte con el editor de la prueba del puesto
 * vive en {@link EditorDeVersionPropia}; aquí queda lo suyo: el banco se califica pregunta a
 * pregunta y su vara se congela en la primera postulación.
 */
@Service
@RequiredArgsConstructor
public class ServicioPreguntasVacanteImpl extends EditorDeVersionPropia implements ServicioPreguntasVacante {

    private static final String PERMISO_AJUSTAR = "ajustar_nota";

    /** «Una vacante, una versión»: el mismo porqué en todos los 409 de la vara. */
    private static final String VARA_QUIETA = "Esta vacante ya tiene postulantes y sus "
            + "preguntas no se cambian: todos sus candidatos se miden con la misma vara. Solo "
            + "se pueden cambiar los puntos y las instrucciones de la IA, y cualquiera de los "
            + "dos vuelve a calcular la nota de todos.";

    private static final Textos TEXTOS = new Textos("PERFIL_INTEGRAL", "Preguntas propias · ",
            VARA_QUIETA, "No hay borrador que cambiar: abre uno agregando una pregunta o un criterio",
            "No hay borrador que publicar: escribe las preguntas primero",
            "Las preguntas no se pueden publicar todavía: ",
            "Esta vacante todavía no tiene preguntas publicadas", "Preguntas publicadas de la vacante",
            "las preguntas publicadas de esta vacante", "publicar_preguntas_propias",
            "corregir_instrucciones_ia", "copiar_preguntas_propias");

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
    protected EditorDePreguntas editor(ContextoUsuario quien, Vacante vacante) {
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
        reescribirPregunta(borrador, pregunta, new PreguntaEscrita(datos.tipo(), datos.enunciado(),
                entero(datos.puntos()), datos.criterioId(), datos.queDebeTener(),
                comoOpciones(datos.opciones())));
        return editor(quien, vacante);
    }

    // ============================== Con la versión publicada ==============================

    @Override
    @Transactional
    public CambioAplicado corregirInstrucciones(ContextoUsuario quien, Long vacanteId,
                                                CorregirInstrucciones datos) {
        // Las preguntas propias no corrigen nada más que la guía, los criterios y las abiertas.
        return corregir(quien, vacanteId, datos.guiaCalificacion(), datos.criterios(), datos.preguntas(),
                (publicada, antes, despues) -> { });
    }

    @Override
    @Transactional
    public CambioAplicado cambiarPuntos(ContextoUsuario quien, Long vacanteId, CambiarPuntos datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco publicada = laPublicada(vacante);
        exigirQueNoHayaRecalificacionEnCurso(guiasDeLaIa(vacante, publicada).keySet());

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
        // La suma es la de lo escrito, como la del formulario. Si algo de lo que suma no es un
        // entero no se dice ninguna (como QA-11 en la prueba): su falta ya lo dice, y una suma
        // que lo dejara fuera contradiría la escrita.
        List<String> faltas = new ArrayList<>();
        int suma = 0;
        boolean haySuma = true;
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
            OptionalInt escrito = ReglasDePuntos.paraLaSuma(puntos);
            suma += escrito.orElse(0);
            haySuma &= escrito.isPresent();
        }
        String total = haySuma ? ReglasDePuntos.faltaDelTotal(suma) : null;
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
        return enLaCola(pedida, cola.encolarRecomendador(vacante.getOrganizacionId(), vacante.getId()),
                faltan);
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

    // ============================== Su propósito ==============================

    @Override
    protected Piezas piezas() {
        return new Piezas(alcance, permisos, vacantes, puestos, versionesBanco, criteriosBanco,
                preguntas, opciones, propuestas, cola, auditoria);
    }

    @Override
    protected Optional<VersionBanco> version(Long vacanteId, String estado) {
        return versionesBanco.preguntasPropiasDe(vacanteId, estado);
    }

    @Override
    protected Textos textos() {
        return TEXTOS;
    }

    /** La vara de las preguntas propias se congela en la primera postulación. */
    @Override
    protected boolean laVaraNoSeMueve(Vacante vacante) {
        return hayPostulantes(vacante);
    }

    private boolean hayPostulantes(Vacante vacante) {
        return postulaciones.countByVacanteId(vacante.getId()) > 0;
    }

    @Override
    protected List<String> faltasDelBorrador(VersionBanco borrador) {
        return faltasParaPublicar(porPuntos.calcular(borrador.getId(), null));
    }

    @Override
    protected List<VersionBanco> publicadasDe(Long organizacionId) {
        return versionesBanco.propiasPublicadasDe(organizacionId);
    }

    @Override
    protected int cuantosRindieron(Vacante vacante, VersionBanco publicada) {
        return postulacionesQueEntregaron(vacante, publicada).size();
    }

    @Override
    protected Map<Long, ColaCalificacionIa.Seguimiento> seguimiento(List<Long> postulacionIds) {
        return cola.recalificacionDe(postulacionIds);
    }

    @Override
    protected boolean recalificar(Long postulacionId) {
        return cola.recalificar(postulacionId);
    }

    // ============================== Apoyo: versiones ==============================

    /**
     * Copia criterios, preguntas, opciones, guía y minutos. Es una copia, no un enlace: cambiar
     * después cualquiera de las dos no toca la otra.
     */
    @Override
    protected void copiarContenido(VersionBanco origen, VersionBanco destino) {
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
    @Override
    protected void vaciarYBorrar(VersionBanco borrador) {
        List<Pregunta> suyas = preguntas.findByVersionBancoIdOrderByOrden(borrador.getId());
        if (!suyas.isEmpty()) {
            opciones.deleteByPreguntaIdIn(suyas.stream().map(Pregunta::getId).toList());
        }
        preguntas.deleteByVersionBancoId(borrador.getId());
        criteriosBanco.deleteByVersionBancoId(borrador.getId());
        versionesBanco.delete(borrador);
        versionesBanco.flush();
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

    @Override
    protected VersionDePreguntas comoVersion(VersionBanco v) {
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
     * Quién tiene abiertas calificadas por la IA (no ajustadas a mano), con la guía de cada
     * nota. Es la gente a la que una guía nueva obliga a recalificar.
     */
    @Override
    protected Map<Long, List<Integer>> guiasDeLaIa(Vacante vacante, VersionBanco version) {
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
        Map<Long, List<Integer>> salida = new LinkedHashMap<>();
        for (NotaRespuesta n : notasRespuesta.findByRespuestaIdIn(List.copyOf(evaluacionDeRespuesta.keySet()))) {
            if (n.getVersionGuia() == null || n.getAjustadaPorUsuarioId() != null) {
                continue;
            }
            Long postulacionId = postulacionDeEvaluacion.get(evaluacionDeRespuesta.get(n.getRespuestaId()));
            salida.computeIfAbsent(postulacionId, k -> new ArrayList<>()).add(n.getVersionGuia());
        }
        return salida;
    }

    // ============================== Apoyo: pequeño ==============================

    private static int puntosDe(Pregunta p) {
        return p.getPuntos() == null ? 0 : p.getPuntos();
    }

    private static int entero(BigDecimal puntos) {
        return puntos.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
    }
}
