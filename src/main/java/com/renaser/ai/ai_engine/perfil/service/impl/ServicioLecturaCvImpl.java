package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.perfil.service.ServicioLecturaCv;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * La decisión de RF-161 en un solo sitio: leer el currículum, o copiar una lectura ya
 * pagada.
 *
 * <p>Si esta persona ya postuló con un archivo de la misma huella, su ficha ({@code dato_cv})
 * se copia a la postulación nueva y no se encola nada — la criba y el retrato la encuentran
 * gratis, y la barrera de la cola («esta postulación ya tiene ficha») pasa a ser verdad sin
 * tocarla. Solo un archivo distinto dispara una lectura nueva.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioLecturaCvImpl implements ServicioLecturaCv {

    private final CvRepository cvs;
    private final ArchivoRepository archivos;
    private final DatoCvRepository datosCv;
    private final ColaCalificacionIa cola;

    @Override
    @Transactional
    public void trasPostular(Long personaId, Long postulacionId) {
        try {
            decidir(personaId, postulacionId);
        } catch (RuntimeException e) {
            // Postular no puede fallar por esto: en el peor caso el curriculum se queda
            // sin leer y la criba lo leera cuando alguien la pida, como siempre.
            log.error("No se pudo decidir la lectura del CV de la postulación {}: {}",
                    postulacionId, e.getMessage(), e);
        }
    }

    private void decidir(Long personaId, Long postulacionId) {
        String hash = cvs.findByPostulacionId(postulacionId)
                .map(cv -> cv.getArchivoOriginalId())
                .flatMap(archivos::findById)
                .map(a -> a.getContenidoHash())
                .orElse(null);

        if (hash != null) {
            List<DatoCv> previas = datosCv.fichasDeLaPersonaConHash(personaId, hash).stream()
                    .filter(d -> !d.getPostulacionId().equals(postulacionId))
                    .toList();
            if (!previas.isEmpty()) {
                copiar(previas.get(0), postulacionId);
                log.info("CV de la postulación {} ya leído antes (misma huella): ficha "
                        + "copiada, sin llamar al modelo", postulacionId);
                return;
            }
        }
        cola.encolarDatosCv(postulacionId);
    }

    private void copiar(DatoCv origen, Long postulacionId) {
        DatoCv fila = datosCv.findByPostulacionId(postulacionId)
                .orElseGet(() -> DatoCv.builder()
                        .postulacionId(postulacionId)
                        .creadoEn(Instant.now())
                        .build());
        fila.setNombre(origen.getNombre());
        fila.setEmail(origen.getEmail());
        fila.setTelefono(origen.getTelefono());
        fila.setPerfilResumen(origen.getPerfilResumen());
        fila.setHabilidades(origen.getHabilidades());
        fila.setExperienciaMesesTotal(origen.getExperienciaMesesTotal());
        fila.setUltimoPuesto(origen.getUltimoPuesto());
        fila.setUltimaEmpresa(origen.getUltimaEmpresa());
        fila.setUltimaMesesDuracion(origen.getUltimaMesesDuracion());
        fila.setEducacionMaxima(origen.getEducacionMaxima());
        // Se conserva de que llamada salio: abrir esta ficha dentro de seis meses tiene
        // que seguir diciendo que se le mando al modelo y que contesto.
        fila.setEjecucionIaId(origen.getEjecucionIaId());
        fila.setActualizadoEn(Instant.now());
        datosCv.save(fila);
    }
}
