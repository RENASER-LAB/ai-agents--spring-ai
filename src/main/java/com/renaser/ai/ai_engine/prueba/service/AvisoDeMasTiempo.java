package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.notificacion.service.DireccionDelCandidato;
import com.renaser.ai.ai_engine.notificacion.service.FechaParaElCandidato;
import com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.service.AvisoDeEtapaEnLaCampana;
import com.renaser.ai.ai_engine.postulacion.service.ServicioEnlaceAcceso;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.whatsapp.service.AvisosPorWhatsapp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Los tres avisos de «más tiempo» para la prueba del puesto (V89): el correo, la campana y, si
 * escribió al bot, WhatsApp. Los tres dicen lo mismo: «Tienes más tiempo para la prueba de
 * {vacante}, hasta el {fecha} (hora de Lima). Entra a Mis procesos para continuarla».
 *
 * <p>⚠️ <b>Todo sale después de confirmar el guardado y en su propia transacción</b>, como la
 * campana de las etapas ({@link AvisoDeEtapaEnLaCampana#alConfirmar}): un aviso de un plazo que
 * luego se deshace sería mentira, y un fallo del aviso no puede deshacer el plazo nuevo. Si algo
 * falla, se anota y se sigue; cada canal es independiente de los otros.
 *
 * <p>⚠️ <b>Sin enlace con token</b>: el correo lleva la dirección de su proceso, que pide su
 * correo y su contraseña, como los recordatorios (15/10/2026).
 */
@Component
@Slf4j
public class AvisoDeMasTiempo {

    /** El título de la campana. */
    public static final String TITULO = "Tienes más tiempo para tu prueba";

    private final ServicioCorreo correo;
    private final ServicioAvisosPortal campana;
    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final DireccionDelCandidato direcciones;
    private final ServicioEnlaceAcceso enlaces;
    private final OrganizacionRepository organizaciones;
    private final AvisosPorWhatsapp whatsapp;
    private final TransactionTemplate enSuTransaccion;

    @SuppressWarnings("java:S107") // Cada uno es un canal o una pieza del aviso.
    public AvisoDeMasTiempo(ServicioCorreo correo, ServicioAvisosPortal campana,
                            UsuarioRepository usuarios, PersonaRepository personas,
                            DireccionDelCandidato direcciones, ServicioEnlaceAcceso enlaces,
                            OrganizacionRepository organizaciones, AvisosPorWhatsapp whatsapp,
                            PlatformTransactionManager transacciones) {
        this.correo = correo;
        this.campana = campana;
        this.usuarios = usuarios;
        this.personas = personas;
        this.direcciones = direcciones;
        this.enlaces = enlaces;
        this.organizaciones = organizaciones;
        this.whatsapp = whatsapp;
        this.enSuTransaccion = new TransactionTemplate(transacciones);
        this.enSuTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * La frase de los tres avisos.
     *
     * @param vacante el título de la vacante; sin él, «la prueba del puesto»
     * @param fecha   la fecha nueva ya dicha: «jue 20 nov, 18:00»
     */
    public static String frase(String vacante, String fecha) {
        String deQue = vacante == null || vacante.isBlank() ? "la prueba del puesto"
                : "la prueba de " + vacante.trim();
        return "Tienes más tiempo para " + deQue + ", hasta el " + fecha
                + " (hora de Lima). Entra a Mis procesos para continuarla.";
    }

    /**
     * Por dónde saldría el aviso: «correo» y «campana» siempre, y «WhatsApp» si hoy le llegaría
     * (empresa en la beta, vínculo activo y número sin baja). Es lo que la ficha dice al guardar.
     */
    public List<String> canales(Postulacion postulacion) {
        List<String> canales = new ArrayList<>(List.of("correo", "campana"));
        try {
            if (whatsapp.llegaria(postulacion.getId(), postulacion.getOrganizacionId())) {
                canales.add("WhatsApp");
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo saber si a la postulación {} le llega WhatsApp: {}",
                    postulacion.getId(), e.getMessage());
        }
        return canales;
    }

    /**
     * Deja los tres avisos para cuando el guardado se confirme. Nunca lanza.
     *
     * @param intento la prueba ya con su fecha nueva
     * @param vacante el título de la vacante
     */
    public void avisar(Postulacion postulacion, IntentoPrueba intento, String vacante) {
        Instant hasta = intento.getVenceEn();
        String fecha = FechaParaElCandidato.conDiaYMes(hasta, Instant.now());
        String frase = frase(vacante, fecha);
        Long organizacionId = postulacion.getOrganizacionId();
        Long usuarioId = postulacion.getUsuarioId();
        Long postulacionId = postulacion.getId();
        Long vacanteId = postulacion.getVacanteId();

        Usuario usuario = usuarios.findById(usuarioId).orElse(null);
        String nombre = usuario == null || usuario.getPersonaId() == null ? ""
                : personas.findById(usuario.getPersonaId()).map(Persona::getNombre).orElse("");
        String destino = usuario == null ? null : direcciones.de(usuario, postulacionId);
        Map<String, String> variables = new HashMap<>(Map.of(
                "nombre", nombre == null ? "" : nombre,
                "vacante", vacante == null ? "" : vacante,
                "vence", fecha,
                "aviso", frase,
                "enlace", Objects.requireNonNullElse(enlaces.enlaceAlProceso(postulacion.getUuid()), ""),
                "codigo", String.valueOf(postulacion.getUuid())));

        AvisoDeEtapaEnLaCampana.alConfirmar(
                () -> enSuTransaccion.executeWithoutResult(t ->
                        mandarElCorreo(organizacionId, usuarioId, destino, variables, postulacionId)),
                "correo " + AvisoPortal.PRUEBA_MAS_TIEMPO, postulacionId);
        AvisoDeEtapaEnLaCampana.alConfirmar(
                () -> campana.publicar(organizacionId, usuarioId, AvisoPortal.PRUEBA_MAS_TIEMPO,
                        TITULO, frase, postulacionId, vacanteId),
                AvisoPortal.PRUEBA_MAS_TIEMPO, postulacionId);
        whatsapp.alDarMasTiempo(postulacion, intento.getId(), hasta, nombre, vacante);
    }

    private void mandarElCorreo(Long organizacionId, Long usuarioId, String destino,
                                Map<String, String> variables, Long postulacionId) {
        try {
            // A la plataforma si a la empresa le faltara el texto: la V89 lo siembra en todas,
            // pero un aviso que no sale en silencio es peor que uno con el texto común.
            Long plataforma = organizaciones.findByEsPlataformaTrue().map(Organizacion::getId).orElse(null);
            correo.plantillaConRecambio(organizacionId, plataforma, AvisoPortal.PRUEBA_MAS_TIEMPO)
                    .ifPresentOrElse(
                            plantilla -> correo.enviarCon(plantilla, usuarioId, destino, variables),
                            () -> log.error("MAS TIEMPO: no hay texto «{}» para la organización {}",
                                    AvisoPortal.PRUEBA_MAS_TIEMPO, organizacionId));
        } catch (RuntimeException e) {
            log.error("MAS TIEMPO: no salió el correo de la postulación {}: {}. La campana sale "
                    + "igual", postulacionId, e.getMessage());
        }
    }
}
