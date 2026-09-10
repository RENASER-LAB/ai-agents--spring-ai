package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.service.ServicioEvaluacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.prueba.service.ServicioPrueba;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Crearle al candidato lo que va a rendir en la etapa técnica.
 *
 * <p>Es un solo gesto —«entrar a la prueba»— que se hace de dos maneras según lo que la
 * vacante haya elegido: el intento de la prueba del puesto de siempre, o el cuestionario
 * técnico CAZATALENTOS. Uno de los dos, nunca los dos, y el que no se usa ni se mira.
 *
 * <p><b>Por qué es una clase y no un trozo del panel.</b> Hasta la V53 esto vivía dentro de
 * {@code ServicioPostulacionesPanelImpl.confirmarAvance} y tomaba la organización de quien
 * pulsaba el botón. Ahora hay un segundo camino —el pase automático— que <b>no tiene a
 * nadie detrás</b>: no hay usuario del que sacarla. Copiar el bloque habría funcionado en
 * los tests, que corren con una sola empresa, y habría metido a un candidato en la etapa
 * técnica de la organización equivocada en cuanto hubiera dos.
 *
 * <p>Por eso la organización sale siempre de la <b>postulación</b>, que es de quien de
 * verdad es el dato.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntradaEtapaTecnica {

    /** La vacante rinde el cuestionario CAZATALENTOS y no la prueba del puesto. */
    public static final String CUESTIONARIO_TECNICO = "CUESTIONARIO_TECNICO";

    private final PostulacionRepository postulaciones;
    private final ServicioPrueba prueba;
    private final ServicioEvaluacion evaluaciones;

    /**
     * Si esta vacante tiene hoy con qué llenar su etapa técnica.
     *
     * <p><b>Responde, no lanza, y esa es toda su razón de ser.</b> Los dos caminos de
     * dentro sí se plantan cuando les falta el instrumento, y para una persona que pulsa
     * «confirmar» eso está bien: se ha equivocado y tiene que enterarse. Pero el pase
     * automático no puede plantarse — reventaría a mitad de la calificación, dejando el
     * trabajo por fallido y pagando el modelo otra vez al reintentarlo. Lo que tiene que
     * hacer es no avanzar y dejar la postulación esperando a alguien, que es exactamente
     * lo que pasaba antes de que existiera el automático.
     *
     * <p>Solo se mira lo que esta clase necesita para crear. Que el cuestionario esté
     * publicado lo comprueba quien lo crea, y por eso el pase automático se protege además
     * con el {@code try/catch} de su listener.
     */
    public boolean hayInstrumento(Vacante vacante) {
        if (CUESTIONARIO_TECNICO.equals(vacante.getInstrumentoEtapaTecnica())) {
            return true;
        }
        return vacante.getVersionPlantillaPruebaId() != null;
    }

    /**
     * Crea el intento de la prueba, o el cuestionario técnico, según lo que rinda la vacante.
     *
     * <p>La versión queda fijada aquí y no cambia aunque después se publique otra (RF-90):
     * es el mismo patrón que la evaluación del banco, que se ata al postular.
     *
     * <p><b>Es idempotente por los dos lados, y hay que mantenerlo así.</b> El intento de la
     * prueba no puede duplicarse porque su tabla lo impide con una clave única. El
     * cuestionario técnico <b>no tiene esa red</b>: la columna admite cualquier id, así que
     * la regla la pone el {@code if} de aquí dentro. Estaba antes en el llamador, y ahí ya
     * no vale: con dos caminos, uno de ellos se olvidaría y crearía un segundo examen
     * dejando el primero —con sus respuestas y sus notas— sin dueño.
     */
    public void crearAlEntrar(Postulacion postulacion, Vacante vacante) {
        Long organizacionId = postulacion.getOrganizacionId();

        if (CUESTIONARIO_TECNICO.equals(vacante.getInstrumentoEtapaTecnica())) {
            if (postulacion.getEvaluacionTecnicaId() != null) {
                return;
            }
            // Sin los minutos: se los pregunta el examen a su vacante cuando el candidato lo
            // abre, no ahora. Así, corregirlos entre este avance y esa apertura sigue
            // alcanzándole.
            postulacion.setEvaluacionTecnicaId(evaluaciones.crearTecnicaAlEntrar(
                    organizacionId, postulacion.getUsuarioId(), vacante.getId()));
            postulaciones.save(postulacion);
            return;
        }

        if (vacante.getVersionPlantillaPruebaId() == null) {
            throw new IllegalStateException(
                    "Esta vacante no tiene plantilla de prueba asignada: no se puede avanzar");
        }
        prueba.crearAlEntrar(organizacionId, postulacion.getId(),
                vacante.getVersionPlantillaPruebaId(), vacante.getPruebaCierraEn());
    }
}
