package com.renaser.ai.ai_engine.colaborador.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.AltaColaborador;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.CambioDeCampo;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.ContratadoPendiente;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.DatosPersonales;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.DatosSituacion;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.EntradaHistorial;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.FichaColaborador;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.FilaColaborador;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Opcion;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.OpcionConId;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.OpcionesColaborador;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.PaginaColaboradores;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Perfil;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Periodo;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Precarga;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.RegistrarCambio;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.RegistrarCese;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Reingreso;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Reporte;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Situacion;
import com.renaser.ai.ai_engine.colaborador.entity.CeseAnulado;
import com.renaser.ai.ai_engine.colaborador.entity.Colaborador;
import com.renaser.ai.ai_engine.colaborador.entity.ContratadoSinAlta;
import com.renaser.ai.ai_engine.colaborador.entity.PeriodoLaboral;
import com.renaser.ai.ai_engine.colaborador.entity.SituacionDeAntes;
import com.renaser.ai.ai_engine.colaborador.entity.SituacionLaboral;
import com.renaser.ai.ai_engine.colaborador.repository.CeseAnuladoRepository;
import com.renaser.ai.ai_engine.colaborador.repository.ColaboradorRepository;
import com.renaser.ai.ai_engine.colaborador.repository.ContratadoSinAltaRepository;
import com.renaser.ai.ai_engine.colaborador.repository.ListadoDeColaboradoresRepository;
import com.renaser.ai.ai_engine.colaborador.repository.ListadoDeColaboradoresRepository.Actual;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Filtros;
import com.renaser.ai.ai_engine.colaborador.repository.PeriodoLaboralRepository;
import com.renaser.ai.ai_engine.colaborador.repository.SituacionLaboralRepository;
import com.renaser.ai.ai_engine.colaborador.service.CatalogosDePersonas;
import com.renaser.ai.ai_engine.colaborador.service.DocumentoYaRegistradoException;
import com.renaser.ai.ai_engine.colaborador.service.EstadoLaboral;
import com.renaser.ai.ai_engine.colaborador.service.HoyEnLima;
import com.renaser.ai.ai_engine.colaborador.service.JefesSinCirculo;
import com.renaser.ai.ai_engine.colaborador.service.LineaDeSituaciones;
import com.renaser.ai.ai_engine.colaborador.service.ReglasDelColaborador;
import com.renaser.ai.ai_engine.colaborador.service.ServicioColaboradores;
import com.renaser.ai.ai_engine.organizacion.entity.Area;
import com.renaser.ai.ai_engine.organizacion.entity.Sede;
import com.renaser.ai.ai_engine.organizacion.repository.AreaRepository;
import com.renaser.ai.ai_engine.organizacion.repository.SedeRepository;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.Remuneracion;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ServicioColaboradoresImpl implements ServicioColaboradores {

    static final String VER = "ver_colaboradores";
    static final String EDITAR = "editar_colaboradores";
    static final String VER_SUELDOS = "ver_sueldos";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String DOMINIO_DE_LA_CARGA_DE_CV = "@cv-convocatoria.local";
    private static final Set<String> ESTADOS_POR_DEFECTO = Set.of("ACTIVO", "POR_INGRESAR");

    private final ColaboradorRepository colaboradores;
    private final PeriodoLaboralRepository periodos;
    private final SituacionLaboralRepository situaciones;
    private final CeseAnuladoRepository cesesAnulados;
    private final ContratadoSinAltaRepository sinAlta;
    private final ListadoDeColaboradoresRepository listado;
    private final SedeRepository sedes;
    private final AreaRepository areas;
    private final PuestoRepository puestos;
    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final SolicitudTalentoRepository solicitudes;
    private final DatoCvRepository datosCv;
    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final CatalogosDelPerfil catalogosDelPerfil;
    private final ServicioAuditoria auditoria;
    private final HoyEnLima reloj;

    // ============ Leer ============

    @Override
    public PaginaColaboradores listar(ContextoUsuario quien, Filtros filtros, int pagina) {
        int laPagina = Math.max(pagina, 0);
        if (!alcanzaATodos(quien, VER)) {
            return new PaginaColaboradores(List.of(), 0, laPagina, TAMANO_PAGINA, false);
        }
        Filtros conEstado = filtros.estados() == null || filtros.estados().isEmpty()
                ? new Filtros(ESTADOS_POR_DEFECTO, filtros.sedeId(), filtros.areaId(),
                        filtros.cargoId(), filtros.porVencer(), filtros.busqueda())
                : filtros;
        var resultado = listado.pagina(quien.organizacionId(), reloj.hoy(), conEstado, laPagina,
                TAMANO_PAGINA);
        List<FilaColaborador> filas = resultado.filas().stream().map(f -> new FilaColaborador(
                f.id(), nombre(f.apellidoPaterno(), f.apellidoMaterno(), f.nombres()),
                f.tipoDocumento(),
                CatalogosDePersonas.textoDe(CatalogosDePersonas.TIPOS_DOCUMENTO, f.tipoDocumento()),
                f.numeroDocumento(), f.cargo(), f.area(), f.sede(), f.jefeId(), f.jefe(),
                f.fechaIngreso(), f.finContrato(), f.fechaCese(), f.estado())).toList();
        return new PaginaColaboradores(filas, resultado.total(), laPagina, TAMANO_PAGINA,
                colaboradores.existsByOrganizacionId(quien.organizacionId()));
    }

    @Override
    public OpcionesColaborador opciones(ContextoUsuario quien) {
        Long org = quien.organizacionId();
        return new OpcionesColaborador(
                CatalogosDePersonas.opciones(CatalogosDePersonas.TIPOS_DOCUMENTO),
                CatalogosDePersonas.opciones(CatalogosDePersonas.SEXOS),
                CatalogosDePersonas.opciones(CatalogosDePersonas.ESTADOS_CIVILES),
                catalogosDelPerfil.nivelesEducativos().stream()
                        .map(n -> new Opcion(n.codigo(), n.nombre())).toList(),
                sedes.findByOrganizacionIdAndEsActivaTrueOrderByNombre(org).stream()
                        .map(s -> new OpcionConId(s.getId(), s.getNombre())).toList(),
                areas.findByOrganizacionIdAndEsActivaTrueOrderByNombre(org).stream()
                        .map(a -> new OpcionConId(a.getId(), a.getNombre())).toList(),
                puestos.findByOrganizacionIdAndEsActivoTrueOrderByNombre(org).stream()
                        .map(p -> new OpcionConId(p.getId(), p.getNombre())).toList(),
                CatalogosDePersonas.opciones(CatalogosDePersonas.TIPOS_CONTRATO),
                CatalogosDePersonas.CONTRATOS_CON_FIN.stream().sorted().toList(),
                CatalogosDePersonas.INDETERMINADO,
                CatalogosDePersonas.opciones(CatalogosDePersonas.REGIMENES),
                CatalogosDePersonas.opciones(CatalogosDePersonas.MONEDAS),
                CatalogosDePersonas.opciones(CatalogosDePersonas.MOTIVOS_CAMBIO),
                CatalogosDePersonas.opciones(CatalogosDePersonas.MOTIVOS_CESE),
                alcanceTodo(quien, EDITAR), alcanceTodo(quien, VER_SUELDOS));
    }

    @Override
    public List<ContratadoPendiente> pendientes(ContextoUsuario quien) {
        if (!alcanzaATodos(quien, VER)) {
            return List.of();
        }
        return listado.pendientes(quien.organizacionId()).stream()
                .map(p -> new ContratadoPendiente(p.postulacionId(),
                        p.anonimizado() ? "(anonimizado)"
                                : String.join(" ", Stream.of(p.nombre(), p.apellidos())
                                        .filter(Objects::nonNull).toList()).trim(),
                        p.vacanteId(), p.vacante(), p.contratadoEn()))
                .toList();
    }

    @Override
    public Precarga precarga(ContextoUsuario quien, Long postulacionId) {
        exigirTodo(quien, EDITAR);
        Postulacion postulacion = laContratacion(quien, postulacionId);
        Vacante vacante = vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(
                        postulacion.getVacanteId(), quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        Long yaTiene = fichaDeLaContratacion(postulacionId);

        Usuario usuario = usuarios.findById(postulacion.getUsuarioId()).orElse(null);
        Persona persona = usuario == null || usuario.getPersonaId() == null ? null
                : personas.findById(usuario.getPersonaId()).orElse(null);
        boolean borrada = persona == null || persona.getAnonimizadoEn() != null;
        String correo = usuario == null || borrada ? null : ReglasDelColaborador.texto(usuario.getCorreo());
        if (correo != null && correo.toLowerCase(Locale.ROOT).endsWith(DOMINIO_DE_LA_CARGA_DE_CV)) {
            correo = null;
        }
        String celular = borrada ? null : datosCv.findByPostulacionId(postulacionId)
                .map(DatoCv::getTelefono).map(ReglasDelColaborador::texto).orElse(null);

        Long cargo = puestos.findByIdAndOrganizacionId(vacante.getPuestoId(), quien.organizacionId())
                .filter(Puesto::isEsActivo).map(Puesto::getId).orElse(null);
        Long area = vacante.getSolicitudTalentoId() == null ? null
                : solicitudes.findByIdAndOrganizacionId(vacante.getSolicitudTalentoId(),
                                quien.organizacionId())
                        .map(SolicitudTalento::getAreaId)
                        .flatMap(id -> areas.findByIdAndOrganizacionId(id, quien.organizacionId()))
                        .filter(Area::isEsActiva).map(Area::getId).orElse(null);

        boolean sueldos = alcanceTodo(quien, VER_SUELDOS);
        boolean fija = Remuneracion.FIJA.equals(Remuneracion.tipoDe(vacante));
        BigDecimal sueldo = sueldos && fija ? vacante.getRemuneracionMin() : null;
        String moneda = sueldo == null ? null
                : Optional.ofNullable(vacante.getRemuneracionMoneda()).orElse("PEN");

        return new Precarga(postulacionId, vacante.getTitulo(),
                borrada ? null : ReglasDelColaborador.texto(persona.getNombre()),
                borrada ? null : ReglasDelColaborador.texto(persona.getApellidos()),
                correo, celular, cargo, area, sueldo, moneda, yaTiene);
    }

    @Override
    public FichaColaborador ficha(ContextoUsuario quien, Long id) {
        Colaborador c = laFicha(quien, id, VER);
        LocalDate hoy = reloj.hoy();
        boolean sueldos = alcanceTodo(quien, VER_SUELDOS);
        PeriodoLaboral periodo = periodoActual(c.getId());
        List<SituacionLaboral> delPeriodo = situaciones.findByPeriodoIdOrderByVigenteDesdeAscIdAsc(periodo.getId());
        EstadoLaboral estado = EstadoLaboral.de(periodo.getFechaIngreso(), periodo.getFechaCese(), hoy);

        // Sin ver_sueldos, un ajuste de solo sueldo no existe: no se programa a la vista, no se
        // anula y no marca desde cuándo puede regir un cambio nuevo. Si ya rige, la situación
        // vigente enseña la fecha y el motivo del último cambio que sí se ve, y ningún «hasta»
        // termina el día antes de un ajuste.
        Optional<SituacionLaboral> vigente = LineaDeSituaciones.vigente(delPeriodo, hoy)
                .map(s -> sueldos ? s : LineaDeSituaciones.visibleSinSueldo(delPeriodo, s));
        List<SituacionLaboral> programadas = LineaDeSituaciones.programadas(delPeriodo, hoy).stream()
                .filter(s -> sueldos || !LineaDeSituaciones.soloTocaElSueldo(delPeriodo, s))
                .toList();
        Optional<SituacionLaboral> base = sueldos ? LineaDeSituaciones.ultima(delPeriodo)
                : LineaDeSituaciones.ultimaSinSueldo(delPeriodo);

        List<Actual> actuales = listado.actuales(quien.organizacionId(), hoy);
        List<Long> leReportan = actuales.stream()
                .filter(a -> c.getId().equals(a.jefeId()) && !"CESADO".equals(a.estado()))
                .map(Actual::id).toList();
        Nombres nombres = nombres(quien.organizacionId(), delPeriodo, actuales, leReportan);
        Function<SituacionLaboral, Situacion> pintar = s -> situacion(s, nombres, sueldos, sueldos
                ? s.getVigenteHasta() : LineaDeSituaciones.hastaSinSueldo(delPeriodo, s, periodo.getFechaCese()));
        List<Reporte> reportes = leReportan.stream()
                .map(idReporte -> new Reporte(idReporte, nombres.colaborador(idReporte)))
                .sorted(Comparator.comparing(Reporte::nombre))
                .toList();

        Long postulacionDeOrigen = periodo.getPostulacionId() != null ? periodo.getPostulacionId()
                : c.getPostulacionId();
        Long vacanteId = null;
        String vacante = null;
        if (postulacionDeOrigen != null) {
            Optional<Vacante> suVacante = postulaciones
                    .findByIdAndOrganizacionId(postulacionDeOrigen, quien.organizacionId())
                    .flatMap(p -> vacantes.findByIdAndOrganizacionId(p.getVacanteId(), quien.organizacionId()));
            vacante = suVacante.map(Vacante::getTitulo).orElse(null);
            // Una vacante eliminada conserva el texto y pierde el enlace: el enlace llevaría
            // a un 404.
            vacanteId = suVacante.filter(v -> v.getEliminadaEn() == null).map(Vacante::getId).orElse(null);
        }

        boolean editar = alcanceTodo(quien, EDITAR);
        return new FichaColaborador(c.getId(), c.nombreCompleto(), estado.name(), perfil(c),
                vacanteId, vacante,
                vigente.map(pintar).orElse(null),
                periodo(periodo),
                programadas.stream().map(pintar).toList(),
                base.map(pintar).orElse(null),
                reportes, editar, sueldos, editar && periodo.getFechaCese() != null);
    }

    @Override
    public List<EntradaHistorial> historial(ContextoUsuario quien, Long id) {
        Colaborador c = laFicha(quien, id, VER);
        LocalDate hoy = reloj.hoy();
        boolean sueldos = alcanceTodo(quien, VER_SUELDOS);
        List<PeriodoLaboral> todos = periodos.findByColaboradorIdOrderByFechaIngresoAscIdAsc(c.getId());
        List<Long> idsPeriodos = todos.stream().map(PeriodoLaboral::getId).toList();
        List<SituacionLaboral> todas = situaciones.findByPeriodoIdIn(idsPeriodos);
        List<CeseAnulado> anulados = cesesAnulados.findByPeriodoIdIn(idsPeriodos);

        // Lo que las anuladas tenían detrás también se nombra: puede que ninguna fila viva lo diga ya.
        List<SituacionLaboral> conLoDeAntes = Stream.concat(todas.stream(), todas.stream()
                        .map(SituacionLaboral::getAntesAlAnular).filter(Objects::nonNull)
                        .map(SituacionDeAntes::comoSituacion))
                .toList();
        Nombres nombres = nombres(quien.organizacionId(), conLoDeAntes,
                listado.actuales(quien.organizacionId(), hoy), List.of());
        Map<Long, String> quienes = nombresDeUsuarios(Stream.of(
                        todas.stream().flatMap(s -> Stream.of(s.getRegistradoPorUsuarioId(), s.getAnuladaPorUsuarioId())),
                        todos.stream().map(PeriodoLaboral::getCeseRegistradoPorUsuarioId),
                        anulados.stream().flatMap(a -> Stream.of(a.getRegistradoPorUsuarioId(), a.getAnuladoPorUsuarioId())))
                .flatMap(Function.identity()).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<EntradaHistorial> entradas = new ArrayList<>();
        for (int i = 0; i < todos.size(); i++) {
            PeriodoLaboral periodo = todos.get(i);
            List<SituacionLaboral> delPeriodo = todas.stream()
                    .filter(s -> s.getPeriodoId().equals(periodo.getId()))
                    .sorted(LineaDeSituaciones.EN_ORDEN).toList();
            List<SituacionLaboral> vivas = LineaDeSituaciones.vivas(delPeriodo);
            SituacionLaboral apertura = vivas.isEmpty() ? null : vivas.get(0);

            if (apertura != null) {
                String titulo = CatalogosDePersonas.motivoDeLaSituacion(apertura.getTipoMotivo());
                entradas.add(new EntradaHistorial(i == 0 ? "INGRESO" : "REINGRESO",
                        periodo.getFechaIngreso(), titulo,
                        diferencias(null, apertura, nombres, sueldos),
                        titulo, apertura.getDetalleMotivo(),
                        quienes.get(apertura.getRegistradoPorUsuarioId()), apertura.getRegistradoEn(),
                        periodo.getFechaIngreso().isAfter(hoy), false, null, null, null));
            }
            for (SituacionLaboral s : delPeriodo) {
                if (apertura != null && s.getId().equals(apertura.getId())) {
                    continue;
                }
                // Una anulada, contra lo que tenía detrás al anularse: lo que se registre después no
                // puede reescribir lo que cambiaba.
                SituacionLaboral anterior = LineaDeSituaciones.frenteA(delPeriodo, s);
                List<CambioDeCampo> todosLosCambios = diferencias(anterior, s, nombres, true);
                List<CambioDeCampo> cambios = sueldos ? todosLosCambios
                        : todosLosCambios.stream().filter(cc -> !cc.campo().equals(CAMPO_SUELDO)).toList();
                // Sin ver_sueldos, un cambio que solo tocó el sueldo no existe para quien mira.
                if (!sueldos && cambios.isEmpty() && !todosLosCambios.isEmpty()) {
                    continue;
                }
                String motivo = CatalogosDePersonas.motivoDeLaSituacion(s.getTipoMotivo());
                entradas.add(new EntradaHistorial("CAMBIO", s.getVigenteDesde(), "Cambio: " + motivo,
                        cambios, motivo, s.getDetalleMotivo(),
                        quienes.get(s.getRegistradoPorUsuarioId()), s.getRegistradoEn(),
                        !s.estaAnulada() && s.getVigenteDesde().isAfter(hoy), s.estaAnulada(),
                        quienes.get(s.getAnuladaPorUsuarioId()), s.getAnuladaEn(),
                        s.getMotivoAnulacion()));
            }
            if (periodo.getFechaCese() != null) {
                String motivo = CatalogosDePersonas.textoDe(CatalogosDePersonas.MOTIVOS_CESE, periodo.getMotivoCese());
                entradas.add(new EntradaHistorial("CESE", periodo.getFechaCese(), "Cese: " + motivo,
                        List.of(), motivo, periodo.getObservacionCese(),
                        quienes.get(periodo.getCeseRegistradoPorUsuarioId()), periodo.getCeseRegistradoEn(),
                        periodo.getFechaCese().isAfter(hoy), false, null, null, null));
            }
            for (CeseAnulado a : anulados) {
                if (!a.getPeriodoId().equals(periodo.getId())) continue;
                String motivo = CatalogosDePersonas.textoDe(CatalogosDePersonas.MOTIVOS_CESE, a.getMotivoCese());
                entradas.add(new EntradaHistorial("CESE", a.getFechaCese(), "Cese: " + motivo,
                        List.of(), motivo, a.getObservacionCese(),
                        quienes.get(a.getRegistradoPorUsuarioId()), a.getRegistradoEn(),
                        false, true, quienes.get(a.getAnuladoPorUsuarioId()), a.getAnuladoEn(),
                        a.getMotivoAnulacion()));
            }
        }
        entradas.sort(Comparator.comparing(EntradaHistorial::fecha)
                .thenComparing(e -> e.registradoEn() == null ? Instant.EPOCH : e.registradoEn())
                .reversed());
        return entradas;
    }

    // ============ Contratados pendientes ============

    @Override
    @Transactional
    public void noDarDeAlta(ContextoUsuario quien, Long postulacionId, String motivo) {
        exigirTodo(quien, EDITAR);
        String elMotivo = ReglasDelColaborador.texto(motivo);
        if (elMotivo == null) {
            throw new IllegalArgumentException("Escribe por qué no se da de alta");
        }
        Postulacion postulacion = laContratacion(quien, postulacionId);
        vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(postulacion.getVacanteId(), quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        if (fichaDeLaContratacion(postulacionId) != null) {
            throw new IllegalStateException("Esta contratación ya tiene su ficha de colaborador");
        }
        if (sinAlta.existsByPostulacionId(postulacionId)) {
            throw new IllegalStateException("Esta contratación ya se sacó del aviso");
        }
        ContratadoSinAlta fila = sinAlta.save(ContratadoSinAlta.builder()
                .organizacionId(quien.organizacionId()).postulacionId(postulacionId)
                .motivo(elMotivo).registradoPorUsuarioId(quien.usuarioId())
                .registradoEn(Instant.now()).build());
        auditoria.registrar(quien.organizacionId(), quien, "no_dar_de_alta_contratado",
                "postulacion", postulacionId, null, Map.of("contratadoSinAltaId", fila.getId()), elMotivo);
    }

    // ============ El alta ============

    @Override
    @Transactional
    public Long darDeAlta(ContextoUsuario quien, AltaColaborador datos) {
        exigirTodo(quien, EDITAR);
        LocalDate hoy = reloj.hoy();
        PersonaLimpia persona = validarPersona(datos.persona(), hoy);

        Long postulacionId = datos.postulacionId();
        if (postulacionId != null) {
            colaboradores.esperarTurno(("contratacion:" + postulacionId).hashCode());
            validarContratacionLibre(quien, postulacionId);
        }
        colaboradores.esperarTurno(claveDelDocumento(quien.organizacionId(), persona.tipo(), persona.numero()));
        exigirDocumentoLibre(quien.organizacionId(), persona.tipo(), persona.numero(), null, hoy);

        boolean sueldos = alcanceTodo(quien, VER_SUELDOS);
        SituacionLimpia situacion = validarSituacion(quien, datos.situacion(), datos.fechaIngreso(),
                null, null, sueldos, null, hoy);

        Instant ahora = Instant.now();
        Colaborador c = persona.aplicarA(Colaborador.builder()
                .organizacionId(quien.organizacionId())
                .postulacionId(postulacionId)
                .creadoPorUsuarioId(quien.usuarioId())
                .creadoEn(ahora)
                .build());
        c.setActualizadoEn(ahora);
        c = colaboradores.saveAndFlush(c);

        PeriodoLaboral periodo = periodos.save(PeriodoLaboral.builder()
                .colaboradorId(c.getId()).fechaIngreso(datos.fechaIngreso())
                .postulacionId(postulacionId).creadoPorUsuarioId(quien.usuarioId())
                .creadoEn(ahora).build());
        situaciones.save(situacion.nueva(c.getId(), periodo.getId(), datos.fechaIngreso(),
                "INGRESO", null, quien.usuarioId(), ahora));

        auditoria.registrar(quien.organizacionId(), quien, "alta_colaborador", "colaborador",
                c.getId(), null, resumenDelAlta(c, datos.fechaIngreso(), postulacionId), null);
        return c.getId();
    }

    // ============ El perfil ============

    @Override
    @Transactional
    public void editarPerfil(ContextoUsuario quien, Long id, DatosPersonales datos) {
        Colaborador c = laFichaParaEscribir(quien, id);
        LocalDate hoy = reloj.hoy();
        PersonaLimpia persona = validarPersona(datos, hoy);
        if (!persona.tipo().equals(c.getTipoDocumento()) || !persona.numero().equals(c.getNumeroDocumento())) {
            colaboradores.esperarTurno(claveDelDocumento(c.getOrganizacionId(), persona.tipo(), persona.numero()));
            exigirDocumentoLibre(c.getOrganizacionId(), persona.tipo(), persona.numero(), c.getId(), hoy);
        }
        Map<String, Object> antes = perfilComoMapa(c);
        persona.aplicarA(c);
        Map<String, Object> despues = perfilComoMapa(c);

        Map<String, Object> anterior = new LinkedHashMap<>();
        Map<String, Object> nuevo = new LinkedHashMap<>();
        for (String campo : antes.keySet()) {
            if (!Objects.equals(antes.get(campo), despues.get(campo))) {
                anterior.put(campo, antes.get(campo));
                nuevo.put(campo, despues.get(campo));
            }
        }
        if (nuevo.isEmpty()) {
            return;
        }
        c.setActualizadoEn(Instant.now());
        colaboradores.save(c);
        auditoria.registrar(c.getOrganizacionId(), quien, "editar_perfil_colaborador", "colaborador",
                c.getId(), anterior, nuevo, null);
    }

    // ============ Los cambios ============

    @Override
    @Transactional
    public Long registrarCambio(ContextoUsuario quien, Long id, RegistrarCambio datos) {
        Colaborador c = laFichaParaEscribir(quien, id);
        LocalDate hoy = reloj.hoy();
        PeriodoLaboral periodo = periodoActual(c.getId());
        if (EstadoLaboral.de(periodo.getFechaIngreso(), periodo.getFechaCese(), hoy) == EstadoLaboral.CESADO) {
            throw new IllegalStateException("Está cesado: para registrar cambios, primero reingrésalo");
        }
        String tipoMotivo = datos.tipoMotivo();
        if (!CatalogosDePersonas.MOTIVOS_CAMBIO.containsKey(tipoMotivo)) {
            throw new IllegalArgumentException("El tipo de motivo no es válido");
        }
        String detalle = ReglasDelColaborador.texto(datos.detalle());
        if ("OTRO".equals(tipoMotivo) && detalle == null) {
            throw new IllegalArgumentException("Con el motivo «otro» hay que escribir el detalle");
        }

        List<SituacionLaboral> delPeriodo = situaciones.findByPeriodoIdOrderByVigenteDesdeAscIdAsc(periodo.getId());
        boolean sueldos = alcanceTodo(quien, VER_SUELDOS);
        // Sin ver_sueldos, un ajuste de solo sueldo no existe para quien registra, rija ya o no:
        // no lo ve ni lo puede anular, así que tampoco le cierra las fechas anteriores.
        SituacionLaboral ultima = (sueldos ? LineaDeSituaciones.ultima(delPeriodo)
                        : LineaDeSituaciones.ultimaSinSueldo(delPeriodo))
                .orElseThrow(() -> new IllegalStateException("Este periodo no tiene situación laboral"));
        LocalDate desde = datos.vigenteDesde();
        if (desde.isBefore(periodo.getFechaIngreso())) {
            throw new IllegalArgumentException("El cambio no puede regir antes de la fecha de ingreso ("
                    + FECHA.format(periodo.getFechaIngreso()) + ")");
        }
        if (desde.isBefore(ultima.getVigenteDesde())) {
            throw new IllegalArgumentException("El cambio no puede regir antes del último cambio del "
                    + "periodo, que rige desde el " + FECHA.format(ultima.getVigenteDesde())
                    + ". Si hace falta, anula antes el cambio programado");
        }
        if (periodo.getFechaCese() != null && desde.isAfter(periodo.getFechaCese())) {
            throw new IllegalArgumentException("El cambio no puede regir después del cese ("
                    + FECHA.format(periodo.getFechaCese()) + ")");
        }

        // De la que parte: la que rige ese día. Con ver_sueldos siempre es la última; sin él puede
        // ser un ajuste de sueldo que no ve, y entonces el sueldo nuevo sigue llegando en su fecha.
        SituacionLaboral previa = sueldos ? ultima
                : LineaDeSituaciones.deLaQueParte(delPeriodo, desde).orElse(ultima);
        SituacionLimpia nueva = validarSituacion(quien, datos.situacion(), periodo.getFechaIngreso(),
                c.getId(), previa, sueldos, null, hoy);
        List<String> campos = nueva.camposDistintosDe(previa);
        if (campos.isEmpty()) {
            throw new IllegalArgumentException("El cambio tiene que cambiar al menos un dato");
        }

        Instant ahora = Instant.now();
        SituacionLaboral guardada = situaciones.saveAndFlush(nueva.nueva(c.getId(), periodo.getId(), desde,
                tipoMotivo, detalle, quien.usuarioId(), ahora));
        // Lo que queda después solo pueden ser ajustes de solo sueldo que quien registra no ve (lo
        // demás le cierra la fecha arriba). Llevan su cambio también: si no, al llegar su día lo
        // desharían, y seguirían tocando solo el sueldo.
        List<SituacionLaboral> arrastradas = sueldos ? List.of() : LineaDeSituaciones.vivas(delPeriodo).stream()
                .filter(s -> s.getVigenteDesde().isAfter(desde)).toList();
        arrastradas.forEach(s -> LineaDeSituaciones.copiarPuesto(guardada, s));
        List<SituacionLaboral> conLaNueva = new ArrayList<>(delPeriodo);
        conLaNueva.add(guardada);
        situaciones.saveAll(LineaDeSituaciones.recalcular(conLaNueva, periodo.getFechaCese()));
        situaciones.saveAll(arrastradas);

        // En la auditoría van los nombres de los campos, nunca el sueldo: la auditoría la lee
        // quien tiene ver_auditoria, que no es lo mismo que ver_sueldos.
        Map<String, Object> despues = new LinkedHashMap<>(Map.of("situacionId", guardada.getId(),
                "vigenteDesde", desde.toString(), "tipoMotivo", tipoMotivo, "campos", campos));
        if (!arrastradas.isEmpty()) {
            despues.put("situacionesQueLoHeredan", arrastradas.stream().map(SituacionLaboral::getId).toList());
        }
        auditoria.registrar(c.getOrganizacionId(), quien, "registrar_cambio_colaborador", "colaborador",
                c.getId(), null, despues, detalle);
        return guardada.getId();
    }

    @Override
    @Transactional
    public void anularCambio(ContextoUsuario quien, Long id, Long situacionId, String motivo) {
        Colaborador c = laFichaParaEscribir(quien, id);
        String elMotivo = exigirMotivo(motivo);
        LocalDate hoy = reloj.hoy();
        List<SituacionLaboral> deLaFicha = situaciones.findByColaboradorIdOrderByVigenteDesdeAscIdAsc(c.getId());
        SituacionLaboral s = deLaFicha.stream()
                .filter(x -> x.getId().equals(situacionId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cambio", "id", situacionId));
        List<SituacionLaboral> delPeriodo = deLaFicha.stream()
                .filter(x -> x.getPeriodoId().equals(s.getPeriodoId())).toList();
        // Sin ver_sueldos, un ajuste de solo sueldo no existe: anularlo sería editar el sueldo.
        // Se contesta igual que a un cambio que no está, sin decir si lo hay.
        if (!alcanceTodo(quien, VER_SUELDOS) && LineaDeSituaciones.soloTocaElSueldo(delPeriodo, s)) {
            throw new ResourceNotFoundException("Cambio", "id", situacionId);
        }
        if (s.estaAnulada()) {
            throw new IllegalStateException("Ese cambio ya está anulado");
        }
        if (CatalogosDePersonas.MOTIVOS_DE_APERTURA.containsKey(s.getTipoMotivo())) {
            throw new IllegalStateException("La situación con la que se entra no se anula: se corrige con "
                    + "un cambio de tipo «corrección»");
        }
        if (!s.getVigenteDesde().isAfter(hoy)) {
            throw new IllegalStateException("Un cambio ya vigente no se anula: se corrige con otro cambio "
                    + "de tipo «corrección»");
        }
        PeriodoLaboral periodo = periodos.findById(s.getPeriodoId()).orElseThrow();
        // Los ajustes de solo sueldo que venían justo detrás partían de este cambio: sin él,
        // vuelven a partir de la anterior, para que al llegar su día no lo traigan de vuelta.
        List<SituacionLaboral> vivas = LineaDeSituaciones.vivas(delPeriodo);
        SituacionLaboral anterior = LineaDeSituaciones.anteriorViva(delPeriodo, s);
        List<SituacionLaboral> arrastradas = new ArrayList<>();
        SituacionLaboral eslabon = s;
        for (SituacionLaboral siguiente : vivas.subList(vivas.indexOf(s) + 1, vivas.size())) {
            if (anterior == null || !LineaDeSituaciones.soloCambiaElSueldo(eslabon, siguiente)) {
                break;
            }
            arrastradas.add(siguiente);
            eslabon = siguiente;
        }
        arrastradas.forEach(x -> LineaDeSituaciones.copiarPuesto(anterior, x));
        LineaDeSituaciones.guardarLoDeAntes(delPeriodo, List.of(s));
        s.setAnuladaEn(Instant.now());
        s.setAnuladaPorUsuarioId(quien.usuarioId());
        s.setMotivoAnulacion(elMotivo);
        situaciones.saveAndFlush(s);
        situaciones.saveAll(arrastradas);
        situaciones.saveAll(LineaDeSituaciones.recalcular(
                situaciones.findByPeriodoIdOrderByVigenteDesdeAscIdAsc(periodo.getId()), periodo.getFechaCese()));
        Map<String, Object> despues = new LinkedHashMap<>(Map.of("situacionId", s.getId(),
                "vigenteDesde", s.getVigenteDesde().toString()));
        if (!arrastradas.isEmpty()) {
            despues.put("situacionesQueLoPierden", arrastradas.stream().map(SituacionLaboral::getId).toList());
        }
        auditoria.registrar(c.getOrganizacionId(), quien, "anular_cambio_colaborador", "colaborador",
                c.getId(), null, despues, elMotivo);
    }

    // ============ El cese ============

    @Override
    @Transactional
    public void registrarCese(ContextoUsuario quien, Long id, RegistrarCese datos) {
        Colaborador c = laFichaParaEscribir(quien, id);
        PeriodoLaboral periodo = periodoActual(c.getId());
        if (periodo.getFechaCese() != null) {
            throw new IllegalStateException("Ya tiene un cese registrado el " + FECHA.format(periodo.getFechaCese()));
        }
        if (!CatalogosDePersonas.MOTIVOS_CESE.containsKey(datos.motivoCodigo())) {
            throw new IllegalArgumentException("El motivo del cese no es válido");
        }
        LocalDate fecha = datos.fechaCese();
        if (fecha.isBefore(periodo.getFechaIngreso())) {
            throw new IllegalArgumentException("La fecha de cese no puede ser anterior a la de ingreso ("
                    + FECHA.format(periodo.getFechaIngreso()) + ")");
        }
        Instant ahora = Instant.now();
        List<SituacionLaboral> delPeriodo = situaciones.findByPeriodoIdOrderByVigenteDesdeAscIdAsc(periodo.getId());
        List<SituacionLaboral> porAnular = delPeriodo.stream()
                .filter(s -> !s.estaAnulada() && s.getVigenteDesde().isAfter(fecha)).toList();
        LineaDeSituaciones.guardarLoDeAntes(delPeriodo, porAnular);
        List<Long> anuladas = new ArrayList<>();
        for (SituacionLaboral s : porAnular) {
            s.setAnuladaEn(ahora);
            s.setAnuladaPorUsuarioId(quien.usuarioId());
            s.setMotivoAnulacion("Se anuló al registrar el cese del " + FECHA.format(fecha));
            anuladas.add(s.getId());
        }
        situaciones.saveAllAndFlush(delPeriodo);
        periodo.setFechaCese(fecha);
        periodo.setMotivoCese(datos.motivoCodigo());
        periodo.setObservacionCese(ReglasDelColaborador.texto(datos.observacion()));
        periodo.setCeseRegistradoPorUsuarioId(quien.usuarioId());
        periodo.setCeseRegistradoEn(ahora);
        periodos.save(periodo);
        situaciones.saveAll(LineaDeSituaciones.recalcular(delPeriodo, fecha));

        auditoria.registrar(c.getOrganizacionId(), quien, "registrar_cese_colaborador", "colaborador",
                c.getId(), null, Map.of("periodoId", periodo.getId(), "fechaCese", fecha.toString(),
                        "motivoCese", datos.motivoCodigo(), "cambiosAnulados", anuladas),
                periodo.getObservacionCese());
    }

    @Override
    @Transactional
    public void anularCese(ContextoUsuario quien, Long id, String motivo) {
        Colaborador c = laFichaParaEscribir(quien, id);
        String elMotivo = exigirMotivo(motivo);
        // Solo el periodo actual: si hubo un reingreso, el cese anterior ya no se puede
        // deshacer, porque dejaría dos periodos abiertos a la vez.
        PeriodoLaboral periodo = periodoActual(c.getId());
        if (periodo.getFechaCese() == null) {
            throw new IllegalStateException("No tiene un cese que anular");
        }
        cesesAnulados.save(CeseAnulado.builder()
                .periodoId(periodo.getId()).fechaCese(periodo.getFechaCese())
                .motivoCese(periodo.getMotivoCese()).observacionCese(periodo.getObservacionCese())
                .registradoPorUsuarioId(periodo.getCeseRegistradoPorUsuarioId())
                .registradoEn(periodo.getCeseRegistradoEn())
                .anuladoPorUsuarioId(quien.usuarioId()).anuladoEn(Instant.now())
                .motivoAnulacion(elMotivo).build());
        LocalDate fechaAnterior = periodo.getFechaCese();
        periodo.setFechaCese(null);
        periodo.setMotivoCese(null);
        periodo.setObservacionCese(null);
        periodo.setCeseRegistradoPorUsuarioId(null);
        periodo.setCeseRegistradoEn(null);
        periodos.save(periodo);
        situaciones.saveAll(LineaDeSituaciones.recalcular(
                situaciones.findByPeriodoIdOrderByVigenteDesdeAscIdAsc(periodo.getId()), null));
        auditoria.registrar(c.getOrganizacionId(), quien, "anular_cese_colaborador", "colaborador",
                c.getId(), Map.of("fechaCese", fechaAnterior.toString()), Map.of("periodoId", periodo.getId()),
                elMotivo);
    }

    // ============ El reingreso ============

    @Override
    @Transactional
    public void reingresar(ContextoUsuario quien, Long id, Reingreso datos) {
        Colaborador c = laFichaParaEscribir(quien, id);
        LocalDate hoy = reloj.hoy();
        PeriodoLaboral anterior = periodoActual(c.getId());
        if (EstadoLaboral.de(anterior.getFechaIngreso(), anterior.getFechaCese(), hoy) != EstadoLaboral.CESADO) {
            throw new IllegalStateException("Solo se reingresa a quien está cesado");
        }
        if (!datos.fechaIngreso().isAfter(anterior.getFechaCese())) {
            throw new IllegalArgumentException("La fecha de reingreso tiene que ser posterior al último cese ("
                    + FECHA.format(anterior.getFechaCese()) + ")");
        }
        Long postulacionId = datos.postulacionId();
        if (postulacionId != null) {
            colaboradores.esperarTurno(("contratacion:" + postulacionId).hashCode());
            validarContratacionLibre(quien, postulacionId);
        }
        SituacionLaboral ultima = LineaDeSituaciones.ultima(
                        situaciones.findByPeriodoIdOrderByVigenteDesdeAscIdAsc(anterior.getId()))
                .orElse(null);
        boolean sueldos = alcanceTodo(quien, VER_SUELDOS);
        SituacionLimpia situacion = validarSituacion(quien, datos.situacion(), datos.fechaIngreso(),
                c.getId(), null, sueldos, ultima, hoy);

        Instant ahora = Instant.now();
        PeriodoLaboral nuevo = periodos.save(PeriodoLaboral.builder()
                .colaboradorId(c.getId()).fechaIngreso(datos.fechaIngreso())
                .postulacionId(postulacionId).creadoPorUsuarioId(quien.usuarioId())
                .creadoEn(ahora).build());
        situaciones.save(situacion.nueva(c.getId(), nuevo.getId(), datos.fechaIngreso(), "REINGRESO",
                null, quien.usuarioId(), ahora));
        auditoria.registrar(c.getOrganizacionId(), quien, "reingresar_colaborador", "colaborador",
                c.getId(), null, Map.of("periodoId", nuevo.getId(), "fechaIngreso", datos.fechaIngreso().toString(),
                        "postulacionId", postulacionId == null ? "" : postulacionId.toString()), null);
    }

    // ============ Validar ============

    /** Lo personal, limpio y comprobado. */
    record PersonaLimpia(String tipo, String numero, String nombres, String paterno, String materno,
                         LocalDate nacimiento, String sexo, String estadoCivil, String nacionalidad,
                         String celular, String correoPersonal, String correoCorporativo,
                         String direccion, String provincia, String nivelEducativo) {

        Colaborador aplicarA(Colaborador c) {
            c.setTipoDocumento(tipo);
            c.setNumeroDocumento(numero);
            c.setNombres(nombres);
            c.setApellidoPaterno(paterno);
            c.setApellidoMaterno(materno);
            c.setFechaNacimiento(nacimiento);
            c.setSexo(sexo);
            c.setEstadoCivil(estadoCivil);
            c.setNacionalidad(nacionalidad);
            c.setCelular(celular);
            c.setCorreoPersonal(correoPersonal);
            c.setCorreoCorporativo(correoCorporativo);
            c.setDireccion(direccion);
            c.setProvinciaUbigeo(provincia);
            c.setNivelEducativoCodigo(nivelEducativo);
            return c;
        }
    }

    private PersonaLimpia validarPersona(DatosPersonales d, LocalDate hoy) {
        String tipo = d.tipoDocumento();
        String numero = ReglasDelColaborador.limpiarDocumento(d.numeroDocumento());
        exigir(ReglasDelColaborador.errorDelDocumento(tipo, numero));
        String nombres = ReglasDelColaborador.texto(d.nombres());
        String paterno = ReglasDelColaborador.texto(d.apellidoPaterno());
        if (nombres == null) throw new IllegalArgumentException("Faltan los nombres");
        if (paterno == null) throw new IllegalArgumentException("Falta el apellido paterno");
        exigir(ReglasDelColaborador.errorDelNacimiento(d.fechaNacimiento(), hoy));
        if (!CatalogosDePersonas.SEXOS.containsKey(d.sexo())) {
            throw new IllegalArgumentException("El sexo no es válido");
        }
        String estadoCivil = ReglasDelColaborador.texto(d.estadoCivil());
        if (estadoCivil != null && !CatalogosDePersonas.ESTADOS_CIVILES.containsKey(estadoCivil)) {
            throw new IllegalArgumentException("El estado civil no es válido");
        }
        String correoPersonal = ReglasDelColaborador.texto(d.correoPersonal());
        String correoCorporativo = ReglasDelColaborador.texto(d.correoCorporativo());
        exigir(ReglasDelColaborador.errorDelCorreo(correoPersonal));
        exigir(ReglasDelColaborador.errorDelCorreo(correoCorporativo));
        String provincia = ReglasDelColaborador.texto(d.provinciaUbigeo());
        if (provincia != null && !catalogosDelPerfil.esCiudadElegible(provincia)) {
            throw new IllegalArgumentException("Esa provincia no está en el catálogo");
        }
        String nivel = ReglasDelColaborador.texto(d.nivelEducativoCodigo());
        if (nivel != null && catalogosDelPerfil.nivelesEducativos().stream()
                .noneMatch(n -> n.codigo().equals(nivel))) {
            throw new IllegalArgumentException("El nivel educativo no es válido");
        }
        return new PersonaLimpia(tipo, numero, nombres, paterno,
                ReglasDelColaborador.texto(d.apellidoMaterno()), d.fechaNacimiento(), d.sexo(),
                estadoCivil, ReglasDelColaborador.texto(d.nacionalidad()),
                ReglasDelColaborador.texto(d.celular()), correoPersonal, correoCorporativo,
                ReglasDelColaborador.texto(d.direccion()), provincia, nivel);
    }

    /** Dónde y en qué condiciones, comprobado contra la empresa. */
    record SituacionLimpia(Long sedeId, Long areaId, Long cargoId, Long jefeId, String tipoContrato,
                           LocalDate finContrato, LocalDate finPeriodoPrueba, String regimen,
                           BigDecimal sueldo, String moneda) {

        SituacionLaboral nueva(Long colaboradorId, Long periodoId, LocalDate desde, String tipoMotivo,
                               String detalle, Long usuarioId, Instant ahora) {
            return SituacionLaboral.builder()
                    .colaboradorId(colaboradorId).periodoId(periodoId).vigenteDesde(desde)
                    .sedeId(sedeId).areaId(areaId).puestoId(cargoId).jefeColaboradorId(jefeId)
                    .tipoContrato(tipoContrato).finContrato(finContrato)
                    .finPeriodoPrueba(finPeriodoPrueba).regimenLaboral(regimen)
                    .sueldoBase(sueldo).moneda(moneda).tipoMotivo(tipoMotivo).detalleMotivo(detalle)
                    .registradoPorUsuarioId(usuarioId).registradoEn(ahora).build();
        }

        List<String> camposDistintosDe(SituacionLaboral s) {
            List<String> campos = new ArrayList<>();
            if (!Objects.equals(sedeId, s.getSedeId())) campos.add("sede");
            if (!Objects.equals(areaId, s.getAreaId())) campos.add("area");
            if (!Objects.equals(cargoId, s.getPuestoId())) campos.add("cargo");
            if (!Objects.equals(jefeId, s.getJefeColaboradorId())) campos.add("jefe");
            if (!Objects.equals(tipoContrato, s.getTipoContrato())) campos.add("tipoContrato");
            if (!Objects.equals(finContrato, s.getFinContrato())) campos.add("finContrato");
            if (!Objects.equals(finPeriodoPrueba, s.getFinPeriodoPrueba())) campos.add("finPeriodoPrueba");
            if (!Objects.equals(regimen, s.getRegimenLaboral())) campos.add("regimenLaboral");
            if (!mismoSueldo(sueldo, moneda, s.getSueldoBase(), s.getMoneda())) campos.add("sueldo");
            return campos;
        }
    }

    /**
     * La situación que llega, comprobada.
     *
     * @param colaboradorId quién la recibe, o null en un alta (nadie apunta todavía a él)
     * @param conserva      la situación de la que parte un cambio: lo que no cambia se conserva
     *                      aunque ya esté desactivado (una sede cerrada, un jefe cesado)
     * @param sueldoPrevio  de dónde sale el sueldo cuando quien registra no puede verlo
     */
    private SituacionLimpia validarSituacion(ContextoUsuario quien, DatosSituacion d, LocalDate ingreso,
                                             Long colaboradorId, SituacionLaboral conserva,
                                             boolean sueldos, SituacionLaboral sueldoPrevio, LocalDate hoy) {
        Long org = quien.organizacionId();
        Sede sede = sedes.findByIdAndOrganizacionId(d.sedeId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Esa sede no existe"));
        if (!sede.isEsActiva() && (conserva == null || !sede.getId().equals(conserva.getSedeId()))) {
            throw new IllegalArgumentException("La sede «" + sede.getNombre() + "» está desactivada");
        }
        Area area = areas.findByIdAndOrganizacionId(d.areaId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Esa área no existe"));
        if (!area.isEsActiva() && (conserva == null || !area.getId().equals(conserva.getAreaId()))) {
            throw new IllegalArgumentException("El área «" + area.getNombre() + "» está desactivada");
        }
        Puesto cargo = puestos.findByIdAndOrganizacionId(d.cargoId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Ese cargo no existe"));
        if (!cargo.isEsActivo() && (conserva == null || !cargo.getId().equals(conserva.getPuestoId()))) {
            throw new IllegalArgumentException("El cargo «" + cargo.getNombre() + "» está desactivado");
        }

        Long jefeId = d.jefeId();
        if (jefeId != null) {
            if (jefeId.equals(colaboradorId)) {
                throw new IllegalArgumentException("Nadie puede ser su propio jefe directo");
            }
            boolean seConserva = conserva != null && jefeId.equals(conserva.getJefeColaboradorId());
            if (!seConserva) {
                List<Actual> actuales = listado.actuales(org, hoy);
                Actual jefe = actuales.stream().filter(a -> a.id().equals(jefeId)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Ese jefe no es colaborador de la empresa"));
                if (!EstadoLaboral.valueOf(jefe.estado()).puedeSerJefe()) {
                    throw new IllegalArgumentException("El jefe directo tiene que estar activo o por ingresar");
                }
                if (colaboradorId != null) {
                    Map<Long, Long> jefeDe = new HashMap<>();
                    actuales.forEach(a -> {
                        if (a.jefeId() != null) jefeDe.put(a.id(), a.jefeId());
                    });
                    jefeDe.remove(colaboradorId);
                    if (JefesSinCirculo.creaCirculo(colaboradorId, jefeId, jefeDe)) {
                        throw new IllegalArgumentException("Ese jefe crearía un círculo: "
                                + "esa persona ya depende, directa o indirectamente, de este colaborador");
                    }
                }
            }
        }

        exigir(ReglasDelColaborador.errorDelContrato(d.tipoContrato(), d.finContrato(), ingreso));
        exigir(ReglasDelColaborador.errorDelPeriodoDePrueba(d.finPeriodoPrueba(), ingreso));
        exigir(ReglasDelColaborador.errorDelRegimen(d.regimenLaboral()));

        BigDecimal sueldo;
        String moneda;
        if (sueldos) {
            sueldo = d.sueldoBase();
            moneda = sueldo == null ? null : Optional.ofNullable(ReglasDelColaborador.texto(d.moneda())).orElse("PEN");
            exigir(ReglasDelColaborador.errorDelSueldo(sueldo, moneda));
        } else {
            // Sin ver_sueldos el sueldo ni se ve ni se escribe: pasa igual que estaba.
            SituacionLaboral previo = conserva != null ? conserva : sueldoPrevio;
            sueldo = previo == null ? null : previo.getSueldoBase();
            moneda = previo == null ? null : previo.getMoneda();
        }
        return new SituacionLimpia(sede.getId(), area.getId(), cargo.getId(), jefeId, d.tipoContrato(),
                d.finContrato(), d.finPeriodoPrueba(), d.regimenLaboral(), sueldo, moneda);
    }

    private void exigirDocumentoLibre(Long org, String tipo, String numero, Long excepto, LocalDate hoy) {
        colaboradores.findByOrganizacionIdAndTipoDocumentoAndNumeroDocumento(org, tipo, numero)
                .filter(existente -> !existente.getId().equals(excepto))
                .ifPresent(existente -> {
                    PeriodoLaboral suPeriodo = periodoActual(existente.getId());
                    boolean cesado = EstadoLaboral.de(suPeriodo.getFechaIngreso(), suPeriodo.getFechaCese(), hoy)
                            == EstadoLaboral.CESADO;
                    throw new DocumentoYaRegistradoException(existente.getId(), cesado);
                });
    }

    /** Una contratación de la empresa, en CONTRATADO, de una vacante viva y todavía sin ficha. */
    private void validarContratacionLibre(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laContratacion(quien, postulacionId);
        vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(postulacion.getVacanteId(), quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        if (fichaDeLaContratacion(postulacionId) != null) {
            throw new IllegalStateException("Esta contratación ya tiene su ficha de colaborador");
        }
    }

    private Postulacion laContratacion(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = postulaciones.findByIdAndOrganizacionId(postulacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        if (!"CONTRATADO".equals(postulacion.getEstadoCodigo())) {
            throw new IllegalStateException("Esta postulación no está contratada");
        }
        return postulacion;
    }

    private Long fichaDeLaContratacion(Long postulacionId) {
        return periodos.findFirstByPostulacionId(postulacionId).map(PeriodoLaboral::getColaboradorId).orElse(null);
    }

    // ============ Permisos ============

    /** Si el alcance de ese permiso es TODO. Sin el permiso, 403. */
    private static boolean alcanzaATodos(ContextoUsuario quien, String permiso) {
        String alcance = quien.alcance(permiso);
        if (alcance == null) {
            throw new AccessDeniedException("No tienes el permiso «" + permiso + "»");
        }
        return "TODO".equals(alcance);
    }

    private static boolean alcanceTodo(ContextoUsuario quien, String permiso) {
        return "TODO".equals(quien.alcance(permiso));
    }

    /** Con un alcance que no es TODO no se alcanza a nadie: lo que se pida es un 404. */
    private static void exigirTodo(ContextoUsuario quien, String permiso) {
        if (!alcanzaATodos(quien, permiso)) {
            throw new ResourceNotFoundException("Colaborador", "alcance", permiso);
        }
    }

    private Colaborador laFicha(ContextoUsuario quien, Long id, String permiso) {
        if (!alcanzaATodos(quien, permiso)) {
            throw new ResourceNotFoundException("Colaborador", "id", id);
        }
        return colaboradores.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador", "id", id));
    }

    /**
     * La ficha para escribir en ella, bloqueada hasta el final de la transacción.
     *
     * <p>Sin el bloqueo, dos peticiones a la vez —un doble clic en «Anular el cese»— leían las dos
     * el mismo estado y lo repetían: dos anulaciones en la auditoría y el cese dos veces en el
     * historial. Con él, la segunda lee lo que dejó la primera y la regla de siempre la rechaza.
     */
    private Colaborador laFichaParaEscribir(ContextoUsuario quien, Long id) {
        if (!alcanzaATodos(quien, EDITAR)) {
            throw new ResourceNotFoundException("Colaborador", "id", id);
        }
        return colaboradores.bloquear(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador", "id", id));
    }

    private PeriodoLaboral periodoActual(Long colaboradorId) {
        return periodos.findFirstByColaboradorIdOrderByFechaIngresoDescIdDesc(colaboradorId)
                .orElseThrow(() -> new IllegalStateException("Esta ficha no tiene ningún periodo"));
    }

    // ============ Pintar ============

    static final String CAMPO_SUELDO = "Sueldo base";

    /** Los nombres de todo lo que una tanda de situaciones nombra, en pocas consultas. */
    private record Nombres(Map<Long, Sede> sedes, Map<Long, Area> areas, Map<Long, Puesto> cargos,
                           Map<Long, Colaborador> colaboradores, Map<Long, EstadoLaboral> estados) {

        String colaborador(Long id) {
            Colaborador c = colaboradores.get(id);
            return c == null ? null : c.nombreCompleto();
        }
    }

    /**
     * @param actuales lo actual de toda la empresa: de ahí sale si un jefe está cesado
     * @param otras    a quién más hay que nombrar, además de los jefes de la tanda
     */
    private Nombres nombres(Long org, Collection<SituacionLaboral> tanda, List<Actual> actuales,
                            Collection<Long> otras) {
        Map<Long, Sede> lasSedes = sedes.findByOrganizacionIdOrderByNombre(org).stream()
                .collect(Collectors.toMap(Sede::getId, Function.identity()));
        Map<Long, Area> lasAreas = areas.findByOrganizacionIdOrderByNombre(org).stream()
                .collect(Collectors.toMap(Area::getId, Function.identity()));
        Set<Long> idsCargos = tanda.stream().map(SituacionLaboral::getPuestoId).collect(Collectors.toSet());
        // Los cargos salen de situaciones de esta empresa: ids derivados de filas ya suyas.
        Map<Long, Puesto> losCargos = puestos.findAllById(idsCargos).stream()
                .filter(p -> p.getOrganizacionId().equals(org))
                .collect(Collectors.toMap(Puesto::getId, Function.identity()));
        Map<Long, EstadoLaboral> estados = actuales.stream()
                .collect(Collectors.toMap(Actual::id, a -> EstadoLaboral.valueOf(a.estado())));
        // Solo a quien se nombra: una ficha no carga la empresa entera.
        Set<Long> idsPersonas = new HashSet<>(otras);
        tanda.forEach(s -> {
            if (s.getJefeColaboradorId() != null) idsPersonas.add(s.getJefeColaboradorId());
        });
        Map<Long, Colaborador> personasDeLaEmpresa = idsPersonas.isEmpty() ? Map.of()
                : colaboradores.findByOrganizacionIdAndIdIn(org, idsPersonas).stream()
                        .collect(Collectors.toMap(Colaborador::getId, Function.identity()));
        return new Nombres(lasSedes, lasAreas, losCargos, personasDeLaEmpresa, estados);
    }

    /** Una situación para pintar, con el «hasta» que le toca ver a quien mira. */
    private Situacion situacion(SituacionLaboral s, Nombres n, boolean sueldos, LocalDate hasta) {
        Sede sede = n.sedes().get(s.getSedeId());
        Area area = n.areas().get(s.getAreaId());
        Puesto cargo = n.cargos().get(s.getPuestoId());
        Long jefeId = s.getJefeColaboradorId();
        return new Situacion(s.getId(), s.getVigenteDesde(), hasta,
                s.getSedeId(), sede == null ? null : sede.getNombre(), sede != null && sede.isEsActiva(),
                s.getAreaId(), area == null ? null : area.getNombre(), area != null && area.isEsActiva(),
                s.getPuestoId(), cargo == null ? null : cargo.getNombre(), cargo != null && cargo.isEsActivo(),
                jefeId, jefeId == null ? null : n.colaborador(jefeId),
                jefeId != null && n.estados().get(jefeId) == EstadoLaboral.CESADO,
                s.getTipoContrato(), CatalogosDePersonas.textoDe(CatalogosDePersonas.TIPOS_CONTRATO, s.getTipoContrato()),
                s.getFinContrato(), s.getFinPeriodoPrueba(), s.getRegimenLaboral(),
                CatalogosDePersonas.textoDe(CatalogosDePersonas.REGIMENES, s.getRegimenLaboral()),
                sueldos ? s.getSueldoBase() : null, sueldos ? s.getMoneda() : null,
                s.getTipoMotivo(), CatalogosDePersonas.motivoDeLaSituacion(s.getTipoMotivo()),
                s.getDetalleMotivo(), null, s.getRegistradoEn());
    }

    private static Periodo periodo(PeriodoLaboral p) {
        return new Periodo(p.getId(), p.getFechaIngreso(), p.getFechaCese(), p.getMotivoCese(),
                CatalogosDePersonas.textoDe(CatalogosDePersonas.MOTIVOS_CESE, p.getMotivoCese()),
                p.getObservacionCese());
    }

    private Perfil perfil(Colaborador c) {
        OpcionUbigeo provincia = c.getProvinciaUbigeo() == null ? null
                : catalogosDelPerfil.ciudadesPorCodigo().get(c.getProvinciaUbigeo());
        String nivel = c.getNivelEducativoCodigo() == null ? null
                : catalogosDelPerfil.nivelesEducativos().stream()
                        .filter(n -> n.codigo().equals(c.getNivelEducativoCodigo()))
                        .map(n -> n.nombre()).findFirst().orElse(c.getNivelEducativoCodigo());
        return new Perfil(c.getTipoDocumento(),
                CatalogosDePersonas.textoDe(CatalogosDePersonas.TIPOS_DOCUMENTO, c.getTipoDocumento()),
                c.getNumeroDocumento(), c.getNombres(), c.getApellidoPaterno(), c.getApellidoMaterno(),
                c.getFechaNacimiento(), c.getSexo(), CatalogosDePersonas.textoDe(CatalogosDePersonas.SEXOS, c.getSexo()),
                c.getEstadoCivil(), CatalogosDePersonas.textoDe(CatalogosDePersonas.ESTADOS_CIVILES, c.getEstadoCivil()),
                c.getNacionalidad(), c.getCelular(), c.getCorreoPersonal(), c.getCorreoCorporativo(),
                c.getDireccion(), c.getProvinciaUbigeo(),
                provincia == null ? null : provincia.departamento() == null ? provincia.nombre()
                        : provincia.nombre() + " (" + provincia.departamento() + ")",
                c.getNivelEducativoCodigo(), nivel);
    }

    /** Lo que cambió de una situación a la siguiente, como «antes → después». */
    private List<CambioDeCampo> diferencias(SituacionLaboral antes, SituacionLaboral despues, Nombres n,
                                            boolean sueldos) {
        List<CambioDeCampo> cambios = new ArrayList<>();
        comparar(cambios, "Cargo", antes == null ? null : nombreCargo(n, antes.getPuestoId()),
                nombreCargo(n, despues.getPuestoId()));
        comparar(cambios, "Área", antes == null ? null : nombreArea(n, antes.getAreaId()),
                nombreArea(n, despues.getAreaId()));
        comparar(cambios, "Sede", antes == null ? null : nombreSede(n, antes.getSedeId()),
                nombreSede(n, despues.getSedeId()));
        comparar(cambios, "Jefe directo",
                antes == null || antes.getJefeColaboradorId() == null ? null : n.colaborador(antes.getJefeColaboradorId()),
                despues.getJefeColaboradorId() == null ? null : n.colaborador(despues.getJefeColaboradorId()));
        comparar(cambios, "Tipo de contrato",
                antes == null ? null : CatalogosDePersonas.textoDe(CatalogosDePersonas.TIPOS_CONTRATO, antes.getTipoContrato()),
                CatalogosDePersonas.textoDe(CatalogosDePersonas.TIPOS_CONTRATO, despues.getTipoContrato()));
        comparar(cambios, "Fin del contrato", antes == null ? null : fecha(antes.getFinContrato()),
                fecha(despues.getFinContrato()));
        comparar(cambios, "Fin del periodo de prueba", antes == null ? null : fecha(antes.getFinPeriodoPrueba()),
                fecha(despues.getFinPeriodoPrueba()));
        comparar(cambios, "Régimen laboral",
                antes == null ? null : CatalogosDePersonas.textoDe(CatalogosDePersonas.REGIMENES, antes.getRegimenLaboral()),
                CatalogosDePersonas.textoDe(CatalogosDePersonas.REGIMENES, despues.getRegimenLaboral()));
        if (sueldos) {
            comparar(cambios, CAMPO_SUELDO, antes == null ? null : sueldo(antes.getSueldoBase(), antes.getMoneda()),
                    sueldo(despues.getSueldoBase(), despues.getMoneda()));
        }
        return cambios;
    }

    private static void comparar(List<CambioDeCampo> cambios, String campo, String antes, String despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.add(new CambioDeCampo(campo, antes, despues));
        }
    }

    private static String nombreSede(Nombres n, Long id) {
        Sede s = n.sedes().get(id);
        return s == null ? null : s.getNombre();
    }

    private static String nombreArea(Nombres n, Long id) {
        Area a = n.areas().get(id);
        return a == null ? null : a.getNombre();
    }

    private static String nombreCargo(Nombres n, Long id) {
        Puesto p = n.cargos().get(id);
        return p == null ? null : p.getNombre();
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? null : FECHA.format(fecha);
    }

    static String sueldo(BigDecimal monto, String moneda) {
        if (monto == null) return null;
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return ("USD".equals(moneda) ? "US$ " : "S/ ") + formato.format(monto);
    }

    static boolean mismoSueldo(BigDecimal a, String monedaA, BigDecimal b, String monedaB) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0 && Objects.equals(monedaA, monedaB);
    }

    private static String nombre(String paterno, String materno, String nombres) {
        String apellidos = materno == null || materno.isBlank() ? paterno : paterno + " " + materno;
        return apellidos + ", " + nombres;
    }

    private Map<String, Object> perfilComoMapa(Colaborador c) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("tipoDocumento", c.getTipoDocumento());
        mapa.put("numeroDocumento", c.getNumeroDocumento());
        mapa.put("nombres", c.getNombres());
        mapa.put("apellidoPaterno", c.getApellidoPaterno());
        mapa.put("apellidoMaterno", c.getApellidoMaterno());
        mapa.put("fechaNacimiento", c.getFechaNacimiento() == null ? null : c.getFechaNacimiento().toString());
        mapa.put("sexo", c.getSexo());
        mapa.put("estadoCivil", c.getEstadoCivil());
        mapa.put("nacionalidad", c.getNacionalidad());
        mapa.put("celular", c.getCelular());
        mapa.put("correoPersonal", c.getCorreoPersonal());
        mapa.put("correoCorporativo", c.getCorreoCorporativo());
        mapa.put("direccion", c.getDireccion());
        mapa.put("provinciaUbigeo", c.getProvinciaUbigeo());
        mapa.put("nivelEducativoCodigo", c.getNivelEducativoCodigo());
        return mapa;
    }

    private static Map<String, Object> resumenDelAlta(Colaborador c, LocalDate ingreso, Long postulacionId) {
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("tipoDocumento", c.getTipoDocumento());
        resumen.put("numeroDocumento", c.getNumeroDocumento());
        resumen.put("nombre", c.nombreCompleto());
        resumen.put("fechaIngreso", ingreso.toString());
        if (postulacionId != null) {
            resumen.put("postulacionId", postulacionId);
        }
        return resumen;
    }

    /** Quién registró algo: su nombre, o su correo si no lo tiene. */
    private Map<Long, String> nombresDeUsuarios(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Usuario> encontrados = usuarios.findAllById(ids);
        Map<Long, Persona> porPersona = personas.findAllById(encontrados.stream()
                        .map(Usuario::getPersonaId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Persona::getId, Function.identity()));
        Map<Long, String> nombres = new HashMap<>();
        for (Usuario u : encontrados) {
            Persona p = u.getPersonaId() == null ? null : porPersona.get(u.getPersonaId());
            String completo = p == null || p.getAnonimizadoEn() != null ? ""
                    : (Optional.ofNullable(p.getNombre()).orElse("") + " "
                       + Optional.ofNullable(p.getApellidos()).orElse("")).trim();
            nombres.put(u.getId(), !completo.isEmpty() ? completo
                    : u.getCorreo() != null ? u.getCorreo() : "Usuario " + u.getId());
        }
        return nombres;
    }

    private static long claveDelDocumento(Long org, String tipo, String numero) {
        return ("colaborador:" + org + ":" + tipo + ":" + numero).hashCode();
    }

    private static String exigirMotivo(String motivo) {
        String elMotivo = ReglasDelColaborador.texto(motivo);
        if (elMotivo == null) {
            throw new IllegalArgumentException("Escribe el motivo");
        }
        return elMotivo;
    }

    private static void exigir(String error) {
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }
}
