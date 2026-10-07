package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AjustarCriterio;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CriterioDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.EntregaVista;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.NoCompleto;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.OpcionDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PreguntaDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PruebaDelCandidato;
import com.renaser.ai.ai_engine.prueba.entity.Entregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.NotaCriterioPrueba;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.NotaCriterioPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.CriterioDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.PreguntaDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.Resultado;
import com.renaser.ai.ai_engine.prueba.service.CierreDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPruebaPropia;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ver {@link ServicioCalificacionPruebaPropia}. */
@Service
@RequiredArgsConstructor
public class ServicioCalificacionPruebaPropiaImpl implements ServicioCalificacionPruebaPropia {

    private static final String PERMISO_FICHA = "abrir_ficha_candidato";
    private static final String PERMISO_AJUSTAR = "ajustar_nota";
    private static final String PERMISO_CONTENIDO = "descargar_entregables";

    private final AlcanceSobreLaVacante alcance;
    private final PostulacionRepository postulaciones;
    private final IntentoPruebaRepository intentos;
    private final NotaCriterioPruebaRepository notas;
    private final EntregableRepository subidas;
    private final ArchivoRepository archivos;
    private final CalificacionDeLaPruebaPropia calculo;
    private final CierreDeLaPruebaPropia cierre;
    private final ColaCalificacionIa cola;
    private final MaquinaEstados maquina;
    private final NombresDeUsuarios nombres;
    private final ServicioAuditoria auditoria;

    // ============================== La prueba de un candidato ==============================

    @Override
    @Transactional(readOnly = true)
    public PruebaDelCandidato prueba(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = alcance.laPostulacionVisible(quien, postulacionId, PERMISO_FICHA);
        Optional<IntentoPrueba> intento = intentos.findByPostulacionId(postulacion.getId());
        if (intento.isEmpty()) {
            // Todavía no llegó a la etapa técnica: no es un error, es lo normal.
            return new PruebaDelCandidato(postulacion.getId(), "SIN_PRUEBA", false, null, null,
                    false, null, List.of(), false, false, null, List.of(), List.of());
        }
        if (!intento.get().esDelEditor()) {
            throw new ResourceNotFoundException("Prueba del editor", "postulación", postulacionId);
        }
        return armar(quien, postulacion, intento.get());
    }

    private PruebaDelCandidato armar(ContextoUsuario quien, Postulacion postulacion, IntentoPrueba intento) {
        Resultado r = calculo.calcular(intento);
        boolean entregada = intento.getEntregadoEn() != null && !intento.isNoCompletada();
        String estado = intento.isNoCompletada() ? "NO_COMPLETADA"
                : intento.getEntregadoEn() != null ? "ENTREGADA"
                : intento.getIniciadoEn() != null ? "EN_CURSO" : "SIN_EMPEZAR";

        List<Long> ids = List.of(postulacion.getId());
        ColaCalificacionIa.Seguimiento recalificacion = cola.recalificacionDePrueba(ids).get(postulacion.getId());
        boolean recalificando = recalificacion != null && "EN_CURSO".equals(recalificacion.estado());
        String motivoPendiente = null;
        boolean faltaLaIa = r.criterios().stream().anyMatch(c -> c.pendiente() && c.esDeIa());
        if (entregada && faltaLaIa) {
            ColaCalificacionIa.Seguimiento s = cola.calificacionDePrueba(ids).get(postulacion.getId());
            if (s == null) {
                motivoPendiente = "Todavía no se pidió la calificación con IA.";
            } else if ("DETENIDA".equals(s.estado())) {
                motivoPendiente = s.motivo();
            } else if ("TERMINADA".equals(s.estado())) {
                motivoPendiente = "La IA no pudo calificar este criterio con lo que había: lo "
                        + "puede calificar una persona.";
            }
        }

        Set<Long> ajustadores = r.criterios().stream().map(CriterioDeLaPrueba::nota)
                .filter(n -> n != null && n.getAjustadaPorUsuarioId() != null)
                .map(NotaCriterioPrueba::getAjustadaPorUsuarioId).collect(Collectors.toSet());
        Map<Long, String> nombrePorUsuario = ajustadores.isEmpty() ? Map.of() : nombres.porUsuario(ajustadores);

        List<CriterioDelCandidato> criterios = r.criterios().stream()
                .map(c -> comoCriterio(c, nombrePorUsuario))
                .toList();
        boolean veElContenido = quien.tiene(PERMISO_CONTENIDO);
        List<Entregable> suyas = subidas.findByIntentoPruebaId(intento.getId());
        // Entregada: los criterios que faltan para la nota. Sin completar: lo que le faltó
        // responder o subir, que es lo mismo que dice la lista «No completaron la prueba».
        List<String> loQueFalta = intento.isNoCompletada()
                ? java.util.stream.Stream.of(CalificacionDeLaPruebaPropia.loQueFalta(r, suyas).dicho())
                        .filter(t -> !t.isBlank()).toList()
                : !entregada ? List.of()
                : r.criterios().stream().filter(CriterioDeLaPrueba::pendiente)
                        .map(c -> "«" + c.criterio().getNombre() + "»: "
                                + (c.esDePersona() ? "la califica una persona"
                                        : "falta la nota de la IA"))
                        .toList();
        List<EntregaVista> entregables = r.entregables().stream()
                .map(e -> comoEntrega(e, ultimaDe(suyas, e), veElContenido, postulacion))
                .toList();

        return new PruebaDelCandidato(postulacion.getId(), estado, r.esCuestionario(),
                intento.getIniciadoEn(), intento.getEntregadoEn(), intento.isEsEntregaAutomatica(),
                entregada ? r.nota() : null, loQueFalta,
                entregada && quien.tiene(PERMISO_AJUSTAR), recalificando, motivoPendiente,
                criterios, entregables);
    }

