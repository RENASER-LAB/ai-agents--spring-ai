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

    /**
     * La sesion del portal a partir de un token ya emitido, con el nombre de quien entra.
     *
     * <p>La usa la entrada por enlace del correo, que emite su token en otro sitio —el
     * paquete de seguridad, que es interno— y necesita devolver el contrato publico del
     * portal.
     */
    com.renaser.ai.ai_engine.portal.dto.DtosPortal.Sesion sesionDe(String token, Long usuarioId);

    List<TextoConsentimientoPublico> textosDeConsentimiento();

    void crearCuenta(CrearCuenta datos, String ip, String userAgent);

    Sesion entrar(Login datos);

    /** Como se llama quien llama, para el portal que ya tiene token pero no nombre. */
    com.renaser.ai.ai_engine.portal.dto.DtosPortal.QuienSoy quienSoy(
            com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario quien);

    void retirarConsentimientoFuturos(ContextoUsuario quien);

    void pedirBorrado(ContextoUsuario quien, String motivo);
}
