package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * El resolutor: «para esta organización y este instrumento, ¿de quién son las filas?».
 *
 * <p>Es el único punto donde una bandera de personalización se interpreta. Bandera
 * apagada → las filas de la plataforma (la empresa lee el método de Renaser tal cual, y
 * una mejora de Renaser le llega sola); encendida → las suyas. Toda consulta de
 * instrumentos pasa por aquí; ningún repositorio decide por su cuenta, porque dos
 * lecturas de la misma bandera acabarían contestando distinto.
 *
 * <p>Resolver es un permiso de LECTURA deliberado, no una fuga: con la bandera apagada la
 * empresa ve el instrumento de la plataforma en solo lectura. Las guardas de mutación no
 * resuelven — editar exige que la fila sea de la propia organización.
 */
@Service
@RequiredArgsConstructor
public class DuenoDelInstrumento {

    private final OrganizacionRepository organizaciones;

    /** El id de la organización dueña de las filas del instrumento para esta organización. */
    public Long duenoDe(Long organizacionId, Instrumento instrumento) {
        Organizacion organizacion = organizaciones.findById(organizacionId)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe la organización " + organizacionId));
        if (organizacion.isEsPlataforma() || instrumento.esPropio(organizacion)) {
            return organizacion.getId();
        }
        return plataforma().getId();
    }

    /** La dueña de la plataforma. Existe siempre: la garantiza la migración V37. */
    public Organizacion plataforma() {
        return organizaciones.findByEsPlataformaTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "Ninguna organización está marcada como plataforma"));
    }
}
