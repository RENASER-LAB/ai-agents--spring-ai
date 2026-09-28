package com.renaser.ai.ai_engine.resena.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.EscribirResena;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.LaResenaDeMiEmpresa;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.Reportar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenaDeMiEmpresa;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenaVisible;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenasDeLaPostulacion;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.RespuestaParaLaAutora;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResumenResenas;
import com.renaser.ai.ai_engine.resena.entity.ReporteResena;
import com.renaser.ai.ai_engine.resena.entity.Resena;
import com.renaser.ai.ai_engine.resena.entity.RespuestaResena;
import com.renaser.ai.ai_engine.resena.exception.ContratacionFueraDeAlcanceException;
import com.renaser.ai.ai_engine.resena.repository.ReporteResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.ResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.RespuestaResenaRepository;
import com.renaser.ai.ai_engine.resena.service.AvisosDeResenas;
import com.renaser.ai.ai_engine.resena.service.LectorDeResenas;
import com.renaser.ai.ai_engine.resena.service.ReglasDeLaResena;
import com.renaser.ai.ai_engine.resena.service.RelojDeResenas;
import com.renaser.ai.ai_engine.resena.service.ServicioResenasPanel;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.renaser.ai.ai_engine.resena.service.ReglasDeLaResena.CONTRATADO;
import static com.renaser.ai.ai_engine.resena.service.ReglasDeLaResena.RESENAR;
import static com.renaser.ai.ai_engine.resena.service.ReglasDeLaResena.VER;

