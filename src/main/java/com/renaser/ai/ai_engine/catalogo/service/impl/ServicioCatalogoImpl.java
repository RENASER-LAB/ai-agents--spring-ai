package com.renaser.ai.ai_engine.catalogo.service.impl;

import com.renaser.ai.ai_engine.catalogo.dto.DtosCatalogo.Catalogos;
import com.renaser.ai.ai_engine.catalogo.dto.DtosCatalogo.EstadoCatalogo;
import com.renaser.ai.ai_engine.catalogo.dto.DtosCatalogo.Opcion;
import com.renaser.ai.ai_engine.catalogo.service.ServicioCatalogo;
import com.renaser.ai.ai_engine.pesos.repository.EtapaRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.vacante.repository.FamiliaRepository;
import com.renaser.ai.ai_engine.vacante.repository.NivelPuestoRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Ver {@link ServicioCatalogo}. */
@Service
@RequiredArgsConstructor
public class ServicioCatalogoImpl implements ServicioCatalogo {

    private final NivelPuestoRepository niveles;
    private final FamiliaRepository familias;
    private final EtapaRepository etapas;
    private final EstadoPostulacionRepository estados;

    /* Estos tres no están en tabla: viven en restricciones CHECK de la base. Se declaran
       aquí, que es el sitio más cercano a la verdad al que puede llegar el frontend.
       ⚠️ Si cambia un CHECK, hay que cambiar esta lista. */
    private static final List<Opcion> URGENCIAS = List.of(
            new Opcion("NORMAL", "Normal"),
            new Opcion("PRIORITARIA", "Prioritaria"),
            new Opcion("URGENTE", "Urgente"));

    private static final List<Opcion> TIPOS_CIERRE = List.of(
            new Opcion("PERMANENTE", "Permanente"),
            new Opcion("PLAZAS", "Por plazas"),
            new Opcion("FECHA", "Por fecha"));

    private static final List<Opcion> MOTIVOS_CIERRE = List.of(
            new Opcion("REQUISITO_OBJETIVO", "No cumple un requisito indispensable"),
            new Opcion("BARRERA_CRITICA", "Barrera crítica confirmada"),
            new Opcion("DECISION_ROJA", "Decisión en rojo"),
            new Opcion("DECISION_PERSONA", "Decisión de una persona"),
            new Opcion("PASA_A_RESERVA", "Pasa a reserva"),
            new Opcion("INACTIVIDAD", "Demasiado tiempo sin avanzar"),
            new Opcion("CIERRE_MANUAL", "Cierre manual"),
            new Opcion("RETIRO_CANDIDATO", "El candidato se retiró"),
            new Opcion("PLAZO_VENCIDO", "Se acabó el plazo"),
            new Opcion("BORRADO_DATOS", "Pidió el borrado de sus datos"));

    @Override
    @Transactional(readOnly = true)
    public Catalogos todos() {
        return new Catalogos(
                niveles.findAllByOrderByOrdenAsc().stream()
                        .map(n -> new Opcion(n.getCodigo(), n.getNombre())).toList(),
                familias.findAllByOrderByNombreAsc().stream()
                        .map(f -> new Opcion(f.getCodigo(), f.getNombre())).toList(),
                etapas.findAllByOrderByOrdenAsc().stream()
                        .map(e -> new Opcion(e.getCodigo(), e.getNombre())).toList(),
                URGENCIAS,
                TIPOS_CIERRE,
                MOTIVOS_CIERRE,
                estados.findAllByOrderByOrden().stream()
                        .map(e -> new EstadoCatalogo(e.getCodigo(), e.getNombre(), e.getEtapaCodigo(),
                                e.getMomentoCodigo(), e.getEsperaA(), e.getOrden(), e.isEsFinal()))
                        .toList());
    }
}
