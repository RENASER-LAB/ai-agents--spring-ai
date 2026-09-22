package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreoVacante;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoVacanteRepository;
import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal;
import com.renaser.ai.ai_engine.notificacion.service.TextosDeCorreoRetirados;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.postulacion.service.PostulacionesEnCarrera;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import com.renaser.ai.ai_engine.vacante.service.CambiosDeLaVacante;
import com.renaser.ai.ai_engine.vacante.service.Remuneracion;
import com.renaser.ai.ai_engine.vacante.service.ServicioVacantesPanel;
import com.renaser.ai.ai_engine.vacante.service.VacanteArchivada;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.*;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
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
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.entity.*;
import com.renaser.ai.ai_engine.vacante.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioVacantesPanelImpl implements ServicioVacantesPanel {

    /** Los dos instrumentos de la etapa técnica. Uno por vacante, nunca los dos (V43). */
    public static final String PLANTILLA = "PLANTILLA";
    public static final String CUESTIONARIO_TECNICO = "CUESTIONARIO_TECNICO";

    /**
     * El suelo del reloj de la etapa técnica, en minutos. El mismo que exige publicar una
     * versión de plantilla: un número por debajo de esto entrega la prueba sola antes de que
     * al candidato le dé tiempo a leer el enunciado.
     */
    private static final int MINUTOS_MINIMOS = 5;

    /** Los dos estados que este servicio pregunta por su nombre. */
    private static final String ESTADO_CERRADA = "CERRADA";
    private static final String ESTADO_PUBLICADA = "PUBLICADA";

    /** El permiso que abre el lápiz de la lista y el PUT de la vacante. */
    private static final String PERMISO_EDITAR = "editar_vacante";

    /**
     * El permiso que archiva y desarchiva, y es el mismo que cierra.
     *
     * <p>Archivar es el paso siguiente de cerrar —retirar de la mesa lo que ya terminó— y no
     * una decisión distinta: quien puede dar por terminada una convocatoria puede guardarla,
     * y quien no puede cerrarla tampoco tiene por qué poder esconderla de la lista de los
     * demás. Un permiso nuevo habría que repartirlo a mano rol por rol, y el día del reparto
     * nadie lo tendría.
     */
    private static final String PERMISO_ARCHIVAR = "cerrar_vacante";

    /**
     * El permiso que elimina, y este sí es propio (V60).
     *
     * <p>Al revés que el archivo, y por una razón concreta: archivar es el paso siguiente de
     * cerrar —retirar de la mesa lo que ya terminó— y no reparte ningún poder nuevo.
     * Eliminar cierra las postulaciones de otras personas, les manda un aviso, retira la
     * convocatoria de todas las pantallas y no se deshace desde el panel. Que eso venga
     * incluido en «puede cerrar convocatorias» no se sigue de nada, y el día que alguien
     * reparta {@code cerrar_vacante} a un rol nuevo estaría regalando esto sin enterarse.
     */
    private static final String PERMISO_ELIMINAR = "eliminar_vacante";

    /**
     * El motivo de cierre de quien estaba dentro cuando la vacante se retiró (V60).
     *
     * <p>Código propio y no {@code CIERRE_MANUAL}: los dos dejan la postulación en
     * {@code CERRADA}, pero la pregunta que se contesta meses después es distinta. «Se cerró
     * porque la convocatoria se retiró» y «se cerró porque alguien lo decidió» no se pueden
     * contar juntas en un informe de motivos de cierre sin perder la única que el candidato
     * no provocó.
     */
    private static final String CIERRE_POR_ELIMINACION = "VACANTE_ELIMINADA";

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
    private final IntentoPruebaRepository intentos;
    // Solo para preguntar si alguien ya abrió su cuestionario técnico: es el otro
    // instrumento de la etapa, y la guarda tiene que mirar los dos.
    private final EvaluacionRepository evaluaciones;
    private final VersionBancoRepository versionesBanco;
    private final ServicioAuditoria auditoria;
    private final DuenoDelInstrumento dueno;
    // Solo para preguntar si ya hay alguien midiéndose en esta vacante: desde la primera
    // postulación, sus instrumentos se quedan quietos.
    private final com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository postulaciones;
    // Quién sigue en carrera en una vacante, decidido en un solo sitio para todo el sistema.
    private final PostulacionesEnCarrera enCarrera;
    // Cerrar una postulación se hace por aquí y solo por aquí: es lo que garantiza que el
    // cierre por eliminación guarde su transición, su motivo y su auditoría como cualquier
    // otro, y que libere lo mismo que un cierre decidido a mano desde la bandeja.
    private final MaquinaEstados maquina;
    // El aviso que se queda esperando dentro del portal. Desde esta entrega es el ÚNICO
    // canal de las noticias de la vacante: lo que cambia en una convocatoria no sale por
    // correo. Ver `avisarDeLaEdicion`.
    private final ServicioAvisosPortal avisos;
    // Qué filas alcanza quien pregunta: el mismo guardián que usa el resto del panel.
    private final AlcanceSobreLaVacante alcance;
    private final Permisos permisos;

    // ============ Puestos ============

    @Override
    @Transactional
    public Long crearPuesto(ContextoUsuario quien, GuardarPuesto datos) {
        String codigo = datos.codigo() == null || datos.codigo().isBlank()
                ? codigoDisponible(quien.organizacionId(), datos.nombre())
                : datos.codigo().trim();
        Puesto puesto = puestos.save(Puesto.builder()
                .organizacionId(quien.organizacionId())
                .codigo(codigo)
                .nombre(datos.nombre())
                .nivelPuestoCodigo(datos.nivelPuestoCodigo())
                .familiaCodigo(datos.familiaCodigo())
                .esActivo(true)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_puesto",
                "puesto", puesto.getId(), null, Map.of("codigo", codigo), null);
        return puesto.getId();
    }

    private String codigoDisponible(Long organizacionId, String nombre) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        if (base.isBlank()) {
            base = "PUESTO";
        }
        String candidato = base;
        int sufijo = 2;
        while (puestos.existsByOrganizacionIdAndCodigo(organizacionId, candidato)) {
            candidato = base + "_" + sufijo++;
        }
        return candidato;
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
        Puesto puesto = puestoParaVacante(quien, solicitud, datos.puestoId());

        // La versión de pesos publicada vigente. Elegir otra es de Dirección (hito 2).
        // De quién son los pesos lo contesta el resolutor: los de la plataforma mientras
        // la empresa no personalice, los suyos en cuanto encienda la bandera.
        VersionPesos pesos = versionesPesos
                .findFirstByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(
                        dueno.duenoDe(quien.organizacionId(), Instrumento.PESOS), "PUBLICADA")
                .orElseThrow(() -> new IllegalStateException("No hay una versión de pesos publicada"));

        // La remuneración se valida ANTES de construir nada, y entra en el builder como un
        // campo más. Escribirla después obligaría a un segundo `save` sobre la fila recién
        // insertada: dos viajes a la base y —peor— un instante en que la vacante existe con
        // un sueldo que todavía no se ha comprobado.
        //
        // Sin `remuneracion` en el cuerpo nace OCULTA, que es como han nacido todas hasta la
        // V55. Y sin marca de «actualizada»: al crearla no se actualizó nada, se declaró, y
        // ponerla ya haría que la primera persona que abriera la vacante viera una novedad
        // que no lo es.
        RemuneracionDeLaVacante sueldo = datos.remuneracion() == null
                ? RemuneracionDeLaVacante.OCULTA : datos.remuneracion();
        String monedaSueldo = Remuneracion.validar(sueldo.tipo(), sueldo.min(), sueldo.max(),
                sueldo.moneda());
        boolean sueldoOculto = Remuneracion.OCULTA.equals(sueldo.tipo());

        Vacante vacante = vacantes.save(Vacante.builder()
                .organizacionId(quien.organizacionId())
                .solicitudTalentoId(solicitud.getId())
                .puestoId(puesto.getId())
                .titulo(datos.titulo())
                .descripcion(datos.descripcion())
                .proposito(datos.proposito())
                .responsabilidades(datos.responsabilidades())
                .requisitos(datos.requisitos())
                .modalidad(datos.modalidad())
                .horario(datos.horario())
                .ubicacion(datos.ubicacion())
                .remuneracionTipo(sueldo.tipo())
                .remuneracionMin(sueldoOculto ? null : sueldo.min())
                .remuneracionMax(sueldoOculto || Remuneracion.FIJA.equals(sueldo.tipo())
                        ? null : sueldo.max())
                .remuneracionMoneda(monedaSueldo)
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

    private Puesto puestoParaVacante(ContextoUsuario quien, SolicitudTalento solicitud,
                                     Long puestoRecibido) {
        Long puestoId = solicitud.getPuestoId();
        if (puestoId != null && puestoRecibido != null && !puestoId.equals(puestoRecibido)) {
            throw new IllegalStateException("El puesto enviado no coincide con el de la solicitud");
        }
        if (puestoId == null) {
            if (puestoRecibido == null) {
                throw new IllegalStateException("Esta solicitud histórica necesita que se elija un puesto");
            }
            puestoId = puestoRecibido;
        }
        Long puestoResuelto = puestoId;
        Puesto puesto = puestos.findByIdAndOrganizacionId(puestoResuelto, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Puesto", "id", puestoResuelto));
        if (!puesto.isEsActivo()) {
            throw new IllegalStateException("El puesto seleccionado está inactivo");
        }
        if (solicitud.getPuestoId() == null) {
            solicitud.setPuestoId(puesto.getId());
            solicitud.setNivelPuestoCodigo(puesto.getNivelPuestoCodigo());
            solicitud.setFamiliaCodigo(puesto.getFamiliaCodigo());
        }
        return puesto;
    }

    /**
     * Corregir una vacante desde el panel: el formulario entero, comparado campo a campo.
     *
     * <p><b>El sueldo entra aquí</b>, y hasta esta entrega no entraba. La razón de dejarlo
     * fuera era buena —cambiarlo le escribía a cada candidato vivo, y eso no podía dispararse
     * al corregir una falta de ortografía—, pero se resolvía peor: el panel tenía que llamar
     * a dos verbos seguidos, y un guardado que cambiara el sueldo y el horario mandaba dos
     * noticias por un solo cambio. Ahora se compara todo de una vez y sale <b>un único
     * aviso</b> con lo que cambió, sueldo incluido.
     *
     * <p><b>Nada se escribe antes de que todo valide.</b> Los montos y la guarda de la
     * simetría se comprueban con la entidad todavía intacta: una forma de cierre imposible o
     * un rango al revés no pueden dejar media vacante guardada.
     *
     * <p><b>Sin cambios no pasa nada</b>: ni auditoría ni aviso. Es lo que hace que reenviar
     * la misma edición —el doble clic, el reintento de una red lenta— sea inofensivo.
     */
    @Override
    @Transactional
    public VacanteActualizadaResponse editar(ContextoUsuario quien, Long id,
                                             GuardarVacante datos) {
        // Con SUS_VACANTES, la de otro responsable no existe: 404 y no 403, como en todo el
        // panel. Lo decide el guardián, que es el único que sabe qué alcanza cada alcance.
        Vacante vacante = alcance.laVacanteVisible(quien, id, PERMISO_EDITAR);
        // ⚠️ El archivo se comprueba ANTES que el estado y antes de tocar nada: el formulario
        // que alguien dejó abierto sigue sabiendo la URL del PUT, y entre abrirlo y guardarlo
        // cabe que otro usuario la archive. Sin esto, el guardado se aplicaría sobre una
        // vacante que ya nadie ve en la lista.
        VacanteArchivada.exigirQueNoLoEste(vacante);
        if (ESTADO_CERRADA.equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada no se edita");
        }

        // El sueldo, validado ANTES de tocar la entidad: si el rango está al revés o la
        // vacante publicada intenta esconder lo que paga, no se guarda ni una coma del resto.
        RemuneracionDeLaVacante sueldo = datos.remuneracion() == null
                ? RemuneracionDeLaVacante.OCULTA : datos.remuneracion();
        exigirQueLaSimetriaNoSeRevoque(vacante, sueldo);
        String monedaSueldo = Remuneracion.validar(sueldo.tipo(), sueldo.min(), sueldo.max(),
                sueldo.moneda());

        String sueldoAntes = Remuneracion.escribir(vacante);
        String sueldoAhora = Remuneracion.escribir(sueldo.tipo(), sueldo.min(), sueldo.max(),
                monedaSueldo);
        CambiosDeLaVacante cambios =
                CambiosDeLaVacante.entre(vacante, datos, sueldoAntes, sueldoAhora);

        if (!cambios.hayCambios()) {
            return new VacanteActualizadaResponse(false, 0);
        }
        String motivo = exigirMotivoSiCambiaElSueldo(vacante, cambios, datos);

        vacante.setTitulo(CambiosDeLaVacante.limpio(datos.titulo()));
        vacante.setDescripcion(CambiosDeLaVacante.limpio(datos.descripcion()));
        vacante.setProposito(opcional(datos.proposito()));
        vacante.setResponsabilidades(opcional(datos.responsabilidades()));
        vacante.setRequisitos(opcional(datos.requisitos()));
        vacante.setModalidad(opcional(datos.modalidad()));
        vacante.setHorario(opcional(datos.horario()));
        vacante.setUbicacion(opcional(datos.ubicacion()));
        if (cambios.cambioElSueldo()) {
            // La marca de «actualizada» solo se mueve si el sueldo cambió de verdad: el
            // portal la pinta como «actualizado el …» junto al monto, y ponerla en cada
            // guardado le anunciaría al candidato una novedad que no existe.
            escribirRemuneracion(vacante, sueldo, Instant.now());
        }
        vacante.setTipoCierre(datos.tipoCierre());
        // Las dos que dependen de la forma de cierre se escriben tal como las calculó la
        // comparación, y no leyendo otra vez el cuerpo: si lo comparado y lo guardado se
        // decidieran por separado, volvería a caber un campo que se borra sin salir en la
        // auditoría. Ver CambiosDeLaVacante.
        vacante.setPlazas(cambios.plazasQueQuedan());
        vacante.setCierraEn(cambios.cierreQueQueda());
        // ⚠️ La fecha de APERTURA no se toca al editar. El formulario de esta entrega no la
        // enseña —responsable, título, textos, modalidad, horario, ubicación, forma de
        // cierre y remuneración—, y escribir lo que el cuerpo no trae la vaciaba en silencio
        // en cuanto alguien abría el lápiz y pulsaba guardar. Se define al crear la vacante.
        vacante.setResponsableUsuarioId(datos.responsableUsuarioId());
        vacantes.save(vacante);

        auditoria.registrar(quien.organizacionId(), quien, "editar_vacante",
                "vacante", id, cambios.anterior(), cambios.nuevo(), motivo);

        return new VacanteActualizadaResponse(true, avisarDeLaEdicion(vacante, cambios));
    }

    /**
     * El motivo del sueldo, exigido solo donde significa algo.
     *
     * <p>En una vacante publicada el cambio de sueldo le llega a cada persona en carrera, y
     * la auditoría tiene que poder contestar «¿por qué les dijimos que bajó?». En borrador no
     * hay a quién contárselo: pedir una justificación para rellenar un campo de una vacante
     * que nadie ha visto es burocracia, y es la misma regla que ya aplica la tarjeta del
     * sueldo en el panel.
     */
    private String exigirMotivoSiCambiaElSueldo(Vacante vacante, CambiosDeLaVacante cambios,
                                                GuardarVacante datos) {
        if (!cambios.cambioElSueldo()) {
            return null;
        }
        String motivo = datos.motivoRemuneracion() == null
                ? "" : datos.motivoRemuneracion().trim();
        if (motivo.isEmpty() && ESTADO_PUBLICADA.equals(vacante.getEstado())) {
            throw new IllegalArgumentException("Di por qué cambia el sueldo: se les avisa a "
                    + "los candidatos y queda en la auditoría de la vacante");
        }
        return motivo.isEmpty() ? null : motivo;
    }

    /** Un campo de texto opcional: en blanco es que no hay nada, no una cadena vacía. */
    private String opcional(String valor) {
        String limpio = CambiosDeLaVacante.limpio(valor);
        return limpio.isEmpty() ? null : limpio;
    }

    @Override
    public List<VacantePanel> listar(ContextoUsuario quien, boolean archivadas) {
        // Un solo conteo para toda la lista: preguntar por fila multiplicaría las consultas
        // por el número de convocatorias de la empresa en la pantalla que más se abre.
        Map<Long, Integer> porVacante = enCarrera.cuantasPorVacante(quien.organizacionId());
        FiltroAlcance alcanceDeEdicion = alcanceDeEdicionDe(quien);
        FiltroAlcance alcanceDeArchivo = alcanceDeArchivoDe(quien);
        // ⚠️ Dos consultas distintas y no un filtro sobre la lista entera. La lista habitual
        // ni siquiera lee las archivadas: es lo que hace que no reaparezcan al buscar, al
        // filtrar por estado ni al paginar, porque nunca llegaron a la pantalla.
        //
        // ⚠️ Y ninguna de las dos trae eliminadas (V60). No es un tercer filtro sobre lo
        // mismo: una eliminada no está «en otra lista», no está en ninguna.
        List<Vacante> filas = archivadas
                ? vacantes
                    .findByOrganizacionIdAndArchivadaEnIsNotNullAndEliminadaEnIsNullOrderByArchivadaEnDesc(
                        quien.organizacionId())
                : vacantes
                    .findByOrganizacionIdAndArchivadaEnIsNullAndEliminadaEnIsNullOrderByCreadoEnDesc(
                        quien.organizacionId());
        FiltroAlcance alcanceDeEliminacion = alcanceDeEliminacionDe(quien);
        return filas.stream()
                // ⚠️ Sin el plazo vigente, y es deliberado: resolverlo pide la versión de la
                // plantilla y los intentos abiertos de CADA vacante, o sea dos consultas por
                // fila en la pantalla que más se abre. La fecha de cierre sí viaja —es una
                // columna de la propia vacante—; lo demás lo trae el detalle, que es donde
                // se configura.
                .map(v -> comoPanel(v, porVacante.getOrDefault(v.getId(), 0),
                        puedeEditar(quien, alcanceDeEdicion, v),
                        alcanceDeArchivo, alcanceDeEliminacion, quien,
                        PlazoDeLaPrueba.SIN_DATO))
                .toList();
    }

    /**
     * El número del botón «Archivadas (N)».
     *
     * <p>Se cuenta sobre exactamente el mismo universo que devuelve {@link #listar}: la
     * organización de quien pregunta. Que salgan de la misma regla es lo que impide que el
     * botón prometa siete y la vista enseñe cinco, que es la forma en que un contador deja de
     * creerse.
     *
     * <p>Consultar Archivadas va con el permiso de lectura de siempre ({@code ver_vacantes},
     * que exige el controlador) y no con el de archivar: quien puede ver las vacantes de la
     * empresa puede ver las que se guardaron.
     */
    @Override
    public ConteoDeArchivadas contarArchivadas(ContextoUsuario quien) {
        return new ConteoDeArchivadas(
                vacantes.countByOrganizacionIdAndArchivadaEnIsNotNullAndEliminadaEnIsNull(
                        quien.organizacionId()));
    }

    @Override
    public VacantePanel detalle(ContextoUsuario quien, Long id) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        return comoPanel(vacante, enCarrera.cuantasEnLaVacante(id),
                puedeEditar(quien, alcanceDeEdicionDe(quien), vacante),
                alcanceDeArchivoDe(quien), alcanceDeEliminacionDe(quien), quien,
                loQueRigeHoy(quien, vacante));
    }

    /**
     * Lo que rige HOY en la etapa técnica de una vacante: su reloj y a cuánta gente
     * alcanzaría moverle la fecha.
     *
     * <p>Existe porque el panel no puede deducirlo. La fecha de cierre sola no dice nada: con
     * una plantilla cronometrada conviven los dos plazos —los minutos de cada persona y la
     * fecha para todos— y gana el que caiga antes; con una de plazo abierto y sin fecha, lo
     * que rige son los días de la plantilla, que están en otra tabla.
     *
     * <p>⚠️ <b>La modalidad es la EFECTIVA.</b> Unos minutos propios de la vacante convierten
     * en cronometrada hasta una plantilla de plazo abierto, y es exactamente lo que hace
     * {@code ServicioPruebaImpl} al arrancar el reloj. Si esta pantalla leyera la modalidad
     * de la fila de la plantilla, diría «N días» sobre una prueba que cierra en una hora.
     *
     * <p>⚠️ <b>La versión se pide con su dueño resuelto, no por id suelto.</b> Una versión de
     * prueba no sabe de organizaciones —eso vive en su plantilla—, así que se busca por el
     * guardián que deriva al padre ({@code laDeLaOrganizacion}) con el dueño que resuelve
     * {@link DuenoDelInstrumento}, igual que al asignarla. La que no sea de esta empresa se
     * lee como «sin dato» y no como el plazo de otra: con una sola organización las dos
     * formas funcionan idéntico, y eso es justo lo que nadie nota hasta que hay dos.
     */
    private PlazoDeLaPrueba loQueRigeHoy(ContextoUsuario quien, Vacante v) {
        Integer minutosVacante = v.getMinutosEtapaTecnica();
        if (CUESTIONARIO_TECNICO.equals(v.getInstrumentoEtapaTecnica())) {
            // El cuestionario técnico no se cierra con una fecha: su plazo son los minutos
            // de la vacante y, si no los fijó, los del banco que le toca. Y no usa
            // `intento_prueba`, así que no hay ningún examen al que mover nada.
            Integer minutos = minutosVacante != null ? minutosVacante
                    : versionesBanco.findFirstByVacanteIdAndEstado(v.getId(), "PUBLICADA")
                            .map(banco -> banco.getMinutosObjetivo())
                            .orElse(null);
            return new PlazoDeLaPrueba(null, minutos, null, 0, 0);
        }
        VersionPlantillaPrueba version = v.getVersionPlantillaPruebaId() == null ? null
                : versionesPrueba.laDeLaOrganizacion(v.getVersionPlantillaPruebaId(),
                        dueno.duenoDe(quien.organizacionId(), Instrumento.PRUEBA)).orElse(null);
        String modalidad = minutosVacante != null ? "CRONOMETRADA"
                : version == null ? null : version.getModalidad();
        Integer minutos = minutosVacante != null ? minutosVacante
                : version != null && "CRONOMETRADA".equals(version.getModalidad())
                        ? version.getDuracionMinutos()
                        : null;
        Integer dias = version != null && "PLAZO_ABIERTO".equals(modalidad)
                ? version.getPlazoDias()
                : null;
        int conPlazoPropio = 0;
        int sinPlazoPropio = 0;
        for (IntentoPrueba intento : intentos.abiertosDeLaVacante(v.getId())) {
            if (intento.isPlazoPropio()) {
                conPlazoPropio++;
            } else {
                sinPlazoPropio++;
            }
        }
        return new PlazoDeLaPrueba(modalidad, minutos, dias, sinPlazoPropio, conPlazoPropio);
    }

    /**
     * El plazo vigente de una vacante, tal como lo enseña el detalle.
     *
     * <p>{@link #SIN_DATO} es lo que sabe la lista: nada. Viaja con todo en vacío para que el
     * panel lo lea como «sin dato» en vez de inventarse un plazo.
     */
    private record PlazoDeLaPrueba(String modalidad, Integer minutos, Integer dias,
                                   Integer abiertosSinPlazoPropio,
                                   Integer abiertosConPlazoPropio) {

        static final PlazoDeLaPrueba SIN_DATO =
                new PlazoDeLaPrueba(null, null, null, null, null);
    }

    /** El alcance de {@code editar_vacante}, o vacío si quien pregunta no lo tiene. */
    private FiltroAlcance alcanceDeEdicionDe(ContextoUsuario quien) {
        return quien.tiene(PERMISO_EDITAR) ? permisos.alcanceDe(PERMISO_EDITAR) : null;
    }

    /** El alcance de {@code cerrar_vacante}, o vacío si quien pregunta no lo tiene. */
    private FiltroAlcance alcanceDeArchivoDe(ContextoUsuario quien) {
        return quien.tiene(PERMISO_ARCHIVAR) ? permisos.alcanceDe(PERMISO_ARCHIVAR) : null;
    }

    /** El alcance de {@code eliminar_vacante}, o vacío si quien pregunta no lo tiene. */
    private FiltroAlcance alcanceDeEliminacionDe(ContextoUsuario quien) {
        return quien.tiene(PERMISO_ELIMINAR) ? permisos.alcanceDe(PERMISO_ELIMINAR) : null;
    }

    /**
     * Si quien mira puede tocar ESTA vacante: tiene el permiso, su alcance la alcanza y la
     * vacante no está cerrada ni archivada.
     *
     * <p>Una cerrada no se edita —lo hace cumplir {@link #editar}—, así que el lápiz no
     * aparece: un botón que siempre contesta 409 es una promesa rota. Una archivada está
     * siempre cerrada, así que lo de la archivada ya estaría dicho; se escribe igual porque
     * la razón es otra y el día que se archive algo que no esté cerrado, esto no se cae.
     */
    private boolean puedeEditar(ContextoUsuario quien, FiltroAlcance alcanceDeEdicion,
                                Vacante vacante) {
        return alcanceDeEdicion != null
                && vacante.getArchivadaEn() == null
                && !ESTADO_CERRADA.equals(vacante.getEstado())
                && alcance.alcanzaALaVacante(quien, alcanceDeEdicion, vacante);
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
        laQueSePuedeTocar(quien, vacanteId);
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
        laQueSePuedeTocar(quien, vacanteId);
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
        Vacante vacante = laQueSePuedeTocar(quien, id);
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
        // Aquí había un freno más: sin texto legal publicado con SU nombre, la empresa no
        // recibía candidatos. Se cayó con la V54, que dejó UN SOLO texto de PROCESO para
        // todas, con un hueco donde va el nombre de la empresa: el texto ya existe siempre
        // y no hay nada que publicar. El freno solo llegaba a servir cuando el alta repartía
        // un borrador que nadie publicaba, y entonces el error salía en la cara de quien
        // abría la vacante en vez de en la de quien podía arreglarlo.
        vacante.setEstado("PUBLICADA");
        vacante.setPublicadaEn(Instant.now());
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_vacante",
                "vacante", id, Map.of("estado", "BORRADOR"), Map.of("estado", "PUBLICADA"), null);
    }

    @Override
    @Transactional
    public void asignarPlantillaEvaluacion(ContextoUsuario quien, Long id, Long plantillaEvaluacionId) {
        Vacante vacante = laQueSePuedeTocar(quien, id);
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
        Vacante vacante = laQueSePuedeTocar(quien, id);
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
        Vacante vacante = laQueSePuedeTocar(quien, id);
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
    public void activarCalificacionAutomatica(ContextoUsuario quien, Long id, boolean activa) {
        Vacante vacante = laQueSePuedeTocar(quien, id);
        if ("CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada no se edita");
        }
        /*
         * ⚠️ **No se exige tener la prueba lista para encenderlo, y es deliberado.**
         *
         * Sería tentador plantarse aquí como hace la evaluación del banco. Pero el orden
         * natural de montar una vacante es encender lo que se quiere y luego elegir el
         * instrumento, y bloquear el interruptor obligaría a hacerlo al revés. Lo que sí
         * está protegido es el efecto: el pase automático comprueba que haya con qué llenar
         * la etapa técnica y, si no lo hay, deja la postulación esperando a una persona en
         * vez de reventar. Publicar la vacante sigue exigiendo el instrumento, como siempre.
         */
        boolean anterior = vacante.isCalificacionAutomatica();
        vacante.setCalificacionAutomatica(activa);
        vacantes.save(vacante);
        auditoria.registrar(quien.organizacionId(), quien, "activar_calificacion_automatica",
                "vacante", id, Map.of("calificacionAutomatica", anterior),
                Map.of("calificacionAutomatica", activa), null);
    }

    @Override
    @Transactional
    public void asignarVersionPesos(ContextoUsuario quien, Long id, Long versionPesosId) {
        Vacante vacante = laQueSePuedeTocar(quien, id);
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
     * <p>⚠️ <b>La misma vara para todos</b>: cambiar de instrumento con gente que ya rindió
     * dejaría a unos medidos con un examen y a otros con otro, en la misma lista. La línea
     * es la <b>primera rendición</b>, no la primera postulación
     * ({@link #exigirVaraQuietaDelInstrumento}): postular no es rendir, y quien todavía no
     * ha abierto su prueba no ha visto nada que se le pueda mover debajo.
     *
     * <p>Los minutos son de la vacante y solo de esta etapa. Vacíos, rige lo que diga el
     * instrumento elegido; los del banco del perfil integral viajan con el banco y no se
     * tocan desde aquí.
     */
    @Override
    @Transactional
    public void elegirInstrumentoTecnico(ContextoUsuario quien, Long id,
                                         String instrumento, Integer minutos) {
        Vacante vacante = laQueSePuedeTocar(quien, id);
        if (!PLANTILLA.equals(instrumento) && !CUESTIONARIO_TECNICO.equals(instrumento)) {
            throw new IllegalArgumentException(
                    "El instrumento de la etapa técnica es «" + PLANTILLA + "» o «"
                            + CUESTIONARIO_TECNICO + "»; llegó «" + instrumento + "»");
        }
        if (minutos != null && minutos < MINUTOS_MINIMOS) {
            throw new IllegalArgumentException("La etapa técnica dura al menos "
                    + MINUTOS_MINIMOS + " minutos; para usar los del instrumento elegido se "
                    + "dejan vacíos");
        }

        // ⚠️ Los minutos son parte de la vara, no un ajuste cosmético: bajarlos a mitad de
        // tanda deja a los de antes con una hora y a los de después con diez minutos,
        // ordenados en la misma lista.
        //
        // Con una diferencia respecto al instrumento: los minutos no se congelan en ningún
        // sitio, los leen al empezar los DOS instrumentos —la prueba del puesto y el
        // cuestionario técnico—. Por eso corregirlos alcanza a todo el que aún no haya
        // abierto el suyo, y por eso la guarda de abajo mira quién ya empezó.
        //
        // ⚠️ Que los dos se comporten igual es la condición para que la guarda diga la
        // verdad: si uno de ellos congelara, este cambio se guardaría y se auditaría sin
        // llegarle a media tanda. Es lo que pasaba con el cuestionario hasta hoy.
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
     * Pariente de {@link #exigirVaraQuieta}, pero con la línea corrida: aquí frena la
     * primera <b>rendición</b>, no la primera postulación.
     *
     * <p>La regla que protege es la misma —todos los candidatos de una vacante se miden con
     * la misma vara— y por eso el mensaje se le parece. Lo que cambia es dónde cae la
     * frontera, y cambia porque la de aquella era más estricta de lo que hace falta:
     * <b>postular no es rendir</b>. Alguien que postuló el martes y todavía no ha abierto su
     * prueba no ha visto ningún enunciado ni ningún reloj, así que corregir los minutos —o
     * incluso el instrumento— no le mueve nada debajo. Con la línea vieja, una vacante con
     * un solo currículum dentro quedaba congelada hasta la siguiente convocatoria.
     *
     * <p>⚠️ <b>Se preguntan los DOS instrumentos, no el que la vacante tenga puesto.</b>
     * Cuesta una consulta más y ahorra un razonamiento frágil: una vacante que ya cambió de
     * instrumento antes puede tener gente que empezó con el otro, y esa gente también rindió.
     *
     * <p>⚠️ La frontera es «empezó», no «entregó»: quien tiene la prueba abierta ahora mismo
     * es precisamente a quien no se le puede mover el reloj.
     */
    private void exigirVaraQuietaDelInstrumento(Vacante vacante) {
        if ("BORRADOR".equals(vacante.getEstado())) {
            return;
        }
        if (intentos.algunoEmpezadoDeLaVacante(vacante.getId())
                || evaluaciones.algunaTecnicaEmpezadaDeLaVacante(vacante.getId())) {
            throw new IllegalStateException("Alguien de esta vacante ya empezó su etapa "
                    + "técnica, y lo que se rinde ahí no se cambia con gente dentro: todos "
                    + "sus candidatos se miden con la misma vara. Para estrenar otro "
                    + "instrumento, ábrelo en la siguiente convocatoria.");
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
        laQueSePuedeTocar(quien, vacanteId);
        // Un aviso que ya no sale por correo no se sustituye por otro texto: sería configurar
        // con cuidado algo que no llega a ninguna parte. Ver TextosDeCorreoRetirados.
        TextosDeCorreoRetirados.exigirQueSigaEnUso(datos.avisoCodigo());
        TextosDeCorreoRetirados.exigirQueSigaEnUso(datos.plantillaCodigo());
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
        laQueSePuedeTocar(quien, vacanteId);
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
        Vacante vacante = laQueSePuedeTocar(quien, vacanteId);
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
            // ⚠️ **Una plantilla CRONOMETRADA SÍ admite fecha, y aquí se rechazaba.** La
            // regla decía que la fecha «anularía el reloj», y dejó de ser cierta cuando
            // `ServicioPruebaImpl.iniciar` pasó a quedarse con el plazo que caiga ANTES entre
            // el reloj y la fecha de la vacante: quien abre temprano tiene sus minutos
            // completos y quien abre pegado a la fecha cierra a la fecha. Las dos cosas
            // conviven, y la fecha es justamente lo que impide empezar el examen la semana
            // siguiente a que cerrara la convocatoria.
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
        Vacante vacante = laQueSePuedeTocar(quien, id);
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

    // ============ Archivar y desarchivar ============

    /**
     * Retira de la lista habitual una vacante que ya terminó, sin perder nada de su proceso.
     *
     * <p><b>Las dos condiciones se vuelven a mirar AQUÍ</b>, aunque el panel ya las haya
     * enseñado en su modal. Entre abrir ese modal y confirmarlo caben minutos: una
     * postulación puede haber vuelto a la carrera, o el compañero de al lado puede haberla
     * archivado ya. Un modal es una foto; la decisión se toma sobre lo que hay.
     *
     * <ul>
     *   <li><b>Cerrada</b>, y no se cierra sola al intentar archivarla: cerrar tiene su
     *       motivo y su aviso a quien esté dentro, y hacerlo de rebote convertiría un gesto
     *       de orden en una decisión que nadie tomó.
     *   <li><b>Sin nadie en carrera</b>: quien sigue esperando una decisión no puede quedar
     *       fuera de la mesa de trabajo. La misma definición de «en carrera» de todo el
     *       sistema ({@code PostulacionesEnCarrera}).
     * </ul>
     *
     * <p>Archivar dos veces no es un error nuevo ni un archivo doble: se rechaza sin efectos,
     * como reenviar cualquier otra acción ya aplicada. Y no libera la solicitud de talento ni
     * deja ningún aviso: para el candidato no ha pasado nada, porque de verdad no ha pasado
     * nada en su proceso.
     */
    @Override
    @Transactional
    public void archivar(ContextoUsuario quien, Long id) {
        Vacante vacante = alcance.laVacanteVisible(quien, id, PERMISO_ARCHIVAR);
        if (vacante.getArchivadaEn() != null) {
            throw new IllegalStateException("Esta vacante ya está archivada");
        }
        if (!ESTADO_CERRADA.equals(vacante.getEstado())) {
            throw new IllegalStateException("Solo se archiva una vacante cerrada; esta está "
                    + enPalabras(vacante.getEstado()) + ". Ciérrala primero, con su motivo");
        }
        int quedan = enCarrera.cuantasEnLaVacante(id);
        if (quedan > 0) {
            throw new IllegalStateException("Quedan " + quedan + " postulantes en carrera. "
                    + "Decide cada uno, o descártalos en lote, desde la vacante antes de "
                    + "archivarla");
        }

        /*
         * ⚠️ **La condición viaja dentro del UPDATE, y no es un adorno.**
         *
         * Las cuatro comprobaciones de arriba leen; esta escribe. Entre lo uno y lo otro cabe
         * otra petición —un doble clic manda dos, separadas por milisegundos—, y con un
         * `save()` normal las dos archivarían: dos filas de auditoría diciendo las dos «de no
         * archivada a archivada», y `archivada_en` con la fecha de la segunda. La traza deja
         * de poder contestar cuándo se archivó.
         *
         * Con la condición dentro, la segunda espera a que la primera confirme, vuelve a
         * mirar y actualiza cero filas. Cero filas es «alguien se me adelantó», y se rechaza
         * igual que archivar una que ya lo estaba: sin efectos duplicados (punto 12).
         */
        Instant cuando = Instant.now();
        if (vacantes.archivarSiNoLoEstaba(id, cuando) == 0) {
            throw new IllegalStateException("Esta vacante ya está archivada");
        }
        auditoria.registrar(quien.organizacionId(), quien, "archivar_vacante",
                "vacante", id, Map.of("archivada", false),
                Map.of("archivada", true, "archivadaEn", cuando.toString()), null);
    }

    /**
     * La devuelve a la lista habitual, y no hace nada más.
     *
     * <p>Vuelve {@code CERRADA}, que es como estaba: desarchivar no reabre la convocatoria ni
     * mueve ninguna postulación, y el candidato ve su proceso exactamente igual antes y
     * después. Por eso tampoco avisa a nadie — no hay noticia que dar.
     */
    @Override
    @Transactional
    public void desarchivar(ContextoUsuario quien, Long id) {
        Vacante vacante = alcance.laVacanteVisible(quien, id, PERMISO_ARCHIVAR);
        if (vacante.getArchivadaEn() == null) {
            throw new IllegalStateException("Esta vacante no está archivada");
        }
        // La misma carrera al revés, y la misma defensa: dos peticiones seguidas no pueden
        // dejar dos filas de auditoría diciendo las dos que la devolvieron a la lista.
        Instant archivadaEn = vacante.getArchivadaEn();
        if (vacantes.desarchivarSiLoEstaba(id) == 0) {
            throw new IllegalStateException("Esta vacante no está archivada");
        }
        auditoria.registrar(quien.organizacionId(), quien, "desarchivar_vacante",
                "vacante", id,
                Map.of("archivada", true, "archivadaEn", archivadaEn.toString()),
                Map.of("archivada", false), null);
    }

    // ============ Eliminar ============

    /**
     * Retira la vacante que no debió existir, y con ella lo que colgaba de su existencia.
     *
     * <p><b>El orden de las cuatro cosas no es casual.</b>
     *
     * <ol>
     *   <li><b>Se mira quién pregunta y si la vacante existe.</b> Una ya eliminada no se
     *       encuentra —el guardián filtra por eliminada— y eso es el 404 de repetir la
     *       eliminación, sin volver a cerrar, auditar ni avisar.
     *   <li><b>Se exige el motivo antes de escribir nada.</b> Vacío o en blanco es un 400, y
     *       la comprobación vive aquí y no solo en el {@code @NotBlank}: un motivo que son
     *       tres espacios pasa la validación del contrato y no contesta nada a quien pregunte
     *       mañana por qué desapareció la convocatoria.
     *   <li><b>Se marca eliminada, con la condición dentro del UPDATE, ANTES de tocar las
     *       postulaciones.</b> Es el punto donde dos peticiones simultáneas se ordenan: la
     *       segunda espera a que la primera confirme, vuelve a mirar y actualiza cero filas.
     *       Si esto fuera lo último, las dos habrían cerrado ya las mismas postulaciones y
     *       mandado dos campanas por el mismo hecho antes de descubrir que una llegaba tarde.
     *   <li><b>Y después se cierra a quien estaba dentro y se libera la solicitud</b>, todo
     *       en la misma transacción: si algo de esto falla, tampoco queda marcada la
     *       eliminación.
     * </ol>
     *
     * <p><b>Los avisos van al final y fuera de la garantía.</b> La campana se publica en
     * transacción propia ({@code ServicioAvisosPortal}); uno que falle se anota y no deshace
     * la eliminación, que ya está decidida. Por eso la respuesta cuenta las dos cifras por
     * separado, y el panel dice la verdad aunque no coincidan.
     */
    @Override
    @Transactional
    public VacanteEliminadaResponse eliminar(ContextoUsuario quien, Long id,
                                             EliminarVacante datos) {
        Vacante vacante = alcance.laVacanteVisible(quien, id, PERMISO_ELIMINAR);
        String motivo = datos == null || datos.motivo() == null ? "" : datos.motivo().trim();
        if (motivo.isEmpty()) {
            throw new IllegalArgumentException("Di por qué se elimina la vacante: se cierran "
                    + "las postulaciones en carrera y queda en la auditoría");
        }

        // ⚠️ Lo que haga falta de la entidad se copia AHORA: el UPDATE de abajo vacía el
        // contexto de persistencia y la deja desligada.
        Long organizacionId = vacante.getOrganizacionId();
        String titulo = vacante.getTitulo();
        Long solicitudId = vacante.getSolicitudTalentoId();

        Instant cuando = Instant.now();
        if (vacantes.eliminarSiSeguiaViva(id, cuando) == 0) {
            // Alguien se adelantó. Para quien llega segundo la vacante ya no existe, y es
            // exactamente lo mismo que contestarle a quien repite la eliminación un rato
            // después: el mismo 404, sin efectos.
            throw new ResourceNotFoundException("Vacante", "id", id);
        }

        // Se leen DESPUÉS del UPDATE, y no antes: entre mirar y escribir cabe una postulación
        // nueva, y la lista que se cierra tiene que ser la que había cuando la vacante dejó
        // de existir. A partir de aquí nadie más puede postular — el portal ya no la ve.
        List<Postulacion> aCerrar = enCarrera.deLaVacante(id);
        for (Postulacion postulacion : aCerrar) {
            /*
             * Cierre de PERSONA y no del sistema: alguien decidió retirar la vacante, y eso
             * es tan humano como descartar a un candidato. Marcarlo como automático dejaría
             * catorce cierres sin nadie detrás.
             *
             * ⚠️ Y POR_LA_CAMPANA en vez del viejo `avisar = false`: el correo se calla
             * porque el aviso de abajo lo cuenta mucho mejor —un «tu postulación se cerró»
             * genérico no explica que la empresa retiró el puesto—, no porque se haya
             * decidido no contárselo. Con el booleano, el historial de la ficha se habría
             * quedado con la coletilla «sin avisar al candidato», que sería falsa.
             */
            maquina.transicionar(postulacion, ESTADO_CERRADA, quien,
                    "Se eliminó la vacante: " + motivo, false, false, CIERRE_POR_ELIMINACION,
                    MaquinaEstados.AvisoDeLaTransicion.POR_LA_CAMPANA);
        }

        // Y la solicitud vuelve a estar libre, que es para lo que se elimina una vacante mal
        // creada: para poder crear la correcta con el mismo respaldo de Dirección. Sin esto,
        // la solicitud se quedaría en CON_VACANTE señalando a una vacante que ya no existe, y
        // habría que pedir a Dirección que aprobara otra por un error de tecleo.
        liberarLaSolicitud(quien, solicitudId, titulo);

        auditoria.registrar(organizacionId, quien, "eliminar_vacante", "vacante", id,
                Map.of("eliminada", false),
                Map.of("eliminada", true, "eliminadaEn", cuando.toString(),
                        "postulacionesCerradas", String.valueOf(aCerrar.size())),
                motivo);

        /*
         * ⚠️ **Todo lo anterior baja a la base ANTES de publicar un solo aviso.**
         *
         * Los avisos se publican en transacción propia ({@code REQUIRES_NEW}), y eso los pone
         * fuera de la garantía de esta: confirman aunque lo de aquí se deshaga. Hibernate, sin
         * este empujón, guarda los cambios de las postulaciones hasta el final —así trabaja— y
         * un fallo de base al escribirlos saldría DESPUÉS de haber avisado. El resultado sería
         * el peor de todos: la vacante sigue ahí, las postulaciones siguen abiertas y a cada
         * candidato le ha llegado una campana diciendo que su proceso se cerró.
         *
         * Con el flush aquí, si algo de arriba no cabe en la base, se rompe ahora y no se
         * avisa a nadie. Lo de después sigue valiendo: un aviso que falla no deshace nada.
         */
        postulaciones.flush();

        int avisados = avisarDeLaEliminacion(organizacionId, titulo, aCerrar);
        return new VacanteEliminadaResponse(aCerrar.size(), avisados);
    }

    /**
     * Devuelve a {@code ABIERTA} la solicitud que respaldaba la vacante.
     *
     * <p>Solo si sigue en {@code CON_VACANTE}: si alguien ya la cerró o la anuló por su
     * cuenta, reabrirla sería deshacer una decisión que nadie ha pedido deshacer. Y si la
     * solicitud ya no está —una base vieja, un borrado de soporte—, la eliminación no se cae
     * por eso: lo que se estaba retirando es la vacante.
     */
    private void liberarLaSolicitud(ContextoUsuario quien, Long solicitudId, String titulo) {
        if (solicitudId == null) {
            return;
        }
        solicitudes.findByIdAndOrganizacionId(solicitudId, quien.organizacionId())
                .filter(s -> "CON_VACANTE".equals(s.getEstado()))
                .ifPresent(solicitud -> {
                    solicitud.setEstado("ABIERTA");
                    solicitudes.save(solicitud);
                    auditoria.registrar(quien.organizacionId(), quien, "liberar_solicitud",
                            "solicitud_talento", solicitud.getId(),
                            Map.of("estado", "CON_VACANTE"), Map.of("estado", "ABIERTA"),
                            "Se eliminó la vacante «" + titulo + "» que la respaldaba");
                });
    }

    /**
     * Le cuenta a cada persona que estaba dentro que la vacante se retiró.
     *
     * <p><b>Sin enlace, y es la única diferencia con los otros dos avisos.</b> El proceso al
     * que llevaría ya no se puede abrir: un aviso que lleva a un 404 hace creer al candidato
     * que se rompió algo suyo, justo cuando acaba de perder el puesto por algo que no hizo.
     *
     * <p>El texto dice las dos cosas que le importan —qué pasó y que no tiene nada que
     * hacer— y no dice el motivo que escribió el equipo: ese es de la auditoría, y casi
     * siempre habla de un error interno de la empresa.
     *
     * @return a cuánta gente le llegó de verdad
     */
    private int avisarDeLaEliminacion(Long organizacionId, String titulo,
                                      List<Postulacion> cerradas) {
        String tituloAviso = "Se retiró la vacante «" + titulo + "»";
        String cuerpo = "La empresa retiró esta vacante y tu postulación quedó cerrada. "
                + "No tienes que hacer nada";
        int avisados = 0;
        for (Postulacion postulacion : cerradas) {
            try {
                AvisoPortal publicado = avisos.publicar(organizacionId,
                        postulacion.getUsuarioId(), AvisoPortal.VACANTE_ELIMINADA, tituloAviso,
                        cuerpo,
                        // Los dos enlaces vacíos, a propósito: ni al proceso ni a la vacante.
                        null, null);
                if (publicado != null) {
                    avisados++;
                }
            } catch (RuntimeException e) {
                log.error("No se pudo avisar de la eliminación a la postulación {}: {}",
                        postulacion.getId(), e.getMessage());
            }
        }
        return avisados;
    }

    /** El estado, dicho como se lee en la pantalla y no como se guarda. */
    private String enPalabras(String estado) {
        return switch (estado == null ? "" : estado) {
            case "BORRADOR" -> "en borrador";
            case ESTADO_PUBLICADA -> "publicada";
            default -> String.valueOf(estado).toLowerCase(Locale.ROOT);
        };
    }

    // ============ ayudas ============

    /**
     * La vacante de la empresa de quien pregunta, si todavía existe.
     *
     * <p>Una eliminada no sale de aquí y por eso su detalle, sus requisitos, su ficha y sus
     * textos de correo contestan 404 sin que ninguno tenga que preguntarlo (V60). Archivada
     * sí sale: archivar conserva la consulta, eliminar la retira — es la diferencia entera
     * entre las dos acciones, dicha en una consulta.
     */
    private Vacante laDeLaOrganizacion(ContextoUsuario quien, Long id) {
        return vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", id));
    }

    /**
     * La misma vacante, pero solo si todavía se puede mover.
     *
     * <p>Lo usan TODAS las acciones que escriben, y por eso es un método y no una línea
     * repetida: la lista de entradas que tienen que respetar el archivo es larga —publicar,
     * cerrar, los cuatro instrumentos, los pesos, el cierre de la prueba, los requisitos, los
     * textos de correo— y la que se olvide sería un agujero que no da ninguna señal.
     * Consultar sigue entrando por {@link #laDeLaOrganizacion}: una archivada se lee entera.
     */
    private Vacante laQueSePuedeTocar(ContextoUsuario quien, Long id) {
        Vacante vacante = laDeLaOrganizacion(quien, id);
        VacanteArchivada.exigirQueNoLoEste(vacante);
        return vacante;
    }

    private VacantePanel comoPanel(Vacante v, int postulantesEnCarrera, boolean puedeEditar,
                                   FiltroAlcance alcanceDeArchivo,
                                   FiltroAlcance alcanceDeEliminacion, ContextoUsuario quien,
                                   PlazoDeLaPrueba plazo) {
        boolean alcanzaParaArchivar = alcanceDeArchivo != null
                && alcance.alcanzaALaVacante(quien, alcanceDeArchivo, v);
        // ⚠️ `puedeArchivar` NO mira cuánta gente sigue en carrera. El icono tiene que estar
        // para que su modal pueda decir «quedan N, decídelos antes»: escondiéndolo, quien
        // mira la fila no tiene forma de saber por qué esa vacante no se puede guardar.
        boolean puedeArchivar = alcanzaParaArchivar
                && v.getArchivadaEn() == null
                && ESTADO_CERRADA.equals(v.getEstado());
        boolean puedeDesarchivar = alcanzaParaArchivar && v.getArchivadaEn() != null;
        // ⚠️ La papelera NO mira el estado ni cuánta gente sigue dentro. Un borrador mal
        // creado, una publicada, una cerrada y una archivada se eliminan igual —es lo que
        // hace distinta a esta acción del archivo—, y lo que hay gente dentro lo cuenta el
        // modal, que para eso recibe `postulantesEnCarrera`. Lo único que decide si aparece
        // es el permiso y su alcance.
        boolean puedeEliminar = alcanceDeEliminacion != null
                && alcance.alcanzaALaVacante(quien, alcanceDeEliminacion, v);
        return new VacantePanel(v.getId(), v.getTitulo(), v.getEstado(), v.getTipoCierre(),
                v.getPuestoId(), v.getSolicitudTalentoId(), v.getResponsableUsuarioId(),
                v.getPublicadaEn(), v.getCerradaEn(), v.isAplicaEvaluacion(),
                v.getPlantillaEvaluacionId(), v.getVersionPlantillaPruebaId(),
                v.getVersionPesosId(), v.getInstrumentoEtapaTecnica(),
                v.getMinutosEtapaTecnica(), v.isCalificacionAutomatica(),
                new RemuneracionDeLaVacante(Remuneracion.tipoDe(v), v.getRemuneracionMin(),
                        v.getRemuneracionMax(), v.getRemuneracionMoneda()),
                v.getRemuneracionActualizadaEn(),
                v.getDescripcion(), v.getProposito(), v.getResponsabilidades(),
                v.getRequisitos(), v.getModalidad(), v.getHorario(), v.getUbicacion(),
                v.getPlazas(), v.getAbreEn(), v.getCierraEn(),
                postulantesEnCarrera, v.getArchivadaEn(), puedeEditar,
                puedeArchivar, puedeDesarchivar, puedeEliminar,
                v.getPruebaCierraEn(), plazo.modalidad(), plazo.minutos(), plazo.dias(),
                plazo.abiertosSinPlazoPropio(), plazo.abiertosConPlazoPropio());
    }

    // ============ La remuneración ============

    @Override
    @Transactional
    public RemuneracionActualizadaResponse actualizarRemuneracion(
            ContextoUsuario quien, Long id, ActualizarRemuneracion datos) {
        Vacante vacante = alcance.laVacanteVisible(quien, id, PERMISO_EDITAR);
        // La otra puerta del sueldo, con la misma guarda que el formulario: la tarjeta del
        // detalle sigue siendo visible en una archivada —el monto es información del
        // proceso— y su botón tiene que encontrarse el 409 igual que el PUT.
        VacanteArchivada.exigirQueNoLoEste(vacante);
        if (ESTADO_CERRADA.equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada no cambia de sueldo");
        }
        exigirQueLaSimetriaNoSeRevoque(vacante, datos.remuneracion());

        // Lo de antes se escribe ANTES de tocar nada: es la mitad de lo que dice el aviso, y
        // leerlo después daría las dos veces el valor nuevo.
        String antes = Remuneracion.escribir(vacante);
        Map<String, Object> valorAnterior = comoMapa(vacante);
        Instant marcaAnterior = vacante.getRemuneracionActualizadaEn();

        escribirRemuneracion(vacante, datos.remuneracion(), Instant.now());
        String ahora = Remuneracion.escribir(vacante);

        if (antes.equals(ahora)) {
            // Guardar «lo mismo que ya había» no es un cambio, y avisar de él sería gastarle
            // la atención a los candidatos con una noticia vacía. Pasa más de lo que parece:
            // el panel manda el formulario entero, y quien entra a mirar y pulsa guardar sin
            // tocar nada llega exactamente aquí.
            //
            // Tampoco se mueve la marca de «actualizada»: el portal seguiría pintando
            // «actualizado el …» con la fecha de hoy sobre un número que no cambió. Se
            // devuelve la que había, sobre la entidad gestionada, porque esta transacción ya
            // le escribió encima la de ahora.
            vacante.setRemuneracionActualizadaEn(marcaAnterior);
            return new RemuneracionActualizadaResponse(antes, ahora, 0);
        }

        vacantes.save(vacante);

        auditoria.registrar(quien.organizacionId(), quien, "actualizar_remuneracion",
                "vacante", id, valorAnterior, comoMapa(vacante), datos.motivo());

        // Y ahora la parte que le importa a la gente: contarlo.
        //
        // En BORRADOR esto devuelve cero sin más —no hay nadie postulado todavía— y ese es el
        // camino normal de rellenar el sueldo antes de publicar.
        int avisados = avisarDelCambio(vacante, antes, ahora);
        return new RemuneracionActualizadaResponse(antes, ahora, avisados);
    }

    /**
     * Publicar el sueldo, o no publicarlo, se decide ANTES de publicar la vacante.
     *
     * <p>El trato de la V55 se cobra por adelantado: la vacante enseña lo que paga y, a
     * cambio, cada persona que postula está obligada a decir lo suyo. Sin esta guarda, una
     * empresa podía publicar un rango, recoger cuarenta pretensiones obligatorias y volver a
     * {@code OCULTA} al día siguiente — quedándose lo cobrado y retirando lo pagado. Dos
     * clics para deshacer la única regla que sostiene esto.
     *
     * <p>Y al revés también se cierra, aunque parezca inofensivo: encender el sueldo de una
     * vacante que ya recibió postulaciones deja una tanda partida en dos —los de antes sin
     * cifra, los de después con ella— que ninguna pantalla puede comparar de frente. Quien
     * postuló bajo las reglas viejas no puede volver atrás a declarar nada, porque el trato
     * se juzga con lo que había el día que cada uno envió su candidatura.
     *
     * <p>Lo que <b>sí</b> se puede cambiar en una vacante publicada es el <b>monto</b>: subir
     * el rango, cerrarlo en una cifra fija, bajarlo. Eso no revoca nada —el sueldo se sigue
     * enseñando— y es justo el cambio que le llega a cada candidato por correo y por su
     * campana. Moverse entre {@code FIJA} y {@code RANGO} entra ahí: las dos publican.
     *
     * <p>En BORRADOR no hay nada que proteger: nadie ha postulado todavía y la decisión es
     * exactamente la que esta guarda quiere que se tome con calma, antes de abrir la puerta.
     */
    private void exigirQueLaSimetriaNoSeRevoque(Vacante vacante, RemuneracionDeLaVacante datos) {
        if (!"PUBLICADA".equals(vacante.getEstado())) {
            return;
        }
        boolean laEnsenaba = Remuneracion.laEnsena(vacante);
        String tipoNuevo = datos == null ? Remuneracion.OCULTA : datos.tipo();
        boolean laEnsenara = !Remuneracion.OCULTA.equals(tipoNuevo);

        if (laEnsenaba && !laEnsenara) {
            throw new IllegalStateException(
                    "Esta vacante ya está publicada enseñando lo que paga, y a cada persona "
                            + "que postuló se le exigió decir cuánto quiere ganar. Dejar de "
                            + "publicarla ahora sería quedarse con lo que dijeron sin dar nada "
                            + "a cambio. El monto sí se puede cambiar; si de verdad hay que "
                            + "esconderlo, ciérrala y abre otra");
        }
        if (!laEnsenaba && laEnsenara) {
            throw new IllegalStateException(
                    "Esta vacante se publicó sin enseñar lo que paga, así que a quienes ya "
                            + "postularon no se les pidió su pretensión y no hay forma de "
                            + "volver atrás a pedírsela. Publicar el sueldo ahora dejaría media "
                            + "tanda con cifra y media sin ella: decídelo antes de publicar, o "
                            + "ciérrala y abre otra");
        }
    }

    /**
     * Escribe los cinco campos del sueldo en una vacante que ya existe, validados y
     * normalizados, y le pone la marca de cuándo se tocó.
     *
     * <p>Una remuneración nula se lee como OCULTA y no como «no lo toques»: el cuerpo lo manda
     * el panel entero cada vez, y un campo ausente significa que la pantalla decidió que no
     * hay sueldo publicado.
     */
    private void escribirRemuneracion(Vacante vacante, RemuneracionDeLaVacante datos,
                                      Instant cuando) {
        RemuneracionDeLaVacante lo = datos == null ? RemuneracionDeLaVacante.OCULTA : datos;
        String moneda = Remuneracion.validar(lo.tipo(), lo.min(), lo.max(), lo.moneda());

        vacante.setRemuneracionTipo(lo.tipo());
        if (Remuneracion.OCULTA.equals(lo.tipo())) {
            // Se limpian los montos en lugar de dejarlos donde estaban. Una vacante OCULTA con
            // cifras guardadas es un sueldo que sigue en la base esperando a que alguien lo
            // lea por descuido, y la restricción de la V55 tampoco lo admitiría.
            vacante.setRemuneracionMin(null);
            vacante.setRemuneracionMax(null);
            vacante.setRemuneracionMoneda(null);
        } else {
            vacante.setRemuneracionMin(lo.min());
            vacante.setRemuneracionMax(Remuneracion.FIJA.equals(lo.tipo()) ? null : lo.max());
            vacante.setRemuneracionMoneda(moneda);
        }
        if (cuando != null) {
            vacante.setRemuneracionActualizadaEn(cuando);
        }
    }

    private Map<String, Object> comoMapa(Vacante v) {
        Map<String, Object> mapa = new java.util.HashMap<>();
        mapa.put("tipo", Remuneracion.tipoDe(v));
        mapa.put("min", v.getRemuneracionMin());
        mapa.put("max", v.getRemuneracionMax());
        mapa.put("moneda", v.getRemuneracionMoneda());
        return mapa;
    }

    /**
     * Le cuenta a cada persona en carrera que el sueldo de su vacante cambió.
     *
     * <p>⚠️ <b>Solo por la campana, desde esta entrega.</b> Antes salía también un correo, y
     * se retiró a propósito: la campana se queda quieta hasta que la persona entra y la ve,
     * mientras que el correo se pierde —cae en promociones, llega a una dirección que el
     * cargador de currículums inventó—. Mandar los dos hacía prometer al panel una entrega
     * que nadie podía confirmar; el texto de correo {@code REMUNERACION_ACTUALIZADA} se
     * conserva en la base, pero ya no se manda ni se ofrece para editar.
     *
     * <p>⚠️ <b>Solo a quien sigue en carrera.</b> A quien ya no continúa —descartado,
     * retirado, cerrado— no se le avisa: la noticia no le afecta, y contarle lo que paga un
     * puesto que ya perdió es recordárselo sin ganar nada.
     *
     * @return a cuánta gente se le avisó
     */
    private int avisarDelCambio(Vacante vacante, String antes, String ahora) {
        String titulo = "Cambió la remuneración de «" + vacante.getTitulo() + "»";
        String cuerpo = "Antes: " + antes + " · Ahora: " + ahora + ". "
                + CambiosDeLaVacante.CIERRE;
        return avisarALosDeLaVacante(vacante, AvisoPortal.REMUNERACION_ACTUALIZADA, titulo,
                cuerpo);
    }

    /**
     * El aviso de que la convocatoria cambió: uno solo, con todo lo que cambió dentro.
     *
     * <p><b>Uno y no uno por campo.</b> Quien corrige el horario, la ubicación y el sueldo de
     * una tacada hace un cambio, no tres, y tres campanas seguidas por el mismo guardado se
     * leen como un fallo del sistema. El texto lo arma {@link CambiosDeLaVacante}.
     *
     * <p>En BORRADOR no hay nadie a quien contárselo, y los cambios que solo tocan lo interno
     * —quién lleva el proceso, cuántas plazas, cuándo cierra— no salen de la empresa.
     */
    private int avisarDeLaEdicion(Vacante vacante, CambiosDeLaVacante cambios) {
        if (!ESTADO_PUBLICADA.equals(vacante.getEstado()) || !cambios.hayVisibles()) {
            return 0;
        }
        return avisarALosDeLaVacante(vacante, AvisoPortal.VACANTE_ACTUALIZADA,
                CambiosDeLaVacante.tituloDelAviso(vacante.getTitulo()),
                cambios.cuerpoDelAviso());
    }

    /**
     * Deja el mismo aviso en la campana de cada postulación en carrera.
     *
     * <p>⚠️ <b>Un fallo aquí no deshace el cambio.</b> La vacante ya está guardada y
     * auditada; que a una persona no se le pueda escribir no puede revertir lo que el panel
     * decidió, ni impedir que los demás se enteren. Se anota y se sigue con las siguientes.
     *
     * <p>El contador cuenta los avisos que de verdad se publicaron —{@code publicar} devuelve
     * {@code null} cuando falla—, porque es la cifra que el panel dice en voz alta.
     *
     * @return a cuánta gente se le avisó
     */
    private int avisarALosDeLaVacante(Vacante vacante, String tipo, String titulo,
                                      String cuerpo) {
        int avisados = 0;
        for (Postulacion postulacion : enCarrera.deLaVacante(vacante.getId())) {
            try {
                AvisoPortal publicado = avisos.publicar(vacante.getOrganizacionId(),
                        postulacion.getUsuarioId(), tipo, titulo, cuerpo,
                        postulacion.getId(), vacante.getId());
                if (publicado != null) {
                    avisados++;
                }
            } catch (RuntimeException e) {
                log.error("No se pudo avisar «{}» a la postulación {}: {}", tipo,
                        postulacion.getId(), e.getMessage());
            }
        }
        return avisados;
    }
}
