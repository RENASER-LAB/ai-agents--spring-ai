package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.entity.Ubigeo;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfil.repository.UbigeoRepository;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.AlertaResponse;
import com.renaser.ai.ai_engine.pesos.entity.Etapa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.CalificacionEncoladaResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.FilaRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.HallazgoResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DatosCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.NotaCriterioResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PasadaEncolada;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PerfilIntegralResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RankingVacante;
import com.renaser.ai.ai_engine.perfilintegral.entity.Alerta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.HallazgoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.AlertaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.HallazgoPerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPerfilIntegralPanel;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import lombok.RequiredArgsConstructor;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static com.renaser.ai.ai_engine.vacante.service.impl.ServicioVacantesPanelImpl.CUESTIONARIO_TECNICO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El Perfil Integral para el panel: mirarlo y volver a pedirlo.
 *
 * <p>La etapa se cierra sola cuando el tercer agente termina, así que este servicio no
 * mueve a nadie de estado: solo lee lo que la IA dejó escrito y, si hace falta, vuelve a
 * poner el trabajo en la cola.
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ServicioPerfilIntegralPanelImpl implements ServicioPerfilIntegralPanel {

    private static final String ETAPA = "PERFIL_INTEGRAL";

    /**
     * La etapa cuyos criterios NO son los ocho globales.
     *
     * <p>Los del currículum valen para cualquier vacante; los de la prueba del puesto son de
     * la plantilla con la que se midió a ese candidato, así que dos vacantes traen columnas
     * distintas. Ver {@code rubricasDeLaPrueba}.
     */
    private static final String ETAPA_TECNICA = "PRUEBA_PUESTO";

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final AlcanceSobreLaVacante alcanceVacante;
    private final PerfilTalentoRepository perfiles;
    private final HallazgoPerfilRepository hallazgos;
    private final NotaCriterioRepository notasCriterio;
    private final NotaEtapaRepository notasEtapa;
    private final AlertaRepository alertas;
    private final CriterioRepository criterios;
    private final CvRepository cvs;
    private final DatoCvRepository datosCv;
    private final com.renaser.ai.ai_engine.parametro.service.ServicioParametros parametros;
    private final PesoCriterioRepository pesosCriterio;
    private final EstadoPostulacionRepository estados;
    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final PuestoRepository puestos;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final ServicioAuditoria auditoria;
    private final ColaCalificacionIa cola;
    private final MaquinaEstados maquina;
    private final com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository evaluaciones;
    private final com.renaser.ai.ai_engine.pesos.repository.EtapaRepository etapasCatalogo;
    private final UbigeoRepository ubigeos;
    private final PerfilCandidatoRepository perfilesCandidato;
    private final com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository intentos;

    // El orden de la tanda. Manda el grupo, no la nota: quien llega a la nota arrastrando un
    // riesgo crítico no va por delante de quien llega sin ninguno, y ordenar por número
    // escondería justo eso. Lo que no tiene grupo va al final, no arriba.
    private static final List<String> ORDEN_GRUPO =
            List.of("ALTA", "POTENCIAL_CON_RIESGO", "NO_PRIORIZADO", "INCOMPATIBLE");

    @Override
    public PerfilIntegralResponse ver(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ver_perfil_integral");
        PerfilTalento perfil = perfiles.findByPostulacionId(postulacionId).orElse(null);

        // Los ocho criterios del currículum mandan el orden y el máximo de cada uno. Se
        // parte de ellos y no de las notas: así un criterio sin calificar se ve como un
        // hueco, en vez de desaparecer de la lista como si no existiera.
        Map<Long, NotaCriterio> notaPorCriterio = notasCriterio.findByPostulacionId(postulacionId)
                .stream()
                .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity(),
                        (a, b) -> a));
        Map<Long, BigDecimal> pesos = pesosDe(postulacion);
        List<NotaCriterioResponse> notas = criterios
                .findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(ETAPA).stream()
                .map(c -> pintarNota(c, notaPorCriterio.get(c.getId()), pesos.get(c.getId())))
                .toList();

        List<HallazgoResponse> lista = perfil == null ? List.of()
                : hallazgos.findByPerfilTalentoId(perfil.getId()).stream()
                        .map(h -> new HallazgoResponse(h.getTipo(), h.getDescripcion(),
                                h.getEvidencia(), h.isEsCanalizable(), h.getSugerencia()))
                        .toList();

        List<AlertaResponse> avisos = alertas.findByPostulacionId(postulacionId).stream()
                .map(a -> new AlertaResponse(a.getTipo(), a.getDescripcion(), a.getCreadoEn()))
                .toList();

        BigDecimal notaDeEtapa = notasEtapa.findByPostulacionIdAndEtapaCodigo(postulacionId, ETAPA)
                .map(NotaEtapa::getPuntaje).orElse(null);

        return new PerfilIntegralResponse(
                postulacionId,
                estadoDeLaCalificacion(postulacion, perfil),
                perfil == null ? null : perfil.getResumen(),
                perfil == null ? null : perfil.getAdecuacion(),
                perfil == null ? null : perfil.getPotencial(),
                perfil == null ? null : perfil.getAltoRendimiento(),
                perfil == null ? null : perfil.getConfianzaEvidencia(),
                notaDeEtapa,
                perfil == null ? null : perfil.getActualizadoEn(),
                lista, notas, avisos);
    }

    @Override
    @Transactional
    public CalificacionEncoladaResponse recalificar(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ajustar_nota");

        // Sin evaluación entregada no hay respuestas que calificar ni nota de lo cerrado
        // sobre la que apoyarse: encolar aquí solo produciría un trabajo condenado a fallar.
        if (postulacion.getEvaluacionId() == null) {
            throw new IllegalStateException(
                    "Esta postulación todavía no tiene evaluación: no hay nada que calificar");
        }

        // Primero lo que falte (huecos, fallos); si no falta nada, se rehace el evaluador
        // igualmente: quien pide RE-calificar quiere notas nuevas, no que le digan que las
        // viejas ya existen. Es la palanca de la calibración: cambia una señal, se pide
        // esto, y las mismas respuestas se puntúan con la señal nueva.
        boolean encolado = cola.encolarPerfilIntegral(postulacionId)
                || cola.reencolarEvaluador(postulacionId);
        if (!encolado) {
            return new CalificacionEncoladaResponse("SIN_CAMBIOS",
                    "Hay un trabajo de calificación en marcha ahora mismo: cuando termine, "
                            + "vuelve a pedirla si hace falta.");
        }
        return new CalificacionEncoladaResponse("ENCOLADA",
                "La calificación quedó en cola. Tarda decenas de segundos: "
                        + "consulta el perfil para ver cuándo termina.");
    }

    @Override
    @Transactional
    public CalificacionEncoladaResponse cribarCv(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ajustar_nota");

        // Lo único indispensable. Sin archivo el agente se plantaría al pedir el texto, y
        // más vale decirlo aquí que gastar tres reintentos para llegar a la misma frase.
        cvs.findByPostulacionId(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Currículum", "postulación", postulacionId));

        // Una postulación cerrada no se vuelve a calificar. Da igual que tenga currículum:
        // el candidato se retiró, no continuó o ya está contratado, y calificarlo lo
        // devolvería a «por confirmar» —a la bandeja de alguien— además de pagar el modelo.
        if (cerrados().contains(postulacion.getEstadoCodigo())) {
            throw new IllegalStateException(
                    "Esta postulación ya está cerrada («" + postulacion.getEstadoCodigo()
                            + "»): no se vuelve a calificar");
        }

        if (!cola.encolarCribaCv(postulacionId)) {
            return new CalificacionEncoladaResponse("SIN_CAMBIOS",
                    "No había nada que cribar: o el currículum ya está leído, o hay un "
                            + "trabajo en marcha ahora mismo.");
        }
        moverACalificando(postulacion);
        auditoria.registrar(quien.organizacionId(), quien, "cribar_cv",
                "postulacion", postulacionId, null, Map.of(), null);
        return new CalificacionEncoladaResponse("ENCOLADA",
                "La criba del currículum quedó en cola. Tarda decenas de segundos: "
                        + "consulta el ranking de la vacante para ver cuándo termina.");
    }

    @Override
    @Transactional
    public PasadaEncolada cribaRapida(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = vacanteVisible(quien, vacanteId, "ajustar_nota");
        Set<String> cerrados = cerrados();
        List<Postulacion> suyas = postulaciones.findByVacanteIdOrderByCreadoEnDesc(vacanteId);
        List<Long> ids = suyas.stream().map(Postulacion::getId).toList();

        // Con qué pasada está cada uno y quién tiene currículum, para la tanda entera y no
        // dentro del bucle. Es la misma tanda que pinta el ranking —crece con los candidatos
        // que se apunten— y preguntarlo por fila costaba dos consultas por persona antes
        // siquiera de decidir si se le encola.
        Map<Long, ColaCalificacionIa.Estado> enCola = cola.estadoDe(ids);
        Map<Long, Cv> cvPorPostulacion = porPostulacion(cvs.findByPostulacionIdIn(ids),
                Cv::getPostulacionId);

        int encolados = 0;
        for (Postulacion p : suyas) {
            // Una tanda no es «todo el mundo que tenga currículum». Quien se retiró, quien
            // no continuó y quien ya está contratado siguen en la vacante, y barrerla sin
            // mirar el estado los devolvía a «por confirmar» pagando el modelo por el camino.
            if (cerrados.contains(p.getEstadoCodigo())) {
                continue;
            }
            // Quien ya tiene retrato no se vuelve a calificar: repetirlo cuesta lo mismo y
            // no cambia nada. Para rehacer uno concreto está el botón de su ficha.
            ColaCalificacionIa.Estado estado = enCola.get(p.getId());
            String pasada = estado == null ? null : estado.pasada();
            if (pasada != null || !cvPorPostulacion.containsKey(p.getId())) {
                continue;
            }
            // Se cuenta lo que de verdad quedó en la cola, no lo que se intentó. Antes se
            // sumaba siempre, así que un segundo clic respondía «43 en cola» sin haber
            // encolado a nadie, y eso mismo quedaba escrito en la auditoría.
            if (!cola.encolarCribaRapida(p.getId())) {
                continue;
            }
            moverACalificando(p);
            encolados++;
        }
        auditoria.registrar(quien.organizacionId(), quien, "criba_rapida",
                "vacante", vacanteId, null, Map.of("candidatos", String.valueOf(encolados)), null);
        return new PasadaEncolada("ENCOLADA", encolados,
                encolados == 0
                        ? "No había nadie sin calificar en «" + vacante.getTitulo() + "»."
                        : encolados + " currículums en cola. Con el modelo rápido, una tanda "
                          + "de diez tarda alrededor de medio minuto.");
    }

    @Override
    @Transactional
    public PasadaEncolada cribaFina(ContextoUsuario quien, Long vacanteId) {
        vacanteVisible(quien, vacanteId, "ajustar_nota");
        Set<String> cerrados = cerrados();
        List<FilaRanking> filas = ranking(quien, vacanteId).filas().stream()
                .filter(f -> !cerrados.contains(f.estado()))
                .toList();

        // Sin una primera pasada que haya ordenado, «los de arriba» no existen: la lista
        // sale por orden alfabético y la segunda pasada se gastaría en la gente que tocó
        // por la letra de su apellido. Es un error fácil de cometer —basta pulsar el botón
        // mientras la tanda todavía se está cargando— y caro de descubrir.
        long conNota = filas.stream().filter(f -> f.notaEtapa() != null).count();
        if (conNota == 0) {
            throw new IllegalStateException(
                    "Todavía no hay ninguna nota en esta convocatoria: la segunda pasada no "
                            + "sabría a quién mirar y elegiría por orden alfabético. Lanza "
                            + "primero la pasada rápida y espera a que termine.");
        }
        if (conNota < filas.size()) {
            log.warn("La segunda pasada de la vacante {} se pide con {} de {} candidatos "
                    + "todavía sin nota: los que faltan no entran en el corte",
                    vacanteId, filas.size() - conNota, filas.size());
        }

        int porcentaje = parametros.entero(quien.organizacionId(), "porcentaje_criba_fina", 50);
        // Al menos uno: con tres candidatos y un corte del 20 % la cuenta da cero, y una
        // segunda pasada que no mira a nadie no es una segunda pasada.
        int cuantos = Math.max(1, (int) Math.ceil(conNota * porcentaje / 100.0));

        List<FilaRanking> corte = filas.stream()
                .filter(f -> f.notaEtapa() != null)
                .limit(cuantos).toList();

        // El corte es la mitad de la tanda por defecto, así que crece con los candidatos.
        // Traerlas de una vez y no con un findById por fila: la postulación solo hace falta
        // para moverla de estado, y quién entra en el corte ya se sabe aquí.
        Map<Long, Postulacion> porId = porId(
                postulaciones.findAllById(corte.stream().map(FilaRanking::postulacionId).toList()),
                Postulacion::getId);

        int encolados = 0;
        for (FilaRanking f : corte) {
            // Quien ya pasó por la fina no repite: es la definitiva.
            if ("FINA".equals(f.pasada())) {
                continue;
            }
            if (!cola.encolarCribaFina(f.postulacionId())) {
                continue;
            }
            Postulacion p = porId.get(f.postulacionId());
            if (p != null) {
                moverACalificando(p);
            }
            encolados++;
        }
        auditoria.registrar(quien.organizacionId(), quien, "criba_fina",
                "vacante", vacanteId, null,
                Map.of("candidatos", String.valueOf(encolados),
                       "porcentaje", String.valueOf(porcentaje)), null);
        return new PasadaEncolada("ENCOLADA", encolados,
                encolados == 0
                        ? "Los de arriba ya están calificados con el modelo que razona."
                        : "Se vuelven a calificar los " + encolados + " primeros (el "
                          + porcentaje + "% de la tanda) con el modelo que razona. "
                          + "Tarda alrededor de un minuto y medio.");
    }

    /**
     * Deja el estado diciendo la verdad mientras la IA trabaja.
     *
     * <p>Si se quedara en PERFIL_TURNO_CANDIDATO, la bandeja seguiría pidiéndole algo al
     * candidato que ya nadie espera.
     */
    private void moverACalificando(Postulacion p) {
        if (!"PERFIL_CALIFICANDO".equals(p.getEstadoCodigo())
                && !"PERFIL_POR_CONFIRMAR".equals(p.getEstadoCodigo())) {
            maquina.transicionar(p, "PERFIL_CALIFICANDO", null, null, true, false, null);
        }
    }

    /** El estado en el que le toca al candidato responder su evaluación. */
    private static final String LE_TOCA = "PERFIL_TURNO_CANDIDATO";

    @Override
    @Transactional
    public com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.EvaluacionReabierta
            reabrirEvaluacion(ContextoUsuario quien, Long postulacionId, Integer dias, String motivo) {

        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Reabrir una evaluación exige un motivo escrito");
        }

        Postulacion postulacion = postulaciones.findByIdAndOrganizacionId(
                        postulacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));

        if (maquina.yaTermino(postulacion)) {
            throw new IllegalStateException("Esta postulación ya terminó: no se le reabre nada");
        }
        if (postulacion.getEvaluacionId() == null) {
            throw new IllegalStateException("Esta postulación no tiene evaluación que reabrir");
        }

        var evaluacion = evaluaciones.findById(postulacion.getEvaluacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "La postulación apunta a una evaluación que no existe"));

        // Una entregada no se reabre. Su nota ya pudo usarse para decidir, y volver a abrirla
        // dejaría esa decisión apoyada en algo que después cambió.
        if ("TERMINADA".equals(evaluacion.getEstado())) {
            throw new IllegalStateException(
                    "Esa evaluación ya fue entregada: reabrirla invalidaría su nota");
        }

        int plazo = dias != null && dias > 0
                ? dias
                : parametros.entero(postulacion.getOrganizacionId(), "dias_plazo_evaluacion", 14);

        java.time.Instant vence = java.time.Instant.now()
                .plus(plazo, java.time.temporal.ChronoUnit.DAYS);
        String venceAntes = String.valueOf(evaluacion.getVenceEn());

        // Si ya la había empezado se respeta: vuelve a EN_CURSO y conserva lo respondido.
        evaluacion.setEstado(evaluacion.getIniciadaEn() == null ? "PENDIENTE" : "EN_CURSO");
        evaluacion.setVenceEn(vence);
        // La vigencia manda sobre el plazo: de nada sirve poder responder si el resultado ya
        // no vale. Se estira solo si se quedaría corta.
        if (evaluacion.getVigenteHasta() == null || evaluacion.getVigenteHasta().isBefore(vence)) {
            evaluacion.setVigenteHasta(vence);
        }
        evaluaciones.save(evaluacion);

        if (!LE_TOCA.equals(postulacion.getEstadoCodigo())) {
            maquina.transicionar(postulacion, LE_TOCA, quien, motivo, false, false, null);
        }

        auditoria.registrar(postulacion.getOrganizacionId(), quien,
                "reabrir_evaluacion", "postulacion", postulacion.getId(),
                java.util.Map.of("venceEn", venceAntes),
                java.util.Map.of("venceEn", vence.toString(), "dias", String.valueOf(plazo)),
                motivo);

        return new com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.EvaluacionReabierta(
                postulacion.getId(), postulacion.getEstadoCodigo(), vence, plazo);
    }


    @Override
    @Transactional(readOnly = true)
    public RankingVacante ranking(ContextoUsuario quien, Long vacanteId) {
        // La criba fina y todo lo anterior a las etapas dependen de esta firma:
        // siempre es la nota de la preselección.
        return ranking(quien, vacanteId, ETAPA);
    }

    @Override
    @Transactional(readOnly = true)
    public RankingVacante ranking(ContextoUsuario quien, Long vacanteId, String etapaCodigo) {
        // Un llamador interno que pase nulo quiere el ranking de siempre, no un 500.
        String etapa = etapaCodigo == null ? ETAPA : etapaCodigo;
        // Primero quien mira, despues que mira: el alcance se comprueba antes de
        // contestar nada, aunque sea un catalogo que ese permiso ya deja ver.
        Vacante vacante = vacanteVisible(quien, vacanteId, "ver_embudo");
        // La preseleccion no se valida: es la etapa de siempre y validarla seria
        // una consulta extra en cada apertura del ranking.
        if (!ETAPA.equals(etapa) && !etapasCatalogo.existsById(etapa)) {
            throw new IllegalArgumentException(
                    "No existe la etapa «" + etapa + "». Las que puntúan: "
                            + etapasCatalogo.findAllByOrderByOrdenAsc().stream()
                                    .map(Etapa::getCodigo)
                                    .collect(Collectors.joining(", ")));
        }
        Puesto puesto = puestos.findById(vacante.getPuestoId()).orElse(null);

        // Los pesos son los de la versión de la vacante, no los de la última publicada: una
        // nota de hace tres meses tiene que seguir explicándose con los pesos de entonces.
        Map<Long, BigDecimal> pesos = puesto == null ? Map.of()
                : pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(
                                vacante.getVersionPesosId(), puesto.getNivelPuestoCodigo()).stream()
                        .collect(Collectors.toMap(PesoCriterio::getCriterioId,
                                PesoCriterio::getPeso, (a, b) -> a));

        List<Criterio> delCurriculum =
                criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(ETAPA);
        Set<Long> idsDelCurriculum =
                delCurriculum.stream().map(Criterio::getId).collect(Collectors.toSet());
        Map<String, String> nombreEstado = estados.findAllByOrderByOrden().stream()
                .collect(Collectors.toMap(EstadoPostulacion::getCodigo,
                        EstadoPostulacion::getNombre, (a, b) -> a));

        List<Postulacion> suyas = postulaciones.findByVacanteIdOrderByCreadoEnDesc(vacanteId);
        List<Long> ids = suyas.stream().map(Postulacion::getId).toList();

        // ============================================================================
        // Todo lo de la tanda, en bloque.
        //
        // Antes esto pedía once cosas por candidato dentro del bucle. Con cien postulantes
        // eran más de mil consultas para pintar una pantalla, y justamente esta pantalla
        // existe para mirar la tanda entera de una vez. Ahora son once consultas en total,
        // no once por fila: cada una trae lo de todos y el bucle solo busca en memoria.
        // ============================================================================
        Map<Long, DatoCv> fichas = porPostulacion(datosCv.findByPostulacionIdIn(ids),
                DatoCv::getPostulacionId);
        Map<Long, ColaCalificacionIa.Estado> estados = cola.estadoDe(ids);
        Map<Long, PerfilTalento> perfilPorPostulacion =
                porPostulacion(perfiles.findByPostulacionIdIn(ids), PerfilTalento::getPostulacionId);
        Map<Long, List<HallazgoPerfil>> hallazgosPorPerfil = hallazgos
                .findByPerfilTalentoIdIn(perfilPorPostulacion.values().stream()
                        .map(PerfilTalento::getId).toList()).stream()
                .collect(Collectors.groupingBy(HallazgoPerfil::getPerfilTalentoId));
        /*
          Las columnas de la pestaña «Prueba del puesto» son las de SU rúbrica, no los ocho
          del currículum. Hasta aquí las cinco pestañas enseñaban las mismas ocho columnas,
          que en la técnica eran ocho criterios del CV con pinta de ser de la prueba.

          Dos consultas para la tanda entera —los intentos, y las rúbricas de las versiones
          que aparezcan—, nunca una por fila: es la misma regla que sigue todo lo de arriba.
          Fuera de esa etapa el mapa se queda vacío y no se consulta nada.
        */
        boolean laTecnica = ETAPA_TECNICA.equals(etapa);
        Map<Long, List<Criterio>> rubricaPorPostulacion =
                laTecnica ? rubricasDeLaPrueba(vacante, ids) : Map.of();

        Map<Long, List<NotaCriterio>> notasPorPostulacion = notasCriterio.findByPostulacionIdIn(ids)
                .stream().collect(Collectors.groupingBy(NotaCriterio::getPostulacionId));
        Map<Long, NotaEtapa> etapaPorPostulacion = porPostulacion(
                notasEtapa.findByPostulacionIdInAndEtapaCodigo(ids, etapa),
                NotaEtapa::getPostulacionId);
        Map<Long, Long> alertasPorPostulacion = alertas.findByPostulacionIdIn(ids).stream()
                .collect(Collectors.groupingBy(Alerta::getPostulacionId, Collectors.counting()));
        Map<Long, Cv> cvPorPostulacion = porPostulacion(cvs.findByPostulacionIdIn(ids),
                Cv::getPostulacionId);

        Map<Long, Usuario> usuariosPorId = porId(
                usuarios.findAllById(suyas.stream().map(Postulacion::getUsuarioId).toList()),
                Usuario::getId);
        Map<Long, Persona> personasPorId = porId(
                personas.findAllById(usuariosPorId.values().stream()
                        .map(Usuario::getPersonaId).filter(Objects::nonNull).toList()),
                Persona::getId);
        Map<Long, Archivo> archivosPorId = porId(
                archivos.findAllById(cvPorPostulacion.values().stream()
                        .map(Cv::getArchivoOriginalId).filter(Objects::nonNull).toList()),
                Archivo::getId);

        // La ciudad, en el mismo bloque y por la misma razón que todo lo de arriba: son
        // dos consultas para la tanda entera —las provincias de todos, y los departamentos
        // de esas provincias— en vez de dos por fila.
        Map<String, String> ciudades = ciudadesDe(personasPorId.values());

        // La pretensión solo se pide si quien mira puede verla. No es solo no pintarla:
        // sin permiso, la consulta ni se lanza, y así el dato no llega a existir en la
        // memoria de esta petición.
        boolean puedeVerPretension = quien.tiene("ver_pretension");
        Map<Long, PerfilCandidato> perfilPorPersona = puedeVerPretension
                ? perfilesCandidato.findByPersonaIdIn(personasPorId.keySet().stream().toList())
                        .stream().collect(Collectors.toMap(PerfilCandidato::getPersonaId,
                                Function.identity(), (a, b) -> a))
                : Map.of();

        List<FilaRanking> filas = new ArrayList<>();
        int calificados = 0, enCurso = 0, fallidos = 0, conFina = 0;

        for (Postulacion p : suyas) {
            ColaCalificacionIa.Estado estado = estados.get(p.getId());
            String comoVa = estado == null ? "SIN_EMPEZAR" : estado.comoVa();
            if ("TERMINADA".equals(comoVa)) calificados++;
            else if ("EN_CURSO".equals(comoVa)) enCurso++;
            else if ("FALLIDA".equals(comoVa)) fallidos++;

            String pasada = estado == null ? null : estado.pasada();
            if ("FINA".equals(pasada)) conFina++;

            PerfilTalento perfil = perfilPorPostulacion.get(p.getId());
            List<HallazgoPerfil> suyos = perfil == null ? List.of()
                    : hallazgosPorPerfil.getOrDefault(perfil.getId(), List.of());

            Map<Long, NotaCriterio> notaPorCriterio = notasPorPostulacion
                    .getOrDefault(p.getId(), List.of()).stream()
                    .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity(),
                            (a, b) -> a));
            /*
              En la prueba del puesto reparte los cien puntos la propia rúbrica —`puntos` de
              cada criterio—, no `peso_criterio`, que solo tiene filas para los ocho globales
              del CV y aquí devolvería nulo en todas. Es además el mismo número que ya usa la
              ficha para decir «lo que más pesa»: si divergieran, la tabla y el párrafo de
              debajo se contradirían en la misma pantalla.
            */
            /*
              ⚠️ **En la etapa técnica las columnas NUNCA son las del currículum**, ni
              siquiera cuando esta fila no tiene rúbrica que enseñar. Quien aún no ha
              rendido —o rinde el cuestionario técnico, que no tiene rúbrica— sale sin
              criterios, y eso es un hueco honesto: caer a los ocho del CV pintaría ocho
              columnas del currículum bajo una cabecera que dice «prueba».
            */
            List<Criterio> suRubrica = laTecnica
                    ? rubricaPorPostulacion.getOrDefault(p.getId(), List.of())
                    : delCurriculum;
            /*
              El peso: en la prueba lo reparte la propia rúbrica —los `puntos` de cada
              criterio, que suman 100—, y en el currículum `peso_criterio`, que solo tiene
              filas para los ocho globales y aquí devolvería nulo en todas. Los `puntos` son
              además el mismo número con el que la ficha dice «lo que más pesa»: si
              divergieran, la tabla y el párrafo de debajo se contradirían en la misma
              pantalla.
            */
            List<NotaCriterioResponse> notas = suRubrica.stream()
                    .map(c -> pintarNota(c, notaPorCriterio.get(c.getId()),
                            laTecnica ? c.getPuntos() : pesos.get(c.getId())))
                    .toList();

            Usuario usuario = usuariosPorId.get(p.getUsuarioId());
            Persona persona = usuario == null ? null : personasPorId.get(usuario.getPersonaId());
            String ciudadCodigo = persona == null ? null : persona.getCiudadUbigeo();
            PerfilCandidato suPerfil = persona == null ? null
                    : perfilPorPersona.get(persona.getId());

            filas.add(new FilaRanking(
                    0,
                    p.getId(),
                    p.getUuid().toString(),
                    nombreDe(persona),
                    usuario == null ? null : usuario.getCorreo(),
                    p.getEstadoCodigo(),
                    nombreEstado.getOrDefault(p.getEstadoCodigo(), p.getEstadoCodigo()),
                    comoVa,
                    pasada,
                    nombreDelArchivo(cvPorPostulacion.get(p.getId()), archivosPorId),
                    pintarDatos(fichas.get(p.getId())),
                    p.getGrupoPrioridad(),
                    etapa(etapaPorPostulacion.get(p.getId())),
                    notaCurriculum(notaPorCriterio.values(), pesos, idsDelCurriculum),
                    perfil == null ? null : perfil.getAdecuacion(),
                    perfil == null ? null : perfil.getPotencial(),
                    perfil == null ? null : perfil.getAltoRendimiento(),
                    perfil == null ? null : perfil.getConfianzaEvidencia(),
                    perfil == null ? null : perfil.getResumen(),
                    (int) suyos.stream().filter(h -> "RIESGO_CRITICO".equals(h.getTipo())).count(),
                    (int) suyos.stream().filter(h -> "FORTALEZA".equals(h.getTipo())).count(),
                    alertasPorPostulacion.getOrDefault(p.getId(), 0L).intValue(),
                    perfil == null ? null : perfil.getActualizadoEn(),
                    notas,
                    // El null se pregunta aquí y no dentro del mapa: un Map.of() vacío
                    // —el de la tanda en la que nadie declaró ciudad— revienta al
                    // buscarle una clave nula, y sin ciudad es el estado normal de quien
                    // creó su cuenta antes de que se pidiera.
                    ciudadCodigo == null ? null : ciudades.get(ciudadCodigo),
                    ciudadCodigo,
                    suPerfil == null ? null : suPerfil.getPretensionMin(),
                    suPerfil == null ? null : suPerfil.getPretensionMax(),
                    suPerfil == null ? null : suPerfil.getPretensionMoneda()));
        }

        filas.sort(Comparator
                .comparingInt((FilaRanking f) -> posicionDe(f.grupoPrioridad()))
                .thenComparing(FilaRanking::notaEtapa,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FilaRanking::candidato,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        // El número de la fila se pone al final, cuando ya están ordenadas: es la posición
        // en la tanda y no un dato del candidato.
        //
        // Copiar campo a campo es lo que exige un record para cambiar uno solo, y es
        // frágil: dos campos seguidos del mismo tipo intercambiados compilan sin una
        // queja. Por eso ciudad y ciudadCodigo —los dos String y vecinos— van comprobados
        // en su propia prueba, sobre una fila YA numerada.
        List<FilaRanking> numeradas = new ArrayList<>(filas.size());
        for (int i = 0; i < filas.size(); i++) {
            FilaRanking f = filas.get(i);
            numeradas.add(new FilaRanking(i + 1, f.postulacionId(), f.uuid(), f.candidato(),
                    f.correo(), f.estado(), f.estadoNombre(), f.estadoCalificacion(),
                    f.pasada(), f.archivoNombre(), f.datos(),
                    f.grupoPrioridad(), f.notaEtapa(), f.notaCurriculum(), f.adecuacion(),
                    f.potencial(), f.altoRendimiento(), f.confianzaEvidencia(), f.resumen(),
                    f.riesgosCriticos(), f.fortalezas(), f.alertas(), f.actualizadoEn(),
                    f.notasCriterio(),
                    f.ciudad(), f.ciudadCodigo(),
                    f.pretensionMin(), f.pretensionMax(), f.pretensionMoneda()));
        }

        return new RankingVacante(vacanteId, vacante.getTitulo(),
                puesto == null ? null : puesto.getNombre(),
                puesto == null ? null : puesto.getNivelPuestoCodigo(),
                numeradas.size(), conFina, calificados, enCurso, fallidos,
                puedeVerPretension, numeradas);
    }

    /**
     * Cómo se llamaba el archivo que subió.
     *
     * <p>No se sirve el currículum desde aquí: el nombre basta para encontrarlo donde
     * viva —la carpeta compartida del equipo—, y así el archivo no tiene que salir por un
     * segundo sitio con su propio control de acceso.
     */
    private String nombreDelArchivo(Long postulacionId) {
        return cvs.findByPostulacionId(postulacionId)
                .map(Cv::getArchivoOriginalId)
                .flatMap(archivos::findById)
                .map(Archivo::getNombreOriginal)
                .orElse(null);
    }

    /** Lo mismo, pero con lo de la tanda ya traído: aquí dentro no se toca la base. */
    private String nombreDelArchivo(Cv cv, Map<Long, Archivo> archivosPorId) {
        if (cv == null || cv.getArchivoOriginalId() == null) return null;
        Archivo archivo = archivosPorId.get(cv.getArchivoOriginalId());
        return archivo == null ? null : archivo.getNombreOriginal();
    }

    private BigDecimal etapa(NotaEtapa nota) {
        return nota == null ? null : nota.getPuntaje();
    }

    /** Indexa por la postulación a la que pertenece cada fila. */
    private <T> Map<Long, T> porPostulacion(List<T> filas, Function<T, Long> deQuien) {
        return filas.stream().collect(Collectors.toMap(deQuien, Function.identity(), (a, b) -> a));
    }

    /** Indexa por el id propio de la fila. */
    private <T> Map<Long, T> porId(Iterable<T> filas, Function<T, Long> id) {
        Map<Long, T> salida = new java.util.HashMap<>();
        filas.forEach(f -> salida.putIfAbsent(id.apply(f), f));
        return salida;
    }

    /** La ficha del candidato, o nada si el agente que la saca todavía no ha corrido. */
    private DatosCandidato pintarDatos(DatoCv d) {
        return d == null ? null : new DatosCandidato(d.getNombre(), d.getEmail(), d.getTelefono(),
                d.getPerfilResumen(), d.getHabilidades(), d.getExperienciaMesesTotal(),
                d.getUltimoPuesto(), d.getUltimaEmpresa(), d.getEducacionMaxima());
    }

    /**
     * La nota del currículum sobre 100: cada criterio por su peso del nivel.
     *
     * <p>Se divide entre los pesos de los criterios que <b>sí</b> tienen nota. Si la IA no
     * pudo puntuar uno, lo justo es repartir su peso entre los demás, no restarlo: un hueco
     * no es un cero.
     */
    /**
     * La nota del currículum de una fila del ranking.
     *
     * <p><b>Solo suma los criterios del currículum</b>, y hay que decirlo explícitamente
     * porque la versión de pesos ya no es solo suya: desde que existen la simulación y la
     * validación, la misma versión trae también los diez criterios de una y los nueve de la
     * otra. Sumar «todo lo que tenga peso» hacía que esta columna cambiara en cuanto un
     * facilitador calificaba una simulación, y dos currículums idénticos mostraban notas
     * distintas sin que nadie hubiera tocado un currículum.
     */
    private BigDecimal notaCurriculum(java.util.Collection<NotaCriterio> notas,
                                      Map<Long, BigDecimal> pesos,
                                      Set<Long> idsDelCurriculum) {
        BigDecimal suma = BigDecimal.ZERO;
        BigDecimal pesoTotal = BigDecimal.ZERO;
        for (NotaCriterio nota : notas) {
            if (!idsDelCurriculum.contains(nota.getCriterioId())) continue;
            BigDecimal peso = pesos.get(nota.getCriterioId());
            if (peso == null || nota.getPuntaje() == null) continue;
            suma = suma.add(nota.getPuntaje().multiply(peso));
            pesoTotal = pesoTotal.add(peso);
        }
        return pesoTotal.compareTo(BigDecimal.ZERO) == 0
                ? null
                : suma.divide(pesoTotal, 2, RoundingMode.HALF_UP);
    }

    /**
     * Dónde va un grupo en el orden. Sin calificar todavía va al final, no arriba.
     *
     * <p>La lista es inmutable y {@code indexOf(null)} en una de esas revienta, así que el
     * nulo se atiende antes: es el caso normal mientras la IA no ha leído a nadie.
     */
    private int posicionDe(String grupo) {
        int donde = grupo == null ? -1 : ORDEN_GRUPO.indexOf(grupo);
        return donde < 0 ? ORDEN_GRUPO.size() : donde;
    }

    private String nombreDe(Persona persona) {
        if (persona == null || persona.getAnonimizadoEn() != null) return "(anonimizado)";
        return ((persona.getNombre() == null ? "" : persona.getNombre()) + " "
                + (persona.getApellidos() == null ? "" : persona.getApellidos())).trim();
    }

    /**
     * La vacante, si quien pregunta puede verla <b>con el permiso que está usando</b>.
     *
     * <p>El permiso llega por parámetro y no está escrito aquí dentro a propósito. Antes
     * siempre se miraba el alcance de {@code ver_embudo}, aunque el endpoint exigiera otro:
     * un rol al que se le diera {@code ajustar_nota} limitado a sus vacantes, pero
     * {@code ver_embudo} sin límite, podía lanzar una criba sobre una convocatoria ajena. Hoy
     * ningún rol sembrado tiene esa forma, pero los roles se configuran desde el panel y esto
     * dejaría de ser cierto sin que nadie tocara una línea de código.
     */
    private Vacante vacanteVisible(ContextoUsuario quien, Long vacanteId, String permiso) {
        // El findById + filter por organización de antes era findByIdAndOrganizacionId escrito
        // a mano: el guardián usa la consulta derivada, que dice lo mismo en una sola pasada.
        Vacante vacante = alcanceVacante.laVacanteVisible(quien, vacanteId, permiso);
        return vacante;
    }

    @Override
    @Transactional
    public void reemplazarCv(ContextoUsuario quien, Long postulacionId, MultipartFile archivo) {
        laVisible(quien, postulacionId, "ajustar_nota");
        Cv curriculum = cvs.findByPostulacionId(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Currículum", "postulación", postulacionId));

        Archivo guardado = almacen.guardar(quien.organizacionId(), archivo);
        curriculum.setArchivoOriginalId(guardado.getId());
        // El texto viejo es de otro archivo: si se dejara, la IA calificaría el currículum
        // anterior creyendo que lee el nuevo. Se borra y se vuelve a extraer al calificar.
        curriculum.setTextoExtraido(null);
        curriculum.setTextoAnonimizado(null);
        curriculum.setAnonimizadoEn(null);
        cvs.save(curriculum);

        // La ficha de datos —teléfono, último puesto, años de experiencia— salió del
        // currículum anterior. Se borra para que la próxima calificación la vuelva a sacar:
        // la cola se salta ese paso cuando la ficha ya existe, así que dejarla puesta
        // enseñaría los datos del archivo viejo debajo del nuevo.
        datosCv.findByPostulacionId(postulacionId).ifPresent(datosCv::delete);

        auditoria.registrar(quien.organizacionId(), quien, "reemplazar_cv",
                "cv", curriculum.getId(), null,
                Map.of("archivoOriginalId", String.valueOf(guardado.getId())), null);
    }

    // ============ Apoyo ============

    /**
     * Los estados de los que ya no se sale: contratado, no continúa y cerrada.
     *
     * <p>No están escritos a mano: se leen de la tabla, que es quien manda. Si Renaser añade
     * un estado final desde una migración, esto lo respeta sin que nadie lo recuerde.
     */
    private Set<String> cerrados() {
        return estados.findAllByOrderByOrden().stream()
                .filter(EstadoPostulacion::isEsFinal)
                .map(EstadoPostulacion::getCodigo)
                .collect(Collectors.toSet());
    }

    /**
     * Con qué rúbrica se midió a cada candidato de esta tanda.
     *
     * <p>Los criterios de la prueba del puesto <b>son de su plantilla</b>, no globales: dos
     * vacantes traen columnas distintas, y por eso la cabecera de la tabla se arma de lo que
     * llegue en las filas y nunca de una lista escrita a mano.
     *
     * <p>Dos consultas para la tanda entera —los intentos, y las rúbricas de las versiones
     * que aparezcan—, nunca una por fila. Es la misma regla por la que existe
     * {@code ciudadesDe}: el bucle del ranking no toca la base.
     *
     * <p>Tres caminos devuelven el mapa vacío, y ninguno es un error:
     * <ul>
     *   <li><b>El cuestionario técnico</b> no tiene rúbrica: se califica pregunta a pregunta
     *       contando criterios, no repartiendo cien puntos entre unos apartados. Lo mismo que
     *       ya hace {@code ServicioCalificacionPruebaImpl.laRubricaDe}.</li>
     *   <li><b>Nadie ha abierto todavía su prueba</b>, que es lo normal en una tanda que
     *       sigue en perfil integral.</li>
     *   <li><b>Una versión sin criterios</b>, que es una plantilla a medio escribir.</li>
     * </ul>
     *
     * <p>Y quien no salga en el mapa se queda sin columnas, no con las del currículum.
     */
    private Map<Long, List<Criterio>> rubricasDeLaPrueba(Vacante vacante, List<Long> ids) {
        if (ids.isEmpty()
                || CUESTIONARIO_TECNICO.equals(vacante.getInstrumentoEtapaTecnica())) {
            return Map.of();
        }

        Map<Long, Long> versionPorPostulacion = intentos.findByPostulacionIdIn(ids).stream()
                .filter(i -> i.getVersionPlantillaPruebaId() != null)
                .collect(Collectors.toMap(IntentoPrueba::getPostulacionId,
                        IntentoPrueba::getVersionPlantillaPruebaId, (a, b) -> a));
        if (versionPorPostulacion.isEmpty()) return Map.of();

        // Agrupadas por versión, con el orden en que se escribió la rúbrica: es el orden en
        // que la persona que calificó las vio, y el que tienen que llevar las columnas.
        Map<Long, List<Criterio>> porVersion = criterios
                .findByVersionPlantillaPruebaIdInOrderByOrden(
                        Set.copyOf(versionPorPostulacion.values()))
                .stream()
                .collect(Collectors.groupingBy(Criterio::getVersionPlantillaPruebaId));

        Map<Long, List<Criterio>> porPostulacion = new java.util.HashMap<>();
        versionPorPostulacion.forEach((postulacionId, version) -> {
            List<Criterio> rubrica = porVersion.get(version);
            if (rubrica != null && !rubrica.isEmpty()) porPostulacion.put(postulacionId, rubrica);
        });
        return porPostulacion;
    }

    /**
     * Un criterio con su nota, listo para la tabla.
     *
     * <p>⚠️ <b>El peso llega resuelto y no se saca de un mapa aquí</b>: en el currículum sale
     * de {@code peso_criterio} —la versión de pesos de la vacante y el nivel de su puesto— y
     * en la prueba del puesto de los {@code puntos} de la propia rúbrica. Son dos orígenes
     * distintos para el mismo hueco, y el que decide es quien llama.
     */
    private NotaCriterioResponse pintarNota(Criterio criterio, NotaCriterio nota,
                                           BigDecimal peso) {
        return new NotaCriterioResponse(
                criterio.getNombre(),
                // El nombre corto, para rotular una columna donde el largo no cabe.
                criterio.getCodigo(),
                nota == null ? null : nota.getPuntaje(),
                criterio.getPuntos(),
                peso,
                nota == null ? null : nota.getExplicacion(),
                nota == null ? null : nota.getOrigen(),
                // Va tal cual: 0 a 100, la misma escala del puntaje. Ver el comentario de
                // NotaCriterioResponse.
                nota == null ? null : nota.getConfianza(),
                nota == null ? null : nota.getMotivoAjuste());
    }

    /**
     * Cómo se llama la ciudad de cada una de esas personas: {@code «Arequipa — Camaná»}.
     *
     * <p>Devuelve el mapa código → nombre pintado, no fila a fila, porque el bucle del
     * ranking no puede tocar la base: son dos consultas para la tanda entera —las
     * provincias que aparecen, y los departamentos de esas provincias— por muchos
     * candidatos que haya.
     *
     * <p>El departamento va delante porque hay provincias homónimas —Lima y Lima, San
     * Martín y San Martín— y una columna que solo dijera «Lima» no distinguiría la capital
     * de la provincia de Cañete. {@code EXT} sale solo: «Fuera del Perú» no cuelga de
     * ningún departamento y añadirle un guion sería inventarse uno.
     */
    private Map<String, String> ciudadesDe(java.util.Collection<Persona> quienes) {
        List<String> codigos = quienes.stream()
                .map(Persona::getCiudadUbigeo)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (codigos.isEmpty()) return Map.of();

        List<Ubigeo> provincias = ubigeos.findAllById(codigos);
        Map<String, String> departamentos = ubigeos.findAllById(provincias.stream()
                        .map(Ubigeo::getPadre).filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Ubigeo::getCodigo, Ubigeo::getNombre, (a, b) -> a));

        Map<String, String> pintadas = new java.util.HashMap<>();
        for (Ubigeo u : provincias) {
            String departamento = u.getPadre() == null ? null : departamentos.get(u.getPadre());
            pintadas.put(u.getCodigo(),
                    departamento == null ? u.getNombre() : departamento + " — " + u.getNombre());
        }
        return pintadas;
    }

    /**
     * Cuanto pesa cada criterio para esta postulacion.
     *
     * <p>Los pesos son los de la version de la vacante y del nivel de su puesto, no los de
     * la ultima version publicada: una nota de hace seis meses tiene que seguir
     * explicandose con los pesos con que se calculo.
     */
    private Map<Long, BigDecimal> pesosDe(Postulacion postulacion) {
        return vacantes.findById(postulacion.getVacanteId())
                .flatMap(v -> puestos.findById(v.getPuestoId())
                        .map(pu -> pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(
                                v.getVersionPesosId(), pu.getNivelPuestoCodigo())))
                .map(lista -> lista.stream().collect(Collectors.toMap(
                        PesoCriterio::getCriterioId, PesoCriterio::getPeso, (a, b) -> a)))
                .orElseGet(Map::of);
    }

    /**
     * En qué punto está la calificación, dicho para que una pantalla sepa qué pintar.
     *
     * <p><b>La señal son los trabajos de la cola, no el estado de la postulación.</b> Ese
     * estado solo pasa a {@code PERFIL_CALIFICANDO} cuando el candidato entrega su
     * evaluación, así que una calificación pedida desde el panel corre sin tocarlo: mirar la
     * postulación decía «no hay nada» con los tres agentes trabajando, y quien preguntaba
     * dejaba de esperar antes de tiempo.
     *
     * <p>Un trabajo vivo manda sobre el retrato ya guardado: si se volvió a calificar, el
     * perfil que hay es el viejo y darlo por bueno sería mentir.
     */
    private String estadoDeLaCalificacion(Postulacion postulacion, PerfilTalento perfil) {
        if (postulacion.getEvaluacionId() == null) {
            return "SIN_EVALUACION";
        }
        String segunLaCola = cola.comoVa(postulacion.getId());
        if ("EN_CURSO".equals(segunLaCola) || "FALLIDA".equals(segunLaCola)) {
            return segunLaCola;
        }
        // La cola dice que acabó: solo es TERMINADA si además dejó el retrato escrito.
        if ("TERMINADA".equals(segunLaCola)) {
            return perfil == null ? "FALLIDA" : "TERMINADA";
        }
        return perfil != null ? "TERMINADA" : "PENDIENTE";
    }

    /** La postulación, comprobando organización y alcance del permiso. */
    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId, String permiso) {
        return alcanceVacante.laPostulacionVisible(quien, postulacionId, permiso);
    }
}
