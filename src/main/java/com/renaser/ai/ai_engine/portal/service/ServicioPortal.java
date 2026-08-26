package com.renaser.ai.ai_engine.portal.service;

import com.renaser.ai.ai_engine.portal.dto.DtosPortal.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ServicioPortal {

    List<VacantePublica> vacantesPublicadas();

    VacantePublica vacante(Long id);

    List<TextoConsentimientoPublico> textosDeConsentimiento();

    // El texto PROCESO publicado de la empresa de esa vacante: lo que el candidato
    // acepta al postular. 404 si la vacante no está publicada.
    ConsentimientoDeVacante consentimientoDeVacante(Long vacanteId);

    void crearCuenta(CrearCuenta datos, String ip, String userAgent);

    Sesion entrar(Login datos);

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

    void retirarConsentimientoFuturos(ContextoUsuario quien);

    void pedirBorrado(ContextoUsuario quien, String motivo);
}