    private static CriterioDelCandidato comoCriterio(CriterioDeLaPrueba c, Map<Long, String> nombrePorUsuario) {
        NotaCriterioPrueba n = c.nota();
        String estado = !c.tieneParteCalificada() ? "SOLO_SISTEMA" : c.pendiente() ? "PENDIENTE" : "CALIFICADO";
        return new CriterioDelCandidato(c.criterio().getId(), c.criterio().getNombre(),
                c.criterio().getQueEvalua(), c.maximo(), c.sistemaMaximo(), c.sistema(),
                c.calificadaMaximo(), c.calificador(),
                n == null ? null : n.getPuntaje(),
                n == null ? null : n.getPuntajeIa(),
                c.notaDelCriterio(), estado,
                n == null ? null : n.getExplicacion(),
                n == null ? null : n.getEvidencia(),
                n == null ? null : n.getOrigen(),
                n == null || n.getAjustadaPorUsuarioId() == null ? null
                        : nombrePorUsuario.getOrDefault(n.getAjustadaPorUsuarioId(), NombresDeUsuarios.ANONIMO),
                n == null ? null : n.getAjustadaEn(),
                n == null ? null : n.getMotivoAjuste(),
                c.preguntas().stream().map(ServicioCalificacionPruebaPropiaImpl::comoPregunta).toList(),
                c.entregables().stream().map(EntregableRequerido::getId).toList());
    }

    private static PreguntaDelCandidato comoPregunta(PreguntaDeLaPrueba pc) {
        RespuestaPrueba r = pc.respuesta();
        List<Long> marcadas;
        if (r == null) {
            marcadas = List.of();
        } else if (ReglasDePuntos.OPCION_MULTIPLE.equals(pc.pregunta().getTipo())) {
            marcadas = CalificacionPorPuntos.marcadasDe(Respuesta.builder().detalle(r.getDetalle()).build());
        } else {
            marcadas = r.getOpcionId() == null ? List.of() : List.of(r.getOpcionId());
        }
        String texto = pc.esAbierta() ? (r == null ? null : r.getTexto())
                : pc.opciones().stream().filter(o -> marcadas.contains(o.getId()))
                        .map(Opcion::getTexto).collect(Collectors.joining(" · "));
        return new PreguntaDelCandidato(pc.pregunta().getId(), pc.pregunta().getTipo(),
                pc.pregunta().getEnunciado(), pc.pregunta().getQueDebeTener(),
                pc.esAbierta() ? null : pc.maximo(), pc.obtenido(),
                texto == null || texto.isBlank() ? null : texto, pc.respondida(),
                pc.opciones().stream()
                        .map(o -> new OpcionDelCandidato(o.getId(), o.getTexto(),
                                o.getPuntaje() == null ? 0 : o.getPuntaje().intValue(),
                                marcadas.contains(o.getId())))
                        .toList());
    }

