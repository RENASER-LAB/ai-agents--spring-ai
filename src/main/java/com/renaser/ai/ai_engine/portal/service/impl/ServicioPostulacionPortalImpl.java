package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.consentimiento.entity.Consentimiento;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioEvaluacion;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.MiPostulacion;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.MiPostulacionDetalle;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.PasoHistorial;
import com.renaser.ai.ai_engine.portal.service.ServicioPostulacionPortal;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.EnlaceCv;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EnlaceCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.RequisitoObjetivo;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La postulación: ver {@link ServicioPostulacionPortal}. Postular toca de todo a
 * propósito —la postulación, su transición, el consentimiento firmado, el CV archivado,
 * el perfil y el correo—, porque todo eso nace junto y en la misma transacción; de ahí
 * que este servicio sea el ancho de los tres del portal.
 */
@Service
@RequiredArgsConstructor
public class ServicioPostulacionPortalImpl implements ServicioPostulacionPortal {

    private final OrganizacionRepository organizaciones;
    private final PersonaRepository personas;
    private final UsuarioRepository usuarios;
    private final ConsentimientoRepository consentimientos;
    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;
    private final RequisitoObjetivoRepository requisitos;
    private final ServicioEvaluacion evaluaciones;
    private final PostulacionRepository postulaciones;
    private final TransicionEstadoRepository transiciones;
    private final EstadoPostulacionRepository estados;
    private final CvRepository cvs;
    private final EnlaceCvRepository enlaces;
    private final MaquinaEstados maquina;
    private final com.renaser.ai.ai_engine.perfil.service.ServicioPropuestaPerfil propuestaPerfil;
    private final com.renaser.ai.ai_engine.perfil.service.ServicioLecturaCv lecturaCv;
    private final AlmacenArchivos almacen;
    private final ServicioCorreo correo;
    private final TextoProcesoPublicado textoProceso;

