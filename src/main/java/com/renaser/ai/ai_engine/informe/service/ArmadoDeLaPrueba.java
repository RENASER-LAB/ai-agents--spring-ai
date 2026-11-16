package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Anexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Dato;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Detalle;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EntregableAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Evaluado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Nota;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PreguntaAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Resumen;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PatronDelCuestionario;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioDesgloseEvaluacion;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.EntregaDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.NotaCriterioResponse;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.RespuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CriterioDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.EntregaVista;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.OpcionDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PreguntaDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PruebaDelCandidato;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPrueba;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPruebaPropia;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.hay;
import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.limpio;

/**
 * El informe de la prueba del puesto: la nota (o «pendiente» y qué falta), cada criterio con su
 * máximo, quién lo puntuó y por qué, los tres mejores y los tres peores, lo que queda por
 * verificar en la entrevista y, en el anexo, el caso, las respuestas y los entregables.
 *
 * <p><b>Es la prueba que rindió ESE candidato</b>, la de su intento, no la que la vacante tenga
 * puesta hoy (la vacante 13 cambió de prueba con gente dentro). Las tres formas se leen por los
 * mismos servicios que la ficha: la prueba de una plantilla, la escrita en el editor (V67) y el
 * cuestionario técnico.
 *
 * <p><b>Del entregable va el nombre y cuándo llegó, nunca su contenido</b>: los servicios se
 * piden sin el permiso de verlo.
 */
@Component
@RequiredArgsConstructor
public class ArmadoDeLaPrueba {

    private static final String ETAPA = "PRUEBA_PUESTO";
    private static final String CUESTIONARIO = "CUESTIONARIO_TECNICO";
    private static final int TRES = 3;

    private final IntentoPruebaRepository intentos;
    private final NotaEtapaRepository notasEtapa;
    private final NotaCriterioRepository notasCriterio;
    private final VersionBancoRepository versionesBanco;
    private final VersionPlantillaPruebaRepository versionesPlantilla;
    private final PlantillaPruebaRepository plantillas;
    private final ServicioCalificacionPrueba deLaPlantilla;
    private final ServicioCalificacionPruebaPropia delEditor;
    private final ServicioDesgloseEvaluacion desglose;
    private final ColaCalificacionIa cola;

    /** Lo que se leyó de la prueba, sea de la forma que sea. */
    record Lectura(String nombre, String caso, Instant entregadaEn, boolean noCompletada,
                   List<Criterio> criterios, List<PreguntaAnexo> preguntas,
                   List<EntregableAnexo> entregables, List<String> loQueFalta, String motivoPendiente) {
        boolean algunaNota() {
            return criterios.stream().anyMatch(c -> c.nota() != null);
        }
    }

    EstadoDeUnInforme estado(Base base) {
        String etapa = EtapaDelInforme.PRUEBA.name();
        BigDecimal nota = notaDeLaEtapa(base);
        if (rindeElCuestionario(base)) {
            return nota != null ? new EstadoDeUnInforme(etapa, true, null, nota)
                    : new EstadoDeUnInforme(etapa, false, MotivosDelInforme.SIN_CALIFICAR, null);
        }
        IntentoPrueba intento = intentos.findByPostulacionId(base.postulacion().getId()).orElse(null);
        if (intento == null || intento.getEntregadoEn() == null && !intento.isNoCompletada()) {
            return new EstadoDeUnInforme(etapa, false, MotivosDelInforme.SIN_CALIFICAR, null);
        }
        ColaCalificacionIa.Seguimiento seguimiento = cola
                .calificacionDePrueba(List.of(base.postulacion().getId())).get(base.postulacion().getId());
        if (seguimiento != null && "EN_CURSO".equals(seguimiento.estado())) {
            return new EstadoDeUnInforme(etapa, false, MotivosDelInforme.CALIFICANDO, null);
        }
        Lectura lectura = leer(base, intento);
        if (nota != null || lectura.algunaNota() || intento.isNoCompletada()) {
            return new EstadoDeUnInforme(etapa, true, null, nota);
        }
        boolean fallo = seguimiento != null && "DETENIDA".equals(seguimiento.estado());
        return new EstadoDeUnInforme(etapa, false,
                fallo ? MotivosDelInforme.FALLO : MotivosDelInforme.SIN_CALIFICAR, null);
    }

