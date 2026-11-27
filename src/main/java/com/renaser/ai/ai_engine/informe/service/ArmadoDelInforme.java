package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.informe.service.LoQueSigueAbierto.Situacion;
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
 * <p><b>Antes que los textos, el seguimiento de pendientes</b> (V94), en la evaluación y en la
 * prueba: si vale con estos datos, sus estados van en los bloques; si falta y hay dudas, se pide,
 * y mientras viene en camino no se pide al redactor —escribirá una sola vez, ya con los estados—.
 * Así se rellena también a quien se calificó antes del seguimiento, la primera vez que alguien
 * abre su informe (D-2), y nunca en lote.
 *
 * <p><b>No recibe a nadie conectado.</b> El permiso y el alcance los comprueba antes
 * {@code ServicioInformesImpl}; la cola, al redactar, no tiene a nadie a quien preguntar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArmadoDelInforme implements SeguimientoTrasCalificar {

    private final PostulacionRepository postulaciones;
    private final LoComunDelInforme comun;
    private final ArmadoDelCv cv;
    private final ArmadoDeLaEvaluacion evaluacion;
    private final ArmadoDeLaPrueba prueba;
    private final TextosDelInforme textos;
    private final LoQueSigueAbierto abierto;

    /** El informe y el archivo del CV, para que quien tenga permiso le añada su enlace firmado. */
    public record Armado(Informe informe, Long archivoCvId) {
    }

    @Transactional(readOnly = true)
    public EstadoDeUnInforme estado(Postulacion postulacion, EtapaDelInforme etapa) {
        return estado(comun.leer(postulacion), etapa);
    }

    /**
     * El informe de esa etapa, o vacío si todavía no hay nada que enseñar (nunca uno vacío ni
     * con ceros). Si {@code pedirTextos}, pide el seguimiento y los textos que falten.
     */
    @Transactional(readOnly = true)
    public Optional<Armado> armar(Postulacion postulacion, EtapaDelInforme etapa, boolean pedirTextos) {
        Base base = comun.leer(postulacion);
        if (!estado(base, etapa).disponible()) {
            return Optional.empty();
        }
        Situacion situacion = situacion(base, etapa);
        Informe sinTextos = conElSeguimiento(base(base, etapa, Instant.now()), situacion);
        boolean enCamino = situacion != null && !situacion.alDia()
                && seguimientoEnCamino(postulacion.getId(), situacion, pedirTextos);
        Insumo insumo = RedaccionDelInforme.insumo(sinTextos);
        String huella = RedaccionDelInforme.huella(insumo);
        Optional<Textos> escritos = textos.vigentes(postulacion.getId(), etapa, huella);
        Informe informe = escritos.map(t -> RedaccionDelInforme.aplicar(sinTextos, t)).orElse(sinTextos);
        // Con el seguimiento en camino no se pide al redactor: lo pide el seguimiento al llegar.
        if (!enCamino && escritos.isEmpty() && RedaccionDelInforme.hayQueRedactar(insumo)) {
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
     * Para el redactor: el informe de la etapa sin textos —con el seguimiento de pendientes que
     * valga—, o vacío si no hay nada que redactar. Lo llama la cola, sin nadie conectado.
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
        return Optional.of(conElSeguimiento(base(base, etapa, Instant.now()), situacion(base, etapa)));
    }

    /**
     * Para el agente del seguimiento: lo que sigue abierto en la etapa con los datos de hoy, o vacío
     * si no hay nada que seguir todavía (sin dudas, sin informe, o esperando al de la evaluación).
     */
    @Transactional(readOnly = true)
    public Optional<Situacion> paraSeguir(Long postulacionId, EtapaDelInforme etapa) {
        return postulaciones.findById(postulacionId)
                .flatMap(postulacion -> abierto.de(comun.leer(postulacion), etapa))
                .filter(s -> s.antes() == null);
    }

    // ==================================================== lo que pregunta la cola (V94)

    @Override
    @Transactional(readOnly = true)
    public boolean pedirElSeguimiento(Long postulacionId, String etapa) {
        EtapaDelInforme laEtapa = EtapaDelInforme.de(etapa);
        return postulaciones.findById(postulacionId)
                .flatMap(postulacion -> abierto.de(comun.leer(postulacion), laEtapa))
                .filter(s -> !s.alDia())
                .map(s -> abierto.pedir(postulacionId, s))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public void trasElSeguimiento(Long postulacionId, String etapa) {
        EtapaDelInforme laEtapa = EtapaDelInforme.de(etapa);
        postulaciones.findById(postulacionId).ifPresent(postulacion -> {
            // Es lo mismo que abrir el informe: con el seguimiento al día, pide al redactor; si
            // los datos cambiaron mientras tanto, pide el seguimiento nuevo.
            armar(postulacion, laEtapa, true);
            if (laEtapa == EtapaDelInforme.EVALUACION) {
                // En cadena: lo que quedó abierto tras la evaluación es lo que lee la prueba. Sin la
                // prueba calificada no hay informe de la prueba y no se pide nada.
                armar(postulacion, EtapaDelInforme.PRUEBA, true);
            }
        });
    }

    // ==================================================== el seguimiento en el informe

    /**
     * La situación del seguimiento, o nula si no hay nada que seguir o algo falla al leerla: el
     * informe sale igual.
     */
    private Situacion situacion(Base base, EtapaDelInforme etapa) {
        try {
            return abierto.de(base, etapa).orElse(null);
        } catch (RuntimeException e) {
            log.warn("No se pudo leer el seguimiento de pendientes del informe {} de la postulación {}: sale "
                    + "como siempre. Motivo: {}", etapa, base.postulacion().getId(), e.toString());
            return null;
        }
    }

    /** El informe con los estados del seguimiento, si vale con estos datos; si no, tal cual. */
    private static Informe conElSeguimiento(Informe informe, Situacion situacion) {
        return situacion == null || !situacion.alDia() ? informe
                : PendientesEntreEtapas.aplicar(informe, situacion.resultado());
    }

    private boolean seguimientoEnCamino(Long postulacionId, Situacion situacion, boolean pedir) {
        try {
            return pedir ? abierto.pedir(postulacionId, situacion) : abierto.enCamino(postulacionId, situacion);
        } catch (RuntimeException e) {
            // Pedir el seguimiento nunca tumba un informe: sale con «Por contrastar».
            log.warn("No se pudo pedir el seguimiento de pendientes {} de la postulación {}: {}",
                    situacion.etapa(), postulacionId, e.toString());
            return false;
        }
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
