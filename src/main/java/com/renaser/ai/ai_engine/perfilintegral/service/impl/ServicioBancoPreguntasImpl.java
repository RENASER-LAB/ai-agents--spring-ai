package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.*;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.mapper.OpcionMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.PreguntaMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.VersionBancoMapper;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioBancoPreguntas;
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
public class ServicioBancoPreguntasImpl implements ServicioBancoPreguntas {

    private final VersionBancoRepository versiones;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final VersionBancoMapper versionBancoMapper;
    private final PreguntaMapper preguntaMapper;
    private final OpcionMapper opcionMapper;
    private final ServicioAuditoria auditoria;
    private final Permisos permisos;

    @Override
    @Transactional
    public Long crearVersion(ContextoUsuario quien, CrearVersionBanco datos) {
        VersionBanco version = versiones.save(VersionBanco.builder()
                .organizacionId(quien.organizacionId())
                .tipoBanco(datos.tipoBanco())
                .nivelPuestoCodigo(datos.nivelPuestoCodigo())
                .etiqueta(datos.etiqueta())
                .estado("BORRADOR")
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_version_banco",
                "version_banco", version.getId(), null,
                Map.of("estado", "BORRADOR", "tipoBanco", datos.tipoBanco()), null);
        return version.getId();
    }

    @Override
    public List<VersionBancoResponse> listarVersiones(ContextoUsuario quien) {
        permisos.alcanceDe("ver_banco_preguntas");
        return versiones.findVisibles(quien.organizacionId()).stream()
                .map(versionBancoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void publicarVersion(ContextoUsuario quien, Long id) {
        VersionBanco version = laVersionVisible(quien, id);
        if (!"BORRADOR".equals(version.getEstado())) {
            throw new IllegalStateException("Solo se publica una versión en borrador; esta está " + version.getEstado());
        }
        version.setEstado("PUBLICADA");
        version.setPublicadaPorUsuarioId(quien.usuarioId());
        version.setPublicadaEn(Instant.now());
        versiones.save(version);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_version_banco",
                "version_banco", id, Map.of("estado", "BORRADOR"), Map.of("estado", "PUBLICADA"), null);
    }

    @Override
    @Transactional
    public Long crearPregunta(ContextoUsuario quien, Long versionBancoId, CrearPregunta datos) {
        VersionBanco version = laVersionVisible(quien, versionBancoId);
        if (!"BORRADOR".equals(version.getEstado())) {
            throw new IllegalStateException("No se puede agregar preguntas a una versión ya publicada");
        }
        Pregunta pregunta = preguntas.save(Pregunta.builder()
                .versionBancoId(versionBancoId)
                .codigo(datos.codigo())
                .bloque(datos.bloque())
                .tipo(datos.tipo())
                .enunciado(datos.enunciado())
                .situacion(datos.situacion())
                .esPuntuable(datos.esPuntuable())
                .orden(datos.orden())
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_pregunta",
                "pregunta", pregunta.getId(), null, Map.of("codigo", datos.codigo()), null);
        return pregunta.getId();
    }

    @Override
    public List<PreguntaResponse> listarPreguntas(ContextoUsuario quien, Long versionBancoId) {
        permisos.alcanceDe("ver_banco_preguntas");
        laVersionVisible(quien, versionBancoId);
        return preguntas.findByVersionBancoIdOrderByOrden(versionBancoId).stream()
                .map(preguntaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public Long agregarOpcion(ContextoUsuario quien, Long preguntaId, CrearOpcion datos) {
        Pregunta pregunta = laPreguntaVisible(quien, preguntaId);
        Opcion opcion = opciones.save(Opcion.builder()
                .preguntaId(pregunta.getId())
                .letra(datos.letra())
                .texto(datos.texto())
                .puntaje(datos.puntaje() == null ? null : java.math.BigDecimal.valueOf(datos.puntaje()))
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "agregar_opcion",
                "opcion", opcion.getId(), null, Map.of("letra", datos.letra()), null);
        return opcion.getId();
    }

    @Override
    public List<OpcionResponse> listarOpciones(ContextoUsuario quien, Long preguntaId) {
        permisos.alcanceDe("ver_banco_preguntas");
        laPreguntaVisible(quien, preguntaId);
        return opciones.findByPreguntaIdOrderByLetra(preguntaId).stream()
                .map(opcionMapper::toResponse)
                .toList();
    }

    // organizacionId nulo = biblioteca global: visible para cualquiera. Si tiene
    // organización, tiene que ser la del usuario.
    private VersionBanco laVersionVisible(ContextoUsuario quien, Long id) {
        VersionBanco version = versiones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Versión del banco", "id", id));
        if (version.getOrganizacionId() != null && !version.getOrganizacionId().equals(quien.organizacionId())) {
            throw new ResourceNotFoundException("Versión del banco", "id", id);
        }
        return version;
    }

    private Pregunta laPreguntaVisible(ContextoUsuario quien, Long id) {
        Pregunta pregunta = preguntas.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta", "id", id));
        laVersionVisible(quien, pregunta.getVersionBancoId());
        return pregunta;
    }
}
