package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * La fecha en que la prueba de una vacante se cierra para todos ({@code vacante.prueba_cierra_en})
 * y lo que pasa al cambiarla: se mueve a los que ya están dentro.
 *
 * <p>La usan los dos sitios desde los que se fija: el bloque «Plazos de la prueba» de una
 * vacante con plantilla ({@code POST /vacantes/{id}/cierre-prueba}) y la configuración del
 * editor de la prueba (V68, {@code PUT …/prueba-propia/fecha-limite}). Con una copia en cada
 * uno, la mitad de la tanda se movería de una forma y la otra mitad de otra.
 *
 * <p>Quien llama ya validó la vacante, su empresa, la fecha y el motivo: aquí solo se escribe.
 */
@Service
@RequiredArgsConstructor
public class FechaLimiteDeLaVacante {

    private final VacanteRepository vacantes;
    private final IntentoPruebaRepository intentos;
    private final VersionPlantillaPruebaRepository versionesPrueba;
    private final VersionBancoRepository versionesBanco;

    /** Cuántos intentos abiertos se movieron, y cuántos se dejaron por tener plazo propio. */
    public record Movidos(int movidos, int conPlazoPropio) {
    }

    /**
     * Pone la fecha en la vacante y la mueve a los intentos abiertos, salvo a los de quien
     * tiene plazo propio (lo concedido a mano a una persona manda sobre la convocatoria).
     * En la prueba del editor, a quien ya empezó una cronometrada no le alarga el reloj.
     */
    public Movidos fijar(Vacante vacante, Instant cierraEn) {
        vacante.setPruebaCierraEn(cierraEn);
        vacantes.save(vacante);

        // Y se mueve a los que ya están dentro. Sin esto, la fecha valdría solo para quien
        // entrara después: la mitad de la tanda cerraría el domingo y la otra mitad a los
        // siete días de su propio lunes, sin nada que lo explicara.
        int movidos = 0;
        int conPlazoPropio = 0;
        for (IntentoPrueba intento : intentos.abiertosDeLaVacante(vacante.getId())) {
            if (intento.isPlazoPropio()) {
                conPlazoPropio++;
                continue;
            }
            intento.setVenceEn(fechaDeCierreDe(intento, cierraEn));
            intentos.save(intento);
            movidos++;
        }
        return new Movidos(movidos, conPlazoPropio);
    }

    /**
     * Qué fecha de cierre le toca a este intento cuando cambia la de la vacante.
     *
     * <p>A quien todavía no ha empezado se le pone la nueva tal cual (o se le deja vacía): su
     * reloj se calcula al empezar, como siempre.
     *
     * <p>El caso que obligó a que esto exista es <b>quitar</b> la fecha. A quien ya está
     * dentro, empezar no vuelve a pasarle: dejársela vacía lo dejaría <b>sin vencimiento para
     * siempre</b> —podría entregar cuando quisiera y el barrido de vencidos jamás lo cerraría,
     * porque una comparación contra nulo nunca casa—. A ese se le devuelve el plazo de su
     * plantilla, contado desde que empezó.
     */
    private Instant fechaDeCierreDe(IntentoPrueba intento, Instant cierraEn) {
        if (intento.getIniciadoEn() == null) {
            return cierraEn;
        }
        // La prueba del editor (V67) no tiene plantilla: su reloj está en su versión.
        if (intento.esDelEditor()) {
            return delEditor(intento, cierraEn);
        }
        if (cierraEn != null) {
            return cierraEn;
        }
        return versionesPrueba.findById(intento.getVersionPlantillaPruebaId())
                .map(v -> "CRONOMETRADA".equals(v.getModalidad())
                        ? intento.getIniciadoEn().plus(v.getDuracionMinutos(), ChronoUnit.MINUTES)
                        : intento.getIniciadoEn().plus(v.getPlazoDias(), ChronoUnit.DAYS))
                // Si su versión ya no existe, se le deja la que tenía: quitarle el
                // vencimiento sería peor que dejarle uno viejo.
                .orElse(intento.getVenceEn());
    }

    /**
     * El intento ya empezado de una prueba del editor: la misma regla que al abrirla
     * ({@code ServicioPruebaImpl.iniciarDelEditor}).
     *
     * <ul>
     *   <li><b>Cronometrada</b>: vence a los N minutos de haber empezado o en la nueva fecha,
     *       lo que llegue antes. Mover la fecha nunca le alarga el reloj: sin esto, quien
     *       empezó a las 16:35 con 90 minutos pasaba de vencer a las 18:05 a vencer el 30/10.
     *       Si el reloj ya se le acabó, sigue acabado: el barrido la cierra.</li>
     *   <li><b>Sin cronómetro</b>: pasa a la nueva fecha. Sin fecha, los días de una versión
     *       anterior a la V68, si los tiene; si no, la que tenía.</li>
     * </ul>
     */
    private Instant delEditor(IntentoPrueba intento, Instant cierraEn) {
        VersionBanco version = versionesBanco.findById(intento.getVersionBancoId()).orElse(null);
        if (version == null) {
            // Sin su versión no hay reloj que respetar: la nueva fecha, o la que tenía.
            return cierraEn != null ? cierraEn : intento.getVenceEn();
        }
        if ("CRONOMETRADA".equals(version.getModalidad()) && version.getDuracionMinutos() != null) {
            Instant porElReloj = intento.getIniciadoEn().plus(version.getDuracionMinutos(), ChronoUnit.MINUTES);
            return cierraEn != null && cierraEn.isBefore(porElReloj) ? cierraEn : porElReloj;
        }
        if (cierraEn != null) {
            return cierraEn;
        }
        return version.getPlazoDias() != null
                ? intento.getIniciadoEn().plus(version.getPlazoDias(), ChronoUnit.DAYS)
                : intento.getVenceEn();
    }
}