    @Override
    @Transactional
    public UUID postular(ContextoUsuario quien, Long vacanteId, MultipartFile cv,
                         String resultadoOrgulloso, String portafolio, String linkedin, String github,
                         List<Long> requisitosConfirmados, Boolean aceptaTratamiento,
                         String ip, String userAgent) {
        // Sin aceptar el texto de la empresa no hay postulación, igual que sin aceptar el
        // de la plataforma no hay cuenta. Es la capa de la pieza D: al postular consiente
        // con ESA empresa, que es quien va a tratar sus datos en este proceso.
        if (!Boolean.TRUE.equals(aceptaTratamiento)) {
            throw new IllegalArgumentException("Hay que aceptar el tratamiento de datos de la "
                    + "empresa de esta vacante para postular");
        }
        // El candidato postula a la vacante de cualquier empresa: la vacante se busca en
        // el tablón entero, no en la organización del candidato (que es la plataforma).
        // Con el mismo colador del tablón: la vacante de una empresa suspendida no
        // recibe postulaciones ni con el id en la mano (pieza F) — esconderla de la
        // lista y aceptarle un POST directo sería un tablón de mentira.
        Vacante vacante = vacantes.findById(vacanteId)
                .filter(v -> "PUBLICADA".equals(v.getEstado()))
                .filter(v -> organizaciones.findById(v.getOrganizacionId())
                        .map(Organizacion::isEsActiva).orElse(false))
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
        if (postulaciones.existsByUsuarioIdAndVacanteId(quien.usuarioId(), vacanteId)) {
            throw new IllegalStateException("Ya postulaste a esta vacante");
        }
        if (resultadoOrgulloso == null || resultadoOrgulloso.isBlank()) {
            throw new IllegalArgumentException("Cuéntanos un resultado del que te sientas orgulloso: es obligatorio");
        }

        // La postulación nace en la organización DE LA VACANTE, no en la del candidato:
        // es lo que hace que el panel de cada empresa vea a sus candidatos y que el
        // aislamiento signifique algo. El mismo criterio arrastra todo lo que el panel de
        // la empresa debe ver: el CV archivado, la evaluación y sus avisos.
        Long organizacionDeLaVacante = vacante.getOrganizacionId();

        // Nace en POSTULADA: el único tramo donde el sistema decide solo, y únicamente
        // contra los requisitos objetivos configurados de antemano
        Postulacion postulacion = postulaciones.save(Postulacion.builder()
                .organizacionId(organizacionDeLaVacante)
                .uuid(UUID.randomUUID())
                .usuarioId(quien.usuarioId())
                .vacanteId(vacanteId)
                .estadoCodigo("POSTULADA")
                .rondasEvidenciaUsadas(0)
                .movidoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());
        transiciones.save(TransicionEstado.builder()
                .postulacionId(postulacion.getId())
                .estadoNuevoCodigo("POSTULADA")
                .esSistema(true).esPorLote(false)
                .ocurridaEn(Instant.now()).creadoEn(Instant.now())
                .build());

        // El registro firmado del texto de LA EMPRESA DE LA VACANTE, amarrado a esta
        // postulación: versión exacta, IP y navegador, como el de la cuenta. Si la
        // empresa no tiene texto publicado —no debería pasar: publicar la vacante lo
        // exige—, el buscador del texto PROCESO corta con un 409 claro antes de guardar
        // nada más.
        Persona persona = personas.findById(quien.personaId()).orElse(null);
        consentimientos.save(Consentimiento.builder()
                .personaId(quien.personaId())
                .textoConsentimientoId(textoProceso.de(organizacionDeLaVacante).getId())
                .postulacionId(postulacion.getId())
                .nombreRegistrado(persona == null ? null
                        : (persona.getNombre() + " " + persona.getApellidos()).trim())
                .aceptadoEn(Instant.now())
                .ip(ip)
                .userAgent(userAgent)
                .creadoEn(Instant.now())
                .build());

        Archivo archivo = almacen.guardar(organizacionDeLaVacante, cv);
        Cv curriculum = cvs.save(Cv.builder()
                .postulacionId(postulacion.getId())
                .archivoOriginalId(archivo.getId())
                .resultadoOrgulloso(resultadoOrgulloso)
                .creadoEn(Instant.now())
                .build());
        guardarEnlace(curriculum.getId(), portafolio, "PORTAFOLIO");
        guardarEnlace(curriculum.getId(), linkedin, "OTRO");
        guardarEnlace(curriculum.getId(), github, "REPOSITORIO");

        // El perfil del candidato se alimenta de lo que acaba de pasar: los enlaces del
        // formulario se le proponen, y el curriculum se manda a leer — o se reutiliza la
        // lectura ya pagada, si esta persona ya postulo con el mismo archivo (RF-161).
        //
        // Van DENTRO de la transaccion de postular, y eso es una decision con precio: la
        // lectura tiene que ver la postulacion recien insertada (sus trabajos y su ficha
        // apuntan a ella por clave foranea), asi que aislarla en una transaccion aparte la
        // dejaria sin nada a lo que apuntar. El precio es que un fallo de base aqui —una
        // carrera por el UNIQUE del perfil entre dos postulaciones a la vez— si tumbaria la
        // postulacion; el try/catch de dentro atrapa el error de logica, no ese.
        propuestaPerfil.proponerEnlaces(quien.personaId(), linkedin, github, portafolio);
        lecturaCv.trasPostular(quien.personaId(), postulacion.getId());

        Usuario usuario = usuarios.findById(quien.usuarioId()).orElseThrow();
        String nombre = persona == null ? "" : persona.getNombre();
        correo.enviar(organizacionDeLaVacante, usuario.getId(), usuario.getCorreo(), "POSTULACION_RECIBIDA",
                Map.of("nombre", nombre == null ? "" : nombre,
                       "vacante", vacante.getTitulo(),
                       "codigo", postulacion.getUuid().toString()));

        // Autodeclaración: el formulario preguntó por cada requisito activo y aquí llegan
        // los que el candidato confirmó. Cualquier requisito activo no confirmado detiene
        // la postulación, y la regla exacta queda escrita en la transición.
        List<RequisitoObjetivo> activos = requisitos.findByVacanteIdAndEsActivoTrue(vacanteId);
        Set<Long> confirmados = requisitosConfirmados == null
                ? Set.of() : new HashSet<>(requisitosConfirmados);
        List<RequisitoObjetivo> incumplidos = activos.stream()
                .filter(r -> !confirmados.contains(r.getId()))
                .toList();

        if (!incumplidos.isEmpty()) {
            String reglas = String.join(" · ", incumplidos.stream().map(RequisitoObjetivo::getRegla).toList());
            maquina.transicionar(postulacion, "NO_CONTINUA", null,
                    "Requisito objetivo no cumplido: " + reglas, true, false, "REQUISITO_OBJETIVO");
        } else if (!vacante.isAplicaEvaluacion()) {
            // Esta vacante no lleva banco de preguntas: no hay nada que el candidato deba
            // responder en el Perfil Integral. Va directo a la bandeja del equipo, que
            // decide a quién invitar a la prueba del puesto — su única evaluación.
            maquina.transicionar(postulacion, "PERFIL_POR_CONFIRMAR", null, null, true, false, null);
        } else {
            // Su evaluación se crea aquí, no cuando entre a responderla: así queda atada a la
            // versión del banco que estaba publicada el día que postuló. Sin esto la
            // postulación quedaría esperando algo que el candidato no tendría cómo hacer.
            Puesto puesto = puestos.findById(vacante.getPuestoId())
                    .orElseThrow(() -> new IllegalStateException(
                            "La vacante apunta a un puesto que no existe"));
            postulacion.setEvaluacionId(evaluaciones.crearAlPostular(
                    organizacionDeLaVacante, quien.usuarioId(),
                    vacante.getPlantillaEvaluacionId(), puesto.getNivelPuestoCodigo()));
            postulaciones.save(postulacion);

            maquina.transicionar(postulacion, "PERFIL_TURNO_CANDIDATO", null, null, true, false, null);
        }
        return postulacion.getUuid();
    }

