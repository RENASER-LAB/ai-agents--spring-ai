package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.Afectado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.ListaDeAfectados;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.NoEncolada;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.TandaDeRecalificacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.VacanteAfectada;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.HallazgoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.HallazgoPerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioRecalificacionDelCv;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Ver {@link ServicioRecalificacionDelCv}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioRecalificacionDelCvImpl implements ServicioRecalificacionDelCv {

    private static final String ETAPA = "PERFIL_INTEGRAL";
    private static final String ABIERTA = "PUBLICADA";
    static final String PENDIENTE = "PENDIENTE";
    static final String EN_CURSO = "EN_CURSO";
    static final String DETENIDA = "DETENIDA";
    private static final String TERMINADA = "TERMINADA";
    /** Datos del CV, evidencia del CV y retrato. La búsqueda suma una si la vacante busca algo. */
    static final int LLAMADAS_POR_PERSONA = 3;
    private static final String MOTIVO = "Recalificación del CV y del retrato: la IA no sabía la fecha "
            + "de hoy o no veía los años de experiencia (spec la-ia-sabe-que-dia-es-hoy)";

    private static final int BANDERAS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;

    /**
     * Un texto que dice que una fecha es «futura», «posterior a hoy», «imposible» o que «aún no
     * ocurre». Con cuantificadores posesivos: el texto es de la IA, pero no cuesta nada.
     */
    private static final Pattern FECHA_QUE_NO_CUADRA = Pattern.compile(
            "\\bfutur[oa]s?\\b|\\bimposibles?\\b|\\bposterior(?:es)?\\s++a\\s++(?:hoy|la\\s++fecha)"
                    + "|\\ba[uú]n\\s++no\\s++(?:ocurr|suced|lleg|ha[ns]?\\s++(?:ocurrido|sucedido|llegado))",
            BANDERAS);

    /**
     * Que el texto hable de fechas: la palabra, un año o un mes. Sin esto, «su potencial futuro»
     * contaría como fecha imposible.
     */
    private static final Pattern HABLA_DE_FECHAS = Pattern.compile(
            "\\bfechas?\\b|\\b(?:19|20)\\d{2}\\b|\\ba[ñn]os?\\b|\\bmes(?:es)?\\b|\\bactualidad\\b"
                    + "|\\b(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|se(?:p|t|pt)iembre"
                    + "|octubre|noviembre|diciembre)\\b",
            BANDERAS);

    private final OrganizacionRepository organizaciones;
    private final VacanteRepository vacantes;
    private final PostulacionRepository postulaciones;
    private final EstadoPostulacionRepository estados;
    private final PerfilTalentoRepository perfiles;
    private final HallazgoPerfilRepository hallazgos;
    private final NotaCriterioRepository notasCriterio;
    private final CriterioRepository criterios;
    private final CvRepository cvs;
    private final AnonimizadorCv anonimizador;
    private final LoBuscadoEnElCv loBuscado;
    private final ColaCalificacionIa cola;
    private final NombresDeUsuarios nombres;
    private final ServicioAuditoria auditoria;

    /** Una afectada con lo que hace falta para encolarla y auditarla. */
    private record Candidata(Afectado afectado, Long organizacionId) {
    }

    @Override
    @Transactional(readOnly = true)
    public ListaDeAfectados afectados(ContextoUsuario quien, Set<Long> soloVacantes) {
        exigirPlataforma(quien);
        List<Afectado> todas = buscar(soloVacantes).stream().map(Candidata::afectado).toList();
        List<Afectado> aRecalificar = todas.stream().filter(a -> !EN_CURSO.equals(a.estado())).toList();
        Map<Long, VacanteAfectada> porVacante = new LinkedHashMap<>();
        for (Afectado a : aRecalificar) {
            porVacante.merge(a.vacanteId(), new VacanteAfectada(a.vacanteId(), a.vacante(), 1),
                    (antes, una) -> new VacanteAfectada(antes.vacanteId(), antes.vacante(), antes.afectados() + 1));
        }
        return new ListaDeAfectados(aRecalificar.size(),
                (int) aRecalificar.stream().filter(Afectado::porLaFecha).count(),
                (int) aRecalificar.stream().filter(Afectado::porLosAnios).count(),
                todas.size() - aRecalificar.size(),
                (int) aRecalificar.stream().filter(a -> DETENIDA.equals(a.estado())).count(),
                aRecalificar.stream().mapToInt(Afectado::llamadas).sum(),
                List.copyOf(porVacante.values()), todas);
    }

    /*
     * Sin @Transactional a propósito: cada encolado va en su propia transacción (la de la cola),
     * y un fallo con una persona no puede dejar marcada para deshacer la tanda entera.
     */
    @Override
    public TandaDeRecalificacion recalificar(ContextoUsuario quien, Set<Long> soloVacantes) {
        exigirPlataforma(quien);
        int encoladas = 0;
        int yaEnMarcha = 0;
        int llamadas = 0;
        List<NoEncolada> fallidas = new ArrayList<>();
        for (Candidata c : buscar(soloVacantes)) {
            Afectado a = c.afectado();
            if (EN_CURSO.equals(a.estado())) {
                yaEnMarcha++;
                continue;
            }
            try {
                if (cola.recalificarElCv(a.postulacionId(), a.llamadas() > LLAMADAS_POR_PERSONA)) {
                    auditoria.registrar(c.organizacionId(), quien, "recalificar_cv_por_la_fecha",
                            "postulacion", a.postulacionId(), null,
                            Map.of("porLaFecha", a.porLaFecha(), "porLosAnios", a.porLosAnios()), MOTIVO);
                    encoladas++;
                    llamadas += a.llamadas();
                    continue;
                }
                String porQue = cola.porQueNoSePuedeUsarLaIa(c.organizacionId());
                if (porQue != null) {
                    fallidas.add(new NoEncolada(a.postulacionId(), a.candidato(), porQue));
                } else {
                    yaEnMarcha++;
                }
            } catch (RuntimeException e) {
                // Esa persona conserva su calificación anterior y se reintenta en otra tanda;
                // las demás siguen.
                log.warn("No se pudo encolar la recalificación del CV de la postulación {}: {}",
                        a.postulacionId(), e.toString());
                fallidas.add(new NoEncolada(a.postulacionId(), a.candidato(),
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        log.info("Recalificación del CV por la fecha: {} encoladas, {} ya en marcha, {} sin encolar "
                + "({} llamadas estimadas), pedida por el usuario {}", encoladas, yaEnMarcha,
                fallidas.size(), llamadas, quien.usuarioId());
        return new TandaDeRecalificacion(encoladas, yaEnMarcha, fallidas, llamadas);
    }

    // ================================================================== quiénes

    private List<Candidata> buscar(Set<Long> soloVacantes) {
        Set<String> cerrados = estados.findAllByOrderByOrden().stream()
                .filter(EstadoPostulacion::isEsFinal)
                .map(EstadoPostulacion::getCodigo)
                .collect(Collectors.toSet());
        List<Vacante> abiertas = vacantes.findByEstadoAndEliminadaEnIsNullOrderByPublicadaEnDesc(ABIERTA).stream()
                .filter(v -> v.getArchivadaEn() == null)
                // Sin filtro, todas las abiertas; con él, solo las pedidas que sigan abiertas.
                .filter(v -> soloVacantes == null || soloVacantes.isEmpty() || soloVacantes.contains(v.getId()))
                .toList();
        Map<Long, Vacante> vacantePorId = new LinkedHashMap<>();
        List<Postulacion> enCurso = new ArrayList<>();
        for (Vacante v : abiertas) {
            vacantePorId.put(v.getId(), v);
            postulaciones.findByVacanteIdOrderByCreadoEnDesc(v.getId()).stream()
                    .filter(p -> !cerrados.contains(p.getEstadoCodigo()))
                    .forEach(enCurso::add);
        }
        List<Long> ids = enCurso.stream().map(Postulacion::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        // Solo quien ya tiene retrato: recalificar es rehacer, no calificar por primera vez.
        Map<Long, PerfilTalento> perfilPorPostulacion = porClave(perfiles.findByPostulacionIdIn(ids),
                PerfilTalento::getPostulacionId);
        Map<Long, List<HallazgoPerfil>> hallazgosPorPerfil = perfilPorPostulacion.isEmpty() ? Map.of()
                : hallazgos.findByPerfilTalentoIdIn(perfilPorPostulacion.values().stream()
                        .map(PerfilTalento::getId).toList()).stream()
                        .collect(Collectors.groupingBy(HallazgoPerfil::getPerfilTalentoId));
        // nota_criterio la comparten las tres etapas: solo cuentan los criterios del CV.
        Set<Long> delCv = criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(ETAPA)
                .stream().map(Criterio::getId).collect(Collectors.toSet());
        Map<Long, List<NotaCriterio>> notasDelCv = notasCriterio.findByPostulacionIdIn(ids).stream()
                .filter(n -> delCv.contains(n.getCriterioId()))
                .collect(Collectors.groupingBy(NotaCriterio::getPostulacionId));
        Map<Long, Cv> cvPorPostulacion = porClave(cvs.findByPostulacionIdIn(ids), Cv::getPostulacionId);

        // Lo que la IA ya hizo con la fecha de hoy no cuenta: quien está al día no es afectado.
        Set<Long> ejecuciones = new HashSet<>();
        perfilPorPostulacion.values().forEach(p -> ejecuciones.add(p.getEjecucionIaId()));
        notasDelCv.values().forEach(l -> l.forEach(n -> ejecuciones.add(n.getEjecucionIaId())));
        ejecuciones.remove(null);
        Set<Long> alDia = ejecuciones.isEmpty() ? Set.of() : cola.conLaFechaDeHoy(ejecuciones);

        Map<Long, ColaCalificacionIa.Seguimiento> seguimiento = cola.recalificacionDelCvDe(ids);
        Map<Long, String> nombrePorUsuario = nombres.porUsuario(enCurso.stream()
                .map(Postulacion::getUsuarioId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, Boolean> buscaEnElCv = new LinkedHashMap<>();

        List<Candidata> salida = new ArrayList<>();
        for (Postulacion p : enCurso) {
            PerfilTalento perfil = perfilPorPostulacion.get(p.getId());
            if (perfil == null) {
                continue;
            }
            List<NotaCriterio> suyas = notasDelCv.getOrDefault(p.getId(), List.of());
            // Lo ajustado a mano no lo rehace la IA: no hace a nadie afectado.
            List<NotaCriterio> deLaIaSinFecha = suyas.stream()
                    .filter(n -> n.getAjustadaPorUsuarioId() == null && !estaAlDia(alDia, n.getEjecucionIaId()))
                    .toList();
            boolean retratoViejo = !estaAlDia(alDia, perfil.getEjecucionIaId());
            boolean hallazgoConFechaMala = retratoViejo
                    && hallazgosPorPerfil.getOrDefault(perfil.getId(), List.of()).stream()
                            .anyMatch(ServicioRecalificacionDelCvImpl::hablaDeUnaFechaQueNoCuadra);
            boolean justificacionConFechaMala = deLaIaSinFecha.stream()
                    .anyMatch(n -> hablaDeUnaFechaQueNoCuadra(n.getExplicacion()));
            boolean porLaFecha = hallazgoConFechaMala || justificacionConFechaMala;
            boolean notasViejas = !deLaIaSinFecha.isEmpty();
            Cv cv = cvPorPostulacion.get(p.getId());
            boolean porLosAnios = (retratoViejo || notasViejas) && cv != null
                    && anonimizador.laReglaViejaTapabaDeMas(cv.getTextoExtraido());
            if (!porLaFecha && !porLosAnios) {
                continue;
            }
            ColaCalificacionIa.Seguimiento s = seguimiento.get(p.getId());
            String estado = estadoDe(s);
            String motivoDelFallo = s != null && DETENIDA.equals(estado) ? s.motivo() : null;
            Vacante vacante = vacantePorId.get(p.getVacanteId());
            boolean busca = buscaEnElCv.computeIfAbsent(vacante.getId(),
                    id -> !loBuscado.vigentes(id).isEmpty());
            String candidato = p.getUsuarioId() == null ? NombresDeUsuarios.ANONIMO
                    : nombrePorUsuario.getOrDefault(p.getUsuarioId(), NombresDeUsuarios.ANONIMO);
            salida.add(new Candidata(new Afectado(p.getId(), candidato, vacante.getId(), vacante.getTitulo(),
                    porLaFecha, porLosAnios, estado, motivoDelFallo,
                    LLAMADAS_POR_PERSONA + (busca ? 1 : 0)), p.getOrganizacionId()));
        }
        return salida;
    }

    /**
     * En qué punto está su recalificación: sin ninguna (o una ya terminada que no la arregló),
     * pendiente; en la cola, en curso; si no, detenida y se reintenta.
     */
    private static String estadoDe(ColaCalificacionIa.Seguimiento s) {
        if (s == null || TERMINADA.equals(s.estado())) {
            return PENDIENTE;
        }
        return EN_CURSO.equals(s.estado()) ? EN_CURSO : DETENIDA;
    }

    /** Si esa ejecución ya llevaba la fecha de hoy. Sin ejecución anotada, no. */
    private static boolean estaAlDia(Set<Long> alDia, Long ejecucionIaId) {
        return ejecucionIaId != null && alDia.contains(ejecucionIaId);
    }

    private static boolean hablaDeUnaFechaQueNoCuadra(HallazgoPerfil h) {
        return Stream.of(h.getDescripcion(), h.getEvidencia(), h.getSugerencia())
                .anyMatch(ServicioRecalificacionDelCvImpl::hablaDeUnaFechaQueNoCuadra);
    }

    /** «Fecha futura», «2026 aún no ocurre», «fechas imposibles»: la palabra y una fecha. */
    static boolean hablaDeUnaFechaQueNoCuadra(String texto) {
        return texto != null && FECHA_QUE_NO_CUADRA.matcher(texto).find()
                && HABLA_DE_FECHAS.matcher(texto).find();
    }

    // ================================================================== permisos

    /** 403 y no 404: no se pide un recurso, se pide una capacidad de la dueña de la plataforma. */
    private void exigirPlataforma(ContextoUsuario quien) {
        Long plataforma = organizaciones.findByEsPlataformaTrue()
                .map(Organizacion::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Ninguna organización está marcada como plataforma"));
        if (quien == null || !plataforma.equals(quien.organizacionId())) {
            throw new AccessDeniedException("La recalificación de los afectados la pide la plataforma");
        }
    }

    private static <T> Map<Long, T> porClave(List<T> filas, Function<T, Long> clave) {
        return filas.stream().collect(Collectors.toMap(clave, Function.identity(), (a, b) -> a));
    }
}
