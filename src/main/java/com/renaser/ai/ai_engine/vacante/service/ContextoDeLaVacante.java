package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Para qué se está calificando: la vacante de una postulación y el puesto de esa vacante.
 *
 * <p><b>Por qué existe.</b> Ningún agente puede juzgar nada sin saber contra qué. Un mismo
 * entregable es excelente para una vacante junior y flojo para una de dirección, y esa
 * diferencia no está en el entregable: está en el puesto. Cada agente necesita el mismo par
 * de datos —cómo se llama el puesto, qué nivel es, qué busca la convocatoria— y hasta ahora
 * cada puente lo resolvía por su cuenta con las mismas dos consultas y el mismo mensaje de
 * error escrito de tres formas distintas.
 *
 * <p>Lo que devuelve {@link #queBuscaLaVacanteDe} es el texto de la convocatoria pegado: el
 * título, el propósito, las responsabilidades y los requisitos. Se manda entero y sin
 * resumir a propósito. Resumirlo aquí sería decidir qué parte de la vacante importa, y esa
 * decisión es justo la que se le está pidiendo al modelo.
 */
@Service
@RequiredArgsConstructor
public class ContextoDeLaVacante {

    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;

    public Vacante vacanteDe(Postulacion postulacion) {
        return vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException(
                        "La vacante de la postulación " + postulacion.getId() + " ya no existe"));
    }

    public Puesto puestoDe(Postulacion postulacion) {
        Vacante vacante = vacanteDe(postulacion);
        return puestos.findById(vacante.getPuestoId())
                .orElseThrow(() -> new IllegalStateException(
                        "La vacante " + vacante.getId() + " apunta a un puesto que no existe"));
    }

    /** El texto de la convocatoria, pegado en un solo bloque. */
    public String queBuscaLaVacanteDe(Postulacion postulacion) {
        Vacante vacante = vacanteDe(postulacion);
        return String.join("\n", List.of(
                        texto(vacante.getTitulo()), texto(vacante.getProposito()),
                        texto(vacante.getResponsabilidades()), texto(vacante.getRequisitos())))
                .trim();
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
