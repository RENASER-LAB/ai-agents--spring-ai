package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Anexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Dato;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Detalle;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Evaluado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Nota;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PreguntaAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Resumen;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Senal;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.CriterioDelDesglose;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DesgloseEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PreguntaDelDesglose;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Dimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.OrdenPregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.DimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OrdenPreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.FormulasCazatalentos;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioDesgloseEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.service.impl.CalificacionCriterios;
import com.renaser.ai.ai_engine.pesos.entity.PesoComponentePerfil;
import com.renaser.ai.ai_engine.pesos.repository.PesoComponentePerfilRepository;
import com.renaser.ai.ai_engine.vacante.service.RecorridoDeLaVacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.hay;
import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.limpio;

/**
 * El informe de la evaluación: la nota del Perfil Integral con cuánto pesó cada parte, los
 * pilares o criterios, los cuatro bloques que salen del retrato, «Lo que pidió el CV» y, al
 * final, el porqué de cada abierta y todas las preguntas con su respuesta.
 *
 * <p><b>«Lo que pidió el CV» no inventa estados.</b> Aquí cada duda va «Por contrastar». Decir si
 * la evaluación la resolvió lo hace el seguimiento de pendientes (V94), que la IA compara con las
 * abiertas de {@link #abiertasConTexto}; {@code ArmadoDelInforme} pone sus estados encima solo si
 * se hizo con estos mismos datos.
 */
@Component
@RequiredArgsConstructor
public class ArmadoDeLaEvaluacion {

    private static final String ETAPA = "PERFIL_INTEGRAL";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final String ABIERTA = "ABIERTA";

    private final EvaluacionRepository evaluaciones;
    private final NotaEtapaRepository notasEtapa;
    private final OrdenPreguntaRepository ordenes;
    private final PreguntaRepository preguntas;
    private final RespuestaRepository respuestas;
    private final OpcionRepository opciones;
    private final NotaRespuestaRepository notasRespuesta;
    private final PreguntaDimensionRepository preguntaDimensiones;
    private final DimensionRepository dimensiones;
    private final CriterioBancoRepository criteriosBanco;
    private final PesoComponentePerfilRepository pesosComponente;
    private final ServicioDesgloseEvaluacion desglose;
    private final CalificacionCriterios calificacionCriterios;
    /** El recorrido de la vacante (V95): lo pendiente nombra la etapa que sigue al Perfil Integral. */
    private final RecorridoDeLaVacante recorridos;

    EstadoDeUnInforme estado(Base base) {
        String etapa = EtapaDelInforme.EVALUACION.name();
        Evaluacion evaluacion = evaluacionDe(base);
        if (evaluacion == null || evaluacion.getTerminadaEn() == null) {
            return new EstadoDeUnInforme(etapa, false, MotivosDelInforme.SIN_CALIFICAR, null);
        }
        BigDecimal nota = notaDeLaEtapa(base);
        if ("EN_CURSO".equals(base.comoVa())) {
            return new EstadoDeUnInforme(etapa, false, MotivosDelInforme.CALIFICANDO, null);
        }
        if (nota != null) {
            return new EstadoDeUnInforme(etapa, true, null, nota);
        }
        if ("FALLIDA".equals(base.comoVa())) {
            return new EstadoDeUnInforme(etapa, false, MotivosDelInforme.FALLO, null);
        }
        DesgloseEvaluacion d = desglose.paraElInforme(base.postulacion());
        boolean aMedias = d.porPuntos() != null && !d.porPuntos().completo();
        return new EstadoDeUnInforme(etapa, false,
                aMedias ? MotivosDelInforme.CALIFICANDO : MotivosDelInforme.SIN_CALIFICAR, null);
    }

