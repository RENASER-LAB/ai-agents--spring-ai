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

    void crearCuenta(CrearCuenta datos, String ip, String userAgent);

    Sesion entrar(Login datos);

    // El formulario de postular: el CV, los enlaces, el texto obligatorio y los
    // requisitos objetivos que el candidato declara cumplir (autodeclaración)
    UUID postular(ContextoUsuario quien, Long vacanteId, MultipartFile cv,
                  String resultadoOrgulloso, String portafolio, String linkedin, String github,
                  List<Long> requisitosConfirmados);

    List<MiPostulacion> misPostulaciones(ContextoUsuario quien);

    MiPostulacionDetalle miPostulacion(ContextoUsuario quien, UUID uuid);

    void retirar(ContextoUsuario quien, UUID uuid);

    void retirarConsentimientoFuturos(ContextoUsuario quien);

    void pedirBorrado(ContextoUsuario quien, String motivo);
}
