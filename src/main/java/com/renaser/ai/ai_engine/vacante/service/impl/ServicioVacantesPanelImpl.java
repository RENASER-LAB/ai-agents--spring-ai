package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.vacante.service.ServicioVacantesPanel;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.*;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.entity.*;
import com.renaser.ai.ai_engine.vacante.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServicioVacantesPanelImpl implements ServicioVacantesPanel {

    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;
    private final RequisitoObjetivoRepository requisitos;
    private final SolicitudTalentoRepository solicitudes;
    private final VersionPesosRepository versionesPesos;
    private final PlantillaEvaluacionRepository plantillas;
    private final VersionPlantillaPruebaRepository versionesPrueba;
    private final ServicioAuditoria auditoria;

    // ============ Puestos ============

    @Override
    @Transactional
    public Long crearPuesto(ContextoUsuario quien, GuardarPuesto datos) {
        Puesto puesto = puestos.save(Puesto.builder()
                .organizacionId(quien.organizacionId())
                .codigo(datos.codigo())
                .nombre(datos.nombre())
                .nivelPuestoCodigo(datos.nivelPuestoCodigo())
                .familiaCodigo(datos.familiaCodigo())
                .esActivo(true)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_puesto",
                "puesto", puesto.getId(), null, Map.of("codigo", datos.codigo()), null);
        return puesto.getId();
    }

    @Override
    public List<PuestoResponse> listarPuestos(ContextoUsuario quien) {
        return puestos.findByOrganizacionIdAndEsActivoTrueOrderByNombre(quien.organizacionId()).stream()
                .map(p -> new PuestoResponse(p.getId(), p.getCodigo(), p.getNombre(),
                        p.getNivelPuestoCodigo(), p.getFamiliaCodigo()))
                .toList();
    }

    // ============ Vacantes ============

    @Override
    @Transactional
    public Long crear(ContextoUsuario quien, GuardarVacante datos) {
        SolicitudTalento solicitud = solicitudes
                .findByIdAndOrganizacionId(datos.solicitudTalentoId(), quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de Talento", "id",
                        datos.solicitudTalentoId()));
        // Toda vacante cuelga de una solicitud aprobada por Dirección: es la regla del
        // cliente y se decidió incluirla en el MVP
        if (!"ABIERTA".equals(solicitud.getEstado())) {
            throw new IllegalStateException(
                    "La solicitud tiene que estar aprobada (ABIERTA) antes de crear la vacante; está "
                            + solicitud.getEstado());
        }
        puestos.findByIdAndOrganizacionId(datos.puestoId(), quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Puesto", "id", datos.puestoId()));

        // La versión de pesos publicada vigente. Elegir otra es de Dirección (hito 2).
        VersionPesos pesos = versionesPesos
                .findFirstByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(quien.organizacionId(), "PUBLICADA")
                .orElseThrow(() -> new IllegalStateException("No hay una versión de pesos publicada"));

        Vacante vacante = vacantes.save(Vacante.builder()
                .organizacionId(quien.organizacionId())
                .solicitudTalentoId(solicitud.getId())
                .puestoId(datos.puestoId())
                .titulo(datos.titulo())
                .descripcion(datos.descripcion())
                .proposito(datos.proposito())
                .responsabilidades(datos.responsabilidades())
                .requisitos(datos.requisitos())
                .modalidad(datos.modalidad())
                .horario(datos.horario())
                .ubicacion(datos.ubicacion())
                .compensacionPublica(datos.compensacionPublica())
                .tipoCierre(datos.tipoCierre())
                .plazas(datos.plazas())
                .abreEn(datos.abreEn())
                .cierraEn(datos.cierraEn())
                .estado("BORRADOR")
                .versionPesosId(pesos.getId())
                .responsableUsuarioId(datos.responsableUsuarioId())
                .creadoEn(Instant.now())
                .build());

        solicitud.setEstado("CON_VACANTE");
        solicitudes.save(solicitud);

        auditoria.registrar(quien.organizacionId(), quien, "crear_vacante",
                "vacante", vacante.getId(), null,
                Map.of("titulo", datos.titulo(), "solicitud", solicitud.getId()), null);
        return vacante.getId();
    }

    @Override
    @Transactional
    public void editar(ContextoUsuario quien, Long id, GuardarVacante datos) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        if ("CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada no se edita");
        }
        Map<String, Object> anterior = Map.of("titulo", vacante.getTitulo(),
                "descripcion", vacante.getDescripcion());
        vacante.setTitulo(datos.titulo());
        vacante.setDescripcion(datos.descripcion());
        vacante.setProposito(datos.proposito());
        vacante.setResponsabilidades(datos.responsabilidades());
        vacante.setRequisitos(datos.requisitos());
        vacante.setModalidad(datos.modalidad());
        vacante.setHorario(datos.horario());
        vacante.setUbicacion(datos.ubicacion());
        vacante.setCompensacionPublica(datos.compensacionPublica());
        vacante.setTipoCierre(datos.tipoCierre());
        vacante.setPlazas(datos.plazas());
        vacante.setAbreEn(datos.abreEn());
        vacante.setCierraEn(datos.cierraEn());
        vacante.setResponsableUsuarioId(datos.responsableUsuarioId());
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "editar_vacante",
                "vacante", id, anterior, Map.of("titulo", datos.titulo()), null);
    }

    @Override
    public List<VacantePanel> listar(ContextoUsuario quien) {
        return vacantes.findByOrganizacionIdOrderByCreadoEnDesc(quien.organizacionId()).stream()
                .map(this::comoPanel)
                .toList();
    }

    @Override
    public VacantePanel detalle(ContextoUsuario quien, Long id) {
        return comoPanel(laDeLaOrganizacion(quien, id));
    }

    // ============ Requisitos objetivos ============

    @Override
    public List<RequisitoPanel> requisitos(ContextoUsuario quien, Long vacanteId) {
        laDeLaOrganizacion(quien, vacanteId);
        return requisitos.findByVacanteId(vacanteId).stream()
                .map(r -> new RequisitoPanel(r.getId(), r.getDescripcion(), r.getRegla(), r.isEsActivo()))
                .toList();
    }

    @Override
    @Transactional
    public Long agregarRequisito(ContextoUsuario quien, Long vacanteId, GuardarRequisito datos) {
        laDeLaOrganizacion(quien, vacanteId);
        RequisitoObjetivo requisito = requisitos.save(RequisitoObjetivo.builder()
                .vacanteId(vacanteId)
                .descripcion(datos.descripcion())
                .regla(datos.regla())
                .esActivo(true)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "definir_requisito_objetivo",
                "requisito_objetivo", requisito.getId(), null,
                Map.of("regla", datos.regla(), "vacante", vacanteId), null);
        return requisito.getId();
    }

    @Override
    @Transactional
    public void desactivarRequisito(ContextoUsuario quien, Long vacanteId, Long requisitoId) {
        laDeLaOrganizacion(quien, vacanteId);
        RequisitoObjetivo requisito = requisitos.findById(requisitoId)
                .filter(r -> r.getVacanteId().equals(vacanteId))
                .orElseThrow(() -> new ResourceNotFoundException("Requisito objetivo", "id", requisitoId));
        // No se borra: se desactiva. Las postulaciones que ya detuvo siguen explicadas.
        requisito.setEsActivo(false);
        requisitos.save(requisito);
        auditoria.registrar(quien.organizacionId(), quien, "desactivar_requisito_objetivo",
                "requisito_objetivo", requisitoId, Map.of("esActivo", true), Map.of("esActivo", false), null);
    }

    // ============ Publicar y cerrar ============

    @Override
    @Transactional
    public void publicar(ContextoUsuario quien, Long id) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        if (!"BORRADOR".equals(vacante.getEstado())) {
            throw new IllegalStateException("Solo se publica una vacante en borrador; está " + vacante.getEstado());
        }
        // Sin plantilla no hay con qué armar la evaluación de quien postule. El error tiene
        // que salir aquí, al publicar, y no en la cara del primer candidato.
        if (vacante.getPlantillaEvaluacionId() == null) {
            throw new IllegalStateException(
                    "Antes de publicar hay que elegir la plantilla de evaluación de esta vacante");
        }
        // "Es obligatoria para todo puesto" (RF-73): mismo motivo que la evaluación.
        if (vacante.getVersionPlantillaPruebaId() == null) {
            throw new IllegalStateException(
                    "Antes de publicar hay que elegir la prueba del puesto de esta vacante");
        }
        vacante.setEstado("PUBLICADA");
        vacante.setPublicadaEn(Instant.now());
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_vacante",
                "vacante", id, Map.of("estado", "BORRADOR"), Map.of("estado", "PUBLICADA"), null);
    }

    @Override
    @Transactional
    public void asignarPlantillaEvaluacion(ContextoUsuario quien, Long id, Long plantillaEvaluacionId) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        PlantillaEvaluacion plantilla = plantillas
                .findByIdAndOrganizacionId(plantillaEvaluacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla de evaluación", "id", plantillaEvaluacionId));
        if (!"PUBLICADA".equals(plantilla.getEstado())) {
            throw new IllegalStateException(
                    "Esa plantilla todavía está en borrador: solo se puede usar una publicada");
        }
        // El nivel tiene que cuadrar: una evaluación de Dirección no sirve para un puesto de
        // ejecución, ni las preguntas ni los pesos.
        Puesto puesto = puestos.findById(vacante.getPuestoId())
                .orElseThrow(() -> new IllegalStateException("La vacante apunta a un puesto que no existe"));
        if (!puesto.getNivelPuestoCodigo().equals(plantilla.getNivelPuestoCodigo())) {
            throw new IllegalArgumentException("La plantilla es de nivel "
                    + plantilla.getNivelPuestoCodigo() + " y el puesto es de nivel "
                    + puesto.getNivelPuestoCodigo());
        }

        Long anterior = vacante.getPlantillaEvaluacionId();
        vacante.setPlantillaEvaluacionId(plantillaEvaluacionId);
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "asignar_plantilla_evaluacion",
                "vacante", id,
                anterior == null ? null : Map.of("plantillaEvaluacionId", String.valueOf(anterior)),
                Map.of("plantillaEvaluacionId", String.valueOf(plantillaEvaluacionId)), null);
    }

    @Override
    @Transactional
    public void asignarPlantillaPrueba(ContextoUsuario quien, Long id, Long versionPlantillaPruebaId) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        VersionPlantillaPrueba version = versionesPrueba.findById(versionPlantillaPruebaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Versión de prueba", "id", versionPlantillaPruebaId));
        if (!"PUBLICADA".equals(version.getEstado())) {
            throw new IllegalStateException(
                    "Esa versión todavía está en borrador: solo se puede usar una publicada");
        }

        Long anterior = vacante.getVersionPlantillaPruebaId();
        vacante.setVersionPlantillaPruebaId(versionPlantillaPruebaId);
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "asignar_plantilla_prueba",
                "vacante", id,
                anterior == null ? null : Map.of("versionPlantillaPruebaId", String.valueOf(anterior)),
                Map.of("versionPlantillaPruebaId", String.valueOf(versionPlantillaPruebaId)), null);
    }

    @Override
    @Transactional
    public void cerrar(ContextoUsuario quien, Long id, String motivo) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        if ("CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("La vacante ya está cerrada");
        }
        String anterior = vacante.getEstado();
        // Cerrar NO arrastra las postulaciones en marcha: cada una se decide una a una
        // desde la bandeja. Esto cambió con el documento nuevo del cliente.
        vacante.setEstado("CERRADA");
        vacante.setCerradaEn(Instant.now());
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "cerrar_vacante",
                "vacante", id, Map.of("estado", anterior), Map.of("estado", "CERRADA"), motivo);
    }

    // ============ ayudas ============

    private Vacante laDeLaOrganizacion(ContextoUsuario quien, Long id) {
        return vacantes.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", id));
    }

    private VacantePanel comoPanel(Vacante v) {
        return new VacantePanel(v.getId(), v.getTitulo(), v.getEstado(), v.getTipoCierre(),
                v.getPuestoId(), v.getSolicitudTalentoId(), v.getResponsableUsuarioId(),
                v.getPublicadaEn(), v.getCerradaEn());
    }
}
