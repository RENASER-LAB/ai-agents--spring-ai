package com.renaser.ai.ai_engine.resena.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ReporteParaModerar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResolverReporte;
import com.renaser.ai.ai_engine.resena.entity.ReporteResena;
import com.renaser.ai.ai_engine.resena.entity.Resena;
import com.renaser.ai.ai_engine.resena.entity.RespuestaResena;
import com.renaser.ai.ai_engine.resena.repository.ReporteResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.ResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.RespuestaResenaRepository;
import com.renaser.ai.ai_engine.resena.service.AvisosDeResenas;
import com.renaser.ai.ai_engine.resena.service.ReglasDeLaResena;
import com.renaser.ai.ai_engine.resena.service.RelojDeResenas;
import com.renaser.ai.ai_engine.resena.service.ServicioModeracionResenas;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ver {@link ServicioModeracionResenas}.
 *
 * <p>Es de la plataforma y cruza empresas a propósito: por eso busca la reseña por id suelto,
 * que la regla de arquitectura anota como acordado. La puerta se cierra antes, con
 * {@link #exigirPlataforma}.
 */
@Service
@RequiredArgsConstructor
public class ServicioModeracionResenasImpl implements ServicioModeracionResenas {

    private static final String MANTENER = "MANTENER";
    private static final String OCULTAR = "OCULTAR";

    private final ReporteResenaRepository reportes;
    private final ResenaRepository resenas;
    private final RespuestaResenaRepository respuestas;
    private final OrganizacionRepository organizaciones;
    private final PersonaRepository personas;
    private final AvisosDeResenas avisos;
    private final ServicioAuditoria auditoria;
    private final RelojDeResenas reloj;

    @Override
    @Transactional(readOnly = true)
    public List<ReporteParaModerar> reportes(ContextoUsuario quien, boolean resueltos) {
        exigirPlataforma(quien);
        List<ReporteResena> lista = resueltos
                ? reportes.findByEstadoNotOrderByResueltoEnDesc(ReporteResena.PENDIENTE)
                : reportes.findByEstadoOrderByReportadoEnAsc(ReporteResena.PENDIENTE);
        return tarjetas(lista);
    }

    @Override
    @Transactional
    public ReporteParaModerar resolver(ContextoUsuario quien, Long reporteId,
                                       ResolverReporte datos) {
        exigirPlataforma(quien);
        ReporteResena reporte = reportes.findById(reporteId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporte", "id", reporteId));
        if (!ReporteResena.PENDIENTE.equals(reporte.getEstado())) {
            throw new IllegalStateException("Este reporte ya se resolvió");
        }
        String decision = datos == null ? null : datos.decision();
        if (!MANTENER.equals(decision) && !OCULTAR.equals(decision)) {
            throw new IllegalArgumentException("La decisión es MANTENER u OCULTAR");
        }
        String nota = ReglasDeLaResena.textoValido(datos.nota(), 1,
                ReglasDeLaResena.MAX_NOTA_REVISION, "La nota de la revisión");
        boolean ocultar = OCULTAR.equals(decision);
        Instant ahora = reloj.ahora();

        Resena resena = laResenaDelReporte(reporte);
        String empresa = organizaciones.findById(resena.getOrganizacionId())
                .map(Organizacion::getNombre).orElse("");

        if (ReporteResena.RESENA.equals(reporte.getObjeto())) {
            if (ocultar) {
                // Definitivo: deja de verse y de contar para la persona y para las demás
                // empresas. La autora la ve atenuada, con esta nota.
                resena.setOcultadaEn(ahora);
                resena.setNotaOcultacion(nota);
                resenas.save(resena);
            }
            // En los dos casos se le cuenta a quien reportó.
            avisos.reporteResuelto(resena, empresa, reporte.getReportadoPorUsuarioId(), ocultar);
        } else {
            RespuestaResena respuesta = respuestas.findById(reporte.getRespuestaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Respuesta", "id",
                            reporte.getRespuestaId()));
            if (ocultar) {
                respuesta.setOcultadaEn(ahora);
                respuesta.setNotaOcultacion(nota);
                respuestas.save(respuesta);
                avisos.respuestaOcultada(resena, empresa, respuesta.getUsuarioId());
            }
            // Si se mantiene, a la persona no se le avisa: ella no reportó nada.
        }

        reporte.setEstado(ocultar ? ReporteResena.OCULTADA : ReporteResena.MANTENIDA);
        reporte.setResueltoPorUsuarioId(quien.usuarioId());
        reporte.setResueltoEn(ahora);
        reporte.setNotaRevision(nota);
        reportes.save(reporte);

        auditoria.registrar(quien.organizacionId(), quien, "resolver_reporte_resena",
                "reporte_resena", reporte.getId(),
                Map.of("estado", ReporteResena.PENDIENTE),
                Map.of("estado", reporte.getEstado(), "objeto", reporte.getObjeto()), null);
        return tarjetas(List.of(reporte)).get(0);
    }

    /**
     * La reseña del reporte, por id suelto: la moderación es de la plataforma y cruza
     * empresas por diseño. Quien llega aquí ya pasó {@link #exigirPlataforma}.
     */
    private Resena laResenaDelReporte(ReporteResena reporte) {
        return resenas.findById(reporte.getResenaId())
                .orElseThrow(() -> new ResourceNotFoundException("Reseña", "id",
                        reporte.getResenaId()));
    }

    private List<ReporteParaModerar> tarjetas(List<ReporteResena> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, Resena> resenaPorId = resenas.findAllById(
                        lista.stream().map(ReporteResena::getResenaId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Resena::getId, Function.identity()));
        Map<Long, RespuestaResena> respuestaPorId = respuestas.findAllById(
                        lista.stream().map(ReporteResena::getRespuestaId).filter(Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(RespuestaResena::getId, Function.identity()));
        Map<Long, String> empresas = organizaciones.findAllById(resenaPorId.values().stream()
                        .map(Resena::getOrganizacionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Organizacion::getId, Organizacion::getNombre));
        Map<Long, String> nombres = personas.findAllById(resenaPorId.values().stream()
                        .map(Resena::getPersonaId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Persona::getId,
                        ServicioModeracionResenasImpl::nombreDe));

        return lista.stream().map(reporte -> {
            Resena resena = resenaPorId.get(reporte.getResenaId());
            RespuestaResena respuesta = reporte.getRespuestaId() == null ? null
                    : respuestaPorId.get(reporte.getRespuestaId());
            return new ReporteParaModerar(reporte.getId(), reporte.getObjeto(),
                    empresas.get(resena.getOrganizacionId()),
                    nombres.getOrDefault(resena.getPersonaId(), NombresDeUsuarios.ANONIMO),
                    resena.getEstrellas(), resena.getTexto(),
                    respuesta == null ? null : respuesta.getTexto(),
                    reporte.getMotivo(), reporte.getComentario(), reporte.getReportadoEn(),
                    reporte.getEstado(), reporte.getResueltoEn(), reporte.getNotaRevision());
        }).toList();
    }

    /** El nombre con la misma regla de anonimización que el resto del panel. */
    private static String nombreDe(Persona persona) {
        if (persona.getAnonimizadoEn() != null) {
            return NombresDeUsuarios.ANONIMO;
        }
        String completo = ((persona.getNombre() == null ? "" : persona.getNombre()) + " "
                + (persona.getApellidos() == null ? "" : persona.getApellidos())).trim();
        return completo.isEmpty() ? NombresDeUsuarios.ANONIMO : completo;
    }

    // 403 y no 404: no se pide un recurso que fingir inexistente, se pide una capacidad, y la
    // capacidad es de la dueña de la plataforma.
    private void exigirPlataforma(ContextoUsuario quien) {
        Long plataforma = organizaciones.findByEsPlataformaTrue()
                .map(Organizacion::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Ninguna organización está marcada como plataforma"));
        if (!plataforma.equals(quien.organizacionId())) {
            throw new AccessDeniedException("Las reseñas reportadas las revisa la plataforma");
        }
    }
}
