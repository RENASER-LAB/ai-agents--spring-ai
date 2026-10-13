package com.renaser.ai.ai_engine.postulacion.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lo que el candidato lee de su proceso en cada momento: el título y la línea que lo explica
 * (V73).
 *
 * <p>Hasta la V73 estos textos vivían solo en el portal ({@code MOMENTOS} de
 * {@code src/dominio/estados.ts}), y el servidor no sabía decir «en qué va» un proceso con las
 * palabras de la pantalla. El bot de WhatsApp tiene que decir exactamente lo mismo que el
 * portal, así que el único sitio de verdad es este. El portal puede seguir pintando su copia
 * mientras coincidan: las dos partes se comparan con
 * {@code docs/contratos/textos-del-momento.json}, que es igual en los dos repositorios
 * ({@code TextosDelMomentoTest} aquí y {@code estados.test.ts} allí).
 *
 * <p>⚠️ <b>Cambiar un texto es cambiarlo en los tres sitios</b>: aquí, en el JSON del contrato y
 * en el portal. Las pruebas de cada lado fallan hasta que coinciden.
 */
public final class TextosDelMomento {

    /** Un momento dicho en palabras: lo grande y la línea que lo explica. */
    public record Texto(String titulo, String texto) {}

    /** Las dos formas de la etapa de la prueba (ver {@code Vacante.instrumentoEtapaTecnica}). */
    public static final String CUESTIONARIO_TECNICO = "CUESTIONARIO_TECNICO";

    private static final String EN_REVISION =
            "Una persona del equipo revisa el resultado y confirma si continúas.";

    private static final Map<String, Texto> POR_ESTADO = new LinkedHashMap<>();

    static {
        poner("POSTULADA", "Estamos revisando tu postulación",
                "Comprobamos los requisitos indispensables del puesto.");
        poner("PERFIL_TURNO_CANDIDATO", "Tienes una evaluación pendiente",
                "Tus respuestas se guardan solas. Puedes salir y volver donde lo dejaste.");
        poner("PERFIL_CALIFICANDO", "Estamos calificando tus respuestas",
                "Se juntan tu currículum y tu evaluación en un solo perfil. Suele tardar poco.");
        poner("PERFIL_POR_CONFIRMAR", "Tu perfil está en revisión", EN_REVISION);
        poner("PRUEBA_TURNO_CANDIDATO", "Prueba del puesto habilitada",
                "El cronómetro empieza cuando confirmes, y no se detiene al cerrar la página.");
        poner("PRUEBA_CALIFICANDO", "Estamos calificando tu prueba",
                "Se revisa tu entregable y la explicación de tus decisiones.");
        poner("PRUEBA_POR_CONFIRMAR", "Tu prueba está en revisión", EN_REVISION);
        poner("SIMULACION_POR_HABILITAR", "Esperando una fecha de simulación",
                "Todavía no hay una sesión con cupo para tu vacante. Te avisaremos en cuanto la haya.");
        poner("SIMULACION_TURNO_CANDIDATO", "Confirma tu simulación",
                "Elige una de las fechas disponibles. La sesión es grupal y dura dos horas.");
        poner("SIMULACION_POR_CONFIRMAR", "Tu simulación está en revisión",
                "Pasó la sesión. Falta calificarla y tener la conversación final.");
        poner("VALIDACION_POR_HABILITAR", "Preparando tu periodo de validación",
                "Falta registrar los datos de la vinculación. Te avisaremos cuando empiece.");
        poner("VALIDACION_TURNO_CANDIDATO", "Validación en curso",
                "Estás trabajando el periodo acordado. Las métricas se registran solas.");
        poner("VALIDACION_POR_CONFIRMAR", "Tu validación está en revisión",
                "Terminó el periodo. El responsable del área completa las últimas métricas.");
        poner("DECISION_TURNO_CANDIDATO", "Necesitamos una evidencia más",
                "Hay una duda concreta que queremos resolver antes de decidir.");
        poner("DECISION_POR_CONFIRMAR", "Decisión final en curso",
                "Ya está todo evaluado. Falta la decisión de la persona responsable.");
        poner("CONTRATADO", "Te damos la bienvenida",
                "El proceso terminó y se te contrató. Nos pondremos en contacto contigo.");
        poner("NO_CONTINUA", "Proceso finalizado",
                "En esta oportunidad no continúas en el proceso. Gracias por participar.");
        poner("CERRADA", "Postulación cerrada",
                "Esta postulación terminó sin llegar a una decisión. Ya no recibirás avisos.");
    }

    /** La vacante rinde el cuestionario técnico y le toca abrirlo. */
    public static final Texto CUESTIONARIO_LISTO = new Texto("Tu prueba técnica está lista",
            "Son preguntas sobre este puesto, y se responden escribiendo. Si la vacante fijó un "
                    + "tiempo, el reloj empieza cuando la abras.");

    /** El cuestionario técnico se está calificando: el título es el de siempre. */
    public static final String CUESTIONARIO_CALIFICANDO =
            "Se están leyendo tus respuestas, una por una.";

    /** La prueba del editor venció con huecos (V67): ya no le toca nada. */
    public static final Texto PRUEBA_SIN_COMPLETAR = new Texto(
            "Tu tiempo terminó y la prueba quedó sin completar",
            "Faltaba responder alguna pregunta o subir algún entregable obligatorio, así que no "
                    + "se entregó ni se va a calificar. Quien lleva tu proceso lo ve y te escribirá.");

    /** Un estado que no está en la lista: el proceso sigue vivo, y se dice. */
    public static final Texto DESCONOCIDO = new Texto("Tu proceso sigue activo",
            "Cuando haya una acción pendiente aparecerá aquí y te llegará un correo.");

    private TextosDelMomento() {}

    private static void poner(String estado, String titulo, String texto) {
        POR_ESTADO.put(estado, new Texto(titulo, texto));
    }

    /** El texto de un estado, sin mirar la vacante. Los dieciocho, en su orden. */
    public static Map<String, Texto> porEstado() {
        return java.util.Collections.unmodifiableMap(POR_ESTADO);
    }

    /**
     * Lo que dice el portal de esta postulación ahora mismo.
     *
     * @param estado              el código del estado
     * @param instrumento         lo que rinde la vacante en su etapa técnica (puede ser nulo)
     * @param pruebaSinCompletar  su prueba del editor venció con huecos
     */
    public static Texto de(String estado, String instrumento, boolean pruebaSinCompletar) {
        if (pruebaSinCompletar && "PRUEBA_TURNO_CANDIDATO".equals(estado)) {
            return PRUEBA_SIN_COMPLETAR;
        }
        Texto base = POR_ESTADO.getOrDefault(estado, DESCONOCIDO);
        if (!CUESTIONARIO_TECNICO.equals(instrumento)) {
            return base;
        }
        if ("PRUEBA_TURNO_CANDIDATO".equals(estado)) {
            return CUESTIONARIO_LISTO;
        }
        if ("PRUEBA_CALIFICANDO".equals(estado)) {
            return new Texto(base.titulo(), CUESTIONARIO_CALIFICANDO);
        }
        return base;
    }
}