    Informe armar(Base base, Instant ahora) {
        Evaluacion evaluacion = evaluacionDe(base);
        DesgloseEvaluacion d = desglose.paraElInforme(base.postulacion());
        List<Fila> filas = evaluacion == null ? List.of() : filas(base, evaluacion);
        long abiertas = filas.stream().filter(f -> ABIERTA.equals(f.pregunta().getTipo())).count();
        long cerradas = filas.size() - abiertas;
        String cuantas = cerradas + (cerradas == 1 ? " cerrada · " : " cerradas · ")
                + abiertas + (abiertas == 1 ? " abierta" : " abiertas");

        BigDecimal notaEtapa = notaDeLaEtapa(base);
        String reparto = reparto(base);
        Nota nota = new Nota(notaEtapa, FormatoDelInforme.notaRedonda(notaEtapa), "de 100",
                reparto == null ? "La nota del Perfil Integral."
                        : "La nota del Perfil Integral: " + reparto + ".");

        List<Dato> datos = new ArrayList<>();
        if (reparto != null) {
            datos.add(new Dato("Cómo se calcula", reparto));
        }
        datos.add(new Dato("Preguntas", cuantas));
        if (evaluacion != null && evaluacion.getTerminadaEn() != null) {
            datos.add(new Dato("Entregada", FormatoDelInforme.fechaHora(evaluacion.getTerminadaEn())));
        }
        datos.add(new Dato("Generado", FormatoDelInforme.fechaHora(ahora)));

        List<Bloque> bloques = List.of(
                LoComunDelInforme.bloque("FUERTES", "Puntos fuertes", "BIEN",
                        LoComunDelInforme.hallazgosDe(base, "FORTALEZA")),
                LoComunDelInforme.bloque("FORTALECER", "Puntos a fortalecer", "NEUTRO",
                        LoComunDelInforme.hallazgosDe(base, "RIESGO_DESARROLLABLE")),
                LoComunDelInforme.bloque("DEBILIDADES", "Debilidades a tener en cuenta", "MAL",
                        LoComunDelInforme.hallazgosDe(base, "RIESGO_CRITICO")),
                LoComunDelInforme.bloque("PIDIO_EL_CV", "Lo que pidió el CV", "CV",
                        LoComunDelInforme.dudasDelCv(base, "Por contrastar", null)),
                LoComunDelInforme.bloque("PENDIENTE", EtapaDelInforme.pendienteTrasElPerfil(
                                recorridos.de(base.postulacion().getVacanteId())),
                        "PENDIENTE", LoComunDelInforme.pendientesDeLaEvaluacion(base, d.patrones(), null)));

        String resumen = base.perfil() == null ? null : FormatoDelInforme.sinMarca(base.perfil().getResumen());
        String pieCerradas = d.cerradas() != null && d.cerradas().preguntas() > 0
                ? "Lo cerrado: " + FormatoDelInforme.nota(d.cerradas().nota()) + " de 100 en "
                        + d.cerradas().preguntas() + (d.cerradas().preguntas() == 1 ? " pregunta." : " preguntas.")
                : null;

        return new Informe(EtapaDelInforme.EVALUACION.name(), EtapaDelInforme.EVALUACION.titulo(),
                base.empresa(), base.vacanteTitulo(), base.postulacion().getId(), base.candidato(),
                base.correo(), ahora, nota, base.postulacion().getGrupoPrioridad(), datos, List.of(),
                null, resumen == null ? null : new Resumen("En pocas líneas", resumen),
                new Evaluado("Qué se evaluó", escala(base, d), evaluado(base, d, filas), null),
                bloques, List.of(), "El porqué de cada nota", detalle(filas), pieCerradas,
                new Anexo("Preguntas y respuestas", filas.size() + (filas.size() == 1 ? " pregunta" : " preguntas"),
                        null, anexo(filas), null, List.of()),
                null, false, MotivosDelInforme.nombreDeArchivo(EtapaDelInforme.EVALUACION, base));
    }

    // ================================================================= las preguntas

