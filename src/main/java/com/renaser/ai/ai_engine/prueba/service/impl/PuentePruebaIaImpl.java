package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.postulacion.service.impl.ExtractorTextoCv;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.CriterioDeRubrica;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.EntregaDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.InsumoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.NotaCriterioPruebaIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.RespuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.ResultadoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.Entregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VarianteCambio;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.PuentePruebaIa;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.service.ContextoDeLaVacante;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ver {@link PuentePruebaIa}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuentePruebaIaImpl implements PuentePruebaIa {

    private static final String CALIFICANDO = "PRUEBA_CALIFICANDO";
    private static final String POR_CONFIRMAR = "PRUEBA_POR_CONFIRMAR";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** El único método de verificación que le toca al agente. Los otros dos son de persona. */
    private static final String POR_AGENTE = "AGENTE";

    /**
     * Hasta dónde se lee un entregable.
     *
     * <p>Los dos topes existen por motivos distintos y ninguno es cosmético. El de bytes
     * protege la memoria: una entrega puede ser un video de doscientos megas, y bajarlo
     * entero para descubrir que no tiene texto tumbaría el proceso. El de caracteres protege
     * la factura: lo que entra al modelo se paga por token, y un documento de cien páginas
     * cuesta más que la nota que produce.
     */
    private static final long MAXIMO_BYTES = 8L * 1024 * 1024;
    private static final int MAXIMO_CARACTERES = 20_000;

    private final PostulacionRepository postulaciones;
    private final IntentoPruebaRepository intentos;
    private final VersionPlantillaPruebaRepository versiones;
    private final VarianteCambioRepository variantes;
    private final EntregableRepository entregables;
    private final EntregableRequeridoRepository entregablesRequeridos;
    private final RespuestaPruebaRepository respuestas;
    private final PreguntaPruebaRepository preguntasCatalogo;
    private final PreguntaVersionPlantillaRepository preguntasElegidas;
    private final CriterioRepository criterios;
    private final NotaCriterioRepository notasCriterio;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final ExtractorTextoCv extractor;
    private final CalificacionPorCriterio calificacion;
    private final ContextoDeLaVacante contexto;
    private final MaquinaEstados maquina;

    // ==================== Lo que se le manda al agente ====================

    @Override
    @Transactional(readOnly = true)
    public InsumoPrueba insumoPrueba(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        IntentoPrueba intento = intentos.findByPostulacionId(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba del puesto", "postulación", postulacionId));
        // Calificar una prueba a medias no da una nota baja: da una nota que no significa
        // nada, porque el candidato todavía podía estar escribiendo cuando se leyó.
        if (intento.getEntregadoEn() == null) {
            throw new IllegalStateException(
                    "La prueba de la postulación " + postulacionId + " todavía no está "
                            + "entregada: no hay nada que calificar");
        }
        VersionPlantillaPrueba version = versiones.findById(intento.getVersionPlantillaPruebaId())
                .orElseThrow(() -> new IllegalStateException(
                        "La versión de plantilla de esta prueba ya no existe"));
        Puesto puesto = contexto.puestoDe(postulacion);

        return new InsumoPrueba(
                puesto.getNombre(),
                puesto.getNivelPuestoCodigo(),
                contexto.queBuscaLaVacanteDe(postulacion),
                version.getEnunciado(),
                version.getMateriales(),
                version.getHerramientasPermitidas(),
                version.getDuracionMinutos(),
                cambioQueLeToco(intento),
                intento.isEsEntregaAutomatica(),
                rubricaParaElAgente(postulacionId, intento),
                loQueRespondio(intento, version),
                loQueEntrego(intento, version));
    }

    /**
     * La parte de la rúbrica que le toca al agente.
     *
     * <p>Dos filtros, y los dos importan. El primero es el método de verificación: quien
     * escribió la rúbrica ya decidió criterio por criterio quién lo mira (RF-87), y pasarle
     * al modelo uno marcado como de persona sería desobedecer esa decisión sin avisar.
     *
     * <p>El segundo es más callado: un criterio <b>sin puntos y sin peso</b> no tiene escala,
     * y una nota sin escala no se puede guardar ni comparar. Se deja fuera en vez de
     * inventarle un máximo de cien.
     */
    private List<CriterioDeRubrica> rubricaParaElAgente(Long postulacionId, IntentoPrueba intento) {
        List<Criterio> rubrica = laRubricaDe(intento);
        Map<Long, BigDecimal> maximos = calificacion.maximosDe(postulacionId, rubrica);

        return rubrica.stream()
                .filter(c -> POR_AGENTE.equals(c.getMetodoVerificacion()))
                .filter(c -> maximos.get(c.getId()) != null)
                .sorted(Comparator.comparing(c -> c.getOrden() == null ? 0 : c.getOrden()))
                .map(c -> new CriterioDeRubrica(c.getCodigo(), c.getNombre(), c.getDescripcion(),
                        maximos.get(c.getId())))
                .toList();
    }

    /** Las preguntas de esta versión con lo que contestó, en el orden en que las vio. */
    private List<RespuestaDePrueba> loQueRespondio(IntentoPrueba intento,
                                                   VersionPlantillaPrueba version) {
        List<Long> ids = preguntasElegidas
                .findByVersionPlantillaPruebaIdOrderByOrden(version.getId()).stream()
                .map(PreguntaVersionPlantilla::getPreguntaPruebaId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, PreguntaPrueba> porId = preguntasCatalogo.findByIdIn(ids).stream()
                .collect(Collectors.toMap(PreguntaPrueba::getId, Function.identity()));
        Map<Long, RespuestaPrueba> contestadas = respuestas.findByIntentoPruebaId(intento.getId())
                .stream()
                .collect(Collectors.toMap(RespuestaPrueba::getPreguntaPruebaId, Function.identity()));

        List<RespuestaDePrueba> salida = new ArrayList<>();
        for (Long id : ids) {
            PreguntaPrueba pregunta = porId.get(id);
            RespuestaPrueba contestada = contestadas.get(id);
            // Sin respuesta no se manda: el hueco no dice nada que la rúbrica pueda
            // calificar, y ocupa sitio en lo que se paga por token.
            if (pregunta == null || contestada == null || esVacio(contestada.getTexto())) {
                continue;
            }
            salida.add(new RespuestaDePrueba(pregunta.getTipo(), pregunta.getEnunciado(),
                    pregunta.getRevela(), contestada.getTexto()));
        }
        return salida;
    }

    /**
     * Lo que subió, con su contenido cuando se puede leer.
     *
     * <p>De cada cosa pedida se mira <b>la última versión</b> que subió: puede haber
     * entregado tres veces antes de que se acabara el plazo, y lo que vale es la última.
     */
    private List<EntregaDelCandidato> loQueEntrego(IntentoPrueba intento,
                                                   VersionPlantillaPrueba version) {
        List<Entregable> subidos = entregables.findByIntentoPruebaId(intento.getId());

        return entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(version.getId())
                .stream()
                .map(requerido -> pintarEntrega(requerido, ultimaVersionDe(subidos, requerido)))
                .toList();
    }

    private Optional<Entregable> ultimaVersionDe(List<Entregable> subidos,
                                                 EntregableRequerido requerido) {
        return subidos.stream()
                .filter(e -> requerido.getId().equals(e.getEntregableRequeridoId()))
                .max(Comparator.comparing(e -> e.getVersion() == null ? 0 : e.getVersion()));
    }

    private EntregaDelCandidato pintarEntrega(EntregableRequerido requerido,
                                              Optional<Entregable> entregado) {
        if (entregado.isEmpty()) {
            return new EntregaDelCandidato(requerido.getNombre(), requerido.getDetalle(),
                    requerido.getFormato(), false, null, null, null,
                    requerido.isEsObligatorio()
                            ? "No lo entregó, y era obligatorio"
                            : "No lo entregó");
        }
        Entregable entregable = entregado.get();
        if (entregable.getArchivoId() == null) {
            // Un enlace es lo único que se le puede enseñar al modelo de una entrega que
            // vive fuera. No se abre, y decirlo es más honesto que callarlo.
            return new EntregaDelCandidato(requerido.getNombre(), requerido.getDetalle(),
                    requerido.getFormato(), true, entregable.getEnlace(), null, null,
                    "Es un enlace: nadie lo ha abierto y no se sabe qué hay dentro");
        }
        Archivo archivo = archivos.findById(entregable.getArchivoId()).orElse(null);
        if (archivo == null || archivo.getBorradoEn() != null || archivo.getRuta() == null) {
            return new EntregaDelCandidato(requerido.getNombre(), requerido.getDetalle(),
                    requerido.getFormato(), true, null, null, null,
                    "El archivo ya no está guardado");
        }
        Leido leido = leer(archivo);
        return new EntregaDelCandidato(requerido.getNombre(), requerido.getDetalle(),
                requerido.getFormato(), true, entregable.getEnlace(),
                archivo.getNombreOriginal(), leido.texto(), leido.porQueNo());
    }

    /**
     * Saca el texto de un entregable, o explica por qué no se pudo.
     *
     * <p><b>Nunca deja caer la excepción.</b> Un entregable ilegible no es un fallo de la
     * calificación: es un dato de la calificación, y lo que hay que hacer con él es
     * contárselo al modelo para que deje ese criterio en manos de una persona. Si esto
     * lanzara, un video —que es una entrega perfectamente válida— dejaría sin calificar la
     * prueba entera.
     */
    private Leido leer(Archivo archivo) {
        if (archivo.getTamano() != null && archivo.getTamano() > MAXIMO_BYTES) {
            return Leido.no("Pesa " + (archivo.getTamano() / (1024 * 1024)) + " MB: demasiado "
                    + "para leerlo aquí. Lo tiene que mirar una persona");
        }
        try {
            String texto = extractor.extraer(almacen.leer(archivo), archivo.getTipo(),
                    archivo.getNombreOriginal());
            if (esVacio(texto)) {
                return Leido.no("Del archivo no salió nada de texto");
            }
            return Leido.si(texto.length() <= MAXIMO_CARACTERES
                    ? texto
                    : texto.substring(0, MAXIMO_CARACTERES) + "\n[...cortado por lo largo]");
        } catch (RuntimeException e) {
            log.info("No se pudo leer el entregable «{}»: {}", archivo.getNombreOriginal(),
                    e.getMessage());
            return Leido.no("No se pudo leer: " + e.getMessage());
        }
    }

    /** El texto de un entregable, o el motivo de que no lo haya. Nunca los dos. */
    private record Leido(String texto, String porQueNo) {
        static Leido si(String texto) {
            return new Leido(texto, null);
        }

        static Leido no(String porQue) {
            return new Leido(null, porQue);
        }
    }

    /**
     * El cambio inesperado que le tocó, si le llegó a aparecer.
     *
     * <p>Se sortea al empezar y solo se le enseña pasado su minuto, así que puede haber
     * variante elegida sin que el candidato la llegara a ver —si entregó antes—. Solo cuenta
     * lo que de verdad vio: juzgar cómo reaccionó a algo que nunca apareció no tiene sentido.
     */
    private String cambioQueLeToco(IntentoPrueba intento) {
        if (intento.getVarianteCambioId() == null || intento.getCambioMostradoEn() == null) {
            return null;
        }
        return variantes.findById(intento.getVarianteCambioId())
                .map(VarianteCambio::getTexto)
                .orElse(null);
    }

    // ==================== Lo que se guarda de lo que contesta ====================

    @Override
    @Transactional
    public void guardarNotasPrueba(Long postulacionId, Long ejecucionIaId,
                                   ResultadoPrueba resultado) {
        if (resultado == null) {
            throw new IllegalStateException(
                    "El agente PRUEBA_PUESTO no devolvió nada: no se guarda una calificación vacía");
        }
        Postulacion postulacion = postulacion(postulacionId);
        IntentoPrueba intento = intentos.findByPostulacionId(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba del puesto", "postulación", postulacionId));

        List<Criterio> rubrica = laRubricaDe(intento);
        Map<String, Criterio> porCodigo = rubrica.stream()
                .collect(Collectors.toMap(Criterio::getCodigo, Function.identity(), (a, b) -> a));
        Map<Long, BigDecimal> maximos = calificacion.maximosDe(postulacionId, rubrica);

        int guardadas = 0;
        for (NotaCriterioPruebaIa nota : lista(resultado.criterios())) {
            Criterio criterio = porCodigo.get(nota.codigo());
            if (criterio == null) {
                log.warn("PRUEBA_PUESTO devolvió un criterio que no está en esta rúbrica: {}",
                        nota.codigo());
                continue;
            }
            // Aunque el modelo se salte el filtro y puntúe uno de persona, aquí no entra: la
            // rúbrica ya dijo quién lo mira, y esa decisión no la cambia el modelo.
            if (!POR_AGENTE.equals(criterio.getMetodoVerificacion())) {
                log.warn("PRUEBA_PUESTO puntuó «{}», que la rúbrica reserva a {}",
                        nota.codigo(), criterio.getMetodoVerificacion());
                continue;
            }
            // Una nota sin explicación no se guarda (RF-150). No se pone un cero en su
            // lugar: quedarse sin nota es distinto de valer cero.
            if (nota.puntaje() == null || esVacio(nota.explicacion())) {
                log.warn("Nota de «{}» descartada: llegó sin puntaje o sin explicación",
                        nota.codigo());
                continue;
            }
            BigDecimal maximo = maximos.get(criterio.getId());
            if (maximo == null) {
                continue;
            }

            NotaCriterio fila = notasCriterio
                    .findByPostulacionIdAndCriterioId(postulacionId, criterio.getId())
                    .orElseGet(() -> NotaCriterio.builder()
                            .postulacionId(postulacionId)
                            .criterioId(criterio.getId())
                            .creadoEn(Instant.now())
                            .build());
            // Si una persona ya la ajustó a mano, la IA no la pisa: el ajuste manda.
            if (fila.getAjustadaPorUsuarioId() != null) {
                continue;
            }
            fila.setPuntaje(acotar(nota.puntaje(), maximo));
            fila.setExplicacion(conEvidencia(nota.explicacion(), nota.evidencia()));
            fila.setOrigen(POR_AGENTE);
            fila.setConfianza(acotar(resultado.confianza(), CIEN));
            fila.setEjecucionIaId(ejecucionIaId);
            notasCriterio.save(fila);
            guardadas++;
        }

        long deAgente = rubrica.stream()
                .filter(c -> POR_AGENTE.equals(c.getMetodoVerificacion()))
                .count();
        log.info("PRUEBA_PUESTO: {} de {} criterios de agente guardados para la postulación {}. "
                        + "La rúbrica entera tiene {}", guardadas, deAgente, postulacionId,
                rubrica.size());

        cerrarLaCalificacion(postulacion, guardadas);
    }

    /**
     * Deja la prueba en manos de una persona.
     *
     * <p><b>La nota de la etapa no se calcula aquí.</b> Casi nunca están todos los criterios:
     * los de método persona siguen vacíos por diseño, y los que el modelo no pudo juzgar
     * también. Sumar media rúbrica daría un número bajo que parece un juicio y es un hueco.
     * La suma la pide el panel cuando la rúbrica está completa.
     *
     * <p><b>Se mueve aunque no se haya guardado ninguna nota.</b> Puede pasar: una entrega
     * que es solo un video no da para puntuar nada por texto. Dejarla en «calificando» sería
     * esconderla, y hace falta justo lo contrario: que alguien la vea. Quien la abra
     * encuentra la rúbrica entera sin notas, que es exactamente lo que ocurrió.
     */
    private void cerrarLaCalificacion(Postulacion postulacion, int guardadas) {
        if (guardadas == 0) {
            log.warn("PRUEBA_PUESTO no pudo puntuar ningún criterio de la postulación {}: la "
                    + "rúbrica queda entera para una persona", postulacion.getId());
        }
        // Solo desde donde tiene sentido. Si alguien la movió a mano mientras el trabajo
        // estaba en la cola, no se la devuelve hacia atrás.
        if (!CALIFICANDO.equals(postulacion.getEstadoCodigo())) {
            log.info("La postulación {} ya no está en {} sino en «{}»: no se mueve",
                    postulacion.getId(), CALIFICANDO, postulacion.getEstadoCodigo());
            return;
        }
        maquina.transicionar(postulacion, POR_CONFIRMAR, null, null, true, false, null);
    }

    // ==================== Apoyo ====================

    private List<Criterio> laRubricaDe(IntentoPrueba intento) {
        return criterios.findByVersionPlantillaPruebaId(intento.getVersionPlantillaPruebaId());
    }

    private Postulacion postulacion(Long postulacionId) {
        return postulaciones.findById(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
    }

    private String conEvidencia(String explicacion, String evidencia) {
        return esVacio(evidencia) ? explicacion : explicacion + "\nEvidencia: " + evidencia;
    }

    /** Un modelo puede devolver 30 sobre 15, o un negativo. Se acota en vez de fallar. */
    private BigDecimal acotar(BigDecimal valor, BigDecimal maximo) {
        if (valor == null) return null;
        if (valor.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        return valor.compareTo(maximo) > 0
                ? maximo.setScale(2, RoundingMode.HALF_UP)
                : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static <T> List<T> lista(List<T> valor) {
        return valor == null ? List.of() : valor;
    }
}
