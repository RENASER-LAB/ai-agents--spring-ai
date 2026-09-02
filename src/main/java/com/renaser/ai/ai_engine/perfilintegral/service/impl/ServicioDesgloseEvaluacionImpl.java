package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.AlineacionVista;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DesgloseEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RespuestaAbiertaVista;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.ResumenCerradas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PatronDelCuestionario;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.Senales;
import com.renaser.ai.ai_engine.perfilintegral.entity.Dimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ResultadoAlineacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion.ResumenCerrado;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioDesgloseEvaluacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioDesgloseEvaluacionImpl implements ServicioDesgloseEvaluacion {

    private final PostulacionRepository postulaciones;
    private final AlcanceSobreLaVacante alcance;
    private final EvaluacionRepository evaluaciones;
    private final RespuestaRepository respuestas;
    private final PreguntaRepository preguntas;
    private final NotaRespuestaRepository notasRespuesta;
    private final ResultadoAlineacionRepository alineaciones;
    private final com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository
            preguntaDimensiones;
    private final com.renaser.ai.ai_engine.perfilintegral.repository.DimensionRepository dimensiones;
    private final ServicioCalificacion calificacion;
    private final CalificacionCriterios calificacionCriterios;
    private final com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository notasEtapa;

    @Override
    @Transactional(readOnly = true)
    public DesgloseEvaluacion ver(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId);

        // Sin evaluación asignada no hay nada que abrir, y no es un error: la vacante
        // pudo publicarse con la evaluación del banco apagada.
        if (postulacion.getEvaluacionId() == null) {
            return new DesgloseEvaluacion(postulacionId, null, null, null,
                    new ResumenCerradas(BigDecimal.ZERO, 0), List.of(), List.of(), List.of());
        }

        Evaluacion evaluacion = evaluaciones.findById(postulacion.getEvaluacionId()).orElse(null);
        List<RespuestaAbiertaVista> abiertas = abiertasDe(postulacion.getEvaluacionId());
        ResumenCerrado cerrado = calificacion.resumenDeLoCerrado(postulacionId);

        return new DesgloseEvaluacion(
                postulacionId,
                evaluacion == null ? null : evaluacion.getEstado(),
                evaluacion == null ? null : evaluacion.getTerminadaEn(),
                notaDe(postulacion, cerrado, abiertas),
                new ResumenCerradas(cerrado.nota(), cerrado.preguntas()),
                abiertas,
                alineaciones.findByEvaluacionId(postulacion.getEvaluacionId()).stream()
                        .map(a -> new AlineacionVista(a.getBloque(), a.getSemaforo(),
                                a.getExplicacion()))
                        .toList(),
                patronesDe(abiertas));
    }

    /**
     * Las respuestas abiertas con su nota, leídas de lo persistido.
     *
     * <p>No recalcula nada: enseña exactamente lo que la IA guardó al calificar (y el
     * ajuste humano si lo hubo). Una respuesta todavía sin nota sale con los campos de
     * nota vacíos — está respondida y pendiente, que es distinto de no estar.
     */
    private List<RespuestaAbiertaVista> abiertasDe(Long evaluacionId) {
        // Orden por id de respuesta: el de contestarlas. Sin ordenar, Postgres
        // devuelve orden de heap y dos aperturas pintan la lista distinta.
        List<Respuesta> suyas = respuestas.findByEvaluacionId(evaluacionId).stream()
                .filter(r -> r.getOpcionId() == null && r.getTexto() != null
                        && !r.getTexto().isBlank())
                .sorted(java.util.Comparator.comparing(Respuesta::getId))
                .toList();
        if (suyas.isEmpty()) {
            return List.of();
        }

        Map<Long, Pregunta> preguntaPorId = preguntas
                .findByIdIn(suyas.stream().map(Respuesta::getPreguntaId).toList()).stream()
                .collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        Map<Long, NotaRespuesta> notaPorRespuesta = notasRespuesta
                .findByRespuestaIdIn(suyas.stream().map(Respuesta::getId).toList()).stream()
                .collect(Collectors.toMap(NotaRespuesta::getRespuestaId, Function.identity()));
        Map<Long, String> pilarPorPregunta = pilaresDe(preguntaPorId.keySet());
        Map<String, String> nombrePorPilar = dimensiones.findAllByOrderByOrden().stream()
                .collect(Collectors.toMap(Dimension::getCodigo, Dimension::getNombre,
                        (a, b) -> a));

        return suyas.stream()
                .filter(r -> {
                    Pregunta p = preguntaPorId.get(r.getPreguntaId());
                    return p != null && p.isEsPuntuable();
                })
                .map(r -> {
                    Pregunta p = preguntaPorId.get(r.getPreguntaId());
                    NotaRespuesta n = notaPorRespuesta.get(r.getId());
                    String pilar = pilarPorPregunta.get(p.getId());
                    return new RespuestaAbiertaVista(
                            p.getEnunciado(),
                            p.getTipo(),
                            r.getTexto(),
                            n == null ? null : n.getPuntaje(),
                            n == null ? null : n.getExplicacion(),
                            n == null ? null : n.getEvidenciaCitada(),
                            n == null ? null : n.getConfianza(),
                            n == null ? null : n.getMotivoAjuste(),
                            pilar == null ? null : nombrePorPilar.getOrDefault(pilar, pilar),
                            pilar,
                            senalesDe(n));
                })
                .toList();
    }

    /**
     * Qué pilar mide cada pregunta.
     *
     * <p>Solo los que empiezan por {@code PIL_}: una pregunta puede colgar además de alguna
     * de las 22 dimensiones del catálogo viejo, y esas no son pilares. Es el mismo filtro
     * que aplica {@code CalificacionCriterios} al ponderar, y tiene que ser el mismo: si
     * aquí se agrupara por una dimensión que allí no pondera, la ficha diría que una
     * respuesta sostiene algo que no mueve ninguna nota.
     *
     * <p>Si una pregunta tuviera dos pilares se queda con uno solo, y a propósito: agrupar
     * es repartir cada respuesta en un sitio. Hoy el banco no produce ese caso.
     */
    private Map<Long, String> pilaresDe(java.util.Collection<Long> preguntaIds) {
        return preguntaDimensiones.findByPreguntaIdIn(List.copyOf(preguntaIds)).stream()
                .filter(pd -> pd.getDimensionCodigo().startsWith("PIL_"))
                .collect(Collectors.toMap(
                        com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension
                                ::getPreguntaId,
                        com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension
                                ::getDimensionCodigo,
                        (a, b) -> a));
    }

    /**
     * Las cuatro señales de una nota, o nada.
     *
     * <p>⚠️ <b>Nada significa «este banco no las medía», no «no se cumplió ninguna».</b> Se
     * decide por {@code c1Episodio}: las cinco columnas se escriben juntas o no se escribe
     * ninguna, así que una sola basta para saber de qué banco viene la nota. Devolver cuatro
     * falsos aquí convertiría cada evaluación anterior a CAZATALENTOS en un cero de cuatro.
     */
    private Senales senalesDe(NotaRespuesta nota) {
        if (nota == null || nota.getC1Episodio() == null) return null;
        return new Senales(
                Boolean.TRUE.equals(nota.getC1Episodio()),
                Boolean.TRUE.equals(nota.getC2Autoria()),
                Boolean.TRUE.equals(nota.getC3Dato()),
                Boolean.TRUE.equals(nota.getC4Incomodidad()),
                nota.getCumpleSenalCero());
    }

    /**
     * La nota que se enseña arriba del desglose.
     *
     * <p>En un banco CRITERIOS es <b>el índice de la etapa</b> —pilares ponderados, el mismo
     * número con el que se ranquea—, leído de {@code nota_etapa}. Promediar los 0–4 a partes
     * iguales aquí enseñaría una nota distinta de la que decide, con los pesos de ítem y de
     * pilar perdidos. Vacía mientras el evaluador no haya terminado.
     *
     * <p>En el resto, la cuenta de siempre: vive en
     * {@link ServicioCalificacion#notaCombinada}, una sola para todos.
     */
    private BigDecimal notaDe(Postulacion postulacion, ResumenCerrado cerrado,
                              List<RespuestaAbiertaVista> abiertas) {
        if (CalificacionCriterios.METODO.equals(calificacionCriterios.metodoDe(postulacion))) {
            return notasEtapa
                    .findByPostulacionIdAndEtapaCodigo(postulacion.getId(), "PERFIL_INTEGRAL")
                    .map(com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa::getPuntaje)
                    .orElse(null);
        }
        return ServicioCalificacion.notaCombinada(cerrado,
                abiertas.stream().map(RespuestaAbiertaVista::puntaje).toList());
    }

    /**
     * El mismo control de visibilidad que el resto del panel: organización y alcance.
     *
     * <p>El alcance se pide de {@code ver_respuestas_evaluacion}, el mismo permiso que guarda
     * la ruta: si un rol lo tiene acotado a sus vacantes, aquí también.
     */
    /** Un solo camino, un solo permiso: por eso va escrito aquí y no por parámetro. */
    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId) {
        return alcance.laPostulacionVisible(quien, postulacionId, "ver_respuestas_evaluacion");
    }

    /**
     * Los patrones que solo se ven mirando el cuestionario entero.
     *
     * <p>Son dos consultas sobre las señales ya guardadas —sin IA y sin coste—, que es
     * exactamente para lo que la V41 las persistió: «se vuelven consultas sobre estas
     * columnas en vez de otra pasada de IA».
     *
     * <p>⚠️ <b>Solo cuentan las respuestas que traen señales.</b> Mezclar las que no las
     * tienen daría «nunca se incomodó» en cualquier evaluación de un banco anterior, que no
     * midió nada de esto. Si no queda ninguna, no hay patrones y no es un hallazgo.
     *
     * <p>Ninguno descarta a nadie: son dos preguntas para la conversación final.
     */
    private List<PatronDelCuestionario> patronesDe(List<RespuestaAbiertaVista> abiertas) {
        List<RespuestaAbiertaVista> conSenales = abiertas.stream()
                .filter(a -> a.senales() != null)
                .toList();
        if (conSenales.isEmpty()) return List.of();

        List<PatronDelCuestionario> patrones = new java.util.ArrayList<>();
        int total = conSenales.size();

        long conIncomodidad = conSenales.stream().filter(a -> a.senales().incomodidad()).count();
        if (conIncomodidad == 0) {
            patrones.add(new PatronDelCuestionario(
                    "SIN_INCOMODIDAD",
                    "No se metió en lo incómodo en ninguna respuesta",
                    "En las " + total + " respuestas contó lo que salió bien. Ni un error "
                            + "propio, ni una decisión que le costara. Vale la pena "
                            + "preguntárselo en la conversación.",
                    0, total));
        }

        long sinAutoria = conSenales.stream().filter(a -> !a.senales().autoria()).count();
        // La mitad, que es el corte que nombra la V41. Con pocas respuestas el patrón se
        // dispara facil, y por eso la frase dice de cuantas sale: quien lo lea juzga.
        if (sinAutoria * 2 >= total) {
            patrones.add(new PatronDelCuestionario(
                    "SOLO_NOSOTROS",
                    "Cuenta lo que hizo el equipo, no lo suyo",
                    "En " + sinAutoria + " de " + total + " respuestas no se distingue qué "
                            + "hizo él de lo que hizo su equipo. Puede ser modestia o puede "
                            + "ser que no fuera suyo; desde aquí no se sabe cuál.",
                    (int) sinAutoria, total));
        }
        return patrones;
    }
}