    /**
     * Las abiertas que contestó con texto, numeradas como en el anexo («Pregunta 3»): lo que lee el
     * seguimiento de pendientes. Son las mismas respuestas que enseña el informe, también las de
     * una evaluación de otra postulación que se reutiliza. Abierta es lo que se contesta
     * escribiendo, como para el evaluador: en el banco v3 hay formatos de texto libre que no se
     * llaman ABIERTA. Las cerradas no viajan.
     */
    List<PendientesEntreEtapas.Fuente> abiertasConTexto(Base base) {
        Evaluacion evaluacion = evaluacionDe(base);
        if (evaluacion == null) {
            return List.of();
        }
        return filas(base, evaluacion).stream()
                .filter(f -> f.respuesta() != null && seEscribe(f.pregunta(), f.respuesta())
                        && hay(f.respuestaTexto()))
                .map(f -> PendientesEntreEtapas.Fuente.respuesta(f.numero(), f.pregunta().getEnunciado(),
                        f.respuestaTexto()))
                .toList();
    }

    /** Una pregunta que se le hizo, con lo que contestó y la nota que le pusieron. */
    record Fila(int numero, Pregunta pregunta, Respuesta respuesta, String respuestaTexto,
                NotaRespuesta nota, String pilar, String criterio, int peso) {
    }