    private static Optional<Entregable> ultimaDe(List<Entregable> suyas, EntregableRequerido requerido) {
        return suyas.stream()
                .filter(e -> requerido.getId().equals(e.getEntregableRequeridoId()))
                .max(Comparator.comparing(e -> e.getVersion() == null ? 0 : e.getVersion()));
    }

    private EntregaVista comoEntrega(EntregableRequerido requerido, Optional<Entregable> entregado,
                                     boolean veElContenido, Postulacion postulacion) {
        if (entregado.isEmpty()) {
            return vista(requerido, false, null, null, null, null,
                    requerido.isEsObligatorio() ? "No lo entregó, y era obligatorio" : "No lo entregó");
        }
        Entregable e = entregado.get();
        if (!veElContenido) {
            return vista(requerido, true, null, null, null, e.getSubidoEn(),
                    "Hace falta el permiso «descargar_entregables» para abrirlo");
        }
        if (e.getArchivoId() == null) {
            return vista(requerido, true, e.getEnlace(), null, null, e.getSubidoEn(), null);
        }
        Archivo archivo = archivos.findByIdAndOrganizacionId(e.getArchivoId(), postulacion.getOrganizacionId())
                .orElse(null);
        if (archivo == null || archivo.getBorradoEn() != null || archivo.getRuta() == null) {
            return vista(requerido, true, null, null, null, e.getSubidoEn(), "El archivo ya no está guardado");
        }
        return vista(requerido, true, e.getEnlace(), archivo.getId(), archivo.getNombreOriginal(),
                e.getSubidoEn(), null);
    }

    /**
     * Un entregable como lo ve la ficha. Lleva la pregunta de la que es el archivo (V68) para
     * enseñarlo junto a su respuesta; los generales van en su sitio.
     */
    private static EntregaVista vista(EntregableRequerido requerido, boolean loEntrego, String enlace,
                                      Long archivoId, String archivoNombre, java.time.Instant subidoEn,
                                      String porQueNoSeVe) {
        String detalle = requerido.getDetalle() == null || requerido.getDetalle().isBlank()
                ? null : requerido.getDetalle();
        return new EntregaVista(requerido.getId(), requerido.getNombre(), detalle,
                requerido.getFormato(), requerido.isEsObligatorio(), requerido.getQueDebeTener(),
                loEntrego, enlace, archivoId, archivoNombre, subidoEn, porQueNoSeVe,
                requerido.getPreguntaId());
    }

    // ============================== Ajustar a mano ==============================

