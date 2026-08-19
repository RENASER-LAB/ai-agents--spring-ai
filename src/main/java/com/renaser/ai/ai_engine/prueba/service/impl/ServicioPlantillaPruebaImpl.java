package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.*;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.VarianteCambio;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.ServicioPlantillaPrueba;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServicioPlantillaPruebaImpl implements ServicioPlantillaPrueba {

    private static final BigDecimal TOLERANCIA = BigDecimal.valueOf(0.01);
    private static final int UNIVERSALES_MIN = 8;
    private static final int UNIVERSALES_MAX = 10;
    private static final int ESPECIFICAS_MIN = 3;
    private static final int ESPECIFICAS_MAX = 5;
    private static final int DURACION_MIN = 60;
    private static final int DURACION_MAX = 120;

    private final PlantillaPruebaRepository plantillas;
    private final VersionPlantillaPruebaRepository versiones;
    private final VarianteCambioRepository variantes;
    private final PreguntaPruebaRepository preguntasCatalogo;
    private final PreguntaVersionPlantillaRepository preguntasElegidas;
    private final EntregableRequeridoRepository entregablesRequeridos;
    private final CriterioRepository criterios;
    private final ServicioAuditoria auditoria;

    @Override
    @Transactional
    public Long crearPlantilla(ContextoUsuario quien, CrearPlantilla datos) {
        PlantillaPrueba plantilla = plantillas.save(PlantillaPrueba.builder()
                .organizacionId(quien.organizacionId())
                .puestoId(datos.puestoId())
                .nombre(datos.nombre())
                .esActiva(true)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_plantilla_prueba",
                "plantilla_prueba", plantilla.getId(), null, Map.of("nombre", datos.nombre()), null);
        return plantilla.getId();
    }

    @Override
    public List<PlantillaResponse> listarPlantillas(ContextoUsuario quien) {
        return plantillas.findByOrganizacionIdOrderByCreadoEnDesc(quien.organizacionId()).stream()
                .map(p -> new PlantillaResponse(p.getId(), p.getNombre(), p.getPuestoId(), p.isEsActiva()))
                .toList();
    }

    @Override
    @Transactional
    public Long crearVersion(ContextoUsuario quien, Long plantillaId, CrearVersion datos) {
        PlantillaPrueba plantilla = laPlantillaVisible(quien, plantillaId);
        Integer siguiente = versiones.findByPlantillaPruebaIdOrderByVersionDesc(plantillaId).stream()
                .findFirst().map(v -> v.getVersion() + 1).orElse(1);

        VersionPlantillaPrueba version = versiones.save(VersionPlantillaPrueba.builder()
                .plantillaPruebaId(plantilla.getId())
                .version(siguiente)
                .enunciado(datos.enunciado())
                .materiales(datos.materiales())
                .herramientasPermitidas(datos.herramientasPermitidas())
                .modalidad(datos.modalidad())
                .duracionMinutos(datos.duracionMinutos())
                .plazoDias(datos.plazoDias())
                .minutoCambioMin(datos.minutoCambioMin())
                .minutoCambioMax(datos.minutoCambioMax())
                .minutosExtra(datos.minutosExtra())
                .estado("BORRADOR")
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_version_plantilla_prueba",
                "version_plantilla_prueba", version.getId(), null,
                Map.of("plantillaId", plantillaId), null);
        return version.getId();
    }

    @Override
    @Transactional
    public void publicarVersion(ContextoUsuario quien, Long versionId) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);

        if ("CRONOMETRADA".equals(version.getModalidad())) {
            int duracion = version.getDuracionMinutos();
            if (duracion < DURACION_MIN || duracion > DURACION_MAX) {
                throw new IllegalArgumentException(
                        "Una prueba cronometrada dura de %d a %d minutos; esta pide %d"
                                .formatted(DURACION_MIN, DURACION_MAX, duracion));
            }
        }

        validarCuotaPreguntas(versionId);
        validarRubrica(versionId);

        version.setEstado("PUBLICADA");
        version.setPublicadaPorUsuarioId(quien.usuarioId());
        version.setPublicadaEn(Instant.now());
        versiones.save(version);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_version_plantilla_prueba",
                "version_plantilla_prueba", versionId,
                Map.of("estado", "BORRADOR"), Map.of("estado", "PUBLICADA"), null);
    }

    @Override
    public VersionCompleta verVersion(ContextoUsuario quien, Long versionId) {
        VersionPlantillaPrueba v = versiones.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Versión de prueba", "id", versionId));

        List<VarianteResponse> vs = variantes.findByVersionPlantillaPruebaId(versionId).stream()
                .map(x -> new VarianteResponse(x.getId(), x.getTexto(), x.getOrden())).toList();

        List<Long> idsElegidas = preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(versionId)
                .stream().map(PreguntaVersionPlantilla::getPreguntaPruebaId).toList();
        List<PreguntaPruebaResponse> ps = preguntasCatalogo.findByIdIn(idsElegidas).stream()
                .map(p -> new PreguntaPruebaResponse(p.getId(), p.getCodigo(), p.getEnunciado(),
                        p.getTipo(), p.getPuestoId()))
                .toList();

        List<EntregableRequeridoResponse> es = entregablesRequeridos
                .findByVersionPlantillaPruebaIdOrderByOrden(versionId).stream()
                .map(e -> new EntregableRequeridoResponse(e.getId(), e.getNombre(), e.getDetalle(),
                        e.getFormato(), e.isEsObligatorio()))
                .toList();

        List<CriterioRubricaResponse> rubrica = criterios.findByVersionPlantillaPruebaId(versionId).stream()
                .map(c -> new CriterioRubricaResponse(c.getId(), c.getCodigo(),
                        c.getNombre(), c.getPuntos() == null ? null : c.getPuntos().doubleValue(),
                        c.getMetodoVerificacion()))
                .toList();

        VersionResponse vr = new VersionResponse(v.getId(), v.getPlantillaPruebaId(), v.getVersion(),
                v.getEnunciado(), v.getModalidad(), v.getDuracionMinutos(), v.getPlazoDias(),
                v.getMinutoCambioMin(), v.getMinutoCambioMax(), v.getEstado(), v.getPublicadaEn());
        return new VersionCompleta(vr, vs, ps, es, rubrica);
    }

    @Override
    @Transactional
    public Long agregarVariante(ContextoUsuario quien, Long versionId, CrearVariante datos) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);
        int siguiente = variantes.findByVersionPlantillaPruebaId(versionId).size() + 1;
        VarianteCambio v = variantes.save(VarianteCambio.builder()
                .versionPlantillaPruebaId(version.getId())
                .texto(datos.texto())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
        return v.getId();
    }

    @Override
    @Transactional
    public Long crearPreguntaCatalogo(ContextoUsuario quien, CrearPreguntaPrueba datos) {
        int siguiente = preguntasCatalogo.findByTipo(datos.tipo()).size() + 1;
        PreguntaPrueba p = preguntasCatalogo.save(PreguntaPrueba.builder()
                .codigo(datos.codigo())
                .enunciado(datos.enunciado())
                .tipo(datos.tipo())
                .puestoId(datos.puestoId())
                .revela(datos.revela())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_pregunta_prueba",
                "pregunta_prueba", p.getId(), null, Map.of("codigo", datos.codigo()), null);
        return p.getId();
    }

    @Override
    public List<PreguntaPruebaResponse> listarPreguntasCatalogo(String tipo) {
        List<PreguntaPrueba> lista = tipo == null ? preguntasCatalogo.findAll()
                : preguntasCatalogo.findByTipo(tipo);
        return lista.stream()
                .map(p -> new PreguntaPruebaResponse(p.getId(), p.getCodigo(), p.getEnunciado(),
                        p.getTipo(), p.getPuestoId()))
                .toList();
    }

    @Override
    @Transactional
    public void elegirPregunta(ContextoUsuario quien, Long versionId, ElegirPregunta datos) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);
        preguntasCatalogo.findById(datos.preguntaPruebaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta de prueba", "id", datos.preguntaPruebaId()));
        int siguiente = preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(versionId).size() + 1;
        preguntasElegidas.save(PreguntaVersionPlantilla.builder()
                .versionPlantillaPruebaId(version.getId())
                .preguntaPruebaId(datos.preguntaPruebaId())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
    }

    @Override
    @Transactional
    public Long agregarEntregableRequerido(ContextoUsuario quien, Long versionId, CrearEntregableRequerido datos) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);
        int siguiente = entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(versionId).size() + 1;
        EntregableRequerido e = entregablesRequeridos.save(EntregableRequerido.builder()
                .versionPlantillaPruebaId(version.getId())
                .nombre(datos.nombre())
                .detalle(datos.detalle())
                .formato(datos.formato())
                .esObligatorio(datos.esObligatorio())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
        return e.getId();
    }

    @Override
    @Transactional
    public Long agregarCriterioRubrica(ContextoUsuario quien, Long versionId, CrearCriterioRubrica datos) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);
        int siguiente = criterios.findByVersionPlantillaPruebaId(versionId).size() + 1;
        Criterio c = criterios.save(Criterio.builder()
                .codigo(datos.codigo())
                .nombre(datos.nombre())
                .descripcion(datos.descripcion())
                .etapaCodigo("PRUEBA_PUESTO")
                .versionPlantillaPruebaId(version.getId())
                .puntos(BigDecimal.valueOf(datos.puntos()))
                .metodoVerificacion(datos.metodoVerificacion())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
        return c.getId();
    }

    // ============ Apoyo ============

    private PlantillaPrueba laPlantillaVisible(ContextoUsuario quien, Long id) {
        return plantillas.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de prueba", "id", id));
    }

    private VersionPlantillaPrueba laVersionEnBorrador(ContextoUsuario quien, Long id) {
        VersionPlantillaPrueba version = versiones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Versión de prueba", "id", id));
        laPlantillaVisible(quien, version.getPlantillaPruebaId());
        if (!"BORRADOR".equals(version.getEstado())) {
            throw new IllegalStateException("Solo se edita una versión en borrador; esta está " + version.getEstado());
        }
        return version;
    }

    // RF-83: entre 8 y 10 universales, entre 3 y 5 específicas. No hay tope de previas.
    private void validarCuotaPreguntas(Long versionId) {
        List<Long> ids = preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(versionId).stream()
                .map(PreguntaVersionPlantilla::getPreguntaPruebaId).toList();
        Map<String, Long> porTipo = preguntasCatalogo.findByIdIn(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(PreguntaPrueba::getTipo, java.util.stream.Collectors.counting()));

        long universales = porTipo.getOrDefault("UNIVERSAL", 0L);
        long especificas = porTipo.getOrDefault("ESPECIFICA", 0L);
        if (universales < UNIVERSALES_MIN || universales > UNIVERSALES_MAX) {
            throw new IllegalArgumentException(
                    "Hacen falta entre %d y %d preguntas universales; hay %d"
                            .formatted(UNIVERSALES_MIN, UNIVERSALES_MAX, universales));
        }
        if (especificas < ESPECIFICAS_MIN || especificas > ESPECIFICAS_MAX) {
            throw new IllegalArgumentException(
                    "Hacen falta entre %d y %d preguntas específicas del puesto; hay %d"
                            .formatted(ESPECIFICAS_MIN, ESPECIFICAS_MAX, especificas));
        }
    }

    // RF-86/89: la rúbrica también suma 100, y se comprueba al publicar, no al guardar.
    private void validarRubrica(Long versionId) {
        BigDecimal suma = criterios.findByVersionPlantillaPruebaId(versionId).stream()
                .map(Criterio::getPuntos)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.subtract(BigDecimal.valueOf(100)).abs().compareTo(TOLERANCIA) > 0) {
            throw new IllegalArgumentException("La rúbrica debe sumar 100 puntos (hoy suma " + suma + ")");
        }
    }
}
