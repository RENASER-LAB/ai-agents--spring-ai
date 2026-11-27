package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Duda;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Resultado;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Seguir;
import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.limpio;

/**
 * Qué sigue abierto en una etapa, con los datos de hoy, y cómo va su seguimiento de pendientes
 * (V94). Lo leen el armado del informe, la cola al terminar una etapa y el propio agente.
 *
 * <p><b>En la evaluación</b> se siguen las dudas del CV contra sus abiertas. <b>En la prueba</b>,
 * solo lo que siguiera abierto tras la evaluación —lo «sigue pendiente» y lo que «empeoró»— más
 * lo que la evaluación dejó por verificar (D-1). Sin evaluación, o si su seguimiento falló, la
 * prueba recibe todas las dudas del CV. Si el de la evaluación viene en camino o falta pedirlo, la
 * prueba lo espera: pedirla antes pagaría dos veces lo mismo. Si el de la evaluación lo resolvió
 * todo y no dejó nada por verificar, la prueba no tiene nada que seguir pero sí un seguimiento que
 * vale: sin llamar a nadie, ninguna duda del CV vuelve a su informe.
 *
 * <p><b>Nada antes del reloj</b>: el de la prueba solo existe cuando su informe está disponible, es
 * decir, con la prueba entregada o cerrada como no completada y calificada.
 */
@Component
@RequiredArgsConstructor
public class LoQueSigueAbierto {

    private final ArmadoDeLaEvaluacion evaluacion;
    private final ArmadoDeLaPrueba prueba;
    private final SeguimientosDelInforme seguimientos;

    /**
     * Cómo está el seguimiento de una etapa.
     *
     * @param seguir    lo que se le mandaría al agente (de un entregable, solo con qué archivo llegó)
     * @param resultado el seguimiento que vale con estos datos —el guardado, o el que sale sin
     *                  llamar porque no había nada que leer—; nulo si falta
     * @param antes     en la prueba, el seguimiento de la evaluación que tiene que llegar primero;
     *                  nulo si no hay que esperarlo
     */
    public record Situacion(EtapaDelInforme etapa, Seguir seguir, String huella, Resultado resultado,
                            Situacion antes) {

        /** Si ya hay un seguimiento que vale con estos datos. */
        public boolean alDia() {
            return resultado != null;
        }
    }

    /**
     * La situación de la etapa, o vacío si no hay nada que seguir ni seguimiento que valga (sin
     * dudas, o sin informe). En la prueba, «nada abierto» tras un seguimiento de la evaluación al
     * día no es vacío: es una situación al día sin dudas.
     */
    public Optional<Situacion> de(Base base, EtapaDelInforme etapa) {
        return switch (etapa) {
            case CV -> Optional.empty();
            case EVALUACION -> deLaEvaluacion(base);
            case PRUEBA -> deLaPrueba(base);
        };
    }

    /**
     * Pide lo que le falta a la situación —el de la evaluación primero, si la prueba lo espera— y
     * devuelve si viene en camino.
     */
    public boolean pedir(Long postulacionId, Situacion situacion) {
        if (situacion.antes() != null) {
            return seguimientos.enCamino(postulacionId, EtapaDelInforme.EVALUACION)
                    || seguimientos.pedir(postulacionId, EtapaDelInforme.EVALUACION, situacion.antes().huella());
        }
        return seguimientos.pedir(postulacionId, situacion.etapa(), situacion.huella());
    }

    /** Si lo que le falta a la situación viene en camino, sin pedir nada. */
    public boolean enCamino(Long postulacionId, Situacion situacion) {
        return seguimientos.enCamino(postulacionId,
                situacion.antes() != null ? EtapaDelInforme.EVALUACION : situacion.etapa());
    }

    private Optional<Situacion> deLaEvaluacion(Base base) {
        List<Duda> dudas = dudasDelCv(base);
        if (dudas.isEmpty() || !evaluacion.estado(base).disponible()) {
            return Optional.empty();
        }
        Seguir seguir = new Seguir(EtapaDelInforme.EVALUACION.name(), base.vacanteTitulo(), dudas,
                evaluacion.abiertasConTexto(base));
        return Optional.of(situacion(base, EtapaDelInforme.EVALUACION, seguir));
    }

