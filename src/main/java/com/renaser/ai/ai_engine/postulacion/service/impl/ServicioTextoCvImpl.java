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
            return conLaReglaDeHoy(cv);
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

    /**
     * El texto ya recortado, pero con la regla del anonimizador de hoy.
     *
     * <p>La regla cambia: hasta noviembre de 2026 tapaba «5 años de experiencia» como si fuera una
     * edad. Lo guardado se hizo con la regla de entonces, así que si queda el texto extraído se
     * vuelve a recortar desde él —sin leer el disco: es una cuenta sobre lo que ya está— y se
     * guarda si cambió. Sin texto extraído (el candidato pidió borrar sus datos) se usa lo que hay.
     */
    private String conLaReglaDeHoy(Cv cv) {
        String extraido = cv.getTextoExtraido();
        if (extraido == null || extraido.isBlank()) {
            return cv.getTextoAnonimizado();
        }
        String alDia = anonimizador.anonimizar(extraido);
        if (!alDia.equals(cv.getTextoAnonimizado())) {
            cv.setTextoAnonimizado(alDia);
            cv.setAnonimizadoEn(Instant.now());
            cvs.save(cv);
            log.info("Currículum de la postulación {} recortado de nuevo con la regla de hoy",
                    cv.getPostulacionId());
        }
        return alDia;
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
