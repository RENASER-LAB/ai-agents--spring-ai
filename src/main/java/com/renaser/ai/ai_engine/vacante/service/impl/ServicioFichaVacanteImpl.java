package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.dto.DtosFichaVacante.FichaResponse;
import com.renaser.ai.ai_engine.vacante.dto.DtosFichaVacante.GuardarFicha;
import com.renaser.ai.ai_engine.vacante.dto.DtosFichaVacante.PesosSugeridos;
import com.renaser.ai.ai_engine.vacante.entity.FichaVacante;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.FichaVacanteRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.ServicioFichaVacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Ver docs/DISENO-PRUEBA-TECNICA-FICHA-Y-REDACTOR.md. Las tres derivaciones que no se
// parsean de texto libre: el TAMAÑO sale de gente_en_empresa, el orden de los riesgos
// es la velocidad de daño (lo decide el dueño), y COMPLETA se calcula, no se declara.
@Service
@RequiredArgsConstructor
public class ServicioFichaVacanteImpl implements ServicioFichaVacante {

    private final FichaVacanteRepository fichas;
    private final VacanteRepository vacantes;
    private final VersionPesosRepository versionesPesos;
    private final DuenoDelInstrumento dueno;
    private final ServicioAuditoria auditoria;

    @Override
    public FichaResponse ver(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laDeLaOrganizacion(quien, vacanteId);
        FichaVacante ficha = fichas.findByVacanteId(vacante.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ficha de vacante", "vacanteId", vacanteId));
        return comoRespuesta(quien, vacante, ficha);
    }

    @Override
    @Transactional
    public FichaResponse guardar(ContextoUsuario quien, Long vacanteId, GuardarFicha datos) {
        Vacante vacante = laDeLaOrganizacion(quien, vacanteId);
        if ("CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada no cambia su ficha");
        }
        exigirOrden(datos.riesgo1(), datos.riesgo2(), datos.riesgo3(), datos.riesgo4(),
                "los riesgos van en orden de velocidad de daño: no puede haber riesgo 3 sin riesgo 2");
        exigirOrden(datos.eliminatoria1(), datos.eliminatoria2(), null, null,
                "no puede haber eliminatoria 2 sin eliminatoria 1");
        exigirOrden(datos.requerimiento1(), datos.requerimiento2(), datos.requerimiento3(), null,
                "los requerimientos se llenan en orden: no puede haber el 2 sin el 1");

        FichaVacante ficha = fichas.findByVacanteId(vacante.getId())
                .orElseGet(() -> FichaVacante.builder()
                        .vacanteId(vacante.getId())
                        .organizacionId(quien.organizacionId())
                        .estado("BORRADOR")
                        .creadoEn(Instant.now())
                        .build());
        String estadoAnterior = ficha.getEstado();

        ficha.setQ1Resultado(datos.q1Resultado());
        ficha.setQ2Riesgo(datos.q2Riesgo());
        ficha.setQ3DiaReal(datos.q3DiaReal());
        ficha.setQ4EpocaDorada(datos.q4EpocaDorada());
        ficha.setQ5Estructura(datos.q5Estructura());
        ficha.setQ6Autonomia(datos.q6Autonomia());
        ficha.setQ7JefeDirecto(datos.q7JefeDirecto());
        ficha.setQ8LoIncomodo(datos.q8LoIncomodo());
        ficha.setQ9Requerimientos(datos.q9Requerimientos());
        ficha.setQ10Espejo(datos.q10Espejo());
        ficha.setGenteEnEmpresa(datos.genteEnEmpresa());
        ficha.setGenteACargo(datos.genteACargo());
        ficha.setRiesgo1(datos.riesgo1());
        ficha.setRiesgo2(datos.riesgo2());
        ficha.setRiesgo3(datos.riesgo3());
        ficha.setRiesgo4(datos.riesgo4());
        ficha.setEliminatoria1(datos.eliminatoria1());
        ficha.setEliminatoria2(datos.eliminatoria2());
        ficha.setRequerimiento1(datos.requerimiento1());
        ficha.setRequerimiento2(datos.requerimiento2());
        ficha.setRequerimiento3(datos.requerimiento3());
        ficha.setFamilias(datos.familias());
        ficha.setTamano(tamanoDe(datos.genteEnEmpresa()));
        ficha.setEstado(estadoDe(ficha));
        ficha.setActualizadoEn(Instant.now());
        fichas.save(ficha);

        auditoria.registrar(quien.organizacionId(), quien, "guardar_ficha_vacante",
                "ficha_vacante", ficha.getId(),
                Map.of("estado", estadoAnterior == null ? "(nueva)" : estadoAnterior),
                Map.of("estado", ficha.getEstado(),
                       "tamano", ficha.getTamano() == null ? "(sin derivar)" : ficha.getTamano()),
                null);
        return comoRespuesta(quien, vacante, ficha);
    }

