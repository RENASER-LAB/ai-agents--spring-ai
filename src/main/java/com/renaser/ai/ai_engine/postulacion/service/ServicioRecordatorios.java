package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.notificacion.service.DireccionDelCandidato;
import com.renaser.ai.ai_engine.notificacion.service.FechaParaElCandidato;
import com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.postulacion.entity.RecordatorioEnviado;
import com.renaser.ai.ai_engine.postulacion.repository.RecordatorioEnviadoRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TurnosParaRecordar;
import com.renaser.ai.ai_engine.postulacion.repository.TurnosParaRecordar.Turno;
import com.renaser.ai.ai_engine.postulacion.service.CalendarioDeRecordatorios.Cual;
import com.renaser.ai.ai_engine.postulacion.service.CalendarioDeRecordatorios.Toca;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Los recordatorios del banco y de la prueba (V70).
 *
 * <p>Quien entrega el banco o abre la prueba al primer aviso no los recibe nunca. Son para el que
 * se quedó a medias: a las 24 horas de que le tocara, si no lo ha hecho, y 24 horas antes de que
 * venza. Por correo y en la campana, con el enlace para entrar.
 *
 * <p>Lo que decide CUÁNDO vive en {@link CalendarioDeRecordatorios}; a QUIÉN, en la consulta de
 * {@link TurnosParaRecordar}. Aquí se junta todo, se mira lo que ya salió y se manda.
 *
 * <p>⚠️ <b>Lo dispara {@code SondeoVencimientos} cada minuto, sin interruptor y sin persona
 * detrás</b>. Por eso:
 * <ul>
 *   <li>Cada recordatorio queda escrito en {@code recordatorio_enviado} <b>en la misma
 *       transacción que su correo</b>, y la base impide escribirlo dos veces: reiniciar el
 *       servidor, o dos sondeos solapados, no repiten nada.</li>
 *   <li>Cada uno va en su transacción: el que falla no se lleva a los demás.</li>
 *   <li>Solo cuentan los turnos abiertos avisando después de la V70; los anteriores no
 *       reciben nada, y así no hay ráfaga el día del despliegue.</li>
 * </ul>
 */
@Service
@Slf4j
public class ServicioRecordatorios {

    /** El interruptor de cada empresa (encendido por defecto). */
    public static final String PARAMETRO_ACTIVOS = "recordatorios_activos";
    /** Horas desde que le toca hasta el primero. */
    public static final String PARAMETRO_HORAS_TRAS = "recordatorio_horas_tras_el_turno";
    /** Horas antes de vencer en que sale el segundo. */
    public static final String PARAMETRO_HORAS_ANTES = "recordatorio_horas_antes_del_plazo";
    static final int HORAS_POR_DEFECTO = 24;

    private final TurnosParaRecordar turnos;
    private final RecordatorioEnviadoRepository registro;
    private final ServicioParametros parametros;
    private final ServicioCorreo correo;
    private final ServicioAvisosPortal avisos;
    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final DireccionDelCandidato direcciones;
    private final ServicioEnlaceAcceso enlaces;
    private final OrganizacionRepository organizaciones;
    private final TransactionTemplate enSuTransaccion;

