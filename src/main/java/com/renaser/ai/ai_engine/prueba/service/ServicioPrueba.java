package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.dto.DtosPrueba.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * La prueba del puesto, desde el lado del candidato.
 *
 * <p>Igual que la evaluación del hito 2: todo entra por el UUID de la postulación, y una
 * prueba que no es suya responde 404, nunca 403.
 */
public interface ServicioPrueba {

    /** La crea el sistema al confirmar el avance a PRUEBA_TURNO_CANDIDATO. No arranca el reloj. */
    /**
     * Le crea el intento al candidato que entra en su turno de la prueba.
     *
     * <p>Si ya tiene uno —retroceder la postulación y volver a avanzarla pasa en el panel—
     * se reutiliza en vez de intentar crear otro, que la clave única de la tabla rechaza.
     * Al que todavía no ha abierto su prueba se le pone la versión que la vacante rinde hoy;
     * al que ya la abrió no se le toca, porque su versión quedó fijada al empezar (RF-90).
     *
     * @param cierraEn cuándo cierra la prueba de su vacante, o {@code null} para contar los
     *                 días de la plantilla desde que empiece (V32)
     */
    Long crearAlEntrar(Long organizacionId, Long postulacionId, Long versionPlantillaPruebaId,
                       java.time.Instant cierraEn);

    MiPrueba ver(ContextoUsuario quien, UUID uuidPostulacion);

    /** Arranca el reloj: fija venceEn y sortea la variante y el minuto del cambio. */
    MiPrueba iniciar(ContextoUsuario quien, UUID uuidPostulacion);

    void responder(ContextoUsuario quien, UUID uuidPostulacion, Long preguntaId, Responder datos);

    void subirEntregableArchivo(ContextoUsuario quien, UUID uuidPostulacion, Long entregableRequeridoId,
                                MultipartFile archivo);

    void subirEntregableEnlace(ContextoUsuario quien, UUID uuidPostulacion, Long entregableRequeridoId,
                               SubirEntregableEnlace datos);

    /** Entrega manual: exige que estén todos los obligatorios. */
    EntregaResponse entregar(ContextoUsuario quien, UUID uuidPostulacion);

    /** Llamado por el sondeo: entrega lo que haya, aunque falten obligatorios. No existe entregar tarde. */
    void entregarVencidos();
}
