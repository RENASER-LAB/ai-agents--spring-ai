package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
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

/**
 * Poner notas a una rúbrica y ponderarlas. Sirve para las tres etapas que se califican así:
 * la prueba del puesto, la simulación y la validación práctica.
 *
 * <p>Salió de {@code ServicioCalificacionPruebaImpl}, que hacía exactamente esto con la etapa
 * escrita a mano. La lógica no cambió — solo dejó de estar atada a una etapa concreta.
 *
 * <p>Lo que hace no es calificar: es <b>ponderar lo ya calificado</b>. Poner cada nota es
 * trabajo de una persona hoy, y de un agente cuando exista. Aquí solo se suma, y solo cuando
 * están todas: media rúbrica no es una nota, es un dato a medias.
 */
@Service
@RequiredArgsConstructor
public class CalificacionPorCriterio {

    private final CriterioRepository criterios;
    private final NotaCriterioRepository notasCriterio;
    private final NotaEtapaRepository notasEtapa;
    private final PesoCriterioRepository pesosCriterio;
    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;

    /** Los criterios globales de una etapa: los que no cuelgan de una plantilla de prueba. */
    public List<Criterio> rubricaGlobalDe(String etapaCodigo) {
        return criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(etapaCodigo);
    }

    /**
     * Cuánto vale como máximo un criterio para esta postulación.
     *
     * <p>Hay dos sitios donde puede vivir ese número, y no es arbitrario: los criterios de una
     * <b>prueba del puesto</b> traen sus puntos consigo, porque cada prueba tiene su propia
     * rúbrica; los <b>globales</b> —currículum, simulación, validación— valen distinto según el
     * nivel del puesto, así que su peso vive en {@code peso_criterio}, atado a la versión de
     * pesos de la vacante.
     */
    public BigDecimal maximoDe(Criterio criterio, Vacante vacante, String nivelPuestoCodigo) {
        if (criterio.getPuntos() != null) {
            return criterio.getPuntos();
        }
        return pesosCriterio
                .findByVersionPesosIdAndNivelPuestoCodigoAndCriterioId(
                        vacante.getVersionPesosId(), nivelPuestoCodigo, criterio.getId())
                .map(PesoCriterio::getPeso)
                .orElse(null);
    }

