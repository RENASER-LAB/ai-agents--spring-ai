package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.CertificacionItem;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EducacionItem;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EnlaceItem;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.ExperienciaItem;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.IdiomaItem;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.LecturaCv;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.Pretension;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EducacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.ExperienciaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.IdiomaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Convierte el perfil de una persona en su DTO completo. Lo comparten el portal (el dueño
 * viéndose a sí mismo) y el panel (el equipo leyendo la ficha): es exactamente el mismo
 * contenido — lo único que cambia entre puertas es si la pretensión viaja, y eso lo decide
 * cada servicio, no este pintor.
 */
@Service
@RequiredArgsConstructor
public class PintorDePerfil {

    private final PerfilCandidatoRepository perfiles;
    private final ExperienciaPerfilRepository experiencias;
    private final EducacionPerfilRepository educaciones;
    private final IdiomaPerfilRepository idiomas;
    private final CertificacionPerfilRepository certificaciones;
    private final EnlacePerfilRepository enlaces;
    private final PostulacionRepository postulaciones;
    private final CvRepository cvs;
    private final DatoCvRepository datosCv;
    private final com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa cola;

    public PerfilCompleto pintar(Long personaId) {
        Optional<PerfilCandidato> perfil = perfiles.findByPersonaId(personaId);
        if (perfil.isEmpty()) {
            // Un perfil que nunca se lleno responde 200 con todo vacio, no 404: la pantalla
            // siempre tiene algo que pintar.
            return new PerfilCompleto(null, null, List.of(), null, null, null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    lecturaDe(personaId, null));
        }
        PerfilCandidato p = perfil.get();
        Long id = p.getId();
        return new PerfilCompleto(
                p.getTitular(), p.getResumen(), habilidadesDe(p), p.getExperienciaMeses(),
                p.getUbicacion(), p.getDisponibilidad(), pretensionDe(p),
                experiencias.findByPerfilCandidatoIdOrderByOrden(id).stream()
                        .map(e -> new ExperienciaItem(e.getId(), e.getPuesto(), e.getEmpresa(),
                                e.getDesde(), e.getHasta(), e.getDescripcion(), e.getOrigen(),
                                e.getConfirmadoEn() != null))
                        .toList(),
                educaciones.findByPerfilCandidatoIdOrderByOrden(id).stream()
                        .map(e -> new EducacionItem(e.getId(), e.getTitulo(), e.getInstitucion(),
                                e.getNivelCodigo(), e.getDesde(), e.getHasta(), e.isEnCurso(),
                                e.getOrigen(), e.getConfirmadoEn() != null))
                        .toList(),
                idiomas.findByPerfilCandidatoIdOrderByIdioma(id).stream()
                        .map(i -> new IdiomaItem(i.getId(), i.getIdioma(), i.getNivelCodigo(),
                                i.getOrigen(), i.getConfirmadoEn() != null))
                        .toList(),
                certificaciones.findByPerfilCandidatoIdOrderByNombre(id).stream()
                        .map(c -> new CertificacionItem(c.getId(), c.getNombre(), c.getEntidad(),
                                c.getEmitidaEn(), c.getVenceEn(), c.getOrigen(),
                                c.getConfirmadoEn() != null))
                        .toList(),
                enlaces.findByPerfilCandidatoIdOrderByTipo(id).stream()
                        .map(e -> new EnlaceItem(e.getId(), e.getTipo(), e.getUrl()))
                        .toList(),
                lecturaDe(personaId, p.getActualizadoEn()));
    }

    /** El mismo perfil sin la pretensión: para quien no tiene el permiso de verla. */
    public PerfilCompleto sinPretension(PerfilCompleto completo) {
        return new PerfilCompleto(completo.titular(), completo.resumen(),
                completo.habilidades(), completo.experienciaMeses(), completo.ubicacion(),
                completo.disponibilidad(), null, completo.experiencia(), completo.educacion(),
                completo.idiomas(), completo.certificaciones(), completo.enlaces(),
                completo.lecturaCv());
    }

    /**
     * En que punto esta la lectura del ultimo curriculum. Se deriva, no se guarda: el
     * estado real vive en los trabajos de la cola y en dato_cv, y duplicarlo seria
     * inventarse una segunda fuente de verdad.
     */
    private LecturaCv lecturaDe(Long personaId, Instant actualizadoEn) {
        List<Postulacion> suyas = postulaciones.deLaPersona(personaId);
        Postulacion ultima = suyas.stream()
                .filter(p -> cvs.findByPostulacionId(p.getId()).isPresent())
                .findFirst().orElse(null);
        if (ultima == null) {
            return new LecturaCv("SIN_CV", actualizadoEn);
        }
        // La mas reciente con curriculum manda: refleja el ultimo archivo subido.
        if (datosCv.findByPostulacionId(ultima.getId()).isPresent()) {
            return new LecturaCv("LISTA", actualizadoEn);
        }
        // Se pregunta por la interfaz acordada de la cola, no por sus tablas: la frontera
        // con el motor de agentes se cruza solo por las clases pactadas.
        //
        // Y se pregunta por LA LECTURA, no por el retrato: `comoVa` mira los cuatro agentes
        // juntos, asi que un evaluador caido decia «no se pudo leer» de un curriculum bien
        // leido, y un retrato terminado sin ficha se quedaba en «leyendo» para siempre.
        if ("EN_CURSO".equals(cola.comoVaLaLectura(ultima.getId()))) {
            return new LecturaCv("EN_CURSO", actualizadoEn);
        }
        // Hay archivo, no hay ficha y no queda nada corriendo. Da igual si el PDF estaba
        // escaneado, si se agoto en reintentos o si nadie llego a pedir la lectura: de ese
        // archivo no salio nada, y lo que la pantalla ofrece en los tres casos es lo mismo,
        // llenarlo a mano. No es un error: se prefirio no leer nada antes que inventar datos.
        return new LecturaCv("NO_LEGIBLE", actualizadoEn);
    }

    private static List<String> habilidadesDe(PerfilCandidato p) {
        if (p.getHabilidades() == null || p.getHabilidades().isBlank()) {
            return List.of();
        }
        return Arrays.stream(p.getHabilidades().split("\\|")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList();
    }

    private static Pretension pretensionDe(PerfilCandidato p) {
        if (p.getPretensionMin() == null) {
            return null;
        }
        return new Pretension(p.getPretensionMin(), p.getPretensionMax(),
                p.getPretensionMoneda());
    }
}