    @Override
    @Transactional
    public PruebaDelCandidato ajustar(ContextoUsuario quien, Long postulacionId, Long criterioId,
                                      AjustarCriterio datos) {
        Postulacion postulacion = alcance.laPostulacionVisible(quien, postulacionId, PERMISO_AJUSTAR);
        alcance.exigirQueSuVacanteSigaExistiendo(postulacion);
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .filter(IntentoPrueba::esDelEditor)
                .orElseThrow(() -> new ResourceNotFoundException("Prueba del editor", "postulación", postulacionId));
        if (intento.isNoCompletada()) {
            throw new IllegalStateException("Esta prueba quedó sin completar: no se califica.");
        }
        if (intento.getEntregadoEn() == null) {
            throw new IllegalStateException("Esta prueba todavía no está entregada: calificarla "
                    + "ahora sería ponerle nota a lo que lleva a medias.");
        }
        Resultado r = calculo.calcular(intento);
        CriterioDeLaPrueba criterio = r.criterios().stream()
                .filter(c -> c.criterio().getId().equals(criterioId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Criterio", "id", criterioId));
        if (!criterio.tieneParteCalificada()) {
            throw new IllegalArgumentException("La parte automática no se ajusta: sale de los "
                    + "puntos de sus cerradas. «" + criterio.criterio().getNombre()
                    + "» no tiene parte calificada.");
        }
        BigDecimal puntaje = datos.puntaje();
        int maximo = criterio.calificadaMaximo();
        if (puntaje.signum() < 0 || puntaje.compareTo(BigDecimal.valueOf(maximo)) > 0) {
            throw new IllegalArgumentException("La nota de «" + criterio.criterio().getNombre()
                    + "» tiene que estar entre 0 y " + maximo);
        }
        if (puntaje.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("La nota admite hasta dos decimales");
        }
        String motivo = datos.motivo().strip();

        NotaCriterioPrueba nota = notas.findByIntentoPruebaIdAndCriterioBancoId(intento.getId(), criterioId)
                .orElse(null);
        BigDecimal anterior = nota == null ? null : nota.getPuntaje();
        if (nota == null) {
            // De persona, o la IA no pudo: la pone una persona. `explicacion` lleva el motivo.
            nota = NotaCriterioPrueba.builder()
                    .intentoPruebaId(intento.getId())
                    .criterioBancoId(criterioId)
                    .origen(NotaCriterioPrueba.PERSONA)
                    .explicacion(motivo)
                    .calificadaPorUsuarioId(quien.usuarioId())
                    .creadoEn(Instant.now())
                    .build();
        } else if (nota.getAjustadaPorUsuarioId() == null && NotaCriterioPrueba.IA.equals(nota.getOrigen())) {
            // La de la IA se guarda una sola vez, en el primer ajuste, y queda a la vista.
            nota.setPuntajeIa(nota.getPuntaje());
        }
        nota.setPuntaje(puntaje.setScale(2, RoundingMode.HALF_UP));
        nota.setAjustadaPorUsuarioId(quien.usuarioId());
        nota.setMotivoAjuste(motivo);
        nota.setAjustadaEn(Instant.now());
        notas.saveAndFlush(nota);
        auditoria.registrar(quien.organizacionId(), quien, "ajustar_nota_criterio_prueba",
                "nota_criterio_prueba", nota.getId(),
                anterior == null ? null : Map.of("puntaje", anterior.toPlainString()),
                Map.of("puntaje", puntaje.toPlainString(), "criterio", criterioId,
                        "postulacion", postulacionId), motivo);

        // Sin IA y sin mover a nadie, salvo el paso a «por confirmar» si con esto la prueba
        // queda entera (punto 9).
        cierre.recalcular(intento, true);
        Postulacion alDia = postulaciones.findByIdAndOrganizacionId(postulacion.getId(),
                postulacion.getOrganizacionId()).orElse(postulacion);
        return armar(quien, alDia, intento);
    }

    // ============================== Quienes no la completaron ==============================

    @Override
    @Transactional(readOnly = true)
    public List<NoCompleto> noCompletaron(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = alcance.laVacanteVisible(quien, vacanteId, "ver_embudo");
        List<Postulacion> suyas = postulaciones.findByVacanteIdOrderByCreadoEnDesc(vacante.getId());
        if (suyas.isEmpty()) {
            return List.of();
        }
        Map<Long, Postulacion> porId = suyas.stream()
                .collect(Collectors.toMap(Postulacion::getId, Function.identity()));
        List<IntentoPrueba> sinCompletar = intentos.findByPostulacionIdIn(porId.keySet()).stream()
                .filter(IntentoPrueba::isNoCompletada)
                .toList();
        return noCompletaronDe(sinCompletar, porId);
    }

    /** La lista, para una tanda ya leída. La comparte el ranking. */
    List<NoCompleto> noCompletaronDe(List<IntentoPrueba> sinCompletar, Map<Long, Postulacion> porId) {
        if (sinCompletar.isEmpty()) {
            return List.of();
        }
        var faltas = calculo.loQueFaltaTanda(sinCompletar);
        Map<Long, String> nombrePorUsuario = nombres.porUsuario(sinCompletar.stream()
                .map(i -> porId.get(i.getPostulacionId())).filter(java.util.Objects::nonNull)
                .map(Postulacion::getUsuarioId).toList());
        List<NoCompleto> salida = new ArrayList<>();
        for (IntentoPrueba i : sinCompletar) {
            Postulacion p = porId.get(i.getPostulacionId());
            var falta = faltas.get(i.getId());
            if (p == null || falta == null) {
                continue;
            }
            String queFalto = falta.dicho();
            salida.add(new NoCompleto(p.getId(),
                    nombrePorUsuario.getOrDefault(p.getUsuarioId(), NombresDeUsuarios.ANONIMO),
                    p.getEstadoCodigo(), queFalto, i.getEntregadoEn(), maquina.yaTermino(p)));
        }
        return salida;
    }
}
