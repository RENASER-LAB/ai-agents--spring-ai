package com.renaser.ai.ai_engine.resena.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.EscribirRespuesta;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.MiRespuesta;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.MisResenas;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ReporteDescargado;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.Reportar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenaDescargada;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenaMia;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenasEnLaDescarga;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.RespuestaDescargada;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResumenResenas;
import com.renaser.ai.ai_engine.resena.entity.ReporteResena;
import com.renaser.ai.ai_engine.resena.entity.Resena;
import com.renaser.ai.ai_engine.resena.entity.RespuestaResena;
import com.renaser.ai.ai_engine.resena.repository.ReporteResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.ResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.RespuestaResenaRepository;
import com.renaser.ai.ai_engine.resena.service.LectorDeResenas;
import com.renaser.ai.ai_engine.resena.service.ReglasDeLaResena;
import com.renaser.ai.ai_engine.resena.service.RelojDeResenas;
import com.renaser.ai.ai_engine.resena.service.ServicioResenasPortal;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ver {@link ServicioResenasPortal}.
 *
 * <p>Toda reseña se busca con la persona que pregunta: lo que no es suyo no se encuentra.
 * Y una reseña borrada por la empresa u ocultada por la plataforma tampoco: para la persona
 * ya no existe, y el portal lo dice con «Esta reseña ya no está disponible».
 */
@Service
@RequiredArgsConstructor
public class ServicioResenasPortalImpl implements ServicioResenasPortal {

    private final ResenaRepository resenas;
    private final RespuestaResenaRepository respuestas;
    private final ReporteResenaRepository reportes;
    private final LectorDeResenas lector;
    private final ServicioAuditoria auditoria;
    private final RelojDeResenas reloj;

    // ============ Leer ============

    @Override
    @Transactional(readOnly = true)
    public ResumenResenas resumen(Long personaId) {
        return lector.resumenDe(personaId);
    }

    @Override
    @Transactional(readOnly = true)
    public MisResenas mias(ContextoUsuario quien) {
        List<Resena> visibles = lector.visiblesDe(quien.personaId());
        if (visibles.isEmpty()) {
            return new MisResenas(lector.resumen(visibles), List.of());
        }
        Map<Long, String> empresas = lector.empresasDe(visibles);
        Map<Long, String> puestos = lector.puestosDe(visibles);
        Map<Long, RespuestaResena> respuestaPorResena = lector.respuestasVivasDe(visibles);
        Map<Long, List<ReporteResena>> reportesPorResena = reportes
                .findByResenaIdInOrderByReportadoEnDesc(
                        visibles.stream().map(Resena::getId).toList()).stream()
                .filter(r -> ReporteResena.RESENA.equals(r.getObjeto()))
                .collect(Collectors.groupingBy(ReporteResena::getResenaId));

        Instant ahora = reloj.ahora();
        List<ResenaMia> lista = visibles.stream().map(r -> {
            RespuestaResena respuesta = respuestaPorResena.get(r.getId());
            List<ReporteResena> suyos = reportesPorResena.getOrDefault(r.getId(), List.of());
            boolean enRevision = !suyos.isEmpty()
                    && ReporteResena.PENDIENTE.equals(suyos.get(0).getEstado());
            return new ResenaMia(r.getId(), r.getEstrellas(), empresas.get(r.getOrganizacionId()),
                    puestos.get(r.getId()), r.getTexto(), r.getPublicadaEn(),
                    r.getEditadaEn() != null,
                    respuesta == null ? null : miRespuesta(respuesta, ahora),
                    respuesta == null, puedeReportar(r, suyos), enRevision);
        }).toList();
        return new MisResenas(lector.resumen(visibles), lista);
    }

    // ============ Reportar ============

    @Override
    @Transactional
    public void reportar(ContextoUsuario quien, Long resenaId, Reportar datos) {
        Resena resena = laVisibleSuya(quien, resenaId);
        String motivo = ReglasDeLaResena.motivoValido(datos == null ? null : datos.motivo());
        String comentario = ReglasDeLaResena.comentarioValido(motivo, datos.comentario());

        List<ReporteResena> suyos = reportes
                .findByResenaIdInOrderByReportadoEnDesc(List.of(resena.getId())).stream()
                .filter(r -> ReporteResena.RESENA.equals(r.getObjeto()))
                .toList();
        if (!suyos.isEmpty()
                && ReporteResena.PENDIENTE.equals(suyos.get(0).getEstado())) {
            throw new IllegalStateException("Ya la reportaste: la estamos revisando");
        }
        if (!puedeReportar(resena, suyos)) {
            throw new IllegalStateException("Ya la revisamos y la mantuvimos. Podrás "
                    + "reportarla otra vez si la empresa la edita");
        }

        ReporteResena reporte = ReporteResena.builder()
                .resenaId(resena.getId())
                .objeto(ReporteResena.RESENA)
                .reportadoPorUsuarioId(quien.usuarioId())
                .motivo(motivo)
                .comentario(comentario)
                .reportadoEn(reloj.ahora())
                .estado(ReporteResena.PENDIENTE)
                .build();
        try {
            reporte = reportes.saveAndFlush(reporte);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Ya la reportaste: la estamos revisando");
        }
        // Ni el comentario ni el texto de la reseña: solo que hubo un reporte y su motivo.
        auditoria.registrar(quien.organizacionId(), quien, "reportar_resena", "reporte_resena",
                reporte.getId(), null, Map.of("resenaId", resena.getId(), "motivo", motivo),
                null);
    }

