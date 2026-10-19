package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.service.TextosDelMomento;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Accion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Lo que le toca hacer ahora a quien postula, con sus plazos reales (V76, regla 8 de
 * specs/Ready/contexto-y-resultados-por-whatsapp.md).
 *
 * <p>Separa tres cosas que el estado mezcla: el procedimiento previsto, la etapa actual y una
 * acción <b>habilitada para esa persona</b>. En revisión, calificando o esperando una fecha no
 * hay acción. Una simulación o una entrevista futura nunca sale como una cita asignada.
 *
 * <p>⚠️ <b>Nada antes del reloj.</b> La acción solo lleva instrucciones generales, el enlace fijo
 * a «Mis procesos» y los plazos: ni el caso, ni las preguntas, ni los materiales. Y solo lee:
 * consultar no inicia intentos, no confirma asistencia y no dispara ningún cronómetro.
 *
 * <p>Los plazos salen de las mismas filas que el portal: el intento ({@code iniciado_en},
 * {@code vence_en}), la evaluación, la versión de la prueba (modalidad y minutos o días) y el
 * cierre de la prueba de la vacante ({@code prueba_cierra_en}). Un dato que no existe va nulo.
 */
@Component
@RequiredArgsConstructor
public class LoQueLeToca {

    // Los tipos y estados de la acción, como los nombra el contrato v2 (C-3).
    public static final String EVALUACION_INICIAL = "EVALUACION_INICIAL";
    public static final String PRUEBA_PUESTO = "PRUEBA_PUESTO";
    public static final String CUESTIONARIO_TECNICO = "CUESTIONARIO_TECNICO";
    public static final String SIMULACION = "SIMULACION";
    public static final String VALIDACION = "VALIDACION";
    public static final String OTRA = "OTRA";

    public static final String HABILITADA = "HABILITADA";
    public static final String EN_CURSO = "EN_CURSO";
    public static final String COMPLETADA = "COMPLETADA";
    public static final String VENCIDA = "VENCIDA";

    public static final String CRONOMETRADA = "CRONOMETRADA";
    public static final String PLAZO_ABIERTO = "PLAZO_ABIERTO";

    /** Cómo se entra, sea cual sea la acción. El chat no da acceso ni empieza nada. */
    static final String INGRESO = "Inicia sesión en el portal con tu correo y contraseña y abre "
            + "«Mis procesos». Preguntar por aquí no empieza nada ni cambia tu proceso.";

    static final String PLAZO_ABIERTO_HABILITADA = "Ábrela en el portal cuando quieras: desde que "
            + "la empiezas tienes un plazo en días para entregarla.";
    static final String YA_EMPEZADA = "Ya la empezaste: entrégala en el portal antes del límite. "
            + "El tiempo sigue corriendo aunque cierres la página.";
    static final String YA_ENTREGADA = "Ya la entregaste. Por ahora no tienes nada pendiente en "
            + "esta etapa.";
    static final String PLAZO_TERMINADO = "El plazo para hacerla terminó.";

    private final IntentoPruebaRepository intentos;
    private final EvaluacionRepository evaluaciones;
    private final VersionBancoRepository versionesBanco;
    private final VersionPlantillaPruebaRepository versionesPlantilla;

    /** Lo que se sabe del tiempo de una prueba: su modalidad y su duración o sus días. */
    private record Tiempo(String modalidad, Integer duracionMinutos, Integer plazoDias) {
        static final Tiempo DESCONOCIDO = new Tiempo(null, null, null);
    }

    /** Los plazos y el punto en que está la acción. */
    private record Momento(String estado, Instant empezarAntesDe, Instant iniciadaEn,
                           Instant entregarAntesDe, boolean sinCompletar) {}

