package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.service.FinDelPerfilIntegral;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.service.ResultadosPorWhatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Cerrar una vacante: a mano desde el panel o sola, al llegar su fecha límite
 * (specs/Ready/perfil-integral-hasta-el-cierre-de-la-vacante, V78).
 *
 * <p>Las dos formas hacen lo mismo, en este orden y en una sola transacción:
 * <ol>
 *   <li>la vacante pasa a {@code CERRADA}: deja de recibir postulaciones y sale del tablón;</li>
 *   <li>queda en la auditoría quién la cerró —el sistema, si fue por su fecha— y por qué;</li>
 *   <li>el Perfil Integral sin terminar vence y quien lo seguía esperando queda cerrado, con
 *       los avisos de siempre ({@link FinDelPerfilIntegral});</li>
 *   <li>y solo entonces, a quien sigue en carrera con WhatsApp v2, el aviso de que la
 *       convocatoria cerró y su postulación sigue. Va después a propósito: a quien se le acaba
 *       de cerrar la postulación ese aviso ya no sería cierto (AC-11).</li>
 * </ol>
 *
 * <p>Lo demás sigue como antes del cambio: quien está en otra etapa no se mueve, y la prueba del
 * puesto conserva su propia fecha límite.
 */
@Service
@Slf4j
public class CierreDeLaVacante {

    /** El motivo que queda en la auditoría cuando la cierra su fecha. */
    public static final String MOTIVO_POR_FECHA = "Llegó la fecha de cierre";

    private static final String CERRADA = "CERRADA";
    private static final String PUBLICADA = "PUBLICADA";

    private final VacanteRepository vacantes;
    private final ServicioAuditoria auditoria;
    private final FinDelPerfilIntegral perfilIntegral;
    private final PostulacionRepository postulaciones;
    private final TransactionTemplate enSuTransaccion;

    /**
     * El aviso de convocatoria cerrada por WhatsApp (V76, contrato v2). Por setter y opcional,
     * como el de la máquina de estados: con el productor v2 apagado no escribe nada.
     */
    private ResultadosPorWhatsapp whatsapp;

    public CierreDeLaVacante(VacanteRepository vacantes, ServicioAuditoria auditoria,
                             FinDelPerfilIntegral perfilIntegral,
                             PostulacionRepository postulaciones,
                             PlatformTransactionManager transacciones) {
        this.vacantes = vacantes;
        this.auditoria = auditoria;
        this.perfilIntegral = perfilIntegral;
        this.postulaciones = postulaciones;
        this.enSuTransaccion = new TransactionTemplate(transacciones);
        this.enSuTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Autowired(required = false)
    void setWhatsapp(ResultadosPorWhatsapp whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Cierra una vacante que quien llama ya bloqueó y comprobó que no estaba cerrada.
     *
     * @param quien  quien la cierra, o nulo si es el sistema
     * @param motivo el que queda en la auditoría
     */
    @Transactional
    public void cerrar(Vacante vacante, ContextoUsuario quien, String motivo) {
        String anterior = vacante.getEstado();
        Instant ahora = Instant.now();
        vacante.setEstado(CERRADA);
        vacante.setCerradaEn(ahora);
        vacantes.save(vacante);
        auditoria.registrar(vacante.getOrganizacionId(), quien, "cerrar_vacante", "vacante",
                vacante.getId(), Map.of("estado", anterior), Map.of("estado", CERRADA), motivo);

        perfilIntegral.alCerrarseLaVacante(vacante.getId(), ahora);
        // Los cierres de arriba, en la base antes de buscar a quién avisar de la convocatoria:
        // esa búsqueda pregunta por quien sigue en carrera, y quien se acaba de cerrar ya no.
        postulaciones.flush();

        // A quien sigue en carrera con WhatsApp activo le queda, en esta misma transacción, el
        // aviso de que la convocatoria cerró: su postulación sigue y su vínculo también (V76).
        if (whatsapp != null) {
            whatsapp.alCerrarLaConvocatoria(vacante);
        }
    }

    /**
     * El sondeo: cierra las vacantes publicadas cuya fecha límite ya llegó.
     *
     * <p>Una transacción por vacante, con su fila bloqueada y todo vuelto a mirar dentro: si el
     * sondeo corre dos veces a la vez, o alguien la cierra a mano en el mismo instante, la
     * segunda la encuentra cerrada y no hace nada. Nadie recibe dos avisos. Una vacante en
     * borrador no se cierra sola: al publicarla se le exige una fecha que no haya pasado.
     *
     * @return cuántas cerró
     */
    public int cerrarLasQueLlegaronASuFecha() {
        int cerradas = 0;
        for (Vacante candidata : vacantes.queLlegaronASuFecha(Instant.now())) {
            try {
                if (Boolean.TRUE.equals(enSuTransaccion.execute(t -> porSuFecha(candidata)))) {
                    cerradas++;
                }
            } catch (RuntimeException e) {
                log.error("No se pudo cerrar por su fecha la vacante {}; se reintenta en el "
                        + "próximo ciclo: {}", candidata.getId(), e.getMessage());
            }
        }
        if (cerradas > 0) {
            log.info("CIERRE POR FECHA: {} vacantes cerradas", cerradas);
        }
        return cerradas;
    }

    private boolean porSuFecha(Vacante candidata) {
        Vacante vacante = vacantes.bloquear(candidata.getId(), candidata.getOrganizacionId())
                .orElse(null);
        if (vacante == null || !PUBLICADA.equals(vacante.getEstado())
                || !FormaDeCierre.esPorFecha(vacante.getTipoCierre())
                || vacante.getCierraEn() == null
                || vacante.getCierraEn().isAfter(Instant.now())) {
            return false;
        }
        cerrar(vacante, null, MOTIVO_POR_FECHA);
        return true;
    }
}
