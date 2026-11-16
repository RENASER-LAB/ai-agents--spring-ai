package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.informe.entity.TextoInforme;
import com.renaser.ai.ai_engine.informe.repository.TextoInformeRepository;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.Textos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Los textos del redactor del informe: leer los que valen, pedir los que faltan y guardar los
 * que llegan.
 *
 * <p><b>Un texto solo vale para los datos con los que se escribió</b> (su huella). Pedir los de
 * una huella ya pedida no vuelve a pagar: si aquella vez falló, el informe sigue con las
 * explicaciones de la IA hasta que algo cambie en la etapa (una recalificación, un ajuste).
 *
 * <p><b>Nada de esto frena una descarga.</b> Si la cola está apagada o algo falla al pedir, el
 * informe sale igual con las explicaciones completas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextosDelInforme {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TextoInformeRepository textos;
    private final ColaCalificacionIa cola;

    /** Los textos guardados, si se escribieron con estos mismos datos. */
    @Transactional(readOnly = true)
    public Optional<Textos> vigentes(Long postulacionId, EtapaDelInforme etapa, String huella) {
        return textos.findByPostulacionIdAndEtapa(postulacionId, etapa.name())
                .filter(t -> huella.equals(t.getHuella()))
                .map(this::leer);
    }

    /** Si el redactor tiene un trabajo vivo para esta etapa: los textos vienen en camino. */
    public boolean enCamino(Long postulacionId, EtapaDelInforme etapa) {
        return cola.redactorDelInformeVivo(postulacionId, etapa.name());
    }

    /**
     * Pide al redactor los textos de estos datos, si no se pidieron ya. Devuelve si vienen en
     * camino. Corre en su propia transacción: el informe se lee en una de solo lectura y esto
     * nunca puede tumbarlo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean pedir(Long postulacionId, EtapaDelInforme etapa, String huella) {
        TextoInforme fila = textos.findByPostulacionIdAndEtapa(postulacionId, etapa.name())
                .orElseGet(() -> TextoInforme.builder()
                        .postulacionId(postulacionId)
                        .etapa(etapa.name())
                        .descartados(0)
                        .creadoEn(Instant.now())
                        .build());
        if (huella.equals(fila.getHuellaPedida())) {
            return enCamino(postulacionId, etapa);
        }
        boolean encolado = cola.encolarRedactorInforme(postulacionId, etapa.name());
        if (!encolado && !enCamino(postulacionId, etapa)) {
            // La IA está apagada o no se pudo encolar: no se anota, para pedirlo la próxima vez.
            return false;
        }
        fila.setHuellaPedida(huella);
        fila.setPedidaEn(Instant.now());
        try {
            textos.saveAndFlush(fila);
        } catch (DataIntegrityViolationException carrera) {
            // Otra petición la creó a la vez: lo pedido ya está pedido.
            log.info("Los textos del informe {} de la postulación {} ya se estaban pidiendo",
                    etapa, postulacionId);
        }
        return true;
    }

    /** Guarda lo que pasó la comprobación, atado a la huella de los datos con que se escribió. */
    @Transactional
    public void guardar(Long postulacionId, EtapaDelInforme etapa, String huella, Textos validados,
                        Long ejecucionIaId) {
        TextoInforme fila = textos.findByPostulacionIdAndEtapa(postulacionId, etapa.name())
                .orElseGet(() -> TextoInforme.builder()
                        .postulacionId(postulacionId)
                        .etapa(etapa.name())
                        .creadoEn(Instant.now())
                        .build());
        fila.setHuella(huella);
        fila.setResumen(validados.resumen());
        fila.setCriterios(JSON.writeValueAsString(validados.criterios()));
        fila.setPreguntas(JSON.writeValueAsString(validados.preguntas()));
        fila.setDescartados(validados.descartados());
        fila.setEjecucionIaId(ejecucionIaId);
        fila.setEscritaEn(Instant.now());
        if (fila.getHuellaPedida() == null) {
            fila.setHuellaPedida(huella);
            fila.setPedidaEn(Instant.now());
        }
        textos.save(fila);
        log.info("Textos del informe {} de la postulación {} guardados ({} descartados)", etapa,
                postulacionId, validados.descartados());
    }

    private Textos leer(TextoInforme t) {
        Map<String, String> criterios = new LinkedHashMap<>();
        List<String> preguntas = new ArrayList<>();
        try {
            if (t.getCriterios() != null) {
                criterios.putAll(JSON.readValue(t.getCriterios(), new TypeReference<Map<String, String>>() { }));
            }
            if (t.getPreguntas() != null) {
                preguntas.addAll(JSON.readValue(t.getPreguntas(), new TypeReference<List<String>>() { }));
            }
        } catch (RuntimeException roto) {
            log.warn("Los textos guardados del informe {} de la postulación {} no se pudieron leer: "
                    + "se usan las explicaciones", t.getEtapa(), t.getPostulacionId());
            return new Textos(null, Map.of(), List.of(), 0);
        }
        return new Textos(t.getResumen(), criterios, preguntas,
                t.getDescartados() == null ? 0 : t.getDescartados());
    }
}
