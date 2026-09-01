package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * La administración de la prueba del puesto: plantillas, versiones, variantes del
 * cambio inesperado, el catálogo de preguntas, los entregables que se piden y la
 * rúbrica.
 *
 * <p>Mismo patrón que {@code ServicioBancoPreguntas}: una versión nace en {@code BORRADOR},
 * se arma con los métodos {@code agregar*}, y {@code publicarVersion} la congela — desde ese
 * momento es inmutable y quien ya la esté rindiendo queda atado a ella (RF-90).
 *
 * <p><b>Mientras está en borrador se corrige y se quita.</b> Hasta ahora solo se podía
 * añadir, y eso convertía cualquier error de tecleo en un callejón sin salida: un criterio
 * de 40 puntos puesto por equivocación dejaba la rúbrica sumando 140, {@code publicarVersion}
 * exige 100 exactos, y no había forma de quitarlo. Con las plantillas cargadas por script no
 * molestaba —el script acierta a la primera—; con una persona tecleando, sí.
 *
 * <p><b>No hay «despublicar», y no es un olvido.</b> Publicar es un acto con nombre y fecha
 * en la auditoría, y desde ese momento la versión puede regir una vacante: {@code
 * ServicioVacantesPanelImpl.asignarPlantillaPrueba} solo acepta versiones {@code PUBLICADA},
 * y {@code exigirVaraQuieta} impide cambiársela a una vacante que ya tiene postulantes.
 * Devolver una versión a borrador dejaría a esa vacante apuntando a algo que ya no se puede
 * usar y que tampoco se puede reemplazar —un estado sin salida— y además abriría la puerta
 * a editar el enunciado de un examen que alguien está rindiendo. La salida a un error en
 * una versión publicada es {@link #crearVersion}: numera sola con la siguiente y la vacante
 * apunta a la nueva.
 */
public interface ServicioPlantillaPrueba {

    Long crearPlantilla(ContextoUsuario quien, CrearPlantilla datos);
    List<PlantillaResponse> listarPlantillas(ContextoUsuario quien);

    Long crearVersion(ContextoUsuario quien, Long plantillaId, CrearVersion datos);
    void publicarVersion(ContextoUsuario quien, Long versionId);
    VersionCompleta verVersion(ContextoUsuario quien, Long versionId);

    /**
     * Las versiones de una plantilla, de la más nueva a la más vieja.
     *
     * <p>Faltaba, y su ausencia costaba caro fuera: solo se podía pedir una versión por su
     * id suelto, así que quien necesitara «las versiones de esta prueba» —el desplegable que
     * elige qué rinde una vacante— no tenía más remedio que <b>tantear ids</b> hasta dar con
     * un hueco. Los ids son una secuencia de toda la plataforma, así que el tanteo dejaba una
     * ristra de 404 legítimos en cada carga y podía no encontrar ninguna versión si las de
     * esa empresa vivían altas.
     *
     * <p>Se ve lo mismo que se ve en {@link #verVersion}, y por la misma puerta
     * ({@code laPlantillaVisible}): una empresa que no personalizó sus instrumentos ve las
     * versiones de la plataforma. Ver no es poder tocar — editar sigue exigiendo que la
     * plantilla sea suya.
     *
     * <p>Vienen <b>todas</b>, borradores incluidos: quien compone una prueba necesita ver el
     * borrador que está armando, y quien elige para una vacante necesita saber que existe
     * pero aún no se puede usar. Filtrar aquí por PUBLICADA escondería la mitad del ciclo de
     * vida; quien solo quiera las usables mira el {@code estado}.
     */
    List<VersionResponse> listarVersiones(ContextoUsuario quien, Long plantillaId);

    Long agregarVariante(ContextoUsuario quien, Long versionId, CrearVariante datos);

    Long crearPreguntaCatalogo(ContextoUsuario quien, CrearPreguntaPrueba datos);
    List<PreguntaPruebaResponse> listarPreguntasCatalogo(String tipo);
    void elegirPregunta(ContextoUsuario quien, Long versionId, ElegirPregunta datos);

    Long agregarEntregableRequerido(ContextoUsuario quien, Long versionId, CrearEntregableRequerido datos);

    Long agregarCriterioRubrica(ContextoUsuario quien, Long versionId, CrearCriterioRubrica datos);

    // ---------- Corregir y quitar, solo en borrador ----------
    // Todo lo de aquí abajo pasa por la misma puerta: la versión tiene que estar en
    // BORRADOR y ser de quien pregunta. Una PUBLICADA responde 409.

    void actualizarVersion(ContextoUsuario quien, Long versionId, CrearVersion datos);

    /**
     * Sube el enunciado de la prueba como archivo y lo deja enlazado en la versión.
     *
     * <p>⚠️ <b>El archivo es el ENUNCIADO, no la prueba entera.</b> De un PDF no sale
     * ninguna nota: subirlo no crea preguntas, ni entregables, ni criterios de rúbrica, y
     * {@link #publicarVersion} sigue exigiendo exactamente lo mismo que antes —la cuota de
     * preguntas y los 100 puntos—. Es el papel que el candidato lee para saber qué tiene que
     * hacer, y lo único que cambia es que ahora se puede subir desde el panel en vez de
     * meterlo por SQL. Quien crea que con esto ya tiene la prueba montada se encontrará con
     * que no se puede publicar, y hará bien.
     *
     * <p>Solo sobre una versión en BORRADOR, como todo lo demás de aquí arriba: el enunciado
     * es parte del examen, y el examen de quien ya lo está rindiendo no se toca.
     */
    ConsignaResponse subirConsigna(ContextoUsuario quien, Long versionId, MultipartFile archivo);

    void quitarPregunta(ContextoUsuario quien, Long versionId, Long preguntaPruebaId);

    void actualizarEntregableRequerido(ContextoUsuario quien, Long entregableId,
                                       CrearEntregableRequerido datos);
    void quitarEntregableRequerido(ContextoUsuario quien, Long entregableId);

    void actualizarCriterioRubrica(ContextoUsuario quien, Long criterioId, CrearCriterioRubrica datos);
    void quitarCriterioRubrica(ContextoUsuario quien, Long criterioId);

    void actualizarVariante(ContextoUsuario quien, Long varianteId, CrearVariante datos);
    void quitarVariante(ContextoUsuario quien, Long varianteId);

    void reordenarPreguntas(ContextoUsuario quien, Long versionId, ReordenarElementos datos);
    void reordenarEntregables(ContextoUsuario quien, Long versionId, ReordenarElementos datos);
    void reordenarVariantes(ContextoUsuario quien, Long versionId, ReordenarElementos datos);
    void reordenarRubrica(ContextoUsuario quien, Long versionId, ReordenarElementos datos);
}