    // ============ Responder ============

    @Override
    @Transactional
    public void responder(ContextoUsuario quien, Long resenaId, EscribirRespuesta datos) {
        Resena resena = laVisibleSuya(quien, resenaId);
        String texto = ReglasDeLaResena.textoValido(datos == null ? null : datos.texto(),
                ReglasDeLaResena.MIN_RESPUESTA, ReglasDeLaResena.MAX_RESPUESTA,
                "Tu respuesta");
        respuestas.findByResenaIdAndBorradaEnIsNull(resena.getId()).ifPresent(ya -> {
            throw ya.getOcultadaEn() != null
                    ? new IllegalStateException("La plataforma ocultó tu respuesta: no puedes "
                            + "responder de nuevo a esta reseña")
                    : yaRespondiste();
        });

        Instant ahora = reloj.ahora();
        RespuestaResena nueva = RespuestaResena.builder()
                .resenaId(resena.getId())
                .usuarioId(quien.usuarioId())
                .texto(texto)
                .publicadaEn(ahora)
                .editableHasta(ahora.plus(ReglasDeLaResena.UN_MES))
                .build();
        try {
            nueva = respuestas.saveAndFlush(nueva);
        } catch (DataIntegrityViolationException e) {
            throw yaRespondiste();
        }
        auditoria.registrar(quien.organizacionId(), quien, "responder_resena",
                "respuesta_resena", nueva.getId(), null, Map.of("resenaId", resena.getId()),
                null);
    }

    @Override
    @Transactional
    public void editarRespuesta(ContextoUsuario quien, Long resenaId, EscribirRespuesta datos) {
        RespuestaResena respuesta = laMiaQueSePuedeCambiar(quien, resenaId);
        String texto = ReglasDeLaResena.textoValido(datos == null ? null : datos.texto(),
                ReglasDeLaResena.MIN_RESPUESTA, ReglasDeLaResena.MAX_RESPUESTA,
                "Tu respuesta");
        if (respuesta.getTexto().equals(texto)) {
            return;
        }
        respuesta.setTexto(texto);
        respuesta.setEditadaEn(reloj.ahora());
        respuestas.save(respuesta);
        auditoria.registrar(quien.organizacionId(), quien, "editar_respuesta_resena",
                "respuesta_resena", respuesta.getId(), null,
                Map.of("resenaId", respuesta.getResenaId()), null);
    }

    @Override
    @Transactional
    public void borrarRespuesta(ContextoUsuario quien, Long resenaId) {
        RespuestaResena respuesta = laMiaQueSePuedeCambiar(quien, resenaId);
        Instant ahora = reloj.ahora();
        respuesta.setBorradaEn(ahora);
        // Con flush: puede volver a responder enseguida sin tropezar con esta fila.
        respuestas.saveAndFlush(respuesta);
        // Lo que la empresa había reportado de ella ya no tiene qué juzgar.
        reportes.findByRespuestaIdAndEstado(respuesta.getId(), ReporteResena.PENDIENTE)
                .ifPresent(r -> {
                    r.setEstado(ReporteResena.RETIRADA);
                    r.setResueltoEn(ahora);
                    reportes.save(r);
                });
        auditoria.registrar(quien.organizacionId(), quien, "borrar_respuesta_resena",
                "respuesta_resena", respuesta.getId(),
                Map.of("resenaId", respuesta.getResenaId()), null, null);
    }

    // ============ La descarga y el borrado de datos ============

