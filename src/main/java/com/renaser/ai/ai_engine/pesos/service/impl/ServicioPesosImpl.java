package com.renaser.ai.ai_engine.pesos.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoDimension;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoDimensionRepository;
import com.renaser.ai.ai_engine.pesos.dto.DtosPesos.*;
import com.renaser.ai.ai_engine.pesos.entity.PesoComponentePerfil;
import com.renaser.ai.ai_engine.pesos.entity.PesoEtapa;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.mapper.PesosMapper;
import com.renaser.ai.ai_engine.pesos.repository.PesoComponentePerfilRepository;
import com.renaser.ai.ai_engine.pesos.repository.PesoEtapaRepository;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.pesos.service.ServicioPesos;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioPesosImpl implements ServicioPesos {

    private static final BigDecimal TOLERANCIA = BigDecimal.valueOf(0.01);

    private final VersionPesosRepository versiones;
    private final PesoEtapaRepository pesosEtapa;
    private final PesoComponentePerfilRepository pesosComponente;
    private final PesoDimensionRepository pesosDimension;
    private final PesoCriterioRepository pesosCriterio;
    private final CriterioRepository criterios;
    private final PesosMapper mapper;
    private final ServicioAuditoria auditoria;
    private final DuenoDelInstrumento dueno;

    @Override
    @Transactional
    public Long crearVersion(ContextoUsuario quien, CrearVersionPesos datos) {
        VersionPesos version = versiones.save(VersionPesos.builder()
                .organizacionId(quien.organizacionId())
                .etiqueta(datos.etiqueta())
                .estado("BORRADOR")
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_version_pesos",
                "version_pesos", version.getId(), null, Map.of("etiqueta", datos.etiqueta()), null);
        return version.getId();
    }

    @Override
    public List<VersionPesosResponse> listar(ContextoUsuario quien) {
        // El resolutor decide de quién son los pesos que esta organización ve: los suyos
        // si personalizó, los de la plataforma si no.
        return versiones.findByOrganizacionIdOrderByCreadoEnDesc(
                        dueno.duenoDe(quien.organizacionId(), Instrumento.PESOS)).stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void publicarVersion(ContextoUsuario quien, Long id) {
        VersionPesos version = laPropia(quien, id);
        if (!"BORRADOR".equals(version.getEstado())) {
            throw new IllegalStateException("Solo se publica una versión en borrador; esta está " + version.getEstado());
        }
        validarSumas(id);
        version.setEstado("PUBLICADA");
        version.setPublicadaPorUsuarioId(quien.usuarioId());
        version.setPublicadaEn(Instant.now());
        versiones.save(version);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_version_pesos",
                "version_pesos", id, Map.of("estado", "BORRADOR"), Map.of("estado", "PUBLICADA"), null);
    }

    @Override
    @Transactional
    public void agregarPesoEtapa(ContextoUsuario quien, Long versionId, CrearPesoEtapa datos) {
        laPropiaEnBorrador(quien, versionId);
        pesosEtapa.save(PesoEtapa.builder().versionPesosId(versionId).etapaCodigo(datos.etapaCodigo())
                .peso(BigDecimal.valueOf(datos.peso())).creadoEn(Instant.now()).build());
    }

    @Override
    @Transactional
    public void agregarPesoComponente(ContextoUsuario quien, Long versionId, CrearPesoComponente datos) {
        laPropiaEnBorrador(quien, versionId);
        pesosComponente.save(PesoComponentePerfil.builder().versionPesosId(versionId).componente(datos.componente())
                .peso(BigDecimal.valueOf(datos.peso())).creadoEn(Instant.now()).build());
    }

    @Override
    @Transactional
    public void agregarPesoDimension(ContextoUsuario quien, Long versionId, CrearPesoDimension datos) {
        laPropiaEnBorrador(quien, versionId);
        pesosDimension.save(PesoDimension.builder().versionPesosId(versionId)
                .nivelPuestoCodigo(datos.nivelPuestoCodigo()).dimensionCodigo(datos.dimensionCodigo())
                .peso(BigDecimal.valueOf(datos.peso())).creadoEn(Instant.now()).build());
    }

    @Override
    @Transactional
    public void agregarPesoCriterio(ContextoUsuario quien, Long versionId, CrearPesoCriterio datos) {
        laPropiaEnBorrador(quien, versionId);
        pesosCriterio.save(PesoCriterio.builder().versionPesosId(versionId)
                .nivelPuestoCodigo(datos.nivelPuestoCodigo()).criterioId(datos.criterioId())
                .peso(BigDecimal.valueOf(datos.peso())).creadoEn(Instant.now()).build());
    }

    @Override
    public List<PesoEtapaResponse> listarPesosEtapa(ContextoUsuario quien, Long versionId) {
        laVisible(quien, versionId);
        return pesosEtapa.findByVersionPesosId(versionId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public List<PesoComponenteResponse> listarPesosComponente(ContextoUsuario quien, Long versionId) {
        laVisible(quien, versionId);
        return pesosComponente.findByVersionPesosId(versionId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public List<PesoDimensionResponse> listarPesosDimension(ContextoUsuario quien, Long versionId) {
        laVisible(quien, versionId);
        return pesosDimension.findByVersionPesosId(versionId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public List<PesoCriterioResponse> listarPesosCriterio(ContextoUsuario quien, Long versionId) {
        laVisible(quien, versionId);
        return pesosCriterio.findByVersionPesosId(versionId).stream().map(mapper::toResponse).toList();
    }

    // Que cada grupo sume lo que debe sumar lo comprueba el código al publicar (docs/05 §17):
    // etapa suma 100, componente suma lo mismo que la etapa PERFIL_INTEGRAL, y dimensión/
    // criterio suman 100 cada uno POR NIVEL DE PUESTO, porque cada nivel tiene su propio reparto.
    private void validarSumas(Long versionId) {
        List<PesoEtapa> etapas = pesosEtapa.findByVersionPesosId(versionId);
        exigirSuma(etapas.stream().map(PesoEtapa::getPeso).toList(), BigDecimal.valueOf(100),
                "Los pesos de etapa deben sumar 100");

        BigDecimal pesoPerfilIntegral = etapas.stream()
                .filter(e -> "PERFIL_INTEGRAL".equals(e.getEtapaCodigo()))
                .map(PesoEtapa::getPeso).findFirst().orElse(BigDecimal.ZERO);
        List<PesoComponentePerfil> componentes = pesosComponente.findByVersionPesosId(versionId);
        exigirSuma(componentes.stream().map(PesoComponentePerfil::getPeso).toList(), pesoPerfilIntegral,
                "Los pesos de componente del Perfil Integral deben sumar lo mismo que la etapa Perfil Integral");

        Map<String, List<PesoDimension>> dimensionPorNivel = pesosDimension.findByVersionPesosId(versionId).stream()
                .collect(Collectors.groupingBy(PesoDimension::getNivelPuestoCodigo));
        dimensionPorNivel.forEach((nivel, filas) -> exigirSuma(
                filas.stream().map(PesoDimension::getPeso).toList(), BigDecimal.valueOf(100),
                "Los pesos de dimensión del nivel " + nivel + " deben sumar 100"));

        // Los criterios se agrupan por nivel Y POR ETAPA. Cada etapa tiene su propia rúbrica
        // que suma 100 por sí sola -los ocho del currículum, los diez de la simulación, las
        // nueve de la validación-, así que agrupar solo por nivel sumaría 300 y rechazaría una
        // versión perfectamente válida.
        Map<String, List<PesoCriterio>> criterioPorNivelYEtapa = pesosCriterio.findByVersionPesosId(versionId)
                .stream()
                .collect(Collectors.groupingBy(pc -> pc.getNivelPuestoCodigo() + " · "
                        + criterios.findById(pc.getCriterioId())
                                .map(Criterio::getEtapaCodigo).orElse("(sin etapa)")));
        criterioPorNivelYEtapa.forEach((nivelYEtapa, filas) -> exigirSuma(
                filas.stream().map(PesoCriterio::getPeso).toList(), BigDecimal.valueOf(100),
                "Los pesos de criterio de " + nivelYEtapa + " deben sumar 100"));
    }

    private void exigirSuma(List<BigDecimal> pesos, BigDecimal esperado, String mensaje) {
        BigDecimal suma = pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.subtract(esperado).abs().compareTo(TOLERANCIA) > 0) {
            throw new IllegalArgumentException(mensaje + " (hoy suman " + suma + ")");
        }
    }

    // Leer resuelve, editar no: con la bandera apagada la organización VE los pesos de
    // la plataforma —eso contesta el resolutor— pero solo edita los suyos.

    private VersionPesos laVisible(ContextoUsuario quien, Long id) {
        return versiones.findByIdAndOrganizacionId(id, quien.organizacionId())
                .or(() -> versiones.findByIdAndOrganizacionId(
                        id, dueno.duenoDe(quien.organizacionId(), Instrumento.PESOS)))
                .orElseThrow(() -> new ResourceNotFoundException("Versión de pesos", "id", id));
    }

    private VersionPesos laPropia(ContextoUsuario quien, Long id) {
        return versiones.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Versión de pesos", "id", id));
    }

    private VersionPesos laPropiaEnBorrador(ContextoUsuario quien, Long id) {
        VersionPesos version = laPropia(quien, id);
        if (!"BORRADOR".equals(version.getEstado())) {
            throw new IllegalStateException("No se pueden agregar pesos a una versión ya publicada");
        }
        return version;
    }
}
