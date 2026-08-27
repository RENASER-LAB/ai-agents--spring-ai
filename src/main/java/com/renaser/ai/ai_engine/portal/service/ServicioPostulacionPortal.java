package com.renaser.ai.ai_engine.portal.service;

import com.renaser.ai.ai_engine.portal.dto.DtosPortal.MiPostulacion;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.MiPostulacionDetalle;
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
    UUID postular(ContextoUsuario quien, Long vacanteId, MultipartFile cv,
                  String resultadoOrgulloso, String portafolio, String linkedin, String github,
                  List<Long> requisitosConfirmados, Boolean aceptaTratamiento,
                  String ip, String userAgent);

    List<MiPostulacion> misPostulaciones(ContextoUsuario quien);

    MiPostulacionDetalle miPostulacion(ContextoUsuario quien, UUID uuid);

    void retirar(ContextoUsuario quien, UUID uuid);
}
