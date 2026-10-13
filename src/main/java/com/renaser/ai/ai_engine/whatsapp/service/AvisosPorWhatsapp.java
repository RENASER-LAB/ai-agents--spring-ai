package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.service.AvisoDeEtapaEnLaCampana;
import com.renaser.ai.ai_engine.postulacion.service.TextosDelMomento;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.whatsapp.entity.AvisoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.AvisoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Lo que la máquina de estados y el borrado de datos le cuentan a WhatsApp (V73).
 *
 * <ul>
 *   <li><b>Un «te toca»</b> ({@code PRUEBA_DISPONIBLE} o {@code POSTULACION_AVANZA}) con un
 *       vínculo ACTIVO deja un aviso en la cola, ligado a su transición. Lo manda después
 *       {@link EnvioAlBot}.</li>
 *   <li><b>Un estado final</b> cierra el vínculo, sin avisar por WhatsApp: los cierres no van
 *       por aquí.</li>
 *   <li><b>El borrado de datos</b> se lleva el número y los vínculos.</li>
 * </ul>
 *
 * <p>⚠️ Los dos primeros ocurren <b>después de que la transición se confirme</b> y en su propia
 * transacción, como la campana ({@link AvisoDeEtapaEnLaCampana#alConfirmar}): un aviso de una
 * transición que se deshace sería mentira, y un fallo aquí no puede deshacer la transición ni
 * quitarle el correo y la campana al candidato. Si algo falla, se anota y se sigue.
 */
@Component
@Slf4j
public class AvisosPorWhatsapp {

    /** Los únicos que van por WhatsApp: los que le dicen que le toca algo. */
    static final Set<String> LOS_TE_TOCA =
            Set.of(AvisoPortal.PRUEBA_DISPONIBLE, AvisoPortal.POSTULACION_AVANZA);

    private final VinculoWhatsappRepository vinculos;
    private final AvisoWhatsappRepository avisos;
    private final BajaWhatsappRepository bajas;
    private final PersonaRepository personas;
    private final BetaWhatsapp beta;
    private final EnvioAlBot envio;
    private final TransactionTemplate enSuTransaccion;

    public AvisosPorWhatsapp(VinculoWhatsappRepository vinculos, AvisoWhatsappRepository avisos,
                             BajaWhatsappRepository bajas, PersonaRepository personas,
                             BetaWhatsapp beta, EnvioAlBot envio,
                             PlatformTransactionManager transacciones) {
        this.vinculos = vinculos;
        this.avisos = avisos;
        this.bajas = bajas;
        this.personas = personas;
        this.beta = beta;
        this.envio = envio;
        this.enSuTransaccion = new TransactionTemplate(transacciones);
        this.enSuTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Lo que hace falta para escribir el aviso: quién, a qué vacante y qué rinde. */
    public record DeLaTransicion(Long transicionId, String nombre, String vacante,
                                 String instrumento) {}

    /**
     * La transición ya mandó su correo y su campana: si es un «te toca» y hay vínculo activo,
     * deja el aviso para el bot. Nunca lanza.
     *
     * @param aviso el aviso que TOCABA (no el texto propio de la vacante), como en la campana
     */
    public void alAvisar(Postulacion postulacion, String aviso, EstadoPostulacion nuevo,
                         DeLaTransicion contexto) {
        if (!LOS_TE_TOCA.contains(aviso) || contexto == null || contexto.transicionId() == null) {
            return;
        }
        Long postulacionId = postulacion.getId();
        Long organizacionId = postulacion.getOrganizacionId();
        String estado = nuevo == null ? null : nuevo.getCodigo();
        AvisoDeEtapaEnLaCampana.alConfirmar(
                () -> enSuTransaccion.executeWithoutResult(t ->
                        encolar(postulacionId, organizacionId, aviso, estado, contexto)),
                "WhatsApp " + aviso, postulacionId);
    }

    /** La postulación llegó a un estado final: su vínculo se cierra. Nunca lanza. */
    public void alTerminar(Postulacion postulacion) {
        Long postulacionId = postulacion.getId();
        AvisoDeEtapaEnLaCampana.alConfirmar(
                () -> enSuTransaccion.executeWithoutResult(t -> cerrarPorFin(postulacionId)),
                "WhatsApp: cierre del vínculo", postulacionId);
    }

    /**
     * El borrado de datos de esta persona: sus vínculos, sus avisos y —si nadie más lo usa— la
     * baja de su número. Dentro de la transacción del borrado, para que vaya todo o nada.
     *
     * <p>Al bot se le cuenta después del commit, una vez: las filas ya no existen para
     * reintentarlo, y su lado tiene su propia retención.
     */
    public void alBorrarLosDatos(Long personaId, String telefonoDeLaCuenta) {
        List<VinculoWhatsapp> suyos = vinculos.findByPersonaId(personaId);
        if (!suyos.isEmpty()) {
            List<Long> ids = suyos.stream().map(VinculoWhatsapp::getId).toList();
            avisos.borrarDe(ids);
            // Al bot solo le importan los que conoce y no se cerraron ya por su lado (BAJA).
            List<VinculoWhatsapp> porContarle = suyos.stream()
                    .filter(v -> v.estaVivo() || v.getRevocacionEnviadaEn() == null)
                    .toList();
            vinculos.deleteAll(suyos);
            vinculos.flush();
            AvisoDeEtapaEnLaCampana.alConfirmar(() -> porContarle.forEach(v -> envio.revocarSinFila(
                    v.getTelefono(), v.getReferencia(), VinculoWhatsapp.POR_BORRADO_DE_DATOS)),
                    "WhatsApp: revocar por borrado de datos", null);
        }
        if (telefonoDeLaCuenta != null && bajas.existsById(telefonoDeLaCuenta)
                && !personas.existsByTelefonoAndIdNot(telefonoDeLaCuenta, personaId)) {
            bajas.deleteById(telefonoDeLaCuenta);
        }
    }

    // ============ dentro de su transacción ============

    void encolar(Long postulacionId, Long organizacionId, String aviso, String estado,
                 DeLaTransicion contexto) {
        if (!beta.ofrecePara(organizacionId)) {
            return;
        }
        // ACTIVO y no PENDIENTE: quien pulsó el botón pero nunca escribió no recibe nada. El
        // bot no puede escribirle primero.
        VinculoWhatsapp vinculo = vinculos.vivoDe(postulacionId)
                .filter(VinculoWhatsapp::estaActivo).orElse(null);
        if (vinculo == null || bajas.existsById(vinculo.getTelefono())
                || avisos.existsByTransicionEstadoIdAndTipo(contexto.transicionId(), aviso)) {
            return;
        }
        boolean esCuestionario = TextosDelMomento.CUESTIONARIO_TECNICO.equals(contexto.instrumento());
        String texto = AvisoPortal.PRUEBA_DISPONIBLE.equals(aviso)
                ? TextosDeWhatsapp.pruebaDisponible(contexto.nombre(), contexto.vacante(), esCuestionario)
                : TextosDeWhatsapp.avance(contexto.nombre(), contexto.vacante(),
                        TextosDelMomento.de(estado, contexto.instrumento(), false));
        Instant ahora = Instant.now();
        avisos.save(AvisoWhatsapp.builder()
                .id(UuidV7.nuevo())
                .postulacionId(postulacionId)
                .vinculoWhatsappId(vinculo.getId())
                .transicionEstadoId(contexto.transicionId())
                .tipo(aviso)
                .estado(AvisoWhatsapp.PENDIENTE)
                .telefono(vinculo.getTelefono())
                .referencia(vinculo.getReferencia())
                // Con los límites del contrato: nombre y vacante hasta 60, el texto hasta 1000.
                .nombre(contexto.nombre() == null ? ""
                        : TextosDeWhatsapp.cortar(contexto.nombre(), TextosDeWhatsapp.LARGO_DEL_NOMBRE))
                .vacante(contexto.vacante() == null ? ""
                        : TextosDeWhatsapp.cortar(contexto.vacante(), TextosDeWhatsapp.LARGO_DE_LA_VACANTE))
                .texto(TextosDeWhatsapp.cortar(texto, TextosDeWhatsapp.LARGO_DEL_TEXTO))
                .intentos(0)
                .proximoIntentoEn(ahora)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build());
    }

    void cerrarPorFin(Long postulacionId) {
        vinculos.vivoParaEscribir(postulacionId).ifPresent(vinculo -> {
            Instant ahora = Instant.now();
            vinculo.cerrar(VinculoWhatsapp.REVOCADO, VinculoWhatsapp.POR_PROCESO_TERMINADO, ahora);
            vinculos.save(vinculo);
            avisos.omitirPendientesDe(List.of(vinculo.getId()), ahora);
        });
    }
}
