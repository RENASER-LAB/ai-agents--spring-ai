package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.archivo.service.TiposDeArchivo;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ConsignaAdjunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CriterioDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EntregableDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.FechaLimiteDeLaPrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.OpcionDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PreguntaDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PreguntaElegida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PruebaDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDeOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDePregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ResumenDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.TextoDe;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregablePropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PosicionDePregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PropuestaDePrueba;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PropuestaPreguntas;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.PreguntasInvalidasException;
import com.renaser.ai.ai_engine.perfilintegral.service.impl.EditorDeVersionPropia;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.OpcionAValidar;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.PreguntaAValidar;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AgregarDeLaPropuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CambiarPuntosDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CorregirInstruccionesDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.FijarFechaLimite;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarCriterioDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarDatosDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarEntregable;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarPreguntaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PuntosDeCriterio;
import com.renaser.ai.ai_engine.prueba.entity.EntregableCubrePregunta;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.NotaCriterioPrueba;
import com.renaser.ai.ai_engine.prueba.repository.CriterioBancoEntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableCubrePreguntaRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.NotaCriterioPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.CriterioDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.PreguntaDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.Resultado;
import com.renaser.ai.ai_engine.prueba.service.CierreDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.FechaLimiteDeLaVacante;
import com.renaser.ai.ai_engine.prueba.service.ReglasDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.ServicioPruebaPropia;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ver {@link ServicioPruebaPropia}. Lo que comparte con el editor de las preguntas propias
 * vive en {@link EditorDeVersionPropia}; aquí queda lo suyo: el caso, el tiempo, los
 * entregables, la parte calificada de cada criterio y su vara, que se congela en la primera
 * rendición.
 */
@Service
@RequiredArgsConstructor
public class ServicioPruebaPropiaImpl extends EditorDeVersionPropia implements ServicioPruebaPropia {

    static final String PROPOSITO = "PRUEBA_PUESTO";
    public static final String PRUEBA_PROPIA = "PRUEBA_PROPIA";

    /** «Una vacante, una versión», desde la primera rendición (decisión 9). */
    private static final String VARA_QUIETA = "Alguien ya empezó a rendir esta prueba y su "
            + "contenido no se cambia: todos sus candidatos se miden con la misma vara. Solo se "
            + "pueden cambiar los puntos y las instrucciones de la IA, y cualquiera de los dos "
            + "vuelve a calcular la nota de todos.";

    private static final Textos TEXTOS = new Textos(PROPOSITO, "Prueba técnica · ", VARA_QUIETA,
            "No hay borrador que cambiar: abre uno agregando un criterio, una pregunta o un entregable",
            "No hay borrador que publicar: escribe la prueba primero",
            "La prueba no se puede publicar todavía: ",
            "Esta vacante todavía no tiene la prueba publicada", "Prueba publicada de la vacante",
            "la prueba publicada de esta vacante", "publicar_prueba_propia",
            "corregir_instrucciones_ia_prueba", "copiar_prueba_propia");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AlcanceSobreLaVacante alcance;
    private final Permisos permisos;
    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;
    private final VersionBancoRepository versionesBanco;
    private final CriterioBancoRepository criteriosBanco;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final EntregableRequeridoRepository entregables;
    // Solo para lo de antes de la V68: lo que miraba cada criterio, marcado a mano.
    private final CriterioBancoEntregableRepository miradas;
    private final EntregableCubrePreguntaRepository cubiertas;
    private final IntentoPruebaRepository intentos;
    private final NotaCriterioPruebaRepository notas;
    private final PropuestaPreguntasRepository propuestas;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final CalificacionDeLaPruebaPropia calculo;
    private final CierreDeLaPruebaPropia cierre;
    private final ColaCalificacionIa cola;
    private final ServicioAuditoria auditoria;
    private final FechaLimiteDeLaVacante fechaLimite;

    // ============================== Leer ==============================

    @Override
    protected EditorDePreguntas editor(ContextoUsuario quien, Vacante vacante) {
        VersionBanco borrador = versionesBanco.pruebaPropiaDe(vacante.getId(), BORRADOR).orElse(null);
        VersionBanco publicada = versionesBanco.pruebaPropiaDe(vacante.getId(), PUBLICADA).orElse(null);
        Resultado delBorrador = borrador == null ? null : calculo.estructura(borrador.getId());
        Resultado deLaPublicada = publicada == null ? null : calculo.estructura(publicada.getId());
        // Lo que frena publicar el borrador incluye la fecha límite, que es de la vacante.
        return new EditorDePreguntas(vacante.getId(), vacante.getTitulo(), nivelDe(vacante),
                vacante.getInstrumentoEtapaTecnica(), true,
                puedeEditar(quien, vacante), yaSeRindio(vacante),
                delBorrador == null ? null : comoVersion(delBorrador, ReglasDeLaPrueba
                        .faltasParaPublicar(delBorrador, vacante.getPruebaCierraEn(), Instant.now())),
                deLaPublicada == null ? null : comoVersion(deLaPublicada,
                        ReglasDeLaPrueba.faltasParaPublicar(deLaPublicada)),
                resumen(delBorrador, deLaPublicada),
                publicada == null ? null : recalificacionDe(vacante, publicada),
                PROPOSITO,
                new FechaLimiteDeLaPrueba(vacante.getPruebaCierraEn(), laFechaPideMotivo(vacante)));
    }

