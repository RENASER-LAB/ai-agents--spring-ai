package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.BusquedaCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.ResultadoBusquedaCv;
import com.renaser.ai.ai_engine.perfilintegral.repository.BusquedaCvRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ResultadoBusquedaCvRepository;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.vacante.entity.LineaBusquedaCv;
import com.renaser.ai.ai_engine.vacante.repository.LineaBusquedaCvRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lo que dice la base de «Qué buscar en el CV» (V80): las líneas vigentes de una vacante, lo que
 * se encontró en el CV de cada persona y cuánto pesa en la nota del CV.
 *
 * <p>Solo lee lo guardado, y por eso no depende de la cola: en qué punto va el trabajo de cada
 * persona (pendiente, buscando, fallido) lo pregunta quien lo pinta a
 * {@code ColaCalificacionIa.comoVaLaBusqueda}. Lo usan la nota del Perfil Integral, lo que recibe
 * {@code POTENCIAL_RIESGO}, el ranking, el Excel y la confirmación de editar las líneas.
 *
 * <p><b>Solo cuenta lo de las líneas vigentes.</b> Una búsqueda guardada con una versión anterior
 * de las líneas no existe para nadie: editar las archiva, y si alguna se cuela (un resultado que
 * llegó justo al cambiarlas) se descarta al leer.
 */
@Service
@RequiredArgsConstructor
public class LoBuscadoEnElCv {

    private final LineaBusquedaCvRepository lineas;
    private final BusquedaCvRepository busquedas;
    private final ResultadoBusquedaCvRepository resultados;
    private final VersionPesosRepository versionesPesos;

    /** Una línea vigente: su id, su puesto en la lista, su texto y su versión. */
    public record LineaVigente(Long id, int orden, String texto, int version) {
    }

    /** Lo que se encontró de una línea en el CV de una persona. */
    public record Encontrado(String estado, String frase, String nota, String pregunta) {
    }

    /**
     * La búsqueda de una persona con las líneas vigentes: TERMINADA (con lo encontrado por id de
     * línea) o SIN_TEXTO (con su motivo).
     */
    public record DeUnaPersona(String estado, String motivo, Map<Long, Encontrado> porLinea) {

        public boolean terminada() {
            return BusquedaCv.TERMINADA.equals(estado);
        }

        public boolean sinTexto() {
            return BusquedaCv.SIN_TEXTO.equals(estado);
        }
    }

    /** Las líneas vigentes de la vacante, en su orden. Vacía si no busca nada. */
    @Transactional(readOnly = true)
    public List<LineaVigente> vigentes(Long vacanteId) {
        if (vacanteId == null) {
            return List.of();
        }
        return lineas.findByVacanteIdAndArchivadaEnIsNullOrderByOrdenAsc(vacanteId).stream()
                .map(LoBuscadoEnElCv::comoVigente)
                .toList();
    }

    /** Los textos vigentes de varias vacantes de una vez. Falta la entrada de la que no busca nada. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> textosVigentesDe(Collection<Long> vacanteIds) {
        if (vacanteIds == null || vacanteIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> salida = new LinkedHashMap<>();
        for (LineaBusquedaCv linea : lineas
                .findByVacanteIdInAndArchivadaEnIsNullOrderByVacanteIdAscOrdenAsc(vacanteIds)) {
            salida.computeIfAbsent(linea.getVacanteId(), id -> new java.util.ArrayList<>())
                    .add(linea.getTexto());
        }
        return salida;
    }

    /**
     * Cuánto pesa «Qué buscar en el CV» dentro de la nota del CV con esa versión de pesos, en %.
     * Una versión que no se encuentra —no debería pasar: toda vacante tiene la suya— pesa lo de
     * siempre.
     */
    @Transactional(readOnly = true)
    public BigDecimal pesoDe(Long versionPesosId) {
        if (versionPesosId == null) {
            return VersionPesos.PESO_BUSQUEDA_CV_POR_DEFECTO;
        }
        return versionesPesos.findById(versionPesosId)
                .map(VersionPesos::getPesoBusquedaCv)
                .orElse(VersionPesos.PESO_BUSQUEDA_CV_POR_DEFECTO);
    }

