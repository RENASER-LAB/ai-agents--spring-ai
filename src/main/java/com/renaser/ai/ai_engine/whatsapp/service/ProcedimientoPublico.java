package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Etapa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Las etapas públicas de una vacante, en el orden de SU recorrido (V95), con su nombre y su
 * explicación (V76, regla 6 de la spec).
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

    public static final String HECHA = "HECHA";
    public static final String ACTUAL = "ACTUAL";
    public static final String PREVISTA = "PREVISTA";

    static final int LARGO_DEL_NOMBRE = 60;
    static final int LARGO_DE_LA_EXPLICACION = 300;

    private ProcedimientoPublico() {}

    /**
     * El procedimiento de una postulación en marcha: las etapas del recorrido de SU vacante
     * (V95), en su orden. Antes de la etapa actual, {@code HECHA}; la suya, {@code ACTUAL};
     * después, {@code PREVISTA} (lo previsto, no una promesa de avanzar). El {@code orden} de cada
     * etapa es su posición en el recorrido (1, 2, 3…), no el del catálogo: el bot ordena por él.
     *
     * @param recorrido las etapas de la vacante, en orden
     * @param etapaActual la etapa del estado, o nulo si aún no entró en ninguna
     * @param modalidadDeLaPrueba {@code CRONOMETRADA}, {@code PLAZO_ABIERTO} o nulo si no se sabe
     */
    public static List<Etapa> enMarcha(Vacante vacante, List<String> recorrido, String etapaActual,
                                       String modalidadDeLaPrueba) {
        int actual = etapaActual == null ? -1 : recorrido.indexOf(etapaActual);
        List<Etapa> etapas = new ArrayList<>();
        for (int i = 0; i < recorrido.size(); i++) {
            etapas.add(etapa(recorrido.get(i), i + 1, comparada(i, actual), vacante, modalidadDeLaPrueba));
        }
        return etapas;
    }

    /**
     * El de una postulación terminada: solo las etapas del recorrido a las que llegó, todas
     * {@code HECHA}, con su posición en el recorrido. Las que no alcanzó no se presentan como
     * previstas: ese proceso ya no sigue.
     */
    public static List<Etapa> terminado(Vacante vacante, List<String> recorrido,
                                        Collection<String> alcanzadas, String modalidadDeLaPrueba) {
        List<Etapa> etapas = new ArrayList<>();
        for (int i = 0; i < recorrido.size(); i++) {
            if (alcanzadas.contains(recorrido.get(i))) {
                etapas.add(etapa(recorrido.get(i), i + 1, HECHA, vacante, modalidadDeLaPrueba));
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
            case DECISION -> "La decisión final";
            // Una etapa nueva necesita aquí su nombre para el bot (ver «Cómo agregar una etapa»).
            default -> "Otra etapa del proceso";
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
            case DECISION -> "Una persona decide, mirando todo el recorrido.";
            default -> "Una etapa más del proceso de esta vacante.";
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
