package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.perfilintegral.service.RecalculoConOtrosPesos;
import com.renaser.ai.ai_engine.pesos.entity.Etapa;
import com.renaser.ai.ai_engine.pesos.entity.PesoComponentePerfil;
import com.renaser.ai.ai_engine.pesos.entity.PesoEtapa;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.EtapaRepository;
import com.renaser.ai.ai_engine.pesos.repository.PesoComponentePerfilRepository;
import com.renaser.ai.ai_engine.pesos.repository.PesoEtapaRepository;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.CambioDePesosHecho;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.CambioDePesosPrevisto;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.PesoAntesYDespues;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.ServicioPesosDeLaVacante;
import com.renaser.ai.ai_engine.vacante.service.VacanteArchivada;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Ver {@link ServicioPesosDeLaVacante}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioPesosDeLaVacanteImpl implements ServicioPesosDeLaVacante {

    private static final String ESTADO_CERRADA = "CERRADA";
    private static final String ESTADO_PUBLICADA = "PUBLICADA";

    /**
     * Los componentes del Perfil Integral, en el orden en que se enseñan. El psicométrico no
     * existe todavía y su peso está en cero: solo sale si alguna de las dos versiones lo usa.
     */
    private static final List<Map.Entry<String, String>> COMPONENTES = List.of(
            Map.entry("CV", "Currículum"),
            Map.entry("EVALUACION", "Preguntas"),
            Map.entry("PSICOMETRICO", "Psicométrico"));

    /** La etapa que se decide, no se puntúa: no tiene peso que enseñar. */
    private static final String ETAPA_SIN_NOTA = "DECISION";

    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;
    private final VersionPesosRepository versionesPesos;
    private final PesoEtapaRepository pesosEtapa;
    private final PesoComponentePerfilRepository pesosComponente;
    private final EtapaRepository etapas;
    private final DuenoDelInstrumento dueno;
    private final RecalculoConOtrosPesos recalculo;
    private final ServicioAuditoria auditoria;

    @Override
    public CambioDePesosPrevisto preverCambio(ContextoUsuario quien, Long vacanteId,
                                              Long versionPesosId) {
        Vacante vacante = vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(
                        vacanteId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
        VersionPesos nueva = laQueSePuedeElegir(quien, versionPesosId);
        Long anterior = vacante.getVersionPesosId();
        boolean cambia = !nueva.getId().equals(anterior);
        int personas = cambia ? recalculo.conAlgunaNota(vacanteId).size() : 0;
        return new CambioDePesosPrevisto(anterior, etiquetaDe(quien, anterior),
                nueva.getId(), nueva.getEtiqueta(), personas, cambia,
                pesosDeEtapa(anterior, nueva.getId()),
                pesosDelPerfilIntegral(anterior, nueva.getId()));
    }

    @Override
    @Transactional
    public CambioDePesosHecho cambiar(ContextoUsuario quien, Long vacanteId, Long versionPesosId) {
        Vacante vacante = laQueAceptaOtrosPesos(quien, vacanteId);
        VersionPesos nueva = laQueSePuedeElegir(quien, versionPesosId);

        Long anterior = vacante.getVersionPesosId();
        // La misma que ya tiene: no hay nada que recalcular ni que registrar.
        if (nueva.getId().equals(anterior)) {
            return new CambioDePesosHecho(anterior, 0);
        }

        vacante.setVersionPesosId(nueva.getId());
        vacantes.saveAndFlush(vacante);
        // Todo en esta transacción y con la vacante bloqueada: si una nota no se puede
        // recalcular, la vacante sigue con la versión anterior y ninguna nota cambia. Lo que
        // llegue después —una calificación de la IA en curso, alguien que postula— lee la
        // versión de la vacante al calcular, y ya es la nueva.
        int recalculadas = anterior == null ? 0
                : recalculo.recalcular(vacante, anterior, nivelDe(vacante));

        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("versionPesosId", String.valueOf(anterior));
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("versionPesosId", String.valueOf(nueva.getId()));
        despues.put("personasRecalculadas", recalculadas);
        auditoria.registrar(quien.organizacionId(), quien, "asignar_version_pesos",
                "vacante", vacanteId, antes, despues, null);
        return new CambioDePesosHecho(nueva.getId(), recalculadas);
    }

    /**
     * La vacante bloqueada, si todavía admite otros pesos: ni cerrada, ni archivada, ni
     * eliminada (409 las tres). Bloqueada porque dos cambios seguidos no se mezclan: el
     * segundo espera a que el primero termine y parte de lo que dejó.
     */
    private Vacante laQueAceptaOtrosPesos(ContextoUsuario quien, Long vacanteId) {
        Optional<Vacante> bloqueada = vacantes.bloquear(vacanteId, quien.organizacionId());
        if (bloqueada.isEmpty()) {
            // La eliminada existe y es de esta empresa: no es un «no existe», es un «ya no».
            boolean eliminada = vacantes.findByIdAndOrganizacionId(vacanteId, quien.organizacionId())
                    .map(v -> v.getEliminadaEn() != null)
                    .orElse(false);
            if (eliminada) {
                throw new IllegalStateException("Esta vacante se eliminó: sus pesos ya no se "
                        + "cambian");
            }
            throw new ResourceNotFoundException("Vacante", "id", vacanteId);
        }
        Vacante vacante = bloqueada.get();
        VacanteArchivada.exigirQueNoLoEste(vacante);
        if (ESTADO_CERRADA.equals(vacante.getEstado())) {
            throw new IllegalStateException("Esta vacante está cerrada: sus pesos ya no se "
                    + "cambian. Las notas se quedan con los que tenía al cerrarse.");
        }
        return vacante;
    }

    /**
     * La versión pedida, si esta empresa puede usarla: de su dueño resuelto —la suya si
     * personalizó los pesos, la de la plataforma si no— y publicada (RF-114).
     */
    private VersionPesos laQueSePuedeElegir(ContextoUsuario quien, Long versionPesosId) {
        VersionPesos version = versionesPesos
                .findByIdAndOrganizacionId(versionPesosId,
                        dueno.duenoDe(quien.organizacionId(), Instrumento.PESOS))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Versión de pesos", "id", versionPesosId));
        if (!ESTADO_PUBLICADA.equals(version.getEstado())) {
            throw new IllegalStateException(
                    "Esa versión de pesos todavía está en borrador: solo se puede usar una publicada");
        }
        return version;
    }

    /**
     * El nombre de la versión que la vacante tiene ahora. Puede ser de la plataforma aunque
     * la empresa haya personalizado después sus pesos: se busca en las dos, nunca por id suelto.
     */
    private String etiquetaDe(ContextoUsuario quien, Long versionId) {
        if (versionId == null) {
            return null;
        }
        return versionesPesos.findByIdAndOrganizacionId(versionId, quien.organizacionId())
                .or(() -> versionesPesos.findByIdAndOrganizacionId(versionId,
                        dueno.plataforma().getId()))
                .map(VersionPesos::getEtiqueta)
                .orElse(null);
    }

    /** Cuánto pesa cada etapa en la nota final, antes y después, en el orden del proceso. */
    private List<PesoAntesYDespues> pesosDeEtapa(Long anterior, Long nueva) {
        Map<String, BigDecimal> antes = porEtapa(anterior);
        Map<String, BigDecimal> despues = porEtapa(nueva);
        List<PesoAntesYDespues> filas = new ArrayList<>();
        for (Etapa etapa : etapas.findAllByOrderByOrdenAsc()) {
            if (ETAPA_SIN_NOTA.equals(etapa.getCodigo())) {
                continue;
            }
            BigDecimal a = antes.get(etapa.getCodigo());
            BigDecimal d = despues.get(etapa.getCodigo());
            if (a != null || d != null) {
                filas.add(new PesoAntesYDespues(etapa.getCodigo(), etapa.getNombre(), a, d));
            }
        }
        return filas;
    }

    /** Cómo se reparte el Perfil Integral entre currículum y preguntas, antes y después. */
    private List<PesoAntesYDespues> pesosDelPerfilIntegral(Long anterior, Long nueva) {
        Map<String, BigDecimal> antes = porComponente(anterior);
        Map<String, BigDecimal> despues = porComponente(nueva);
        List<PesoAntesYDespues> filas = new ArrayList<>();
        for (Map.Entry<String, String> componente : COMPONENTES) {
            BigDecimal a = antes.get(componente.getKey());
            BigDecimal d = despues.get(componente.getKey());
            if (conPeso(a) || conPeso(d)) {
                filas.add(new PesoAntesYDespues(componente.getKey(), componente.getValue(), a, d));
            }
        }
        return filas;
    }

    private Map<String, BigDecimal> porEtapa(Long version) {
        if (version == null) {
            return Map.of();
        }
        return pesosEtapa.findByVersionPesosId(version).stream()
                .collect(Collectors.toMap(PesoEtapa::getEtapaCodigo, PesoEtapa::getPeso,
                        (a, b) -> a));
    }

    private Map<String, BigDecimal> porComponente(Long version) {
        if (version == null) {
            return Map.of();
        }
        return pesosComponente.findByVersionPesosId(version).stream()
                .collect(Collectors.toMap(PesoComponentePerfil::getComponente,
                        PesoComponentePerfil::getPeso, (a, b) -> a));
    }

    private static boolean conPeso(BigDecimal peso) {
        return peso != null && peso.signum() != 0;
    }

    private String nivelDe(Vacante vacante) {
        return vacante.getPuestoId() == null ? null
                : puestos.findByIdAndOrganizacionId(vacante.getPuestoId(), vacante.getOrganizacionId())
                        .map(Puesto::getNivelPuestoCodigo).orElse(null);
    }
}
