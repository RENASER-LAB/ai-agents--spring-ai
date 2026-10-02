package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.CalificacionIaEncolada;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.DefinirPlazoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.EntregaDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.Entregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.NotaCriterioResponse;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.PlazoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.PlazoVigente;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.PonerNotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.RespuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPrueba;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import static com.renaser.ai.ai_engine.vacante.service.impl.ServicioVacantesPanelImpl.CUESTIONARIO_TECNICO;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ServicioCalificacionPruebaImpl implements ServicioCalificacionPrueba {

    private static final String ETAPA = "PRUEBA_PUESTO";

    private final PostulacionRepository postulaciones;
    private final AlcanceSobreLaVacante alcance;
    private final IntentoPruebaRepository intentos;
    private final CriterioRepository criterios;
    private final PreguntaVersionPlantillaRepository preguntasElegidas;
    private final PreguntaPruebaRepository preguntasCatalogo;
    private final RespuestaPruebaRepository respuestas;
    private final NotaCriterioRepository notasCriterio;
    private final VersionPesosRepository versionesPesos;
    private final ColaCalificacionIa cola;
    private final ServicioAuditoria auditoria;
    private final com.renaser.ai.ai_engine.vacante.repository.VacanteRepository vacantes;
    private final com.renaser.ai.ai_engine.perfilintegral.service.impl
            .CalificacionCuestionarioTecnico cuestionarioTecnico;
    private final com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository notasEtapa;
    private final CalificacionPorCriterio calificacion;
    private final EntregableRepository entregables;
    private final EntregableRequeridoRepository entregablesRequeridos;
    private final com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository archivos;
    // La prueba escrita en el editor (V67). Nulos en las pruebas unitarias de las plantillas,
    // que usan el constructor de siempre: solo se tocan en la rama del editor.
    private final com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia calculo;
    private final com.renaser.ai.ai_engine.prueba.service.CierreDeLaPruebaPropia cierre;

    /** El constructor de las plantillas: el que usan sus pruebas unitarias. */
    public ServicioCalificacionPruebaImpl(PostulacionRepository postulaciones, AlcanceSobreLaVacante alcance,
            IntentoPruebaRepository intentos, CriterioRepository criterios,
            PreguntaVersionPlantillaRepository preguntasElegidas, PreguntaPruebaRepository preguntasCatalogo,
            RespuestaPruebaRepository respuestas, NotaCriterioRepository notasCriterio,
            VersionPesosRepository versionesPesos, ColaCalificacionIa cola, ServicioAuditoria auditoria,
            com.renaser.ai.ai_engine.vacante.repository.VacanteRepository vacantes,
            com.renaser.ai.ai_engine.perfilintegral.service.impl.CalificacionCuestionarioTecnico cuestionarioTecnico,
            com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository notasEtapa,
            CalificacionPorCriterio calificacion, EntregableRepository entregables,
            EntregableRequeridoRepository entregablesRequeridos,
            com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository archivos) {
        this(postulaciones, alcance, intentos, criterios, preguntasElegidas, preguntasCatalogo,
                respuestas, notasCriterio, versionesPesos, cola, auditoria, vacantes,
                cuestionarioTecnico, notasEtapa, calificacion, entregables, entregablesRequeridos,
                archivos, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ServicioCalificacionPruebaImpl(PostulacionRepository postulaciones, AlcanceSobreLaVacante alcance,
            IntentoPruebaRepository intentos, CriterioRepository criterios,
            PreguntaVersionPlantillaRepository preguntasElegidas, PreguntaPruebaRepository preguntasCatalogo,
            RespuestaPruebaRepository respuestas, NotaCriterioRepository notasCriterio,
            VersionPesosRepository versionesPesos, ColaCalificacionIa cola, ServicioAuditoria auditoria,
            com.renaser.ai.ai_engine.vacante.repository.VacanteRepository vacantes,
            com.renaser.ai.ai_engine.perfilintegral.service.impl.CalificacionCuestionarioTecnico cuestionarioTecnico,
            com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository notasEtapa,
            CalificacionPorCriterio calificacion, EntregableRepository entregables,
            EntregableRequeridoRepository entregablesRequeridos,
            com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository archivos,
            com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia calculo,
            com.renaser.ai.ai_engine.prueba.service.CierreDeLaPruebaPropia cierre) {
        this.postulaciones = postulaciones;
        this.alcance = alcance;
        this.intentos = intentos;
        this.criterios = criterios;
        this.preguntasElegidas = preguntasElegidas;
        this.preguntasCatalogo = preguntasCatalogo;
        this.respuestas = respuestas;
        this.notasCriterio = notasCriterio;
        this.versionesPesos = versionesPesos;
        this.cola = cola;
        this.auditoria = auditoria;
        this.vacantes = vacantes;
        this.cuestionarioTecnico = cuestionarioTecnico;
        this.notasEtapa = notasEtapa;
        this.calificacion = calificacion;
        this.entregables = entregables;
        this.entregablesRequeridos = entregablesRequeridos;
        this.archivos = archivos;
        this.calculo = calculo;
        this.cierre = cierre;
    }

    /** La prueba de esta postulación, si es una escrita en el editor (V67). */
    private Optional<IntentoPrueba> delEditor(Long postulacionId) {
        return intentos.findByPostulacionId(postulacionId).filter(IntentoPrueba::esDelEditor);
    }

    @Override
    public List<NotaCriterioResponse> verNotas(ContextoUsuario quien, Long postulacionId) {
        /*
          ⚠️ **Leer el desglose ya no pide el permiso de CORREGIRLO.** Pedía `ajustar_nota`,
          que solo tienen Talento y Dirección, así que Responsable de Área abría el embudo de
          su vacante, veía la nota de cada candidato —y desde hoy también las columnas de la
          rúbrica en la tabla— y al pulsar la fila se topaba con un 403 sobre el desglose de
          esa misma nota.

          Ahora pide `abrir_ficha_candidato`, que es exactamente esto —abrir la ficha de
          alguien— y que Responsable de Área ya tiene con alcance SUS_VACANTES. Es el mismo
          permiso con el que `verEntregables`, aquí abajo, deja ver lo que el candidato
          entregó. **Escribir no se toca**: `ajustarNota` sigue pidiendo `ajustar_nota`.
        */
        Postulacion postulacion = laVisible(quien, postulacionId, "abrir_ficha_candidato");
        List<Criterio> rubrica = laRubricaDe(postulacion);
        Map<Long, NotaCriterio> notasPorCriterio = notasCriterio.findByPostulacionId(postulacionId).stream()
                .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity()));

        return rubrica.stream().map(c -> {
            NotaCriterio n = notasPorCriterio.get(c.getId());
            return new NotaCriterioResponse(c.getId(), c.getNombre(),
                    c.getPuntos() == null ? null : c.getPuntos().doubleValue(),
                    n == null || n.getPuntaje() == null ? null : n.getPuntaje().doubleValue(),
                    n == null ? null : n.getExplicacion(),
                    n == null ? null : n.getOrigen());
        }).toList();
    }

    /**
     * Si esta postulación rinde el cuestionario técnico en vez de la prueba del puesto.
     *
     * <p>Lo dice su vacante (V43), y de ahí salen las cuatro bifurcaciones de este servicio:
     * sin ellas, las cuatro pantallas del panel responden 404 sobre un {@code intento_prueba}
     * que no existe, y el equipo se queda sin poder leer ni recalificar lo que su candidato
     * escribió.
     */
    private boolean rindeElCuestionario(Postulacion postulacion) {
        return vacantes.findById(postulacion.getVacanteId())
                .map(v -> CUESTIONARIO_TECNICO.equals(v.getInstrumentoEtapaTecnica()))
                .orElse(false);
    }

    /**
     * Lo que subió, entregable a entregable. Ver {@link ServicioCalificacionPrueba}.
     *
     * <p>Es el mismo recorrido que hace {@code PuentePruebaIaImpl.loQueEntrego} para armar el
     * insumo del agente —los pedidos de su versión, y de cada uno la última que subió—, y por
     * eso repite sus tres guardas sobre el archivo: borrado, sin ruta, o ya inexistente. Un
     * archivo que ya no está tiene que decirlo con palabras, no ofrecer una descarga que
     * contesta 404.
     */
    @Override
    @Transactional(readOnly = true)
    public List<EntregaDeLaPrueba> verEntregables(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "abrir_ficha_candidato");
        // El cuestionario técnico se contesta escribiendo: no hay nada que subir, y eso es
        // una lista vacía y no un error. Lo mismo que hace `laRubricaDe`.
        if (rindeElCuestionario(postulacion)) {
            return List.of();
        }
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba del puesto", "postulación", postulacionId));

        // Ver el CONTENIDO —el enlace que pegó, o el archivo— pide el mismo permiso que
        // descargarlo. Sin él se sigue diciendo qué entregó y cuándo, que es lo que necesita
        // quien solo lleva el seguimiento.
        boolean puedeVerElContenido = quien.tiene("descargar_entregables");

        List<Entregable> subidos = entregables.findByIntentoPruebaId(intento.getId());
        List<EntregableRequerido> pedidos = intento.esDelEditor()
                ? entregablesRequeridos.findByVersionBancoIdOrderByOrdenAscIdAsc(intento.getVersionBancoId())
                : entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(
                        intento.getVersionPlantillaPruebaId());
        return pedidos
                .stream()
                .map(requerido -> pintarEntrega(requerido, ultimaVersionDe(subidos, requerido),
                        puedeVerElContenido))
                .toList();
    }

    /** De un pedido, la última que subió: pudo entregarlo tres veces y vale la última. */
    private Optional<Entregable> ultimaVersionDe(List<Entregable> subidos,
                                                 EntregableRequerido requerido) {
        return subidos.stream()
                .filter(e -> requerido.getId().equals(e.getEntregableRequeridoId()))
                .max(Comparator.comparing(e -> e.getVersion() == null ? 0 : e.getVersion()));
    }

    private EntregaDeLaPrueba pintarEntrega(EntregableRequerido requerido,
                                            Optional<Entregable> entregado,
                                            boolean puedeVerElContenido) {
        if (entregado.isEmpty()) {
            return new EntregaDeLaPrueba(requerido.getId(), requerido.getNombre(),
                    requerido.getDetalle(), requerido.getFormato(), requerido.isEsObligatorio(),
                    false, null, null, null, null, null,
                    requerido.isEsObligatorio() ? "No lo entregó, y era obligatorio"
                                                : "No lo entregó");
        }
        Entregable e = entregado.get();
        if (!puedeVerElContenido) {
            return new EntregaDeLaPrueba(requerido.getId(), requerido.getNombre(),
                    requerido.getDetalle(), requerido.getFormato(), requerido.isEsObligatorio(),
                    true, null, null, null, e.getVersion(), e.getSubidoEn(),
                    "Hace falta el permiso «descargar_entregables» para abrirlo");
        }
        if (e.getArchivoId() == null) {
            return new EntregaDeLaPrueba(requerido.getId(), requerido.getNombre(),
                    requerido.getDetalle(), requerido.getFormato(), requerido.isEsObligatorio(),
                    true, e.getEnlace(), null, null, e.getVersion(), e.getSubidoEn(), null);
        }
        Archivo archivo = archivos.findById(e.getArchivoId()).orElse(null);
        // Las mismas tres guardas del puente del agente: un archivo borrado, sin ruta o que ya
        // no existe se dice, no se ofrece una descarga que va a contestar 404.
        if (archivo == null || archivo.getBorradoEn() != null || archivo.getRuta() == null) {
            return new EntregaDeLaPrueba(requerido.getId(), requerido.getNombre(),
                    requerido.getDetalle(), requerido.getFormato(), requerido.isEsObligatorio(),
                    true, e.getEnlace(), null, null, e.getVersion(), e.getSubidoEn(),
                    "El archivo ya no está guardado");
        }
        return new EntregaDeLaPrueba(requerido.getId(), requerido.getNombre(),
                requerido.getDetalle(), requerido.getFormato(), requerido.isEsObligatorio(),
                true, e.getEnlace(), archivo.getId(), archivo.getNombreOriginal(),
                e.getVersion(), e.getSubidoEn(), null);
    }

    @Override
    public List<RespuestaDePrueba> verRespuestas(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "abrir_ficha_candidato");
        if (rindeElCuestionario(postulacion)) {
            // El mismo contrato: para quien lee, son preguntas con lo que contestó. Que unas
            // vengan de una plantilla de prueba y otras del cuestionario de la vacante es
            // cosa nuestra, no de la pantalla.
            return cuestionarioTecnico.respuestasDe(postulacion).stream()
                    .map(r -> new RespuestaDePrueba(r.preguntaId(), r.codigo(), r.orden(),
                            "ABIERTA", r.enunciado(), r.texto(), r.respondidaEn()))
                    .toList();
        }
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba del puesto", "postulación", postulacionId));
        // La prueba del editor (V67) se lee entera, criterio por criterio, en
        // /prueba-propia: sus preguntas no son del catálogo de las plantillas.
        if (intento.esDelEditor()) {
            return List.of();
        }

        // Las preguntas de SU versión de la plantilla, en el orden en que las vio. Salen de
        // ahí y no del catálogo entero: una versión publicada después puede llevar otras, y
        // lo que hay que enseñar es lo que se le puso delante a esta persona.
        List<Long> ids = preguntasElegidas
                .findByVersionPlantillaPruebaIdOrderByOrden(intento.getVersionPlantillaPruebaId())
                .stream().map(PreguntaVersionPlantilla::getPreguntaPruebaId).toList();
        Map<Long, PreguntaPrueba> porId = preguntasCatalogo.findByIdIn(ids).stream()
                .collect(Collectors.toMap(PreguntaPrueba::getId, Function.identity()));
        Map<Long, RespuestaPrueba> suyas = respuestas.findByIntentoPruebaId(intento.getId())
                .stream().collect(Collectors.toMap(RespuestaPrueba::getPreguntaPruebaId,
                        Function.identity(), (a, b) -> a));

        List<RespuestaDePrueba> salida = new ArrayList<>();
        for (Long id : ids) {
            PreguntaPrueba pregunta = porId.get(id);
            if (pregunta == null) continue;
            RespuestaPrueba respuesta = suyas.get(id);
            // Se emite también la que dejó en blanco: saber que no contestó la cuarta es
            // parte de lo que se revisa, y omitirla la haría invisible.
            salida.add(new RespuestaDePrueba(
                    pregunta.getId(), pregunta.getCodigo(), pregunta.getOrden(),
                    pregunta.getTipo(), pregunta.getEnunciado(),
                    respuesta == null ? null : respuesta.getTexto(),
                    respuesta == null ? null : respuesta.getRespondidaEn()));
        }
        return salida;
    }

    @Override
    public CalificacionIaEncolada calificarConIa(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laQueSePuedeTocar(quien, postulacionId, "ajustar_nota");

        // ⚠️ Con el cuestionario técnico, este botón es la ÚNICA forma de recuperar una
        // calificación que no salió: si la IA estaba apagada al entregar, o el modelo devolvió
        // notas inservibles, la postulación se queda en PRUEBA_CALIFICANDO y su etapa —el 30%
        // de la decisión— desaparecería en silencio del puntaje final.
        if (rindeElCuestionario(postulacion)) {
            if (postulacion.getEvaluacionTecnicaId() == null) {
                throw new IllegalStateException("Esta persona todavía no tiene cuestionario "
                        + "técnico: se le crea al avanzarla a la etapa de la prueba");
            }
            if (!cola.encolarCuestionarioTecnico(postulacionId)) {
                return new CalificacionIaEncolada("SIN_CAMBIOS",
                        "No se pidió nada: o ya lo calificó el agente, o hay un trabajo en "
                                + "marcha ahora mismo, o la calificación con IA está apagada.");
            }
            return new CalificacionIaEncolada("ENCOLADA",
                    "La calificación del cuestionario técnico quedó en cola. Tarda decenas de "
                            + "segundos: vuelve a consultar la nota para verla.");
        }

        // Lo indispensable, dicho aquí y no tres reintentos después. El agente se plantaría
        // igual al pedir el insumo, pero entonces el mensaje se quedaría en el registro en
        // vez de llegar a quien apretó el botón.
        IntentoPrueba intento = intentos.findByPostulacionId(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba del puesto", "postulación", postulacionId));
        if (intento.esDelEditor()) {
            return calificarConIaDelEditor(postulacion, intento);
        }
        if (intento.getEntregadoEn() == null) {
            throw new IllegalStateException(
                    "Esta prueba todavía no está entregada: calificarla ahora daría una nota "
                            + "de lo que el candidato lleve escrito a medias");
        }

        // Si la rúbrica no le reserva nada al agente, encolar solo gastaría una vuelta de
        // cola para que el agente descubra lo mismo y termine sin hacer nada.
        long paraElAgente = laRubricaDe(postulacion).stream()
                .filter(c -> "AGENTE".equals(c.getMetodoVerificacion()))
                .count();
        if (paraElAgente == 0) {
            return new CalificacionIaEncolada("SIN_CAMBIOS",
                    "La rúbrica de esta prueba no tiene ningún criterio marcado para el agente: "
                            + "la califica una persona entera.");
        }

        if (!cola.encolarPruebaPuesto(postulacionId)) {
            return new CalificacionIaEncolada("SIN_CAMBIOS",
                    "No se pidió nada: o ya está calificada por el agente, o hay un trabajo en "
                            + "marcha ahora mismo.");
        }
        return new CalificacionIaEncolada("ENCOLADA",
                "La calificación quedó en cola. Tarda decenas de segundos: vuelve a consultar "
                        + "las notas para verla.");
    }

    /**
     * Pedir (o volver a pedir) la IA para los criterios de IA que siguen pendientes en una
     * prueba del editor. Si la primera calificación ya terminó y dejó alguno sin nota, se pide
     * por el carril de la recalificación: el normal no repite lo terminado.
     */
    private CalificacionIaEncolada calificarConIaDelEditor(Postulacion postulacion, IntentoPrueba intento) {
        if (intento.isNoCompletada()) {
            throw new IllegalStateException("Esta prueba quedó sin completar: no se califica.");
        }
        if (intento.getEntregadoEn() == null) {
            throw new IllegalStateException("Esta prueba todavía no está entregada: calificarla "
                    + "ahora daría una nota de lo que el candidato lleve escrito a medias");
        }
        var r = calculo.calcular(intento);
        boolean faltaLaIa = r.criterios().stream().anyMatch(c -> c.pendiente() && c.esDeIa());
        if (!faltaLaIa) {
            return new CalificacionIaEncolada("SIN_CAMBIOS", "No queda ningún criterio de IA sin "
                    + "nota: lo que falta, si falta algo, lo califica una persona.");
        }
        Long postulacionId = postulacion.getId();
        String motivo = cola.porQueNoSePuedeUsarLaIa(postulacion.getOrganizacionId());
        if (motivo != null) {
            return new CalificacionIaEncolada("SIN_CAMBIOS", motivo);
        }
        ColaCalificacionIa.Seguimiento ultima = cola.calificacionDePrueba(List.of(postulacionId))
                .get(postulacionId);
        if (ultima != null && "EN_CURSO".equals(ultima.estado())) {
            return new CalificacionIaEncolada("SIN_CAMBIOS", "Ya hay una calificación en marcha ahora mismo.");
        }
        boolean encolada = cola.encolarPruebaPuesto(postulacionId) || cola.recalificarPrueba(postulacionId);
        if (!encolada) {
            return new CalificacionIaEncolada("SIN_CAMBIOS", "Ya hay una calificación en marcha ahora mismo.");
        }
        return new CalificacionIaEncolada("ENCOLADA", "La calificación quedó en cola. Tarda "
                + "decenas de segundos: vuelve a abrir la prueba para verla.");
    }

    @Override
    @Transactional
    public void ponerNota(ContextoUsuario quien, Long postulacionId, Long criterioId, PonerNotaCriterio datos) {
        Postulacion postulacion = laQueSePuedeTocar(quien, postulacionId, "ajustar_nota");
        if (delEditor(postulacion.getId()).isPresent()) {
            throw new IllegalArgumentException("En esta prueba se ajusta la parte calificada de "
                    + "cada criterio desde su desglose (/prueba-propia/criterios/{id}/nota)");
        }
        Criterio criterio = criterios.findById(criterioId)
                .orElseThrow(() -> new ResourceNotFoundException("Criterio", "id", criterioId));
        if (!laRubricaDe(postulacion).contains(criterio)) {
            throw new IllegalArgumentException("Ese criterio no pertenece a la rúbrica de esta prueba");
        }
        BigDecimal maximo = criterio.getPuntos();
        BigDecimal puntaje = BigDecimal.valueOf(datos.puntaje());
        if (puntaje.compareTo(BigDecimal.ZERO) < 0 || (maximo != null && puntaje.compareTo(maximo) > 0)) {
            throw new IllegalArgumentException(
                    "El puntaje de «%s» tiene que estar entre 0 y %s".formatted(criterio.getNombre(), maximo));
        }

        NotaCriterio fila = notasCriterio.findByPostulacionIdAndCriterioId(postulacionId, criterioId)
                .orElseGet(() -> NotaCriterio.builder()
                        .postulacionId(postulacionId)
                        .criterioId(criterioId)
                        .creadoEn(Instant.now())
                        .build());
        boolean yaExistia = fila.getId() != null;
        fila.setPuntaje(puntaje);
        fila.setExplicacion(datos.explicacion());
        fila.setOrigen("PERSONA");
        fila.setCalificadaPorUsuarioId(quien.usuarioId());
        if (yaExistia) {
            fila.setAjustadaPorUsuarioId(quien.usuarioId());
            fila.setMotivoAjuste(datos.explicacion());
            fila.setAjustadaEn(Instant.now());
        }
        notasCriterio.save(fila);
    }

    @Override
    @Transactional
    public BigDecimal calcularNotaEtapa(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laQueSePuedeTocar(quien, postulacionId, "ajustar_nota");
        // El cuestionario técnico no se pondera por rúbrica: su nota es el índice sobre las
        // calificaciones de sus respuestas. Recalcularlo aquí le da al equipo la misma
        // palanca que tiene con la prueba del puesto — pedirlo cuando ya están las notas.
        Optional<IntentoPrueba> delEditor = delEditor(postulacion.getId());
        if (delEditor.isPresent()) {
            BigDecimal nota = cierre.recalcular(delEditor.get(), false);
            if (nota == null) {
                throw new IllegalStateException("Todavía no se puede poner la nota: falta la "
                        + "parte calificada de algún criterio");
            }
            return nota;
        }
        if (rindeElCuestionario(postulacion)) {
            cuestionarioTecnico.calificarEtapa(postulacion);
            return notasEtapa.findByPostulacionIdAndEtapaCodigo(postulacion.getId(), ETAPA)
                    .map(n -> n.getPuntaje())
                    .orElseThrow(() -> new IllegalStateException(
                            "Todavía no se puede poner la nota: falta la calificación de "
                                    + "alguna respuesta del cuestionario"));
        }
        // Se delega en la versión compartida a propósito. Aquí hubo una copia de la misma
        // suma, y la copia se desvió: sumaba TODAS las notas de criterio de la postulación
        // en vez de las de esta rúbrica. Como `nota_criterio` es una sola tabla para las tres
        // etapas que puntúan por criterio, a la nota de la prueba se le pegaban las del
        // perfil: un candidato con 50 sobre 100 salió con 675. Una rúbrica bien acotada no
        // basta si quien suma no la mira.
        return calificacion.calcularNotaEtapa(postulacion, ETAPA, laRubricaDe(postulacion));
    }

    @Override
    @Transactional
    public PlazoPrueba definirPlazo(ContextoUsuario quien, Long postulacionId,
                                    DefinirPlazoPrueba datos) {
        Postulacion postulacion = laQueSePuedeTocar(quien, postulacionId, "mover_postulacion");
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba del puesto", "postulación", postulacionId));
        if (intento.getEntregadoEn() != null) {
            throw new IllegalStateException(
                    "Esta prueba ya se entregó: cambiarle el plazo ahora no cambia nada");
        }
        // Una fecha ya pasada se la entregaría sola en el siguiente barrido. Si de verdad se
        // le quiere cerrar, hay una transición para eso y deja dicho por qué.
        if (datos.venceEn().isBefore(Instant.now())) {
            throw new IllegalArgumentException(
                    "Esa fecha ya pasó: al candidato se le entregaría la prueba sola");
        }

        Instant anterior = intento.getVenceEn();
        intento.setVenceEn(datos.venceEn());
        // Queda marcado como suyo: si después se mueve la fecha de la convocatoria, a esta
        // persona no se la toca. Sin la marca, «más horas para este candidato» se perdería
        // en el siguiente cambio de la vacante y nadie lo notaría (V32).
        intento.setPlazoPropio(true);
        intentos.save(intento);

        auditoria.registrar(quien.organizacionId(), quien, "definir_plazo_prueba",
                "intento_prueba", intento.getId(),
                anterior == null ? null : Map.of("venceEn", anterior.toString()),
                Map.of("venceEn", datos.venceEn().toString()), datos.motivo());

        return new PlazoPrueba(postulacionId, intento.getVenceEn(),
                intento.getIniciadoEn() != null);
    }

    /**
     * Lo que rige hoy para esta persona. Ver {@link ServicioCalificacionPrueba#verPlazo}.
     *
     * <p>⚠️ <b>Ninguna de las dos ausencias es un error.</b> Ni la vacante del cuestionario
     * técnico —que no usa {@code intento_prueba}— ni quien todavía no llegó a la etapa tienen
     * nada que enseñar, y {@code definirPlazo} contesta a los dos «Prueba del puesto no
     * encontrada», que en la ficha se lee como una avería. Aquí se contestan con
     * {@code existeIntento=false} para que la pantalla diga cuál de los dos casos es.
     */
    @Override
    @Transactional(readOnly = true)
    public PlazoVigente verPlazo(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "abrir_ficha_candidato");
        /*
          ⚠️ **Por id Y organización, no por id suelto.** La dueña sale de la propia
          postulación —que `laVisible` ya resolvió contra quien pregunta—, que es el mismo
          criterio de `ServicioPruebaImpl.minutosDeLaVacante`: así la cadena entera cuelga de
          una sola cosa verificada y no de dos que podrían no casar. Con una sola empresa las
          dos formas se comportan igual; con dos, la suelta enseña el plazo de la otra.

          Una vacante que no aparezca se lee como «sin instrumento conocido» en vez de
          reventar: la ficha tiene que abrirse igual.
        */
        Vacante vacante = vacantes
                .findByIdAndOrganizacionId(postulacion.getVacanteId(),
                        postulacion.getOrganizacionId())
                .orElse(null);
        String instrumento = vacante == null ? null : vacante.getInstrumentoEtapaTecnica();
        if (CUESTIONARIO_TECNICO.equals(instrumento)) {
            return new PlazoVigente(false, null, null, null, null, instrumento);
        }
        return intentos.findByPostulacionId(postulacion.getId())
                .map(intento -> new PlazoVigente(true, intento.getVenceEn(),
                        origenDe(intento, vacante), intento.getIniciadoEn(),
                        intento.getEntregadoEn(), instrumento))
                .orElseGet(() -> new PlazoVigente(false, null, null, null, null, instrumento));
    }

    /**
     * De dónde sale la fecha que tiene este intento.
     *
     * <p>No se guarda en ninguna columna —{@code plazo_propio} solo marca una de las tres—,
     * así que se deduce, y el orden importa:
     *
     * <ol>
     *   <li><b>{@code PROPIO}</b>: alguien se la puso a mano. Se mira primero porque esa
     *       fecha puede coincidir con la de la vacante y seguiría siendo suya: mover la de la
     *       convocatoria no se la toca, y la ficha tiene que seguir diciendo «a mano».
     *   <li><b>{@code VACANTE}</b>: es exactamente la fecha común de la convocatoria.
     *   <li><b>{@code RELOJ}</b>: ya empezó y su fecha no es la de la vacante, así que la
     *       calculó el servidor al abrir —sus minutos, o los días de su plantilla—.
     * </ol>
     *
     * <p>Quien no ha empezado y tiene una fecha que ya no es la de la vacante se lee como
     * {@code VACANTE}: es la que heredó al entrar en la etapa, y el reloj todavía no ha
     * corrido para él.
     */
    private String origenDe(IntentoPrueba intento, Vacante vacante) {
        if (intento.getVenceEn() == null) {
            return null;
        }
        if (intento.isPlazoPropio()) {
            return "PROPIO";
        }
        Instant deLaVacante = vacante == null ? null : vacante.getPruebaCierraEn();
        if (intento.getVenceEn().equals(deLaVacante)) {
            return "VACANTE";
        }
        return intento.getIniciadoEn() != null ? "RELOJ" : "VACANTE";
    }

    // ============ Apoyo ============

    /**
     * La rúbrica con la que se puntúa esta prueba.
     *
     * <p>⚠️ El cuestionario técnico <b>no tiene</b>: se califica pregunta a pregunta contando
     * criterios, no repartiendo cien puntos entre unos cuantos apartados. Devolver vacío en vez
     * de reventar es lo que deja que la pantalla de notas se abra igual y enseñe lo que sí hay.
     */
    private List<Criterio> laRubricaDe(Postulacion postulacion) {
        if (rindeElCuestionario(postulacion)) {
            return List.of();
        }
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Prueba del puesto", "postulación", postulacion.getId()));
        // La prueba del editor (V67) no tiene rúbrica de la tabla `criterio`: sus criterios
        // son de su versión y se leen en /prueba-propia.
        if (intento.esDelEditor()) {
            return List.of();
        }
        // Ordenada: esto acaba en una pantalla y en las columnas del ranking, y sin `orden`
        // la rúbrica sale como la devuelva la base, que puede cambiar entre dos peticiones.
        return criterios.findByVersionPlantillaPruebaIdOrderByOrden(
                intento.getVersionPlantillaPruebaId());
    }

    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId, String permiso) {
        return alcance.laPostulacionVisible(quien, postulacionId, permiso);
    }

    /**
     * La postulación sobre la que se va a <b>escribir</b>: visible, y de una vacante que sigue
     * existiendo.
     *
     * <p>⚠️ <b>Las cuatro escrituras de este servicio pasan por aquí</b> —el plazo de la
     * persona, la nota de un criterio, la nota de la etapa y la calificación con IA— y ninguna
     * la miraba. Con el formulario «El plazo de esta persona» abierto desde antes de eliminar
     * la vacante, «Guardar el plazo» movía el intento, lo marcaba como propio y dejaba
     * auditoría sobre una postulación cerrada de una convocatoria retirada, cuando la fecha de
     * la vacante y mover la postulación ya contestaban 404. Es la misma respuesta que
     * esas dos: una eliminada no existe, y eso es un 404 y no un 409 (V60).
     *
     * <p>Las lecturas siguen por {@link #laVisible}, igual que la ficha de la postulación.
     */
    private Postulacion laQueSePuedeTocar(ContextoUsuario quien, Long postulacionId,
                                          String permiso) {
        Postulacion postulacion = laVisible(quien, postulacionId, permiso);
        alcance.exigirQueSuVacanteSigaExistiendo(postulacion);
        return postulacion;
    }
}
