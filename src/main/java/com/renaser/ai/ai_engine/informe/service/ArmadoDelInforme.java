package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.Insumo;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.Textos;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Un solo armado del informe, dos salidas: la página del visor y el PDF leen lo que devuelve.
 *
 * <p>Arma la etapa con lo que ya está guardado, le pone los textos del redactor si se
 * escribieron con estos mismos datos y, si faltan, se los pide —sin esperarlos: mientras tanto
 * van las explicaciones completas de la IA—.
 *
 * <p><b>No recibe a nadie conectado.</b> El permiso y el alcance los comprueba antes
 * {@code ServicioInformesImpl}; la cola, al redactar, no tiene a nadie a quien preguntar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArmadoDelInforme {

    private final PostulacionRepository postulaciones;
    private final LoComunDelInforme comun;
    private final ArmadoDelCv cv;
    private final ArmadoDeLaEvaluacion evaluacion;
    private final ArmadoDeLaPrueba prueba;
    private final TextosDelInforme textos;

    /** El informe y el archivo del CV, para que quien tenga permiso le añada su enlace firmado. */
    public record Armado(Informe informe, Long archivoCvId) {
    }

    @Transactional(readOnly = true)
    public EstadoDeUnInforme estado(Postulacion postulacion, EtapaDelInforme etapa) {
        return estado(comun.leer(postulacion), etapa);
    }

    /**
     * El informe de esa etapa, o vacío si todavía no hay nada que enseñar (nunca uno vacío ni
     * con ceros). Si {@code pedirTextos}, pide al redactor los que falten.
     */
    @Transactional(readOnly = true)
    public Optional<Armado> armar(Postulacion postulacion, EtapaDelInforme etapa, boolean pedirTextos) {
        Base base = comun.leer(postulacion);
        if (!estado(base, etapa).disponible()) {
            return Optional.empty();
        }
        Informe sinTextos = base(base, etapa, Instant.now());
        Insumo insumo = RedaccionDelInforme.insumo(sinTextos);
        String huella = RedaccionDelInforme.huella(insumo);
        Optional<Textos> escritos = textos.vigentes(postulacion.getId(), etapa, huella);
        Informe informe = escritos.map(t -> RedaccionDelInforme.aplicar(sinTextos, t)).orElse(sinTextos);
        boolean enCamino = false;
        if (escritos.isEmpty() && RedaccionDelInforme.hayQueRedactar(insumo)) {
            try {
                enCamino = pedirTextos ? textos.pedir(postulacion.getId(), etapa, huella)
                        : textos.enCamino(postulacion.getId(), etapa);
            } catch (RuntimeException e) {
                // Pedir los textos nunca tumba un informe: sale con las explicaciones.
                log.warn("No se pudieron pedir los textos del informe {} de la postulación {}: {}",
                        etapa, postulacion.getId(), e.toString());
            }
        }
        Informe conEnlaces = new Informe(informe.etapa(), informe.titulo(), informe.empresa(),
                informe.vacante(), informe.postulacionId(), informe.candidato(), informe.correo(),
                informe.generadoEn(), informe.nota(), informe.grupo(), informe.datosClave(),
                informe.recuentos(), informe.experiencia(), informe.resumen(), informe.evaluado(),
                informe.bloques(), comun.redes(base), informe.detalleTitulo(), informe.detalle(),
                informe.detallePie(), informe.anexo(), informe.aviso(), enCamino, informe.nombreArchivo());
        return Optional.of(new Armado(conEnlaces,
                base.cv() == null ? null : base.cv().getArchivoOriginalId()));
    }

    /**
     * Para el redactor: el informe de la etapa sin textos, o vacío si no hay nada que redactar.
     * Lo llama la cola, sin nadie conectado.
     */
    @Transactional(readOnly = true)
    public Optional<Informe> paraRedactar(Long postulacionId, EtapaDelInforme etapa) {
        Postulacion postulacion = postulaciones.findById(postulacionId).orElse(null);
        if (postulacion == null) {
            return Optional.empty();
        }
        Base base = comun.leer(postulacion);
        if (!estado(base, etapa).disponible()) {
            return Optional.empty();
        }
        return Optional.of(base(base, etapa, Instant.now()));
    }

    private EstadoDeUnInforme estado(Base base, EtapaDelInforme etapa) {
        return switch (etapa) {
            case CV -> cv.estado(base);
            case EVALUACION -> evaluacion.estado(base);
            case PRUEBA -> prueba.estado(base);
        };
    }

    private Informe base(Base base, EtapaDelInforme etapa, Instant ahora) {
        return switch (etapa) {
            case CV -> cv.armar(base, ahora);
            case EVALUACION -> evaluacion.armar(base, ahora);
            case PRUEBA -> prueba.armar(base, ahora);
        };
    }
}
