package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.*;
import com.renaser.ai.ai_engine.perfilintegral.entity.CampoCaso;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.ParConsistencia;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.RangoPregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.mapper.CampoCasoMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.OpcionMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.ParConsistenciaMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.PreguntaMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.RangoPreguntaMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.VersionBancoMapper;
import com.renaser.ai.ai_engine.perfilintegral.repository.CampoCasoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ParConsistenciaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RangoPreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioBancoPreguntas;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioBancoPreguntasImpl implements ServicioBancoPreguntas {

    // Los formatos del banco v0.1. El motor v3 no sabe puntuarlos (puntuarItem los salta
    // en silencio), así que un banco nuevo no puede declararlos puntuables: parecería que
    // funciona y nunca sumaría un punto.
    private static final Set<String> TIPOS_V01 = Set.of(
            "ESTILO", "SITUACION", "CONDUCTUAL", "MICROCASO", "DILEMA", "CONSISTENCIA");

    private final VersionBancoRepository versiones;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final RangoPreguntaRepository rangos;
    private final CampoCasoRepository camposCaso;
    private final ParConsistenciaRepository pares;
    private final PreguntaDimensionRepository preguntaDimensiones;
    private final EvaluacionRepository evaluaciones;
    private final VersionBancoMapper versionBancoMapper;
    private final PreguntaMapper preguntaMapper;
    private final OpcionMapper opcionMapper;
    private final RangoPreguntaMapper rangoMapper;
    private final CampoCasoMapper campoCasoMapper;
    private final ParConsistenciaMapper parMapper;
    private final ServicioAuditoria auditoria;
    private final Permisos permisos;

    @Override
    @Transactional
    public Long crearVersion(ContextoUsuario quien, CrearVersionBanco datos) {
        VersionBanco version = versiones.save(VersionBanco.builder()
                .organizacionId(quien.organizacionId())
                .tipoBanco(datos.tipoBanco())
                .nivelPuestoCodigo(datos.nivelPuestoCodigo())
                .etiqueta(datos.etiqueta())
                .estado("BORRADOR")
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_version_banco",
                "version_banco", version.getId(), null,
                Map.of("estado", "BORRADOR", "tipoBanco", datos.tipoBanco()), null);
        return version.getId();
    }

    @Override
    public List<VersionBancoResponse> listarVersiones(ContextoUsuario quien) {
        permisos.alcanceDe("ver_banco_preguntas");
        return versiones.findVisibles(quien.organizacionId()).stream()
                .map(versionBancoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void publicarVersion(ContextoUsuario quien, Long id) {
        VersionBanco version = laVersionVisible(quien, id);
        if (!"BORRADOR".equals(version.getEstado())) {
            throw new IllegalStateException("Solo se publica una versión en borrador; esta está " + version.getEstado());
        }
        // La foto completa (preguntas + opciones + tramos) solo existe aquí: una pregunta
        // se crea antes que sus opciones, así que validar antes sería validar a medias.
        validarCoherencia(version);

        version.setEstado("PUBLICADA");
        version.setPublicadaPorUsuarioId(quien.usuarioId());
        version.setPublicadaEn(Instant.now());
        versiones.save(version);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_version_banco",
                "version_banco", id, Map.of("estado", "BORRADOR"), Map.of("estado", "PUBLICADA"), null);

        // Publicar retira a la que reemplaza. El selector del candidato coge la PUBLICADA
        // más reciente, así que dejar dos publicadas del mismo nivel "funciona", pero el
        // estado miente — y el estado es lo único que el panel ve.
        for (VersionBanco saliente : versiones.findPublicadasHermanas(
                version.getTipoBanco(), version.getNivelPuestoCodigo(),
                version.getOrganizacionId(), version.getId())) {
            archivarYRepuntar(quien, saliente, version,
                    "reemplazada al publicar la versión " + version.getId());
        }
    }

    @Override
    @Transactional
    public void archivarVersion(ContextoUsuario quien, Long id) {
        VersionBanco version = laVersionVisible(quien, id);
        if (!"PUBLICADA".equals(version.getEstado())) {
            // Un BORRADOR no circula: abandonarlo no necesita endpoint. Y una ARCHIVADA ya está.
            throw new IllegalStateException("Solo se archiva una versión publicada; esta está " + version.getEstado());
        }

        VersionBanco reemplazo = versiones.findPublicadasHermanas(
                        version.getTipoBanco(), version.getNivelPuestoCodigo(),
                        version.getOrganizacionId(), version.getId()).stream()
                .max(Comparator.comparing(VersionBanco::getPublicadaEn))
                .orElse(null);

        if (reemplazo == null && "NIVEL".equals(version.getTipoBanco())) {
            int sinEmpezar = evaluaciones.findByVersionBancoNivelIdAndIniciadaEnIsNull(id).size();
            if (sinEmpezar > 0) {
                throw new IllegalStateException(
                        "Archivar dejaría a " + sinEmpezar + " candidato(s) sin banco de preguntas: "
                                + "publica antes una versión de reemplazo para este nivel");
            }
        }
        archivarYRepuntar(quien, version, reemplazo, null);
    }

    /**
     * Archiva una versión y, si hay reemplazo, le pasa las evaluaciones que aún no empezaron.
     * A quien ya empezó no se le toca: su examen está armado con las preguntas de su versión
     * y un banco archivado conserva sus claves, así que se puntúa igual (RF-138).
     */
    private void archivarYRepuntar(ContextoUsuario quien, VersionBanco saliente,
                                   VersionBanco reemplazo, String motivo) {
        int repuntadas = 0;
        if (reemplazo != null && "NIVEL".equals(saliente.getTipoBanco())) {
            // iniciada_en es la frontera exacta que mira iniciar() para armar el examen:
            // antes de eso el candidato no ha visto ni una pregunta y el cambio es invisible.
            List<Evaluacion> sinEmpezar =
                    evaluaciones.findByVersionBancoNivelIdAndIniciadaEnIsNull(saliente.getId());
            for (Evaluacion evaluacion : sinEmpezar) {
                evaluacion.setVersionBancoNivelId(reemplazo.getId());
            }
            evaluaciones.saveAll(sinEmpezar);
            repuntadas = sinEmpezar.size();
        }
        saliente.setEstado("ARCHIVADA");
        versiones.save(saliente);
        auditoria.registrar(quien.organizacionId(), quien, "archivar_version_banco",
                "version_banco", saliente.getId(),
                Map.of("estado", "PUBLICADA"),
                Map.of("estado", "ARCHIVADA", "evaluacionesRepuntadas", repuntadas), motivo);
    }

    @Override
    @Transactional
    public Long crearPregunta(ContextoUsuario quien, Long versionBancoId, CrearPregunta datos) {
        VersionBanco version = laVersionVisible(quien, versionBancoId);
        exigirBorrador(version, "agregar preguntas");

        // Campos que solo tienen sentido en su formato: rechazarlos temprano evita que el
        // panel guarde configuración que el motor jamás va a mirar.
        if (datos.casosPedidos() != null && !"CD".equals(datos.tipo())) {
            throw new IllegalArgumentException("casosPedidos es solo de los casos descompuestos (CD)");
        }
        if ((datos.rangosDePreguntaCodigo() != null || datos.formulaPuntaje() != null)
                && !"V".equals(datos.tipo())) {
            throw new IllegalArgumentException("la tabla de rangos y la fórmula son solo de los ítems V");
        }

        Pregunta pregunta = preguntas.save(Pregunta.builder()
                .versionBancoId(versionBancoId)
                .codigo(datos.codigo())
                .bloque(datos.bloque())
                .tipo(datos.tipo())
                .enunciado(datos.enunciado())
                .situacion(datos.situacion())
                .logicaInterna(datos.logicaInterna())
                .esPuntuable(datos.esPuntuable())
                .orden(datos.orden())
                .peso(datos.peso())
                .esClave(Boolean.TRUE.equals(datos.esClave()))
                .esEliminatorio(Boolean.TRUE.equals(datos.esEliminatorio()))
                .casosPedidos(datos.casosPedidos())
                .rangosDePreguntaCodigo(datos.rangosDePreguntaCodigo())
                .formulaPuntaje(datos.formulaPuntaje())
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_pregunta",
                "pregunta", pregunta.getId(), null,
                Map.of("codigo", datos.codigo(), "tipo", datos.tipo()), null);
        return pregunta.getId();
    }

    @Override
    public List<PreguntaResponse> listarPreguntas(ContextoUsuario quien, Long versionBancoId) {
        permisos.alcanceDe("ver_banco_preguntas");
        laVersionVisible(quien, versionBancoId);
        return preguntas.findByVersionBancoIdOrderByOrden(versionBancoId).stream()
                .map(preguntaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public Long agregarOpcion(ContextoUsuario quien, Long preguntaId, CrearOpcion datos) {
        Pregunta pregunta = laPreguntaVisible(quien, preguntaId);
        // El mismo candado que crearPregunta. Sin él, la clave de una pregunta publicada se
        // podía alterar por debajo de un examen ya en curso.
        exigirBorrador(laVersionVisible(quien, pregunta.getVersionBancoId()), "agregar opciones");

        Opcion opcion = opciones.save(Opcion.builder()
                .preguntaId(pregunta.getId())
                .letra(datos.letra())
                .texto(datos.texto())
                .puntaje(datos.puntaje() == null ? null : BigDecimal.valueOf(datos.puntaje()))
                .valor(datos.valor())
                .esDistractor(Boolean.TRUE.equals(datos.esDistractor()))
                .ordenCorrecto(datos.ordenCorrecto())
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "agregar_opcion",
                "opcion", opcion.getId(), null, Map.of("letra", datos.letra()), null);
        return opcion.getId();
    }

    @Override
    public List<OpcionResponse> listarOpciones(ContextoUsuario quien, Long preguntaId) {
        permisos.alcanceDe("ver_banco_preguntas");
        laPreguntaVisible(quien, preguntaId);
        return opciones.findByPreguntaIdOrderByLetra(preguntaId).stream()
                .map(opcionMapper::toResponse)
                .toList();
    }

    // ---------- Los tramos de los ítems V ----------

    @Override
    @Transactional
    public Long agregarRango(ContextoUsuario quien, Long preguntaId, CrearRango datos) {
        Pregunta pregunta = laPreguntaVisible(quien, preguntaId);
        exigirBorrador(laVersionVisible(quien, pregunta.getVersionBancoId()), "agregar rangos");
        if (!"V".equals(pregunta.getTipo())) {
            throw new IllegalArgumentException("los rangos de puntaje son solo de los ítems V");
        }
        RangoPregunta rango = rangos.save(RangoPregunta.builder()
                .preguntaId(pregunta.getId())
                .orden(datos.orden())
                .condicion(datos.condicion())
                .puntaje(datos.puntaje())
                .generaBandera(Boolean.TRUE.equals(datos.generaBandera()))
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "agregar_rango_pregunta",
                "rango_pregunta", rango.getId(), null,
                Map.of("pregunta", pregunta.getCodigo(), "orden", datos.orden()), null);
        return rango.getId();
    }

    @Override
    public List<RangoResponse> listarRangos(ContextoUsuario quien, Long preguntaId) {
        permisos.alcanceDe("ver_banco_preguntas");
        laPreguntaVisible(quien, preguntaId);
        return rangos.findByPreguntaIdOrderByOrden(preguntaId).stream()
                .map(rangoMapper::toResponse)
                .toList();
    }

    // ---------- Los campos de los casos descompuestos ----------

    @Override
    @Transactional
    public Long agregarCampoCaso(ContextoUsuario quien, Long preguntaId, CrearCampoCaso datos) {
        Pregunta pregunta = laPreguntaVisible(quien, preguntaId);
        exigirBorrador(laVersionVisible(quien, pregunta.getVersionBancoId()), "agregar campos");
        if (!"CD".equals(pregunta.getTipo())) {
            throw new IllegalArgumentException("los campos de caso son solo de los casos descompuestos (CD)");
        }
        CampoCaso campo = camposCaso.save(CampoCaso.builder()
                .preguntaId(pregunta.getId())
                .orden(datos.orden())
                .etiqueta(datos.etiqueta())
                .validacion(datos.validacion())
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "agregar_campo_caso",
                "campo_caso", campo.getId(), null,
                Map.of("pregunta", pregunta.getCodigo(), "orden", datos.orden()), null);
        return campo.getId();
    }

    @Override
    public List<CampoCasoResponse> listarCamposCaso(ContextoUsuario quien, Long preguntaId) {
        permisos.alcanceDe("ver_banco_preguntas");
        laPreguntaVisible(quien, preguntaId);
        return camposCaso.findByPreguntaIdOrderByOrden(preguntaId).stream()
                .map(campoCasoMapper::toResponse)
                .toList();
    }

    // ---------- Los pares de consistencia ----------

    @Override
    @Transactional
    public Long agregarParConsistencia(ContextoUsuario quien, Long versionBancoId, CrearParConsistencia datos) {
        VersionBanco version = laVersionVisible(quien, versionBancoId);
        exigirBorrador(version, "agregar pares de consistencia");

        // La FK solo exige que las preguntas existan; que sean de ESTA versión hay que
        // comprobarlo aquí, o un par podría cruzar dos bancos y no medir nada.
        exigirDeLaVersion(versionBancoId, datos.preguntaAId());
        exigirDeLaVersion(versionBancoId, datos.preguntaBId());

        ParConsistencia par = pares.save(ParConsistencia.builder()
                .versionBancoId(versionBancoId)
                .preguntaAId(datos.preguntaAId())
                .preguntaBId(datos.preguntaBId())
                .diferenciaMaxima(datos.diferenciaMaxima())
                .penalizacionPorcentaje(datos.penalizacionPorcentaje())
                .separacionMinimaItems(datos.separacionMinimaItems())
                .condicion(datos.condicion())
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "agregar_par_consistencia",
                "par_consistencia", par.getId(), null,
                Map.of("preguntaAId", datos.preguntaAId(), "preguntaBId", datos.preguntaBId()), null);
        return par.getId();
    }

    @Override
    public List<ParConsistenciaResponse> listarParesConsistencia(ContextoUsuario quien, Long versionBancoId) {
        permisos.alcanceDe("ver_banco_preguntas");
        laVersionVisible(quien, versionBancoId);
        return pares.findByVersionBancoId(versionBancoId).stream()
                .map(parMapper::toResponse)
                .toList();
    }

    // ---------- La edición de un borrador ----------
    // Mientras la versión está en BORRADOR se edita entera: reemplazar, borrar, incluso
    // descartarla. Nada de esto ha circulado todavía, así que no hay historia que
    // proteger. En cuanto se publica, esta puerta se cierra (exigirBorrador) y solo
    // queda la corrección editorial de más abajo.

    @Override
    @Transactional
    public void actualizarPregunta(ContextoUsuario quien, Long preguntaId, CrearPregunta datos) {
        Pregunta pregunta = laPreguntaVisible(quien, preguntaId);
        exigirBorrador(laVersionVisible(quien, pregunta.getVersionBancoId()), "editar preguntas");
        // Las mismas guardas por formato que al crear: editar no es una puerta trasera.
        if (datos.casosPedidos() != null && !"CD".equals(datos.tipo())) {
            throw new IllegalArgumentException("casosPedidos es solo de los casos descompuestos (CD)");
        }
        if ((datos.rangosDePreguntaCodigo() != null || datos.formulaPuntaje() != null)
                && !"V".equals(datos.tipo())) {
            throw new IllegalArgumentException("la tabla de rangos y la fórmula son solo de los ítems V");
        }

        Map<String, Object> antes = Map.of("codigo", pregunta.getCodigo(),
                "tipo", pregunta.getTipo(), "enunciado", pregunta.getEnunciado());
        pregunta.setCodigo(datos.codigo());
        pregunta.setBloque(datos.bloque());
        pregunta.setTipo(datos.tipo());
        pregunta.setEnunciado(datos.enunciado());
        pregunta.setSituacion(datos.situacion());
        pregunta.setLogicaInterna(datos.logicaInterna());
        pregunta.setEsPuntuable(datos.esPuntuable());
        pregunta.setOrden(datos.orden());
        pregunta.setPeso(datos.peso());
        pregunta.setEsClave(Boolean.TRUE.equals(datos.esClave()));
        pregunta.setEsEliminatorio(Boolean.TRUE.equals(datos.esEliminatorio()));
        pregunta.setCasosPedidos(datos.casosPedidos());
        pregunta.setRangosDePreguntaCodigo(datos.rangosDePreguntaCodigo());
        pregunta.setFormulaPuntaje(datos.formulaPuntaje());
        preguntas.save(pregunta);

        auditoria.registrar(quien.organizacionId(), quien, "editar_pregunta",
                "pregunta", preguntaId, antes,
                Map.of("codigo", datos.codigo(), "tipo", datos.tipo(),
                        "enunciado", datos.enunciado()), null);
    }

    @Override
    @Transactional
    public void eliminarPregunta(ContextoUsuario quien, Long preguntaId) {
        Pregunta pregunta = laPreguntaVisible(quien, preguntaId);
        exigirBorrador(laVersionVisible(quien, pregunta.getVersionBancoId()), "eliminar preguntas");

        // De fuera hacia dentro: primero lo que la apunta, o la FK no deja borrarla.
        pares.deleteByPreguntaAIdOrPreguntaBId(preguntaId, preguntaId);
        List<Long> suya = List.of(preguntaId);
        opciones.deleteByPreguntaIdIn(suya);
        rangos.deleteByPreguntaIdIn(suya);
        camposCaso.deleteByPreguntaIdIn(suya);
        preguntaDimensiones.deleteByPreguntaIdIn(suya);
        preguntas.delete(pregunta);

        auditoria.registrar(quien.organizacionId(), quien, "eliminar_pregunta",
                "pregunta", preguntaId,
                Map.of("codigo", pregunta.getCodigo(), "tipo", pregunta.getTipo()),
                null, null);
    }

    @Override
    @Transactional
    public void actualizarOpcion(ContextoUsuario quien, Long opcionId, CrearOpcion datos) {
        Opcion opcion = laOpcionEditable(quien, opcionId, "editar opciones");
        Map<String, Object> antes = Map.of("letra", opcion.getLetra(), "texto", opcion.getTexto());
        opcion.setLetra(datos.letra());
        opcion.setTexto(datos.texto());
        opcion.setPuntaje(datos.puntaje() == null ? null : BigDecimal.valueOf(datos.puntaje()));
        opcion.setValor(datos.valor());
        opcion.setEsDistractor(Boolean.TRUE.equals(datos.esDistractor()));
        opcion.setOrdenCorrecto(datos.ordenCorrecto());
        opciones.save(opcion);
        auditoria.registrar(quien.organizacionId(), quien, "editar_opcion",
                "opcion", opcionId, antes,
                Map.of("letra", datos.letra(), "texto", datos.texto()), null);
    }

    @Override
    @Transactional
    public void eliminarOpcion(ContextoUsuario quien, Long opcionId) {
        Opcion opcion = laOpcionEditable(quien, opcionId, "eliminar opciones");
        opciones.delete(opcion);
        auditoria.registrar(quien.organizacionId(), quien, "eliminar_opcion",
                "opcion", opcionId,
                Map.of("letra", opcion.getLetra(), "texto", opcion.getTexto()), null, null);
    }

    @Override
    @Transactional
    public void actualizarRango(ContextoUsuario quien, Long rangoId, CrearRango datos) {
        RangoPregunta rango = elRangoEditable(quien, rangoId, "editar rangos");
        Map<String, Object> antes = Map.of("orden", rango.getOrden(),
                "condicion", rango.getCondicion(), "puntaje", rango.getPuntaje());
        rango.setOrden(datos.orden());
        rango.setCondicion(datos.condicion());
        rango.setPuntaje(datos.puntaje());
        rango.setGeneraBandera(Boolean.TRUE.equals(datos.generaBandera()));
        rangos.save(rango);
        auditoria.registrar(quien.organizacionId(), quien, "editar_rango_pregunta",
                "rango_pregunta", rangoId, antes,
                Map.of("orden", datos.orden(), "condicion", datos.condicion(),
                        "puntaje", datos.puntaje()), null);
    }

    @Override
    @Transactional
    public void eliminarRango(ContextoUsuario quien, Long rangoId) {
        RangoPregunta rango = elRangoEditable(quien, rangoId, "eliminar rangos");
        rangos.delete(rango);
        auditoria.registrar(quien.organizacionId(), quien, "eliminar_rango_pregunta",
                "rango_pregunta", rangoId,
                Map.of("orden", rango.getOrden(), "condicion", rango.getCondicion()),
                null, null);
    }

    @Override
    @Transactional
    public void actualizarCampoCaso(ContextoUsuario quien, Long campoId, CrearCampoCaso datos) {
        CampoCaso campo = elCampoEditable(quien, campoId, "editar campos");
        Map<String, Object> antes = Map.of("orden", campo.getOrden(),
                "etiqueta", campo.getEtiqueta());
        campo.setOrden(datos.orden());
        campo.setEtiqueta(datos.etiqueta());
        campo.setValidacion(datos.validacion());
        camposCaso.save(campo);
        auditoria.registrar(quien.organizacionId(), quien, "editar_campo_caso",
                "campo_caso", campoId, antes,
                Map.of("orden", datos.orden(), "etiqueta", datos.etiqueta()), null);
    }

    @Override
    @Transactional
    public void eliminarCampoCaso(ContextoUsuario quien, Long campoId) {
        CampoCaso campo = elCampoEditable(quien, campoId, "eliminar campos");
        camposCaso.delete(campo);
        auditoria.registrar(quien.organizacionId(), quien, "eliminar_campo_caso",
                "campo_caso", campoId,
                Map.of("orden", campo.getOrden(), "etiqueta", campo.getEtiqueta()),
                null, null);
    }

    @Override
    @Transactional
    public void actualizarParConsistencia(ContextoUsuario quien, Long parId,
                                          CrearParConsistencia datos) {
        ParConsistencia par = elParEditable(quien, parId, "editar pares de consistencia");
        // El par nuevo tiene que seguir siendo de esta versión, igual que al crearlo.
        exigirDeLaVersion(par.getVersionBancoId(), datos.preguntaAId());
        exigirDeLaVersion(par.getVersionBancoId(), datos.preguntaBId());

        Map<String, Object> antes = Map.of("preguntaAId", par.getPreguntaAId(),
                "preguntaBId", par.getPreguntaBId());
        par.setPreguntaAId(datos.preguntaAId());
        par.setPreguntaBId(datos.preguntaBId());
        par.setDiferenciaMaxima(datos.diferenciaMaxima());
        par.setPenalizacionPorcentaje(datos.penalizacionPorcentaje());
        par.setSeparacionMinimaItems(datos.separacionMinimaItems());
        par.setCondicion(datos.condicion());
        pares.save(par);
        auditoria.registrar(quien.organizacionId(), quien, "editar_par_consistencia",
                "par_consistencia", parId, antes,
                Map.of("preguntaAId", datos.preguntaAId(), "preguntaBId", datos.preguntaBId()),
                null);
    }

    @Override
    @Transactional
    public void eliminarParConsistencia(ContextoUsuario quien, Long parId) {
        ParConsistencia par = elParEditable(quien, parId, "eliminar pares de consistencia");
        pares.delete(par);
        auditoria.registrar(quien.organizacionId(), quien, "eliminar_par_consistencia",
                "par_consistencia", parId,
                Map.of("preguntaAId", par.getPreguntaAId(), "preguntaBId", par.getPreguntaBId()),
                null, null);
    }

    /**
     * Descarta un borrador entero: se borra de verdad, con sus hijas.
     *
     * <p>Es la única cosa del banco que se borra, y puede serlo porque un BORRADOR nunca
     * llegó a nadie: solo una versión PUBLICADA se le asigna a un candidato. Lo que el
     * append-only protege es lo que alguien pudo ver, y aquí no hay nada de eso. Aun así
     * se comprueba que ninguna evaluación lo apunte antes de tocarlo: si algún día algo
     * cambiara, mejor un 409 que una FK rota.
     */
    @Override
    @Transactional
    public void descartarBorrador(ContextoUsuario quien, Long versionBancoId) {
        VersionBanco version = laVersionVisible(quien, versionBancoId);
        exigirBorrador(version, "descartar la versión");
        int apuntando = evaluaciones.findByVersionBancoNivelIdAndIniciadaEnIsNull(versionBancoId).size();
        if (apuntando > 0) {
            throw new IllegalStateException("No se descarta: hay " + apuntando
                    + " evaluación(es) apuntando a esta versión");
        }

        List<Long> idsPreguntas = preguntas.findByVersionBancoIdOrderByOrden(versionBancoId)
                .stream().map(Pregunta::getId).toList();
        pares.deleteByVersionBancoId(versionBancoId);
        if (!idsPreguntas.isEmpty()) {
            opciones.deleteByPreguntaIdIn(idsPreguntas);
            rangos.deleteByPreguntaIdIn(idsPreguntas);
            camposCaso.deleteByPreguntaIdIn(idsPreguntas);
            preguntaDimensiones.deleteByPreguntaIdIn(idsPreguntas);
        }
        preguntas.deleteByVersionBancoId(versionBancoId);
        versiones.delete(version);

        auditoria.registrar(quien.organizacionId(), quien, "descartar_borrador_banco",
                "version_banco", versionBancoId,
                Map.of("etiqueta", version.getEtiqueta(), "preguntas", idsPreguntas.size()),
                null, null);
    }

    // ---------- La corrección editorial de una publicada ----------
    // Una errata en un enunciado que el candidato está leyendo se corrige hoy, no en la
    // versión siguiente. Lo que no se toca por aquí es la clave —ni el valor de un EF-4,
    // ni el puntaje de un SJT-R, ni el peso, ni el tipo, ni cuántos campos pide un CD—
    // porque eso cambiaría la nota bajo un examen en curso (RF-138). Esa frontera no la
    // vigila un if: la dibujan los DTOs Corregir*, que no llevan esos campos.

    @Override
    @Transactional
    public void corregirTextoPregunta(ContextoUsuario quien, Long preguntaId,
                                      CorregirTextoPregunta datos) {
        Pregunta pregunta = laPreguntaVisible(quien, preguntaId);
        exigirPublicada(laVersionVisible(quien, pregunta.getVersionBancoId()));
        Map<String, Object> antes = Map.of("enunciado", pregunta.getEnunciado());
        if (datos.enunciado() != null) {
            pregunta.setEnunciado(datos.enunciado());
        }
        if (datos.situacion() != null) {
            pregunta.setSituacion(datos.situacion());
        }
        if (datos.logicaInterna() != null) {
            pregunta.setLogicaInterna(datos.logicaInterna());
        }
        preguntas.save(pregunta);
        auditoria.registrar(quien.organizacionId(), quien, "corregir_texto_pregunta",
                "pregunta", preguntaId, antes,
                Map.of("enunciado", pregunta.getEnunciado()), null);
    }

    @Override
    @Transactional
    public void corregirTextoOpcion(ContextoUsuario quien, Long opcionId,
                                    CorregirTextoOpcion datos) {
        Opcion opcion = opciones.findById(opcionId)
                .orElseThrow(() -> new ResourceNotFoundException("Opción", "id", opcionId));
        Pregunta pregunta = laPreguntaVisible(quien, opcion.getPreguntaId());
        exigirPublicada(laVersionVisible(quien, pregunta.getVersionBancoId()));
        Map<String, Object> antes = Map.of("texto", opcion.getTexto());
        opcion.setTexto(datos.texto());
        opciones.save(opcion);
        auditoria.registrar(quien.organizacionId(), quien, "corregir_texto_opcion",
                "opcion", opcionId, antes, Map.of("texto", datos.texto()), null);
    }

    @Override
    @Transactional
    public void corregirTextoCampoCaso(ContextoUsuario quien, Long campoId,
                                       CorregirTextoCampoCaso datos) {
        CampoCaso campo = camposCaso.findById(campoId)
                .orElseThrow(() -> new ResourceNotFoundException("Campo de caso", "id", campoId));
        Pregunta pregunta = laPreguntaVisible(quien, campo.getPreguntaId());
        exigirPublicada(laVersionVisible(quien, pregunta.getVersionBancoId()));
        Map<String, Object> antes = Map.of("etiqueta", campo.getEtiqueta());
        if (datos.etiqueta() != null) {
            campo.setEtiqueta(datos.etiqueta());
        }
        if (datos.validacion() != null) {
            campo.setValidacion(datos.validacion());
        }
        camposCaso.save(campo);
        auditoria.registrar(quien.organizacionId(), quien, "corregir_texto_campo_caso",
                "campo_caso", campoId, antes,
                Map.of("etiqueta", campo.getEtiqueta()), null);
    }

    @Override
    @Transactional
    public void corregirTextoRango(ContextoUsuario quien, Long rangoId,
                                   CorregirTextoRango datos) {
        RangoPregunta rango = rangos.findById(rangoId)
                .orElseThrow(() -> new ResourceNotFoundException("Rango", "id", rangoId));
        Pregunta pregunta = laPreguntaVisible(quien, rango.getPreguntaId());
        exigirPublicada(laVersionVisible(quien, pregunta.getVersionBancoId()));
        Map<String, Object> antes = Map.of("condicion", rango.getCondicion());
        rango.setCondicion(datos.condicion());
        rangos.save(rango);
        auditoria.registrar(quien.organizacionId(), quien, "corregir_texto_rango",
                "rango_pregunta", rangoId, antes,
                Map.of("condicion", datos.condicion()), null);
    }

    @Override
    @Transactional
    public void corregirTextoParConsistencia(ContextoUsuario quien, Long parId,
                                             CorregirTextoPar datos) {
        ParConsistencia par = pares.findById(parId)
                .orElseThrow(() -> new ResourceNotFoundException("Par de consistencia", "id", parId));
        exigirPublicada(laVersionVisible(quien, par.getVersionBancoId()));
        Map<String, Object> antes = Map.of("condicion",
                par.getCondicion() == null ? "" : par.getCondicion());
        par.setCondicion(datos.condicion());
        pares.save(par);
        auditoria.registrar(quien.organizacionId(), quien, "corregir_texto_par_consistencia",
                "par_consistencia", parId, antes,
                Map.of("condicion", datos.condicion()), null);
    }

    @Override
    @Transactional
    public void corregirEtiquetaVersion(ContextoUsuario quien, Long versionBancoId,
                                        CorregirEtiquetaVersion datos) {
        VersionBanco version = laVersionVisible(quien, versionBancoId);
        exigirPublicada(version);
        Map<String, Object> antes = Map.of("etiqueta", version.getEtiqueta());
        version.setEtiqueta(datos.etiqueta());
        versiones.save(version);
        auditoria.registrar(quien.organizacionId(), quien, "corregir_etiqueta_version",
                "version_banco", versionBancoId, antes,
                Map.of("etiqueta", datos.etiqueta()), null);
    }

    // ---------- La coherencia que publicar exige ----------

    /**
     * Un banco publicado se le pone delante a un candidato tal cual está: esta es la última
     * aduana. Cada formato necesita su clave completa — sin ella el motor no revienta, hace
     * algo peor: salta el ítem en silencio y la nota sale con menos preguntas de las que el
     * candidato respondió.
     */
    private void validarCoherencia(VersionBanco version) {
        List<Pregunta> todas = preguntas.findByVersionBancoIdOrderByOrden(version.getId());
        if (todas.isEmpty()) {
            throw new IllegalStateException("No se publica un banco vacío: esta versión no tiene ninguna pregunta");
        }
        Map<Long, List<Opcion>> opcionesPorPregunta = opciones
                .findByPreguntaIdIn(todas.stream().map(Pregunta::getId).toList()).stream()
                .collect(Collectors.groupingBy(Opcion::getPreguntaId));

        for (Pregunta p : todas) {
            List<Opcion> suyas = opcionesPorPregunta.getOrDefault(p.getId(), List.of());
            boolean tienePeso = p.getPeso() != null && p.getPeso() > 0;

            if (p.isEsPuntuable() && !tienePeso) {
                throw new IllegalStateException(falla(p, "puntúa pero no tiene peso; el motor la saltaría en silencio"));
            }
            if (!p.isEsPuntuable() && tienePeso) {
                throw new IllegalStateException(falla(p, "tiene peso pero está marcada como no puntuable"));
            }
            if (TIPOS_V01.contains(p.getTipo()) && p.isEsPuntuable()) {
                throw new IllegalStateException(falla(p,
                        "usa el formato " + p.getTipo() + " del banco v0.1, que el motor actual no sabe puntuar"));
            }

            switch (p.getTipo()) {
                case "EF-4" -> {
                    if (suyas.size() < 2 || suyas.stream().anyMatch(o -> o.getValor() == null)) {
                        throw new IllegalStateException(falla(p,
                                "una elección forzada necesita al menos 2 opciones, todas con su valor oculto"));
                    }
                }
                case "SJT-R" -> {
                    boolean clavesCompletas = suyas.size() >= 2 && suyas.stream().allMatch(o ->
                            o.getPuntaje() != null
                                    && o.getPuntaje().compareTo(BigDecimal.ONE) >= 0
                                    && o.getPuntaje().compareTo(BigDecimal.valueOf(5)) <= 0);
                    if (!clavesCompletas) {
                        throw new IllegalStateException(falla(p,
                                "un situacional necesita al menos 2 opciones, todas con su calificación esperada de 1 a 5"));
                    }
                }
                case "SEC" -> {
                    Set<Integer> lugares = suyas.stream()
                            .map(Opcion::getOrdenCorrecto)
                            .filter(java.util.Objects::nonNull)
                            .map(Short::intValue)
                            .collect(Collectors.toSet());
                    Set<Integer> esperados = java.util.stream.IntStream.rangeClosed(1, suyas.size())
                            .boxed().collect(Collectors.toSet());
                    if (suyas.size() < 2 || !lugares.equals(esperados)) {
                        throw new IllegalStateException(falla(p,
                                "un ordenamiento necesita que sus pasos cubran las posiciones 1.." + suyas.size()
                                        + " sin huecos ni repetidos"));
                    }
                }
                case "INV", "DE" -> {
                    boolean hayReales = suyas.stream().anyMatch(o -> !o.isEsDistractor());
                    boolean hayDistractores = suyas.stream().anyMatch(Opcion::isEsDistractor);
                    if (!hayReales || !hayDistractores) {
                        throw new IllegalStateException(falla(p,
                                "necesita elementos reales y distractores; sin distractores el formato no mide nada"));
                    }
                }
                case "CD" -> {
                    if (p.getCasosPedidos() == null || p.getCasosPedidos() < 1) {
                        throw new IllegalStateException(falla(p,
                                "un caso descompuesto necesita casosPedidos: es el denominador con el que se puntúa"));
                    }
                }
                case "V" -> {
                    boolean puedePuntuarse = rangos.countByPreguntaId(p.getId()) > 0
                            || p.getRangosDePreguntaCodigo() != null
                            || p.getFormulaPuntaje() != null;
                    if (!puedePuntuarse) {
                        throw new IllegalStateException(falla(p,
                                "un dato verificable necesita su tabla de tramos, una referencia a otra o la fórmula escrita"));
                    }
                }
                case "PC" -> {
                    if (p.isEsPuntuable() || tienePeso) {
                        throw new IllegalStateException(falla(p,
                                "un par de consistencia no suma: solo penaliza si se contradice"));
                    }
                }
                default -> { /* formatos v0.1 no puntuables: se admiten tal cual */ }
            }
        }
    }

    private static String falla(Pregunta p, String motivo) {
        return "No se puede publicar: la pregunta " + p.getCodigo() + " " + motivo;
    }

    private void exigirBorrador(VersionBanco version, String accion) {
        if (!"BORRADOR".equals(version.getEstado())) {
            throw new IllegalStateException(
                    "No se puede " + accion + ": la versión está " + version.getEstado()
                            + " y solo un borrador se edita");
        }
    }

    /**
     * La corrección editorial es solo para lo publicado: un borrador se edita entero con
     * los PUT, y una archivada es historia que nadie vuelve a tocar.
     */
    private void exigirPublicada(VersionBanco version) {
        if (!"PUBLICADA".equals(version.getEstado())) {
            throw new IllegalStateException(
                    "La corrección de textos es de una versión publicada; esta está "
                            + version.getEstado()
                            + ("BORRADOR".equals(version.getEstado())
                                    ? ": un borrador se edita entero"
                                    : ": una versión archivada ya no se toca"));
        }
    }

    private Opcion laOpcionEditable(ContextoUsuario quien, Long opcionId, String accion) {
        Opcion opcion = opciones.findById(opcionId)
                .orElseThrow(() -> new ResourceNotFoundException("Opción", "id", opcionId));
        Pregunta pregunta = laPreguntaVisible(quien, opcion.getPreguntaId());
        exigirBorrador(laVersionVisible(quien, pregunta.getVersionBancoId()), accion);
        return opcion;
    }

    private RangoPregunta elRangoEditable(ContextoUsuario quien, Long rangoId, String accion) {
        RangoPregunta rango = rangos.findById(rangoId)
                .orElseThrow(() -> new ResourceNotFoundException("Rango", "id", rangoId));
        Pregunta pregunta = laPreguntaVisible(quien, rango.getPreguntaId());
        exigirBorrador(laVersionVisible(quien, pregunta.getVersionBancoId()), accion);
        return rango;
    }

    private CampoCaso elCampoEditable(ContextoUsuario quien, Long campoId, String accion) {
        CampoCaso campo = camposCaso.findById(campoId)
                .orElseThrow(() -> new ResourceNotFoundException("Campo de caso", "id", campoId));
        Pregunta pregunta = laPreguntaVisible(quien, campo.getPreguntaId());
        exigirBorrador(laVersionVisible(quien, pregunta.getVersionBancoId()), accion);
        return campo;
    }

    private ParConsistencia elParEditable(ContextoUsuario quien, Long parId, String accion) {
        ParConsistencia par = pares.findById(parId)
                .orElseThrow(() -> new ResourceNotFoundException("Par de consistencia", "id", parId));
        exigirBorrador(laVersionVisible(quien, par.getVersionBancoId()), accion);
        return par;
    }

    private void exigirDeLaVersion(Long versionBancoId, Long preguntaId) {
        Pregunta pregunta = preguntas.findById(preguntaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta", "id", preguntaId));
        if (!versionBancoId.equals(pregunta.getVersionBancoId())) {
            throw new IllegalArgumentException(
                    "La pregunta " + pregunta.getCodigo() + " no es de esta versión del banco");
        }
    }

    // organizacionId nulo = biblioteca global: visible para cualquiera. Si tiene
    // organización, tiene que ser la del usuario.
    private VersionBanco laVersionVisible(ContextoUsuario quien, Long id) {
        VersionBanco version = versiones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Versión del banco", "id", id));
        if (version.getOrganizacionId() != null && !version.getOrganizacionId().equals(quien.organizacionId())) {
            throw new ResourceNotFoundException("Versión del banco", "id", id);
        }
        return version;
    }

    private Pregunta laPreguntaVisible(ContextoUsuario quien, Long id) {
        Pregunta pregunta = preguntas.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta", "id", id));
        laVersionVisible(quien, pregunta.getVersionBancoId());
        return pregunta;
    }
}
