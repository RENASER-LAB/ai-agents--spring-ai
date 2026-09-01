package com.renaser.ai.ai_engine.administracion.service.impl;

import com.renaser.ai.ai_engine.administracion.dto.DtosAdministracion.SolicitudBorradoPanel;
import com.renaser.ai.ai_engine.administracion.service.ServicioBorradoDatos;
import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.consentimiento.entity.SolicitudBorrado;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.SolicitudBorradoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.CorreoEnviadoRepository;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EnlaceCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ver {@link ServicioBorradoDatos}.
 *
 * <p>El borrado de la ley 29733 es DE LA PLATAFORMA: los candidatos son cuentas de la
 * plataforma y sus postulaciones cruzan empresas, así que la anonimización toca datos
 * de varias. Una empresa que pudiera ejecutarlo estaría borrando candidatos ajenos —
 * era la peor de las fugas: destructiva, no de lectura.
 */
@Service
@RequiredArgsConstructor
public class ServicioBorradoDatosImpl implements ServicioBorradoDatos {

    private final SolicitudBorradoRepository solicitudesBorrado;
    private final PersonaRepository personas;
    private final com.renaser.ai.ai_engine.perfil.service.ServicioCicloVidaPerfil cicloVidaPerfil;
    private final UsuarioRepository usuarios;
    private final OrganizacionRepository organizaciones;
    private final ConsentimientoRepository consentimientos;
    private final PostulacionRepository postulaciones;
    private final EstadoPostulacionRepository estados;
    private final CvRepository cvs;
    private final EnlaceCvRepository enlaces;
    private final ArchivoRepository archivos;
    private final CorreoEnviadoRepository correosEnviados;
    private final AlmacenArchivos almacen;
    private final MaquinaEstados maquina;
    private final ServicioCorreo correo;
    private final ServicioAuditoria auditoria;

    @Override
    public List<SolicitudBorradoPanel> solicitudesBorradoPendientes(ContextoUsuario quien) {
        exigirPlataforma(quien);
        return solicitudesBorrado.findByEjecutadoEnIsNullOrderBySolicitadoEnAsc().stream()
                .map(s -> new SolicitudBorradoPanel(s.getId(), s.getPersonaId(), s.getMotivo(),
                        s.getSolicitadoEn(), s.getEjecutadoEn()))
                .toList();
    }

    // La anonimización completa, en el orden que importa: primero el aviso (todavía hay
    // correo), después el vaciado, y la trazabilidad queda intacta pero sin nombre.
    @Override
    @Transactional
    public void ejecutarBorrado(ContextoUsuario quien, Long solicitudId) {
        exigirPlataforma(quien);
        SolicitudBorrado solicitud = solicitudesBorrado.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de borrado", "id", solicitudId));
        if (solicitud.getEjecutadoEn() != null) {
            throw new IllegalStateException("Esta solicitud ya fue ejecutada");
        }
        Persona persona = personas.findById(solicitud.getPersonaId())
                .orElseThrow(() -> new ResourceNotFoundException("Persona", "id", solicitud.getPersonaId()));
        Usuario usuario = usuarios.findFirstByPersonaId(persona.getId()).orElse(null);

        // 1 · El aviso sale antes del vaciado, mientras el correo todavía existe
        if (usuario != null && usuario.getCorreo() != null) {
            correo.enviar(quien.organizacionId(), usuario.getId(), usuario.getCorreo(),
                    "BORRADO_EJECUTADO", Map.of());
        }

        // 2 · Las postulaciones activas se cierran con su motivo
        if (usuario != null) {
            for (Postulacion p : postulaciones.findByUsuarioIdOrderByCreadoEnDesc(usuario.getId())) {
                boolean esFinal = estados.findById(p.getEstadoCodigo())
                        .map(EstadoPostulacion::isEsFinal).orElse(true);
                if (!esFinal) {
                    maquina.transicionar(p, "CERRADA", quien,
                            "La persona pidió el borrado de sus datos", false, false, "BORRADO_DATOS");
                }
                // 3 · El CV: el archivo físico se borra, el texto y los enlaces se vacían
                cvs.findByPostulacionId(p.getId()).ifPresent(cv -> {
                    if (cv.getArchivoOriginalId() != null) {
                        archivos.findById(cv.getArchivoOriginalId()).ifPresent(almacen::borrarContenido);
                    }
                    cv.setTextoExtraido(null);
                    cv.setResultadoOrgulloso(null);
                    cvs.save(cv);
                    enlaces.findByCvId(cv.getId()).forEach(e -> {
                        e.setUrl("[eliminado por solicitud de borrado]");
                        enlaces.save(e);
                    });
                });
            }
        }

        // 4 · Los correos enviados conservan la fila pero pierden el contenido personal
        if (usuario != null) {
            correosEnviados.findByUsuarioIdOrderByEnviadoEnDesc(usuario.getId()).forEach(c -> {
                c.setAsunto("[eliminado por solicitud de borrado]");
                c.setCuerpo("[eliminado por solicitud de borrado]");
                correosEnviados.save(c);
            });
        }

        // 5 · El consentimiento pierde el nombre registrado
        consentimientos.findByPersonaId(persona.getId()).forEach(c -> {
            c.setNombreRegistrado(null);
            consentimientos.save(c);
        });

        // 5b · El perfil del candidato se borra entero, y de verdad — no se anonimiza.
        // No sostiene ninguna decisión (no puntúa), así que no hay nada que conservar.
        // Antes de vaciar la persona, porque se localiza por persona_id.
        cicloVidaPerfil.borrarPorPersona(persona.getId());

        // 6 · La persona queda vacía, la cuenta sin correo y desactivada
        persona.setNombre(null);
        persona.setApellidos(null);
        persona.setTelefono(null);
        persona.setDocumento(null);
        persona.setFechaNacimiento(null);
        // La ciudad también: dice dónde vive alguien, y eso es dato personal aunque venga de
        // una lista cerrada. Con la provincia y el resto del expediente —vacante, fechas,
        // notas— se vuelve a señalar a una persona concreta, que es justo lo que anonimizar
        // tiene que impedir.
        persona.setCiudadUbigeo(null);
        persona.setAnonimizadoEn(Instant.now());
        personas.save(persona);
        if (usuario != null) {
            usuario.setCorreo(null);
            usuario.setEsActivo(false);
            usuarios.save(usuario);
        }

        solicitud.setEjecutadoEn(Instant.now());
        solicitud.setEjecutadoPorUsuarioId(quien.usuarioId());
        solicitudesBorrado.save(solicitud);

        auditoria.registrar(quien.organizacionId(), quien, "ejecutar_borrado_datos",
                "persona", persona.getId(), null, Map.of("anonimizada", true), solicitud.getMotivo());
    }

    // 403 y no 404: estos endpoints no piden un recurso concreto que fingir inexistente,
    // piden una capacidad — y la capacidad es de la dueña de la plataforma.
    private void exigirPlataforma(ContextoUsuario quien) {
        Organizacion plataforma = organizaciones.findByEsPlataformaTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "Ninguna organización está marcada como plataforma"));
        if (!plataforma.getId().equals(quien.organizacionId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "El borrado de datos de candidatos lo ejecuta la plataforma");
        }
    }
}
