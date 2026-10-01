package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.organizacion.service.CopiadorDeInstrumentos;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.perfilintegral.entity.CuotaPlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.repository.CuotaPlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ver {@link CopiadorDeInstrumentos}.
 *
 * <p>Los pesos van por INSERT…SELECT, el patrón de la V17: sus hijas cuelgan de la versión y
 * de catálogos globales, sin ids que remapear. Las plantillas de evaluación, fila a fila.
 *
 * <p>⚠️ <b>El banco y las pruebas ya no se copian</b> (V67, decisión 13): cada empresa escribe
 * sus preguntas y su prueba técnica en cada vacante. El código que copiaba el catálogo de
 * RENASER se retiró; quien ya tenía su copia la conserva, porque es suya.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CopiadorDeInstrumentosImpl implements CopiadorDeInstrumentos {

    private final DuenoDelInstrumento resolutor;
    private final VersionPesosRepository versionesPesos;
    private final PlantillaEvaluacionRepository plantillasEvaluacion;
    private final CuotaPlantillaEvaluacionRepository cuotas;
    private final JdbcTemplate jdbc;

    // ============ Los pesos ============

    @Override
    @Transactional
    public Map<String, Integer> copiarPesos(Long organizacionDestino) {
        Long plataforma = resolutor.plataforma().getId();
        VersionPesos vigente = versionesPesos
                .findFirstByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(plataforma, "PUBLICADA")
                .orElseThrow(() -> new IllegalStateException(
                        "La plataforma no tiene una versión de pesos publicada que copiar"));

        Instant ahora = Instant.now();
        VersionPesos nueva = versionesPesos.save(VersionPesos.builder()
                .organizacionId(organizacionDestino)
                .etiqueta(vigente.getEtiqueta())
                .estado("PUBLICADA")
                .publicadaEn(ahora)
                .creadoEn(ahora)
                .copiadaDeVersionId(vigente.getId())
                .build());

        // Las cuatro tablas de reparto, al estilo V17: cuelgan de la versión y de
        // catálogos globales (etapas, dimensiones, criterios), sin ids que remapear.
        Map<String, Integer> conteos = conteosVacios();
        conteos.put("version_pesos", 1);
        conteos.put("peso_etapa", jdbc.update("""
                INSERT INTO peso_etapa (version_pesos_id, etapa_codigo, peso, creado_en)
                SELECT ?, etapa_codigo, peso, now() FROM peso_etapa WHERE version_pesos_id = ?""",
                nueva.getId(), vigente.getId()));
        conteos.put("peso_componente_perfil", jdbc.update("""
                INSERT INTO peso_componente_perfil (version_pesos_id, componente, peso, creado_en)
                SELECT ?, componente, peso, now() FROM peso_componente_perfil WHERE version_pesos_id = ?""",
                nueva.getId(), vigente.getId()));
        conteos.put("peso_dimension", jdbc.update("""
                INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso, creado_en)
                SELECT ?, nivel_puesto_codigo, dimension_codigo, peso, now() FROM peso_dimension WHERE version_pesos_id = ?""",
                nueva.getId(), vigente.getId()));
        conteos.put("peso_criterio", jdbc.update("""
                INSERT INTO peso_criterio (version_pesos_id, nivel_puesto_codigo, criterio_id, peso, creado_en)
                SELECT ?, nivel_puesto_codigo, criterio_id, peso, now() FROM peso_criterio WHERE version_pesos_id = ?""",
                nueva.getId(), vigente.getId()));
        log.info("Pesos copiados a la organización {}: {}", organizacionDestino, conteos);
        return conteos;
    }

    // ============ Las plantillas de evaluación ============

    @Override
    @Transactional
    public Map<String, Integer> copiarPlantillasEvaluacion(Long organizacionDestino) {
        Long plataforma = resolutor.plataforma().getId();
        List<PlantillaEvaluacion> publicadas = plantillasEvaluacion
                .findByOrganizacionIdOrderByCreadoEnDesc(plataforma).stream()
                .filter(p -> "PUBLICADA".equals(p.getEstado()))
                .toList();
        if (publicadas.isEmpty()) {
            throw new IllegalStateException(
                    "La plataforma no tiene ninguna plantilla de evaluación publicada que copiar");
        }

        Instant ahora = Instant.now();
        Map<String, Integer> conteos = conteosVacios("plantilla_evaluacion", "cuota_plantilla_evaluacion");
        for (PlantillaEvaluacion vieja : publicadas) {
            PlantillaEvaluacion nueva = plantillasEvaluacion.save(PlantillaEvaluacion.builder()
                    .organizacionId(organizacionDestino)
                    .nombre(vieja.getNombre())
                    .nivelPuestoCodigo(vieja.getNivelPuestoCodigo())
                    .familiaCodigo(vieja.getFamiliaCodigo())
                    .version(vieja.getVersion())
                    .estado("PUBLICADA")
                    .minutosObjetivo(vieja.getMinutosObjetivo())
                    .vigenciaMeses(vieja.getVigenciaMeses())
                    .publicadaEn(ahora)
                    .creadoEn(ahora)
                    .copiadaDeVersionId(vieja.getId())
                    .build());
            sumar(conteos, "plantilla_evaluacion", 1);
            for (CuotaPlantillaEvaluacion cuota : cuotas.findByPlantillaEvaluacionId(vieja.getId())) {
                cuotas.save(CuotaPlantillaEvaluacion.builder()
                        .plantillaEvaluacionId(nueva.getId())
                        .tipoBanco(cuota.getTipoBanco())
                        .tipoPregunta(cuota.getTipoPregunta())
                        .dimensionCodigo(cuota.getDimensionCodigo())
                        .cantidadMin(cuota.getCantidadMin())
                        .cantidadMax(cuota.getCantidadMax())
                        .creadoEn(ahora)
                        .build());
                sumar(conteos, "cuota_plantilla_evaluacion", 1);
            }
        }
        log.info("Plantillas de evaluación copiadas a la organización {}: {}", organizacionDestino, conteos);
        return conteos;
    }

    // ============ Apoyo ============

    private static Map<String, Integer> conteosVacios(String... tablas) {
        Map<String, Integer> conteos = new LinkedHashMap<>();
        for (String tabla : tablas) {
            conteos.put(tabla, 0);
        }
        return conteos;
    }

    private static void sumar(Map<String, Integer> conteos, String tabla, int cuantas) {
        conteos.merge(tabla, cuantas, Integer::sum);
    }
}