    /** ≤30 MICRO · 31–200 MEDIA · 200+ GRANDE. La única dependencia de la etapa 2 hacia la 1. */
    static String tamanoDe(Integer genteEnEmpresa) {
        if (genteEnEmpresa == null) {
            return null;
        }
        if (genteEnEmpresa <= 30) {
            return "MICRO";
        }
        return genteEnEmpresa <= 200 ? "MEDIA" : "GRANDE";
    }

    // COMPLETA es lo que enciende «generar cuestionario»: exige las nueve preguntas
    // (el espejo Q10 es opcional), la estructura en números, los cuatro riesgos, la
    // primera eliminatoria y las familias de textura.
    private static String estadoDe(FichaVacante f) {
        boolean completa = conTexto(f.getQ1Resultado()) && conTexto(f.getQ2Riesgo())
                && conTexto(f.getQ3DiaReal()) && conTexto(f.getQ4EpocaDorada())
                && conTexto(f.getQ5Estructura()) && conTexto(f.getQ6Autonomia())
                && conTexto(f.getQ7JefeDirecto()) && conTexto(f.getQ8LoIncomodo())
                && conTexto(f.getQ9Requerimientos())
                && f.getGenteEnEmpresa() != null && f.getGenteACargo() != null
                && conTexto(f.getRiesgo1()) && conTexto(f.getRiesgo2())
                && conTexto(f.getRiesgo3()) && conTexto(f.getRiesgo4())
                && conTexto(f.getEliminatoria1()) && conTexto(f.getFamilias());
        return completa ? "COMPLETA" : "BORRADOR";
    }

    private static boolean conTexto(String s) {
        return s != null && !s.isBlank();
    }

    // Llenar el 3 sin el 2 no es un capricho de validación: el hueco rompería el orden,
    // y aquí el orden significa velocidad de daño.
    private static void exigirOrden(String a, String b, String c, String d, String mensaje) {
        boolean hueco = (conTexto(b) && !conTexto(a))
                || (conTexto(c) && !conTexto(b))
                || (conTexto(d) && !conTexto(c));
        if (hueco) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    // La versión de pesos CAZATALENTOS del tamaño derivado, para asignarla con un clic.
    // Se busca por etiqueta porque así se sembraron en V41 («CAZATALENTOS · MICRO» /
    // «CAZATALENTOS · MEDIA/GRANDE»); si un día llevan un campo propio, esto se afina.
    private PesosSugeridos sugerenciaDePesos(ContextoUsuario quien, Vacante vacante, String tamano) {
        if (tamano == null) {
            return null;
        }
        String marca = "MICRO".equals(tamano) ? "MICRO" : "MEDIA/GRANDE";
        List<VersionPesos> deLaPlataforma = versionesPesos.findByOrganizacionIdOrderByCreadoEnDesc(
                dueno.duenoDe(quien.organizacionId(), Instrumento.PESOS));
        return deLaPlataforma.stream()
                .filter(v -> "PUBLICADA".equals(v.getEstado()))
                .filter(v -> v.getEtiqueta() != null && v.getEtiqueta().contains("CAZATALENTOS")
                        && v.getEtiqueta().contains(marca))
                .max(Comparator.comparing(VersionPesos::getPublicadaEn,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(v -> new PesosSugeridos(v.getId(), v.getEtiqueta(),
                        Objects.equals(vacante.getVersionPesosId(), v.getId())))
                .orElse(null);
    }

    private FichaResponse comoRespuesta(ContextoUsuario quien, Vacante vacante, FichaVacante f) {
        return new FichaResponse(f.getId(), f.getVacanteId(),
                f.getQ1Resultado(), f.getQ2Riesgo(), f.getQ3DiaReal(), f.getQ4EpocaDorada(),
                f.getQ5Estructura(), f.getQ6Autonomia(), f.getQ7JefeDirecto(),
                f.getQ8LoIncomodo(), f.getQ9Requerimientos(), f.getQ10Espejo(),
                f.getGenteEnEmpresa(), f.getGenteACargo(),
                f.getRiesgo1(), f.getRiesgo2(), f.getRiesgo3(), f.getRiesgo4(),
                f.getEliminatoria1(), f.getEliminatoria2(),
                f.getRequerimiento1(), f.getRequerimiento2(), f.getRequerimiento3(),
                f.getFamilias(), f.getTamano(), f.getEstado(), f.getActualizadoEn(),
                sugerenciaDePesos(quien, vacante, f.getTamano()));
    }

    private Vacante laDeLaOrganizacion(ContextoUsuario quien, Long id) {
        return vacantes.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", id));
    }
}