    private void guardarEnlace(Long cvId, String url, String tipo) {
        if (url != null && !url.isBlank()) {
            enlaces.save(EnlaceCv.builder().cvId(cvId).url(url.trim()).tipo(tipo).creadoEn(Instant.now()).build());
        }
    }

    // ============ Mis postulaciones ============

    @Override
    public List<MiPostulacion> misPostulaciones(ContextoUsuario quien) {
        List<Postulacion> mias = postulaciones.findByUsuarioIdOrderByCreadoEnDesc(quien.usuarioId());

        // Son pocas —las de una sola persona—, pero el catálogo de estados son dieciocho filas
        // fijas y se estaba pidiendo una por postulación. Traerlo entero cuesta lo mismo que
        // pedir uno, y así la pantalla no crece en consultas con lo que se postule nadie.
        Map<String, String> nombreEstado = estados.findAllByOrderByOrden().stream()
                .collect(Collectors.toMap(EstadoPostulacion::getCodigo,
                        EstadoPostulacion::getNombre, (a, b) -> a));
        List<Long> vacanteIds = mias.stream().map(Postulacion::getVacanteId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Vacante> porVacante = vacantes.findAllById(vacanteIds).stream()
                .collect(Collectors.toMap(Vacante::getId, Function.identity()));
        // El nombre de cada empresa, por lo mismo que en el tablón: esta lista mezcla los
        // procesos del candidato en todas las empresas y cada uno dice de quién es.
        Map<Long, String> nombrePorOrganizacion = nombresDeOrganizacion(
                porVacante.values().stream().toList());

        return mias.stream()
                .map(p -> comoResumen(p, porVacante, nombreEstado, nombrePorOrganizacion))
                .toList();
    }

    @Override
    public MiPostulacionDetalle miPostulacion(ContextoUsuario quien, UUID uuid) {
        Postulacion postulacion = laMia(quien, uuid);
        List<PasoHistorial> historial = transiciones
                .findByPostulacionIdOrderByOcurridaEnAsc(postulacion.getId()).stream()
                .map(t -> new PasoHistorial(t.getEstadoAnteriorCodigo(), t.getEstadoNuevoCodigo(),
                        t.isEsSistema(), t.getOcurridaEn()))
                .toList();
        return new MiPostulacionDetalle(comoResumen(postulacion), historial);
    }

    @Override
    @Transactional
    public void retirar(ContextoUsuario quien, UUID uuid) {
        Postulacion postulacion = laMia(quien, uuid);
        EstadoPostulacion estado = estados.findById(postulacion.getEstadoCodigo()).orElseThrow();
        if (estado.isEsFinal()) {
            throw new IllegalStateException("Esta postulación ya terminó: no se puede retirar");
        }
        // Retirarse NO borra los datos: eso se pide aparte, y es otra cosa
        maquina.transicionar(postulacion, "CERRADA", quien,
                "El candidato retiró su postulación desde el portal", false, false, "RETIRO_CANDIDATO");
    }

    // ============ ayudas ============

    private Map<Long, String> nombresDeOrganizacion(List<Vacante> deVacantes) {
        // SIN filtrar por activa a propósito: lo usa «mis postulaciones», y el candidato
        // que ya está dentro de un proceso conserva su vista aunque suspendan a la
        // empresa — él no paga el problema comercial de nadie (pieza F).
        List<Long> ids = deVacantes.stream().map(Vacante::getOrganizacionId).distinct().toList();
        return organizaciones.findAllById(ids).stream()
                .collect(Collectors.toMap(Organizacion::getId, Organizacion::getNombre));
    }

    private Postulacion laMia(ContextoUsuario quien, UUID uuid) {
        return postulaciones.findByUuid(uuid)
                .filter(p -> p.getUsuarioId().equals(quien.usuarioId()))
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "código", uuid));
    }

    /** Una sola: la vacante y su estado se piden sueltos porque no hay tanda con la que ir. */
    private MiPostulacion comoResumen(Postulacion p) {
        Map<Long, Vacante> unaVacante = vacantes.findById(p.getVacanteId()).stream()
                .collect(Collectors.toMap(Vacante::getId, Function.identity()));
        Map<String, String> unEstado = estados.findById(p.getEstadoCodigo()).stream()
                .collect(Collectors.toMap(EstadoPostulacion::getCodigo, EstadoPostulacion::getNombre));
        return comoResumen(p, unaVacante, unEstado,
                nombresDeOrganizacion(unaVacante.values().stream().toList()));
    }

    private MiPostulacion comoResumen(Postulacion p, Map<Long, Vacante> porVacante,
                                      Map<String, String> nombrePorEstado,
                                      Map<Long, String> nombrePorOrganizacion) {
        Optional<Vacante> vacante = Optional.ofNullable(porVacante.get(p.getVacanteId()));
        String titulo = vacante.map(Vacante::getTitulo).orElse("");
        String empresa = vacante.map(Vacante::getOrganizacionId)
                .map(id -> nombrePorOrganizacion.getOrDefault(id, "")).orElse("");
        // Si el código no está en el catálogo se enseña el código, igual que antes: es feo,
        // pero deja ver qué estado es en vez de un hueco en blanco.
        String nombreEstado = nombrePorEstado.getOrDefault(p.getEstadoCodigo(), p.getEstadoCodigo());
        long dias = Duration.between(p.getMovidoEn(), Instant.now()).toDays();
        return new MiPostulacion(p.getUuid().toString(), titulo, empresa, p.getEstadoCodigo(),
                nombreEstado, p.getGrupoPrioridad(), dias, p.getCreadoEn());
    }
}
