package com.renaser.ai.ai_engine.postulacion.service.impl;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.service.ServicioTextoCv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Ver {@link ServicioTextoCv}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioTextoCvImpl implements ServicioTextoCv {

    private final CvRepository cvs;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final ExtractorTextoCv extractor;
    private final AnonimizadorCv anonimizador;

    @Override
    @Transactional
    public String prepararParaIa(Long postulacionId) {
        Cv cv = cvs.findByPostulacionId(postulacionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Esta postulación no tiene currículum: no hay nada que calificar"));

        // Ya estaba hecho. Un reintento de la IA no vuelve a leer el disco.
        if (cv.getTextoAnonimizado() != null && !cv.getTextoAnonimizado().isBlank()) {
            return cv.getTextoAnonimizado();
        }

        Archivo archivo = archivos.findById(cv.getArchivoOriginalId())
                .orElseThrow(() -> new IllegalStateException("El archivo del currículum ya no existe"));
        if (archivo.getRuta() == null) {
            // Pasa cuando el candidato pidió que se borren sus datos. No es un fallo técnico:
            // simplemente ya no hay currículum que leer, y eso no se puede reintentar.
            throw new IllegalStateException(
                    "El currículum de esta postulación se borró: la IA ya no puede leerlo");
        }

        String completo = extractor.extraer(almacen.leer(archivo), archivo.getTipo(),
                archivo.getNombreOriginal());
        String recortado = anonimizador.anonimizar(completo);

        cv.setTextoExtraido(completo);
        cv.setTextoAnonimizado(recortado);
        cv.setAnonimizadoEn(Instant.now());
        cvs.save(cv);

        log.info("Currículum de la postulación {} listo para la IA: {} caracteres, {} tapados",
                postulacionId, recortado.length(),
                contar(recortado));
        return recortado;
    }

    private int contar(String texto) {
        int veces = 0;
        int desde = 0;
        while ((desde = texto.indexOf(AnonimizadorCv.TAPADO, desde)) >= 0) {
            veces++;
            desde += AnonimizadorCv.TAPADO.length();
        }
        return veces;
    }
}