    @SuppressWarnings("java:S107") // Cada uno es una pieza del recordatorio: quién, qué texto, por dónde.
    public ServicioRecordatorios(TurnosParaRecordar turnos, RecordatorioEnviadoRepository registro,
                                 ServicioParametros parametros, ServicioCorreo correo,
                                 ServicioAvisosPortal avisos, UsuarioRepository usuarios,
                                 PersonaRepository personas, DireccionDelCandidato direcciones,
                                 ServicioEnlaceAcceso enlaces, OrganizacionRepository organizaciones,
                                 PlatformTransactionManager transacciones) {
        this.turnos = turnos;
        this.registro = registro;
        this.parametros = parametros;
        this.correo = correo;
        this.avisos = avisos;
        this.usuarios = usuarios;
        this.personas = personas;
        this.direcciones = direcciones;
        this.enlaces = enlaces;
        this.organizaciones = organizaciones;
        this.enSuTransaccion = new TransactionTemplate(transacciones);
        this.enSuTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Lo que el sondeo llama cada minuto: con la hora de ahora. */
    public int enviarPendientes() {
        return enviarPendientes(Instant.now());
    }

    /**
     * Manda los recordatorios que tocan a esta hora.
     *
     * <p>Recibe la hora para poder probarlo sin esperar 24 horas ni depender de si la prueba
     * corre de día o de noche en Lima.
     *
     * @return cuántos salieron
     */
    public int enviarPendientes(Instant ahora) {
        // De noche no sale nada, y ni se mira: lo que tocaba sale a las 8:00.
        if (!CalendarioDeRecordatorios.enHorario(ahora)) {
            return 0;
        }
        List<Turno> abiertos = turnos.abiertos();
        if (abiertos.isEmpty()) {
            return 0;
        }
        Map<Long, List<RecordatorioEnviado>> hechos = registro
                .findByTransicionEstadoIdIn(abiertos.stream().map(Turno::turnoId).toList())
                .stream()
                .collect(Collectors.groupingBy(RecordatorioEnviado::getTransicionEstadoId));
        Map<Long, Reglas> porEmpresa = new HashMap<>();
        Long plataforma = organizaciones.findByEsPlataformaTrue().map(Organizacion::getId).orElse(null);

        int enviados = 0;
        for (Turno turno : abiertos) {
            Reglas reglas = porEmpresa.computeIfAbsent(turno.organizacionId(), this::reglasDe);
            if (!reglas.activos()) {
                continue;
            }
            List<RecordatorioEnviado> suyos = hechos.getOrDefault(turno.turnoId(), List.of());
            boolean yaElPrimero = suyos.stream()
                    .anyMatch(r -> RecordatorioEnviado.TRAS_ENTRAR.equals(r.getTipo()));
            boolean yaElDelPlazo = suyos.stream()
                    .anyMatch(r -> RecordatorioEnviado.ANTES_DEL_PLAZO.equals(r.getTipo())
                            && Objects.equals(r.getPlazoEn(), turno.venceEn()));
            var toca = CalendarioDeRecordatorios.queToca(turno.turnoDesde(), turno.venceEn(),
                    ahora, reglas.trasEntrar(), reglas.antesDelPlazo(), yaElPrimero, yaElDelPlazo);
            if (toca.isEmpty()) {
                continue;
            }
            try {
                if (Boolean.TRUE.equals(enSuTransaccion.execute(
                        estado -> mandar(turno, toca.get(), plataforma)))) {
                    enviados++;
                }
            } catch (RuntimeException e) {
                // Lo más probable: otro sondeo lo mandó a la vez y la base no dejó escribirlo
                // dos veces. Sea lo que sea, este no sale y los demás siguen.
                log.error("RECORDATORIO: no salió el {} de la postulación {}: {}",
                        toca.get().cual(), turno.postulacionId(), e.getMessage());
            }
        }
        if (enviados > 0) {
            log.info("RECORDATORIO: {} enviados", enviados);
        }
        return enviados;
    }

    /** Lo de cada empresa: si los manda y a cuántas horas. */
    private record Reglas(boolean activos, Duration trasEntrar, Duration antesDelPlazo) {
    }

    private Reglas reglasDe(Long organizacionId) {
        return new Reglas(
                parametros.booleano(organizacionId, PARAMETRO_ACTIVOS, true),
                Duration.ofHours(horas(organizacionId, PARAMETRO_HORAS_TRAS)),
                Duration.ofHours(horas(organizacionId, PARAMETRO_HORAS_ANTES)));
    }

    /** Las horas del parámetro; un cero o un negativo no tienen sentido y valen lo de siempre. */
    private int horas(Long organizacionId, String codigo) {
        int valor = parametros.entero(organizacionId, codigo, HORAS_POR_DEFECTO);
        return valor > 0 ? valor : HORAS_POR_DEFECTO;
    }

    /**
     * Escribe que salió y lo manda, en una sola transacción.
     *
     * <p>Primero la fila: si la base no la deja escribir —ya estaba—, no sale nada. El correo se
     * registra en la misma transacción; la campana, al confirmarse.
     */
    private boolean mandar(Turno turno, Toca toca, Long plataforma) {
        boolean delPlazo = toca.cual() == Cual.ANTES_DEL_PLAZO;
        registro.saveAndFlush(RecordatorioEnviado.builder()
                .postulacionId(turno.postulacionId())
                .transicionEstadoId(turno.turnoId())
                .tipo(delPlazo ? RecordatorioEnviado.ANTES_DEL_PLAZO : RecordatorioEnviado.TRAS_ENTRAR)
                .plazoEn(delPlazo ? turno.venceEn() : null)
                .resultado(RecordatorioEnviado.ENVIADO)
                .creadoEn(Instant.now())
                .build());
        if (toca.omitirElOtro()) {
            registro.saveAndFlush(RecordatorioEnviado.builder()
                    .postulacionId(turno.postulacionId())
                    .transicionEstadoId(turno.turnoId())
                    .tipo(RecordatorioEnviado.TRAS_ENTRAR)
                    .resultado(RecordatorioEnviado.OMITIDO)
                    .creadoEn(Instant.now())
                    .build());
        }
        avisar(turno, toca.cual(), plataforma);
        return true;
    }

    /** El correo y la campana, independientes: si uno falla, el otro sale igual. */
    private void avisar(Turno turno, Cual cual, Long plataforma) {
        String codigo = turno.esDelBanco()
                ? AvisoPortal.RECORDATORIO_EVALUACION : AvisoPortal.RECORDATORIO_PRUEBA;
        String vacante = turno.vacanteTitulo() == null ? "" : turno.vacanteTitulo();
        String vence = turno.venceEn() == null ? "" : FechaParaElCandidato.dicha(turno.venceEn());
        String frase = frase(turno.esDelBanco(), cual, vacante, vence);

        try {
            Usuario usuario = usuarios.findById(turno.usuarioId()).orElse(null);
            String nombre = usuario == null ? "" : personas.findById(usuario.getPersonaId())
                    .map(Persona::getNombre).orElse("");
            Map<String, String> variables = new HashMap<>(Map.of(
                    "nombre", nombre == null ? "" : nombre,
                    "vacante", vacante,
                    "vence", vence,
                    "aviso", frase,
                    "enlace", enlace(turno.postulacionId()),
                    "codigo", String.valueOf(turno.postulacionUuid())));
            // A la plataforma si a la empresa le faltara el texto: la V70 lo siembra en todas,
            // pero un recordatorio que no sale en silencio es peor que uno con el texto común.
            correo.plantillaConRecambio(turno.organizacionId(), plataforma, codigo)
                    .ifPresentOrElse(
                            plantilla -> correo.enviarCon(plantilla, turno.usuarioId(),
                                    usuario == null ? null : direcciones.de(usuario, turno.postulacionId()),
                                    variables),
                            () -> log.error("RECORDATORIO: no hay texto «{}» para la organización {}",
                                    codigo, turno.organizacionId()));
        } catch (RuntimeException e) {
            log.error("RECORDATORIO: no salió el correo de la postulación {}: {}. La campana sale "
                    + "igual", turno.postulacionId(), e.getMessage());
        }

        String cuerpo = turno.esDelBanco()
                ? "Tus respuestas se guardan solas: puedes empezar ahora y terminarla después."
                : "Si tu prueba tiene tiempo, empieza a contar cuando la abras y confirmes.";
        AvisoDeEtapaEnLaCampana.alConfirmar(() -> avisos.publicar(turno.organizacionId(),
                        turno.usuarioId(), codigo, frase, cuerpo, turno.postulacionId(),
                        turno.vacanteId()),
                codigo, turno.postulacionId());
    }

    /** La frase del recordatorio: el asunto del correo y el título de la campana. */
    static String frase(boolean delBanco, Cual cual, String vacante, String vence) {
        if (delBanco) {
            return cual == Cual.TRAS_ENTRAR
                    ? "Aún no has respondido tu evaluación para " + vacante
                    : "Tu evaluación para " + vacante + " vence el " + vence;
        }
        return cual == Cual.TRAS_ENTRAR
                ? "Aún no has empezado tu prueba del puesto para " + vacante
                : "Tu prueba del puesto para " + vacante + " vence el " + vence;
    }

    /** Por dónde entrar, como en los avisos de etapa. Sin enlace, el correo sale igual. */
    private String enlace(Long postulacionId) {
        try {
            return enlaces.generarEnlace(postulacionId).url();
        } catch (RuntimeException e) {
            log.error("RECORDATORIO: no se pudo crear el enlace de acceso de la postulación {}: {}",
                    postulacionId, e.getMessage());
            return "";
        }
    }
}