    Informe armar(Base base, Instant ahora) {
        IntentoPrueba intento = intentos.findByPostulacionId(base.postulacion().getId()).orElse(null);
        Lectura lectura = leer(base, intento);
        BigDecimal notaEtapa = notaDeLaEtapa(base);

        List<String> faltan = lectura.criterios().stream().filter(c -> c.nota() == null)
                .map(Criterio::nombre).toList();
        String base100 = notaEtapa != null ? "La suma de los criterios de su rúbrica."
                : faltan.isEmpty() ? "Todavía no tiene nota de la etapa."
                : "Pendiente: le falta la nota de " + String.join(", ", faltan) + ".";
        Nota nota = new Nota(notaEtapa,
                notaEtapa == null ? "pendiente" : FormatoDelInforme.notaRedonda(notaEtapa),
                notaEtapa == null ? null : "de 100", base100);

        long entregados = lectura.entregables().stream().filter(EntregableAnexo::entregado).count();
        List<Dato> datos = new ArrayList<>();
        if (hay(lectura.nombre())) {
            datos.add(new Dato("Prueba", lectura.nombre()));
        }
        if (!lectura.entregables().isEmpty()) {
            datos.add(new Dato("Entregables", entregados + " de " + lectura.entregables().size()
                    + " entregados"));
        }
        datos.add(new Dato(lectura.noCompletada() ? "No la completó" : "Entregada",
                FormatoDelInforme.fechaHora(lectura.entregadaEn())));
        datos.add(new Dato("Generado", FormatoDelInforme.fechaHora(ahora)));

        List<Criterio> conNota = lectura.criterios().stream()
                .filter(c -> c.nota() != null && c.porcentaje() != null)
                .sorted(Comparator.comparing(Criterio::porcentaje).reversed())
                .toList();
        int cuantosFuertes = Math.min(TRES, conNota.size());
        List<Punto> fuertes = conNota.subList(0, cuantosFuertes).stream()
                .map(c -> new Punto(c.nombre(), null, "CRITERIO", null, null)).toList();
        List<Criterio> resto = conNota.subList(cuantosFuertes, conNota.size());
        List<Punto> fortalecer = new ArrayList<>();
        for (int i = resto.size() - 1; i >= 0 && fortalecer.size() < TRES; i--) {
            Criterio c = resto.get(i);
            fortalecer.add(new Punto(c.nombre(), c.texto(), "CRITERIO", null, null));
        }

        List<Bloque> bloques = List.of(
                LoComunDelInforme.bloque("FUERTES", "Puntos fuertes", "BIEN", fuertes),
                LoComunDelInforme.bloque("FORTALECER", "Puntos a fortalecer", "NEUTRO", fortalecer),
                LoComunDelInforme.bloque("PENDIENTE", EtapaDelInforme.PRUEBA.pendiente(), "PENDIENTE",
                        paraLaEntrevista(base, lectura)));

        return new Informe(EtapaDelInforme.PRUEBA.name(), EtapaDelInforme.PRUEBA.titulo(),
                base.empresa(), base.vacanteTitulo(), base.postulacion().getId(), base.candidato(),
                base.correo(), ahora, nota, base.postulacion().getGrupoPrioridad(), datos, List.of(),
                null, resumenDeHechos(lectura) == null ? null
                        : new Resumen("En pocas líneas", resumenDeHechos(lectura)),
                new Evaluado("Qué se evaluó", "puntos de cada criterio", lectura.criterios(),
                        "Quién puso la nota: IA, Persona o Sistema"),
                bloques, List.of(), "El porqué de cada nota", List.of(), null,
                new Anexo("Respuestas y entregables",
                        lectura.preguntas().size() + (lectura.preguntas().size() == 1 ? " pregunta" : " preguntas")
                                + (lectura.entregables().isEmpty() ? ""
                                : " · " + lectura.entregables().size() + " entregables"),
                        lectura.caso(), lectura.preguntas(),
                        lectura.entregables().isEmpty() ? null
                                : entregados + " de " + lectura.entregables().size() + " entregados",
                        lectura.entregables()),
                null, false, MotivosDelInforme.nombreDeArchivo(EtapaDelInforme.PRUEBA, base));
    }

