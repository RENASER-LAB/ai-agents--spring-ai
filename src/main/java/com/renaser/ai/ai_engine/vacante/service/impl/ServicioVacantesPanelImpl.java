package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreoVacante;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoVacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.ServicioVacantesPanel;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.*;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.entity.*;
import com.renaser.ai.ai_engine.vacante.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ServicioVacantesPanelImpl implements ServicioVacantesPanel {

    /** Los dos instrumentos de la etapa técnica. Uno por vacante, nunca los dos (V43). */
    public static final String PLANTILLA = "PLANTILLA";
    public static final String CUESTIONARIO_TECNICO = "CUESTIONARIO_TECNICO";

    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;
    private final RequisitoObjetivoRepository requisitos;
    private final SolicitudTalentoRepository solicitudes;
    private final VersionPesosRepository versionesPesos;
    private final PlantillaEvaluacionRepository plantillas;
    private final VersionPlantillaPruebaRepository versionesPrueba;
    private final PlantillaPruebaRepository plantillasPrueba;
    private final PlantillaCorreoRepository plantillasCorreo;
    private final PlantillaCorreoVacanteRepository plantillasPorVacante;
    private final TextoConsentimientoRepository textosConsentimiento;
    private final IntentoPruebaRepository intentos;
    private final VersionBancoRepository versionesBanco;
    private final ServicioAuditoria auditoria;
    private final DuenoDelInstrumento dueno;
    private final com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository postulaciones;

    // ============ Puestos ============

    @Override
    @Transactional
    public Long crearPuesto(ContextoUsuario quien, GuardarPuesto datos) {
        Puesto puesto = puestos.save(Puesto.builder()
                .organizacionId(quien.organizacionId())
                .codigo(datos.codigo())
                .nombre(datos.nombre())
                .nivelPuestoCodigo(datos.nivelPuestoCodigo())
                .familiaCodigo(datos.familiaCodigo())
                .esActivo(true)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_puesto",
                "puesto", puesto.getId(), null, Map.of("codigo", datos.codigo()), null);
        return puesto.getId();
    }

    @Override
    public List<PuestoResponse> listarPuestos(ContextoUsuario quien) {
        return puestos.findByOrganizacionIdAndEsActivoTrueOrderByNombre(quien.organizacionId()).stream()
                .map(p -> new PuestoResponse(p.getId(), p.getCodigo(), p.getNombre(),
                        p.getNivelPuestoCodigo(), p.getFamiliaCodigo()))
                .toList();
    }

    // ============ Vacantes ============

    @Override
    @Transactional
    public Long crear(ContextoUsuario quien, GuardarVacante datos) {
        SolicitudTalento solicitud = solicitudes
                .findByIdAndOrganizacionId(datos.solicitudTalentoId(), quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de Talento", "id",
                        datos.solicitudTalentoId()));
        // Toda vacante cuelga de una solicitud aprobada por Dirección: es la regla del
        // cliente y se decidió incluirla en el MVP
        if (!"ABIERTA".equals(solicitud.getEstado())) {
            throw new IllegalStateException(
                    "La solicitud tiene que estar aprobada (ABIERTA) antes de crear la vacante; está "
                            + solicitud.getEstado());
        }
        puestos.findByIdAndOrganizacionId(datos.puestoId(), quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Puesto", "id", datos.puestoId()));

        // La versión de pesos publicada vigente. Elegir otra es de Dirección (hito 2).
        // De quién son los pesos lo contesta el resolutor: los de la plataforma mientras
        // la empresa no personalice, los suyos en cuanto encienda la bandera.
        VersionPesos pesos = versionesPesos
                .findFirstByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(
                        dueno.duenoDe(quien.organizacionId(), Instrumento.PESOS), "PUBLICADA")
                .orElseThrow(() -> new IllegalStateException("No hay una versión de pesos publicada"));

        Vacante vacante = vacantes.save(Vacante.builder()
                .organizacionId(quien.organizacionId())
                .solicitudTalentoId(solicitud.getId())
                .puestoId(datos.puestoId())
                .titulo(datos.titulo())
                .descripcion(datos.descripcion())
                .proposito(datos.proposito())
                .responsabilidades(datos.responsabilidades())
                .requisitos(datos.requisitos())
                .modalidad(datos.modalidad())
                .horario(datos.horario())
                .ubicacion(datos.ubicacion())
                .compensacionPublica(datos.compensacionPublica())
                .tipoCierre(datos.tipoCierre())
                .plazas(datos.plazas())
                .abreEn(datos.abreEn())
                .cierraEn(datos.cierraEn())
                .estado("BORRADOR")
                .versionPesosId(pesos.getId())
                .responsableUsuarioId(datos.responsableUsuarioId())
                .creadoEn(Instant.now())
                .build());

        solicitud.setEstado("CON_VACANTE");
        solicitudes.save(solicitud);

        auditoria.registrar(quien.organizacionId(), quien, "crear_vacante",
                "vacante", vacante.getId(), null,
                Map.of("titulo", datos.titulo(), "solicitud", solicitud.getId()), null);
        return vacante.getId();
    }

    @Override
    @Transactional
    public void editar(ContextoUsuario quien, Long id, GuardarVacante datos) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        if ("CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada no se edita");
        }
        Map<String, Object> anterior = Map.of("titulo", vacante.getTitulo(),
                "descripcion", vacante.getDescripcion());
        vacante.setTitulo(datos.titulo());
        vacante.setDescripcion(datos.descripcion());
        vacante.setProposito(datos.proposito());
        vacante.setResponsabilidades(datos.responsabilidades());
        vacante.setRequisitos(datos.requisitos());
        vacante.setModalidad(datos.modalidad());
        vacante.setHorario(datos.horario());
        vacante.setUbicacion(datos.ubicacion());
        vacante.setCompensacionPublica(datos.compensacionPublica());
        vacante.setTipoCierre(datos.tipoCierre());
        vacante.setPlazas(datos.plazas());
        vacante.setAbreEn(datos.abreEn());
        vacante.setCierraEn(datos.cierraEn());
        vacante.setResponsableUsuarioId(datos.responsableUsuarioId());
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "editar_vacante",
                "vacante", id, anterior, Map.of("titulo", datos.titulo()), null);
    }

    @Override
    public List<VacantePanel> listar(ContextoUsuario quien) {
        return vacantes.findByOrganizacionIdOrderByCreadoEnDesc(quien.organizacionId()).stream()
                .map(this::comoPanel)
                .toList();
    }

    @Override
    public VacantePanel detalle(ContextoUsuario quien, Long id) {
        return comoPanel(laDeLaOrganizacion(quien, id));
    }

    // ============ Requisitos objetivos ============

    @Override
    public List<RequisitoPanel> requisitos(ContextoUsuario quien, Long vacanteId) {
        laDeLaOrganizacion(quien, vacanteId);
        return requisitos.findByVacanteId(vacanteId).stream()
                .map(r -> new RequisitoPanel(r.getId(), r.getDescripcion(), r.getRegla(), r.isEsActivo()))
                .toList();
    }

    @Override
    @Transactional
    public Long agregarRequisito(ContextoUsuario quien, Long vacanteId, GuardarRequisito datos) {
        laDeLaOrganizacion(quien, vacanteId);
        RequisitoObjetivo requisito = requisitos.save(RequisitoObjetivo.builder()
                .vacanteId(vacanteId)
                .descripcion(datos.descripcion())
                .regla(datos.regla())
                .esActivo(true)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "definir_requisito_objetivo",
                "requisito_objetivo", requisito.getId(), null,
                Map.of("regla", datos.regla(), "vacante", vacanteId), null);
        return requisito.getId();
    }

    @Override
    @Transactional
    public void desactivarRequisito(ContextoUsuario quien, Long vacanteId, Long requisitoId) {
        laDeLaOrganizacion(quien, vacanteId);
        RequisitoObjetivo requisito = requisitos.findById(requisitoId)
                .filter(r -> r.getVacanteId().equals(vacanteId))
                .orElseThrow(() -> new ResourceNotFoundException("Requisito objetivo", "id", requisitoId));
        // No se borra: se desactiva. Las postulaciones que ya detuvo siguen explicadas.
        requisito.setEsActivo(false);
        requisitos.save(requisito);
        auditoria.registrar(quien.organizacionId(), quien, "desactivar_requisito_objetivo",
                "requisito_objetivo", requisitoId, Map.of("esActivo", true), Map.of("esActivo", false), null);
    }

    // ============ Publicar y cerrar ============

    @Override
    @Transactional
    public void publicar(ContextoUsuario quien, Long id) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        if (!"BORRADOR".equals(vacante.getEstado())) {
            throw new IllegalStateException("Solo se publica una vacante en borrador; está " + vacante.getEstado());
        }
        // Sin banco publicado del nivel no hay con qué armar la evaluación de quien postule.
        // El error tiene que salir aquí, al publicar, y no en la cara del primer candidato.
        // Una vacante con la evaluación apagada no lo necesita: su única evaluación es la
        // prueba.
        //
        // ⚠️ Antes esta guarda pedía la PLANTILLA, y pedía lo que no hacía falta: desde que
        // se retiraron las cuotas, la plantilla no decide qué preguntas caen —solo el tiempo
        // y la vigencia— y hay una publicada por nivel, así que elegirla era una pregunta con
        // una sola respuesta legal. Lo que de verdad falta cuando no hay examen posible es el
        // banco, y ese error salía en crearAlPostular, o sea encima del candidato.
        //
        // Con la evaluación apagada no hace falta: su única evaluación es la prueba.
        if (vacante.isAplicaEvaluacion()) {
            exigirBancoDelNivel(vacante);
        }
        // "Es obligatoria para todo puesto" (RF-73), pero desde el ciclo 2 hay DOS formas de
        // cumplirlo y la vacante dice cuál usa: la prueba del puesto de siempre, o el
        // cuestionario técnico que el dueño aprobó para ella. Lo que no se puede es publicar
        // sin ninguna de las dos, porque entonces el candidato llega a su etapa técnica y no
        // encuentra nada que rendir.
        exigirInstrumentoTecnico(vacante);
        // El requisito del día uno de la pieza A: sin texto legal publicado con SU nombre,
        // la empresa no recibe candidatos — al postular se firma ese texto (ley 29733), y
        // no puede firmarse lo que no existe. Renaser lo tiene publicado desde la V9; a
        // las empresas nuevas el alta se lo copia en borrador y les toca publicarlo.
        if (textosConsentimiento
                .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                        quien.organizacionId(), "PROCESO")
                .isEmpty()) {
            throw new IllegalStateException("Antes de publicar una vacante, publica el texto de "
                    + "consentimiento de tu empresa (POST /panel/textos-consentimiento): quien "
                    + "postule tiene que saber quién tratará sus datos");
        }
        vacante.setEstado("PUBLICADA");
        vacante.setPublicadaEn(Instant.now());
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_vacante",
                "vacante", id, Map.of("estado", "BORRADOR"), Map.of("estado", "PUBLICADA"), null);
    }

    @Override
    @Transactional
    public void asignarPlantillaEvaluacion(ContextoUsuario quien, Long id, Long plantillaEvaluacionId) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        // Del dueño resuelto: con la bandera apagada la vacante usa las plantillas de la
        // plataforma; encendida, solo las propias. Cualquier otra es un «no existe».
        PlantillaEvaluacion plantilla = plantillas
                .findByIdAndOrganizacionId(plantillaEvaluacionId,
                        dueno.duenoDe(quien.organizacionId(), Instrumento.PLANTILLA_EVALUACION))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla de evaluación", "id", plantillaEvaluacionId));
        if (!"PUBLICADA".equals(plantilla.getEstado())) {
            throw new IllegalStateException(
                    "Esa plantilla todavía está en borrador: solo se puede usar una publicada");
        }
        // El nivel tiene que cuadrar: una evaluación de Dirección no sirve para un puesto de
        // ejecución, ni las preguntas ni los pesos.
        Puesto puesto = puestos.findById(vacante.getPuestoId())
                .orElseThrow(() -> new IllegalStateException("La vacante apunta a un puesto que no existe"));
        if (!puesto.getNivelPuestoCodigo().equals(plantilla.getNivelPuestoCodigo())) {
            throw new IllegalArgumentException("La plantilla es de nivel "
                    + plantilla.getNivelPuestoCodigo() + " y el puesto es de nivel "
                    + puesto.getNivelPuestoCodigo());
        }

        Long anterior = vacante.getPlantillaEvaluacionId();
        exigirVaraQuieta(vacante, anterior, plantillaEvaluacionId, "su plantilla de evaluación");
        vacante.setPlantillaEvaluacionId(plantillaEvaluacionId);
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "asignar_plantilla_evaluacion",
                "vacante", id,
                anterior == null ? null : Map.of("plantillaEvaluacionId", String.valueOf(anterior)),
                Map.of("plantillaEvaluacionId", String.valueOf(plantillaEvaluacionId)), null);
    }

    @Override
    @Transactional
    public void asignarPlantillaPrueba(ContextoUsuario quien, Long id, Long versionPlantillaPruebaId) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        VersionPlantillaPrueba version = versionesPrueba.findById(versionPlantillaPruebaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Versión de prueba", "id", versionPlantillaPruebaId));
        // La versión no sabe de organizaciones: se deriva a su plantilla y se valida
        // contra el dueño resuelto. Sin esto, una vacante podía colgarse la prueba de
        // otra empresa — y ese examen se le sirve al candidato al postular.
        plantillasPrueba.findByIdAndOrganizacionId(version.getPlantillaPruebaId(),
                        dueno.duenoDe(quien.organizacionId(), Instrumento.PRUEBA))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Versión de prueba", "id", versionPlantillaPruebaId));
        if (!"PUBLICADA".equals(version.getEstado())) {
            throw new IllegalStateException(
                    "Esa versión todavía está en borrador: solo se puede usar una publicada");
        }

        Long anterior = vacante.getVersionPlantillaPruebaId();
        exigirVaraQuieta(vacante, anterior, versionPlantillaPruebaId, "su versión de prueba");
        vacante.setVersionPlantillaPruebaId(versionPlantillaPruebaId);
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "asignar_plantilla_prueba",
                "vacante", id,
                anterior == null ? null : Map.of("versionPlantillaPruebaId", String.valueOf(anterior)),
                Map.of("versionPlantillaPruebaId", String.valueOf(versionPlantillaPruebaId)), null);
    }

    @Override
    @Transactional
    public void definirAplicacionEvaluacion(ContextoUsuario quien, Long id, boolean aplica) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        if ("CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada no se edita");
        }
        // Encenderla en una vacante ya publicada y sin banco del nivel dejaría al siguiente
        // candidato chocando contra un error al postular: el aviso tiene que salir aquí.
        if (aplica && "PUBLICADA".equals(vacante.getEstado())) {
            exigirBancoDelNivel(vacante);
        }
        boolean anterior = vacante.isAplicaEvaluacion();
        vacante.setAplicaEvaluacion(aplica);
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "definir_aplicacion_evaluacion",
                "vacante", id, Map.of("aplicaEvaluacion", anterior),
                Map.of("aplicaEvaluacion", aplica), null);
    }

    @Override
    @Transactional
    public void asignarVersionPesos(ContextoUsuario quien, Long id, Long versionPesosId) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        VersionPesos version = versionesPesos
                .findByIdAndOrganizacionId(versionPesosId,
                        dueno.duenoDe(quien.organizacionId(), Instrumento.PESOS))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Versión de pesos", "id", versionPesosId));
        // La misma regla que al crear la vacante (RF-114): rige una versión aprobada.
        if (!"PUBLICADA".equals(version.getEstado())) {
            throw new IllegalStateException(
                    "Esa versión de pesos todavía está en borrador: solo se puede usar una publicada");
        }

        // Nada se recalcula hacia atrás: cada nota guardada conserva la versión con la que
        // se calculó. Pero eso protege el pasado, no la competencia: dos candidatos de la
        // misma vacante medidos con varas distintas no se pueden ordenar en la misma lista.
        // Por eso la versión solo se cambia en borrador (docs/DECISION-UNA-VACANTE-UNA-VERSION.md).
        Long anterior = vacante.getVersionPesosId();
        exigirVaraQuieta(vacante, anterior, versionPesosId, "su versión de pesos");
        vacante.setVersionPesosId(versionPesosId);
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "asignar_version_pesos",
                "vacante", id,
                anterior == null ? null : Map.of("versionPesosId", String.valueOf(anterior)),
                Map.of("versionPesosId", String.valueOf(versionPesosId)), null);
    }

    /**
     * Qué rinde esta vacante en su etapa técnica, y cuánto tiempo tiene el candidato.
     *
     * <p><b>Uno de los dos, nunca los dos.</b> O la prueba del puesto de siempre —enunciado,
     * cronómetro, entregables— o el cuestionario técnico que el REDACTOR escribió para esta
     * vacante y el dueño aprobó. Lo que se elija es lo que el candidato encuentra cuando le
     * toca la etapa, y de ahí sale su nota.
     *
     * <p>Se declara aquí y no se deduce de si hay un cuestionario publicado: preparar uno
     * «por si acaso» no puede cambiar en silencio lo que va a rendir la gente.
     *
     * <p>⚠️ <b>La misma vara para todos</b> ({@link #exigirVaraQuieta}): cambiar de
     * instrumento con candidatos ya dentro dejaría a unos medidos con un examen y a otros con
     * otro, en la misma lista. Ojo a la línea exacta: la guarda frena desde la primera
     * postulación, no desde la primera rendición — es más estricta de lo que pide la regla, y
     * se deja así a propósito, igual que en las otras tres decisiones de la vacante.
     *
     * <p>Los minutos son de la vacante y solo de esta etapa. Vacíos, rige lo que diga el
     * instrumento elegido; los del banco del perfil integral viajan con el banco y no se
     * tocan desde aquí.
     */
    @Override
    @Transactional
    public void elegirInstrumentoTecnico(ContextoUsuario quien, Long id,
                                         String instrumento, Integer minutos) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        if (!PLANTILLA.equals(instrumento) && !CUESTIONARIO_TECNICO.equals(instrumento)) {
            throw new IllegalArgumentException(
                    "El instrumento de la etapa técnica es «" + PLANTILLA + "» o «"
                            + CUESTIONARIO_TECNICO + "»; llegó «" + instrumento + "»");
        }
        if (minutos != null && minutos <= 0) {
            throw new IllegalArgumentException("Los minutos de la etapa técnica, si se fijan, "
                    + "son más de cero; para usar los del instrumento elegido se dejan vacíos");
        }

        // ⚠️ Los minutos son parte de la vara, no un ajuste cosmético: cada examen los
        // congela al crearse, así que bajarlos a mitad de tanda deja a los de antes con una
        // hora y a los de después con diez minutos, ordenados en la misma lista.
        String anterior = vacante.getInstrumentoEtapaTecnica();
        if (!anterior.equals(instrumento)
                || !Objects.equals(vacante.getMinutosEtapaTecnica(), minutos)) {
            exigirVaraQuietaDelInstrumento(vacante);
        }
        vacante.setInstrumentoEtapaTecnica(instrumento);
        vacante.setMinutosEtapaTecnica(minutos);
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "elegir_instrumento_tecnico",
                "vacante", id,
                Map.of("instrumentoEtapaTecnica", anterior),
                Map.of("instrumentoEtapaTecnica", instrumento,
                        "minutosEtapaTecnica", String.valueOf(minutos)), null);
    }

    /**
     * Que la vacante tenga con qué llenar su etapa técnica antes de publicarse.
     *
     * <p>El mensaje dice cuál falta según lo que la vacante haya declarado: mandar a «elige
     * la prueba del puesto» a quien eligió el cuestionario técnico lleva a la pantalla
     * equivocada.
     */
    private void exigirInstrumentoTecnico(Vacante vacante) {
        if (CUESTIONARIO_TECNICO.equals(vacante.getInstrumentoEtapaTecnica())) {
            versionesBanco.findFirstByVacanteIdAndEstado(vacante.getId(), "PUBLICADA")
                    .orElseThrow(() -> new IllegalStateException(
                            "Esta vacante rinde el cuestionario técnico y todavía no hay ninguno "
                                    + "publicado: apruébalo antes de publicar la vacante"));
            return;
        }
        if (vacante.getVersionPlantillaPruebaId() == null) {
            throw new IllegalStateException(
                    "Antes de publicar hay que elegir la prueba del puesto de esta vacante");
        }
    }

    /**
     * Gemela de {@link #exigirVaraQuieta} para el instrumento, que es texto y no un id.
     *
     * <p>No se pudo reutilizar aquella tal cual —compara {@code Long}— pero la regla, la
     * línea (la primera postulación) y el mensaje son los mismos a propósito: quien lea los
     * dos tiene que ver la misma decisión, no dos parecidas.
     */
    private void exigirVaraQuietaDelInstrumento(Vacante vacante) {
        if ("BORRADOR".equals(vacante.getEstado())) {
            return;
        }
        if (postulaciones.countByVacanteId(vacante.getId()) > 0) {
            throw new IllegalStateException("Esta vacante ya tiene postulantes y lo que se "
                    + "rinde en su etapa técnica no se cambia: todos sus candidatos se miden "
                    + "con la misma vara. Para estrenar otro instrumento, ábrelo en la "
                    + "siguiente convocatoria.");
        }
    }

    /**
     * Sin banco de preguntas publicado para el nivel del puesto no hay examen que servir.
     *
     * <p>Es la misma búsqueda que hace {@code ServicioEvaluacionImpl.crearAlPostular} cuando
     * alguien postula, adelantada al momento de publicar: allí el fallo es un 500 en la cara
     * de quien acaba de mandar su currículum, y aquí es una frase para quien todavía puede
     * arreglarlo.
     *
     * <p>⚠️ <b>No mira {@code vacante.isAplicaEvaluacion()}, y es a propósito.</b> Al
     * ENCENDER la evaluación la vacante todavía la tiene apagada —el {@code set} viene
     * después—, así que preguntárselo aquí dejaría pasar justo el caso que esto existe para
     * frenar. Decide quien llama, que es el único que sabe si la evaluación va a estar
     * encendida cuando esto termine.
     */
    private void exigirBancoDelNivel(Vacante vacante) {
        Puesto puesto = puestos.findById(vacante.getPuestoId())
                .orElseThrow(() -> new IllegalStateException(
                        "La vacante apunta a un puesto que no existe"));
        String nivel = puesto.getNivelPuestoCodigo();
        if (versionesBanco.laPublicadaDelNivel(
                dueno.duenoDe(vacante.getOrganizacionId(), Instrumento.BANCO), "NIVEL", nivel)
                .isEmpty()) {
            throw new IllegalStateException("No hay ningún banco de preguntas publicado para el "
                    + "nivel " + nivel + ": quien postule no tendría evaluación que responder. "
                    + "Publica uno en Configuración, o apaga la evaluación del banco en esta "
                    + "vacante y quédate con la prueba del puesto");
        }
        /*
         * ⚠️ Las DOS cosas que `crearAlPostular` resuelve, no solo el banco.
         *
         * Mientras la vacante estaba obligada a elegir plantilla, este camino no existía:
         * `asignarPlantillaEvaluacion` ya la había validado contra dueño y nivel. Desde que
         * se resuelve sola hace falta comprobarla aquí, o el `IllegalStateException` de
         * `laPlantilla()` sale como un 500 en `POST /portal/postulaciones` — que es
         * exactamente el fallo que esta guarda existe para adelantar.
         *
         * Los dos instrumentos resuelven su dueño por separado, así que una empresa con
         * plantillas propias puede tener banco de un nivel y no plantilla del mismo.
         */
        if (plantillas.laPublicadaDelNivel(
                dueno.duenoDe(vacante.getOrganizacionId(), Instrumento.PLANTILLA_EVALUACION),
                nivel).isEmpty()) {
            throw new IllegalStateException("No hay ninguna plantilla de evaluación publicada "
                    + "para el nivel " + nivel + ": quien postule no podría empezar su "
                    + "evaluación. Publica una, o apaga la evaluación del banco en esta "
                    + "vacante y quédate con la prueba del puesto");
        }
    }

    /**
     * Una vacante, una versión, de principio a fin (docs/DECISION-UNA-VACANTE-UNA-VERSION.md).
     *
     * <p>Todos los candidatos de una vacante se miden con la misma vara: cambiarle un
     * instrumento con gente ya dentro deja a los de antes calificados con uno y a los de
     * después con otro, y el ranking los ordena juntos como si fueran comparables. Que cada
     * nota conserve su versión protege el pasado; esto protege la competencia.
     *
     * <p>La línea es la <b>primera postulación</b>, no la publicación: una vacante publicada
     * a la que nadie ha postulado todavía puede terminar de configurarse (es el camino del
     * flujo sin banco, que asigna sus pesos después de publicar). Y asignar donde no había
     * nada se permite siempre: nadie fue medido con una vara que no existía.
     */
    private void exigirVaraQuieta(Vacante vacante, Long anterior, Long nuevo, String queCosa) {
        if (anterior == null || anterior.equals(nuevo) || "BORRADOR".equals(vacante.getEstado())) {
            return;
        }
        if (postulaciones.countByVacanteId(vacante.getId()) > 0) {
            throw new IllegalStateException("Esta vacante ya tiene postulantes y " + queCosa
                    + " no se cambia: todos sus candidatos se miden con la misma vara. Para "
                    + "estrenar otra versión, ábrela en la siguiente convocatoria; para "
                    + "recalibrar señales, edita las preguntas del banco y recalifica a todos "
                    + "(scripts/recalificar-banco.py).");
        }
    }

    @Override
    public List<PlantillaCorreoDeVacante> plantillasCorreo(ContextoUsuario quien, Long vacanteId) {
        laDeLaOrganizacion(quien, vacanteId);
        return plantillasPorVacante.findByVacanteIdOrderByAvisoCodigo(vacanteId).stream()
                .map(p -> new PlantillaCorreoDeVacante(p.getAvisoCodigo(), p.getPlantillaCodigo()))
                .toList();
    }

    @Override
    @Transactional
    public void asignarPlantillaCorreo(ContextoUsuario quien, Long vacanteId,
                                       AsignarPlantillaCorreo datos) {
        laDeLaOrganizacion(quien, vacanteId);
        if (datos.avisoCodigo().equals(datos.plantillaCodigo())) {
            throw new IllegalArgumentException(
                    "Sustituir «" + datos.avisoCodigo() + "» por sí mismo no cambia nada");
        }
        // Que el texto exista se comprueba AQUÍ y no al mandarlo: si se dejara pasar un código
        // equivocado, el fallo aparecería semanas después, cuando un candidato avanzara y su
        // correo no saliera. Y ese fallo no da señal — la postulación avanza igual.
        plantillasCorreo
                .findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                        quien.organizacionId(), datos.plantillaCodigo())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay ninguna plantilla de correo activa con el código «"
                                + datos.plantillaCodigo() + "»"));

        PlantillaCorreoVacante fila = plantillasPorVacante
                .findByVacanteIdAndAvisoCodigo(vacanteId, datos.avisoCodigo())
                .orElseGet(() -> PlantillaCorreoVacante.builder()
                        .vacanteId(vacanteId)
                        .avisoCodigo(datos.avisoCodigo())
                        .creadoEn(Instant.now())
                        .build());
        String anterior = fila.getPlantillaCodigo();
        fila.setPlantillaCodigo(datos.plantillaCodigo());
        plantillasPorVacante.save(fila);

        auditoria.registrar(quien.organizacionId(), quien, "asignar_plantilla_correo_vacante",
                "vacante", vacanteId,
                anterior == null ? null : Map.of(datos.avisoCodigo(), anterior),
                Map.of(datos.avisoCodigo(), datos.plantillaCodigo()), null);
    }

    @Override
    @Transactional
    public void quitarPlantillaCorreo(ContextoUsuario quien, Long vacanteId, String avisoCodigo) {
        laDeLaOrganizacion(quien, vacanteId);
        PlantillaCorreoVacante fila = plantillasPorVacante
                .findByVacanteIdAndAvisoCodigo(vacanteId, avisoCodigo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Texto propio de la vacante", "aviso", avisoCodigo));
        plantillasPorVacante.delete(fila);
        auditoria.registrar(quien.organizacionId(), quien, "quitar_plantilla_correo_vacante",
                "vacante", vacanteId, Map.of(avisoCodigo, fila.getPlantillaCodigo()), null, null);
    }

    @Override
    @Transactional
    public CierrePruebaResponse definirCierrePrueba(ContextoUsuario quien, Long vacanteId,
                                                    DefinirCierrePrueba datos) {
        Vacante vacante = laDeLaOrganizacion(quien, vacanteId);
        if ("CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada no se edita");
        }
        if (datos.cierraEn() != null) {
            // Una fecha ya pasada no se rechaza por pedante: el barrido de vencidos la vería
            // al minuto siguiente y entregaría sola la tanda entera. Un año mal tecleado no
            // puede costar eso.
            if (datos.cierraEn().isBefore(Instant.now())) {
                throw new IllegalArgumentException(
                        "Esa fecha ya pasó: fijarla entregaría sola la prueba de todos");
            }
            // Sin plantilla de prueba no hay nada que cerrar con una fecha. Desde el ciclo 2
            // es un camino normal —una vacante con cuestionario técnico se publica sin ella—
            // y no un borrador a medias: antes reventaba con «The given id must not be null»,
            // el error crudo de Spring Data en la cara de quien usa el panel.
            if (vacante.getVersionPlantillaPruebaId() == null) {
                throw new IllegalStateException("Esta vacante no rinde una prueba del puesto, "
                        + "así que no hay una fecha de cierre que fijarle: su etapa técnica es "
                        + "el cuestionario, y su tiempo son los minutos de la vacante");
            }
            // Y no tiene sentido sobre un cronómetro: ahí el plazo son los minutos que corren
            // desde que cada uno empieza, y una fecha fija los anularía sin decirlo.
            versionesPrueba.findById(vacante.getVersionPlantillaPruebaId())
                    .filter(v -> "CRONOMETRADA".equals(v.getModalidad()))
                    .ifPresent(v -> {
                        throw new IllegalArgumentException(
                                "La prueba de esta vacante es cronometrada (" + v.getDuracionMinutos()
                                        + " minutos desde que cada candidato empieza): una fecha "
                                        + "de cierre para todos anularía el reloj");
                    });
        }

        Instant anterior = vacante.getPruebaCierraEn();
        vacante.setPruebaCierraEn(datos.cierraEn());
        vacantes.save(vacante);

        // Y se mueve a los que ya están dentro. Sin esto, la fecha valdría solo para quien
        // entrara después: la mitad de la tanda cerraría el domingo y la otra mitad a los
        // siete días de su propio lunes, sin nada que lo explicara.
        int movidos = 0;
        int conPlazoPropio = 0;
        for (IntentoPrueba intento : intentos.abiertosDeLaVacante(vacanteId)) {
            if (intento.isPlazoPropio()) {
                conPlazoPropio++;
                continue;
            }
            intento.setVenceEn(fechaDeCierreDe(intento, datos.cierraEn()));
            intentos.save(intento);
            movidos++;
        }

        auditoria.registrar(quien.organizacionId(), quien, "definir_cierre_prueba",
                "vacante", vacanteId,
                anterior == null ? null : Map.of("pruebaCierraEn", anterior.toString()),
                datos.cierraEn() == null ? Map.of() : Map.of("pruebaCierraEn", datos.cierraEn().toString()),
                datos.motivo());

        return new CierrePruebaResponse(datos.cierraEn(), movidos, conPlazoPropio);
    }

    /**
     * Qué fecha de cierre le toca a este intento cuando cambia la de la vacante.
     *
     * <p>El caso que obliga a que esto exista es <b>quitar</b> la fecha. A quien todavía no
     * ha empezado se le deja vacía y se le calculará al empezar, como siempre. Pero a quien
     * ya está dentro, empezar no vuelve a pasarle: dejársela vacía lo dejaría <b>sin
     * vencimiento para siempre</b> —podría entregar cuando quisiera y el barrido de vencidos
     * jamás lo cerraría, porque una comparación contra nulo nunca casa—. A ese se le devuelve
     * el plazo de su plantilla, contado desde que empezó.
     */
    private Instant fechaDeCierreDe(IntentoPrueba intento, Instant cierraEn) {
        if (cierraEn != null || intento.getIniciadoEn() == null) {
            return cierraEn;
        }
        return versionesPrueba.findById(intento.getVersionPlantillaPruebaId())
                .map(v -> "CRONOMETRADA".equals(v.getModalidad())
                        ? intento.getIniciadoEn().plus(v.getDuracionMinutos(), ChronoUnit.MINUTES)
                        : intento.getIniciadoEn().plus(v.getPlazoDias(), ChronoUnit.DAYS))
                // Si su versión ya no existe, se le deja la que tenía: quitarle el
                // vencimiento sería peor que dejarle uno viejo.
                .orElse(intento.getVenceEn());
    }

    @Override
    @Transactional
    public void cerrar(ContextoUsuario quien, Long id, String motivo) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        if ("CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("La vacante ya está cerrada");
        }
        String anterior = vacante.getEstado();
        // Cerrar NO arrastra las postulaciones en marcha: cada una se decide una a una
        // desde la bandeja. Esto cambió con el documento nuevo del cliente.
        vacante.setEstado("CERRADA");
        vacante.setCerradaEn(Instant.now());
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "cerrar_vacante",
                "vacante", id, Map.of("estado", anterior), Map.of("estado", "CERRADA"), motivo);
    }

    // ============ ayudas ============

    private Vacante laDeLaOrganizacion(ContextoUsuario quien, Long id) {
        return vacantes.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", id));
    }

    private VacantePanel comoPanel(Vacante v) {
        return new VacantePanel(v.getId(), v.getTitulo(), v.getEstado(), v.getTipoCierre(),
                v.getPuestoId(), v.getSolicitudTalentoId(), v.getResponsableUsuarioId(),
                v.getPublicadaEn(), v.getCerradaEn(), v.isAplicaEvaluacion(),
                v.getPlantillaEvaluacionId(), v.getVersionPlantillaPruebaId(),
                v.getVersionPesosId());
    }
}
