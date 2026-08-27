package com.renaser.ai.ai_engine.perfil.service;

/**
 * Decide qué pasa con el currículum recién subido: leerlo, o reutilizar una lectura ya
 * pagada.
 *
 * <p>Cada lectura es una llamada al modelo y cuesta dinero. La huella del archivo
 * ({@code archivo.contenido_hash}) permite saber que «este PDF ya se leyó»: si la misma
 * persona postuló antes con el mismo archivo, su ficha se copia a la postulación nueva y
 * no se paga nada (RF-161). Solo un archivo nuevo dispara una lectura nueva.
 */
public interface ServicioLecturaCv {

    /**
     * Se llama al terminar de postular. Nunca lanza: postular no puede fallar porque la
     * lectura no se haya podido decidir — en el peor caso, simplemente no se lee.
     */
    void trasPostular(Long personaId, Long postulacionId);
}
