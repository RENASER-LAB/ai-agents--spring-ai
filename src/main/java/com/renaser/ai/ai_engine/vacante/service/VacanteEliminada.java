package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

/**
 * Una vacante eliminada ya no existe para nadie.
 *
 * <p><b>Por qué no se parece a {@link VacanteArchivada}.</b> Una archivada se lee entera y no
 * se toca: contesta 409 a quien intenta moverla, porque está ahí y el estado actual no admite
 * ese cambio. Una eliminada contesta <b>404</b>, y con el mismo texto que si el id nunca
 * hubiera existido: el panel no la lista, el portal no la enseña y su detalle no se abre.
 * Decir «existe pero no puedes» sobre algo que el equipo acaba de retirar sería contradecir a
 * la pantalla que ya no la enseña.
 *
 * <p><b>Casi nadie tiene que llamar a esto</b>, y es lo bueno del diseño: la puerta normal del
 * panel es {@code findByIdAndOrganizacionIdAndEliminadaEnIsNull}, así que una eliminada
 * simplemente no se encuentra y el 404 sale solo. Esta clase es para los dos casos en que la
 * fila ya está cargada —o llega por una postulación— y hay que preguntarlo explícitamente.
 *
 * <p>Lo que <b>no</b> decide: quién puede eliminar (eso es {@code eliminar_vacante} y su
 * alcance) ni qué arrastra la eliminación (eso vive en {@code ServicioVacantesPanelImpl},
 * porque necesita cerrar postulaciones, avisar y liberar la solicitud).
 */
public final class VacanteEliminada {

    private VacanteEliminada() {}

    /** Si esta vacante está eliminada, para quien pregunta no existe. */
    public static void exigirQueSigaExistiendo(Vacante vacante) {
        if (vacante != null && vacante.getEliminadaEn() != null) {
            throw new ResourceNotFoundException("Vacante", "id", vacante.getId());
        }
    }

    /**
     * Lo mismo cuando quien pregunta no tiene la vacante en la mano.
     *
     * <p>Es el caso de las acciones sobre una postulación: lo que llega es la postulación, y
     * de su vacante solo hace falta saber una cosa. Preguntarla con un {@code exists} evita
     * cargar la fila entera —y evita de paso el {@code findById} suelto que la regla de
     * arquitectura vigila.
     */
    public static void exigirQueSigaExistiendo(boolean eliminada, Long vacanteId) {
        if (eliminada) {
            throw new ResourceNotFoundException("Vacante", "id", vacanteId);
        }
    }
}
