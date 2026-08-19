package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.NotaCriterioResponse;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.PonerNotaCriterio;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPrueba;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioCalificacionPruebaImpl implements ServicioCalificacionPrueba {

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final IntentoPruebaRepository intentos;
    private final CriterioRepository criterios;
    private final NotaCriterioRepository notasCriterio;
    private final NotaEtapaRepository notasEtapa;
    private final VersionPesosRepository versionesPesos;
    private final Permisos permisos;

    @Override
    public List<NotaCriterioResponse> verNotas(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ajustar_nota");
        List<Criterio> rubrica = laRubricaDe(postulacion);
        Map<Long, NotaCriterio> notasPorCriterio = notasCriterio.findByPostulacionId(postulacionId).stream()
                .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity()));

        return rubrica.stream().map(c -> {
            NotaCriterio n = notasPorCriterio.get(c.getId());
            return new NotaCriterioResponse(c.getId(), c.getNombre(),
                    c.getPuntos() == null ? null : c.getPuntos().doubleValue(),
                    n == null || n.getPuntaje() == null ? null : n.getPuntaje().doubleValue(),
                    n == null ? null : n.getExplicacion(),
                    n == null ? null : n.getOrigen());
        }).toList();
    }

    @Override
    @Transactional
    public void ponerNota(ContextoUsuario quien, Long postulacionId, Long criterioId, PonerNotaCriterio datos) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ajustar_nota");
        Criterio criterio = criterios.findById(criterioId)
                .orElseThrow(() -> new ResourceNotFoundException("Criterio", "id", criterioId));
        if (!laRubricaDe(postulacion).contains(criterio)) {
            throw new IllegalArgumentException("Ese criterio no pertenece a la rúbrica de esta prueba");
        }
        BigDecimal maximo = criterio.getPuntos();
        BigDecimal puntaje = BigDecimal.valueOf(datos.puntaje());
        if (puntaje.compareTo(BigDecimal.ZERO) < 0 || (maximo != null && puntaje.compareTo(maximo) > 0)) {
            throw new IllegalArgumentException(
                    "El puntaje de «%s» tiene que estar entre 0 y %s".formatted(criterio.getNombre(), maximo));
        }

        NotaCriterio fila = notasCriterio.findByPostulacionIdAndCriterioId(postulacionId, criterioId)
                .orElseGet(() -> NotaCriterio.builder()
                        .postulacionId(postulacionId)
                        .criterioId(criterioId)
                        .creadoEn(Instant.now())
                        .build());
        boolean yaExistia = fila.getId() != null;
        fila.setPuntaje(puntaje);
        fila.setExplicacion(datos.explicacion());
        fila.setOrigen("PERSONA");
        fila.setCalificadaPorUsuarioId(quien.usuarioId());
        if (yaExistia) {
            fila.setAjustadaPorUsuarioId(quien.usuarioId());
            fila.setMotivoAjuste(datos.explicacion());
            fila.setAjustadaEn(Instant.now());
        }
        notasCriterio.save(fila);
    }

    @Override
    @Transactional
    public BigDecimal calcularNotaEtapa(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ajustar_nota");
        List<Criterio> rubrica = laRubricaDe(postulacion);
        Map<Long, NotaCriterio> notasPorCriterio = notasCriterio.findByPostulacionId(postulacionId).stream()
                .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity()));

        List<String> faltan = rubrica.stream()
                .filter(c -> !notasPorCriterio.containsKey(c.getId()))
                .map(Criterio::getNombre)
                .toList();
        if (!faltan.isEmpty()) {
            throw new IllegalStateException(
                    "Todavía faltan notas por poner: " + String.join(", ", faltan));
        }

        // Cada criterio.puntos ya es su peso dentro de 100 (la rúbrica publicada suma
        // 100), así que sumar los puntajes obtenidos da directamente la nota de la etapa.
        BigDecimal nota = notasPorCriterio.values().stream()
                .map(NotaCriterio::getPuntaje)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Vacante vacante = vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException("La vacante de esta postulación ya no existe"));

        NotaEtapa fila = notasEtapa.findByPostulacionIdAndEtapaCodigo(postulacionId, "PRUEBA_PUESTO")
                .orElseGet(() -> NotaEtapa.builder()
                        .postulacionId(postulacionId)
                        .etapaCodigo("PRUEBA_PUESTO")
                        .creadoEn(Instant.now())
                        .build());
        fila.setPuntaje(nota);
        fila.setVersionPesosId(vacante.getVersionPesosId());
        fila.setCalculadaEn(Instant.now());
        notasEtapa.save(fila);
        return nota;
    }

    // ============ Apoyo ============

    private List<Criterio> laRubricaDe(Postulacion postulacion) {
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Prueba del puesto", "postulación", postulacion.getId()));
        return criterios.findByVersionPlantillaPruebaId(intento.getVersionPlantillaPruebaId());
    }

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
}
