package com.renaser.ai.ai_engine.notificacion.service;

import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A que direccion se le escribe a un candidato.
 *
 * <p>Existe porque hay dos direcciones y no siempre coinciden. Cuando alguien se postula por el
 * portal, la que escribio al crear la cuenta es la suya y no hay mas que hablar. Pero cuando una
 * convocatoria llega como una carpeta de curriculums, el cargador le inventa una
 * —{@code nombre.apellido@cv-convocatoria.local}, un dominio que no existe— porque una cuenta
 * necesita un correo para poder existir. La direccion de verdad esta escrita dentro del
 * curriculum, y el agente que lo lee la guarda en {@code dato_cv.email}.
 *
 * <p><b>Escribir a la inventada no da error.</b> El servidor de correo acepta la direccion, la
 * fila de {@code correo_enviado} queda como ENVIADO, y nadie recibe nada. Por eso la eleccion no
 * puede quedar en cada sitio que manda —serian dos, y el tercero que se escriba lo olvidaria—
 * sino aqui.
 *
 * <p>Gana la de la cuenta cuando sirve: es la que la persona eligio y por la que entra. La del
 * curriculum es el recambio, no la preferida.
 */
@Service
@RequiredArgsConstructor
public class DireccionDelCandidato {

    // El dominio que pone scripts/cargar-convocatoria.py cuando no sabe el correo. No resuelve
    // en ningun sitio a proposito: asi el fallo es ruidoso y no un correo perdido en silencio.
    private static final String DOMINIO_INVENTADO = "@cv-convocatoria.local";

    private final DatoCvRepository datosCv;

    /**
     * La direccion buena, o {@code null} si no hay ninguna a la que se pueda escribir.
     *
     * <p>Devolver {@code null} es una respuesta valida y esperada: {@link ServicioCorreo} guarda
     * igual el texto del aviso y lo marca NO_ENVIADO. Queda escrito lo que se le habria dicho y
     * queda claro que no le llego.
     */
    public String de(Usuario usuario, Long postulacionId) {
        String deLaCuenta = usuario == null ? null : usuario.getCorreo();
        if (sirve(deLaCuenta)) {
            return deLaCuenta;
        }
        String delCurriculum = datosCv.findByPostulacionId(postulacionId)
                .map(d -> d.getEmail())
                .orElse(null);
        return sirve(delCurriculum) ? delCurriculum.trim() : null;
    }

    /**
     * Una direccion sirve si se puede entregar en ella.
     *
     * <p>La comprobacion es deliberadamente floja: no valida el formato de un correo, solo
     * descarta lo que seguro no se puede entregar. La del curriculum la escribio un modelo
     * leyendo un PDF, asi que puede venir con espacios o partida; y una regla estricta rechazaria
     * direcciones reales y raras, que es peor que dejar pasar una que rebota.
     */
    private boolean sirve(String direccion) {
        if (direccion == null) {
            return false;
        }
        String limpia = direccion.trim();
        if (limpia.isEmpty() || limpia.toLowerCase().endsWith(DOMINIO_INVENTADO)) {
            return false;
        }
        int arroba = limpia.indexOf('@');
        return arroba > 0
                && limpia.indexOf('.', arroba) > arroba + 1
                && !limpia.contains(" ")
                && !limpia.endsWith(".");
    }
}
