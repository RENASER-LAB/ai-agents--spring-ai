package com.renaser.ai.ai_engine.portal.service;

import com.renaser.ai.ai_engine.portal.dto.DtosPortal.CrearCuenta;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.Login;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.Sesion;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.TextoConsentimientoPublico;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * La cuenta del candidato y su acceso: crearla, entrar, los textos legales de la
 * plataforma, y los derechos que se ejercen desde la cuenta — retirar el consentimiento
 * de futuros contactos y pedir el borrado 29733 (que ejecuta la plataforma, no el portal).
 *
 * <p>El candidato es DE LA PLATAFORMA: una sola cuenta, y con ella postula a la vacante
 * de cualquier empresa. Su cuenta, sus consentimientos y su login cuelgan de la
 * organización plataforma; lo único del portal que cruza empresas es el tablón de
 * vacantes, y su postulación nace en la empresa de la vacante.
 */
public interface ServicioCuentaPortal {

    List<TextoConsentimientoPublico> textosDeConsentimiento();

    void crearCuenta(CrearCuenta datos, String ip, String userAgent);

    Sesion entrar(Login datos);

    void retirarConsentimientoFuturos(ContextoUsuario quien);

    void pedirBorrado(ContextoUsuario quien, String motivo);
}