    private Optional<Situacion> deLaPrueba(Base base) {
        if (!prueba.estado(base).disponible()) {
            return Optional.empty();
        }
        Long postulacionId = base.postulacion().getId();
        Situacion laEvaluacion = deLaEvaluacion(base).orElse(null);
        List<Duda> delCv = dudasDelCv(base);
        Situacion antes = null;
        boolean evaluacionAlDia = laEvaluacion != null && laEvaluacion.alDia();
        if (evaluacionAlDia) {
            delCv = laEvaluacion.resultado().dudas().stream().filter(d -> !d.resuelta()).toList();
        } else if (laEvaluacion != null
                && (seguimientos.enCamino(postulacionId, EtapaDelInforme.EVALUACION)
                || !seguimientos.yaPedido(postulacionId, EtapaDelInforme.EVALUACION, laEvaluacion.huella()))) {
            antes = laEvaluacion;
        }
        List<Duda> dudas = new ArrayList<>();
        for (Duda d : delCv) {
            dudas.add(d.conCodigo("D" + (dudas.size() + 1)));
        }
        for (Punto p : prueba.deLaEvaluacion(base)) {
            dudas.add(Duda.abierta("D" + (dudas.size() + 1), PendientesEntreEtapas.DE_LA_EVALUACION, p.texto(),
                    p.etiqueta(), p.detalle()));
        }
        if (dudas.isEmpty()) {
            // Nada abierto no es «sin seguimiento»: si el de la evaluación al día lo resolvió todo,
            // vale ya —lo resuelto no se repite en la prueba— sin llamar ni pedir nada. Sin él, el
            // informe de la prueba sale como hoy.
            return evaluacionAlDia ? Optional.of(nadaAbierto(base)) : Optional.empty();
        }
        Seguir seguir = new Seguir(EtapaDelInforme.PRUEBA.name(), base.vacanteTitulo(), dudas,
                prueba.paraElSeguimiento(base));
        if (antes != null) {
            // Mientras llega el de la evaluación no se enseña ninguno de la prueba: podría ser de
            // antes de que cambiara lo que quedó abierto.
            return Optional.of(new Situacion(EtapaDelInforme.PRUEBA, seguir, PendientesEntreEtapas.huella(seguir),
                    null, antes));
        }
        return Optional.of(situacion(base, EtapaDelInforme.PRUEBA, seguir));
    }

    /** La prueba sin nada que seguir tras la evaluación: al día, sin dudas y sin nada que leer. */
    private static Situacion nadaAbierto(Base base) {
        Seguir nada = new Seguir(EtapaDelInforme.PRUEBA.name(), base.vacanteTitulo(), List.of(), List.of());
        return new Situacion(EtapaDelInforme.PRUEBA, nada, PendientesEntreEtapas.huella(nada),
                PendientesEntreEtapas.sinLlamar(nada), null);
    }

    private Situacion situacion(Base base, EtapaDelInforme etapa, Seguir seguir) {
        String huella = PendientesEntreEtapas.huella(seguir);
        Resultado resultado = PendientesEntreEtapas.hayQueLeer(seguir)
                ? seguimientos.vigente(base.postulacion().getId(), etapa, huella).orElse(null)
                : PendientesEntreEtapas.sinLlamar(seguir);
        return new Situacion(etapa, seguir, huella, resultado, null);
    }

    /** Las mismas dudas que enseña el informe, numeradas D1, D2… */
    static List<Duda> dudasDelCv(Base base) {
        List<Duda> dudas = new ArrayList<>();
        for (AfirmacionCv a : LoComunDelInforme.dudosas(base)) {
            dudas.add(Duda.abierta("D" + (dudas.size() + 1), PendientesEntreEtapas.DEL_CV, a.getTexto().strip(),
                    a.getClasificacion(), limpio(a.getPreguntaValidacion())));
        }
        return dudas;
    }
}
