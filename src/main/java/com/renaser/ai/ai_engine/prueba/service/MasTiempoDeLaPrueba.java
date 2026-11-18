package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.RecordatorioEnviado;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.RecordatorioEnviadoRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.postulacion.service.ServicioRecordatorios;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.DarMasTiempo;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.MasTiempo;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.MasTiempoDado;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.SinCompletar;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * «Darle más tiempo» a quien no completó la prueba del puesto (V89).
 *
 * <p>Se le acabó el tiempo con algo sin responder o sin subir, o se le pasó la fecha sin abrirla.
 * El equipo fija una fecha y hora nueva —futura y sin tope—, escribe el motivo y decide si
 * avisarle. La prueba vuelve a «En curso» con lo que ya tenía, o a «Sin empezar» si no la había
 * abierto; la fecha queda como suya ({@code plazo_propio}) y la postulación sigue donde estaba,
 * en «le toca al candidato».
 *
 * <p>Las reglas, en el orden en que se comprueban:
 * <ol>
 *   <li>El permiso {@code mover_postulacion} y el alcance sobre la vacante; una vacante
 *       eliminada no existe (404).</li>
 *   <li>Con la vacante <b>archivada</b>, no (409). Con la vacante <b>cerrada</b>, sí: el cierre
 *       automático de la V78 la cierra antes de que se rinda la prueba, y la prueba conserva su
 *       propia fecha (decisión del 18/11/2026).</li>
 *   <li>Un proceso cerrado no se reabre (409).</li>
 *   <li>Tiene que haber algo que reabrir: un segundo envío para la misma persona ya no la
 *       encuentra sin completar (409) y no repite los avisos.</li>
 *   <li>La fecha tiene que ser futura (400).</li>
 * </ol>
 *
 * <p>⚠️ <b>El barrido y este guardado se esperan el uno al otro</b>: los dos toman primero la
 * vacante, como el resto de caminos que tocan una prueba. Si el barrido cierra la prueba mientras
 * alguien le da más tiempo, el guardado que llega después la reabre con la fecha nueva; si llega
 * antes, el barrido ya no la encuentra vencida.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasTiempoDeLaPrueba {

    /** El mismo permiso que «El plazo de esta persona». */
    public static final String PERMISO = "mover_postulacion";
    /** Lo que dice el chip junto al botón apagado. */
    public static final String VACANTE_ARCHIVADA = "Vacante archivada";
    public static final String PROCESO_CERRADO = "Proceso cerrado";
    /** El 409 de una vacante archivada. */
    public static final String ARCHIVADA_NO =
            "La vacante está archivada: desarchívala para darle más tiempo";

    private static final String TURNO_DE_LA_PRUEBA = "PRUEBA_TURNO_CANDIDATO";

    private final AlcanceSobreLaVacante alcance;
    private final PostulacionRepository postulaciones;
    private final IntentoPruebaRepository intentos;
    private final VacanteRepository vacantes;
    private final MaquinaEstados maquina;
    private final ServicioAuditoria auditoria;
    private final NombresDeUsuarios nombres;
    private final CalificacionDeLaPruebaPropia calculo;
    private final TransicionEstadoRepository transiciones;
    private final RecordatorioEnviadoRepository recordatorios;
    private final ServicioParametros parametros;
    private final AvisoDeMasTiempo avisos;

    /**
     * Le da más tiempo y deja los avisos para cuando se confirme. Ver la cabecera.
     *
     * @return la fecha nueva, en qué punto queda su prueba y por dónde se le avisa
     */
    @Transactional
    public MasTiempoDado dar(ContextoUsuario quien, Long postulacionId, DarMasTiempo datos) {
        Postulacion postulacion = alcance.laPostulacionVisible(quien, postulacionId, PERMISO);
        alcance.exigirQueSuVacanteSigaExistiendo(postulacion);
        Vacante vacante = vacantes.findByIdAndOrganizacionId(postulacion.getVacanteId(),
                        postulacion.getOrganizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", postulacion.getVacanteId()));
        if (vacante.getArchivadaEn() != null) {
            throw new IllegalStateException(ARCHIVADA_NO);
        }

        // La vacante primero, como el barrido; después la fila de la postulación, para que un
        // descarte que llegue a la vez gane o pierda entero y nunca a medias.
        vacantes.bloquearParaCalificarYReleer(postulacion);
        String estadoAhora = postulaciones.estadoBloqueandoLaFila(postulacion.getId());
        if (!Objects.equals(estadoAhora, postulacion.getEstadoCodigo())) {
            throw new IllegalStateException("Su proceso acaba de cambiar desde otra pantalla: "
                    + "vuelve a cargar su ficha antes de darle más tiempo");
        }
        if (maquina.yaTermino(postulacion)) {
            throw new IllegalStateException(
                    "Su proceso ya está cerrado: un proceso cerrado no se reabre ni recibe más tiempo");
        }

        // Se lee por primera vez aquí, ya con la vacante tomada: es la fila confirmada, la que
        // dejó el barrido si acababa de pasar.
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba del puesto", "postulación", postulacionId));
        Instant ahora = Instant.now();
        if (!intento.seLePuedeDarMasTiempo(ahora)) {
            throw new IllegalStateException("Su prueba ya no está sin completar: ya tiene tiempo "
                    + "para hacerla o ya la entregó. Vuelve a cargar su ficha");
        }
        // Con la precisión de la base (microsegundos): la misma fecha tiene que casar después
        // con la del recordatorio y la del aviso por WhatsApp, que se comparan con lo guardado.
        Instant nueva = datos.venceEn().truncatedTo(ChronoUnit.MICROS);
        if (!nueva.isAfter(ahora)) {
            throw new IllegalArgumentException("Esa fecha ya pasó: elige una fecha y hora futuras");
        }

        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("venceEn", intento.getVenceEn() == null ? null : intento.getVenceEn().toString());
        antes.put("noCompletada", intento.isNoCompletada());
        antes.put("abierta", intento.getIniciadoEn() != null);

        // Vuelve a estar abierta: sin la marca, sin la entrega del barrido y con sus respuestas y
        // sus archivos, que nunca se tocaron. La fecha es suya: si después cambia la de la
        // vacante, a ella no se la toca (V32), igual que con «El plazo de esta persona».
        String motivo = datos.motivo().trim();
        intento.setNoCompletada(false);
        intento.setEntregadoEn(null);
        intento.setEsEntregaAutomatica(false);
        intento.setVenceEn(nueva);
        intento.setPlazoPropio(true);
        intento.setMasTiempoEn(ahora);
        intento.setMasTiempoPorUsuarioId(quien.usuarioId());
        intento.setMasTiempoMotivo(motivo);
        intentos.save(intento);

        auditoria.registrar(quien.organizacionId(), quien, "dar_mas_tiempo_prueba",
                "intento_prueba", intento.getId(), antes,
                Map.of("venceEn", nueva.toString()), motivo);

        recordatoriosQueYaNoTocan(postulacion, nueva, ahora);

        boolean avisar = datos.avisar() == null || datos.avisar();
        List<String> avisadoPor = List.of();
        if (avisar) {
            avisadoPor = avisos.canales(postulacion);
            avisos.avisar(postulacion, intento, vacante.getTitulo());
        }
        return new MasTiempoDado(postulacion.getId(), intento.getVenceEn(),
                intento.getIniciadoEn() != null ? EstadoPruebaDelPuesto.EN_CURSO.name()
                        : EstadoPruebaDelPuesto.SIN_EMPEZAR.name(),
                avisadoPor);
    }

    /** Lo que la ficha dice arriba sobre su tiempo (V89): si no la completó, o el más tiempo dado. */
    public record DeLaFicha(SinCompletar sinCompletar, MasTiempo masTiempo) {}

    /**
     * Lo que la ficha cuenta de su tiempo: si no la completó —cuándo, qué le faltó y si el botón
     * se puede usar— o, con la prueba otra vez abierta, quién le dio más tiempo y por qué.
     *
     * @param vacante la vacante de la postulación, o nulo si no se encontró
     */
    @Transactional(readOnly = true)
    public DeLaFicha deLaFicha(ContextoUsuario quien, Postulacion postulacion, Vacante vacante,
                               IntentoPrueba intento) {
        Instant ahora = Instant.now();
        SinCompletar sinCompletar = null;
        if (intento.quedoSinCompletar(ahora)) {
            boolean sinAbrir = intento.getIniciadoEn() == null;
            String bloqueo = null;
            if (vacante != null && vacante.getArchivadaEn() != null) {
                bloqueo = VACANTE_ARCHIVADA;
            } else if (maquina.yaTermino(postulacion)) {
                bloqueo = PROCESO_CERRADO;
            }
            sinCompletar = new SinCompletar(
                    intento.getVenceEn() != null ? intento.getVenceEn() : intento.getEntregadoEn(),
                    sinAbrir, sinAbrir ? null : queLeFalto(intento), quien.tiene(PERMISO), bloqueo);
        }
        MasTiempo masTiempo = null;
        if (sinCompletar == null && intento.getEntregadoEn() == null && intento.getMasTiempoEn() != null) {
            masTiempo = new MasTiempo(intento.getMasTiempoEn(),
                    intento.getMasTiempoPorUsuarioId() == null ? NombresDeUsuarios.ANONIMO
                            : nombres.de(intento.getMasTiempoPorUsuarioId()),
                    intento.getMasTiempoMotivo());
        }
        return new DeLaFicha(sinCompletar, masTiempo);
    }

    /** «le faltaron 4 preguntas y «Tablero.xlsx»», o nulo si no se puede decir. */
    private String queLeFalto(IntentoPrueba intento) {
        if (!intento.esDelEditor()) {
            return null;
        }
        try {
            var falta = calculo.loQueFaltaTanda(List.of(intento)).get(intento.getId());
            return falta == null || falta.nada() ? null : falta.dicho();
        } catch (RuntimeException e) {
            log.warn("No se pudo contar lo que le faltó a la prueba {}: la ficha sale sin ese "
                    + "detalle", intento.getId(), e);
            return null;
        }
    }

    /**
     * Los recordatorios que dar más tiempo deja sin sentido, escritos como OMITIDO en el turno
     * en que está (V70). No es una transición: el turno sigue siendo el mismo.
     *
     * <ul>
     *   <li><b>«A las 24 h de que le toque» no se repite</b>: ya salió o se da por gastado.</li>
     *   <li><b>«24 h antes de que venza», con la fecha nueva a menos de esas horas</b>: su momento
     *       ya pasó y saldría justo detrás del aviso de «más tiempo». A más de esas horas sale
     *       una vez, con la fecha nueva, también si ya la había empezado.</li>
     * </ul>
     */
    private void recordatoriosQueYaNoTocan(Postulacion postulacion, Instant venceEn, Instant ahora) {
        if (!TURNO_DE_LA_PRUEBA.equals(postulacion.getEstadoCodigo())) {
            return;
        }
        TransicionEstado turno = transiciones.findFirstByPostulacionIdOrderByIdDesc(postulacion.getId())
                .filter(t -> TURNO_DE_LA_PRUEBA.equals(t.getEstadoNuevoCodigo()))
                .orElse(null);
        if (turno == null) {
            return;
        }
        List<RecordatorioEnviado> hechos = recordatorios.findByTransicionEstadoIdIn(List.of(turno.getId()));
        boolean yaElPrimero = hechos.stream()
                .anyMatch(r -> RecordatorioEnviado.TRAS_ENTRAR.equals(r.getTipo()));
        if (!yaElPrimero) {
            recordatorios.save(omitido(postulacion, turno, RecordatorioEnviado.TRAS_ENTRAR, null, ahora));
        }
        int horas = parametros.entero(postulacion.getOrganizacionId(),
                ServicioRecordatorios.PARAMETRO_HORAS_ANTES, ServicioRecordatorios.HORAS_POR_DEFECTO);
        Duration antes = Duration.ofHours(horas > 0 ? horas : ServicioRecordatorios.HORAS_POR_DEFECTO);
        boolean yaElDeEstaFecha = hechos.stream()
                .anyMatch(r -> RecordatorioEnviado.ANTES_DEL_PLAZO.equals(r.getTipo())
                        && venceEn.equals(r.getPlazoEn()));
        if (!venceEn.minus(antes).isAfter(ahora) && !yaElDeEstaFecha) {
            recordatorios.save(omitido(postulacion, turno, RecordatorioEnviado.ANTES_DEL_PLAZO, venceEn, ahora));
        }
    }

    private static RecordatorioEnviado omitido(Postulacion postulacion, TransicionEstado turno,
                                               String tipo, Instant plazoEn, Instant ahora) {
        return RecordatorioEnviado.builder()
                .postulacionId(postulacion.getId())
                .transicionEstadoId(turno.getId())
                .tipo(tipo)
                .plazoEn(plazoEn)
                .resultado(RecordatorioEnviado.OMITIDO)
                .creadoEn(ahora)
                .build();
    }
}
