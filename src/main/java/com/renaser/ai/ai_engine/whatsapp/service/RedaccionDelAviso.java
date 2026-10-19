package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.TextosDelMomento;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * El texto de un aviso «te toca» por WhatsApp, con el formato de la regla 19 (V76): la vacante en
 * negrita, lo que toca, el plazo para empezar la prueba si EX lo sabe y el enlace fijo a «Mis
 * procesos». Los tipos, las firmas y el contrato v1 no cambian: el bot lo envía tal cual.
 */
@Component
@RequiredArgsConstructor
public class RedaccionDelAviso {

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final LoQueLeToca acciones;
    private final PropiedadesWhatsapp propiedades;

    /**
     * El texto del aviso, hasta 1000 caracteres.
     *
     * @param aviso  {@code PRUEBA_DISPONIBLE} o {@code POSTULACION_AVANZA}
     * @param estado el estado al que acaba de pasar la postulación
     */
    public String texto(Long postulacionId, String aviso, String estado,
                        AvisosPorWhatsapp.DeLaTransicion contexto) {
        String enlace = propiedades.getEnlaceMisProcesos();
        if (AvisoPortal.PRUEBA_DISPONIBLE.equals(aviso)) {
            boolean esCuestionario = TextosDelMomento.CUESTIONARIO_TECNICO.equals(contexto.instrumento());
            return TextosDeWhatsapp.pruebaDisponible(contexto.nombre(), contexto.vacante(), esCuestionario,
                    empezarAntesDe(postulacionId), enlace);
        }
        return TextosDeWhatsapp.avance(contexto.nombre(), contexto.vacante(),
                TextosDelMomento.de(estado, contexto.instrumento(), false), enlace);
    }

    /** Hasta cuándo puede empezar la prueba, leído de su intento o su cuestionario; nulo si no se sabe. */
    private Instant empezarAntesDe(Long postulacionId) {
        Postulacion postulacion = postulaciones.findById(postulacionId).orElse(null);
        if (postulacion == null) {
            return null;
        }
        Instant empezar = acciones.empezarAntesDe(postulacion,
                vacantes.findById(postulacion.getVacanteId()).orElse(null));
        // Una fecha ya pasada no es un plazo que se pueda ofrecer.
        return empezar != null && empezar.isAfter(Instant.now()) ? empezar : null;
    }
}
