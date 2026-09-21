package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.vacante.entity.Vacante;

/**
 * Una vacante archivada se lee, no se toca.
 *
 * <p><b>Por qué existe.</b> Archivar retira la convocatoria de la lista de todos los días y
 * deja su proceso consultable: el ranking, las fichas, el historial y las descargas siguen
 * ahí. Lo que no sigue es la posibilidad de moverla —corregir el texto, cambiar el sueldo,
 * publicarla, reconfigurar sus instrumentos o reabrir una postulación—, y esa mitad no se
 * puede sostener escondiendo botones: el panel es un cliente más del API, y el formulario que
 * alguien dejó abierto antes de que otro la archivara sigue sabiendo la URL del PUT.
 *
 * <p><b>La regla está escrita una sola vez</b> porque hay quince entradas que tienen que
 * cumplirla, y quince copias significan que la número quince se olvida. Se falla con
 * {@link IllegalStateException}, que el manejador de errores traduce a 409: no es que la
 * petición esté mal escrita —lo estaría igual sobre una vacante viva—, es que el estado
 * actual no la admite.
 *
 * <p>Lo que <b>no</b> decide esta clase: quién puede archivar (eso es {@code cerrar_vacante} y
 * su alcance) ni cuándo se puede (eso es «cerrada y sin nadie en carrera», que vive en
 * {@code ServicioVacantesPanelImpl} porque necesita contar postulaciones).
 */
public final class VacanteArchivada {

    /**
     * Lo que se le dice a quien lo intenta.
     *
     * <p>Dice también la salida —desarchivar— porque un «no se puede» sin camino se lee como
     * una avería, y aquí el camino existe y es de una sola pulsación.
     */
    public static final String EXPLICACION =
            "Esta vacante está archivada: se puede consultar, pero no cambiar. Para volver a "
                    + "moverla, desarchívala desde «Vacantes archivadas»";

    private VacanteArchivada() {}

    /** Si esta vacante está archivada, no se toca. */
    public static void exigirQueNoLoEste(Vacante vacante) {
        exigirQueNoLoEste(vacante != null && vacante.getArchivadaEn() != null);
    }

    /**
     * Lo mismo cuando quien pregunta no tiene la vacante en la mano.
     *
     * <p>Es el caso de las acciones sobre una postulación: lo que llega es la postulación, y
     * de su vacante solo hace falta saber una cosa. Preguntarla con un {@code exists} evita
     * cargar la fila entera —y evita de paso el {@code findById} suelto que la regla de
     * arquitectura vigila.
     */
    public static void exigirQueNoLoEste(boolean archivada) {
        if (archivada) {
            throw new IllegalStateException(EXPLICACION);
        }
    }
}
