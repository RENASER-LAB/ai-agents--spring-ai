package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Etapa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Las etapas públicas de una vacante, en su orden, con su nombre y su explicación (V76, regla 6
 * de la spec).
 *
 * <p>Los nombres y las explicaciones son los del recorrido del portal («Tu perfil», «La prueba del
 * puesto»…, en {@code Seguimiento.tsx}), y cambian solo donde el de siempre sería falso para esta
 * vacante: sin evaluación inicial no se promete una evaluación escrita, con el cuestionario
 * técnico no se habla de un encargo, y una prueba sin cronómetro no se dice «con tiempo medido».
 * No se afirma que todos respondan un banco de preguntas ni que toda vacante tenga la misma prueba.
 *
 * <p>Los textos de {@code TextosDelMomento}, compartidos con el portal, no se tocan.
 */
public final class ProcedimientoPublico {

    public static final String PERFIL_INTEGRAL = "PERFIL_INTEGRAL";
    public static final String PRUEBA_PUESTO = "PRUEBA_PUESTO";
    public static final String SIMULACION = "SIMULACION";
    public static final String VALIDACION = "VALIDACION";
    public static final String DECISION = "DECISION";

    /** Las cinco etapas del recorrido, en su orden. Todas aplican a toda vacante publicada. */
    public static final List<String> ETAPAS =
            List.of(PERFIL_INTEGRAL, PRUEBA_PUESTO, SIMULACION, VALIDACION, DECISION);

    public static final String HECHA = "HECHA";
    public static final String ACTUAL = "ACTUAL";
    public static final String PREVISTA = "PREVISTA";

    static final int LARGO_DEL_NOMBRE = 60;
    static final int LARGO_DE_LA_EXPLICACION = 300;

    private ProcedimientoPublico() {}

    /**
     * El procedimiento de una postulación en marcha: antes de la etapa actual, {@code HECHA};
     * la suya, {@code ACTUAL}; después, {@code PREVISTA} (lo previsto, no una promesa de avanzar).
     *
     * @param etapaActual la etapa del estado, o nulo si aún no entró en ninguna
     * @param modalidadDeLaPrueba {@code CRONOMETRADA}, {@code PLAZO_ABIERTO} o nulo si no se sabe
     */
    public static List<Etapa> enMarcha(Vacante vacante, String etapaActual, String modalidadDeLaPrueba) {
        int actual = etapaActual == null ? -1 : ETAPAS.indexOf(etapaActual);
        List<Etapa> etapas = new ArrayList<>();
        for (int i = 0; i < ETAPAS.size(); i++) {
            etapas.add(etapa(ETAPAS.get(i), i + 1, comparada(i, actual), vacante, modalidadDeLaPrueba));
        }
        return etapas;
    }

    /**
     * El de una postulación terminada: solo las etapas a las que llegó, todas {@code HECHA}. Las que
     * no alcanzó no se presentan como previstas: ese proceso ya no sigue.
     */
    public static List<Etapa> terminado(Vacante vacante, Collection<String> alcanzadas,
                                        String modalidadDeLaPrueba) {
        List<Etapa> etapas = new ArrayList<>();
        for (int i = 0; i < ETAPAS.size(); i++) {
            if (alcanzadas.contains(ETAPAS.get(i))) {
                etapas.add(etapa(ETAPAS.get(i), i + 1, HECHA, vacante, modalidadDeLaPrueba));
            }
        }
        return etapas;
    }

    private static String comparada(int etapa, int actual) {
        if (etapa < actual) {
            return HECHA;
        }
        return etapa == actual ? ACTUAL : PREVISTA;
    }

    private static Etapa etapa(String codigo, int orden, String estado, Vacante vacante,
                               String modalidadDeLaPrueba) {
        return new Etapa(codigo,
                TextosDeWhatsapp.cortar(nombre(codigo, vacante), LARGO_DEL_NOMBRE),
                TextosDeWhatsapp.cortar(explicacion(codigo, vacante, modalidadDeLaPrueba),
                        LARGO_DE_LA_EXPLICACION),
                orden, estado);
    }

    static String nombre(String codigo, Vacante vacante) {
        return switch (codigo) {
            case PERFIL_INTEGRAL -> "Tu perfil";
            case PRUEBA_PUESTO -> esCuestionario(vacante) ? "La prueba técnica" : "La prueba del puesto";
            case SIMULACION -> "La simulación de trabajo";
            case VALIDACION -> "El periodo de validación";
            default -> "La decisión final";
        };
    }

    static String explicacion(String codigo, Vacante vacante, String modalidadDeLaPrueba) {
        return switch (codigo) {
            case PERFIL_INTEGRAL -> vacante != null && !vacante.isAplicaEvaluacion()
                    ? "Se revisa tu currículum. Esta vacante no pide una evaluación escrita."
                    : "Tu currículum y una evaluación escrita.";
            case PRUEBA_PUESTO -> laPrueba(vacante, modalidadDeLaPrueba);
            case SIMULACION -> "Una sesión de trabajo, con fecha a elegir.";
            case VALIDACION -> "Un periodo corto trabajando de verdad.";
            default -> "Una persona decide, mirando todo el recorrido.";
        };
    }

    private static String laPrueba(Vacante vacante, String modalidad) {
        if (esCuestionario(vacante)) {
            return "Preguntas sobre este puesto, que se responden escribiendo.";
        }
        if (LoQueLeToca.CRONOMETRADA.equals(modalidad)) {
            return "Un encargo real del puesto, con tiempo medido.";
        }
        if (LoQueLeToca.PLAZO_ABIERTO.equals(modalidad)) {
            return "Un encargo real del puesto, con un plazo en días para entregarlo.";
        }
        return "Un encargo real del puesto.";
    }

    private static boolean esCuestionario(Vacante vacante) {
        return vacante != null
                && LoQueLeToca.CUESTIONARIO_TECNICO.equals(vacante.getInstrumentoEtapaTecnica());
    }
}
