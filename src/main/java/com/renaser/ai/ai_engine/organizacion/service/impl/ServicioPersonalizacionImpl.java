package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.Personalizacion;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.CopiadorDeInstrumentos;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.organizacion.service.ServicioPersonalizacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
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
    private final VersionBancoRepository versionesBanco;
    private final ServicioAuditoria auditoria;

    @Override
    public Personalizacion ver(ContextoUsuario quien) {
        Organizacion organizacion = laDe(quien);
        return new Personalizacion(organizacion.isBancoPropio(), organizacion.isPesosPropios(),
                organizacion.isPlantillasEvaluacionPropias(), organizacion.isPruebasPuestoPropias());
    }

    @Override
    @Transactional
    public void encender(ContextoUsuario quien, Instrumento instrumento) {
        Organizacion organizacion = laDe(quien);
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
            case BANCO -> copiador.copiarBanco(organizacion.getId());
            case PESOS -> copiador.copiarPesos(organizacion.getId());
            case PLANTILLA_EVALUACION -> copiador.copiarPlantillasEvaluacion(organizacion.getId());
            case PRUEBA -> copiador.copiarPruebas(organizacion.getId());
        };
        instrumento.poner(organizacion, true);
        organizaciones.save(organizacion);

        auditoria.registrar(quien.organizacionId(), quien, "encender_personalizacion",
                "organizacion", organizacion.getId(), null,
                Map.of("instrumento", instrumento.name(), "copiado", copiado.toString()), null);
    }

    @Override
    @Transactional
    public void apagar(ContextoUsuario quien, Instrumento instrumento) {
        Organizacion organizacion = laDe(quien);
        if (!instrumento.esPropio(organizacion)) {
            throw new IllegalStateException(
                    "La personalización de " + instrumento + " ya está apagada");
        }

        // El banco propio publicado pasa a ARCHIVADA: el selector del candidato elige la
        // PUBLICADA más reciente del dueño, y aunque el resolutor ya apunte a la
        // plataforma, dejar la copia como PUBLICADA haría mentir al estado — y el estado
        // es lo único que el panel ve. Nada se borra (RF-138): quien ya rindió con la
        // copia conserva sus preguntas y sus claves. Los otros instrumentos no tienen
        // estado ARCHIVADA en su esquema; basta con que el resolutor deje de mirarlos.
        int archivadas = 0;
        if (instrumento == Instrumento.BANCO) {
            for (VersionBanco version : versionesBanco
                    .findByOrganizacionIdAndEstado(organizacion.getId(), "PUBLICADA")) {
                version.setEstado("ARCHIVADA");
                versionesBanco.save(version);
                archivadas++;
            }
        }
        instrumento.poner(organizacion, false);
        organizaciones.save(organizacion);

        auditoria.registrar(quien.organizacionId(), quien, "apagar_personalizacion",
                "organizacion", organizacion.getId(), null,
                Map.of("instrumento", instrumento.name(), "versionesArchivadas", archivadas), null);
        log.info("Personalización de {} apagada en la organización {} · {} versiones archivadas",
                instrumento, organizacion.getId(), archivadas);
    }

    private Organizacion laDe(ContextoUsuario quien) {
        return organizaciones.findById(quien.organizacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "No existe la organización " + quien.organizacionId()));
    }
}