    public List<Vista> verNotas(Long postulacionId, List<Criterio> rubrica) {
        Map<Long, NotaCriterio> puestas = notasCriterio.findByPostulacionId(postulacionId).stream()
                .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity()));
        Map<Long, BigDecimal> maximos = maximosDe(postulacionId, rubrica);

        return rubrica.stream().map(c -> {
            NotaCriterio n = puestas.get(c.getId());
            BigDecimal maximo = maximos.get(c.getId());
            return new Vista(c.getId(), c.getNombre(),
                    maximo == null ? null : maximo.doubleValue(),
                    n == null || n.getPuntaje() == null ? null : n.getPuntaje().doubleValue(),
                    n == null ? null : n.getExplicacion(),
                    n == null ? null : n.getOrigen());
        }).toList();
    }

    /** El máximo de cada criterio de la rúbrica, resuelto para la vacante de esta postulación. */
    public Map<Long, BigDecimal> maximosDe(Long postulacionId, List<Criterio> rubrica) {
        Postulacion postulacion = postulaciones.findById(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        Vacante vacante = vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException("La vacante de esta postulación ya no existe"));
        String nivel = puestos.findById(vacante.getPuestoId())
                .map(Puesto::getNivelPuestoCodigo)
                .orElseThrow(() -> new IllegalStateException("La vacante apunta a un puesto que no existe"));

        return rubrica.stream()
                .filter(c -> maximoDe(c, vacante, nivel) != null)
                .collect(Collectors.toMap(Criterio::getId, c -> maximoDe(c, vacante, nivel)));
    }

    /**
     * Pone o corrige la nota de un criterio.
     *
     * <p>La explicación es obligatoria siempre — lo exige el contrato y lo exige la base. Si es
     * una corrección, además queda registrado quién la cambió y por qué.
     */
    @Transactional
    public void ponerNota(ContextoUsuario quien, Long postulacionId, List<Criterio> rubrica,
                          Long criterioId, double puntaje, String explicacion) {
        Criterio criterio = rubrica.stream()
                .filter(c -> c.getId().equals(criterioId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ese criterio no pertenece a la rúbrica de esta etapa"));

        BigDecimal maximo = maximosDe(postulacionId, rubrica).get(criterioId);
        BigDecimal valor = BigDecimal.valueOf(puntaje);
        if (valor.compareTo(BigDecimal.ZERO) < 0 || (maximo != null && valor.compareTo(maximo) > 0)) {
            throw new IllegalArgumentException(
                    "El puntaje de «%s» tiene que estar entre 0 y %s".formatted(criterio.getNombre(), maximo));
        }

        NotaCriterio fila = notasCriterio.findByPostulacionIdAndCriterioId(postulacionId, criterioId)
                .orElseGet(() -> NotaCriterio.builder()
                        .postulacionId(postulacionId)
                        .criterioId(criterioId)
                        .creadoEn(Instant.now())
                        .build());
        boolean esCorreccion = fila.getId() != null;
        fila.setPuntaje(valor);
        fila.setExplicacion(explicacion);
        // Quien la pone a mano es una persona. Cuando el agente exista, entrará como AGENTE,
        // y lo que RENASER OS alimente solo, como AUTOMATICO (RF-111).
        fila.setOrigen("PERSONA");
        fila.setCalificadaPorUsuarioId(quien.usuarioId());
        if (esCorreccion) {
            fila.setAjustadaPorUsuarioId(quien.usuarioId());
            fila.setMotivoAjuste(explicacion);
            fila.setAjustadaEn(Instant.now());
        }
        notasCriterio.save(fila);
    }

    /**
     * Suma las notas puestas y guarda la nota de la etapa.
     *
     * <p>Cada {@code criterio.puntos} ya es su peso dentro de 100 —la rúbrica suma 100—, así que
     * sumar los puntajes obtenidos da directamente la nota. La nota queda atada a la versión de
     * pesos <b>de la vacante</b>, no a la última publicada: es lo que permite reconstruir una
     * decisión vieja tal como se tomó (RF-114).
     */
    @Transactional
    public BigDecimal calcularNotaEtapa(Postulacion postulacion, String etapaCodigo, List<Criterio> rubrica) {
        Map<Long, NotaCriterio> puestas = notasCriterio.findByPostulacionId(postulacion.getId()).stream()
                .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity()));

        List<String> faltan = rubrica.stream()
                .filter(c -> !puestas.containsKey(c.getId()))
                .map(Criterio::getNombre)
                .toList();
        if (!faltan.isEmpty()) {
            throw new IllegalStateException("Todavía faltan notas por poner: " + String.join(", ", faltan));
        }

        BigDecimal nota = rubrica.stream()
                .map(c -> puestas.get(c.getId()).getPuntaje())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Vacante vacante = vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException("La vacante de esta postulación ya no existe"));

        NotaEtapa fila = notasEtapa.findByPostulacionIdAndEtapaCodigo(postulacion.getId(), etapaCodigo)
                .orElseGet(() -> NotaEtapa.builder()
                        .postulacionId(postulacion.getId())
                        .etapaCodigo(etapaCodigo)
                        .creadoEn(Instant.now())
                        .build());
        fila.setPuntaje(nota);
        fila.setVersionPesosId(vacante.getVersionPesosId());
        fila.setCalculadaEn(Instant.now());
        notasEtapa.save(fila);
        return nota;
    }

    /** Una fila de la rúbrica con su nota, si ya la tiene. */
    public record Vista(Long criterioId, String nombre, Double puntosMaximos,
                        Double puntaje, String explicacion, String origen) {}
}
