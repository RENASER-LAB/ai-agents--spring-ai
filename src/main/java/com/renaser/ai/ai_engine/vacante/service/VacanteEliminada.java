package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

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
 * simplemente no se encuentra y el 404 sale solo. Esta clase es para los casos en que la
 * fila ya está cargada, o en que se llega por una postulación —desde el panel por su id, o
 * desde el portal por su código— y hay que preguntarlo explícitamente.
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

    /**
     * El proceso de un candidato, pedido desde el portal por su código: si su vacante se
     * eliminó, ese proceso ya no existe.
     *
     * <p><b>Es la misma pregunta para todas las puertas del portal que entran por el código
     * de la postulación</b>: su detalle, la evaluación, el cuestionario técnico, la prueba del
     * puesto y la simulación. El enlace de cualquiera de ellas sigue en el correo de «tu
     * prueba está lista», en el historial del navegador y en los avisos viejos de la campana,
     * y basta con que una no lo pregunte para que alguien empiece —con reloj y todo— el
     * examen de una vacante que ya no existe. Por eso vive aquí y no copiada en cada
     * servicio.
     *
     * <p><b>Contesta como si la postulación no existiera</b> —«Postulación» por su código, y
     * no «Vacante» por su id—: el candidato no pidió la vacante sino su proceso, y ese 404 es
     * el que el portal traduce a «Esta vacante ya no está disponible». Es además el mismo
     * texto que «esa postulación no es tuya», así que tampoco dice nada de más a quien pruebe
     * códigos ajenos.
     *
     * <p>Se llama <b>después</b> de comprobar que la postulación es de quien pregunta, sobre
     * la fila ya cargada, y pregunta con un {@code exists} para no traerse la vacante entera.
     */
    public static void exigirQueSuProcesoSigaExistiendo(Postulacion suya,
                                                        VacanteRepository vacantes) {
        if (suya.getVacanteId() != null
                && vacantes.existsByIdAndEliminadaEnIsNotNull(suya.getVacanteId())) {
            throw new ResourceNotFoundException("Postulación", "código", suya.getUuid());
        }
    }
}