    /**
     * La búsqueda de cada persona con las líneas vigentes, de una sola vez para la tanda. Falta la
     * entrada de quien no tiene una guardada con esas líneas: pendiente, buscando o fallida, que
     * lo dice la cola.
     */
    @Transactional(readOnly = true)
    public Map<Long, DeUnaPersona> deLaTanda(List<LineaVigente> vigentes,
                                              Collection<Long> postulacionIds) {
        if (vigentes.isEmpty() || postulacionIds == null || postulacionIds.isEmpty()) {
            return Map.of();
        }
        int version = vigentes.get(0).version();
        Map<Long, BusquedaCv> vivas = busquedas.findByPostulacionIdInAndArchivadaEnIsNull(postulacionIds)
                .stream()
                .filter(b -> b.getVersionLineas() != null && b.getVersionLineas() == version)
                .collect(Collectors.toMap(BusquedaCv::getPostulacionId, b -> b, (a, b) -> a));
        if (vivas.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, Encontrado>> encontradoPorPersona = new HashMap<>();
        for (ResultadoBusquedaCv r : resultados.findByPostulacionIdInAndArchivadoEnIsNull(vivas.keySet())) {
            BusquedaCv suya = vivas.get(r.getPostulacionId());
            if (suya == null || !suya.getId().equals(r.getBusquedaCvId())) {
                continue;
            }
            encontradoPorPersona.computeIfAbsent(r.getPostulacionId(), id -> new HashMap<>())
                    .put(r.getLineaBusquedaCvId(),
                            new Encontrado(r.getEstado(), r.getFraseCv(), r.getNota(), r.getPregunta()));
        }
        Map<Long, DeUnaPersona> salida = new HashMap<>();
        vivas.forEach((postulacionId, b) -> salida.put(postulacionId, new DeUnaPersona(
                b.getEstado(), b.getMotivo(),
                encontradoPorPersona.getOrDefault(postulacionId, Map.of()))));
        return salida;
    }

    /** La de una persona, con las líneas vigentes de su vacante; nula si no hay una guardada. */
    @Transactional(readOnly = true)
    public DeUnaPersona deUnaPersona(Long postulacionId, Long vacanteId) {
        List<LineaVigente> vigentes = vigentes(vacanteId);
        return deLaTanda(vigentes, List.of(postulacionId)).get(postulacionId);
    }

    /**
     * Lo buscado de esa persona, sobre 100, o nulo si su vacante no busca nada o su búsqueda con
     * las líneas vigentes no terminó (pendiente, en curso, fallida o con el CV sin texto).
     */
    @Transactional(readOnly = true)
    public BigDecimal notaDe(Long postulacionId, Long vacanteId) {
        List<LineaVigente> vigentes = vigentes(vacanteId);
        DeUnaPersona suya = deLaTanda(vigentes, List.of(postulacionId)).get(postulacionId);
        return notaDeLoBuscado(vigentes, suya);
    }

    /**
     * Lo buscado de una persona con las líneas vigentes, sobre 100: nulo si su búsqueda no
     * terminó. Una línea sin resultado cuenta como «No aparece».
     */
    public static BigDecimal notaDeLoBuscado(List<LineaVigente> vigentes, DeUnaPersona suya) {
        if (vigentes.isEmpty() || suya == null || !suya.terminada()) {
            return null;
        }
        return NotaDelCurriculum.deLoBuscado(vigentes.stream()
                .map(l -> {
                    Encontrado e = suya.porLinea().get(l.id());
                    return e == null ? ResultadoBusquedaCv.NO_APARECE : e.estado();
                })
                .toList());
    }

    /**
     * Si hay que buscar en el CV de esta persona: su vacante tiene líneas y todavía no hay una
     * búsqueda guardada con las vigentes —ni terminada ni marcada como CV sin texto—.
     */
    @Transactional(readOnly = true)
    public boolean hayQueBuscar(Long postulacionId, Long vacanteId) {
        List<LineaVigente> vigentes = vigentes(vacanteId);
        return !vigentes.isEmpty()
                && !deLaTanda(vigentes, List.of(postulacionId)).containsKey(postulacionId);
    }

    /**
     * Lo que pasa con lo encontrado cuando las líneas de la vacante cambian (V80), dentro de la
     * transacción que guarda las nuevas: lo buscado con las de antes se archiva —no se borra— y
     * el «CV sin texto» se arrastra a la versión nueva, porque el archivo sigue sin texto y
     * volver a mirarlo no cambiaría nada.
     *
     * @param antes          las líneas vigentes justo antes del cambio
     * @param versionNueva   la versión de las líneas nuevas, o nula si se quitaron todas
     * @param postulacionIds las personas de la vacante
     * @return a quién se le archivó una búsqueda TERMINADA: su nota del CV llevaba lo buscado y
     *         hay que recalcularla sin ello
     */
    @Transactional
    public List<Long> archivarAlCambiarLasLineas(List<LineaVigente> antes, Integer versionNueva,
                                                 Collection<Long> postulacionIds) {
        if (postulacionIds == null || postulacionIds.isEmpty()) {
            return List.of();
        }
        Map<Long, DeUnaPersona> deAntes = deLaTanda(antes, postulacionIds);
        Instant ahora = Instant.now();
        resultados.archivarLosVivos(postulacionIds, ahora);
        busquedas.archivarLasVivas(postulacionIds, ahora);
        List<Long> conNotaQueCambia = new java.util.ArrayList<>();
        deAntes.forEach((postulacionId, suya) -> {
            if (suya.terminada()) {
                conNotaQueCambia.add(postulacionId);
            } else if (suya.sinTexto() && versionNueva != null) {
                busquedas.save(BusquedaCv.builder()
                        .postulacionId(postulacionId)
                        .versionLineas(versionNueva)
                        .estado(BusquedaCv.SIN_TEXTO)
                        .motivo(suya.motivo())
                        .creadoEn(ahora)
                        .build());
            }
        });
        busquedas.flush();
        return conNotaQueCambia;
    }

    private static LineaVigente comoVigente(LineaBusquedaCv linea) {
        return new LineaVigente(linea.getId(), linea.getOrden(), linea.getTexto(),
                linea.getVersionLineas());
    }
}