    /**
     * Cambiar la fecha pide un motivo con la prueba publicada y alguien ya en la etapa
     * técnica (punto 10): entonces hay a quien se le mueve el plazo. Antes, no.
     */
    private boolean laFechaPideMotivo(Vacante vacante) {
        return versionesBanco.pruebaPropiaDe(vacante.getId(), PUBLICADA).isPresent()
                && intentos.algunoDeLaVacante(vacante.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenDePreguntas resumenDe(Long vacanteId) {
        VersionBanco borrador = versionesBanco.pruebaPropiaDe(vacanteId, BORRADOR).orElse(null);
        VersionBanco publicada = versionesBanco.pruebaPropiaDe(vacanteId, PUBLICADA).orElse(null);
        return resumen(borrador == null ? null : calculo.estructura(borrador.getId()),
                publicada == null ? null : calculo.estructura(publicada.getId()));
    }

    /**
     * «Sin prueba», «Borrador · 70 de 100 puntos · 2 entregables» o «Publicada · 5 criterios ·
     * 2 entregables · 90 min». Con una publicada, lo que rinde quien postula manda sobre el
     * taller, aunque haya un borrador abierto.
     */
    private static ResumenDePreguntas resumen(Resultado borrador, Resultado publicada) {
        Resultado r = publicada != null ? publicada : borrador;
        if (r == null) {
            return new ResumenDePreguntas("SIN_PRUEBA", null, null, null, null, null, null, null);
        }
        VersionBanco v = r.version();
        boolean cronometrada = ReglasDeLaPrueba.CRONOMETRADA.equals(v.getModalidad());
        return new ResumenDePreguntas(publicada != null ? "PUBLICADA" : BORRADOR, r.total(),
                r.criterios().size(), r.todas().size(), r.entregables().size(),
                cronometrada ? v.getDuracionMinutos() : null,
                ReglasDeLaPrueba.PLAZO_ABIERTO.equals(v.getModalidad()) ? v.getPlazoDias() : null,
                r.esCuestionario());
    }

    // ============================== El borrador ==============================

    @Override
    @Transactional
    public EditorDePreguntas guardarDatos(ContextoUsuario quien, Long vacanteId,
                                          GuardarDatosDeLaPrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        String modalidad = textoONulo(datos.modalidad());
        List<String> faltas = ReglasDeLaPrueba.formaDelTiempo(modalidad, datos.duracionMinutos());
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("El tiempo no se puede guardar así", faltas);
        }
        VersionBanco borrador = elBorrador(vacante);
        borrador.setGuiaCalificacion(textoONulo(datos.guiaCalificacion()));
        borrador.setEnunciado(textoONulo(datos.enunciado()));
        borrador.setMateriales(textoONulo(datos.materiales()));
        borrador.setHerramientasPermitidas(textoONulo(datos.herramientasPermitidas()));
        borrador.setModalidad(modalidad);
        // Solo el número de su modalidad: unos minutos en una sin cronómetro mentirían. Y sin
        // días (V68): «Sin cronómetro» se trabaja hasta la fecha límite de la vacante.
        borrador.setDuracionMinutos(ReglasDeLaPrueba.CRONOMETRADA.equals(modalidad)
                ? datos.duracionMinutos() : null);
        borrador.setPlazoDias(null);
        versionesBanco.save(borrador);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas subirConsigna(ContextoUsuario quien, Long vacanteId, MultipartFile archivo) {
        Vacante vacante = laEditable(quien, vacanteId);
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Falta el archivo del enunciado");
        }
        // PDF o Word, como la consigna de las plantillas. Lo vuelve a mirar el almacén, que es
        // el embudo de los bytes; aquí se dice antes de tocar nada.
        TiposDeArchivo.exigirValido(archivo.getOriginalFilename(), archivo.getContentType());
        VersionBanco borrador = elBorrador(vacante);
        Archivo guardado = almacen.guardar(vacante.getOrganizacionId(), archivo);
        borrador.setConsignaArchivoId(guardado.getId());
        // El enlace largo que pega el aviso PRUEBA_DISPONIBLE. Un almacén sin enlaces (el de
        // memoria de las pruebas) deja el aviso sin él, igual que con las plantillas.
        borrador.setUrlConsigna(almacen.urlDeConsigna(guardado)
                .map(AlmacenArchivos.EnlaceFirmado::url).orElse(null));
        versionesBanco.save(borrador);
        auditoria.registrar(quien.organizacionId(), quien, "subir_consigna_prueba_propia",
                "version_banco", borrador.getId(), null,
                Map.of("archivoId", guardado.getId(),
                        "nombreOriginal", String.valueOf(guardado.getNombreOriginal())), null);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas quitarConsigna(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        borrador.setConsignaArchivoId(null);
        borrador.setUrlConsigna(null);
        versionesBanco.save(borrador);
        return editor(quien, vacante);
    }

    // ---------- Criterios ----------

    @Override
    @Transactional
    public EditorDePreguntas agregarCriterio(ContextoUsuario quien, Long vacanteId,
                                            GuardarCriterioDePrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        // Un criterio nuevo no tiene cerradas: todo lo que vale es, de momento, calificado.
        exigirPuntosDelCriterio(datos, 0);
        VersionBanco borrador = elBorrador(vacante);
        // Lo que mira no se escribe: sale del alcance de los entregables (V68).
        nuevoCriterio(borrador, datos.nombre(), datos.queEvalua(),
                entero(datos.puntos()), textoONulo(datos.calificador()));
        return editor(quien, vacante);
    }

    /**
     * Lo que se escribe es lo que vale el criterio entero (V69); su parte calificada se deduce
     * de sus cerradas de hoy y, desde aquí, se ajusta sola si cambian: el total se mantiene.
     */
    @Override
    @Transactional
    public EditorDePreguntas editarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                           GuardarCriterioDePrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        CriterioBanco criterio = elCriterio(borrador, criterioId);
        int cerradas = cerradasDe(borrador, criterio.getId());
        exigirPuntosDelCriterio(datos, cerradas);
        criterio.setNombre(datos.nombre().strip());
        criterio.setQueEvalua(textoONulo(datos.queEvalua()));
        int total = entero(datos.puntos());
        int calificada = Math.max(0, total - cerradas);
        criterio.setPuntosDelCriterio(total);
        criterio.setPuntosCalificados(calificada);
        criterio.setCalificador(calificada > 0 ? textoONulo(datos.calificador()) : null);
        criteriosBanco.save(criterio);
        return editor(quien, vacante);
    }

    /** Lo que suman hoy las cerradas de un criterio del borrador. */
    private int cerradasDe(VersionBanco borrador, Long criterioId) {
        return preguntas.findByVersionBancoIdOrderByOrden(borrador.getId()).stream()
                .filter(p -> criterioId.equals(p.getCriterioBancoId()))
                .filter(p -> !ReglasDePuntos.ABIERTA.equals(p.getTipo()) && p.getPuntos() != null)
                .mapToInt(Pregunta::getPuntos)
                .sum();
    }

    // ---------- Preguntas ----------

    @Override
    @Transactional
    public EditorDePreguntas agregarPregunta(ContextoUsuario quien, Long vacanteId,
                                            GuardarPreguntaDePrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        exigirForma(datos);
        VersionBanco borrador = elBorrador(vacante);
        Long criterioId = datos.criterioId();
        if (criterioId != null) {
            elCriterio(borrador, criterioId);
        } else if (criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).isEmpty()) {
            // Sin ningún criterio, la primera pregunta crea «General», como en la fase 1. Vale
            // lo que esa pregunta (V69): solo cerradas, sin parte calificada que decir.
            criterioId = nuevoCriterio(borrador, "General", null,
                    puntosDe(datos.tipo(), datos.puntos()), 0, null).getId();
        }
        nuevaPregunta(borrador, datos.tipo(), datos.enunciado(), puntosDe(datos.tipo(), datos.puntos()),
                criterioId, datos.queDebeTener(), comoOpciones(datos.opciones()));
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas editarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                           GuardarPreguntaDePrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        exigirForma(datos);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        Pregunta pregunta = laPregunta(borrador, preguntaId);
        reescribirPregunta(borrador, pregunta, new PreguntaEscrita(datos.tipo(), datos.enunciado(),
                puntosDe(datos.tipo(), datos.puntos()), datos.criterioId(), datos.queDebeTener(),
                comoOpciones(datos.opciones())));
        return editor(quien, vacante);
    }

    // ---------- Entregables ----------

    /**
     * El alcance de un entregable, ya comprobado contra el borrador (V68).
     *
     * @param alcance    PREGUNTA, TODA_LA_PRUEBA o PREGUNTAS
     * @param preguntaId la pregunta de la que es el archivo, si es PREGUNTA
     * @param cubre      las preguntas que reúne, si es PREGUNTAS
     */
    private record Alcance(String alcance, Long preguntaId, List<Long> cubre) {

        static Alcance deTodaLaPrueba() {
            return new Alcance(EntregableRequerido.TODA_LA_PRUEBA, null, List.of());
        }

        static Alcance deLasPreguntas(List<Long> cubre) {
            return new Alcance(EntregableRequerido.PREGUNTAS, null, List.copyOf(cubre));
        }
    }

