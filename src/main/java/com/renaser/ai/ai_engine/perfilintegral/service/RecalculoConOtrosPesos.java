package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.impl.CalificacionCriterios;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rehacer las notas de todos los candidatos de una vacante cuando cambia su versión de pesos
 * (V74, specs/pesos-con-candidatos-y-fin-del-prestamo.md, parte A).
 *
 * <p><b>Ni IA, ni máquina de estados, ni decisiones.</b> Se reponderan las notas que ya
 * existen con los pesos que la vacante tiene AHORA —quien llama ya le puso la versión nueva—
 * y nada más: no se encola ningún trabajo, nadie cambia de etapa y la fila de {@code decision}
 * de quien ya fue decidido conserva la nota y la versión con que se decidió. La vara cambia,
 * pero para todos a la vez.
 *
 * <p>Qué cambia de valor y qué no:
 * <ul>
 *   <li><b>Perfil Integral</b>: el currículum (sus ocho criterios con el peso del nivel), la
 *       evaluación y el reparto entre los dos. Por el mismo camino que el ajuste a mano
 *       ({@link PuenteCalificacionIa#recalcularConOtrosPesos}), que además rehace el grupo de
 *       prioridad con los hallazgos guardados. Con el banco {@code CRITERIOS}, antes se
 *       repondera su índice con las dimensiones nuevas, en el mismo orden que el flujo de
 *       siempre.</li>
 *   <li><b>Simulación y validación</b>: cada nota de criterio está en la escala de su peso
 *       (un 8 sobre 10), así que se lleva a la escala del peso nuevo (12 sobre 15) y la nota
 *       de la etapa se vuelve a sumar con {@link CalificacionPorCriterio#calcularNotaEtapa},
 *       que solo escribe la nota.</li>
 *   <li><b>Prueba técnica</b>: lleva sus propios puntos y no cambia de valor. Solo pasa a
 *       decir la versión nueva.</li>
 *   <li><b>La nota final</b> no se guarda: el semáforo y el ranking la calculan al vuelo con
 *       los pesos de etapa de la vacante.</li>
 * </ul>
 *
 * <p>⚠️ <b>Corre dentro de la transacción de quien cambia la versión</b>, con la vacante ya
 * bloqueada: si algo falla a mitad, no se guarda nada y la vacante sigue con la anterior. El
 * ranking no filtra por versión, pero así tampoco hay un instante con la mitad reescrita.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecalculoConOtrosPesos {

    static final String PERFIL_INTEGRAL = "PERFIL_INTEGRAL";
    static final String PRUEBA_PUESTO = "PRUEBA_PUESTO";
    static final String SIMULACION = "SIMULACION";
    static final String VALIDACION = "VALIDACION";

    private final PostulacionRepository postulaciones;
    private final NotaEtapaRepository notasEtapa;
    private final NotaCriterioRepository notasCriterio;
    private final PesoCriterioRepository pesosCriterio;
    private final PerfilTalentoRepository perfiles;
    private final CalificacionPorCriterio porCriterio;
    private final CalificacionCriterios bancoCriterios;
    private final PuenteCalificacionIa puente;

    /**
     * Las postulaciones de la vacante con alguna nota: una de etapa o un retrato. Son las que
     * un cambio de pesos recalcula, y las que cuenta la confirmación. Quien todavía no tiene
     * ninguna no cuenta: su nota saldrá ya con la versión nueva.
     */
    public List<Long> conAlgunaNota(Long vacanteId) {
        List<Long> ids = idsDe(postulaciones.findByVacanteIdOrderByCreadoEnDesc(vacanteId));
        if (ids.isEmpty()) {
            return List.of();
        }
        Set<Long> conNota = new HashSet<>();
        notasEtapa.findByPostulacionIdIn(ids).forEach(n -> conNota.add(n.getPostulacionId()));
        perfiles.findByPostulacionIdIn(ids).forEach(p -> conNota.add(p.getPostulacionId()));
        return ids.stream().filter(conNota::contains).toList();
    }

    /**
     * Rehace las notas de toda la vacante con la versión que tiene ahora.
     *
     * @param vacante         la vacante, bloqueada y ya con la versión nueva
     * @param versionAnterior la que tenía: dice en qué escala están las notas de simulación y
     *                        validación
     * @param nivel           el nivel del puesto, que elige los pesos de criterio
     * @return cuántas personas se recalcularon (las de {@link #conAlgunaNota})
     */
    @Transactional
    public int recalcular(Vacante vacante, Long versionAnterior, String nivel) {
        Long versionNueva = vacante.getVersionPesosId();
        List<Postulacion> todas = postulaciones.findByVacanteIdOrderByCreadoEnDesc(vacante.getId());
        List<Long> ids = idsDe(todas);
        if (ids.isEmpty()) {
            return 0;
        }
        Map<Long, List<NotaEtapa>> etapasDe = notasEtapa.findByPostulacionIdIn(ids).stream()
                .collect(Collectors.groupingBy(NotaEtapa::getPostulacionId));
        Map<Long, PerfilTalento> perfilDe = perfiles.findByPostulacionIdIn(ids).stream()
                .collect(Collectors.toMap(PerfilTalento::getPostulacionId, Function.identity(),
                        (a, b) -> a));
        Map<Long, List<NotaCriterio>> criteriosDe = notasCriterio.findByPostulacionIdIn(ids).stream()
                .collect(Collectors.groupingBy(NotaCriterio::getPostulacionId));

        Escalas escalas = new Escalas(pesosDelNivel(versionAnterior, nivel),
                pesosDelNivel(versionNueva, nivel), reescalables());

        int recalculadas = 0;
        for (Postulacion postulacion : todas) {
            // Antes que nada, aunque no tenga nota de etapa: un facilitador puede estar a mitad
            // de la rúbrica, y lo que siga poniendo ya va en la escala nueva.
            List<NotaCriterio> reescaladas =
                    escalas.llevarALaNueva(criteriosDe.getOrDefault(postulacion.getId(), List.of()));
            if (!reescaladas.isEmpty()) {
                notasCriterio.saveAll(reescaladas);
            }

            List<NotaEtapa> suyas = etapasDe.getOrDefault(postulacion.getId(), List.of());
            PerfilTalento perfil = perfilDe.get(postulacion.getId());
            if (suyas.isEmpty() && perfil == null) {
                continue;
            }
            recalcularUna(postulacion, porEtapa(suyas), perfil, versionNueva);
            recalculadas++;
        }
        log.info("PESOS: la vacante {} pasó de la versión {} a la {}: {} personas recalculadas, "
                + "sin IA y sin mover a nadie", vacante.getId(), versionAnterior, versionNueva,
                recalculadas);
        return recalculadas;
    }

    private void recalcularUna(Postulacion postulacion, Map<String, NotaEtapa> suyas,
                               PerfilTalento perfil, Long versionNueva) {
        perfilIntegral(postulacion, suyas.get(PERFIL_INTEGRAL), perfil, versionNueva);

        if (perfil != null) {
            // El retrato no cambia: solo pasa a decir con qué versión se lee su nota.
            perfil.setVersionPesosId(versionNueva);
            perfiles.save(perfil);
        }

        // La prueba técnica lleva sus propios puntos: su nota vale lo mismo.
        NotaEtapa prueba = suyas.get(PRUEBA_PUESTO);
        if (prueba != null) {
            prueba.setVersionPesosId(versionNueva);
            notasEtapa.save(prueba);
        }

        for (String etapa : List.of(SIMULACION, VALIDACION)) {
            if (suyas.containsKey(etapa)) {
                sumarDeNuevo(postulacion, etapa);
            }
        }
    }

    /**
     * La nota del Perfil Integral, por el camino que la escribió.
     *
     * <p>Hay tres orígenes posibles y cada uno se rehace con el suyo, para que el número sea
     * el mismo que habría salido de calcularla hoy por primera vez:
     * <ul>
     *   <li>con el banco {@code CRITERIOS}, el índice de los pilares con las dimensiones de
     *       la versión nueva (lo escribe el evaluador antes del retrato);</li>
     *   <li>con el retrato hecho —o con preguntas propias o sin evaluación, donde la nota solo
     *       la escribe el recálculo—, el currículum y la evaluación con el reparto nuevo, y el
     *       grupo;</li>
     *   <li>con el banco de antes y sin retrato todavía, la nota de lo cerrado, que no depende
     *       de los pesos: solo pasa a decir la versión nueva.</li>
     * </ul>
     */
    private void perfilIntegral(Postulacion postulacion, NotaEtapa nota, PerfilTalento perfil,
                                Long versionNueva) {
        String metodo = bancoCriterios.metodoDe(postulacion);
        boolean criterios = CalificacionCriterios.METODO.equals(metodo);
        if (criterios) {
            bancoCriterios.calificarEtapa(postulacion);
        }
        boolean laEscribeElRecalculo = postulacion.getEvaluacionId() == null
                || CalificacionPorPuntos.METODO.equals(metodo);
        if (perfil != null || (nota != null && laEscribeElRecalculo)) {
            puente.recalcularConOtrosPesos(postulacion.getId());
        } else if (nota != null && !criterios) {
            nota.setVersionPesosId(versionNueva);
            notasEtapa.save(nota);
        }
    }

    /** La simulación o la validación, vuelta a sumar con sus notas ya en la escala nueva. */
    private void sumarDeNuevo(Postulacion postulacion, String etapa) {
        try {
            porCriterio.calcularNotaEtapa(postulacion, etapa, porCriterio.rubricaGlobalDe(etapa));
        } catch (IllegalStateException e) {
            throw new IllegalStateException("No se pudo recalcular la nota de " + etapa
                    + " de la postulación " + postulacion.getId() + " (" + e.getMessage()
                    + "): no se cambió nada", e);
        }
    }

    /**
     * Los criterios que cambian de escala con los pesos: los globales de la simulación y de
     * la validación, que valen su {@code peso_criterio}. Uno con puntos propios no.
     */
    private Set<Long> reescalables() {
        Set<Long> ids = new HashSet<>();
        for (String etapa : List.of(SIMULACION, VALIDACION)) {
            porCriterio.rubricaGlobalDe(etapa).stream()
                    .filter(c -> c.getPuntos() == null)
                    .map(Criterio::getId)
                    .forEach(ids::add);
        }
        return ids;
    }

    private Map<Long, BigDecimal> pesosDelNivel(Long version, String nivel) {
        if (version == null || nivel == null) {
            return Map.of();
        }
        Map<Long, BigDecimal> pesos = new HashMap<>();
        for (PesoCriterio pc : pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(version, nivel)) {
            pesos.putIfAbsent(pc.getCriterioId(), pc.getPeso());
        }
        return pesos;
    }

    private static Map<String, NotaEtapa> porEtapa(List<NotaEtapa> notas) {
        return notas.stream().collect(Collectors.toMap(NotaEtapa::getEtapaCodigo,
                Function.identity(), (a, b) -> a));
    }

    private static List<Long> idsDe(List<Postulacion> todas) {
        return todas.stream().map(Postulacion::getId).filter(Objects::nonNull).toList();
    }

    /**
     * Lo que vale cada criterio reescalable en la versión de antes y en la de ahora.
     *
     * <p>Las notas de la simulación y la validación se ponen sobre el peso del criterio: un 8
     * sobre 10. Si la versión nueva le da 15, ese mismo desempeño es un 12. Se guarda así,
     * en la escala nueva, porque es la que enseña el panel al lado del máximo y la que suma
     * {@code calcularNotaEtapa}.
     */
    private record Escalas(Map<Long, BigDecimal> antes, Map<Long, BigDecimal> ahora,
                           Set<Long> reescalables) {

        /** Pone cada nota en la escala nueva y devuelve las que cambiaron. */
        List<NotaCriterio> llevarALaNueva(List<NotaCriterio> notas) {
            List<NotaCriterio> cambiadas = new ArrayList<>();
            for (NotaCriterio nota : notas) {
                if (!reescalables.contains(nota.getCriterioId()) || nota.getPuntaje() == null) {
                    continue;
                }
                BigDecimal nueva = reescalada(nota.getPuntaje(), antes.get(nota.getCriterioId()),
                        ahora.get(nota.getCriterioId()));
                if (nueva.compareTo(nota.getPuntaje()) != 0) {
                    nota.setPuntaje(nueva);
                    cambiadas.add(nota);
                }
            }
            return cambiadas;
        }
    }

    /**
     * Un puntaje de la escala de antes llevado a la de ahora.
     *
     * <p>Sin peso en la versión nueva no hay escala a la que llevarlo y se queda como está.
     * Sin peso en la de antes (o con cero) no hay de dónde sacar la proporción: el puntaje solo
     * podía ser cero, y se acota al máximo nuevo por si acaso.
     */
    static BigDecimal reescalada(BigDecimal puntaje, BigDecimal maximoAntes, BigDecimal maximoAhora) {
        if (maximoAhora == null) {
            return puntaje;
        }
        if (maximoAntes == null || maximoAntes.signum() == 0) {
            return puntaje.min(maximoAhora);
        }
        return puntaje.multiply(maximoAhora).divide(maximoAntes, 2, RoundingMode.HALF_UP);
    }
}