    /**
     * «El porqué de cada nota» de la prueba: la explicación de los criterios cuya línea bajo la
     * barra la escribió el redactor (la explicación entera no está a la vista), y los que no
     * tienen nota con su motivo. Si todo está ya bajo las barras, no hay nada que repetir.
     */
    static List<Detalle> detalleTrasLosTextos(List<Criterio> criterios) {
        List<Detalle> salida = new ArrayList<>();
        for (Criterio c : criterios) {
            boolean explicacionOculta = hay(c.explicacion()) && !c.explicacion().equals(c.texto());
            if (!explicacionOculta) {
                continue;
            }
            salida.add(new Detalle(null, c.nombre(), c.quien(), null, c.ajustada(), c.motivoAjuste(),
                    c.explicacion(), null, List.of(), c.pendiente()));
        }
        return salida;
    }

    // ================================================================= la lectura

    private Lectura leer(Base base, IntentoPrueba intento) {
        if (rindeElCuestionario(base)) {
            List<PreguntaAnexo> preguntas = new ArrayList<>();
            int n = 0;
            for (RespuestaDePrueba r : deLaPlantilla.respuestasParaElInforme(base.postulacion())) {
                preguntas.add(new PreguntaAnexo(++n, r.enunciado(), "Abierta", limpio(r.respuesta()),
                        null, null));
            }
            return new Lectura("Cuestionario técnico", null, null, false, List.of(), preguntas,
                    List.of(), List.of(), null);
        }
        if (intento == null) {
            return new Lectura(null, null, null, false, List.of(), List.of(), List.of(), List.of(), null);
        }
        if (intento.esDelEditor()) {
            return delEditor(base, intento);
        }
        return deLaPlantilla(base, intento);
    }

    private Lectura delEditor(Base base, IntentoPrueba intento) {
        PruebaDelCandidato prueba = delEditor.paraElInforme(base.postulacion());
        var version = intento.getVersionBancoId() == null ? null
                : versionesBanco.findById(intento.getVersionBancoId()).orElse(null);
        if (prueba == null) {
            return new Lectura(null, null, intento.getEntregadoEn(), intento.isNoCompletada(),
                    List.of(), List.of(), List.of(), List.of(), null);
        }
        List<Criterio> criterios = new ArrayList<>();
        List<PreguntaAnexo> preguntas = new ArrayList<>();
        int n = 0;
        for (CriterioDelCandidato c : prueba.criterios()) {
            BigDecimal maximo = BigDecimal.valueOf(c.maximo());
            boolean ajustada = c.ajustadaPor() != null;
            String quien = ajustada ? "Persona"
                    : "SOLO_SISTEMA".equals(c.estado()) ? "Sistema"
                    : "PERSONA".equals(c.calificador()) ? "Persona" : "IA";
            String pendiente = null;
            if (c.nota() == null) {
                pendiente = "PERSONA".equals(c.calificador()) ? "Le toca calificarlo a una persona"
                        : hay(prueba.motivoPendiente()) ? prueba.motivoPendiente()
                        : "Falta que la IA lo califique";
            }
            String explicacion = limpio(c.explicacion());
            criterios.add(new Criterio(c.nombre(), c.nota(), maximo, FormatoDelInforme.nota(c.nota()),
                    "/ " + c.maximo(), FormatoDelInforme.porcentaje(c.nota(), maximo), quien, ajustada,
                    ajustada ? limpio(c.motivoAjuste()) : null, explicacion, explicacion, null, pendiente));
            for (PreguntaDelCandidato p : c.preguntas()) {
                preguntas.add(new PreguntaAnexo(++n, p.enunciado(), tipoDicho(p.tipo()) + " · " + c.nombre(),
                        respuestaDe(p), null, null));
            }
        }
        List<EntregableAnexo> entregables = prueba.entregables().stream()
                .map(ArmadoDeLaPrueba::entregable).toList();
        return new Lectura(version == null || !hay(version.getEtiqueta()) ? "Prueba del puesto" : version.getEtiqueta(),
                version == null ? null : limpio(version.getEnunciado()), prueba.entregadoEn(),
                "NO_COMPLETADA".equals(prueba.estado()) || intento.isNoCompletada(), criterios,
                preguntas, entregables, prueba.loQueFalta(), prueba.motivoPendiente());
    }

