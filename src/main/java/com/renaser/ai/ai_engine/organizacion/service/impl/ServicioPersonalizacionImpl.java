package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.Personalizacion;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.CopiadorDeInstrumentos;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.organizacion.service.ServicioPersonalizacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Ver {@link ServicioPersonalizacion}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioPersonalizacionImpl implements ServicioPersonalizacion {

    private final OrganizacionRepository organizaciones;
    private final CopiadorDeInstrumentos copiador;
    private final ServicioAuditoria auditoria;

    @Override
    public Personalizacion ver(ContextoUsuario quien) {
        Organizacion organizacion = laDe(quien);
        return new Personalizacion(organizacion.isPesosPropios(),
                organizacion.isPlantillasEvaluacionPropias());
    }

    /*
     * Personalizar el banco o las pruebas ya no existe (V67, decisión 13), y desde la V74
     * tampoco son un Instrumento: el texto «BANCO» o «PRUEBA» de la API contesta 400 con
     * Instrumento.YA_NO_EXISTE antes de llegar aquí, y no copia ni cambia nada.
     */

    @Override
    @Transactional
    public void encender(ContextoUsuario quien, Instrumento instrumento) {
        encender(quien, laDe(quien), instrumento, null);
    }

    @Override
    @Transactional
    public void encenderPara(ContextoUsuario quien, Long organizacionId, Instrumento instrumento,
                             String motivo) {
        Organizacion objetivo = laObjetivo(quien, organizacionId);
        encender(quien, objetivo, instrumento, motivo);
    }

    @Override
    @Transactional
    public void apagarPara(ContextoUsuario quien, Long organizacionId, Instrumento instrumento,
                           String motivo) {
        Organizacion objetivo = laObjetivo(quien, organizacionId);
        apagar(quien, objetivo, instrumento, motivo);
    }

    private void encender(ContextoUsuario quien, Organizacion organizacion,
                          Instrumento instrumento, String motivo) {
        if (organizacion.isEsPlataforma()) {
            throw new IllegalStateException(
                    "La plataforma ya es dueña de su método: no tiene nada que personalizar");
        }
        if (instrumento.esPropio(organizacion)) {
            throw new IllegalStateException(
                    "La personalización de " + instrumento + " ya está encendida");
        }
        // Copiar y encender van en la misma transacción: una bandera encendida sin copia
        // dejaría a la empresa sin instrumento ninguno, que es peor que cualquiera de los
        // dos estados estables.
        Map<String, Integer> copiado = switch (instrumento) {
            case PESOS -> copiador.copiarPesos(organizacion.getId());
            case PLANTILLA_EVALUACION -> copiador.copiarPlantillasEvaluacion(organizacion.getId());
        };
        instrumento.poner(organizacion, true);
        organizaciones.save(organizacion);

        auditoria.registrar(quien.organizacionId(), quien, "encender_personalizacion",
                "organizacion", organizacion.getId(), null,
                Map.of("instrumento", instrumento.name(), "copiado", copiado.toString()), motivo);
    }

    @Override
    @Transactional
    public void apagar(ContextoUsuario quien, Instrumento instrumento) {
        apagar(quien, laDe(quien), instrumento, null);
    }

    private void apagar(ContextoUsuario quien, Organizacion organizacion,
                        Instrumento instrumento, String motivo) {
        if (!instrumento.esPropio(organizacion)) {
            throw new IllegalStateException(
                    "La personalización de " + instrumento + " ya está apagada");
        }

        instrumento.poner(organizacion, false);
        organizaciones.save(organizacion);

        auditoria.registrar(quien.organizacionId(), quien, "apagar_personalizacion",
                "organizacion", organizacion.getId(), null,
                Map.of("instrumento", instrumento.name()), motivo);
        log.info("Personalización de {} apagada en la organización {}", instrumento,
                organizacion.getId());
    }

    private Organizacion laDe(ContextoUsuario quien) {
        return organizaciones.findById(quien.organizacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "No existe la organización " + quien.organizacionId()));
    }

    /**
     * La organización sobre la que actúa la plataforma (pieza F). Doble llave como el
     * resto del panel de plataforma: el permiso lo mira el controlador, y aquí se exige
     * además SER la plataforma — y que el objetivo exista, con 404 para lo que no.
     */
    private Organizacion laObjetivo(ContextoUsuario quien, Long organizacionId) {
        Organizacion plataforma = organizaciones.findByEsPlataformaTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "Ninguna organización está marcada como plataforma"));
        if (!plataforma.getId().equals(quien.organizacionId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Solo la plataforma enciende personalizaciones de otra organización");
        }
        return organizaciones.findById(organizacionId)
                .orElseThrow(() -> new com.renaser.ai.ai_engine.ai.exception
                        .ResourceNotFoundException("Empresa", "id", organizacionId));
    }
}
