package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.organizacion.service.CopiadorDeInstrumentos;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.perfilintegral.entity.CampoCaso;
import com.renaser.ai.ai_engine.perfilintegral.entity.CuotaPlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.ParConsistencia;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.RangoPregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CampoCasoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.CuotaPlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ParConsistenciaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RangoPreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.VarianteCambio;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ver {@link CopiadorDeInstrumentos}.
 *
 * <p>El banco se copia iterando en Java y no con INSERT…SELECT: sus tablas hijas apuntan
 * a la pregunta y a la opción por id, así que cada fila copiada necesita el mapa
 * id-viejo → id-nuevo que solo existe después de insertar. Son ~190 preguntas: barato.
 * Las cuatro tablas sin entidad JPA (opcion_dimension, multiplicador_bloque,
 * umbral_nivel, filtro_eliminatorio) van por JdbcTemplate. Los pesos sí van por
 * INSERT…SELECT, el patrón de la V17: sus hijas cuelgan de la versión y de catálogos
 * globales, sin ids que remapear.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CopiadorDeInstrumentosImpl implements CopiadorDeInstrumentos {

    private final DuenoDelInstrumento resolutor;
    private final VersionBancoRepository versionesBanco;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final RangoPreguntaRepository rangos;
    private final CampoCasoRepository camposCaso;
    private final ParConsistenciaRepository pares;
    private final PreguntaDimensionRepository preguntaDimensiones;
    private final VersionPesosRepository versionesPesos;
    private final PlantillaEvaluacionRepository plantillasEvaluacion;
    private final CuotaPlantillaEvaluacionRepository cuotas;
    private final PlantillaPruebaRepository plantillasPrueba;
    private final VersionPlantillaPruebaRepository versionesPrueba;
    private final VarianteCambioRepository variantes;
    private final PreguntaVersionPlantillaRepository preguntasElegidas;
    private final EntregableRequeridoRepository entregablesRequeridos;
    private final CriterioRepository criterios;
    private final JdbcTemplate jdbc;

    // ============ El banco ============

    @Override
    @Transactional
    public Map<String, Integer> copiarBanco(Long organizacionDestino) {
        Long plataforma = resolutor.plataforma().getId();
        List<VersionBanco> publicadas =
                versionesBanco.findByOrganizacionIdAndEstado(plataforma, "PUBLICADA");
        if (publicadas.isEmpty()) {
            throw new IllegalStateException("La plataforma no tiene ningún banco publicado que copiar");
        }

        Map<String, Integer> conteos = conteosVacios("version_banco", "pregunta", "opcion",
                "opcion_dimension", "pregunta_dimension", "rango_pregunta", "campo_caso",
                "par_consistencia", "multiplicador_bloque", "umbral_nivel", "filtro_eliminatorio");
        for (VersionBanco vieja : publicadas) {
            copiarVersionBanco(vieja, organizacionDestino, conteos);
        }
        log.info("Banco copiado a la organización {}: {}", organizacionDestino, conteos);
        return conteos;
    }

    private void copiarVersionBanco(VersionBanco vieja, Long destino, Map<String, Integer> conteos) {
        Instant ahora = Instant.now();
        VersionBanco nueva = versionesBanco.save(VersionBanco.builder()
                .organizacionId(destino)
                .tipoBanco(vieja.getTipoBanco())
                .nivelPuestoCodigo(vieja.getNivelPuestoCodigo())
                .etiqueta(vieja.getEtiqueta())
                .estado("PUBLICADA")
                .publicadaEn(ahora)
                .creadoEn(ahora)
                .copiadaDeVersionId(vieja.getId())
                // Sin esto, un banco CAZATALENTOS copiado caería al motor de claves y
                // calificaría a todos con 0 en silencio: el método viaja con la versión.
                .metodoCalificacion(vieja.getMetodoCalificacion())
                // Y el tiempo, por lo mismo: son SUS preguntas las que se tardan en
                // responder. Sin esta línea la copia nace en null y el examen de la
                // empresa nueva cae al minutaje de la plantilla, que es el que la V44
                // vino a corregir.
                .minutosObjetivo(vieja.getMinutosObjetivo())
                .build());
        sumar(conteos, "version_banco", 1);

        // Las preguntas, guardando el mapa id-viejo → id-nuevo del que cuelga todo lo demás
        Map<Long, Long> mapaPreguntas = new HashMap<>();
        List<Pregunta> viejas = preguntas.findByVersionBancoIdOrderByOrden(vieja.getId());
        for (Pregunta p : viejas) {
            Pregunta copia = preguntas.save(Pregunta.builder()
                    .versionBancoId(nueva.getId())
                    .codigo(p.getCodigo())
                    .bloque(p.getBloque())
                    .tipo(p.getTipo())
                    .enunciado(p.getEnunciado())
                    .situacion(p.getSituacion())
                    .logicaInterna(p.getLogicaInterna())
                    .esPuntuable(p.isEsPuntuable())
                    .orden(p.getOrden())
                    .peso(p.getPeso())
                    .esClave(p.isEsClave())
                    .esEliminatorio(p.isEsEliminatorio())
                    .casosPedidos(p.getCasosPedidos())
                    .rangosDePreguntaCodigo(p.getRangosDePreguntaCodigo())
                    .formulaPuntaje(p.getFormulaPuntaje())
                    // La guía del evaluador del banco CAZATALENTOS: sin ella el agente
                    // calificaría a ojo, que es lo que el instrumento prohíbe.
                    .c3Esperado(p.getC3Esperado())
                    .c4Esperado(p.getC4Esperado())
                    .senalDeCero(p.getSenalDeCero())
                    .presencial(p.isPresencial())
                    .creadoEn(ahora)
                    .build());
            mapaPreguntas.put(p.getId(), copia.getId());
        }
        sumar(conteos, "pregunta", viejas.size());

        List<Long> idsViejos = viejas.stream().map(Pregunta::getId).toList();
        if (!idsViejos.isEmpty()) {
            copiarHijasDePregunta(vieja.getId(), idsViejos, mapaPreguntas, ahora, conteos);
        }

        // Las hijas de la versión: los pares remapean sus dos preguntas; las tres tablas
        // de reglas (multiplicadores, umbrales, filtros) solo cambian de versión — los
        // filtros nombran preguntas por CÓDIGO, y el código se copió tal cual.
        for (ParConsistencia par : pares.findByVersionBancoId(vieja.getId())) {
            pares.save(ParConsistencia.builder()
                    .versionBancoId(nueva.getId())
                    .preguntaAId(mapaPreguntas.get(par.getPreguntaAId()))
                    .preguntaBId(mapaPreguntas.get(par.getPreguntaBId()))
                    .diferenciaMaxima(par.getDiferenciaMaxima())
                    .penalizacionPorcentaje(par.getPenalizacionPorcentaje())
                    .separacionMinimaItems(par.getSeparacionMinimaItems())
                    .condicion(par.getCondicion())
                    .creadoEn(ahora)
                    .build());
            sumar(conteos, "par_consistencia", 1);
        }
        sumar(conteos, "multiplicador_bloque", jdbc.update("""
                INSERT INTO multiplicador_bloque (version_banco_id, familia_documento, familia_codigo, bloque, multiplicador, creado_en)
                SELECT ?, familia_documento, familia_codigo, bloque, multiplicador, now()
                FROM multiplicador_bloque WHERE version_banco_id = ?""",
                nueva.getId(), vieja.getId()));
        sumar(conteos, "umbral_nivel", jdbc.update("""
                INSERT INTO umbral_nivel (version_banco_id, porcentaje_min, resultado, nivel, creado_en)
                SELECT ?, porcentaje_min, resultado, nivel, now()
                FROM umbral_nivel WHERE version_banco_id = ?""",
                nueva.getId(), vieja.getId()));
        sumar(conteos, "filtro_eliminatorio", jdbc.update("""
                INSERT INTO filtro_eliminatorio (version_banco_id, codigo, descripcion, preguntas, creado_en)
                SELECT ?, codigo, descripcion, preguntas, now()
                FROM filtro_eliminatorio WHERE version_banco_id = ?""",
                nueva.getId(), vieja.getId()));
    }

    private void copiarHijasDePregunta(Long versionViejaId, List<Long> idsViejos,
                                       Map<Long, Long> mapaPreguntas,
                                       Instant ahora, Map<String, Integer> conteos) {
        // Las opciones, con su propio mapa: opcion_dimension cuelga de ellas
        Map<Long, Long> mapaOpciones = new HashMap<>();
        List<Opcion> opcionesViejas = opciones.findByPreguntaIdIn(idsViejos);
        for (Opcion o : opcionesViejas) {
            Opcion copia = opciones.save(Opcion.builder()
                    .preguntaId(mapaPreguntas.get(o.getPreguntaId()))
                    .letra(o.getLetra())
                    .texto(o.getTexto())
                    .puntaje(o.getPuntaje())
                    .valor(o.getValor())
                    .esDistractor(o.isEsDistractor())
                    .ordenCorrecto(o.getOrdenCorrecto())
                    .creadoEn(ahora)
                    .build());
            mapaOpciones.put(o.getId(), copia.getId());
        }
        sumar(conteos, "opcion", opcionesViejas.size());

        // opcion_dimension no tiene entidad JPA: se lee crudo y se inserta con el id nuevo
        for (Map<String, Object> fila : jdbc.queryForList("""
                SELECT od.opcion_id, od.dimension_codigo, od.incremento
                FROM opcion_dimension od
                JOIN opcion o ON o.id = od.opcion_id
                JOIN pregunta p ON p.id = o.pregunta_id
                WHERE p.version_banco_id = ?""",
                versionViejaId)) {
            jdbc.update("""
                    INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento, creado_en)
                    VALUES (?, ?, ?, now())""",
                    mapaOpciones.get(((Number) fila.get("opcion_id")).longValue()),
                    fila.get("dimension_codigo"), fila.get("incremento"));
            sumar(conteos, "opcion_dimension", 1);
        }

        for (PreguntaDimension pd : preguntaDimensiones.findByPreguntaIdIn(idsViejos)) {
            preguntaDimensiones.save(PreguntaDimension.builder()
                    .preguntaId(mapaPreguntas.get(pd.getPreguntaId()))
                    .dimensionCodigo(pd.getDimensionCodigo())
                    .creadoEn(ahora)
                    .build());
            sumar(conteos, "pregunta_dimension", 1);
        }
        for (Long idViejo : idsViejos) {
            for (RangoPregunta r : rangos.findByPreguntaIdOrderByOrden(idViejo)) {
                rangos.save(RangoPregunta.builder()
                        .preguntaId(mapaPreguntas.get(idViejo))
                        .orden(r.getOrden())
                        .condicion(r.getCondicion())
                        .puntaje(r.getPuntaje())
                        .generaBandera(r.isGeneraBandera())
                        .creadoEn(ahora)
                        .build());
                sumar(conteos, "rango_pregunta", 1);
            }
            for (CampoCaso c : camposCaso.findByPreguntaIdOrderByOrden(idViejo)) {
                camposCaso.save(CampoCaso.builder()
                        .preguntaId(mapaPreguntas.get(idViejo))
                        .orden(c.getOrden())
                        .etiqueta(c.getEtiqueta())
                        .validacion(c.getValidacion())
                        .creadoEn(ahora)
                        .build());
                sumar(conteos, "campo_caso", 1);
            }
        }
    }

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

    // ============ Las pruebas del puesto ============

    @Override
    @Transactional
    public Map<String, Integer> copiarPruebas(Long organizacionDestino) {
        Long plataforma = resolutor.plataforma().getId();
        List<PlantillaPrueba> activas = plantillasPrueba
                .findByOrganizacionIdOrderByCreadoEnDesc(plataforma).stream()
                .filter(PlantillaPrueba::isEsActiva)
                .toList();

        Instant ahora = Instant.now();
        Map<String, Integer> conteos = conteosVacios("plantilla_prueba", "version_plantilla_prueba",
                "variante_cambio", "pregunta_version_plantilla", "entregable_requerido", "criterio");
        for (PlantillaPrueba vieja : activas) {
            // Solo viaja lo publicado: una plantilla sin versión publicada no tiene nada
            // que una empresa pueda usar todavía.
            VersionPlantillaPrueba versionVieja = versionesPrueba
                    .findByPlantillaPruebaIdOrderByVersionDesc(vieja.getId()).stream()
                    .filter(v -> "PUBLICADA".equals(v.getEstado()))
                    .findFirst()
                    .orElse(null);
            if (versionVieja == null) {
                continue;
            }
            copiarPrueba(vieja, versionVieja, organizacionDestino, ahora, conteos);
        }
        if (conteos.get("plantilla_prueba") == 0) {
            throw new IllegalStateException(
                    "La plataforma no tiene ninguna prueba del puesto publicada que copiar");
        }
        log.info("Pruebas copiadas a la organización {}: {}", organizacionDestino, conteos);
        return conteos;
    }

    private void copiarPrueba(PlantillaPrueba vieja, VersionPlantillaPrueba versionVieja,
                              Long destino, Instant ahora, Map<String, Integer> conteos) {
        // puesto_id queda vacío: apunta a un puesto de la plataforma, que es ajeno a la
        // empresa. Ella lo amarra a los suyos cuando arme su organigrama.
        PlantillaPrueba nueva = plantillasPrueba.save(PlantillaPrueba.builder()
                .organizacionId(destino)
                .puestoId(null)
                .nombre(vieja.getNombre())
                .esActiva(true)
                .creadoEn(ahora)
                .copiadaDeVersionId(vieja.getId())
                .build());
        sumar(conteos, "plantilla_prueba", 1);

        // vacante_id también: la versión copiada no es de ninguna convocatoria todavía.
        VersionPlantillaPrueba versionNueva = versionesPrueba.save(VersionPlantillaPrueba.builder()
                .plantillaPruebaId(nueva.getId())
                .vacanteId(null)
                .version(versionVieja.getVersion())
                .enunciado(versionVieja.getEnunciado())
                .materiales(versionVieja.getMateriales())
                .herramientasPermitidas(versionVieja.getHerramientasPermitidas())
                .urlConsigna(versionVieja.getUrlConsigna())
                .modalidad(versionVieja.getModalidad())
                .duracionMinutos(versionVieja.getDuracionMinutos())
                .plazoDias(versionVieja.getPlazoDias())
                .minutoCambioMin(versionVieja.getMinutoCambioMin())
                .minutoCambioMax(versionVieja.getMinutoCambioMax())
                .minutosExtra(versionVieja.getMinutosExtra())
                .estado("PUBLICADA")
                .publicadaEn(ahora)
                .creadoEn(ahora)
                .build());
        sumar(conteos, "version_plantilla_prueba", 1);

        for (VarianteCambio v : variantes.findByVersionPlantillaPruebaId(versionVieja.getId())) {
            variantes.save(VarianteCambio.builder()
                    .versionPlantillaPruebaId(versionNueva.getId())
                    .texto(v.getTexto())
                    .orden(v.getOrden())
                    .creadoEn(ahora)
                    .build());
            sumar(conteos, "variante_cambio", 1);
        }
        // Las preguntas del catálogo se REUTILIZAN, no se copian: su código es único
        // global y son enunciados, no claves — la fila nueva solo dice «esta versión
        // eligió esta pregunta».
        for (PreguntaVersionPlantilla pv : preguntasElegidas
                .findByVersionPlantillaPruebaIdOrderByOrden(versionVieja.getId())) {
            preguntasElegidas.save(PreguntaVersionPlantilla.builder()
                    .versionPlantillaPruebaId(versionNueva.getId())
                    .preguntaPruebaId(pv.getPreguntaPruebaId())
                    .orden(pv.getOrden())
                    .creadoEn(ahora)
                    .build());
            sumar(conteos, "pregunta_version_plantilla", 1);
        }
        for (EntregableRequerido e : entregablesRequeridos
                .findByVersionPlantillaPruebaIdOrderByOrden(versionVieja.getId())) {
            entregablesRequeridos.save(EntregableRequerido.builder()
                    .versionPlantillaPruebaId(versionNueva.getId())
                    .nombre(e.getNombre())
                    .detalle(e.getDetalle())
                    .formato(e.getFormato())
                    .esObligatorio(e.isEsObligatorio())
                    .orden(e.getOrden())
                    .creadoEn(ahora)
                    .build());
            sumar(conteos, "entregable_requerido", 1);
        }
        // Solo los criterios de ESTA versión: los globales del CV (sin versión) son de
        // todas las organizaciones y se quedan donde están.
        for (Criterio c : criterios.findByVersionPlantillaPruebaId(versionVieja.getId())) {
            criterios.save(Criterio.builder()
                    .codigo(c.getCodigo())
                    .nombre(c.getNombre())
                    .descripcion(c.getDescripcion())
                    .etapaCodigo(c.getEtapaCodigo())
                    .versionPlantillaPruebaId(versionNueva.getId())
                    .puntos(c.getPuntos())
                    .metodoVerificacion(c.getMetodoVerificacion())
                    .orden(c.getOrden())
                    .creadoEn(ahora)
                    .build());
            sumar(conteos, "criterio", 1);
        }
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