/**
 * Ver {@link ServicioResenasPanel}.
 *
 * <p>Las reglas que aplica, y dónde:
 * <ul>
 *   <li>Quién: la postulación se busca en la empresa de quien pregunta (lo ajeno es un 404
 *       que no confirma nada) y el alcance lo decide {@link AlcanceSobreLaVacante}; lo que
 *       es de la empresa pero no le llega es otro 404, que sí dice «fuera de tu alcance».</li>
 *   <li>Cuándo: la contratación es la transición a {@code CONTRATADO}; se escribe a partir de
 *       los 30 días y se cambia durante los 30 días siguientes a la primera publicación.
 *       Manda {@link RelojDeResenas}.</li>
 *   <li>Una por contratación: lo garantiza el índice parcial de la base; aquí se pregunta
 *       antes para contestar un 409 con su porqué, y se traduce la carrera que se cuele.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ServicioResenasPanelImpl implements ServicioResenasPanel {

    private static final String EN_REVISION = "EN_REVISION";

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final AlcanceSobreLaVacante alcance;
    private final TransicionEstadoRepository transiciones;
    private final UsuarioRepository usuarios;
    private final OrganizacionRepository organizaciones;
    private final NombresDeUsuarios nombres;
    private final ResenaRepository resenas;
    private final RespuestaResenaRepository respuestas;
    private final ReporteResenaRepository reportes;
    private final LectorDeResenas lector;
    private final AvisosDeResenas avisos;
    private final ServicioAuditoria auditoria;
    private final RelojDeResenas reloj;

    // ============ Leer ============

    @Override
    @Transactional(readOnly = true)
    public ResenasDeLaPostulacion ver(ContextoUsuario quien, Long postulacionId) {
        Postulacion p = laDeMiEmpresa(quien, postulacionId);
        boolean alcanzaVer = alcanza(quien, VER, p);
        boolean alcanzaResenar = alcanza(quien, RESENAR, p);
        if (!alcanzaVer && !alcanzaResenar) {
            throw new ContratacionFueraDeAlcanceException(postulacionId);
        }

        ResumenResenas resumen = null;
        List<ResenaVisible> lista = null;
        if (alcanzaVer) {
            // La lectura entre empresas: por la persona de ESTA postulación, que quien
            // pregunta ya puede ver. Todas las empresas, también la propia.
            List<Resena> visibles = lector.visiblesDe(personaDe(p));
            resumen = lector.resumen(visibles);
            lista = lector.paraLeer(visibles);
        }
        boolean puedeResenar = alcanzaResenar && CONTRATADO.equals(p.getEstadoCodigo());
        return new ResenasDeLaPostulacion(nombres.de(p.getUsuarioId()), alcanzaVer, resumen,
                lista, puedeResenar, puedeResenar ? bloqueDeMiEmpresa(quien, p) : null);
    }

    // ============ Escribir ============

    @Override
    @Transactional
    public LaResenaDeMiEmpresa publicar(ContextoUsuario quien, Long postulacionId,
                                        EscribirResena datos) {
        Postulacion p = laQuePuedoResenar(quien, postulacionId);
        int estrellas = ReglasDeLaResena.estrellasValidas(datos == null ? null : datos.estrellas());
        String texto = ReglasDeLaResena.textoValido(datos.texto(), ReglasDeLaResena.MIN_RESENA,
                ReglasDeLaResena.MAX_RESENA, "La opinión");

        Instant ahora = reloj.ahora();
        Instant abreEn = ReglasDeLaResena.abreEn(contratadoEn(p));
        if (!ReglasDeLaResena.yaLlego(abreEn, ahora)) {
            throw new IllegalStateException("Todavía no se puede reseñar: podrás hacerlo desde "
                    + "el " + ReglasDeLaResena.fecha(abreEn)
                    + ", al cumplir un mes de su contratación");
        }
        if (resenas.findByPostulacionIdAndBorradaEnIsNull(p.getId()).isPresent()) {
            throw yaTieneResena();
        }

        Resena nueva = Resena.builder()
                .postulacionId(p.getId())
                .organizacionId(p.getOrganizacionId())
                .personaId(personaDe(p))
                .estrellas(estrellas)
                .texto(texto)
                .escritaPorUsuarioId(quien.usuarioId())
                .publicadaEn(ahora)
                .build();
        try {
            // Con flush: si otra persona de la misma empresa publicó en el mismo instante,
            // el índice salta AQUÍ y no al confirmar, y el aviso no llega a salir.
            nueva = resenas.saveAndFlush(nueva);
        } catch (DataIntegrityViolationException e) {
            throw yaTieneResena();
        }

        // Sin el texto: la auditoría no se puede borrar, y un texto guardado allí
        // sobreviviría al borrado de datos de la persona.
        auditoria.registrar(p.getOrganizacionId(), quien, "publicar_resena", "resena",
                nueva.getId(), null,
                Map.of("postulacionId", p.getId(), "estrellas", estrellas), null);
        avisos.publicada(nueva, nombreDeLaEmpresa(p.getOrganizacionId()), p.getUsuarioId());
        return bloqueDeMiEmpresa(quien, p);
    }

    @Override
    @Transactional
    public LaResenaDeMiEmpresa editar(ContextoUsuario quien, Long postulacionId,
                                      EscribirResena datos) {
        Postulacion p = laQuePuedoResenar(quien, postulacionId);
        Resena resena = laVivaQueSePuedeCambiar(p);
        int estrellas = ReglasDeLaResena.estrellasValidas(datos == null ? null : datos.estrellas());
        String texto = ReglasDeLaResena.textoValido(datos.texto(), ReglasDeLaResena.MIN_RESENA,
                ReglasDeLaResena.MAX_RESENA, "La opinión");

        // Guardar lo mismo no es editar: ni marca «Editada» ni le mueve el plazo a nadie.
        if (resena.getEstrellas() == estrellas && resena.getTexto().equals(texto)) {
            return bloqueDeMiEmpresa(quien, p);
        }

        Instant ahora = reloj.ahora();
        int antes = resena.getEstrellas();
        resena.setEstrellas(estrellas);
        resena.setTexto(texto);
        resena.setEditadaEn(ahora);
        resenas.save(resena);
        auditoria.registrar(p.getOrganizacionId(), quien, "editar_resena", "resena",
                resena.getId(), Map.of("estrellas", antes), Map.of("estrellas", estrellas),
                null);

        // Si ya la había respondido, su versión puede haber quedado hablando de otra cosa: se
        // le da un mes entero DESDE ESTA EDICIÓN para ajustarla, y se le avisa. Una respuesta
        // ocultada no se puede tocar, así que ni plazo ni aviso.
        respuestas.findByResenaIdAndBorradaEnIsNull(resena.getId())
                .filter(r -> r.getOcultadaEn() == null)
                .ifPresent(respuesta -> {
                    Instant hasta = ahora.plus(ReglasDeLaResena.UN_MES);
                    respuesta.setEditableHasta(hasta);
                    respuestas.save(respuesta);
                    avisos.editadaYaRespondida(resena,
                            nombreDeLaEmpresa(p.getOrganizacionId()), respuesta.getUsuarioId(),
                            hasta);
                });
        return bloqueDeMiEmpresa(quien, p);
    }

    @Override
    @Transactional
    public LaResenaDeMiEmpresa borrar(ContextoUsuario quien, Long postulacionId) {
        Postulacion p = laQuePuedoResenar(quien, postulacionId);
        Resena resena = laVivaQueSePuedeCambiar(p);
        Instant ahora = reloj.ahora();

        resena.setBorradaEn(ahora);
        // Con flush: la contratación queda libre en el acto, y el índice de «una viva» no
        // puede tropezar con esta fila si alguien publica la siguiente enseguida.
        resenas.saveAndFlush(resena);

        // La respuesta va con su reseña: se borra con ella, sin aviso.
        respuestas.findByResenaIdAndBorradaEnIsNull(resena.getId()).ifPresent(respuesta -> {
            respuesta.setBorradaEn(ahora);
            respuestas.save(respuesta);
        });
        // Lo que estaba pendiente de revisar se cierra: ya no hay nada que juzgar.
        reportes.findByResenaIdInOrderByReportadoEnDesc(List.of(resena.getId())).stream()
                .filter(r -> ReporteResena.PENDIENTE.equals(r.getEstado()))
                .forEach(r -> {
                    r.setEstado(ReporteResena.RETIRADA);
                    r.setResueltoEn(ahora);
                    reportes.save(r);
                });

        auditoria.registrar(p.getOrganizacionId(), quien, "borrar_resena", "resena",
                resena.getId(), Map.of("postulacionId", p.getId(),
                        "estrellas", resena.getEstrellas()), null, null);
        return bloqueDeMiEmpresa(quien, p);
    }

    @Override
    @Transactional
    public LaResenaDeMiEmpresa reportarRespuesta(ContextoUsuario quien, Long postulacionId,
                                                 Reportar datos) {
        Postulacion p = laQuePuedoResenar(quien, postulacionId);
        Resena resena = resenas.findByPostulacionIdAndBorradaEnIsNull(p.getId())
                .filter(r -> r.getOcultadaEn() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Reseña", "postulación",
                        postulacionId));
        RespuestaResena respuesta = respuestas.findByResenaIdAndBorradaEnIsNull(resena.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Respuesta", "reseña",
                        resena.getId()));
        if (respuesta.getOcultadaEn() != null) {
            throw new IllegalStateException("La plataforma ya ocultó esta respuesta");
        }
        String motivo = ReglasDeLaResena.motivoValido(datos == null ? null : datos.motivo());
        String comentario = ReglasDeLaResena.comentarioValido(motivo, datos.comentario());

        List<ReporteResena> deLaRespuesta = reportesDeLaRespuesta(respuesta);
        if (!deLaRespuesta.isEmpty()) {
            ReporteResena ultimo = deLaRespuesta.get(0);
            if (ReporteResena.PENDIENTE.equals(ultimo.getEstado())) {
                throw new IllegalStateException("Ya reportaste esta respuesta: la plataforma "
                        + "la está revisando");
            }
            if (!seEditoDespues(respuesta.getEditadaEn(), ultimo)) {
                throw new IllegalStateException("La plataforma ya la revisó y la mantuvo. "
                        + "Podrás reportarla otra vez si la persona la edita");
            }
        }

        ReporteResena reporte = ReporteResena.builder()
                .resenaId(resena.getId())
                .respuestaId(respuesta.getId())
                .objeto(ReporteResena.RESPUESTA)
                .reportadoPorUsuarioId(quien.usuarioId())
                .organizacionReportanteId(quien.organizacionId())
                .motivo(motivo)
                .comentario(comentario)
                .reportadoEn(reloj.ahora())
                .estado(ReporteResena.PENDIENTE)
                .build();
        try {
            reporte = reportes.saveAndFlush(reporte);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Ya reportaste esta respuesta: la plataforma la "
                    + "está revisando");
        }
        // Ni el comentario ni el texto: solo que hubo un reporte y por qué motivo.
        auditoria.registrar(p.getOrganizacionId(), quien, "reportar_respuesta_resena",
                "reporte_resena", reporte.getId(), null,
                Map.of("resenaId", resena.getId(), "motivo", motivo), null);
        return bloqueDeMiEmpresa(quien, p);
    }

    // ============ El bloque de la empresa autora ============

    private LaResenaDeMiEmpresa bloqueDeMiEmpresa(ContextoUsuario quien, Postulacion p) {
        Instant ahora = reloj.ahora();
        Instant contratadoEn = contratadoEn(p);
        Instant abreEn = ReglasDeLaResena.abreEn(contratadoEn);
        String empresa = nombreDeLaEmpresa(p.getOrganizacionId());
        String puesto = vacantes.findByIdAndOrganizacionId(p.getVacanteId(), p.getOrganizacionId())
                .map(Vacante::getTitulo).orElse(null);

        Optional<Resena> viva = resenas.findByPostulacionIdAndBorradaEnIsNull(p.getId());
        if (viva.isEmpty()) {
            String estado = ReglasDeLaResena.yaLlego(abreEn, ahora)
                    ? LaResenaDeMiEmpresa.SE_PUEDE_ESCRIBIR
                    : LaResenaDeMiEmpresa.AUN_NO_TOCA;
            return new LaResenaDeMiEmpresa(estado, empresa, puesto, contratadoEn, abreEn, null);
        }

        Resena r = viva.get();
        Instant editableHasta = ReglasDeLaResena.editableHasta(r.getPublicadaEn());
        String estado;
        if (r.getOcultadaEn() != null) {
            estado = LaResenaDeMiEmpresa.OCULTADA;
        } else if (!ReglasDeLaResena.yaLlego(editableHasta, ahora)) {
            estado = LaResenaDeMiEmpresa.EDITABLE;
        } else {
            estado = LaResenaDeMiEmpresa.FIJA;
        }
        // Si la plataforma ocultó la reseña, la respuesta deja de verse con ella.
        RespuestaParaLaAutora respuesta = r.getOcultadaEn() != null ? null
                : respuestas.findByResenaIdAndBorradaEnIsNull(r.getId())
                        .map(this::paraLaAutora).orElse(null);
        return new LaResenaDeMiEmpresa(estado, empresa, puesto, contratadoEn, abreEn,
                new ResenaDeMiEmpresa(r.getId(), r.getEstrellas(), r.getTexto(),
                        r.getPublicadaEn(), r.getEditadaEn() != null, editableHasta,
                        r.getNotaOcultacion(), respuesta));
    }

    /**
     * La respuesta como la ve la empresa autora, con el estado de su reporte.
     *
     * <p>Se puede reportar una vez; si la plataforma la mantuvo y la persona la editó
     * después, otra vez. Ocultada es definitivo.
     */
    private RespuestaParaLaAutora paraLaAutora(RespuestaResena respuesta) {
        boolean editada = respuesta.getEditadaEn() != null;
        if (respuesta.getOcultadaEn() != null) {
            return new RespuestaParaLaAutora(respuesta.getTexto(), respuesta.getPublicadaEn(),
                    editada, true, ReporteResena.OCULTADA, respuesta.getNotaOcultacion(), false);
        }
        List<ReporteResena> deLaRespuesta = reportesDeLaRespuesta(respuesta);
        if (deLaRespuesta.isEmpty()) {
            return new RespuestaParaLaAutora(respuesta.getTexto(), respuesta.getPublicadaEn(),
                    editada, false, null, null, true);
        }
        ReporteResena ultimo = deLaRespuesta.get(0);
        if (ReporteResena.PENDIENTE.equals(ultimo.getEstado())) {
            return new RespuestaParaLaAutora(respuesta.getTexto(), respuesta.getPublicadaEn(),
                    editada, false, EN_REVISION, null, false);
        }
        if (seEditoDespues(respuesta.getEditadaEn(), ultimo)) {
            return new RespuestaParaLaAutora(respuesta.getTexto(), respuesta.getPublicadaEn(),
                    editada, false, null, null, true);
        }
        return new RespuestaParaLaAutora(respuesta.getTexto(), respuesta.getPublicadaEn(),
                editada, false, ultimo.getEstado(), ultimo.getNotaRevision(), false);
    }

    private List<ReporteResena> reportesDeLaRespuesta(RespuestaResena respuesta) {
        return reportes.findByResenaIdInOrderByReportadoEnDesc(List.of(respuesta.getResenaId()))
                .stream()
                .filter(r -> respuesta.getId().equals(r.getRespuestaId()))
                .toList();
    }

    /** Si lo reportado cambió después de que la plataforma lo mantuviera. */
    static boolean seEditoDespues(Instant editadaEn, ReporteResena resuelto) {
        return ReporteResena.MANTENIDA.equals(resuelto.getEstado())
                && editadaEn != null && resuelto.getResueltoEn() != null
                && editadaEn.isAfter(resuelto.getResueltoEn());
    }

    // ============ Quién y qué ============

    /**
     * La postulación de mi empresa. Lo de otra empresa, y lo que no existe, responden el
     * mismo 404: no se confirma nada.
     */
    private Postulacion laDeMiEmpresa(ContextoUsuario quien, Long postulacionId) {
        return postulaciones.findByIdAndOrganizacionId(postulacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id",
                        postulacionId));
    }

    /**
     * La contratación que puedo reseñar: de mi empresa, al alcance de
     * {@code resenar_contratado}, con su vacante viva y en {@code CONTRATADO}.
     */
    private Postulacion laQuePuedoResenar(ContextoUsuario quien, Long postulacionId) {
        Postulacion p = laDeMiEmpresa(quien, postulacionId);
        if (!alcanza(quien, RESENAR, p)) {
            throw new ContratacionFueraDeAlcanceException(postulacionId);
        }
        alcance.exigirQueSuVacanteSigaExistiendo(p);
        if (!CONTRATADO.equals(p.getEstadoCodigo())) {
            throw new IllegalStateException("Solo se reseña a quien se contrató: esta "
                    + "postulación no está en «Contratado»");
        }
        return p;
    }

    /** La reseña viva de mi empresa, si todavía se puede editar o borrar. */
    private Resena laVivaQueSePuedeCambiar(Postulacion p) {
        Resena resena = resenas.findByPostulacionIdAndBorradaEnIsNull(p.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Reseña", "postulación",
                        p.getId()));
        if (resena.getOcultadaEn() != null) {
            throw new IllegalStateException("La plataforma ocultó esta reseña: ya no se puede "
                    + "cambiar");
        }
        Instant hasta = ReglasDeLaResena.editableHasta(resena.getPublicadaEn());
        if (ReglasDeLaResena.yaLlego(hasta, reloj.ahora())) {
            throw new IllegalStateException("Ya no se puede cambiar: el plazo para editarla o "
                    + "borrarla terminó el " + ReglasDeLaResena.fecha(hasta));
        }
        return resena;
    }

    /**
     * Si el permiso llega a esta postulación. Quién alcanza qué lo decide el guardián de
     * siempre; aquí solo se le pregunta con el alcance de ESTE permiso.
     */
    private boolean alcanza(ContextoUsuario quien, String permiso, Postulacion p) {
        return quien.tiene(permiso) && alcance.alcanzaA(quien,
                FiltroAlcance.desde(quien.alcance(permiso), quien.usuarioId()), p);
    }

    /**
     * Cuándo se contrató: la transición a {@code CONTRATADO}, venga de la decisión o de la
     * transición manual. Si una fila antigua no la tuviera, cuenta el último movimiento.
     */
    private Instant contratadoEn(Postulacion p) {
        return transiciones
                .findFirstByPostulacionIdAndEstadoNuevoCodigoOrderByOcurridaEnDesc(p.getId(),
                        CONTRATADO)
                .map(TransicionEstado::getOcurridaEn)
                .orElseGet(() -> p.getMovidoEn() != null ? p.getMovidoEn() : p.getCreadoEn());
    }

    private Long personaDe(Postulacion p) {
        return usuarios.findById(p.getUsuarioId())
                .map(Usuario::getPersonaId)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", p.getId()));
    }

    private String nombreDeLaEmpresa(Long organizacionId) {
        return organizaciones.findById(organizacionId).map(Organizacion::getNombre).orElse("");
    }

    private static IllegalStateException yaTieneResena() {
        return new IllegalStateException("Esta contratación ya tiene una reseña de tu empresa");
    }
}