    private List<Fila> filas(Base base, Evaluacion evaluacion) {
        List<Respuesta> suyas = respuestas.findByEvaluacionId(evaluacion.getId());
        Map<Long, Respuesta> porPregunta = suyas.stream()
                .collect(Collectors.toMap(Respuesta::getPreguntaId, Function.identity(), (a, b) -> a));
        // Las que le tocaron, en el orden en que las vio (RF-51). Sin orden congelado, las que
        // contestó, en el orden en que las contestó.
        List<Long> ids = ordenes.findByEvaluacionIdOrderByPosicion(evaluacion.getId()).stream()
                .map(OrdenPregunta::getPreguntaId).toList();
        if (ids.isEmpty()) {
            ids = suyas.stream().sorted(java.util.Comparator.comparing(Respuesta::getId))
                    .map(Respuesta::getPreguntaId).distinct().toList();
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Pregunta> porId = preguntas.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        Map<Long, List<Opcion>> opcionesDe = opciones.findByPreguntaIdIn(ids).stream()
                .collect(Collectors.groupingBy(Opcion::getPreguntaId));
        Map<Long, NotaRespuesta> notaDe = notasRespuesta.findByRespuestaIdIn(
                        suyas.stream().map(Respuesta::getId).toList()).stream()
                .collect(Collectors.toMap(NotaRespuesta::getRespuestaId, Function.identity(), (a, b) -> a));
        Map<String, String> nombrePilar = dimensiones.findAllByOrderByOrden().stream()
                .collect(Collectors.toMap(Dimension::getCodigo, Dimension::getNombre, (a, b) -> a));
        Map<Long, String> pilarDe = preguntaDimensiones.findByPreguntaIdIn(ids).stream()
                .filter(pd -> pd.getDimensionCodigo().startsWith("PIL_"))
                .collect(Collectors.toMap(PreguntaDimension::getPreguntaId,
                        PreguntaDimension::getDimensionCodigo, (a, b) -> a));
        Set<Long> versiones = new HashSet<>();
        porId.values().forEach(p -> versiones.add(p.getVersionBancoId()));
        Map<Long, String> criterioDe = criteriosBanco.findByVersionBancoIdIn(List.copyOf(versiones))
                .stream().collect(Collectors.toMap(CriterioBanco::getId, CriterioBanco::getNombre, (a, b) -> a));

        List<Fila> filas = new ArrayList<>();
        int numero = 0;
        for (Long id : ids) {
            Pregunta p = porId.get(id);
            if (p == null) {
                continue;
            }
            Respuesta r = porPregunta.get(id);
            String pilar = pilarDe.get(id);
            filas.add(new Fila(++numero, p, r, respuestaDe(p, r, opcionesDe.getOrDefault(id, List.of())),
                    r == null ? null : notaDe.get(r.getId()),
                    pilar == null ? null : nombrePilar.getOrDefault(pilar, pilar),
                    p.getCriterioBancoId() == null ? null : criterioDe.get(p.getCriterioBancoId()),
                    p.getPeso() == null ? 1 : p.getPeso()));
        }
        return filas;
    }

    /** El texto en las abiertas y la opción (u opciones) elegidas en las cerradas. Nulo: en blanco. */
    private static String respuestaDe(Pregunta p, Respuesta r, List<Opcion> suyas) {
        if (r == null) {
            return null;
        }
        if (seEscribe(p, r)) {
            return limpio(r.getTexto());
        }
        Set<Long> marcadas = new HashSet<>(CalificacionPorPuntos.marcadasDe(r));
        if (r.getOpcionId() != null) {
            marcadas.add(r.getOpcionId());
        }
        List<String> elegidas = suyas.stream()
                .filter(o -> marcadas.contains(o.getId()))
                .sorted(java.util.Comparator.comparing((Opcion o) -> o.getOrden() == null ? 0 : o.getOrden())
                        .thenComparing(o -> o.getLetra() == null ? "" : o.getLetra()))
                .map(o -> hay(o.getTexto()) ? "«" + o.getTexto().strip() + "»"
                        : o.getLetra() == null ? "(sin texto)" : o.getLetra())
                .toList();
        return elegidas.isEmpty() ? limpio(r.getTexto()) : String.join(" · ", elegidas);
    }

    /** Si la respuesta se escribió: una abierta, o un texto sin opción ni marcas. */
    private static boolean seEscribe(Pregunta p, Respuesta r) {
        return ABIERTA.equals(p.getTipo()) || (r.getOpcionId() == null && hay(r.getTexto())
                && CalificacionPorPuntos.marcadasDe(r).isEmpty());
    }

    private static String tipoDicho(String tipo) {
        if (tipo == null) {
            return "Pregunta";
        }
        return switch (tipo) {
            case ABIERTA -> "Abierta";
            case "OPCION_UNICA" -> "Opción única";
            case "OPCION_MULTIPLE" -> "Opción múltiple";
            case "ESCALA" -> "Escala";
            default -> {
                String t = tipo.replace('_', ' ').toLowerCase(Locale.forLanguageTag("es"));
                yield Character.toUpperCase(t.charAt(0)) + t.substring(1);
            }
        };
    }

    private static String meta(Fila f) {
        String donde = f.pilar() != null ? f.pilar() : f.criterio();
        return donde == null ? tipoDicho(f.pregunta().getTipo())
                : tipoDicho(f.pregunta().getTipo()) + " · " + donde;
    }

    private static List<PreguntaAnexo> anexo(List<Fila> filas) {
        return filas.stream().map(f -> {
            String explicacion = f.nota() == null ? null : limpio(f.nota().getExplicacion());
            return new PreguntaAnexo(f.numero(), f.pregunta().getEnunciado(), meta(f),
                    f.respuestaTexto(), ABIERTA.equals(f.pregunta().getTipo()) ? explicacion : null,
                    explicacion);
        }).toList();
    }

    /** El porqué de cada abierta: nota, explicación, lo que citó la IA, y las señales si las hay. */
    private static List<Detalle> detalle(List<Fila> filas) {
        List<Detalle> salida = new ArrayList<>();
        for (Fila f : filas) {
            if (!ABIERTA.equals(f.pregunta().getTipo()) || !f.pregunta().isEsPuntuable()) {
                continue;
            }
            NotaRespuesta n = f.nota();
            boolean enBlanco = f.respuestaTexto() == null;
            BigDecimal puntaje = n != null ? n.getPuntaje() : enBlanco ? BigDecimal.ZERO : null;
            String maximo = maximoDe(f);
            String notaTexto = puntaje == null ? FormatoDelInforme.HUECO
                    : FormatoDelInforme.nota(puntaje) + (maximo == null ? "" : " / " + maximo);
            boolean ajustada = n != null && n.getAjustadaPorUsuarioId() != null;
            String ajuste = null;
            if (ajustada) {
                String motivo = limpio(n.getMotivoAjuste());
                ajuste = n.getPuntajeIa() == null ? motivo
                        : "La IA puso " + FormatoDelInforme.nota(n.getPuntajeIa())
                                + (motivo == null ? "" : ": " + motivo);
            }
            salida.add(new Detalle(f.numero(), f.pregunta().getEnunciado(), meta(f), notaTexto,
                    ajustada, ajuste,
                    n == null ? (enBlanco ? "Sin respuesta: vale 0, que es la regla de siempre." : null)
                            : limpio(n.getExplicacion()),
                    n == null ? null : limpio(n.getEvidenciaCitada()), senales(n),
                    puntaje == null ? "Falta calificarla" : null));
        }
        return salida;
    }

    /** El techo de una abierta, cuando se sabe: 4 por su peso en los bancos CAZATALENTOS. */
    private static String maximoDe(Fila f) {
        if (f.pregunta().getPuntos() != null && f.pregunta().getPuntos() > 0) {
            return String.valueOf(f.pregunta().getPuntos());
        }
        if (f.nota() != null && f.nota().getC1Episodio() != null) {
            return String.valueOf(FormulasCazatalentos.TECHO_DEL_ITEM * Math.max(1, f.peso()));
        }
        return null;
    }

    /**
     * Las cuatro señales, o ninguna. Sin {@code c1Episodio} la nota es de un banco que no las
     * medía, y entonces no se enseña ninguna casilla (cuatro «no» serían falsos).
     */
    private static List<Senal> senales(NotaRespuesta n) {
        if (n == null || n.getC1Episodio() == null) {
            return List.of();
        }
        return List.of(new Senal("Episodio real", Boolean.TRUE.equals(n.getC1Episodio())),
                new Senal("Fue suyo", Boolean.TRUE.equals(n.getC2Autoria())),
                new Senal("Dato duro", Boolean.TRUE.equals(n.getC3Dato())),
                new Senal("Incomodidad", Boolean.TRUE.equals(n.getC4Incomodidad())));
    }

    // ================================================================= qué se evaluó

    private String escala(Base base, DesgloseEvaluacion d) {
        return d.porPuntos() != null ? "puntos de cada criterio" : "sobre 100";
    }

    private List<Criterio> evaluado(Base base, DesgloseEvaluacion d, List<Fila> filas) {
        if (d.porPuntos() != null) {
            return d.porPuntos().criterios().stream().map(c -> criterioPropio(c)).toList();
        }
        if (CalificacionCriterios.METODO.equals(calificacionCriterios.metodoDe(base.postulacion()))) {
            return pilares(filas);
        }
        List<Criterio> salida = new ArrayList<>();
        if (d.cerradas() != null && d.cerradas().preguntas() > 0) {
            salida.add(new Criterio("Preguntas cerradas", d.cerradas().nota(), CIEN,
                    FormatoDelInforme.nota(d.cerradas().nota()), null,
                    FormatoDelInforme.porcentaje(d.cerradas().nota(), CIEN), "Sistema", false, null,
                    null, null, d.cerradas().preguntas() + " preguntas", null));
        }
        if (d.notaEvaluacion() != null) {
            salida.add(new Criterio("Evaluación completa", d.notaEvaluacion(), CIEN,
                    FormatoDelInforme.nota(d.notaEvaluacion()), null,
                    FormatoDelInforme.porcentaje(d.notaEvaluacion(), CIEN), null, false, null,
                    null, null, null, null));
        }
        return salida;
    }

    private static Criterio criterioPropio(CriterioDelDesglose c) {
        long abiertas = c.preguntas().stream().filter(p -> ABIERTA.equals(p.tipo())).count();
        long cerradas = c.preguntas().size() - abiertas;
        BigDecimal maximo = BigDecimal.valueOf(c.maximo());
        BigDecimal nota = c.pendiente() ? null : c.nota();
        boolean ajustada = c.preguntas().stream().anyMatch(PreguntaDelDesglose::ajustada);
        String motivo = c.preguntas().stream().filter(PreguntaDelDesglose::ajustada)
                .map(PreguntaDelDesglose::motivoAjuste).filter(FormatoDelInforme::hay)
                .collect(Collectors.joining(" · "));
        String quien = c.ia() != null && c.iaMaximo() > 0 ? "IA" : "Sistema";
        return new Criterio(c.nombre(), nota, maximo, FormatoDelInforme.nota(nota),
                "/ " + c.maximo(), FormatoDelInforme.porcentaje(nota, maximo),
                ajustada ? "Persona" : quien, ajustada, motivo.isEmpty() ? null : motivo, null,
                null, cerradas + " cerradas · " + abiertas + " abiertas",
                c.pendiente() ? "Le falta la nota de alguna abierta" : null);
    }

    /** Los pilares de un banco CAZATALENTOS, con la misma cuenta que pondera el índice. */
    private static List<Criterio> pilares(List<Fila> filas) {
        Map<String, List<Fila>> porPilar = new LinkedHashMap<>();
        for (Fila f : filas) {
            if (f.pilar() != null && f.pregunta().isEsPuntuable()) {
                porPilar.computeIfAbsent(f.pilar(), k -> new ArrayList<>()).add(f);
            }
        }
        List<Criterio> salida = new ArrayList<>();
        porPilar.forEach((pilar, suyas) -> {
            BigDecimal obtenido = BigDecimal.ZERO;
            int pesos = 0;
            boolean completo = true;
            for (Fila f : suyas) {
                BigDecimal puntaje = f.nota() == null ? null : f.nota().getPuntaje();
                if (puntaje == null) {
                    completo = false;
                    break;
                }
                obtenido = obtenido.add(puntaje.multiply(BigDecimal.valueOf(f.peso())));
                pesos += f.peso();
            }
            BigDecimal nota = completo ? FormulasCazatalentos.puntajePilar(obtenido, pesos) : null;
            long abiertas = suyas.stream().filter(f -> ABIERTA.equals(f.pregunta().getTipo())).count();
            salida.add(new Criterio(pilar, nota, CIEN, FormatoDelInforme.nota(nota), null,
                    FormatoDelInforme.porcentaje(nota, CIEN), "IA", false, null, null, null,
                    (suyas.size() - abiertas) + " cerradas · " + abiertas + " abiertas",
                    completo ? null : "Le falta la nota de alguna respuesta"));
        });
        return salida;
    }

    // ================================================================= la nota

    private Evaluacion evaluacionDe(Base base) {
        Long id = base.postulacion().getEvaluacionId();
        return id == null ? null : evaluaciones.findById(id).orElse(null);
    }

    private BigDecimal notaDeLaEtapa(Base base) {
        return notasEtapa.findByPostulacionIdAndEtapaCodigo(base.postulacion().getId(), ETAPA)
                .map(NotaEtapa::getPuntaje).orElse(null);
    }

    /** «CV 30 % · Evaluación 70 %», con los pesos de la versión de la vacante. */
    private String reparto(Base base) {
        if (base.vacante() == null || base.vacante().getVersionPesosId() == null) {
            return null;
        }
        Map<String, BigDecimal> pesos = pesosComponente.findByVersionPesosId(base.vacante().getVersionPesosId())
                .stream().collect(Collectors.toMap(PesoComponentePerfil::getComponente,
                        PesoComponentePerfil::getPeso, (a, b) -> a));
        BigDecimal cv = pesos.getOrDefault("CV", BigDecimal.ZERO);
        BigDecimal ev = pesos.getOrDefault("EVALUACION", BigDecimal.ZERO);
        BigDecimal total = cv.add(ev);
        if (total.signum() <= 0) {
            return null;
        }
        BigDecimal deCv = cv.multiply(CIEN).divide(total, 0, RoundingMode.HALF_UP);
        return "CV " + deCv.toPlainString() + " % · Evaluación " + CIEN.subtract(deCv).toPlainString() + " %";
    }
}
