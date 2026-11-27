package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.informe.entity.SeguimientoPendiente;
import com.renaser.ai.ai_engine.informe.repository.SeguimientoPendienteRepository;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Duda;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Resultado;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * El seguimiento de pendientes guardado (V94): leer el que vale, pedir el que falta y guardar el
 * que llega. Es el hermano de {@link TextosDelInforme}.
 *
 * <p><b>Un seguimiento solo vale para los datos con los que se hizo</b> (su huella). Pedir el de
 * una huella ya pedida no vuelve a pagar: si aquella vez falló, el informe sigue con «Por
 * contrastar» hasta que algo cambie en la etapa.
 *
 * <p><b>Nada de esto frena una descarga ni una etapa.</b> Si la cola está apagada o algo falla al
 * pedir, el informe sale igual que siempre.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeguimientosDelInforme {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SeguimientoPendienteRepository seguimientos;
    private final ColaCalificacionIa cola;

    /** El seguimiento guardado, si se hizo con estos mismos datos. */
    @Transactional(readOnly = true)
    public Optional<Resultado> vigente(Long postulacionId, EtapaDelInforme etapa, String huella) {
        return seguimientos.findByPostulacionIdAndEtapa(postulacionId, etapa.name())
                .filter(s -> huella.equals(s.getHuella()))
                .flatMap(this::leer);
    }

    /** Si estos datos ya se pidieron: viniera o no, no se vuelve a pagar. */
    @Transactional(readOnly = true)
    public boolean yaPedido(Long postulacionId, EtapaDelInforme etapa, String huella) {
        return seguimientos.findByPostulacionIdAndEtapa(postulacionId, etapa.name())
                .map(s -> huella.equals(s.getHuellaPedida()))
                .orElse(false);
    }

    /** Si el agente tiene un trabajo vivo para esta etapa: el seguimiento viene en camino. */
    public boolean enCamino(Long postulacionId, EtapaDelInforme etapa) {
        return cola.seguimientoPendientesVivo(postulacionId, etapa.name());
    }

    /**
     * Pide el seguimiento de estos datos, si no se pidió ya. Devuelve si viene en camino. Corre en
     * su propia transacción: el informe se lee en una de solo lectura y esto nunca puede tumbarlo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean pedir(Long postulacionId, EtapaDelInforme etapa, String huella) {
        SeguimientoPendiente fila = seguimientos.findByPostulacionIdAndEtapa(postulacionId, etapa.name())
                .orElseGet(() -> nueva(postulacionId, etapa));
        return PedidoSinRepetir.pedir(fila, huella,
                () -> cola.encolarSeguimientoPendientes(postulacionId, etapa.name()),
                () -> enCamino(postulacionId, etapa), seguimientos,
                "El seguimiento de pendientes " + etapa + " de la postulación " + postulacionId);
    }

    /** Guarda cómo quedó cada duda, atado a la huella de los datos con que se hizo. */
    @Transactional
    public void guardar(Long postulacionId, EtapaDelInforme etapa, String huella, Resultado resultado,
                        Long ejecucionIaId) {
        SeguimientoPendiente fila = seguimientos.findByPostulacionIdAndEtapa(postulacionId, etapa.name())
                .orElseGet(() -> nueva(postulacionId, etapa));
        fila.setHuella(huella);
        fila.setDudas(JSON.writeValueAsString(resultado.dudas()));
        fila.setDescartados(resultado.descartados());
        fila.setMotivos(JSON.writeValueAsString(resultado.motivos()));
        fila.setEjecucionIaId(ejecucionIaId);
        fila.setEscritaEn(Instant.now());
        if (fila.getHuellaPedida() == null) {
            fila.setHuellaPedida(huella);
            fila.setPedidaEn(Instant.now());
        }
        seguimientos.save(fila);
        if (resultado.descartados() > 0) {
            log.info("Seguimiento de pendientes {} de la postulación {} guardado con {} descartes: {}", etapa,
                    postulacionId, resultado.descartados(), resultado.motivos());
        } else {
            log.info("Seguimiento de pendientes {} de la postulación {} guardado", etapa, postulacionId);
        }
    }

    private static SeguimientoPendiente nueva(Long postulacionId, EtapaDelInforme etapa) {
        return SeguimientoPendiente.builder()
                .postulacionId(postulacionId)
                .etapa(etapa.name())
                .descartados(0)
                .creadoEn(Instant.now())
                .build();
    }

    private Optional<Resultado> leer(SeguimientoPendiente s) {
        if (s.getDudas() == null) {
            return Optional.empty();
        }
        try {
            List<Duda> dudas = JSON.readValue(s.getDudas(), new TypeReference<List<Duda>>() { });
            List<String> motivos = s.getMotivos() == null ? List.of()
                    : JSON.readValue(s.getMotivos(), new TypeReference<List<String>>() { });
            return Optional.of(new Resultado(dudas, s.getDescartados() == null ? 0 : s.getDescartados(), motivos));
        } catch (RuntimeException roto) {
            log.warn("El seguimiento de pendientes {} de la postulación {} no se pudo leer: el informe sale "
                    + "como si no lo hubiera", s.getEtapa(), s.getPostulacionId());
            return Optional.empty();
        }
    }
}