    @Override
    @Transactional(readOnly = true)
    public ResenasEnLaDescarga paraLaDescarga(Long personaId) {
        List<Resena> todas = resenas.findByPersonaId(personaId);
        if (todas.isEmpty()) {
            return new ResenasEnLaDescarga(List.of(), List.of());
        }
        Map<Long, String> empresas = lector.empresasDe(todas);
        List<Resena> vivas = todas.stream().filter(r -> r.getBorradaEn() == null)
                .sorted(LectorDeResenas.MAS_RECIENTES).toList();
        Map<Long, String> puestos = lector.puestosDe(vivas);
        Map<Long, RespuestaResena> respuestaPorResena = lector.respuestasVivasDe(vivas);

        // Las visibles, y también la ocultada que tenga una respuesta suya: la respuesta deja
        // de verse con la reseña, pero es de ella y no se borra. La reseña ocultada va sin su
        // texto, solo para dar contexto.
        List<ResenaDescargada> lista = vivas.stream()
                .filter(r -> r.esVisible() || respuestaPorResena.containsKey(r.getId()))
                .map(r -> {
                    boolean visible = r.esVisible();
                    RespuestaResena resp = respuestaPorResena.get(r.getId());
                    return new ResenaDescargada(empresas.get(r.getOrganizacionId()),
                            puestos.get(r.getId()),
                            visible ? r.getEstrellas() : null,
                            visible ? r.getTexto() : null,
                            r.getPublicadaEn(), visible ? r.getEditadaEn() : null, !visible,
                            resp == null ? null : new RespuestaDescargada(resp.getTexto(),
                                    resp.getPublicadaEn(), resp.getEditadaEn(),
                                    resp.getOcultadaEn() != null, resp.getNotaOcultacion()));
                })
                .toList();

        Map<Long, Resena> porId = todas.stream()
                .collect(Collectors.toMap(Resena::getId, Function.identity()));
        List<ReporteDescargado> susReportes = reportes
                .findByResenaIdInOrderByReportadoEnDesc(porId.keySet()).stream()
                .filter(r -> ReporteResena.RESENA.equals(r.getObjeto()))
                .map(r -> new ReporteDescargado(
                        empresas.get(porId.get(r.getResenaId()).getOrganizacionId()),
                        r.getMotivo(), r.getComentario(), r.getReportadoEn(), r.getEstado(),
                        r.getResueltoEn(), r.getNotaRevision()))
                .toList();
        return new ResenasEnLaDescarga(lista, susReportes);
    }

    @Override
    @Transactional
    public Map<String, Integer> borrarDeLaPersona(Long personaId) {
        List<Resena> todas = resenas.findByPersonaId(personaId);
        if (todas.isEmpty()) {
            return Map.of("resenas", 0, "respuestas", 0, "reportes", 0);
        }
        List<Long> ids = todas.stream().map(Resena::getId).toList();
        List<ReporteResena> susReportes = reportes.findByResenaIdInOrderByReportadoEnDesc(ids);
        List<RespuestaResena> susRespuestas = respuestas.findByResenaIdIn(ids);
        // En este orden por las claves ajenas: lo que apunta primero, lo apuntado después.
        reportes.deleteAllInBatch(susReportes);
        respuestas.deleteAllInBatch(susRespuestas);
        resenas.deleteAllInBatch(todas);
        return Map.of("resenas", todas.size(), "respuestas", susRespuestas.size(),
                "reportes", susReportes.size());
    }

    // ============ Reglas ============

    /**
     * Si puede reportarla ahora: una vez, y otra más si la plataforma la mantuvo y la empresa
     * la editó después. Con un reporte pendiente, no.
     */
    private static boolean puedeReportar(Resena resena, List<ReporteResena> suyos) {
        if (suyos.isEmpty()) {
            return true;
        }
        ReporteResena ultimo = suyos.get(0);
        return ServicioResenasPanelImpl.seEditoDespues(resena.getEditadaEn(), ultimo);
    }

    private static MiRespuesta miRespuesta(RespuestaResena respuesta, Instant ahora) {
        boolean ocultada = respuesta.getOcultadaEn() != null;
        return new MiRespuesta(respuesta.getTexto(), respuesta.getPublicadaEn(),
                respuesta.getEditadaEn() != null, respuesta.getEditableHasta(),
                !ocultada && !ReglasDeLaResena.yaLlego(respuesta.getEditableHasta(), ahora),
                ocultada, respuesta.getNotaOcultacion());
    }

    /**
     * Una reseña suya y visible. La de otra persona, la borrada y la ocultada responden el
     * mismo 404: para ella ya no está.
     */
    private Resena laVisibleSuya(ContextoUsuario quien, Long resenaId) {
        return resenas.findByIdAndPersonaIdAndBorradaEnIsNull(resenaId, quien.personaId())
                .filter(r -> r.getOcultadaEn() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Reseña", "id", resenaId));
    }

    /** Su respuesta viva, si todavía se puede editar o borrar. */
    private RespuestaResena laMiaQueSePuedeCambiar(ContextoUsuario quien, Long resenaId) {
        Resena resena = laVisibleSuya(quien, resenaId);
        RespuestaResena respuesta = respuestas.findByResenaIdAndBorradaEnIsNull(resena.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Respuesta", "reseña",
                        resenaId));
        if (respuesta.getOcultadaEn() != null) {
            throw new IllegalStateException("La plataforma ocultó tu respuesta: ya no se puede "
                    + "cambiar");
        }
        if (ReglasDeLaResena.yaLlego(respuesta.getEditableHasta(), reloj.ahora())) {
            throw new IllegalStateException("Ya no se puede cambiar: el plazo para editarla o "
                    + "borrarla terminó el " + ReglasDeLaResena.fecha(respuesta.getEditableHasta()));
        }
        return respuesta;
    }

    private static IllegalStateException yaRespondiste() {
        return new IllegalStateException("Ya respondiste a esta reseña");
    }
}
