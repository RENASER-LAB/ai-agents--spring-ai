package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.CorregirPreguntaTecnica;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.CuestionarioResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.PreguntaDelCuestionario;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.RecetaCuestionarioTecnico;
import com.renaser.ai.ai_engine.perfilintegral.service.RecetaCuestionarioTecnico.PreguntaPublicable;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCuestionarioTecnico;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.FichaVacante;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.FichaVacanteRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ServicioCuestionarioTecnicoImpl implements ServicioCuestionarioTecnico {

    private final VacanteRepository vacantes;
    private final FichaVacanteRepository fichas;
    private final VersionBancoRepository versionesBanco;
    private final PreguntaRepository preguntas;
    private final ColaCalificacionIa cola;
    private final ServicioAuditoria auditoria;

    @Override
    public boolean generar(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laDeLaOrganizacion(quien, vacanteId);
        FichaVacante ficha = fichas.findByVacanteId(vacante.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Esta vacante no tiene ficha: llénala antes de generar el cuestionario"));
        if (!"COMPLETA".equals(ficha.getEstado())) {
            throw new IllegalStateException("La ficha está a medias: complétala antes de "
                    + "generar el cuestionario");
        }
        boolean encolada = cola.encolarRedactor(quien.organizacionId(), vacante.getId());
        if (encolada) {
            auditoria.registrar(quien.organizacionId(), quien, "generar_cuestionario_tecnico",
                    "vacante", vacante.getId(), null, Map.of("agente", "REDACTOR"), null);
        }
        return encolada;
    }

    @Override
    public CuestionarioResponse ver(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laDeLaOrganizacion(quien, vacanteId);
        // El borrador manda: es la copia de trabajo. La publicada solo se enseña cuando
        // no hay nada en el taller.
        Optional<VersionBanco> version = versionesBanco
                .findFirstByVacanteIdAndEstado(vacante.getId(), "BORRADOR")
                .or(() -> versionesBanco.findFirstByVacanteIdAndEstado(vacante.getId(), "PUBLICADA"));

        List<PreguntaDelCuestionario> lasPreguntas = version
                .map(v -> preguntas.findByVersionBancoIdOrderByOrden(v.getId()).stream()
                        .map(p -> new PreguntaDelCuestionario(p.getId(), p.getCodigo(),
                                p.getBloque(), p.getEnunciado(), p.getC3Esperado(),
                                p.getC4Esperado(), p.getSenalDeCero(), p.isPresencial(),
                                p.getOrden()))
                        .toList())
                .orElse(List.of());

        boolean desactualizado = version.isPresent() && fichas.findByVacanteId(vacante.getId())
                .map(f -> f.getActualizadoEn() != null && version.get().getCreadoEn() != null
                        && f.getActualizadoEn().isAfter(version.get().getCreadoEn()))
                .orElse(false);

        return new CuestionarioResponse(
                version.map(VersionBanco::getId).orElse(null),
                version.map(VersionBanco::getEstado).orElse(null),
                desactualizado,
                cola.comoVaElRedactor(vacante.getId()),
                lasPreguntas);
    }

    @Override
    @Transactional
    public void corregirPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                 CorregirPreguntaTecnica datos) {
        Vacante vacante = laDeLaOrganizacion(quien, vacanteId);
        VersionBanco borrador = versionesBanco
                .findFirstByVacanteIdAndEstado(vacante.getId(), "BORRADOR")
                .orElseThrow(() -> new IllegalStateException(
                        "No hay borrador que corregir: genera el cuestionario primero"));
        Pregunta pregunta = preguntas.findById(preguntaId)
                .filter(p -> p.getVersionBancoId().equals(borrador.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pregunta del borrador", "id", preguntaId));

        Map<String, Object> antes = Map.of(
                "enunciado", pregunta.getEnunciado(),
                "c3Esperado", String.valueOf(pregunta.getC3Esperado()),
                "c4Esperado", String.valueOf(pregunta.getC4Esperado()),
                "senalDeCero", String.valueOf(pregunta.getSenalDeCero()));
        pregunta.setEnunciado(datos.enunciado());
        pregunta.setC3Esperado(datos.c3Esperado());
        pregunta.setC4Esperado(datos.c4Esperado());
        pregunta.setSenalDeCero(datos.senalDeCero());
        preguntas.save(pregunta);
        auditoria.registrar(quien.organizacionId(), quien, "corregir_pregunta_tecnica",
                "pregunta", preguntaId, antes,
                Map.of("enunciado", datos.enunciado(),
                       "c3Esperado", String.valueOf(datos.c3Esperado()),
                       "c4Esperado", String.valueOf(datos.c4Esperado()),
                       "senalDeCero", String.valueOf(datos.senalDeCero())),
                null);
    }

    @Override
    @Transactional
    public void publicar(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laDeLaOrganizacion(quien, vacanteId);
        VersionBanco borrador = versionesBanco
                .findFirstByVacanteIdAndEstado(vacante.getId(), "BORRADOR")
                .orElseThrow(() -> new IllegalStateException(
                        "No hay borrador que publicar: genera el cuestionario primero"));

        // El dueño pudo editar a mano: la aduana se vuelve a pasar entera antes de que
        // esto toque a un solo candidato.
        List<PreguntaPublicable> publicables = preguntas
                .findByVersionBancoIdOrderByOrden(borrador.getId()).stream()
                .map(p -> new PreguntaPublicable(p.getCodigo(), p.getEnunciado(),
                        p.getC3Esperado(), p.getC4Esperado(), p.getSenalDeCero(),
                        p.isPresencial()))
                .toList();
        List<String> errores = RecetaCuestionarioTecnico.validarPublicacion(
                borrador.getNivelPuestoCodigo(), publicables);
        if (!errores.isEmpty()) {
            throw new IllegalArgumentException("El cuestionario no pasa la aduana: "
                    + String.join(" · ", errores));
        }

        // Publicar retira a la publicada anterior DE ESTA VACANTE (nada se borra). Los
        // bancos por nivel de la plataforma ni se miran: son mundos distintos.
        versionesBanco.findFirstByVacanteIdAndEstado(vacante.getId(), "PUBLICADA")
                .ifPresent(saliente -> {
                    saliente.setEstado("ARCHIVADA");
                    // saveAndFlush: el índice parcial único (una PUBLICADA por vacante) no
                    // perdona que el borrador pase a PUBLICADA antes de que esta se archive.
                    versionesBanco.saveAndFlush(saliente);
                    auditoria.registrar(quien.organizacionId(), quien,
                            "archivar_cuestionario_tecnico", "version_banco", saliente.getId(),
                            Map.of("estado", "PUBLICADA"), Map.of("estado", "ARCHIVADA"),
                            "reemplazada al publicar la versión " + borrador.getId());
                });

        borrador.setEstado("PUBLICADA");
        borrador.setPublicadaPorUsuarioId(quien.usuarioId());
        borrador.setPublicadaEn(Instant.now());
        versionesBanco.save(borrador);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_cuestionario_tecnico",
                "version_banco", borrador.getId(),
                Map.of("estado", "BORRADOR"), Map.of("estado", "PUBLICADA"), null);
    }

    private Vacante laDeLaOrganizacion(ContextoUsuario quien, Long id) {
        return vacantes.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", id));
    }
}
