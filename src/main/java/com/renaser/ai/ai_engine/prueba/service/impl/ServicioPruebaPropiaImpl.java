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
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CriterioDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EntregableDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.OpcionDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PreguntaDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PreguntaElegida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PruebaDeLaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDeOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDePregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Recalificacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ResumenDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.TextoDe;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VacanteCopiable;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregablePropuesto;
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
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.PreguntasInvalidasException;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.OpcionAValidar;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.PreguntaAValidar;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AgregarDeLaPropuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CambiarPuntosDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CorregirInstruccionesDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarCriterioDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarDatosDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarEntregable;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarPreguntaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PuntosDeCriterio;
import com.renaser.ai.ai_engine.prueba.entity.CriterioBancoEntregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.NotaCriterioPrueba;
import com.renaser.ai.ai_engine.prueba.repository.CriterioBancoEntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.NotaCriterioPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.CriterioDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.PreguntaDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.Resultado;
import com.renaser.ai.ai_engine.prueba.service.CierreDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.ReglasDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.ServicioPruebaPropia;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ver {@link ServicioPruebaPropia}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioPruebaPropiaImpl implements ServicioPruebaPropia {

    static final String BORRADOR = "BORRADOR";
    static final String PUBLICADA = "PUBLICADA";
    static final String PROPOSITO = "PRUEBA_PUESTO";
    public static final String PRUEBA_PROPIA = "PRUEBA_PROPIA";

    private static final String PERMISO_VER = "ver_vacantes";
    private static final String PERMISO_EDITAR = "editar_vacante";

    /** «Una vacante, una versión», desde la primera rendición (decisión 9). */
    private static final String VARA_QUIETA = "Alguien ya empezó a rendir esta prueba y su "
            + "contenido no se cambia: todos sus candidatos se miden con la misma vara. Solo se "
            + "pueden cambiar los puntos y las instrucciones de la IA, y cualquiera de los dos "
            + "vuelve a calcular la nota de todos.";

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
    private final CriterioBancoEntregableRepository miradas;
    private final IntentoPruebaRepository intentos;
    private final NotaCriterioPruebaRepository notas;
    private final PropuestaPreguntasRepository propuestas;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final CalificacionDeLaPruebaPropia calculo;
    private final CierreDeLaPruebaPropia cierre;
    private final ColaCalificacionIa cola;
    private final ServicioAuditoria auditoria;

    // ============================== Leer ==============================

    @Override
    @Transactional(readOnly = true)
    public EditorDePreguntas ver(ContextoUsuario quien, Long vacanteId) {
        return editor(quien, laVisible(quien, vacanteId));
    }

    private EditorDePreguntas editor(ContextoUsuario quien, Vacante vacante) {
        VersionBanco borrador = versionesBanco.pruebaPropiaDe(vacante.getId(), BORRADOR).orElse(null);
        VersionBanco publicada = versionesBanco.pruebaPropiaDe(vacante.getId(), PUBLICADA).orElse(null);
        Resultado delBorrador = borrador == null ? null : calculo.estructura(borrador.getId());
        Resultado deLaPublicada = publicada == null ? null : calculo.estructura(publicada.getId());
        return new EditorDePreguntas(vacante.getId(), vacante.getTitulo(), nivelDe(vacante),
                vacante.getInstrumentoEtapaTecnica(), true,
                puedeEditar(quien, vacante), yaSeRindio(vacante),
                delBorrador == null ? null : comoVersion(delBorrador),
                deLaPublicada == null ? null : comoVersion(deLaPublicada),
                resumen(delBorrador, deLaPublicada),
                publicada == null ? null : recalificacionDe(publicada),
                PROPOSITO);
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
    public EditorDePreguntas abrirBorrador(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        elBorrador(vacante);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas guardarDatos(ContextoUsuario quien, Long vacanteId,
                                          GuardarDatosDeLaPrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        String modalidad = textoONulo(datos.modalidad());
        List<String> faltas = ReglasDeLaPrueba.formaDelTiempo(modalidad, datos.duracionMinutos(),
                datos.plazoDias());
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("El tiempo no se puede guardar así", faltas);
        }
        VersionBanco borrador = elBorrador(vacante);
        borrador.setGuiaCalificacion(textoONulo(datos.guiaCalificacion()));
        borrador.setEnunciado(textoONulo(datos.enunciado()));
        borrador.setMateriales(textoONulo(datos.materiales()));
        borrador.setHerramientasPermitidas(textoONulo(datos.herramientasPermitidas()));
        borrador.setModalidad(modalidad);
        // Solo el número de su modalidad: unos minutos en una de plazo abierto mentirían.
        borrador.setDuracionMinutos(ReglasDeLaPrueba.CRONOMETRADA.equals(modalidad)
                ? datos.duracionMinutos() : null);
        borrador.setPlazoDias(ReglasDeLaPrueba.PLAZO_ABIERTO.equals(modalidad)
                ? datos.plazoDias() : null);
        versionesBanco.save(borrador);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas descartarBorrador(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        versionesBanco.pruebaPropiaDe(vacante.getId(), BORRADOR).ifPresent(this::vaciarYBorrar);
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
        exigirParteCalificada(datos);
        VersionBanco borrador = elBorrador(vacante);
        List<Long> mira = entregablesDelBorrador(borrador, datos.entregables());
        CriterioBanco nuevo = nuevoCriterio(borrador, datos.nombre(), datos.queEvalua(),
                entero(datos.puntosCalificados()), textoONulo(datos.calificador()));
        ponerMiradas(nuevo.getId(), mira);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas editarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                           GuardarCriterioDePrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        exigirParteCalificada(datos);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        CriterioBanco criterio = elCriterio(borrador, criterioId);
        List<Long> mira = entregablesDelBorrador(borrador, datos.entregables());
        criterio.setNombre(datos.nombre().strip());
        criterio.setQueEvalua(textoONulo(datos.queEvalua()));
        int calificados = entero(datos.puntosCalificados());
        criterio.setPuntosCalificados(calificados);
        criterio.setCalificador(calificados > 0 ? textoONulo(datos.calificador()) : null);
        criteriosBanco.save(criterio);
        ponerMiradas(criterio.getId(), mira);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas quitarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        CriterioBanco criterio = elCriterio(borrador, criterioId);
        // Sus preguntas quedan «sin criterio» hasta que alguien las mueva: borrarlas en cascada
        // sería perder trabajo por un clic en la X del bloque. Sus entregables siguen en la
        // prueba; solo deja de mirarlos.
        List<Pregunta> suyas = preguntas.findByVersionBancoIdOrderByOrden(borrador.getId()).stream()
                .filter(p -> criterioId.equals(p.getCriterioBancoId())).toList();
        int siguiente = siguienteOrden(borrador.getId(), null);
        for (Pregunta p : suyas) {
            p.setCriterioBancoId(null);
            p.setOrden(siguiente++);
        }
        preguntas.saveAllAndFlush(suyas);
        miradas.deleteByCriterioBancoId(criterio.getId());
        miradas.flush();
        criteriosBanco.delete(criterio);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas moverCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                          Mover datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        List<CriterioBanco> todos = new ArrayList<>(
                criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()));
        int i = indiceDe(todos, criterioId, CriterioBanco::getId, "Criterio");
        int j = i + paso(datos);
        if (j >= 0 && j < todos.size()) {
            renumerar(todos, i, j, CriterioBanco::setOrden);
            criteriosBanco.saveAll(todos);
        }
        return editor(quien, vacante);
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
            // Sin ningún criterio, la primera pregunta crea «General», como en la fase 1.
            criterioId = nuevoCriterio(borrador, "General", null, 0, null).getId();
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
        if (datos.criterioId() != null) {
            elCriterio(borrador, datos.criterioId());
        }
        if (!Objects.equals(pregunta.getCriterioBancoId(), datos.criterioId())) {
            pregunta.setCriterioBancoId(datos.criterioId());
            pregunta.setOrden(siguienteOrden(borrador.getId(), datos.criterioId()));
        }
        int puntos = puntosDe(datos.tipo(), datos.puntos());
        pregunta.setTipo(datos.tipo());
        pregunta.setEnunciado(datos.enunciado().strip());
        pregunta.setPuntos(puntos);
        pregunta.setEsPuntuable(puntos > 0);
        pregunta.setQueDebeTener(ReglasDePuntos.ABIERTA.equals(datos.tipo())
                ? textoONulo(datos.queDebeTener()) : null);
        preguntas.save(pregunta);
        // Las opciones se rehacen enteras: en un borrador ninguna respuesta apunta a ellas.
        opciones.deleteByPreguntaIdIn(List.of(pregunta.getId()));
        opciones.flush();
        guardarOpciones(pregunta, comoOpciones(datos.opciones()));
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas quitarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId) {
        Vacante vacante = laEditable(quien, vacanteId);
        Pregunta pregunta = laPregunta(elBorradorQueYaExiste(vacante), preguntaId);
        opciones.deleteByPreguntaIdIn(List.of(pregunta.getId()));
        preguntas.delete(pregunta);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas moverPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                          Mover datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        Pregunta pregunta = laPregunta(borrador, preguntaId);
        List<Pregunta> hermanas = new ArrayList<>(preguntas
                .findByVersionBancoIdOrderByOrden(borrador.getId()).stream()
                .filter(p -> Objects.equals(p.getCriterioBancoId(), pregunta.getCriterioBancoId()))
                .sorted(Comparator.comparing((Pregunta p) -> p.getOrden() == null
                        ? Integer.MAX_VALUE : p.getOrden()).thenComparing(Pregunta::getId))
                .toList());
        int i = indiceDe(hermanas, preguntaId, Pregunta::getId, "Pregunta");
        int j = i + paso(datos);
        if (j >= 0 && j < hermanas.size()) {
            renumerar(hermanas, i, j, Pregunta::setOrden);
            preguntas.saveAll(hermanas);
        }
        return editor(quien, vacante);
    }

    // ---------- Entregables ----------

    @Override
    @Transactional
    public EditorDePreguntas agregarEntregable(ContextoUsuario quien, Long vacanteId,
                                              GuardarEntregable datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        exigirFormaDelEntregable(datos);
        VersionBanco borrador = elBorrador(vacante);
        nuevoEntregable(borrador, datos.nombre(), datos.detalle(), datos.formato(),
                datos.obligatorio(), datos.queDebeTener());
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas editarEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId,
                                             GuardarEntregable datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        exigirFormaDelEntregable(datos);
        EntregableRequerido e = elEntregable(elBorradorQueYaExiste(vacante), entregableId);
        e.setNombre(datos.nombre().strip());
        e.setDetalle(datos.detalle() == null ? "" : datos.detalle().strip());
        e.setFormato(datos.formato());
        e.setEsObligatorio(datos.obligatorio());
        e.setQueDebeTener(textoONulo(datos.queDebeTener()));
        entregables.save(e);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas quitarEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId) {
        Vacante vacante = laEditable(quien, vacanteId);
        EntregableRequerido e = elEntregable(elBorradorQueYaExiste(vacante), entregableId);
        // Desaparece de lo que miran sus criterios (el panel pidió confirmarlo antes).
        miradas.deleteByEntregableRequeridoId(e.getId());
        miradas.flush();
        entregables.delete(e);
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

    // ---------- Publicar ----------

    @Override
    @Transactional
    public EditorDePreguntas publicar(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = versionesBanco.pruebaPropiaDe(vacante.getId(), BORRADOR)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay borrador que publicar: escribe la prueba primero"));
        List<String> faltas = ReglasDeLaPrueba.faltasParaPublicar(calculo.estructura(borrador.getId()));
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("La prueba no se puede publicar todavía: "
                    + (faltas.size() == 1 ? "falta una cosa" : "faltan " + faltas.size() + " cosas"),
                    faltas);
        }
        Optional<VersionBanco> anterior = versionesBanco.pruebaPropiaDe(vacante.getId(), PUBLICADA);
        if (anterior.isPresent()) {
            // Hasta la primera rendición se puede sustituir; desde ahí, no (decisión 9).
            if (yaSeRindio(vacante)) {
                throw new IllegalStateException(VARA_QUIETA);
            }
            VersionBanco saliente = anterior.get();
            saliente.setEstado("ARCHIVADA");
            // saveAndFlush: el índice «una publicada por vacante y propósito» no perdona que
            // el borrador pase a PUBLICADA antes de que esta se archive.
            versionesBanco.saveAndFlush(saliente);
        }
        borrador.setEstado(PUBLICADA);
        borrador.setPublicadaPorUsuarioId(quien.usuarioId());
        borrador.setPublicadaEn(Instant.now());
        versionesBanco.save(borrador);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_prueba_propia",
                "version_banco", borrador.getId(), Map.of("estado", BORRADOR),
                Map.of("estado", PUBLICADA, "vacante", vacante.getId()), null);
        return editor(quien, vacante);
    }

    // ============================== Con la versión publicada ==============================

    @Override
    @Transactional
    public CambioAplicado corregirInstrucciones(ContextoUsuario quien, Long vacanteId,
                                                CorregirInstruccionesDePrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco publicada = laPublicada(vacante);
        Map<Long, List<NotaCriterioPrueba>> conNota = notasDeLaIaPorPostulacion(publicada);
        exigirQueNoHayaRecalificacionEnCurso(conNota.keySet());

        // Sin saldo o con la IA apagada NO se guarda nada: guardarlo sin recalificar dejaría a
        // unos medidos con una guía y a otros con otra.
        if (!conNota.isEmpty()) {
            String motivo = cola.porQueNoSePuedeUsarLaIa(vacante.getOrganizacionId());
            if (motivo != null) {
                throw new IllegalStateException(motivo + " No se guardó el cambio: guardarlo "
                        + "sin recalificar dejaría a unos candidatos medidos con una guía y a "
                        + "otros con otra.");
            }
        }

        Map<String, Object> antes = new LinkedHashMap<>();
        Map<String, Object> despues = new LinkedHashMap<>();
        if (datos.guiaCalificacion() != null) {
            String nueva = textoONulo(datos.guiaCalificacion());
            if (!Objects.equals(nueva, publicada.getGuiaCalificacion())) {
                antes.put("guiaCalificacion", String.valueOf(publicada.getGuiaCalificacion()));
                despues.put("guiaCalificacion", String.valueOf(nueva));
                publicada.setGuiaCalificacion(nueva);
            }
        }
        Map<Long, CriterioBanco> criterios = criteriosBanco
                .findByVersionBancoIdOrderByOrdenAscIdAsc(publicada.getId()).stream()
                .collect(Collectors.toMap(CriterioBanco::getId, Function.identity()));
        for (TextoDe t : lista(datos.criterios())) {
            CriterioBanco c = criterios.get(t.id());
            if (c == null) {
                throw new IllegalArgumentException("El criterio " + t.id()
                        + " no es de la prueba publicada de esta vacante");
            }
            String nuevo = textoONulo(t.texto());
            if (!Objects.equals(nuevo, c.getQueEvalua())) {
                antes.put("criterio " + c.getId() + " · qué evalúa", String.valueOf(c.getQueEvalua()));
                despues.put("criterio " + c.getId() + " · qué evalúa", String.valueOf(nuevo));
                c.setQueEvalua(nuevo);
                criteriosBanco.save(c);
            }
        }
        Map<Long, Pregunta> suyas = preguntas.findByVersionBancoIdOrderByOrden(publicada.getId())
                .stream().collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        for (TextoDe t : lista(datos.preguntas())) {
            Pregunta p = suyas.get(t.id());
            if (p == null || !ReglasDePuntos.ABIERTA.equals(p.getTipo())) {
                throw new IllegalArgumentException("La pregunta " + t.id()
                        + " no es una abierta de la prueba publicada de esta vacante");
            }
            String nuevo = textoONulo(t.texto());
            if (!Objects.equals(nuevo, p.getQueDebeTener())) {
                antes.put("pregunta " + p.getId() + " · qué debe tener", String.valueOf(p.getQueDebeTener()));
                despues.put("pregunta " + p.getId() + " · qué debe tener", String.valueOf(nuevo));
                p.setQueDebeTener(nuevo);
                preguntas.save(p);
            }
        }
        Map<Long, EntregableRequerido> deLaVersion = entregables
                .findByVersionBancoIdOrderByOrdenAscIdAsc(publicada.getId()).stream()
                .collect(Collectors.toMap(EntregableRequerido::getId, Function.identity()));
        for (TextoDe t : lista(datos.entregables())) {
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
        if (antes.isEmpty()) {
            return new CambioAplicado(0);
        }
        int anterior = publicada.getVersionGuia() == null ? 1 : publicada.getVersionGuia();
        publicada.setVersionGuia(anterior + 1);
        versionesBanco.save(publicada);
        antes.put("versionGuia", anterior);
        despues.put("versionGuia", anterior + 1);
        auditoria.registrar(quien.organizacionId(), quien, "corregir_instrucciones_ia_prueba",
                "version_banco", publicada.getId(), antes, despues, null);

        int encoladas = 0;
        for (Long postulacionId : conNota.keySet()) {
            if (cola.recalificarPrueba(postulacionId)) {
                encoladas++;
            }
        }
        log.info("Instrucciones de la IA de la prueba corregidas en la vacante {} (guía {}): {} "
                + "personas a recalificar, {} encoladas", vacante.getId(), anterior + 1,
                conNota.size(), encoladas);
        return new CambioAplicado(conNota.size());
    }

    @Override
    @Transactional
    public CambioAplicado reintentarRecalificacion(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco publicada = laPublicada(vacante);
        int vigente = publicada.getVersionGuia() == null ? 1 : publicada.getVersionGuia();
        Map<Long, List<NotaCriterioPrueba>> conNota = notasDeLaIaPorPostulacion(publicada);
        Map<Long, ColaCalificacionIa.Seguimiento> seguimiento =
                cola.recalificacionDePrueba(List.copyOf(conNota.keySet()));
        List<Long> pendientes = new ArrayList<>();
        conNota.forEach((postulacionId, suyas) -> {
            boolean viejas = suyas.stream().anyMatch(n -> !Objects.equals(n.getVersionGuia(), vigente));
            ColaCalificacionIa.Seguimiento s = seguimiento.get(postulacionId);
            if (viejas && (s == null || !"EN_CURSO".equals(s.estado()))) {
                pendientes.add(postulacionId);
            }
        });
        if (pendientes.isEmpty()) {
            return new CambioAplicado(0);
        }
        String motivo = cola.porQueNoSePuedeUsarLaIa(vacante.getOrganizacionId());
        if (motivo != null) {
            return new CambioAplicado(0, motivo + " No se volvió a pedir la recalificación: "
                    + personas(pendientes.size(), "sigue", "siguen")
                    + " con la nota de la guía anterior.");
        }
        int encoladas = 0;
        for (Long postulacionId : pendientes) {
            if (cola.recalificarPrueba(postulacionId)) {
                encoladas++;
            }
        }
        int sinEncolar = pendientes.size() - encoladas;
        return new CambioAplicado(encoladas, sinEncolar == 0 ? null
                : personas(sinEncolar, "ya tenía", "ya tenían")
                        + " una recalificación en marcha: no se pidió otra.");
    }

    @Override
    @Transactional
    public CambioAplicado cambiarPuntos(ContextoUsuario quien, Long vacanteId, CambiarPuntosDePrueba datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco publicada = laPublicada(vacante);
        exigirQueNoHayaRecalificacionEnCurso(notasDeLaIaPorPostulacion(publicada).keySet());

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
        Map<Long, BigDecimal> calificadasNuevas = new HashMap<>();
        for (PuntosDeCriterio c : lista(datos.criterios())) {
            if (!criterioPorId.containsKey(c.id())) {
                throw new IllegalArgumentException("El criterio " + c.id()
                        + " no es de la prueba publicada de esta vacante");
            }
            calificadasNuevas.put(c.id(), c.puntosCalificados());
        }

        // Todo se valida antes de tocar nada: o cambia entero, o no cambia.
        List<String> faltas = new ArrayList<>();
        int suma = 0;
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
            if (ReglasDePuntos.esEntero(puntos)) {
                suma += puntos.intValue();
            }
        }
        for (CriterioDeLaPrueba c : r.criterios()) {
            BigDecimal nuevo = calificadasNuevas.getOrDefault(c.criterio().getId(),
                    BigDecimal.valueOf(c.calificadaMaximo()));
            String cual = "El criterio «" + c.criterio().getNombre() + "»";
            List<String> deEste = ReglasDeLaPrueba.formaDeLaParteCalificada(nuevo, c.calificador());
            deEste.forEach(f -> faltas.add(cual + ": " + f));
            if (!deEste.isEmpty()) {
                continue;
            }
            // Dar o quitar la parte calificada es cambiar qué se califica, y eso es contenido.
            if (c.tieneParteCalificada() && nuevo.signum() == 0) {
                faltas.add(cual + ": su parte calificada no puede quedar en 0, porque tiene "
                        + "abiertas o entregables que alguien califica.");
            } else if (!c.tieneParteCalificada() && nuevo.signum() > 0) {
                faltas.add(cual + " no tiene parte calificada: dársela es cambiar la prueba, y "
                        + "eso ya no se puede.");
            }
            suma += nuevo.intValue();
        }
        String total = ReglasDePuntos.faltaDelTotal(suma);
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
        calificadasNuevas.forEach((id, valor) -> {
            CriterioBanco c = criterioPorId.get(id).criterio();
            int viejo = c.getPuntosCalificados() == null ? 0 : c.getPuntosCalificados();
            int nuevo = valor.intValue();
            if (viejo != nuevo) {
                antes.put("criterio " + id + " · parte calificada", viejo);
                despues.put("criterio " + id + " · parte calificada", nuevo);
                c.setPuntosCalificados(nuevo);
                criteriosBanco.save(c);
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

    /** En proporción al máximo nuevo, con dos decimales; de un máximo 0 no hay proporción. */
    static BigDecimal escalar(BigDecimal nota, int maximoAnterior, int maximoNuevo) {
        if (nota == null) {
            return null;
        }
        if (maximoAnterior <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal escalada = nota.multiply(BigDecimal.valueOf(maximoNuevo))
                .divide(BigDecimal.valueOf(maximoAnterior), 2, RoundingMode.HALF_UP);
        return ReglasDePuntos.acotar(escalada, BigDecimal.valueOf(maximoNuevo));
    }

    // ============================== Copiar de otra vacante ==============================

    @Override
    @Transactional(readOnly = true)
    public List<VacanteCopiable> copiables(ContextoUsuario quien, Long vacanteId, String buscar,
                                           String nivel) {
        Vacante destino = laVisible(quien, vacanteId);
        List<VersionBanco> publicadas = versionesBanco.pruebasPropiasPublicadasDe(quien.organizacionId())
                .stream().filter(v -> !destino.getId().equals(v.getVacanteId())).toList();
        if (publicadas.isEmpty()) {
            return List.of();
        }
        Map<Long, Vacante> vacantesPorId = vacantes.findAllById(publicadas.stream()
                        .map(VersionBanco::getVacanteId).collect(Collectors.toSet())).stream()
                .filter(v -> quien.organizacionId().equals(v.getOrganizacionId())
                        && v.getEliminadaEn() == null)
                .collect(Collectors.toMap(Vacante::getId, Function.identity()));
        Map<Long, String> nivelPorPuesto = puestos.findAllById(vacantesPorId.values().stream()
                        .map(Vacante::getPuestoId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().filter(p -> quien.organizacionId().equals(p.getOrganizacionId()))
                .collect(Collectors.toMap(Puesto::getId, Puesto::getNivelPuestoCodigo));
        List<Long> versionIds = publicadas.stream().map(VersionBanco::getId).toList();
        Map<Long, Long> criteriosPorVersion = criteriosBanco.findByVersionBancoIdIn(versionIds).stream()
                .collect(Collectors.groupingBy(CriterioBanco::getVersionBancoId, Collectors.counting()));
        Map<Long, Long> preguntasPorVersion = preguntas.findByVersionBancoIdIn(versionIds).stream()
                .collect(Collectors.groupingBy(Pregunta::getVersionBancoId, Collectors.counting()));

        String buscado = sinTildes(buscar);
        return publicadas.stream()
                .filter(v -> vacantesPorId.containsKey(v.getVacanteId()))
                .map(v -> {
                    Vacante origen = vacantesPorId.get(v.getVacanteId());
                    return new VacanteCopiable(origen.getId(), origen.getTitulo(),
                            nivelPorPuesto.get(origen.getPuestoId()), origen.getPublicadaEn(),
                            estadoDe(origen),
                            criteriosPorVersion.getOrDefault(v.getId(), 0L).intValue(),
                            preguntasPorVersion.getOrDefault(v.getId(), 0L).intValue());
                })
                .filter(c -> buscado.isEmpty() || sinTildes(c.titulo()).contains(buscado))
                .filter(c -> nivel == null || nivel.isBlank() || nivel.equals(c.nivel()))
                .sorted(Comparator.comparing(VacanteCopiable::publicadaEn,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(VacanteCopiable::vacanteId, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VersionDePreguntas vistaPrevia(ContextoUsuario quien, Long vacanteId, Long vacanteOrigenId) {
        laVisible(quien, vacanteId);
        return comoVersion(calculo.estructura(laPublicadaDeOtraVacante(quien, vacanteOrigenId).getId()));
    }

    @Override
    @Transactional
    public EditorDePreguntas copiar(ContextoUsuario quien, Long vacanteId, CopiarDeOtraVacante datos) {
        Vacante destino = laEditable(quien, vacanteId);
        if (destino.getId().equals(datos.vacanteOrigenId())) {
            throw new IllegalArgumentException("Se copia de otra vacante, no de esta misma");
        }
        // Mismo corte por empresa que la vista previa: lo ajeno es 404 y no se crea nada.
        VersionBanco origen = laPublicadaDeOtraVacante(quien, datos.vacanteOrigenId());
        if (versionesBanco.pruebaPropiaDe(destino.getId(), PUBLICADA).isPresent() && yaSeRindio(destino)) {
            throw new IllegalStateException(VARA_QUIETA);
        }
        versionesBanco.pruebaPropiaDe(destino.getId(), BORRADOR).ifPresent(this::vaciarYBorrar);
        VersionBanco borrador = nuevoBorrador(destino);
        copiarContenido(origen, borrador);
        auditoria.registrar(quien.organizacionId(), quien, "copiar_prueba_propia",
                "version_banco", borrador.getId(), null,
                Map.of("desdeVacante", datos.vacanteOrigenId(), "desdeVersion", origen.getId()), null);
        return editor(quien, destino);
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
        if (!cola.encolarRecomendadorDePrueba(vacante.getOrganizacionId(), vacante.getId())) {
            pedida.setEstado(PropuestaPreguntas.FALLIDA);
            pedida.setMotivoFallo("No se pudo poner en la cola: ya había una en marcha, o la IA "
                    + "está apagada.");
            pedida.setTerminadaEn(Instant.now());
            propuestas.save(pedida);
            return new RecomendacionPedida(false, pedida.getMotivoFallo());
        }
        return new RecomendacionPedida(true, "La IA completará los " + faltan
                + " puntos que faltan. Tarda unos segundos.");
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

        // Un criterio que mira entregables propuestos se los trae consigo.
        for (Integer c : criteriosElegidos) {
            enLaPropuesta(criteriosPropuestos, c);
            entregablesElegidos.addAll(lista(criteriosPropuestos.get(c).entregables()));
        }
        Map<Integer, Long> entregableCreado = new HashMap<>();
        for (Integer i : entregablesElegidos) {
            if (i == null || i < 0 || i >= entregablesPropuestos.size()) {
                throw new IllegalArgumentException("La propuesta no tiene ese entregable");
            }
            EntregablePropuesto e = entregablesPropuestos.get(i);
            entregableCreado.put(i, nuevoEntregable(borrador, e.nombre(), e.detalle(), e.formato(),
                    !Boolean.FALSE.equals(e.obligatorio()), e.queDebeTener()).getId());
        }

        Map<Long, CriterioBanco> existentes = criteriosBanco
                .findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).stream()
                .collect(Collectors.toMap(CriterioBanco::getId, Function.identity()));
        Set<Long> entregablesDelBorrador = entregables
                .findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).stream()
                .map(EntregableRequerido::getId).collect(Collectors.toSet());

        Map<Integer, Set<Integer>> elegidas = new LinkedHashMap<>();
        for (Integer c : criteriosElegidos) {
            Set<Integer> todas = new LinkedHashSet<>();
            for (int i = 0; i < lista(criteriosPropuestos.get(c).preguntas()).size(); i++) {
                todas.add(i);
            }
            elegidas.computeIfAbsent(c, k -> new LinkedHashSet<>()).addAll(todas);
        }
        for (PreguntaElegida p : preguntasElegidas) {
            CriterioPropuesto cp = enLaPropuesta(criteriosPropuestos, p.criterio());
            if (p.pregunta() < 0 || p.pregunta() >= lista(cp.preguntas()).size()) {
                throw new IllegalArgumentException("La propuesta no tiene esa pregunta");
            }
            elegidas.computeIfAbsent(p.criterio(), k -> new LinkedHashSet<>()).add(p.pregunta());
        }
        Set<Integer> enteros = Set.copyOf(criteriosElegidos);
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
                boolean entero = enteros.contains(e.getKey());
                int calificada = entero && cp.parteCalificada() != null
                        ? cp.parteCalificada().setScale(0, RoundingMode.HALF_UP).intValue() : 0;
                CriterioBanco nuevo = nuevoCriterio(borrador, cp.nombre(), cp.queEvalua(), calificada,
                        calificada > 0 ? cp.calificador() : null);
                criterioId = nuevo.getId();
                if (entero) {
                    List<Long> mira = new ArrayList<>();
                    lista(cp.entregables()).forEach(i -> {
                        Long id = entregableCreado.get(i);
                        if (id != null) {
                            mira.add(id);
                        }
                    });
                    lista(cp.entregablesExistentes()).stream()
                            .filter(entregablesDelBorrador::contains).forEach(mira::add);
                    ponerMiradas(criterioId, mira);
                }
            }
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
                nuevaPregunta(borrador, pp.tipo(), pp.enunciado(), puntosDe(pp.tipo(), puntos), criterioId,
                        pp.queDebeTener(), suyas);
            }
        }
        return editor(quien, vacante);
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

    // ============================== Apoyo: vacante y versiones ==============================

    private Vacante laVisible(ContextoUsuario quien, Long vacanteId) {
        return alcance.laVacanteVisible(quien, vacanteId, PERMISO_VER);
    }

    private Vacante laEditable(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = alcance.laVacanteVisible(quien, vacanteId, PERMISO_EDITAR);
        if (vacante.getArchivadaEn() != null || "CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada o archivada no se edita");
        }
        return vacante;
    }

    private boolean puedeEditar(ContextoUsuario quien, Vacante vacante) {
        if (!quien.tiene(PERMISO_EDITAR)) {
            return false;
        }
        FiltroAlcance alcanceDeEdicion = permisos.alcanceDe(PERMISO_EDITAR);
        return alcanceDeEdicion != null
                && vacante.getArchivadaEn() == null
                && !"CERRADA".equals(vacante.getEstado())
                && alcance.alcanzaALaVacante(quien, alcanceDeEdicion, vacante);
    }

    /** La frontera de la decisión 9: alguien ya abrió su prueba en esta vacante. */
    private boolean yaSeRindio(Vacante vacante) {
        return intentos.algunoEmpezadoDeLaVacante(vacante.getId());
    }

    private String nivelDe(Vacante vacante) {
        return vacante.getPuestoId() == null ? null
                : puestos.findByIdAndOrganizacionId(vacante.getPuestoId(), vacante.getOrganizacionId())
                        .map(Puesto::getNivelPuestoCodigo).orElse(null);
    }

    private static String estadoDe(Vacante v) {
        if (v.getArchivadaEn() != null) {
            return "ARCHIVADA";
        }
        return "CERRADA".equals(v.getEstado()) ? "CERRADA" : "ACTIVA";
    }

    private VersionBanco laPublicada(Vacante vacante) {
        return versionesBanco.pruebaPropiaDe(vacante.getId(), PUBLICADA)
                .orElseThrow(() -> new IllegalStateException(
                        "Esta vacante todavía no tiene la prueba publicada"));
    }

    private VersionBanco laPublicadaDeOtraVacante(ContextoUsuario quien, Long vacanteOrigenId) {
        Vacante origen = vacantes
                .findByIdAndOrganizacionIdAndEliminadaEnIsNull(vacanteOrigenId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteOrigenId));
        return versionesBanco.pruebaPropiaDe(origen.getId(), PUBLICADA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prueba publicada de la vacante", "id", vacanteOrigenId));
    }

    /**
     * El borrador de la vacante; si no hay, se abre. Con una versión publicada nace como copia
     * suya, y eso solo hasta la primera rendición.
     */
    private VersionBanco elBorrador(Vacante vacante) {
        Optional<VersionBanco> borrador = versionesBanco.pruebaPropiaDe(vacante.getId(), BORRADOR);
        if (borrador.isPresent()) {
            return borrador.get();
        }
        Optional<VersionBanco> publicada = versionesBanco.pruebaPropiaDe(vacante.getId(), PUBLICADA);
        if (publicada.isPresent() && yaSeRindio(vacante)) {
            throw new IllegalStateException(VARA_QUIETA);
        }
        VersionBanco nuevo = nuevoBorrador(vacante);
        publicada.ifPresent(p -> copiarContenido(p, nuevo));
        return nuevo;
    }

    private VersionBanco elBorradorQueYaExiste(Vacante vacante) {
        return versionesBanco.pruebaPropiaDe(vacante.getId(), BORRADOR)
                .orElseThrow(() -> {
                    boolean publicada = versionesBanco.pruebaPropiaDe(vacante.getId(), PUBLICADA).isPresent();
                    return new IllegalStateException(publicada && yaSeRindio(vacante)
                            ? VARA_QUIETA
                            : "No hay borrador que cambiar: abre uno agregando un criterio, una "
                                    + "pregunta o un entregable");
                });
    }

    private VersionBanco nuevoBorrador(Vacante vacante) {
        return versionesBanco.saveAndFlush(VersionBanco.builder()
                .organizacionId(vacante.getOrganizacionId())
                .tipoBanco("VACANTE")
                .nivelPuestoCodigo(nivelDe(vacante))
                .vacanteId(vacante.getId())
                .proposito(PROPOSITO)
                .metodoCalificacion(CalificacionPorPuntos.METODO)
                .etiqueta("Prueba técnica · " + vacante.getTitulo())
                .estado(BORRADOR)
                .versionGuia(1)
                .creadoEn(Instant.now())
                .build());
    }

    /**
     * Copia el caso, el adjunto, el tiempo, la guía, los criterios con su parte calificada,
     * los entregables, qué mira cada criterio, las preguntas y sus opciones. Es una copia, no
     * un enlace: cambiar después cualquiera de las dos no toca la otra (el adjunto se comparte:
     * es el mismo archivo de la misma empresa, y nadie lo modifica).
     */
    private void copiarContenido(VersionBanco origen, VersionBanco destino) {
        destino.setGuiaCalificacion(origen.getGuiaCalificacion());
        destino.setEnunciado(origen.getEnunciado());
        destino.setConsignaArchivoId(origen.getConsignaArchivoId());
        destino.setUrlConsigna(origen.getUrlConsigna());
        destino.setMateriales(origen.getMateriales());
        destino.setHerramientasPermitidas(origen.getHerramientasPermitidas());
        destino.setModalidad(origen.getModalidad());
        destino.setDuracionMinutos(origen.getDuracionMinutos());
        destino.setPlazoDias(origen.getPlazoDias());
        versionesBanco.save(destino);

        Map<Long, Long> entregableNuevo = new HashMap<>();
        for (EntregableRequerido e : entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(origen.getId())) {
            EntregableRequerido copia = entregables.save(EntregableRequerido.builder()
                    .versionBancoId(destino.getId())
                    .nombre(e.getNombre())
                    .detalle(e.getDetalle() == null ? "" : e.getDetalle())
                    .formato(e.getFormato())
                    .esObligatorio(e.isEsObligatorio())
                    .orden(e.getOrden())
                    .queDebeTener(e.getQueDebeTener())
                    .creadoEn(Instant.now())
                    .build());
            entregableNuevo.put(e.getId(), copia.getId());
        }
        List<CriterioBanco> suyos = criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(origen.getId());
        Map<Long, List<Long>> miraPorCriterio = suyos.isEmpty() ? Map.of()
                : miradas.findByCriterioBancoIdIn(suyos.stream().map(CriterioBanco::getId).toList())
                        .stream().collect(Collectors.groupingBy(CriterioBancoEntregable::getCriterioBancoId,
                                Collectors.mapping(CriterioBancoEntregable::getEntregableRequeridoId,
                                        Collectors.toList())));
        Map<Long, Long> criterioNuevo = new HashMap<>();
        for (CriterioBanco c : suyos) {
            CriterioBanco copia = criteriosBanco.save(CriterioBanco.builder()
                    .versionBancoId(destino.getId())
                    .nombre(c.getNombre())
                    .queEvalua(c.getQueEvalua())
                    .orden(c.getOrden())
                    .puntosCalificados(c.getPuntosCalificados())
                    .calificador(c.getCalificador())
                    .creadoEn(Instant.now())
                    .build());
            criterioNuevo.put(c.getId(), copia.getId());
            ponerMiradas(copia.getId(), miraPorCriterio.getOrDefault(c.getId(), List.of()).stream()
                    .map(entregableNuevo::get).filter(Objects::nonNull).toList());
        }
        List<Pregunta> deLaVersion = preguntas.findByVersionBancoIdOrderByOrden(origen.getId());
        Map<Long, List<Opcion>> opcionesDe = deLaVersion.isEmpty() ? Map.of()
                : opciones.findByPreguntaIdIn(deLaVersion.stream().map(Pregunta::getId).toList()).stream()
                        .collect(Collectors.groupingBy(Opcion::getPreguntaId));
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
    }

    /** Un borrador se descarta entero: ningún intento apunta a él. */
    private void vaciarYBorrar(VersionBanco borrador) {
        List<CriterioBanco> suyos = criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId());
        if (!suyos.isEmpty()) {
            miradas.deleteByCriterioBancoIdIn(suyos.stream().map(CriterioBanco::getId).toList());
            miradas.flush();
        }
        List<Pregunta> deLaVersion = preguntas.findByVersionBancoIdOrderByOrden(borrador.getId());
        if (!deLaVersion.isEmpty()) {
            opciones.deleteByPreguntaIdIn(deLaVersion.stream().map(Pregunta::getId).toList());
        }
        preguntas.deleteByVersionBancoId(borrador.getId());
        entregables.deleteByVersionBancoId(borrador.getId());
        criteriosBanco.deleteByVersionBancoId(borrador.getId());
        versionesBanco.delete(borrador);
        versionesBanco.flush();
    }

    private CriterioBanco elCriterio(VersionBanco version, Long criterioId) {
        return criteriosBanco.findById(criterioId)
                .filter(c -> version.getId().equals(c.getVersionBancoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Criterio", "id", criterioId));
    }

    private Pregunta laPregunta(VersionBanco version, Long preguntaId) {
        return preguntas.findById(preguntaId)
                .filter(p -> version.getId().equals(p.getVersionBancoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta", "id", preguntaId));
    }

    private EntregableRequerido elEntregable(VersionBanco version, Long entregableId) {
        return entregables.findById(entregableId)
                .filter(e -> version.getId().equals(e.getVersionBancoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Entregable", "id", entregableId));
    }

    /** Los ids de entregables pedidos, comprobados contra el borrador: lo ajeno es 404. */
    private List<Long> entregablesDelBorrador(VersionBanco borrador, List<Long> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) {
            return List.of();
        }
        Set<Long> suyos = entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).stream()
                .map(EntregableRequerido::getId).collect(Collectors.toSet());
        List<Long> salida = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(pedidos)) {
            if (id == null || !suyos.contains(id)) {
                throw new ResourceNotFoundException("Entregable", "id", id);
            }
            salida.add(id);
        }
        return salida;
    }

    private void ponerMiradas(Long criterioId, Collection<Long> entregableIds) {
        miradas.deleteByCriterioBancoId(criterioId);
        miradas.flush();
        for (Long id : new LinkedHashSet<>(entregableIds)) {
            miradas.save(CriterioBancoEntregable.builder()
                    .criterioBancoId(criterioId)
                    .entregableRequeridoId(id)
                    .creadoEn(Instant.now())
                    .build());
        }
    }

    private CriterioBanco nuevoCriterio(VersionBanco borrador, String nombre, String queEvalua,
                                        int puntosCalificados, String calificador) {
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
                .puntosCalificados(puntosCalificados)
                .calificador(puntosCalificados > 0 ? calificador : null)
                .creadoEn(Instant.now())
                .build());
    }

    private EntregableRequerido nuevoEntregable(VersionBanco borrador, String nombre, String detalle,
                                                String formato, boolean obligatorio, String queDebeTener) {
        List<String> faltas = ReglasDeLaPrueba.formaDelEntregable(nombre, formato);
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("El entregable no se puede guardar así", faltas);
        }
        int orden = entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()).stream()
                .map(EntregableRequerido::getOrden).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        return entregables.save(EntregableRequerido.builder()
                .versionBancoId(borrador.getId())
                .nombre(nombre.strip())
                .detalle(detalle == null ? "" : detalle.strip())
                .formato(formato)
                .esObligatorio(obligatorio)
                .orden(orden)
                .queDebeTener(textoONulo(queDebeTener))
                .creadoEn(Instant.now())
                .build());
    }

    private Pregunta nuevaPregunta(VersionBanco borrador, String tipo, String enunciado, int puntos,
                                   Long criterioId, String queDebeTener, List<OpcionAValidar> suyasOpciones) {
        Pregunta pregunta = preguntas.save(Pregunta.builder()
                .versionBancoId(borrador.getId())
                .codigo(codigoNuevo(borrador))
                .tipo(tipo)
                .enunciado(enunciado.strip())
                .esPuntuable(puntos > 0)
                .orden(siguienteOrden(borrador.getId(), criterioId))
                .puntos(puntos)
                .criterioBancoId(criterioId)
                .queDebeTener(ReglasDePuntos.ABIERTA.equals(tipo) ? textoONulo(queDebeTener) : null)
                .creadoEn(Instant.now())
                .build());
        guardarOpciones(pregunta, suyasOpciones);
        return pregunta;
    }

    /** Las opciones de una cerrada, con su orden y la letra que pone el servidor. */
    private void guardarOpciones(Pregunta pregunta, List<OpcionAValidar> suyas) {
        if (!ReglasDePuntos.esCerrada(pregunta.getTipo())) {
            return;
        }
        boolean escala = ReglasDePuntos.ESCALA.equals(pregunta.getTipo());
        int n = 1;
        for (OpcionAValidar o : suyas) {
            String rotulo = o.texto() == null ? "" : o.texto().strip();
            opciones.save(Opcion.builder()
                    .preguntaId(pregunta.getId())
                    .letra(escala ? String.valueOf(n) : String.valueOf((char) ('A' + n - 1)))
                    .texto(rotulo.isEmpty() && escala ? String.valueOf(n) : rotulo)
                    .puntaje(o.puntos().setScale(0, RoundingMode.UNNECESSARY))
                    .orden(n)
                    .creadoEn(Instant.now())
                    .build());
            n++;
        }
    }

    private String codigoNuevo(VersionBanco borrador) {
        int mayor = preguntas.findByVersionBancoIdOrderByOrden(borrador.getId()).stream()
                .map(Pregunta::getCodigo)
                .filter(c -> c != null && c.matches("P\\d+"))
                .mapToInt(c -> Integer.parseInt(c.substring(1)))
                .max().orElse(0);
        return "P" + (mayor + 1);
    }

    private int siguienteOrden(Long versionId, Long criterioId) {
        return preguntas.findByVersionBancoIdOrderByOrden(versionId).stream()
                .filter(p -> Objects.equals(p.getCriterioBancoId(), criterioId))
                .map(Pregunta::getOrden).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
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

    private static void exigirParteCalificada(GuardarCriterioDePrueba datos) {
        List<String> faltas = ReglasDeLaPrueba.formaDeLaParteCalificada(datos.puntosCalificados(),
                textoONulo(datos.calificador()));
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException("El criterio no se puede guardar así", faltas);
        }
    }

    private static void exigirFormaDelEntregable(GuardarEntregable datos) {
        List<String> faltas = ReglasDeLaPrueba.formaDelEntregable(datos.nombre(), datos.formato());
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

    private VersionDePreguntas comoVersion(Resultado r) {
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
                            c.maximo(), c.sistemaMaximo(), c.esDeIa() ? c.calificadaMaximo() : 0,
                            c.preguntas().stream().map(ServicioPruebaPropiaImpl::comoPregunta).toList(),
                            c.calificadaMaximo(), c.calificador(), mira);
                })
                .toList();
        List<EntregableDeLaVersion> deLaPrueba = r.entregables().stream()
                .map(e -> new EntregableDeLaVersion(e.getId(), e.getNombre(),
                        e.getDetalle() == null || e.getDetalle().isBlank() ? null : e.getDetalle(),
                        e.getFormato(), e.isEsObligatorio(), e.getQueDebeTener(),
                        e.getOrden() == null ? 0 : e.getOrden(),
                        criteriosQueLoMiran.getOrDefault(e.getId(), List.of())))
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
                ReglasDeLaPrueba.faltasParaPublicar(r), prueba);
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
     * Quién tiene criterios calificados por la IA (no ajustados a mano), con esas notas: la
     * gente a la que una guía nueva obliga a recalificar.
     */
    private Map<Long, List<NotaCriterioPrueba>> notasDeLaIaPorPostulacion(VersionBanco version) {
        List<IntentoPrueba> entregados = entregadosDe(version);
        if (entregados.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> postulacionDeIntento = entregados.stream()
                .collect(Collectors.toMap(IntentoPrueba::getId, IntentoPrueba::getPostulacionId));
        Map<Long, List<NotaCriterioPrueba>> salida = new LinkedHashMap<>();
        for (NotaCriterioPrueba n : notas.findByIntentoPruebaIdIn(postulacionDeIntento.keySet())) {
            if (!NotaCriterioPrueba.IA.equals(n.getOrigen()) || n.ajustadaAMano()
                    || n.getVersionGuia() == null) {
                continue;
            }
            salida.computeIfAbsent(postulacionDeIntento.get(n.getIntentoPruebaId()),
                    k -> new ArrayList<>()).add(n);
        }
        return salida;
    }

    private void exigirQueNoHayaRecalificacionEnCurso(Collection<Long> postulacionIds) {
        boolean enCurso = cola.recalificacionDePrueba(List.copyOf(postulacionIds)).values().stream()
                .anyMatch(s -> "EN_CURSO".equals(s.estado()));
        if (enCurso) {
            throw new IllegalStateException("Hay una recalificación de la IA en curso: espera a "
                    + "que termine para volver a cambiar la guía o los puntos.");
        }
    }

    private Recalificacion recalificacionDe(VersionBanco publicada) {
        int vigente = publicada.getVersionGuia() == null ? 1 : publicada.getVersionGuia();
        Map<Long, List<NotaCriterioPrueba>> conNota = notasDeLaIaPorPostulacion(publicada);
        int rindieron = entregadosDe(publicada).size();
        if (conNota.isEmpty()) {
            return new Recalificacion(rindieron, 0, 0, 0, 0, List.of());
        }
        Map<Long, ColaCalificacionIa.Seguimiento> seguimiento =
                cola.recalificacionDePrueba(List.copyOf(conNota.keySet()));
        int alDia = 0;
        int recalificando = 0;
        int pendientes = 0;
        Set<String> motivos = new LinkedHashSet<>();
        for (Map.Entry<Long, List<NotaCriterioPrueba>> e : conNota.entrySet()) {
            boolean alDiaEsta = e.getValue().stream().allMatch(n -> Objects.equals(n.getVersionGuia(), vigente));
            ColaCalificacionIa.Seguimiento s = seguimiento.get(e.getKey());
            if (alDiaEsta) {
                alDia++;
            } else if (s != null && "EN_CURSO".equals(s.estado())) {
                recalificando++;
            } else {
                pendientes++;
                motivos.add(s != null && s.motivo() != null ? s.motivo()
                        : "No se llegó a pedir su recalificación.");
            }
        }
        return new Recalificacion(rindieron, conNota.size(), alDia, recalificando, pendientes,
                List.copyOf(motivos));
    }

    // ============================== Apoyo: pequeño ==============================

    private static String personas(int cuantas, String verboUna, String verboVarias) {
        return cuantas == 1 ? "1 persona " + verboUna : cuantas + " personas " + verboVarias;
    }

    private static int paso(Mover datos) {
        return switch (datos.direccion().toUpperCase(Locale.ROOT)) {
            case "ARRIBA" -> -1;
            case "ABAJO" -> 1;
            default -> throw new IllegalArgumentException("La dirección es ARRIBA o ABAJO");
        };
    }

    private static <T> int indiceDe(List<T> lista, Long id, Function<T, Long> suId, String que) {
        for (int i = 0; i < lista.size(); i++) {
            if (id.equals(suId.apply(lista.get(i)))) {
                return i;
            }
        }
        throw new ResourceNotFoundException(que, "id", id);
    }

    private static <T> void renumerar(List<T> lista, int i, int j, BiConsumer<T, Integer> ponerOrden) {
        T a = lista.get(i);
        lista.set(i, lista.get(j));
        lista.set(j, a);
        for (int k = 0; k < lista.size(); k++) {
            ponerOrden.accept(lista.get(k), k + 1);
        }
    }

    private static int entero(BigDecimal puntos) {
        return puntos == null ? 0 : puntos.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private static String sinTildes(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    private static <T> List<T> lista(List<T> valor) {
        return valor == null ? List.of() : valor;
    }
}