    private Lectura deLaPlantilla(Base base, IntentoPrueba intento) {
        List<NotaCriterioResponse> notas = deLaPlantilla.notasParaElInforme(base.postulacion());
        Map<Long, NotaCriterio> filas = notasCriterio.findByPostulacionId(base.postulacion().getId()).stream()
                .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity(), (a, b) -> a));
        List<Criterio> criterios = new ArrayList<>();
        for (NotaCriterioResponse n : notas) {
            BigDecimal maximo = n.puntosMaximos() == null ? null : BigDecimal.valueOf(n.puntosMaximos());
            BigDecimal puntaje = n.puntaje() == null ? null : BigDecimal.valueOf(n.puntaje());
            NotaCriterio fila = filas.get(n.criterioId());
            boolean ajustada = fila != null && fila.getAjustadaPorUsuarioId() != null;
            String explicacion = limpio(n.explicacion());
            criterios.add(new Criterio(n.nombre(), puntaje, maximo, FormatoDelInforme.nota(puntaje),
                    maximo == null ? null : "/ " + FormatoDelInforme.nota(maximo),
                    FormatoDelInforme.porcentaje(puntaje, maximo),
                    puntaje == null ? null : MotivosDelInforme.quien(n.origen(), ajustada), ajustada,
                    ajustada ? limpio(fila.getMotivoAjuste()) : null, explicacion, explicacion, null,
                    puntaje == null ? "Falta calificarlo" : null));
        }
        List<PreguntaAnexo> preguntas = new ArrayList<>();
        int n = 0;
        for (RespuestaDePrueba r : deLaPlantilla.respuestasParaElInforme(base.postulacion())) {
            preguntas.add(new PreguntaAnexo(++n, r.enunciado(), tipoDicho(r.tipo()), limpio(r.respuesta()),
                    null, null));
        }
        List<EntregableAnexo> entregables = deLaPlantilla.entregablesParaElInforme(base.postulacion())
                .stream().map(ArmadoDeLaPrueba::entregable).toList();
        var version = intento.getVersionPlantillaPruebaId() == null ? null
                : versionesPlantilla.findById(intento.getVersionPlantillaPruebaId()).orElse(null);
        String nombre = version == null || version.getPlantillaPruebaId() == null ? "Prueba del puesto"
                : plantillas.findById(version.getPlantillaPruebaId()).map(p -> p.getNombre())
                        .filter(FormatoDelInforme::hay).orElse("Prueba del puesto");
        return new Lectura(nombre, version == null ? null : limpio(version.getEnunciado()),
                intento.getEntregadoEn(), intento.isNoCompletada(), criterios, preguntas, entregables,
                List.of(), null);
    }

    private static EntregableAnexo entregable(EntregaVista e) {
        return new EntregableAnexo(e.nombre(), e.loEntrego()
                ? "Subido el " + FormatoDelInforme.diaYHora(e.subidoEn())
                : "No se subió ningún archivo", e.loEntrego());
    }

    private static EntregableAnexo entregable(EntregaDeLaPrueba e) {
        return new EntregableAnexo(e.nombre(), e.loEntrego()
                ? "Subido el " + FormatoDelInforme.diaYHora(e.subidoEn())
                : "No se subió ningún archivo", e.loEntrego());
    }

    private static String respuestaDe(PreguntaDelCandidato p) {
        if (!p.respondida()) {
            return null;
        }
        if (p.opciones() != null && !p.opciones().isEmpty()) {
            String marcadas = p.opciones().stream().filter(OpcionDelCandidato::marcada)
                    .map(o -> "«" + o.texto() + "»").collect(Collectors.joining(" · "));
            if (!marcadas.isEmpty()) {
                return marcadas;
            }
        }
        return limpio(p.respuesta());
    }

    private static String tipoDicho(String tipo) {
        if (tipo == null) {
            return "Pregunta";
        }
        return switch (tipo) {
            case "ABIERTA" -> "Abierta";
            case "OPCION_UNICA" -> "Opción única";
            case "OPCION_MULTIPLE" -> "Opción múltiple";
            case "ESCALA" -> "Escala";
            default -> "Pregunta";
        };
    }

    // ================================================================= lo pendiente

    /**
     * Lo que queda por verificar en la entrevista, en tres grupos y nunca recortado: lo que deja
     * la propia prueba, lo que dejó la evaluación y las dudas del CV, que suelen ser lo más largo.
     */
    private List<Punto> paraLaEntrevista(Base base, Lectura lectura) {
        List<Punto> puntos = new ArrayList<>();
        String deLaPrueba = "De la prueba";
        for (Criterio c : lectura.criterios()) {
            if (c.nota() == null) {
                puntos.add(new Punto(c.nombre() + ": " + (c.pendiente() == null ? "sin nota" : c.pendiente()),
                        null, "PRUEBA", "Sin nota", deLaPrueba));
            }
        }
        for (EntregableAnexo e : lectura.entregables()) {
            if (!e.entregado()) {
                puntos.add(new Punto("No entregó «" + e.nombre() + "»", null, "PRUEBA", "No entregado",
                        deLaPrueba));
            }
        }
        if (lectura.noCompletada()) {
            puntos.add(new Punto("No completó la prueba", null, "PRUEBA", null, deLaPrueba));
        }
        List<PatronDelCuestionario> patrones = base.postulacion().getEvaluacionId() == null ? List.of()
                : desglose.paraElInforme(base.postulacion()).patrones();
        puntos.addAll(LoComunDelInforme.pendientesDeLaEvaluacion(base, patrones, "De la evaluación"));
        puntos.addAll(LoComunDelInforme.dudasDelCv(base, null, "Del CV"));
        return puntos;
    }

    /** Lo que se dice sin redactor: solo hechos contados, sin la nota. */
    private static String resumenDeHechos(Lectura lectura) {
        List<String> frases = new ArrayList<>();
        long respondidas = lectura.preguntas().stream().filter(p -> p.respuesta() != null).count();
        int total = lectura.preguntas().size();
        if (lectura.noCompletada()) {
            frases.add("No completó la prueba: respondió " + respondidas + " de " + total + " preguntas.");
        } else if (total > 0) {
            frases.add("Respondió " + respondidas + " de " + total + " preguntas.");
        }
        if (!lectura.entregables().isEmpty()) {
            long entregados = lectura.entregables().stream().filter(EntregableAnexo::entregado).count();
            frases.add("Entregó " + entregados + " de " + lectura.entregables().size() + " entregables.");
        }
        List<String> sinNota = lectura.criterios().stream().filter(c -> c.nota() == null)
                .map(Criterio::nombre).toList();
        if (!sinNota.isEmpty()) {
            frases.add("Falta la nota de " + String.join(", ", sinNota) + ".");
        }
        return frases.isEmpty() ? null : String.join(" ", frases);
    }

    private boolean rindeElCuestionario(Base base) {
        return base.vacante() != null && CUESTIONARIO.equals(base.vacante().getInstrumentoEtapaTecnica());
    }

    private BigDecimal notaDeLaEtapa(Base base) {
        return notasEtapa.findByPostulacionIdAndEtapaCodigo(base.postulacion().getId(), ETAPA)
                .map(NotaEtapa::getPuntaje).orElse(null);
    }
}
