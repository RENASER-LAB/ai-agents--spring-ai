package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPlantillaEvaluacion.*;
import com.renaser.ai.ai_engine.perfilintegral.entity.CuotaPlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.mapper.CuotaPlantillaEvaluacionMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.PlantillaEvaluacionMapper;
import com.renaser.ai.ai_engine.perfilintegral.repository.CuotaPlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPlantillasEvaluacion;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServicioPlantillasEvaluacionImpl implements ServicioPlantillasEvaluacion {

    private final PlantillaEvaluacionRepository plantillas;
    private final CuotaPlantillaEvaluacionRepository cuotas;
    private final PlantillaEvaluacionMapper plantillaMapper;
    private final CuotaPlantillaEvaluacionMapper cuotaMapper;
    private final ServicioAuditoria auditoria;
    private final Permisos permisos;
    private final DuenoDelInstrumento dueno;

    @Override
    @Transactional
    public Long crear(ContextoUsuario quien, CrearPlantilla datos) {
        PlantillaEvaluacion plantilla = plantillas.save(PlantillaEvaluacion.builder()
                .organizacionId(quien.organizacionId())
                .nombre(datos.nombre())
                .nivelPuestoCodigo(datos.nivelPuestoCodigo())
                .familiaCodigo(datos.familiaCodigo())
                .version(datos.version())
                .estado("BORRADOR")
                .minutosObjetivo(datos.minutosObjetivo())
                .vigenciaMeses(datos.vigenciaMeses())
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_plantilla_evaluacion",
                "plantilla_evaluacion", plantilla.getId(), null,
                Map.of("estado", "BORRADOR", "nombre", datos.nombre()), null);
        return plantilla.getId();
    }

    @Override
    public List<PlantillaResponse> listar(ContextoUsuario quien) {
        permisos.alcanceDe("elegir_plantilla_evaluacion");
        // El resolutor decide de quién son las plantillas que esta organización ve: las
        // suyas si personalizó, las de la plataforma si no.
        return plantillas.findByOrganizacionIdOrderByCreadoEnDesc(
                        dueno.duenoDe(quien.organizacionId(), Instrumento.PLANTILLA_EVALUACION)).stream()
                .map(plantillaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void publicar(ContextoUsuario quien, Long id) {
        PlantillaEvaluacion plantilla = laPropia(quien, id);
        if (!"BORRADOR".equals(plantilla.getEstado())) {
            throw new IllegalStateException("Solo se publica una plantilla en borrador; esta está " + plantilla.getEstado());
        }
        // RF-49: si la duración objetivo pasa de 60 minutos, se avisa antes de publicar.
        int minimoCuotas = cuotas.findByPlantillaEvaluacionId(id).size();
        if (minimoCuotas == 0) {
            throw new IllegalStateException("La plantilla no tiene ninguna cuota de preguntas definida");
        }
        plantilla.setEstado("PUBLICADA");
        plantilla.setPublicadaPorUsuarioId(quien.usuarioId());
        plantilla.setPublicadaEn(Instant.now());
        plantillas.save(plantilla);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_plantilla_evaluacion",
                "plantilla_evaluacion", id, Map.of("estado", "BORRADOR"), Map.of("estado", "PUBLICADA"), null);
    }

    @Override
    @Transactional
    public Long agregarCuota(ContextoUsuario quien, Long plantillaId, CrearCuota datos) {
        PlantillaEvaluacion plantilla = laPropia(quien, plantillaId);
        if (!"BORRADOR".equals(plantilla.getEstado())) {
            throw new IllegalStateException("No se pueden agregar cuotas a una plantilla ya publicada");
        }
        if (datos.cantidadMin() > datos.cantidadMax()) {
            throw new IllegalArgumentException("cantidadMin no puede ser mayor que cantidadMax");
        }
        CuotaPlantillaEvaluacion cuota = cuotas.save(CuotaPlantillaEvaluacion.builder()
                .plantillaEvaluacionId(plantillaId)
                .tipoBanco(datos.tipoBanco())
                .tipoPregunta(datos.tipoPregunta())
                .dimensionCodigo(datos.dimensionCodigo())
                .cantidadMin(datos.cantidadMin())
                .cantidadMax(datos.cantidadMax())
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "agregar_cuota_plantilla",
                "cuota_plantilla_evaluacion", cuota.getId(), null,
                Map.of("tipoBanco", datos.tipoBanco()), null);
        return cuota.getId();
    }

    @Override
    public List<CuotaResponse> listarCuotas(ContextoUsuario quien, Long plantillaId) {
        permisos.alcanceDe("elegir_plantilla_evaluacion");
        laVisible(quien, plantillaId);
        return cuotas.findByPlantillaEvaluacionId(plantillaId).stream()
                .map(cuotaMapper::toResponse)
                .toList();
    }

    // Leer resuelve, editar no: con la bandera apagada la organización VE las plantillas
    // de la plataforma —eso contesta el resolutor— pero solo edita las suyas.

    private PlantillaEvaluacion laVisible(ContextoUsuario quien, Long id) {
        return plantillas.findByIdAndOrganizacionId(id, quien.organizacionId())
                .or(() -> plantillas.findByIdAndOrganizacionId(
                        id, dueno.duenoDe(quien.organizacionId(), Instrumento.PLANTILLA_EVALUACION)))
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de evaluación", "id", id));
    }

    private PlantillaEvaluacion laPropia(ContextoUsuario quien, Long id) {
        return plantillas.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de evaluación", "id", id));
    }
}
