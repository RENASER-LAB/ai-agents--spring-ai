package com.renaser.ai.ai_engine.simulacion.service;

import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.simulacion.entity.SesionSimulacion;
import com.renaser.ai.ai_engine.simulacion.entity.SesionVacante;
import com.renaser.ai.ai_engine.simulacion.repository.InscripcionSesionRepository;
import com.renaser.ai.ai_engine.simulacion.repository.SesionSimulacionRepository;
import com.renaser.ai.ai_engine.simulacion.repository.SesionVacanteRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Las tres reglas que mueven solas a un candidato entre «esperando fecha» y «puede elegir».
 *
 * <p><b>Es el único punto del sistema donde el estado de una postulación depende de lo que pase
 * en otra tabla</b>, y no de una acción sobre ella misma. Por eso está aislado aquí en vez de
 * repartido por los servicios que crean o cancelan sesiones.
 *
 * <p>Lo que lo hace peligroso es que un fallo aquí <b>no produce ningún error</b>: un candidato
 * esperando una sesión que ya existe simplemente no avanza, y nadie se entera hasta que alguien
 * mira la bandeja y pregunta por qué lleva dos semanas parado.
 *
 * <ul>
 *   <li>Se publica una sesión o se amplía su cupo → los que esperaban pasan a elegir, y se les avisa.
 *   <li>Se llena el cupo de la última sesión → los que no se inscribieron vuelven a esperar.
 *   <li>Se cancela una sesión → sus inscritos vuelven a elegir, o a esperar si ya no queda ninguna.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioDisponibilidadSimulacion {

    private static final String ESPERANDO = "SIMULACION_POR_HABILITAR";
    private static final String PUEDE_ELEGIR = "SIMULACION_TURNO_CANDIDATO";

    private final PostulacionRepository postulaciones;
    private final SesionSimulacionRepository sesiones;
    private final SesionVacanteRepository sesionesVacante;
    private final InscripcionSesionRepository inscripciones;
    private final VacanteRepository vacantes;
    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final MaquinaEstados maquina;
    private final ServicioCorreo correo;

    /**
     * Vuelve a mirar las postulaciones de las vacantes que toca esta sesión y las coloca donde
     * corresponde.
     *
     * <p>Se llama después de crear, ampliar, llenar o cancelar una sesión. Es idempotente a
     * propósito: recalcula el estado que debería tener cada uno, en vez de intentar adivinar
     * qué cambió. Llamarla de más no rompe nada.
     */
    @Transactional
    public void recalcularPara(SesionSimulacion sesion) {
        List<Long> vacanteIds = sesionesVacante.findBySesionSimulacionId(sesion.getId()).stream()
                .map(SesionVacante::getVacanteId).toList();
        for (Long vacanteId : vacanteIds) {
            recalcularVacante(sesion.getOrganizacionId(), vacanteId);
        }
    }

    @Transactional
    public void recalcularVacante(Long organizacionId, Long vacanteId) {
        boolean hayDonde = sesiones.hayAlgunaDisponiblePara(organizacionId, vacanteId);

        for (Postulacion p : postulaciones.findByVacanteIdOrderByCreadoEnDesc(vacanteId)) {
            boolean tieneInscripcion = inscripciones
                    .findByPostulacionIdAndEsVigenteTrue(p.getId()).isPresent();

            if (ESPERANDO.equals(p.getEstadoCodigo()) && hayDonde) {
                maquina.transicionar(p, PUEDE_ELEGIR, null, null, true, false, null);
                avisarQueYaPuedeElegir(p);

            } else if (PUEDE_ELEGIR.equals(p.getEstadoCodigo()) && !hayDonde && !tieneInscripcion) {
                // Se quedó sin dónde inscribirse: vuelve a la bandeja del equipo como
                // pendiente de programar. No se le avisa: no hay nada que pueda hacer.
                maquina.transicionar(p, ESPERANDO, null, null, true, false, null);
            }
        }
    }

    /**
     * Al cancelar una sesión, sus inscritos pierden la inscripción y vuelven a elegir — o a
     * esperar, si esa era la última que quedaba.
     *
     * <p>La inscripción vieja no se borra: se marca como no vigente. Así queda el rastro de que
     * esa persona sí había elegido fecha, y no parece que nunca respondió.
     */
    @Transactional
    public void alCancelarse(SesionSimulacion sesion) {
        inscripciones.findBySesionSimulacionIdAndEsVigenteTrue(sesion.getId()).forEach(i -> {
            i.setEsVigente(false);
            inscripciones.save(i);
            postulaciones.findById(i.getPostulacionId()).ifPresent(this::avisarQueSeCancelo);
        });
        recalcularPara(sesion);
    }

    // ============ Avisos ============

    private void avisarQueYaPuedeElegir(Postulacion postulacion) {
        enviar(postulacion, "SESION_DISPONIBLE");
    }

    private void avisarQueSeCancelo(Postulacion postulacion) {
        enviar(postulacion, "SESION_CANCELADA");
    }

    private void enviar(Postulacion postulacion, String plantilla) {
        Usuario usuario = usuarios.findById(postulacion.getUsuarioId()).orElse(null);
        if (usuario == null) return;
        String nombre = personas.findById(usuario.getPersonaId()).map(Persona::getNombre).orElse("");
        String vacante = vacantes.findById(postulacion.getVacanteId()).map(Vacante::getTitulo).orElse("");
        correo.enviar(postulacion.getOrganizacionId(), usuario.getId(), usuario.getCorreo(), plantilla,
                Map.of("nombre", nombre == null ? "" : nombre, "vacante", vacante));
    }
}