    @Override
    @Transactional
    public EditorDePreguntas agregarEntregable(ContextoUsuario quien, Long vacanteId,
                                              GuardarEntregable datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        exigirFormaDelEntregable(datos);
        VersionBanco borrador = elBorrador(vacante);
        nuevoEntregable(borrador, datos.nombre(), datos.detalle(), datos.formato(),
                datos.obligatorio(), datos.queDebeTener(), elAlcance(borrador, datos, null));
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas editarEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId,
                                             GuardarEntregable datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        exigirFormaDelEntregable(datos);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        EntregableRequerido e = elEntregable(borrador, entregableId);
        Alcance alcance = elAlcance(borrador, datos, e.getId());
        e.setNombre(datos.nombre().strip());
        e.setDetalle(datos.detalle() == null ? "" : datos.detalle().strip());
        e.setFormato(datos.formato());
        e.setEsObligatorio(datos.obligatorio());
        e.setQueDebeTener(textoONulo(datos.queDebeTener()));
        e.setAlcance(alcance.alcance());
        e.setPreguntaId(alcance.preguntaId());
        // Un entregable de antes (con «Mira» a mano) deja de mirarse a mano al darle alcance.
        miradas.deleteByEntregableRequeridoId(e.getId());
        cubiertas.deleteByEntregableRequeridoId(e.getId());
        cubiertas.flush();
        entregables.save(e);
        ponerCubiertas(e.getId(), alcance.cubre());
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas quitarEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId) {
        Vacante vacante = laEditable(quien, vacanteId);
        EntregableRequerido e = elEntregable(elBorradorQueYaExiste(vacante), entregableId);
        soltarYBorrar(e);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas moverEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId,
                                            Mover datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        List<EntregableRequerido> todos = new ArrayList<>(
                entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()));
        int i = indiceDe(todos, entregableId, EntregableRequerido::getId, "Entregable");
        int j = i + paso(datos);
        if (j >= 0 && j < todos.size()) {
            renumerar(todos, i, j, EntregableRequerido::setOrden);
            entregables.saveAll(todos);
        }
        return editor(quien, vacante);
    }

    // ---------- La fecha límite ----------

    @Override
    @Transactional
    public EditorDePreguntas fijarFechaLimite(ContextoUsuario quien, Long vacanteId, FijarFechaLimite datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        if (!PRUEBA_PROPIA.equals(vacante.getInstrumentoEtapaTecnica())) {
            throw new IllegalStateException("Esta vacante no rinde la prueba del editor: su fecha "
                    + "se fija en «Plazos de la prueba», en la vacante");
        }
        List<String> faltas = new ArrayList<>();
        Instant cierraEn = datos.cierraEn();
        if (cierraEn == null) {
            faltas.add(ReglasDeLaPrueba.FALTA_LA_FECHA);
        } else if (!cierraEn.isAfter(Instant.now())) {
            // Una fecha ya pasada entregaría sola, en el siguiente barrido, la prueba de todos.
            faltas.add("Esa fecha ya pasó: la fecha límite tiene que ser futura.");
        }
        String motivo = textoONulo(datos.motivo());
        boolean pideMotivo = laFechaPideMotivo(vacante);
        if (pideMotivo && motivo == null) {
            faltas.add("Hay personas en la etapa técnica: cambiar la fecha pide un motivo, que "
                    + "queda registrado.");
        }
        exigirSinFaltas("La fecha límite no se guardó: ", faltas);
        Instant anterior = vacante.getPruebaCierraEn();
        if (Objects.equals(cierraEn, anterior)) {
            return editor(quien, vacante);
        }
        FechaLimiteDeLaVacante.Movidos movidos = fechaLimite.fijar(vacante, cierraEn);
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("pruebaCierraEn", String.valueOf(cierraEn));
        despues.put("intentosMovidos", movidos.movidos());
        despues.put("intentosConPlazoPropio", movidos.conPlazoPropio());
        auditoria.registrar(quien.organizacionId(), quien, "fijar_fecha_limite_prueba_propia",
                "vacante", vacante.getId(),
                anterior == null ? null : Map.of("pruebaCierraEn", anterior.toString()),
                despues, motivo);
        return editor(quien, vacante);
    }

    // ============================== Con la versión publicada ==============================

    @Override
    @Transactional
    public CambioAplicado corregirInstrucciones(ContextoUsuario quien, Long vacanteId,
                                                CorregirInstruccionesDePrueba datos) {
        return corregir(quien, vacanteId, datos.guiaCalificacion(), datos.criterios(), datos.preguntas(),
                (publicada, antes, despues) -> corregirEntregables(publicada, datos.entregables(), antes,
                        despues));
    }

    /** El «qué debe tener» de los entregables: lo que la prueba corrige además del banco. */
    private void corregirEntregables(VersionBanco publicada, List<TextoDe> textos,
                                     Map<String, Object> antes, Map<String, Object> despues) {
        Map<Long, EntregableRequerido> deLaVersion = entregables
                .findByVersionBancoIdOrderByOrdenAscIdAsc(publicada.getId()).stream()
                .collect(Collectors.toMap(EntregableRequerido::getId, Function.identity()));
        for (TextoDe t : lista(textos)) {
            EntregableRequerido e = deLaVersion.get(t.id());
            if (e == null) {
                throw new IllegalArgumentException("El entregable " + t.id()
                        + " no es de la prueba publicada de esta vacante");
            }
            String nuevo = textoONulo(t.texto());
            if (!Objects.equals(nuevo, e.getQueDebeTener())) {
                antes.put("entregable " + e.getId() + " · qué debe tener", String.valueOf(e.getQueDebeTener()));
                despues.put("entregable " + e.getId() + " · qué debe tener", String.valueOf(nuevo));
                e.setQueDebeTener(nuevo);
                entregables.save(e);
            }
        }
    }

    @Override
    @Transactional
    public CambioAplicado cambiarPuntos(ContextoUsuario quien, Long vacanteId, CambiarPuntosDePrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco publicada = laPublicada(vacante);
        exigirQueNoHayaRecalificacionEnCurso(guiasDeLaIa(vacante, publicada).keySet());

        Resultado r = calculo.estructura(publicada.getId());
        Map<Long, PreguntaDeLaPrueba> porId = r.todas().stream()
                .collect(Collectors.toMap(pc -> pc.pregunta().getId(), Function.identity()));
        Map<Long, CriterioDeLaPrueba> criterioPorId = r.criterios().stream()
                .collect(Collectors.toMap(c -> c.criterio().getId(), Function.identity()));

        Map<Long, BigDecimal> puntosNuevos = new HashMap<>();
        Map<Long, BigDecimal> opcionesNuevas = new HashMap<>();
        for (PuntosDePregunta p : lista(datos.preguntas())) {
            PreguntaDeLaPrueba pc = porId.get(p.id());
            if (pc == null) {
                throw new IllegalArgumentException("La pregunta " + p.id()
                        + " no es de la prueba publicada de esta vacante");
            }
            puntosNuevos.put(p.id(), p.puntos());
            Set<Long> suyasOpciones = pc.opciones().stream().map(Opcion::getId).collect(Collectors.toSet());
            for (PuntosDeOpcion o : lista(p.opciones())) {
                if (!suyasOpciones.contains(o.id())) {
                    throw new IllegalArgumentException("La opción " + o.id() + " no es de la pregunta " + p.id());
                }
                opcionesNuevas.put(o.id(), o.puntos());
            }
        }
        Map<Long, BigDecimal> totalesPedidos = new HashMap<>();
        for (PuntosDeCriterio c : lista(datos.criterios())) {
            if (!criterioPorId.containsKey(c.id())) {
                throw new IllegalArgumentException("El criterio " + c.id()
                        + " no es de la prueba publicada de esta vacante");
            }
            totalesPedidos.put(c.id(), c.puntos());
        }

        // Todo se valida antes de tocar nada: o cambia entero, o no cambia.
        List<String> faltas = new ArrayList<>();
        int posicion = 1;
        for (PreguntaDeLaPrueba pc : r.todas()) {
            Pregunta p = pc.pregunta();
            String donde = ReglasDePuntos.nombreDe(posicion++, p.getEnunciado());
            BigDecimal puntos = puntosNuevos.getOrDefault(p.getId(), BigDecimal.valueOf(pc.maximo()));
            if (pc.esAbierta()) {
                if (puntos.signum() != 0) {
                    faltas.add(donde + ": en la prueba, una abierta no lleva puntos.");
                }
                continue;
            }
            List<OpcionAValidar> suyasOpciones = pc.opciones().stream()
                    .map(o -> new OpcionAValidar(ReglasDePuntos.ESCALA.equals(p.getTipo()) ? null : o.getTexto(),
                            opcionesNuevas.getOrDefault(o.getId(), o.getPuntaje())))
                    .toList();
            PreguntaAValidar aValidar = new PreguntaAValidar(p.getTipo(), p.getEnunciado(), puntos,
                    null, suyasOpciones);
            faltas.addAll(ReglasDePuntos.formaDeLaPregunta(donde, aValidar));
            faltas.addAll(ReglasDePuntos.puntuacionDeLaPregunta(donde, aValidar));
        }
        // La suma es la de lo escrito, como la del formulario: lo que vale cada criterio y las
        // cerradas sueltas. Si algo de eso está mal escrito no se dice ninguna (QA-11): su falta
        // ya lo dice, y una suma que lo dejara fuera contradiría la escrita.
        int suma = 0;
        boolean haySuma = true;
        for (PreguntaDeLaPrueba pc : r.sinCriterio()) {
            if (!pc.esAbierta()) {
                OptionalInt puntos = ReglasDeLaPrueba.paraLaSuma(puntosNuevosDe(pc, puntosNuevos));
                suma += puntos.orElse(0);
                haySuma &= puntos.isPresent();
            }
        }
        // Lo que vale cada criterio (V69). Lo que no se dice se mantiene: con parte calificada,
        // su total, y la parte se ajusta a sus cerradas nuevas; solo de cerradas, lo que sumen.
        Map<Long, int[]> repartoNuevo = new HashMap<>();   // criterio → {total, parte calificada}
        for (CriterioDeLaPrueba c : r.criterios()) {
            String cual = "El criterio «" + c.criterio().getNombre() + "»";
            int cerradas = cerradasNuevas(c, puntosNuevos);
            BigDecimal pedido = totalesPedidos.get(c.criterio().getId());
            OptionalInt vale = valeEscrito(c, pedido, puntosNuevos);
            suma += vale.orElse(0);
            haySuma &= vale.isPresent();
            List<String> deEste = pedido == null ? List.of()
                    : ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(pedido, c.calificador(), cerradas);
            deEste.forEach(f -> faltas.add(cual + ": " + f));
            if (!deEste.isEmpty()) {
                continue;
            }
            int total = pedido != null ? pedido.intValue()
                    : c.tieneParteCalificada() ? c.puntosDelCriterio() : cerradas;
            int calificada = total - cerradas;
            // Dar o quitar la parte calificada es cambiar qué se califica, y eso es contenido.
            if (calificada < 0) {
                faltas.add(ReglasDeLaPrueba.cerradasPorEncima(c.criterio().getNombre(), cerradas, total));
            } else if (c.tieneParteCalificada() && calificada == 0) {
                faltas.add(cual + ": su parte calificada no puede quedar en 0, porque tiene "
                        + "abiertas o entregables que alguien califica.");
            } else if (!c.tieneParteCalificada() && calificada > 0) {
                faltas.add(cual + " no tiene parte calificada: dársela es cambiar la prueba, y "
                        + "eso ya no se puede. Vale lo que sumen sus cerradas (" + cerradas + ").");
            } else {
                repartoNuevo.put(c.criterio().getId(), new int[]{total, calificada});
            }
        }
        String total = haySuma ? ReglasDePuntos.faltaDelTotal(suma) : null;
        if (total != null) {
            faltas.add(0, total);
        }
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("Los puntos no se cambiaron: "
                    + (faltas.size() == 1 ? "falta una cosa" : "faltan " + faltas.size() + " cosas"),
                    faltas);
        }

        Map<String, Object> antes = new LinkedHashMap<>();
        Map<String, Object> despues = new LinkedHashMap<>();
        puntosNuevos.forEach((id, valor) -> {
            Pregunta p = porId.get(id).pregunta();
            int viejo = p.getPuntos() == null ? 0 : p.getPuntos();
            int nuevo = valor.intValue();
            if (viejo != nuevo) {
                antes.put("pregunta " + id, viejo);
                despues.put("pregunta " + id, nuevo);
                p.setPuntos(nuevo);
                p.setEsPuntuable(nuevo > 0);
                preguntas.save(p);
            }
        });
        List<Opcion> opcionesCambiadas = new ArrayList<>();
        r.todas().stream().flatMap(pc -> pc.opciones().stream()).forEach(o -> {
            BigDecimal nuevo = opcionesNuevas.get(o.getId());
            if (nuevo != null && (o.getPuntaje() == null || o.getPuntaje().compareTo(nuevo) != 0)) {
                antes.put("opción " + o.getId(), String.valueOf(o.getPuntaje()));
                despues.put("opción " + o.getId(), nuevo.stripTrailingZeros().toPlainString());
                o.setPuntaje(nuevo.setScale(0, RoundingMode.UNNECESSARY));
                opcionesCambiadas.add(o);
            }
        });
        opciones.saveAll(opcionesCambiadas);
        Map<Long, int[]> calificadasQueCambian = new HashMap<>();   // criterio → {antes, después}
        repartoNuevo.forEach((id, reparto) -> {
            CriterioDeLaPrueba calculado = criterioPorId.get(id);
            CriterioBanco c = calculado.criterio();
            int totalViejo = calculado.puntosDelCriterio();
            int viejo = calculado.calificadaMaximo();
            int totalNuevo = reparto[0];
            int nuevo = reparto[1];
            if (totalViejo != totalNuevo || viejo != nuevo) {
                c.setPuntosDelCriterio(totalNuevo);
                c.setPuntosCalificados(nuevo);
                criteriosBanco.save(c);
            }
            if (totalViejo != totalNuevo) {
                antes.put("criterio " + id + " · puntos", totalViejo);
                despues.put("criterio " + id + " · puntos", totalNuevo);
            }
            if (viejo != nuevo) {
                antes.put("criterio " + id + " · parte calificada", viejo);
                despues.put("criterio " + id + " · parte calificada", nuevo);
                calificadasQueCambian.put(id, new int[]{viejo, nuevo});
            }
        });
        if (antes.isEmpty()) {
            return new CambioAplicado(0);
        }

        // La parte calificada ya puesta —por la IA, por una persona o ajustada— se escala en
        // proporción a su máximo nuevo (12 de 20 pasa a 9 de 15). Las cerradas no se
        // reescriben: se calculan al leer con los puntos nuevos.
        if (!calificadasQueCambian.isEmpty()) {
            List<NotaCriterioPrueba> puestas = notas.findByCriterioBancoIdIn(calificadasQueCambian.keySet());
            for (NotaCriterioPrueba n : puestas) {
                int[] cambio = calificadasQueCambian.get(n.getCriterioBancoId());
                n.setPuntaje(escalar(n.getPuntaje(), cambio[0], cambio[1]));
                if (n.getPuntajeIa() != null) {
                    n.setPuntajeIa(escalar(n.getPuntajeIa(), cambio[0], cambio[1]));
                }
            }
            notas.saveAll(puestas);
            notas.flush();
        }
        auditoria.registrar(quien.organizacionId(), quien, "cambiar_puntos_prueba_propia",
                "version_banco", publicada.getId(), antes, despues, null);

        // En la misma transacción, sin IA y sin mover a nadie de etapa.
        List<IntentoPrueba> entregados = entregadosDe(publicada);
        for (IntentoPrueba intento : entregados) {
            cierre.recalcular(intento, false);
        }
        return new CambioAplicado(entregados.size());
    }

    /** Lo que suman las cerradas de un criterio con los puntos nuevos (los no dichos, los de hoy). */
    private static int cerradasNuevas(CriterioDeLaPrueba c, Map<Long, BigDecimal> puntosNuevos) {
        int suma = 0;
        for (PreguntaDeLaPrueba pc : c.preguntas()) {
            if (!pc.esAbierta()) {
                BigDecimal puntos = puntosNuevosDe(pc, puntosNuevos);
                suma += ReglasDePuntos.esEntero(puntos) ? puntos.intValue() : 0;
            }
        }
        return suma;
    }

    /** Los puntos nuevos de una pregunta: los dichos, o los de hoy si no se dicen. */
    private static BigDecimal puntosNuevosDe(PreguntaDeLaPrueba pc, Map<Long, BigDecimal> puntosNuevos) {
        return puntosNuevos.getOrDefault(pc.pregunta().getId(), BigDecimal.valueOf(pc.maximo()));
    }

    /**
     * Lo que vale un criterio tal como quedó escrito, para la suma de la prueba (QA-11): lo
     * pedido, aunque esté fuera de 0 a 100 o por debajo de sus cerradas (eso lo dice su
     * falta); sin pedir, su total si tiene parte calificada, o lo que sumen sus cerradas.
     * Vacío si lo que cuenta no es un entero: entonces no hay suma que decir.
     */
    private static OptionalInt valeEscrito(CriterioDeLaPrueba c, BigDecimal pedido,
                                           Map<Long, BigDecimal> puntosNuevos) {
        if (pedido != null) {
            return ReglasDeLaPrueba.paraLaSuma(pedido);
        }
        if (c.tieneParteCalificada()) {
            return OptionalInt.of(c.puntosDelCriterio());
        }
        int suma = 0;
        for (PreguntaDeLaPrueba pc : c.preguntas()) {
            if (!pc.esAbierta()) {
                OptionalInt puntos = ReglasDeLaPrueba.paraLaSuma(puntosNuevosDe(pc, puntosNuevos));
                if (puntos.isEmpty()) {
                    return OptionalInt.empty();
                }
                suma += puntos.getAsInt();
            }
        }
        return OptionalInt.of(suma);
    }

    // ============================== Recomendaciones por IA ==============================

    /**
     * ⚠️ <b>Sin transacción propia, a propósito</b>, como en la fase 1: la propuesta pedida se
     * confirma ANTES de encolar, porque el agente la lee desde otro hilo.
     */
    @Override
    public RecomendacionPedida pedirRecomendaciones(ContextoUsuario quien, Long vacanteId,
                                                    PedirRecomendaciones datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = versionesBanco.pruebaPropiaDe(vacante.getId(), BORRADOR).orElse(null);
        VersionBanco base = borrador != null ? borrador
                : versionesBanco.pruebaPropiaDe(vacante.getId(), PUBLICADA).orElse(null);
        if (borrador == null && base != null && yaSeRindio(vacante)) {
            throw new IllegalStateException(VARA_QUIETA);
        }
        int total = base == null ? 0 : calculo.estructura(base.getId()).total();
        if (total >= ReglasDePuntos.TOTAL) {
            throw new IllegalStateException("La prueba ya suma " + total + " puntos: no queda "
                    + "sitio. Quita puntos para que la IA complete lo que falte.");
        }
        String motivo = cola.porQueNoSePuedeUsarLaIa(vacante.getOrganizacionId());
        if (motivo != null) {
            return new RecomendacionPedida(false, motivo + " No se pidieron recomendaciones.");
        }
        // Solo frena una de la prueba en curso: la del Perfil Integral va por su carril.
        if ("EN_CURSO".equals(cola.comoVaElRecomendadorDePrueba(vacante.getId()).estado())) {
            return new RecomendacionPedida(false,
                    "Ya hay una recomendación de la prueba en marcha: espera a que termine.");
        }
        int faltan = ReglasDePuntos.TOTAL - total;
        PropuestaPreguntas pedida = propuestas.save(PropuestaPreguntas.builder()
                .organizacionId(vacante.getOrganizacionId())
                .vacanteId(vacante.getId())
                .proposito(PropuestaPreguntas.PRUEBA_PUESTO)
                .indicacion(textoONulo(datos == null ? null : datos.indicacion()))
                .puntosQueFaltan(faltan)
                .estado(PropuestaPreguntas.PEDIDA)
                .pedidaPorUsuarioId(quien.usuarioId())
                .creadoEn(Instant.now())
                .build());
        return enLaCola(pedida, cola.encolarRecomendadorDePrueba(vacante.getOrganizacionId(),
                vacante.getId()), faltan);
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoDeLaRecomendacion comoVaLaRecomendacion(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laVisible(quien, vacanteId);
        PropuestaPreguntas ultima = propuestas.findFirstByVacanteIdAndPropositoOrderByIdDesc(
                vacante.getId(), PropuestaPreguntas.PRUEBA_PUESTO).orElse(null);
        if (ultima == null) {
            return new EstadoDeLaRecomendacion("SIN_PEDIR", null, null, null, null, List.of());
        }
        if (PropuestaPreguntas.LISTA.equals(ultima.getEstado())) {
            PropuestaDePrueba p = leerPropuesta(ultima);
            return new EstadoDeLaRecomendacion("LISTA", null, ultima.getId(),
                    ultima.getPuntosQueFaltan(), ultima.getIndicacion(), lista(p.criterios()),
                    p.caso(), lista(p.entregables()));
        }
        if (PropuestaPreguntas.FALLIDA.equals(ultima.getEstado())) {
            return new EstadoDeLaRecomendacion("FALLIDA", ultima.getMotivoFallo(), ultima.getId(),
                    ultima.getPuntosQueFaltan(), ultima.getIndicacion(), List.of());
        }
        ColaCalificacionIa.Seguimiento trabajo = cola.comoVaElRecomendadorDePrueba(vacante.getId());
        String estado = "EN_CURSO".equals(trabajo.estado()) ? "EN_CURSO" : "FALLIDA";
        String motivo = "EN_CURSO".equals(estado) ? null
                : trabajo.motivo() != null ? trabajo.motivo()
                : "La IA terminó sin dejar ninguna propuesta.";
        return new EstadoDeLaRecomendacion(estado, motivo, ultima.getId(),
                ultima.getPuntosQueFaltan(), ultima.getIndicacion(), List.of());
    }

    @Override
    @Transactional
    public EditorDePreguntas agregarDeLaPropuesta(ContextoUsuario quien, Long vacanteId,
                                                  Long propuestaId, AgregarDeLaPropuestaDePrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        PropuestaPreguntas propuesta = propuestas.findByIdAndVacanteIdAndProposito(propuestaId,
                        vacante.getId(), PropuestaPreguntas.PRUEBA_PUESTO)
                .orElseThrow(() -> new ResourceNotFoundException("Propuesta", "id", propuestaId));
        if (!PropuestaPreguntas.LISTA.equals(propuesta.getEstado())) {
            throw new IllegalStateException("Esa propuesta todavía no está lista");
        }
        PropuestaDePrueba propuesto = leerPropuesta(propuesta);
        List<CriterioPropuesto> criteriosPropuestos = lista(propuesto.criterios());
        List<EntregablePropuesto> entregablesPropuestos = lista(propuesto.entregables());
        boolean conCaso = datos != null && datos.caso();
        List<Integer> criteriosElegidos = lista(datos == null ? null : datos.criterios());
        List<PreguntaElegida> preguntasElegidas = lista(datos == null ? null : datos.preguntas());
        Set<Integer> entregablesElegidos = new LinkedHashSet<>(lista(datos == null ? null : datos.entregables()));
        if (!conCaso && criteriosElegidos.isEmpty() && preguntasElegidas.isEmpty()
                && entregablesElegidos.isEmpty()) {
            throw new IllegalArgumentException("Elige qué agregar de la propuesta");
        }
        for (Integer i : entregablesElegidos) {
            if (i == null || i < 0 || i >= entregablesPropuestos.size()) {
                throw new IllegalArgumentException("La propuesta no tiene ese entregable");
            }
        }
        VersionBanco borrador = elBorrador(vacante);

        if (conCaso && propuesto.caso() != null) {
            // El caso no reemplaza al que ya se escribió: solo llena lo que esté vacío.
            if (borrador.getEnunciado() == null) {
                borrador.setEnunciado(textoONulo(propuesto.caso().enunciado()));
            }
            if (borrador.getMateriales() == null) {
                borrador.setMateriales(textoONulo(propuesto.caso().materiales()));
            }
            if (borrador.getHerramientasPermitidas() == null) {
                borrador.setHerramientasPermitidas(textoONulo(propuesto.caso().herramientasPermitidas()));
            }
            versionesBanco.save(borrador);
        }

        // Las preguntas elegidas, por criterio: los criterios enteros y las sueltas.
        Map<Integer, Set<Integer>> elegidas = new LinkedHashMap<>();
        for (Integer c : criteriosElegidos) {
            CriterioPropuesto cp = enLaPropuesta(criteriosPropuestos, c);
            Set<Integer> todas = elegidas.computeIfAbsent(c, k -> new LinkedHashSet<>());
            for (int i = 0; i < lista(cp.preguntas()).size(); i++) {
                todas.add(i);
            }
        }
        for (PreguntaElegida p : preguntasElegidas) {
            CriterioPropuesto cp = enLaPropuesta(criteriosPropuestos, p.criterio());
            if (p.pregunta() < 0 || p.pregunta() >= lista(cp.preguntas()).size()) {
                throw new IllegalArgumentException("La propuesta no tiene esa pregunta");
            }
            elegidas.computeIfAbsent(p.criterio(), k -> new LinkedHashSet<>()).add(p.pregunta());
        }
        Map<String, Long> creadas = agregarCriteriosYPreguntas(borrador, criteriosPropuestos,
                elegidas, Set.copyOf(criteriosElegidos));

        // Los entregables (V68): el archivo de una pregunta llega con su pregunta; un general,
        // si se eligió, o si cubre alguna pregunta de un criterio que se agregó entero.
        for (int k = 0; k < entregablesPropuestos.size(); k++) {
            EntregablePropuesto e = entregablesPropuestos.get(k);
            Alcance alcance = alcanceDeLaPropuesta(e, borrador, criteriosPropuestos, creadas,
                    entregablesElegidos.contains(k), Set.copyOf(criteriosElegidos));
            // Un general que ya está (se agregó con otro criterio) no se duplica.
            boolean deUnaPregunta = alcance != null && EntregableRequerido.PREGUNTA.equals(alcance.alcance());
            if (alcance != null && (deUnaPregunta || !yaHayUnoQueSeLlama(borrador, e.nombre()))) {
                nuevoEntregable(borrador, e.nombre(), e.detalle(), e.formato(),
                        !Boolean.FALSE.equals(e.obligatorio()), e.queDebeTener(), alcance);
            }
        }
        return editor(quien, vacante);
    }

    /**
     * Crea los criterios y las preguntas elegidas. Devuelve el id de cada pregunta creada por
     * su posición en la propuesta («criterio-pregunta»), para colgarle su archivo.
     */
    private Map<String, Long> agregarCriteriosYPreguntas(VersionBanco borrador,
                                                         List<CriterioPropuesto> criteriosPropuestos,
                                                         Map<Integer, Set<Integer>> elegidas,
                                                         Set<Integer> enteros) {
        Map<Long, CriterioBanco> existentes = criteriosBanco
                .findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).stream()
                .collect(Collectors.toMap(CriterioBanco::getId, Function.identity()));
        Map<String, Long> creadas = new HashMap<>();
        for (Map.Entry<Integer, Set<Integer>> e : elegidas.entrySet()) {
            CriterioPropuesto cp = criteriosPropuestos.get(e.getKey());
            Long criterioId;
            if (cp.criterioExistenteId() != null) {
                if (!existentes.containsKey(cp.criterioExistenteId())) {
                    throw new IllegalStateException("El criterio al que iban esas preguntas ya no "
                            + "está en el borrador");
                }
                criterioId = cp.criterioExistenteId();
            } else {
                // Lo que vale (V69): entero, lo propuesto; con solo algunas de sus preguntas,
                // lo que sumen sus cerradas elegidas, sin parte calificada.
                boolean entero = enteros.contains(e.getKey());
                int total = puntosAlAgregar(entero, cp, e.getValue());
                int calificada = Math.max(0, total - cerradasElegidas(cp, e.getValue()));
                criterioId = nuevoCriterio(borrador, cp.nombre(), cp.queEvalua(), total, calificada,
                        entero ? cp.calificador() : null).getId();
            }
            // Un criterio del borrador sigue valiendo lo mismo: sus cerradas nuevas salen de su
            // parte calificada (V69), como cualquier cerrada que se le agregue a mano.
            for (Integer i : e.getValue()) {
                PreguntaPropuesta pp = cp.preguntas().get(i);
                List<OpcionAValidar> suyas = lista(pp.opciones()).stream()
                        .map(o -> new OpcionAValidar(o.texto(), o.puntos())).toList();
                BigDecimal puntos = ReglasDePuntos.ABIERTA.equals(pp.tipo()) ? BigDecimal.ZERO : pp.puntos();
                List<String> faltas = ReglasDeLaPrueba.formaDeLaPregunta("La pregunta propuesta",
                        new PreguntaAValidar(pp.tipo(), pp.enunciado(), puntos, pp.queDebeTener(), suyas));
                if (!faltas.isEmpty()) {
                    throw new PreguntasInvalidasException("La propuesta no se puede agregar así", faltas);
                }
                Pregunta nueva = nuevaPregunta(borrador, pp.tipo(), pp.enunciado(), puntosDe(pp.tipo(), puntos),
                        criterioId, pp.queDebeTener(), suyas);
                creadas.put(e.getKey() + "-" + i, nueva.getId());
            }
        }
        return creadas;
    }

    /**
     * Lo que vale un criterio nuevo de la propuesta al agregarlo (V69). Entero, lo que propuso
     * la IA (o, en una propuesta de antes, sus cerradas más su parte calificada); con solo
     * algunas de sus preguntas, lo que sumen las cerradas elegidas: sin la propuesta entera no
     * hay parte calificada que dar.
     */
    private static int puntosAlAgregar(boolean entero, CriterioPropuesto cp, Set<Integer> elegidas) {
        BigDecimal propuesto = cp.puntosDelCriterio();
        if (entero && propuesto != null) {
            return propuesto.setScale(0, RoundingMode.HALF_UP).intValue();
        }
        return cerradasElegidas(cp, elegidas);
    }

    /** Lo que suman las cerradas elegidas de un criterio de la propuesta. */
    private static int cerradasElegidas(CriterioPropuesto cp, Set<Integer> elegidas) {
        List<PreguntaPropuesta> suyas = lista(cp.preguntas());
        int cerradas = 0;
        for (Integer i : elegidas) {
            PreguntaPropuesta pp = i >= 0 && i < suyas.size() ? suyas.get(i) : null;
            if (pp != null && !ReglasDePuntos.ABIERTA.equals(pp.tipo()) && pp.puntos() != null) {
                cerradas += pp.puntos().setScale(0, RoundingMode.HALF_UP).intValue();
            }
        }
        return cerradas;
    }

    /**
     * El alcance con que entra al borrador un entregable de la propuesta, o nulo si esta vez
     * no entra. Las preguntas que cubre un general se buscan entre las recién creadas y, si
     * se agregaron antes, por su enunciado en el borrador.
     */
    private Alcance alcanceDeLaPropuesta(EntregablePropuesto e, VersionBanco borrador,
                                         List<CriterioPropuesto> criterios, Map<String, Long> creadas,
                                         boolean elegido, Set<Integer> criteriosEnteros) {
        if (e.esDeUnaPregunta()) {
            Long pregunta = creadas.get(e.pregunta().criterio() + "-" + e.pregunta().pregunta());
            return pregunta == null ? null : new Alcance(EntregableRequerido.PREGUNTA, pregunta, List.of());
        }
        if (e.cubreTodaLaPrueba()) {
            return elegido || !criteriosEnteros.isEmpty() ? Alcance.deTodaLaPrueba() : null;
        }
        List<PosicionDePregunta> cubre = lista(e.cubre());
        boolean deUnEntero = cubre.stream().anyMatch(p -> p != null && criteriosEnteros.contains(p.criterio()));
        if (!elegido && !deUnEntero) {
            return null;
        }
        Map<String, Long> porEnunciado = new HashMap<>();
        preguntas.findByVersionBancoIdOrderByOrden(borrador.getId())
                .forEach(p -> porEnunciado.putIfAbsent(normal(p.getEnunciado()), p.getId()));
        List<Long> ids = new ArrayList<>();
        for (PosicionDePregunta p : cubre) {
            if (p == null || p.criterio() == null || p.pregunta() == null) {
                continue;
            }
            Long id = creadas.get(p.criterio() + "-" + p.pregunta());
            if (id == null) {
                id = porEnunciado.get(normal(enunciadoPropuesto(criterios, p)));
            }
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return Alcance.deLasPreguntas(ids);
    }

    /** El enunciado de una pregunta de la propuesta, o nulo si la posición no existe. */
    private static String enunciadoPropuesto(List<CriterioPropuesto> criterios, PosicionDePregunta p) {
        if (p.criterio() < 0 || p.criterio() >= criterios.size()) {
            return null;
        }
        List<PreguntaPropuesta> suyas = lista(criterios.get(p.criterio()).preguntas());
        return p.pregunta() < 0 || p.pregunta() >= suyas.size() ? null : suyas.get(p.pregunta()).enunciado();
    }

    /** Si el borrador ya tiene un entregable con ese nombre: agregar dos veces no duplica. */
    private boolean yaHayUnoQueSeLlama(VersionBanco borrador, String nombre) {
        String buscado = normal(nombre);
        return entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).stream()
                .anyMatch(e -> normal(e.getNombre()).equals(buscado));
    }

    private static String normal(String texto) {
        return texto == null ? "" : texto.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private static CriterioPropuesto enLaPropuesta(List<CriterioPropuesto> propuestos, Integer i) {
        if (i == null || i < 0 || i >= propuestos.size()) {
            throw new IllegalArgumentException("La propuesta no tiene ese criterio");
        }
        return propuestos.get(i);
    }

    private PropuestaDePrueba leerPropuesta(PropuestaPreguntas propuesta) {
        if (propuesta.getContenido() == null || propuesta.getContenido().isBlank()) {
            return new PropuestaDePrueba(null, List.of(), List.of());
        }
        return JSON.readValue(propuesta.getContenido(), PropuestaDePrueba.class);
    }

    // ============================== Su propósito ==============================

    @Override
    protected Optional<VersionBanco> version(Long vacanteId, String estado) {
        return versionesBanco.pruebaPropiaDe(vacanteId, estado);
    }

    @Override
    protected Piezas piezas() {
        return new Piezas(alcance, permisos, vacantes, puestos, versionesBanco, criteriosBanco,
                preguntas, opciones, propuestas, cola, auditoria);
    }

    /** La vara de la prueba se congela en la primera rendición (decisión 9). */
    @Override
    protected boolean laVaraNoSeMueve(Vacante vacante) {
        return yaSeRindio(vacante);
    }

    /** La frontera de la decisión 9: alguien ya abrió su prueba en esta vacante. */
    private boolean yaSeRindio(Vacante vacante) {
        return intentos.algunoEmpezadoDeLaVacante(vacante.getId());
    }

    @Override
    protected Textos textos() {
        return TEXTOS;
    }

    @Override
    protected List<String> faltasDelBorrador(VersionBanco borrador) {
        // La fecha límite es de la vacante (V68): sin ella, o pasada, no se publica.
        Instant cierraEn = vacantes.findByIdAndOrganizacionId(borrador.getVacanteId(),
                        borrador.getOrganizacionId())
                .map(Vacante::getPruebaCierraEn).orElse(null);
        return ReglasDeLaPrueba.faltasParaPublicar(calculo.estructura(borrador.getId()), cierraEn,
                Instant.now());
    }

    @Override
    protected List<VersionBanco> publicadasDe(Long organizacionId) {
        return versionesBanco.pruebasPropiasPublicadasDe(organizacionId);
    }

    @Override
    protected int cuantosRindieron(Vacante vacante, VersionBanco publicada) {
        return entregadosDe(publicada).size();
    }

    @Override
    protected Map<Long, ColaCalificacionIa.Seguimiento> seguimiento(List<Long> postulacionIds) {
        return cola.recalificacionDePrueba(postulacionIds);
    }

    @Override
    protected boolean recalificar(Long postulacionId) {
        return cola.recalificarPrueba(postulacionId);
    }

    /**
     * La parte calificada se deduce del total (V69); al publicar se deja escrita la de ese
     * momento, la misma que leerá la calificación, para que la columna no mienta a quien la
     * consulte fuera del servidor.
     */
    @Override
    protected void alPublicar(VersionBanco borrador) {
        List<CriterioBanco> cambiados = new ArrayList<>();
        for (CriterioDeLaPrueba c : calculo.estructura(borrador.getId()).criterios()) {
            CriterioBanco criterio = c.criterio();
            if (criterio.getPuntosDelCriterio() != null
                    && !Objects.equals(criterio.getPuntosCalificados(), c.calificadaMaximo())) {
                criterio.setPuntosCalificados(c.calificadaMaximo());
                cambiados.add(criterio);
            }
        }
        criteriosBanco.saveAll(cambiados);
    }

    /**
     * Lo que miraba a mano el criterio (un borrador de antes de la V68) se suelta: sus
     * entregables siguen en la prueba, y lo que mira se deduce del alcance.
     */
    @Override
    protected void antesDeQuitarElCriterio(Long criterioId) {
        miradas.deleteByCriterioBancoId(criterioId);
        miradas.flush();
    }

    /**
     * Quitar una pregunta quita también su archivo, y la saca de lo que cubre cada general:
     * si era la única, el general queda sin cubrir y sale como falta al publicar.
     */
    @Override
    protected void antesDeQuitarLaPregunta(Long preguntaId) {
        entregables.findByPreguntaId(preguntaId).ifPresent(this::soltarYBorrar);
        cubiertas.deleteByPreguntaId(preguntaId);
        cubiertas.flush();
    }

    /**
     * Copia el caso, el adjunto, el tiempo, la guía, los criterios con su parte calificada,
     * las preguntas con sus opciones y los entregables con su alcance (V68). Es una copia, no
     * un enlace: cambiar después cualquiera de las dos no toca la otra (el adjunto se comparte:
     * es el mismo archivo de la misma empresa, y nadie lo modifica). <b>La fecha límite no
     * viaja</b>: es de la vacante.
     *
     * <p>Un entregable de antes de la V68, con «Mira» marcado a mano, pasa a general que cubre
     * las preguntas de los criterios que lo miraban; si alguno no tenía preguntas, cubre toda
     * la prueba. Es lo que pasa al abrir un borrador desde una publicada de antes, o al copiarla.
     */
    @Override
    protected void copiarContenido(VersionBanco origen, VersionBanco destino) {
        destino.setGuiaCalificacion(origen.getGuiaCalificacion());
        destino.setEnunciado(origen.getEnunciado());
        destino.setConsignaArchivoId(origen.getConsignaArchivoId());
        destino.setUrlConsigna(origen.getUrlConsigna());
        destino.setMateriales(origen.getMateriales());
        destino.setHerramientasPermitidas(origen.getHerramientasPermitidas());
        destino.setModalidad(origen.getModalidad());
        destino.setDuracionMinutos(origen.getDuracionMinutos());
        // Sin días (V68): una de plazo abierto de antes pasa a «Sin cronómetro».
        destino.setPlazoDias(null);
        versionesBanco.save(destino);

        // Lo que vale cada criterio viaja con él (V69): una publicada de antes, sin total, le
        // da a la copia el que valía —sus cerradas más su parte calificada—.
        Resultado r = calculo.estructura(origen.getId());
        Map<Long, Long> criterioNuevo = new HashMap<>();
        for (CriterioDeLaPrueba calculado : r.criterios()) {
            CriterioBanco c = calculado.criterio();
            CriterioBanco copia = criteriosBanco.save(CriterioBanco.builder()
                    .versionBancoId(destino.getId())
                    .nombre(c.getNombre())
                    .queEvalua(c.getQueEvalua())
                    .orden(c.getOrden())
                    .puntosDelCriterio(calculado.puntosDelCriterio())
                    .puntosCalificados(calculado.calificadaMaximo())
                    .calificador(c.getCalificador())
                    .creadoEn(Instant.now())
                    .build());
            criterioNuevo.put(c.getId(), copia.getId());
        }
        List<Pregunta> deLaVersion = preguntas.findByVersionBancoIdOrderByOrden(origen.getId());
        Map<Long, List<Opcion>> opcionesDe = deLaVersion.isEmpty() ? Map.of()
                : opciones.findByPreguntaIdIn(deLaVersion.stream().map(Pregunta::getId).toList()).stream()
                        .collect(Collectors.groupingBy(Opcion::getPreguntaId));
        Map<Long, Long> preguntaNueva = new HashMap<>();
        for (Pregunta p : deLaVersion) {
            Pregunta copia = preguntas.save(Pregunta.builder()
                    .versionBancoId(destino.getId())
                    .codigo(p.getCodigo())
                    .tipo(p.getTipo())
                    .enunciado(p.getEnunciado())
                    .esPuntuable(p.isEsPuntuable())
                    .orden(p.getOrden())
                    .puntos(p.getPuntos())
                    .criterioBancoId(p.getCriterioBancoId() == null ? null : criterioNuevo.get(p.getCriterioBancoId()))
                    .queDebeTener(p.getQueDebeTener())
                    .creadoEn(Instant.now())
                    .build());
            preguntaNueva.put(p.getId(), copia.getId());
            for (Opcion o : opcionesDe.getOrDefault(p.getId(), List.of())) {
                opciones.save(Opcion.builder()
                        .preguntaId(copia.getId())
                        .letra(o.getLetra())
                        .texto(o.getTexto())
                        .puntaje(o.getPuntaje())
                        .orden(o.getOrden())
                        .creadoEn(Instant.now())
                        .build());
            }
        }

        // Los entregables, con su alcance llevado a las preguntas de la copia.
        Map<Long, List<Long>> criteriosQueLoMiran = new HashMap<>();
        Map<Long, List<Long>> preguntasDelCriterio = new HashMap<>();
        for (CriterioDeLaPrueba c : r.criterios()) {
            preguntasDelCriterio.put(c.criterio().getId(),
                    c.preguntas().stream().map(pc -> pc.pregunta().getId()).toList());
            c.entregables().forEach(e -> criteriosQueLoMiran
                    .computeIfAbsent(e.getId(), k -> new ArrayList<>()).add(c.criterio().getId()));
        }
        for (EntregableRequerido e : r.entregables()) {
            Alcance alcance = alcanceDeLaCopia(e, r.cubreDe(e.getId()),
                    criteriosQueLoMiran.getOrDefault(e.getId(), List.of()), preguntasDelCriterio,
                    preguntaNueva);
            EntregableRequerido copia = entregables.save(EntregableRequerido.builder()
                    .versionBancoId(destino.getId())
                    .nombre(e.getNombre())
                    .detalle(e.getDetalle() == null ? "" : e.getDetalle())
                    .formato(e.getFormato())
                    .esObligatorio(e.isEsObligatorio())
                    .orden(e.getOrden())
                    .queDebeTener(e.getQueDebeTener())
                    .alcance(alcance.alcance())
                    .preguntaId(alcance.preguntaId())
                    .creadoEn(Instant.now())
                    .build());
            if (!alcance.cubre().isEmpty()) {
                ponerCubiertas(copia.getId(), alcance.cubre());
            }
        }
    }

    /** El alcance de un entregable en la copia: el suyo, o el que se deduce de su «Mira» de antes. */
    private static Alcance alcanceDeLaCopia(EntregableRequerido e, List<Long> cubre,
                                            List<Long> loMiraban, Map<Long, List<Long>> preguntasDelCriterio,
                                            Map<Long, Long> preguntaNueva) {
        if (EntregableRequerido.PREGUNTA.equals(e.getAlcance())) {
            Long nueva = preguntaNueva.get(e.getPreguntaId());
            return nueva == null ? Alcance.deLasPreguntas(List.of())
                    : new Alcance(EntregableRequerido.PREGUNTA, nueva, List.of());
        }
        if (EntregableRequerido.TODA_LA_PRUEBA.equals(e.getAlcance())) {
            return Alcance.deTodaLaPrueba();
        }
        List<Long> deLaCopia = new ArrayList<>();
        if (EntregableRequerido.PREGUNTAS.equals(e.getAlcance())) {
            cubre.forEach(id -> deLaCopia.add(preguntaNueva.get(id)));
        } else {
            // De antes de la V68: los criterios que lo miraban dicen qué cubre.
            for (Long criterio : loMiraban) {
                List<Long> suyas = preguntasDelCriterio.getOrDefault(criterio, List.of());
                if (suyas.isEmpty()) {
                    return Alcance.deTodaLaPrueba();
                }
                suyas.forEach(id -> deLaCopia.add(preguntaNueva.get(id)));
            }
        }
        return Alcance.deLasPreguntas(deLaCopia.stream().filter(Objects::nonNull).distinct().toList());
    }

    /** Un borrador se descarta entero: ningún intento apunta a él. */
    @Override
    protected void vaciarYBorrar(VersionBanco borrador) {
        List<CriterioBanco> suyos = criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId());
        if (!suyos.isEmpty()) {
            miradas.deleteByCriterioBancoIdIn(suyos.stream().map(CriterioBanco::getId).toList());
            miradas.flush();
        }
        // Los entregables antes que las preguntas: el archivo de una pregunta apunta a ella.
        List<Long> deLaVersion = entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId())
                .stream().map(EntregableRequerido::getId).toList();
        if (!deLaVersion.isEmpty()) {
            cubiertas.deleteByEntregableRequeridoIdIn(deLaVersion);
        }
        entregables.deleteByVersionBancoId(borrador.getId());
        entregables.flush();
        List<Pregunta> susPreguntas = preguntas.findByVersionBancoIdOrderByOrden(borrador.getId());
        if (!susPreguntas.isEmpty()) {
            opciones.deleteByPreguntaIdIn(susPreguntas.stream().map(Pregunta::getId).toList());
        }
        preguntas.deleteByVersionBancoId(borrador.getId());
        criteriosBanco.deleteByVersionBancoId(borrador.getId());
        versionesBanco.delete(borrador);
        versionesBanco.flush();
    }

    private EntregableRequerido elEntregable(VersionBanco version, Long entregableId) {
        return entregables.findById(entregableId)
                .filter(e -> version.getId().equals(e.getVersionBancoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Entregable", "id", entregableId));
    }

    /**
     * El alcance pedido, comprobado contra el borrador: la pregunta y lo que cubre tienen que
     * ser suyos (lo ajeno es 404) y una pregunta pide como mucho un archivo.
     *
     * @param mismo el entregable que se está editando, que no cuenta como «otro» de su pregunta
     */
    private Alcance elAlcance(VersionBanco borrador, GuardarEntregable datos, Long mismo) {
        if (datos.preguntaId() != null) {
            Pregunta pregunta = laPregunta(borrador, datos.preguntaId());
            entregables.findByPreguntaId(pregunta.getId())
                    .filter(otro -> !otro.getId().equals(mismo))
                    .ifPresent(otro -> {
                        throw new PreguntasInvalidasException("El archivo no se puede guardar así",
                                List.of("Esta pregunta ya pide un archivo («" + otro.getNombre()
                                        + "»): una pregunta pide como mucho uno. Cámbialo o quítalo."));
                    });
            return new Alcance(EntregableRequerido.PREGUNTA, pregunta.getId(), List.of());
        }
        if (Boolean.TRUE.equals(datos.todaLaPrueba())) {
            return Alcance.deTodaLaPrueba();
        }
        List<Long> cubre = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(lista(datos.cubre()))) {
            if (id == null) {
                throw new ResourceNotFoundException("Pregunta", "id", null);
            }
            cubre.add(laPregunta(borrador, id).getId());
        }
        return Alcance.deLasPreguntas(cubre);
    }

    /** Las preguntas que cubre un general: se escriben enteras. Sin ninguna, no se toca nada. */
    private void ponerCubiertas(Long entregableId, Collection<Long> preguntaIds) {
        if (preguntaIds.isEmpty()) {
            return;
        }
        for (Long id : new LinkedHashSet<>(preguntaIds)) {
            cubiertas.save(EntregableCubrePregunta.builder()
                    .entregableRequeridoId(entregableId)
                    .preguntaId(id)
                    .creadoEn(Instant.now())
                    .build());
        }
    }

    /** Quita un entregable del borrador con lo que cuelga de él. */
    private void soltarYBorrar(EntregableRequerido e) {
        miradas.deleteByEntregableRequeridoId(e.getId());
        cubiertas.deleteByEntregableRequeridoId(e.getId());
        cubiertas.flush();
        entregables.delete(e);
        entregables.flush();
    }

    /** Un criterio nuevo sin preguntas, que vale {@code puntos}: de momento, todo calificado. */
    private CriterioBanco nuevoCriterio(VersionBanco borrador, String nombre, String queEvalua,
                                        int puntos, String calificador) {
        return nuevoCriterio(borrador, nombre, queEvalua, puntos, puntos, calificador);
    }

    /**
     * Un criterio nuevo, que vale {@code puntos} (V69). Su parte calificada se deduce al leer;
     * la que se escribe es la que tendrá con las cerradas que se le van a agregar.
     */
    private CriterioBanco nuevoCriterio(VersionBanco borrador, String nombre, String queEvalua,
                                        int puntos, int calificada, String calificador) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El criterio necesita un nombre");
        }
        int orden = criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).stream()
                .map(CriterioBanco::getOrden).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        return criteriosBanco.save(CriterioBanco.builder()
                .versionBancoId(borrador.getId())
                .nombre(nombre.strip())
                .queEvalua(textoONulo(queEvalua))
                .orden(orden)
                .puntosDelCriterio(puntos)
                .puntosCalificados(calificada)
                .calificador(calificada > 0 ? textoONulo(calificador) : null)
                .creadoEn(Instant.now())
                .build());
    }

    private EntregableRequerido nuevoEntregable(VersionBanco borrador, String nombre, String detalle,
                                                String formato, boolean obligatorio, String queDebeTener,
                                                Alcance alcance) {
        List<String> faltas = ReglasDeLaPrueba.formaDelEntregable(nombre, formato);
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("El entregable no se puede guardar así", faltas);
        }
        int orden = entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).stream()
                .map(EntregableRequerido::getOrden).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        EntregableRequerido nuevo = entregables.save(EntregableRequerido.builder()
                .versionBancoId(borrador.getId())
                .nombre(nombre.strip())
                .detalle(detalle == null ? "" : detalle.strip())
                .formato(formato)
                .esObligatorio(obligatorio)
                .orden(orden)
                .queDebeTener(textoONulo(queDebeTener))
                .alcance(alcance.alcance())
                .preguntaId(alcance.preguntaId())
                .creadoEn(Instant.now())
                .build());
        if (!alcance.cubre().isEmpty()) {
            ponerCubiertas(nuevo.getId(), alcance.cubre());
        }
        return nuevo;
    }

    private static void exigirForma(GuardarPreguntaDePrueba datos) {
        List<String> faltas = ReglasDeLaPrueba.formaDeLaPregunta("La pregunta",
                new PreguntaAValidar(datos.tipo(), datos.enunciado(),
                        ReglasDePuntos.ABIERTA.equals(datos.tipo()) && datos.puntos() == null
                                ? BigDecimal.ZERO : datos.puntos(),
                        datos.queDebeTener(), comoOpciones(datos.opciones())));
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("La pregunta no se puede guardar así", faltas);
        }
    }

    private static void exigirPuntosDelCriterio(GuardarCriterioDePrueba datos, int cerradas) {
        List<String> faltas = ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(datos.puntos(),
                textoONulo(datos.calificador()), cerradas);
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("El criterio no se puede guardar así", faltas);
        }
    }

    /** La forma del entregable y de su alcance, dicha entera (V68). */
    private static void exigirFormaDelEntregable(GuardarEntregable datos) {
        List<String> faltas = new ArrayList<>(ReglasDeLaPrueba.formaDelEntregable(datos.nombre(), datos.formato()));
        faltas.addAll(ReglasDeLaPrueba.formaDelAlcance(datos.preguntaId(), datos.todaLaPrueba(), datos.cubre()));
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("El entregable no se puede guardar así", faltas);
        }
    }

    /** Una abierta no lleva puntos; una cerrada, los suyos (ya validados como enteros). */
    private static int puntosDe(String tipo, BigDecimal puntos) {
        if (ReglasDePuntos.ABIERTA.equals(tipo) || puntos == null) {
            return 0;
        }
        return puntos.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
    }

    private static List<OpcionAValidar> comoOpciones(List<GuardarOpcion> suyas) {
        return lista(suyas).stream().map(o -> new OpcionAValidar(o.texto(), o.puntos())).toList();
    }

    // ============================== Apoyo: lo que se pinta ==============================

    @Override
    protected VersionDePreguntas comoVersion(VersionBanco version) {
        Resultado r = calculo.estructura(version.getId());
        return comoVersion(r, ReglasDeLaPrueba.faltasParaPublicar(r));
    }

    /**
     * La versión tal como se pinta. «Mira» de cada criterio sale de la misma regla que la
     * calificación (V68), y cada entregable dice su alcance.
     *
     * @param avisos lo que frena publicarla: con la fecha límite si es el borrador de la vacante
     */
    private VersionDePreguntas comoVersion(Resultado r, List<String> avisos) {
        VersionBanco v = r.version();
        Map<Long, List<Long>> criteriosQueLoMiran = new HashMap<>();
        List<CriterioDeLaVersion> criterios = r.criterios().stream()
                .map(c -> {
                    List<Long> mira = c.entregables().stream().map(EntregableRequerido::getId).toList();
                    mira.forEach(id -> criteriosQueLoMiran.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(c.criterio().getId()));
                    return new CriterioDeLaVersion(c.criterio().getId(), c.criterio().getNombre(),
                            c.criterio().getQueEvalua(),
                            c.criterio().getOrden() == null ? 0 : c.criterio().getOrden(),
                            // Lo que vale (V69): lo escrito, aunque sus cerradas pasen de ahí.
                            c.puntosDelCriterio(), c.sistemaMaximo(), c.esDeIa() ? c.calificadaMaximo() : 0,
                            c.preguntas().stream().map(ServicioPruebaPropiaImpl::comoPregunta).toList(),
                            c.calificadaMaximo(), c.calificador(), mira);
                })
                .toList();
        List<EntregableDeLaVersion> deLaPrueba = r.entregables().stream()
                .map(e -> new EntregableDeLaVersion(e.getId(), e.getNombre(),
                        e.getDetalle() == null || e.getDetalle().isBlank() ? null : e.getDetalle(),
                        e.getFormato(), e.isEsObligatorio(), e.getQueDebeTener(),
                        e.getOrden() == null ? 0 : e.getOrden(),
                        criteriosQueLoMiran.getOrDefault(e.getId(), List.of()),
                        e.getAlcance(), e.getPreguntaId(), r.cubreDe(e.getId())))
                .toList();
        ConsignaAdjunta consigna = v.getConsignaArchivoId() == null ? null
                : new ConsignaAdjunta(v.getConsignaArchivoId(), archivos
                        .findByIdAndOrganizacionId(v.getConsignaArchivoId(), v.getOrganizacionId())
                        .map(Archivo::getNombreOriginal).orElse(null));
        PruebaDeLaVersion prueba = new PruebaDeLaVersion(v.getEnunciado(), consigna,
                v.getMateriales(), v.getHerramientasPermitidas(), v.getModalidad(),
                v.getDuracionMinutos(), v.getPlazoDias(), r.esCuestionario(), deLaPrueba);
        return new VersionDePreguntas(v.getId(), v.getEstado(), v.getGuiaCalificacion(),
                v.getMinutosObjetivo(), v.getVersionGuia() == null ? 1 : v.getVersionGuia(),
                r.total(), criterios.size(), r.todas().size(), criterios,
                r.sinCriterio().stream().map(ServicioPruebaPropiaImpl::comoPregunta).toList(),
                avisos, prueba);
    }

    private static PreguntaDeLaVersion comoPregunta(PreguntaDeLaPrueba pc) {
        Pregunta p = pc.pregunta();
        return new PreguntaDeLaVersion(p.getId(), p.getTipo(), p.getEnunciado(), pc.maximo(),
                p.getCriterioBancoId(), p.getOrden() == null ? 0 : p.getOrden(), p.getQueDebeTener(),
                pc.opciones().stream()
                        .map(o -> new OpcionDeLaVersion(o.getId(), o.getTexto(),
                                o.getPuntaje() == null ? 0 : o.getPuntaje().intValue(),
                                o.getOrden() == null ? 0 : o.getOrden()))
                        .toList());
    }

    // ============================== Apoyo: recalificación ==============================

    /** Los intentos entregados (no los «no completada») de esta versión. */
    private List<IntentoPrueba> entregadosDe(VersionBanco version) {
        return intentos.findByVersionBancoId(version.getId()).stream()
                .filter(i -> i.getEntregadoEn() != null && !i.isNoCompletada())
                .toList();
    }

    /**
     * Quién tiene criterios calificados por la IA (no ajustados a mano), con la guía de cada
     * nota: la gente a la que una guía nueva obliga a recalificar.
     */
    @Override
    protected Map<Long, List<Integer>> guiasDeLaIa(Vacante vacante, VersionBanco version) {
        List<IntentoPrueba> entregados = entregadosDe(version);
        if (entregados.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> postulacionDeIntento = entregados.stream()
                .collect(Collectors.toMap(IntentoPrueba::getId, IntentoPrueba::getPostulacionId));
        Map<Long, List<Integer>> salida = new LinkedHashMap<>();
        for (NotaCriterioPrueba n : notas.findByIntentoPruebaIdIn(postulacionDeIntento.keySet())) {
            if (!NotaCriterioPrueba.IA.equals(n.getOrigen()) || n.ajustadaAMano()
                    || n.getVersionGuia() == null) {
                continue;
            }
            salida.computeIfAbsent(postulacionDeIntento.get(n.getIntentoPruebaId()),
                    k -> new ArrayList<>()).add(n.getVersionGuia());
        }
        return salida;
    }

    // ============================== Apoyo: pequeño ==============================

    private static int entero(BigDecimal puntos) {
        return puntos == null ? 0 : puntos.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
    }
}
