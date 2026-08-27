package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Function;

/**
 * Qué filas del panel alcanza quien pregunta, según el alcance de su permiso.
 *
 * <p><b>Por qué existe.</b> «Con {@code SUS_VACANTES} solo alcanzas lo de las vacantes que
 * diriges» estaba escrito catorce veces en nueve clases de siete módulos, byte a byte igual.
 * Mientras la regla no cambiara daba lo mismo; el día que una vacante tenga dos responsables,
 * o que el vínculo pase por el área, hay que acertar en las catorce, y basta olvidar una para
 * que alguien siga viendo candidatos que no le tocan. Sin que nada falle y sin que ninguna
 * prueba lo diga: por eso es la clase de error que se descubre tarde y por casualidad.
 *
 * <p><b>El permiso llega por parámetro y no escrito aquí dentro</b>, y no es un capricho: un
 * mismo servicio guarda acciones distintas con permisos distintos, y el alcance que vale es el
 * del permiso que se está ejerciendo. Mirar siempre el mismo abre una escalada silenciosa —un
 * rol con {@code ajustar_nota} acotado pero {@code ver_embudo} libre acabaría ajustando notas
 * de una convocatoria ajena—.
 *
 * <p><b>Lo que no alcanza responde 404 y no 403</b>, con el mismo texto que si no existiera. Un
 * 403 confirmaría que esa postulación existe, y de ahí se sondea qué ids hay al otro lado.
 *
 * <p><b>Esta es la regla del panel</b>, y solo del panel. Un endpoint del portal no debe usarla:
 * al candidato se le siembra {@code abrir_ficha_candidato} con alcance {@code PROPIO}, y aquí
 * {@code PROPIO} no alcanza a nadie —lo que le daría un 404 sobre su propia ficha—. En el portal
 * la pregunta es otra: si la postulación es suya, y eso lo comprueba cada servicio del portal
 * contra el usuario de la fila.
 */
@Service
@RequiredArgsConstructor
public class AlcanceSobreLaVacante {

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final Permisos permisos;

    /**
     * La postulación de esta empresa, si el alcance del permiso llega a ella.
     *
     * @throws ResourceNotFoundException si no existe, si es de otra empresa, o si el alcance no
     *                                   la alcanza — los tres con el mismo texto, a propósito
     */
    public Postulacion laPostulacionVisible(ContextoUsuario quien, Long postulacionId,
                                            String permiso) {
        Postulacion p = postulaciones.findByIdAndOrganizacionId(postulacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        if (!alcanzaA(quien, permisos.alcanceDe(permiso), p)) {
            throw new ResourceNotFoundException("Postulación", "id", postulacionId);
        }
        return p;
    }

    /** La vacante de esta empresa, si el alcance del permiso llega a ella. */
    public Vacante laVacanteVisible(ContextoUsuario quien, Long vacanteId, String permiso) {
        Vacante v = vacantes.findByIdAndOrganizacionId(vacanteId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
        if (!alcanza(quien, permisos.alcanceDe(permiso), Optional.of(v))) {
            throw new ResourceNotFoundException("Vacante", "id", vacanteId);
        }
        return v;
    }

    /**
     * Si este alcance llega a esa postulación, con la vacante ya cargada.
     *
     * <p>La vacante se pide por una función y no por id para que quien tenga una tanda pueda
     * pasar el mapa que ya cargó: preguntar por cada fila convierte una lista en una consulta
     * por elemento.
     */
    public boolean alcanzaA(ContextoUsuario quien, FiltroAlcance alcance, Postulacion p,
                            Function<Long, Optional<Vacante>> laVacante) {
        if (p == null) {
            return false;
        }
        // Solo se pregunta por la vacante cuando de verdad hace falta saber de quién es.
        return alcance.tipo() == FiltroAlcance.Tipo.SUS_VACANTES
                ? alcanza(quien, alcance, laVacante.apply(p.getVacanteId()))
                : alcanza(quien, alcance, Optional.empty());
    }

    /** Lo mismo para una postulación suelta, cuando no hay tanda con la que ir. */
    public boolean alcanzaA(ContextoUsuario quien, FiltroAlcance alcance, Postulacion p) {
        // findById literal y no una referencia a método: la regla de arquitectura que vigila
        // las búsquedas por id suelto recorre las llamadas, y una referencia se le escapa.
        return alcanzaA(quien, alcance, p, id -> vacantes.findById(id));
    }

    /**
     * El único sitio donde se decide qué significa que una vacante sea tuya.
     *
     * <p>Es un {@code switch} sobre el enum y no un {@code if}: si mañana aparece un cuarto
     * alcance, esto deja de compilar en vez de colarse por el «no es SUS_VACANTES, luego pasa»
     * —que es exactamente el fallo que esta clase viene a cerrar—.
     */
    private boolean alcanza(ContextoUsuario quien, FiltroAlcance alcance, Optional<Vacante> vacante) {
        return switch (alcance.tipo()) {
            case TODO -> true;
            case SUS_VACANTES -> vacante
                    .map(v -> quien.usuarioId().equals(v.getResponsableUsuarioId()))
                    .orElse(false);
            // De momento pasa, como pasaba en los catorce sitios de los que salió esto. Cambia
            // en el commit siguiente, que es donde tiene sus pruebas.
            case PROPIO -> true;
        };
    }
}
