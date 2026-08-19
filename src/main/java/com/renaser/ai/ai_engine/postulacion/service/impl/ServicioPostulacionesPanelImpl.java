package com.renaser.ai.ai_engine.postulacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.*;
import com.renaser.ai.ai_engine.archivo.repository.*;
import com.renaser.ai.ai_engine.archivo.service.*;
import com.renaser.ai.ai_engine.auditoria.entity.*;
import com.renaser.ai.ai_engine.auditoria.repository.*;
import com.renaser.ai.ai_engine.auditoria.service.*;
import com.renaser.ai.ai_engine.notificacion.entity.*;
import com.renaser.ai.ai_engine.notificacion.repository.*;
import com.renaser.ai.ai_engine.notificacion.service.*;
import com.renaser.ai.ai_engine.parametro.entity.*;
import com.renaser.ai.ai_engine.parametro.repository.*;
import com.renaser.ai.ai_engine.parametro.service.*;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.postulacion.service.ServicioPostulacionesPanel;
import com.renaser.ai.ai_engine.prueba.service.ServicioPrueba;
import com.renaser.ai.ai_engine.simulacion.service.ServicioDisponibilidadSimulacion;
import com.renaser.ai.ai_engine.validacion.service.ServicioValidacion;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.*;
import com.renaser.ai.ai_engine.postulacion.entity.*;
import com.renaser.ai.ai_engine.postulacion.repository.*;
import com.renaser.ai.ai_engine.postulacion.service.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ServicioPostulacionesPanelImpl implements ServicioPostulacionesPanel {

    private static final Set<String> ESPERAS = Set.of("CANDIDATO", "SISTEMA", "TALENTO", "AREA");

    private final PostulacionRepository postulaciones;
    private final EstadoPostulacionRepository estados;
    private final TransicionEstadoRepository transiciones;
    private final VacanteRepository vacantes;
    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final CvRepository cvs;
    private final EnlaceCvRepository enlaces;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final MaquinaEstados maquina;
    private final Permisos permisos;
    private final ServicioPrueba prueba;
    private final ServicioValidacion validacion;
    private final ServicioDisponibilidadSimulacion disponibilidad;

    @Override
    public List<FilaBandeja> bandeja(ContextoUsuario quien, String esperaA) {
        if (!ESPERAS.contains(esperaA)) {
            throw new IllegalArgumentException("espera_a tiene que ser CANDIDATO, SISTEMA, TALENTO o AREA");
        }
        FiltroAlcance alcance = permisos.alcanceDe("ver_candidatos");
        Map<String, EstadoPostulacion> catalogo = catalogoEstados();
        return postulaciones.bandeja(quien.organizacionId(), esperaA, alcance.responsableOFiltroNulo())
                .stream()
                .map(p -> filaBandeja(p, catalogo))
                .toList();
    }

    @Override
    public ConteoEmbudo embudo(ContextoUsuario quien, Long vacanteId) {
        vacanteVisible(quien, vacanteId, "ver_embudo");
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (Object[] fila : postulaciones.embudo(vacanteId)) {
            conteo.put((String) fila[0], (Long) fila[1]);
        }
        return new ConteoEmbudo(conteo);
    }

    @Override
    public FichaPostulacion ficha(ContextoUsuario quien, Long postulacionId) {
        Postulacion p = laVisible(quien, postulacionId, "abrir_ficha_candidato");
        Usuario usuario = usuarios.findById(p.getUsuarioId()).orElseThrow();
        String candidato = personas.findById(usuario.getPersonaId())
                .map(per -> nombreCompleto(per)).orElse("(anonimizado)");
        String vacante = vacantes.findById(p.getVacanteId()).map(Vacante::getTitulo).orElse("");
        String nombreEstado = estados.findById(p.getEstadoCodigo())
                .map(EstadoPostulacion::getNombre).orElse(p.getEstadoCodigo());

        Cv cv = cvs.findByPostulacionId(p.getId()).orElse(null);
        List<String> urls = cv == null ? List.of()
                : enlaces.findByCvId(cv.getId()).stream().map(EnlaceCv::getUrl).toList();

        return new FichaPostulacion(p.getId(), p.getUuid().toString(), candidato, usuario.getCorreo(),
                vacante, p.getEstadoCodigo(), nombreEstado, p.getGrupoPrioridad(), p.getMotivoCierre(),
                cv == null ? null : cv.getResultadoOrgulloso(), urls,
                cv == null ? null : cv.getArchivoOriginalId(), p.getCreadoEn(), p.getMovidoEn());
    }

    @Override
    public List<PasoHistorial> historial(ContextoUsuario quien, Long postulacionId) {
        laVisible(quien, postulacionId, "abrir_ficha_candidato");
        return transiciones.findByPostulacionIdOrderByOcurridaEnAsc(postulacionId).stream()
                .map(t -> new PasoHistorial(t.getEstadoAnteriorCodigo(), t.getEstadoNuevoCodigo(),
                        t.getUsuarioId(), t.isEsSistema(), t.isEsPorLote(), t.getMotivo(), t.getOcurridaEn()))
                .toList();
    }

    @Override
    @Transactional
    public void transicionar(ContextoUsuario quien, Long postulacionId, Transicionar datos) {
        Postulacion p = laVisible(quien, postulacionId, "mover_postulacion");
        // Si el destino es un cierre, hace falta decir de qué clase
        String motivoCierre = datos.motivoCierre();
        if ("CERRADA".equals(datos.estadoDestino()) && motivoCierre == null) {
            motivoCierre = "CIERRE_MANUAL";
        }
        if ("NO_CONTINUA".equals(datos.estadoDestino()) && motivoCierre == null) {
            motivoCierre = "DECISION_PERSONA";
        }
        maquina.transicionar(p, datos.estadoDestino(), quien, datos.motivo(), false, false, motivoCierre);
    }

    @Override
    @Transactional
    public void confirmarAvance(ContextoUsuario quien, Long postulacionId, String motivo) {
        Postulacion p = laVisible(quien, postulacionId, "confirmar_avance");
        EstadoPostulacion siguiente = maquina.siguiente(p.getEstadoCodigo())
                .orElseThrow(() -> new IllegalStateException(
                        "Desde " + p.getEstadoCodigo() + " no hay un avance que calcular: "
                                + "usa una transición manual con motivo"));

        // Al entrar a su turno para la prueba, se le crea el intento: mismo patrón que la
        // evaluación del hito 2, la versión de la plantilla queda fijada aquí y no cambia
        // aunque después se publique otra (RF-90).
        if ("PRUEBA_TURNO_CANDIDATO".equals(siguiente.getCodigo())) {
            Vacante vacante = vacantes.findById(p.getVacanteId())
                    .orElseThrow(() -> new IllegalStateException("La vacante de esta postulación ya no existe"));
            if (vacante.getVersionPlantillaPruebaId() == null) {
                throw new IllegalStateException(
                        "Esta vacante no tiene plantilla de prueba asignada: no se puede avanzar");
            }
            prueba.crearAlEntrar(quien.organizacionId(), p.getId(), vacante.getVersionPlantillaPruebaId());
        }

        // Al entrar a validación se crea su periodo, en POR_HABILITAR: alguien tiene que
        // decidir la modalidad —y, si es trabajo real, registrar la figura contractual—
        // antes de que empiece a correr.
        if ("VALIDACION_POR_HABILITAR".equals(siguiente.getCodigo())) {
            validacion.crearAlEntrar(p.getId(), quien.organizacionId());
        }

        maquina.transicionar(p, siguiente.getCodigo(), quien, motivo, false, false, null);

        // Entrar a simulación no crea nada: la inscripción la elige el candidato. Lo que sí
        // hace falta es mirar si ya hay una sesión con cupo para su vacante, porque de eso
        // depende si se queda esperando o puede elegir ya.
        if ("SIMULACION_POR_HABILITAR".equals(siguiente.getCodigo())) {
            disponibilidad.recalcularVacante(p.getOrganizacionId(), p.getVacanteId());
        }
    }

    @Override
    public byte[] descargarArchivo(ContextoUsuario quien, Long archivoId, StringBuilder nombreSalida) {
        permisos.alcanceDe("descargar_entregables");
        Archivo archivo = archivos.findByIdAndOrganizacionId(archivoId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Archivo", "id", archivoId));
        nombreSalida.append(archivo.getNombreOriginal() == null ? "archivo" : archivo.getNombreOriginal());
        return almacen.leer(archivo);
    }

    // ============ ayudas ============

    private Map<String, EstadoPostulacion> catalogoEstados() {
        Map<String, EstadoPostulacion> mapa = new HashMap<>();
        estados.findAll().forEach(e -> mapa.put(e.getCodigo(), e));
        return mapa;
    }

    private FilaBandeja filaBandeja(Postulacion p, Map<String, EstadoPostulacion> catalogo) {
        EstadoPostulacion estado = catalogo.get(p.getEstadoCodigo());
        String candidato = usuarios.findById(p.getUsuarioId())
                .flatMap(u -> personas.findById(u.getPersonaId()))
                .map(this::nombreCompleto)
                .orElse("(anonimizado)");
        String vacante = vacantes.findById(p.getVacanteId()).map(Vacante::getTitulo).orElse("");
        return new FilaBandeja(p.getId(), p.getUuid().toString(), candidato, vacante,
                p.getEstadoCodigo(), estado == null ? "" : estado.getNombre(),
                estado == null ? "" : estado.getEsperaA(), p.getGrupoPrioridad(),
                Duration.between(p.getMovidoEn(), Instant.now()).toDays());
    }

    private String nombreCompleto(Persona persona) {
        if (persona.getAnonimizadoEn() != null) return "(anonimizado)";
        return ((persona.getNombre() == null ? "" : persona.getNombre()) + " "
                + (persona.getApellidos() == null ? "" : persona.getApellidos())).trim();
    }

    // El alcance SUS_VACANTES se comprueba contra el responsable de la vacante:
    // es el único vínculo usuario-vacante que existe en el hito 1
    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId, String permiso) {
        Postulacion p = postulaciones.findByIdAndOrganizacionId(postulacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        FiltroAlcance alcance = permisos.alcanceDe(permiso);
        if (alcance.tipo() == FiltroAlcance.Tipo.SUS_VACANTES) {
            boolean esSuya = vacantes.findById(p.getVacanteId())
                    .map(v -> quien.usuarioId().equals(v.getResponsableUsuarioId()))
                    .orElse(false);
            if (!esSuya) {
                throw new ResourceNotFoundException("Postulación", "id", postulacionId);
            }
        }
        return p;
    }

    private void vacanteVisible(ContextoUsuario quien, Long vacanteId, String permiso) {
        Vacante v = vacantes.findByIdAndOrganizacionId(vacanteId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
        FiltroAlcance alcance = permisos.alcanceDe(permiso);
        if (alcance.tipo() == FiltroAlcance.Tipo.SUS_VACANTES
                && !quien.usuarioId().equals(v.getResponsableUsuarioId())) {
            throw new ResourceNotFoundException("Vacante", "id", vacanteId);
        }
    }
}
