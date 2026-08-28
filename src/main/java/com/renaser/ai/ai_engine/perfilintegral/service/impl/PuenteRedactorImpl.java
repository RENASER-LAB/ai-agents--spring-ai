package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.BloquePedido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.FichaDelDueno;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.InsumoRedactor;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.PreguntaGenerada;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.ResultadoRedactor;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteRedactor;
import com.renaser.ai.ai_engine.perfilintegral.service.RecetaCuestionarioTecnico;
import com.renaser.ai.ai_engine.vacante.entity.FichaVacante;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.FichaVacanteRepository;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PuenteRedactorImpl implements PuenteRedactor {

    private final VacanteRepository vacantes;
    private final FichaVacanteRepository fichas;
    private final PuestoRepository puestos;
    private final VersionBancoRepository versionesBanco;
    private final PreguntaRepository preguntas;

    @Override
    public InsumoRedactor insumo(Long vacanteId) {
        Vacante vacante = vacantes.findById(vacanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
        FichaVacante ficha = fichas.findByVacanteId(vacanteId)
                .orElseThrow(() -> new IllegalStateException(
                        "La vacante " + vacanteId + " no tiene ficha: sin ella no hay cuestionario"));
        if (!"COMPLETA".equals(ficha.getEstado())) {
            // Media ficha no es un insumo: generar con huecos escribiría preguntas
            // genéricas, que es exactamente lo que el método prohíbe.
            throw new IllegalStateException("La ficha de la vacante " + vacanteId
                    + " está a medias: se genera con la ficha COMPLETA");
        }
        String nivel = puestos.findById(vacante.getPuestoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Puesto", "id", vacante.getPuestoId()))
                .getNivelPuestoCodigo();

        // La estructura del nivel, con el tema de cada bloque puesto desde la ficha: los
        // riesgos en el orden de daño del dueño, y la muestra sobre el resultado esperado.
        List<BloquePedido> estructura = new ArrayList<>();
        for (BloquePedido bloque : RecetaCuestionarioTecnico.estructura(nivel)) {
            String tema = switch (bloque.bloque()) {
                case RecetaCuestionarioTecnico.RIESGO_1 -> ficha.getRiesgo1();
                case RecetaCuestionarioTecnico.RIESGO_2 -> ficha.getRiesgo2();
                case RecetaCuestionarioTecnico.RIESGO_3 -> ficha.getRiesgo3();
                case RecetaCuestionarioTecnico.REQUERIMIENTO -> ficha.getQ9Requerimientos();
                case RecetaCuestionarioTecnico.PRESENCIAL -> ficha.getQ1Resultado();
                default -> null;
            };
            estructura.add(new BloquePedido(bloque.bloque(), bloque.cantidad(), tema));
        }

        return new InsumoRedactor(nivel, vacante.getTitulo(), vacante.getDescripcion(),
                new FichaDelDueno(
                        ficha.getQ1Resultado(), ficha.getQ2Riesgo(), ficha.getQ3DiaReal(),
                        ficha.getQ4EpocaDorada(), ficha.getQ5Estructura(), ficha.getQ6Autonomia(),
                        ficha.getQ7JefeDirecto(), ficha.getQ8LoIncomodo(),
                        ficha.getQ9Requerimientos(), ficha.getQ10Espejo(),
                        ficha.getGenteEnEmpresa(), ficha.getGenteACargo(),
                        ficha.getRiesgo1(), ficha.getRiesgo2(), ficha.getRiesgo3(),
                        ficha.getRiesgo4(),
                        ficha.getEliminatoria1(), ficha.getEliminatoria2(),
                        ficha.getRequerimiento1(), ficha.getRequerimiento2(),
                        ficha.getRequerimiento3(), ficha.getFamilias()),
                estructura);
    }

    @Override
    @Transactional
    public void guardarBorrador(Long vacanteId, ResultadoRedactor resultado) {
        Vacante vacante = vacantes.findById(vacanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
        String nivel = puestos.findById(vacante.getPuestoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Puesto", "id", vacante.getPuestoId()))
                .getNivelPuestoCodigo();

        // Regenerar reemplaza: el borrador anterior se archiva (nada se borra) y el
        // índice parcial de V42 garantiza que nunca haya dos vivos.
        versionesBanco.findFirstByVacanteIdAndEstado(vacanteId, "BORRADOR")
                .ifPresent(anterior -> {
                    anterior.setEstado("ARCHIVADA");
                    versionesBanco.save(anterior);
                    log.info("El borrador {} de la vacante {} queda archivado: lo reemplaza "
                            + "una generación nueva", anterior.getId(), vacanteId);
                });

        VersionBanco version = versionesBanco.save(VersionBanco.builder()
                .organizacionId(vacante.getOrganizacionId())
                .tipoBanco("VACANTE")
                .nivelPuestoCodigo(nivel)
                .vacanteId(vacanteId)
                .metodoCalificacion("CRITERIOS")
                .etiqueta("Cuestionario técnico · " + vacante.getTitulo())
                .estado("BORRADOR")
                .creadoEn(Instant.now())
                .build());

        int orden = 1;
        for (PreguntaGenerada p : resultado.preguntas()) {
            boolean presencial = Boolean.TRUE.equals(p.presencial());
            preguntas.save(Pregunta.builder()
                    .versionBancoId(version.getId())
                    .codigo(p.codigo())
                    .bloque(p.bloqueEtiqueta() == null ? p.bloque() : p.bloqueEtiqueta())
                    .tipo("ABIERTA")
                    .enunciado(p.enunciado())
                    // La muestra no se envía ni se puntúa en el formulario; todo lo demás
                    // puntúa con peso 1: el índice técnico no pondera por ítem.
                    .esPuntuable(!presencial)
                    .presencial(presencial)
                    .peso((short) 1)
                    .c3Esperado(p.c3Esperado())
                    .c4Esperado(p.c4Esperado())
                    .senalDeCero(p.senalDeCero())
                    .orden(orden++)
                    .creadoEn(Instant.now())
                    .build());
        }
        log.info("El REDACTOR dejó el borrador {} de la vacante {}: {} preguntas",
                version.getId(), vacanteId, resultado.preguntas().size());
    }
}