    /**
     * La acción de esta postulación ahora mismo, o {@code null} si no tiene ninguna.
     *
     * @param enlace el enlace fijo a «Mis procesos»; solo viaja con la acción abierta
     */
    public Accion de(Postulacion postulacion, Vacante vacante, Instant ahora, String enlace) {
        String estado = postulacion.getEstadoCodigo();
        if (estado == null) {
            return null;
        }
        return switch (estado) {
            case "PERFIL_TURNO_CANDIDATO" -> evaluacionInicial(postulacion, ahora, enlace);
            case "PRUEBA_TURNO_CANDIDATO" -> esCuestionario(vacante)
                    ? cuestionario(postulacion, vacante, ahora, enlace)
                    : pruebaDelPuesto(postulacion, vacante, ahora, enlace);
            case "SIMULACION_TURNO_CANDIDATO" -> sinPlazos(SIMULACION, HABILITADA, estado, enlace);
            case "VALIDACION_TURNO_CANDIDATO" -> sinPlazos(VALIDACION, EN_CURSO, estado, enlace);
            case "DECISION_TURNO_CANDIDATO" -> sinPlazos(OTRA, HABILITADA, estado, enlace);
            default -> null;
        };
    }

    /**
     * Hasta cuándo puede empezar la prueba de la etapa técnica, si EX lo sabe: la línea del plazo
     * del aviso «prueba disponible». Nulo si ya la empezó o si no hay fecha.
     */
    public Instant empezarAntesDe(Postulacion postulacion, Vacante vacante) {
        if (esCuestionario(vacante)) {
            Evaluacion evaluacion = laEvaluacion(postulacion.getEvaluacionTecnicaId());
            return evaluacion == null || evaluacion.getIniciadaEn() != null ? null : evaluacion.getVenceEn();
        }
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId()).orElse(null);
        if (intento == null) {
            return vacante == null ? null : vacante.getPruebaCierraEn();
        }
        return intento.getIniciadoEn() != null ? null : intento.getVenceEn();
    }

    /**
     * {@code CRONOMETRADA}, {@code PLAZO_ABIERTO} o nulo: cómo se mide la prueba del puesto de esta
     * postulación (la versión de su intento, o la publicada de la vacante). Nulo con el cuestionario.
     */
    public String modalidadDeLaPrueba(Postulacion postulacion, Vacante vacante) {
        if (vacante == null || esCuestionario(vacante)) {
            return null;
        }
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId()).orElse(null);
        return tiempoDeLaPrueba(vacante, intento).modalidad();
    }

    /** Su prueba del editor venció con huecos y se cerró sin entregar (V67): el portal lo dice aparte. */
    public boolean pruebaSinCompletar(Postulacion postulacion) {
        return intentos.findByPostulacionId(postulacion.getId())
                .map(IntentoPrueba::isNoCompletada).orElse(false);
    }

    /**
     * El momento más reciente en que cambió lo que la acción cuenta (QA-C0-F1). Nulo si no hay
     * nada. Solo lee:
     * <ul>
     *   <li>el intento y las evaluaciones: cuándo nacieron, empezaron o terminaron, y cuándo la
     *       base fechó el último cambio de su estado o de sus plazos ({@code contexto_actualizado_en},
     *       V77): ampliar un plazo o conceder uno propio también cuenta;</li>
     *   <li>y el paso del tiempo: una acción que ya está {@code VENCIDA} cambió en el momento en
     *       que venció su límite, aunque nadie tocara nada.</li>
     * </ul>
     */
    public Instant ultimoCambio(Postulacion postulacion, Vacante vacante, Instant ahora) {
        Instant ultimo = null;
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId()).orElse(null);
        if (intento != null) {
            ultimo = elMasReciente(ultimo, intento.getCreadoEn(), intento.getIniciadoEn(),
                    intento.getEntregadoEn(), intento.getContextoActualizadoEn());
        }
        for (Long id : new Long[] {postulacion.getEvaluacionId(), postulacion.getEvaluacionTecnicaId()}) {
            Evaluacion evaluacion = laEvaluacion(id);
            if (evaluacion != null) {
                ultimo = elMasReciente(ultimo, evaluacion.getCreadoEn(), evaluacion.getIniciadaEn(),
                        evaluacion.getTerminadaEn(), evaluacion.getContextoActualizadoEn());
            }
        }
        return elMasReciente(ultimo, cuandoVencio(de(postulacion, vacante, ahora, null), ahora));
    }

    /**
     * El límite que venció, si la acción está {@code VENCIDA} por él: el de entrega si ya la
     * empezó, si no el de empezar. Nulo si no venció o si el límite aún no ha pasado (la marcó
     * vencida otra cosa, que ya tiene su fecha).
     */
    private static Instant cuandoVencio(Accion accion, Instant ahora) {
        if (accion == null || !VENCIDA.equals(accion.estado())) {
            return null;
        }
        Instant limite = accion.entregarAntesDe() != null ? accion.entregarAntesDe() : accion.empezarAntesDe();
        return yaPaso(limite, ahora) ? limite : null;
    }

    /** El más reciente de los momentos dados, saltando los nulos. */
    public static Instant elMasReciente(Instant... momentos) {
        Instant ultimo = null;
        for (Instant momento : momentos) {
            if (momento != null && (ultimo == null || momento.isAfter(ultimo))) {
                ultimo = momento;
            }
        }
        return ultimo;
    }

    // ============ cada acción ============

    private Accion evaluacionInicial(Postulacion postulacion, Instant ahora, String enlace) {
        Evaluacion evaluacion = laEvaluacion(postulacion.getEvaluacionId());
        String instrucciones = TextosDelMomento.de("PERFIL_TURNO_CANDIDATO", null, false).texto();
        // Sin cronómetro: se responde a su ritmo, se puede salir y volver, hasta su vencimiento.
        Tiempo tiempo = new Tiempo(PLAZO_ABIERTO, null, null);
        if (evaluacion == null) {
            return armar(EVALUACION_INICIAL, new Momento(HABILITADA, null, null, null, false),
                    tiempo, instrucciones, enlace);
        }
        Instant vence = evaluacion.getVenceEn();
        Momento momento;
        if (evaluacion.getTerminadaEn() != null || "TERMINADA".equals(evaluacion.getEstado())) {
            momento = new Momento(COMPLETADA, null, evaluacion.getIniciadaEn(), vence, false);
        } else if ("VENCIDA".equals(evaluacion.getEstado()) || yaPaso(vence, ahora)) {
            momento = new Momento(VENCIDA, null, evaluacion.getIniciadaEn(), vence, false);
        } else if (evaluacion.getIniciadaEn() == null) {
            // La evaluación no tiene intento: el límite de entrega es el suyo, y sin empezar
            // también es el último momento para empezarla.
            momento = new Momento(HABILITADA, vence, null, vence, false);
        } else {
            momento = new Momento(EN_CURSO, null, evaluacion.getIniciadaEn(), vence, false);
        }
        return armar(EVALUACION_INICIAL, momento, tiempo, instrucciones, enlace);
    }

    private Accion cuestionario(Postulacion postulacion, Vacante vacante, Instant ahora, String enlace) {
        Evaluacion evaluacion = laEvaluacion(postulacion.getEvaluacionTecnicaId());
        Integer minutos = vacante.getMinutosEtapaTecnica();
        if (minutos == null && evaluacion != null && evaluacion.getVersionBancoNivelId() != null) {
            minutos = versionesBanco.findById(evaluacion.getVersionBancoNivelId())
                    .map(VersionBanco::getMinutosObjetivo).orElse(null);
        }
        Tiempo tiempo = minutos != null ? new Tiempo(CRONOMETRADA, minutos, null)
                : new Tiempo(PLAZO_ABIERTO, null, null);
        Momento momento;
        if (evaluacion == null) {
            momento = new Momento(HABILITADA, null, null, null, false);
        } else if (evaluacion.getTerminadaEn() != null || "TERMINADA".equals(evaluacion.getEstado())) {
            momento = new Momento(COMPLETADA, null, evaluacion.getIniciadaEn(), evaluacion.getVenceEn(), false);
        } else if (evaluacion.getIniciadaEn() == null) {
            momento = yaPaso(evaluacion.getVenceEn(), ahora)
                    ? new Momento(VENCIDA, evaluacion.getVenceEn(), null, null, false)
                    : new Momento(HABILITADA, evaluacion.getVenceEn(), null, null, false);
        } else {
            momento = new Momento(yaPaso(evaluacion.getVenceEn(), ahora) ? VENCIDA : EN_CURSO, null,
                    evaluacion.getIniciadaEn(), evaluacion.getVenceEn(), false);
        }
        return armar(CUESTIONARIO_TECNICO, momento, tiempo,
                TextosDelMomento.CUESTIONARIO_LISTO.texto(), enlace);
    }

    private Accion pruebaDelPuesto(Postulacion postulacion, Vacante vacante, Instant ahora, String enlace) {
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId()).orElse(null);
        Tiempo tiempo = tiempoDeLaPrueba(vacante, intento);
        Instant cierreDeLaVacante = vacante == null ? null : vacante.getPruebaCierraEn();
        Momento momento;
        if (intento == null) {
            momento = yaPaso(cierreDeLaVacante, ahora)
                    ? new Momento(VENCIDA, cierreDeLaVacante, null, null, false)
                    : new Momento(HABILITADA, cierreDeLaVacante, null, null, false);
        } else if (intento.isNoCompletada()) {
            // Venció con algo sin responder (V67): se cerró sin entregar.
            momento = new Momento(VENCIDA, null, intento.getIniciadoEn(), intento.getVenceEn(), true);
        } else if (intento.getEntregadoEn() != null) {
            momento = new Momento(COMPLETADA, null, intento.getIniciadoEn(), intento.getVenceEn(), false);
        } else if (intento.getIniciadoEn() == null) {
            // Sin empezar, su vence_en es la fecha de cierre que rige para esta persona (la de
            // la vacante o la que se le concedió).
            Instant empezar = intento.getVenceEn() != null ? intento.getVenceEn() : cierreDeLaVacante;
            momento = yaPaso(empezar, ahora)
                    ? new Momento(VENCIDA, empezar, null, null, false)
                    : new Momento(HABILITADA, empezar, null, null, false);
        } else {
            // Empezada: su vence_en ya es el límite de entrega. La fecha para empezar sigue siendo
            // el cierre de la vacante, salvo que se le concediera una propia (ya no se conoce).
            Instant empezar = intento.isPlazoPropio() ? null : cierreDeLaVacante;
            momento = new Momento(yaPaso(intento.getVenceEn(), ahora) ? VENCIDA : EN_CURSO, empezar,
                    intento.getIniciadoEn(), intento.getVenceEn(), false);
        }
        String habilitada = CRONOMETRADA.equals(tiempo.modalidad())
                ? TextosDelMomento.de("PRUEBA_TURNO_CANDIDATO", vacante == null ? null
                        : vacante.getInstrumentoEtapaTecnica(), false).texto()
                : PLAZO_ABIERTO_HABILITADA;
        return armar(PRUEBA_PUESTO, momento, tiempo, habilitada, enlace);
    }

    /** Simulación, validación y la evidencia de la decisión: sin plazos que EX pueda afirmar. */
    private static Accion sinPlazos(String tipo, String estado, String codigoDelEstado, String enlace) {
        String instrucciones = TextosDelMomento.de(codigoDelEstado, null, false).texto();
        return armar(tipo, new Momento(estado, null, null, null, false), Tiempo.DESCONOCIDO,
                instrucciones, enlace);
    }

    // ============ ayudas ============

    /**
     * La modalidad y la duración que de verdad rigen, las mismas que pinta el portal: la versión
     * a la que quedó atado el intento, y sin intento la publicada de la vacante. En la plantilla,
     * los minutos de la vacante mandan sobre los suyos y la vuelven cronometrada.
     */
    private Tiempo tiempoDeLaPrueba(Vacante vacante, IntentoPrueba intento) {
        Long versionBancoId = intento == null ? null : intento.getVersionBancoId();
        Long versionPlantillaId = intento == null ? null : intento.getVersionPlantillaPruebaId();
        if (intento == null && vacante != null) {
            if ("PRUEBA_PROPIA".equals(vacante.getInstrumentoEtapaTecnica())) {
                versionBancoId = versionesBanco.pruebaPropiaDe(vacante.getId(), "PUBLICADA")
                        .map(VersionBanco::getId).orElse(null);
            } else {
                versionPlantillaId = vacante.getVersionPlantillaPruebaId();
            }
        }
        if (versionBancoId != null) {
            VersionBanco propia = versionesBanco.findById(versionBancoId).orElse(null);
            return propia == null ? Tiempo.DESCONOCIDO
                    : comoTiempo(propia.getModalidad(), propia.getDuracionMinutos(), propia.getPlazoDias());
        }
        if (versionPlantillaId != null) {
            VersionPlantillaPrueba plantilla = versionesPlantilla.findById(versionPlantillaId).orElse(null);
            Integer minutosVacante = vacante == null ? null : vacante.getMinutosEtapaTecnica();
            if (minutosVacante != null) {
                return new Tiempo(CRONOMETRADA, minutosVacante, null);
            }
            return plantilla == null ? Tiempo.DESCONOCIDO
                    : comoTiempo(plantilla.getModalidad(), plantilla.getDuracionMinutos(), plantilla.getPlazoDias());
        }
        return Tiempo.DESCONOCIDO;
    }

    private static Tiempo comoTiempo(String modalidad, Integer minutos, Integer dias) {
        if (CRONOMETRADA.equals(modalidad)) {
            return new Tiempo(CRONOMETRADA, minutos, null);
        }
        if (PLAZO_ABIERTO.equals(modalidad)) {
            return new Tiempo(PLAZO_ABIERTO, null, dias);
        }
        return Tiempo.DESCONOCIDO;
    }

    /**
     * La acción en su forma del contrato. Abierta ({@code HABILITADA} o {@code EN_CURSO}) lleva el
     * enlace fijo; entregada o vencida, no: ya no se invita a hacerla.
     */
    private static Accion armar(String tipo, Momento momento, Tiempo tiempo, String habilitada,
                                String enlace) {
        boolean abierta = HABILITADA.equals(momento.estado()) || EN_CURSO.equals(momento.estado());
        String instrucciones = switch (momento.estado()) {
            case COMPLETADA -> YA_ENTREGADA;
            case VENCIDA -> momento.sinCompletar() ? TextosDelMomento.PRUEBA_SIN_COMPLETAR.texto()
                    : PLAZO_TERMINADO;
            case EN_CURSO -> esDeLaEtapaTecnica(tipo) ? YA_EMPEZADA : habilitada;
            default -> habilitada;
        };
        return new Accion(tipo, momento.estado(),
                TextosDeWhatsapp.cortar(instrucciones, 600), INGRESO,
                abierta ? TextosDeWhatsapp.enlaceFijo(enlace) : null,
                tiempo.modalidad(), tiempo.duracionMinutos(), tiempo.plazoDias(),
                alMilisegundo(momento.empezarAntesDe()), alMilisegundo(momento.iniciadaEn()),
                alMilisegundo(momento.entregarAntesDe()), TextosDeWhatsapp.ZONA.getId());
    }

    private static boolean esDeLaEtapaTecnica(String tipo) {
        return PRUEBA_PUESTO.equals(tipo) || CUESTIONARIO_TECNICO.equals(tipo);
    }

    private static boolean esCuestionario(Vacante vacante) {
        return vacante != null && CUESTIONARIO_TECNICO.equals(vacante.getInstrumentoEtapaTecnica());
    }

    private Evaluacion laEvaluacion(Long id) {
        return id == null ? null : evaluaciones.findById(id).orElse(null);
    }

    private static boolean yaPaso(Instant limite, Instant ahora) {
        return limite != null && !limite.isAfter(ahora);
    }

    public static Instant alMilisegundo(Instant momento) {
        return momento == null ? null : momento.truncatedTo(ChronoUnit.MILLIS);
    }
}
