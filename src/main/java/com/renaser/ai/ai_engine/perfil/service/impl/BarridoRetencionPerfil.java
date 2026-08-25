package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfil.service.ServicioCicloVidaPerfil;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * El paso del tiempo sobre los perfiles: lo que la ley 29733 llama plazo de conservación.
 *
 * <p>Un perfil sin actividad se conserva los meses que diga el parámetro
 * {@code meses_conservar_perfil} (arranca en 24) y después se borra con todo lo suyo.
 * «Actividad» es lo más reciente entre tocar el perfil y postular: quien sigue postulando
 * sigue presente aunque no edite su ficha.
 *
 * <p><b>El plazo que manda es el MÁXIMO entre organizaciones.</b> El perfil es de la
 * persona y transversal; el parámetro es por organización. Ante dos plazos distintos, gana
 * el más conservador: borrar antes de tiempo es irreversible y conservar de más se corrige
 * bajando el parámetro.
 *
 * <p>Corre una vez al día, de madrugada, con su propio cron: el sondeo de vencimientos va
 * cada minuto porque sus consultas son baratas, y un barrido de perfiles no lo es.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BarridoRetencionPerfil {

    static final String PARAMETRO = "meses_conservar_perfil";
    static final int MESES_POR_DEFECTO = 24;

    private final PerfilCandidatoRepository perfiles;
    private final PostulacionRepository postulaciones;
    private final OrganizacionRepository organizaciones;
    private final ServicioParametros parametros;
    private final ServicioCicloVidaPerfil cicloVida;

    @Scheduled(cron = "${app.perfil.retencion-cron:0 30 3 * * *}")
    public void ejecutar() {
        try {
            barrer();
        } catch (RuntimeException e) {
            // Manana vuelve a correr; un fallo hoy no puede dejar la excepcion sin dueno.
            log.error("El barrido de retención de perfiles falló: {}", e.getMessage(), e);
        }
    }

    /** Separado del cron para poder probarlo sin esperar a las tres y media. */
    public int barrer() {
        int meses = organizaciones.findAll().stream()
                .mapToInt(o -> parametros.entero(o.getId(), PARAMETRO, MESES_POR_DEFECTO))
                .max().orElse(MESES_POR_DEFECTO);
        Instant limite = Instant.now().minus(meses * 30L, ChronoUnit.DAYS);

        int borrados = 0;
        for (PerfilCandidato perfil : perfiles.findAll()) {
            if (ultimaActividadDe(perfil).isBefore(limite)) {
                cicloVida.borrarPorPersona(perfil.getPersonaId());
                borrados++;
            }
        }
        if (borrados > 0) {
            log.info("Retención de perfiles: {} borrados por llevar más de {} meses sin "
                    + "actividad", borrados, meses);
        }
        return borrados;
    }

    private Instant ultimaActividadDe(PerfilCandidato perfil) {
        Instant delPerfil = perfil.getActualizadoEn() != null
                ? perfil.getActualizadoEn()
                : perfil.getCreadoEn();
        List<Postulacion> suyas = postulaciones.deLaPersona(perfil.getPersonaId());
        Instant dePostular = suyas.isEmpty() ? Instant.EPOCH : suyas.get(0).getCreadoEn();
        return delPerfil.isAfter(dePostular) ? delPerfil : dePostular;
    }
}
