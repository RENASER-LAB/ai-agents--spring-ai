package com.renaser.ai.ai_engine.notificacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.notificacion.repository.AvisoPortalRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La campana del portal: publicar avisos y apagarlos (V56).
 *
 * <p>Vive junto a {@link ServicioCorreo} porque es su pareja: casi todo lo que merece un
 * correo merece también quedarse esperando dentro. Los dos salen del mismo hecho y ninguno
 * sustituye al otro — el correo alcanza a quien no vuelve, el aviso alcanza a quien sí.
 *
 * <p>⚠️ <b>Publicar un aviso nunca puede tumbar lo que lo provocó.</b> Igual que con el
 * correo: si la vacante cambió de sueldo, el cambio está hecho y deshacerlo porque no se
 * pudo escribir la noticia sería peor que quedarse sin noticia. Por eso {@link #publicar}
 * atrapa lo suyo y lo anota.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioAvisosPortal {

    private final AvisoPortalRepository avisos;

    /**
     * Deja un aviso esperando a alguien.
     *
     * <p>⚠️ <b>En transacción propia, y el {@code REQUIRES_NEW} es la mitad de la garantía.</b>
     * Sin él, el {@code catch} de abajo no protege nada: un INSERT que falla dentro de la
     * transacción del llamador la deja marcada {@code rollback-only} antes de lanzar, así que
     * atrapar la excepción no la rescata y el commit revienta después con un
     * {@code UnexpectedRollbackException} — llevándose por delante el cambio de sueldo y su
     * fila de auditoría, que es exactamente lo que este método promete no hacer.
     *
     * <p>El precio es el de siempre con {@code REQUIRES_NEW}: el aviso se compromete antes
     * que lo que lo provocó, así que si el cambio de sueldo se deshace después, el aviso se
     * queda. Es el lado correcto del que equivocarse — un aviso de más se explica; un sueldo
     * revertido en silencio, no.
     *
     * @return el aviso guardado, o {@code null} si no se pudo — nunca lanza.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AvisoPortal publicar(Long organizacionId, Long usuarioId, String tipo,
                                String titulo, String cuerpo,
                                Long postulacionId, Long vacanteId) {
        try {
            return avisos.save(AvisoPortal.builder()
                    .usuarioId(usuarioId)
                    .organizacionId(organizacionId)
                    .tipo(tipo)
                    .titulo(titulo)
                    .cuerpo(cuerpo)
                    .postulacionId(postulacionId)
                    .vacanteId(vacanteId)
                    .creadoEn(Instant.now())
                    .build());
        } catch (RuntimeException e) {
            log.error("No se pudo publicar el aviso «{}» para el usuario {}: {}",
                    tipo, usuarioId, e.getMessage());
            return null;
        }
    }

    /** Todos los suyos, los nuevos arriba. */
    public List<AvisoPortal> mios(Long usuarioId) {
        return avisos.findByUsuarioIdOrderByCreadoEnDesc(usuarioId);
    }

    /** Cuántos le quedan sin ver: el número del punto. */
    public long sinLeer(Long usuarioId) {
        return avisos.countByUsuarioIdAndLeidoEnIsNull(usuarioId);
    }

    /**
     * Qué postulaciones suyas tienen algo sin leer, y cuántos avisos cada una.
     *
     * <p>Es lo que pinta el punto de cada fila en «mis postulaciones». Una sola consulta para
     * toda la lista: preguntar por cada fila multiplicaría las consultas por el número de
     * procesos que lleva la persona, en la pantalla que abre siempre que entra.
     */
    public Map<Long, Long> sinLeerPorPostulacion(Long usuarioId, List<Long> postulacionIds) {
        if (postulacionIds == null || postulacionIds.isEmpty()) {
            return Map.of();
        }
        return avisos
                .findByUsuarioIdAndPostulacionIdInAndLeidoEnIsNull(usuarioId, postulacionIds)
                .stream()
                .collect(Collectors.groupingBy(AvisoPortal::getPostulacionId,
                        Collectors.counting()));
    }

    /**
     * Apaga el punto: marca leídos todos los que le quedaban.
     *
     * @return cuántos se apagaron
     */
    public int marcarTodosLeidos(Long usuarioId) {
        return avisos.marcarTodosLeidos(usuarioId, Instant.now());
    }

    /**
     * Apaga uno solo, el suyo.
     *
     * <p>Comprueba el dueño antes de tocarlo: un id de aviso es un número secuencial, y sin
     * esta línea cualquiera podría marcar leídos los de otra persona probando números. No
     * revela nada al fallar —se ignora en silencio— porque tampoco hay nada que el candidato
     * deba hacer distinto si el aviso no era suyo.
     */
    public void marcarLeido(Long usuarioId, Long avisoId) {
        avisos.findById(avisoId)
                .filter(a -> a.getUsuarioId().equals(usuarioId))
                .filter(a -> a.getLeidoEn() == null)
                .ifPresent(a -> {
                    a.setLeidoEn(Instant.now());
                    avisos.save(a);
                });
    }

    /** Un aviso por id, tolerando que no exista. Para las pruebas y el detalle. */
    public Map<Long, AvisoPortal> porId(List<Long> ids) {
        return avisos.findAllById(ids).stream()
                .collect(Collectors.toMap(AvisoPortal::getId, Function.identity()));
    }
}
