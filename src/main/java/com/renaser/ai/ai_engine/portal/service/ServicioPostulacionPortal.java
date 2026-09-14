package com.renaser.ai.ai_engine.portal.service;

import com.renaser.ai.ai_engine.portal.dto.DtosPortal.MiPostulacion;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.MiPostulacionDetalle;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.MisAvisos;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * La postulación vista desde el candidato: postular a una vacante del tablón, seguir
 * las suyas y retirarse. La postulación nace en la organización DE LA VACANTE, no en la
 * del candidato: es lo que hace que el panel de cada empresa vea a sus candidatos y que
 * el aislamiento signifique algo.
 */
public interface ServicioPostulacionPortal {

    // El formulario de postular: el CV, los enlaces, el texto obligatorio, los requisitos
    // objetivos que el candidato declara cumplir (autodeclaración) y la aceptación del
    // texto legal de la empresa de la vacante — obligatoria, y queda firmada con IP y
    // navegador a nombre de esa empresa y de esta postulación (pieza D).
    /**
     * @param pretensionMonto cuánto quiere ganar en esta vacante. <b>Obligatorio si la vacante
     *        publica lo que paga</b> y se ignora si no (V54): es el trato simétrico —la
     *        empresa enseña su presupuesto, el candidato enseña su precio— y no tiene sentido
     *        pedírselo a quien no ha recibido nada a cambio.
     */
    UUID postular(ContextoUsuario quien, Long vacanteId, MultipartFile cv,
                  String resultadoOrgulloso, String portafolio, String linkedin, String github,
                  List<Long> requisitosConfirmados, Boolean aceptaTratamiento,
                  java.math.BigDecimal pretensionMonto, String pretensionMoneda,
                  String ip, String userAgent);

    List<MiPostulacion> misPostulaciones(ContextoUsuario quien);

    MiPostulacionDetalle miPostulacion(ContextoUsuario quien, UUID uuid);

    void retirar(ContextoUsuario quien, UUID uuid);

    // ---------- la campana (V55) ----------

    /** Mis avisos, los nuevos arriba, y cuántos me quedan sin ver. */
    MisAvisos misAvisos(ContextoUsuario quien);

    /**
     * Apaga el punto de todos.
     *
     * @return cuántos se apagaron
     */
    int marcarAvisosLeidos(ContextoUsuario quien);

    /** Apaga uno. Si no es suyo, no pasa nada: se ignora en silencio. */
    void marcarAvisoLeido(ContextoUsuario quien, Long avisoId);
}
