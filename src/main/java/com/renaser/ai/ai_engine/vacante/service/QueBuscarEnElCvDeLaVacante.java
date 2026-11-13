package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.LineaVigente;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.LineaBusquedaCv;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.LineaBusquedaCvRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Las líneas de «Qué buscar en el CV» de una vacante (V80): guardarlas al crear y al editar, y lo
 * que arrastra cambiarlas con candidatos dentro.
 *
 * <p><b>Van aparte de los textos públicos</b>: no cuentan como cambio visible (no se avisa a
 * nadie), no tocan la revisión del contexto de WhatsApp (V77) y un formulario que no las manda no
 * las borra. Por eso {@code CambiosDeLaVacante} ni las ve.
 *
 * <p><b>Cambiarlas con CV ya calificados pide confirmación</b> y, en una sola operación, con la
 * vacante bloqueada —el mismo cerrojo que el cambio de pesos—: archiva las líneas y lo encontrado
 * con ellas, deja las nuevas con la versión siguiente, recalcula sin lo buscado la nota de quien
 * lo tenía (sin eventos ni mover a nadie) y encola la búsqueda de cada uno como rebúsqueda.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueBuscarEnElCvDeLaVacante {

    private static final String ETAPA_DEL_CV = "PERFIL_INTEGRAL";

    private final LineaBusquedaCvRepository lineas;
    private final VacanteRepository vacantes;
    private final PostulacionRepository postulaciones;
    private final NotaCriterioRepository notasCriterio;
    private final CriterioRepository criterios;
    private final LoBuscadoEnElCv loBuscado;
    private final PuenteCalificacionIa puente;
    private final ColaCalificacionIa cola;
    private final ServicioAuditoria auditoria;

    /** Cómo acabó un guardado de las líneas. */
    public record Cambio(boolean cambiaron, int cvsABuscar) {
        public static final Cambio SIN_CAMBIOS = new Cambio(false, 0);
    }

    /** Las líneas vigentes de una vacante, en su orden. */
    public List<String> vigentes(Long vacanteId) {
        return loBuscado.vigentes(vacanteId).stream().map(LineaVigente::texto).toList();
    }

    /** Las vigentes de varias vacantes, para la lista del panel. Falta la de la que no busca nada. */
    public Map<Long, List<String>> vigentesDe(Collection<Long> vacanteIds) {
        return loBuscado.textosVigentesDe(vacanteIds);
    }

    /** Las de una vacante recién creada: sin nadie dentro, se guardan sin preguntar. */
    @Transactional
    public void alCrear(Long vacanteId, List<String> recibidas) {
        List<String> nuevas = ReglasQueBuscarEnElCv.lasQueSeGuardan(recibidas);
        if (!nuevas.isEmpty()) {
            escribir(vacanteId, nuevas, Instant.now());
        }
    }

    /**
     * Cuántas personas habría que volver a buscar si cambian las líneas: aquellas cuyo CV ya se
     * calificó o se está calificando, y las que ya tienen una búsqueda hecha, en curso o fallida.
     * No cuentan las de «CV sin texto»: con ellas no hay nada que volver a mirar.
     */
    @Transactional(readOnly = true)
    public List<Long> paraVolverABuscar(Long vacanteId) {
        List<Long> ids = postulaciones.findByVacanteIdOrderByCreadoEnDesc(vacanteId).stream()
                .map(Postulacion::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Set<Long> delCurriculum = criterios
                .findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(ETAPA_DEL_CV).stream()
                .map(Criterio::getId).collect(Collectors.toSet());
        Set<Long> conElCvCalificado = notasCriterio.findByPostulacionIdIn(ids).stream()
                .filter(n -> delCurriculum.contains(n.getCriterioId()) && n.getPuntaje() != null)
                .map(NotaCriterio::getPostulacionId)
                .collect(Collectors.toSet());
        Map<Long, ColaCalificacionIa.Estado> retratos = cola.estadoDe(ids);
        Map<Long, String> busquedas = cola.comoVaLaBusqueda(ids);
        Map<Long, LoBuscadoEnElCv.DeUnaPersona> guardadas =
                loBuscado.deLaTanda(loBuscado.vigentes(vacanteId), ids);
        return ids.stream()
                .filter(id -> {
                    LoBuscadoEnElCv.DeUnaPersona suya = guardadas.get(id);
                    if (suya != null && suya.sinTexto()) {
                        return false;
                    }
                    ColaCalificacionIa.Estado retrato = retratos.get(id);
                    boolean calificandose = retrato != null && "EN_CURSO".equals(retrato.comoVa());
                    return conElCvCalificado.contains(id) || calificandose
                            || busquedas.containsKey(id) || suya != null;
                })
                .toList();
    }

    /**
     * Guarda las líneas de una vacante que se edita.
     *
     * @param recibidas  las que mandó el formulario; nulas = no las tocó, y no cambia nada
     * @param confirmado si quien guarda ya confirmó volver a buscar en los CV calificados
     * @throws IllegalArgumentException (400) si alguna línea no se puede buscar o es muy larga
     * @throws IllegalStateException    (409) si cambian, hay CV calificados y no se confirmó
     */
    @Transactional
    public Cambio alEditar(ContextoUsuario quien, Vacante vacante, List<String> recibidas,
                           boolean confirmado) {
        if (recibidas == null) {
            return Cambio.SIN_CAMBIOS;
        }
        List<String> nuevas = ReglasQueBuscarEnElCv.lasQueSeGuardan(recibidas);
        if (nuevas.equals(vigentes(vacante.getId()))) {
            return Cambio.SIN_CAMBIOS;
        }
        // El mismo cerrojo que el cambio de pesos: dos cambios seguidos no se mezclan, y un
        // resultado de la IA que llegue ahora espera a que esto termine (y se descarta si es de
        // las líneas de antes).
        vacantes.bloquear(vacante.getId(), vacante.getOrganizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacante.getId()));
        List<LineaVigente> antes = loBuscado.vigentes(vacante.getId());
        List<String> textosDeAntes = antes.stream().map(LineaVigente::texto).toList();
        if (nuevas.equals(textosDeAntes)) {
            return Cambio.SIN_CAMBIOS;
        }
        List<Long> aBuscar = paraVolverABuscar(vacante.getId());
        if (!aBuscar.isEmpty() && !confirmado) {
            throw new IllegalStateException(aBuscar.size() == 1
                    ? "Hay 1 persona con el CV calificado: confirma antes de cambiar «Qué buscar "
                            + "en el CV», porque se vuelve a buscar en su CV."
                    : "Hay " + aBuscar.size() + " personas con el CV calificado: confirma antes de "
                            + "cambiar «Qué buscar en el CV», porque se vuelve a buscar en sus CV.");
        }

        Instant ahora = Instant.now();
        lineas.archivarLasVivas(vacante.getId(), ahora);
        Integer versionNueva = nuevas.isEmpty() ? null : escribir(vacante.getId(), nuevas, ahora);

        List<Long> todas = postulaciones.findByVacanteIdOrderByCreadoEnDesc(vacante.getId()).stream()
                .map(Postulacion::getId).toList();
        // Lo encontrado con las de antes se archiva; la nota de quien lo tenía vuelve a los
        // criterios generales, sin mover a nadie de etapa, hasta que llegue su nueva búsqueda.
        for (Long postulacionId : loBuscado.archivarAlCambiarLasLineas(antes, versionNueva, todas)) {
            puente.recalcularSiYaTieneNota(postulacionId);
        }
        int encoladas = 0;
        if (versionNueva != null) {
            for (Long postulacionId : aBuscar) {
                // Una viva no se duplica: ya leerá las líneas nuevas al empezar.
                cola.buscarDeNuevoEnElCv(postulacionId);
                encoladas++;
            }
        }

        Map<String, Object> deAntes = new LinkedHashMap<>();
        deAntes.put("lineas", textosDeAntes);
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("lineas", nuevas);
        despues.put("personasABuscar", encoladas);
        auditoria.registrar(quien.organizacionId(), quien, "editar_que_buscar_en_el_cv",
                "vacante", vacante.getId(), deAntes, despues, null);
        log.info("«Qué buscar en el CV» de la vacante {} cambió ({} → {} líneas): {} CV a buscar de "
                + "nuevo", vacante.getId(), textosDeAntes.size(), nuevas.size(), encoladas);
        return new Cambio(true, encoladas);
    }

    /** Escribe las líneas con la versión siguiente y la devuelve. */
    private int escribir(Long vacanteId, List<String> nuevas, Instant ahora) {
        int version = lineas.ultimaVersion(vacanteId) + 1;
        for (int i = 0; i < nuevas.size(); i++) {
            lineas.save(LineaBusquedaCv.builder()
                    .vacanteId(vacanteId)
                    .versionLineas(version)
                    .orden(i + 1)
                    .texto(nuevas.get(i))
                    .creadoEn(ahora)
                    .build());
        }
        lineas.flush();
        return version;
    }
}
