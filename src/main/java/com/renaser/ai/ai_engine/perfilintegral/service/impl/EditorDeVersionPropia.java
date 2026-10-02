package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Recalificacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.TextoDe;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VacanteCopiable;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
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
import com.renaser.ai.ai_engine.perfilintegral.service.EditorDeLaVacante;
import com.renaser.ai.ai_engine.perfilintegral.service.PreguntasInvalidasException;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.OpcionAValidar;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

/**
 * Lo que hacen igual los dos editores de una vacante: el de sus preguntas propias (V66, fase 1)
 * y el de su prueba del puesto (V67). Los dos guardan un borrador y una versión publicada por
 * vacante y propósito, con criterios, preguntas y opciones, y se publican, se copian, corrigen
 * las instrucciones de la IA y reintentan la recalificación de la misma manera.
 *
 * <p>Cada editor pone su propósito: de qué versiones habla, desde cuándo su vara ya no se
 * mueve, cómo se copia y se borra un borrador entero, cómo se pinta, quién rindió y sus
 * textos. <b>La calificación no está aquí</b> (decisión 3 de la spec de la prueba): la prueba
 * califica el criterio entero y las preguntas propias, pregunta a pregunta.
 */
@Slf4j
public abstract class EditorDeVersionPropia implements EditorDeLaVacante {

    protected static final String BORRADOR = "BORRADOR";
    protected static final String PUBLICADA = "PUBLICADA";

    private static final String PERMISO_VER = "ver_vacantes";
    private static final String PERMISO_EDITAR = "editar_vacante";
    private static final String EN_CURSO = "EN_CURSO";

    /** Con qué trabaja el editor: cada servicio pone las suyas. */
    public record Piezas(AlcanceSobreLaVacante alcance, Permisos permisos,
                         VacanteRepository vacantes, PuestoRepository puestos,
                         VersionBancoRepository versiones, CriterioBancoRepository criterios,
                         PreguntaRepository preguntas, OpcionRepository opciones,
                         PropuestaPreguntasRepository propuestas, ColaCalificacionIa cola,
                         ServicioAuditoria auditoria) {
    }

    /**
     * Lo que cada propósito dice a su manera.
     *
     * @param proposito         el {@code version_banco.proposito} de sus versiones
     * @param etiqueta          con qué empieza la etiqueta de un borrador nuevo
     * @param varaQuieta        el porqué de todos los 409 de «la vara ya no se mueve»
     * @param sinBorrador       cuando se cambia algo y no hay borrador
     * @param nadaQuePublicar   cuando se publica y no hay borrador
     * @param noSePublica       con qué empieza la lista de lo que impide publicar
     * @param sinPublicada      cuando se toca la publicada y no la hay
     * @param publicadaDeOtra   el recurso del 404 al copiar de otra vacante
     * @param suPublicada       «las preguntas publicadas de esta vacante», para los 400
     * @param alPublicar        la acción de auditoría al publicar
     * @param alCorregir        la acción de auditoría al corregir las instrucciones
     * @param alCopiar          la acción de auditoría al copiar
     */
    public record Textos(String proposito, String etiqueta, String varaQuieta,
                         String sinBorrador, String nadaQuePublicar, String noSePublica,
                         String sinPublicada, String publicadaDeOtra, String suPublicada,
                         String alPublicar, String alCorregir, String alCopiar) {
    }

    /** Una pregunta como queda al editarla, ya validada por su editor. */
    public record PreguntaEscrita(String tipo, String enunciado, int puntos, Long criterioId,
                                  String queDebeTener, List<OpcionAValidar> opciones) {
    }

    /** Lo que un editor corrige además de la guía, los criterios y las abiertas. */
    @FunctionalInterface
    protected interface MasInstrucciones {
        void corregir(VersionBanco publicada, Map<String, Object> antes, Map<String, Object> despues);
    }

    // ============================== Lo que pone cada propósito ==============================

    protected abstract Piezas piezas();

    protected abstract Textos textos();

    /** Su versión de la vacante en ese estado. */
    protected abstract Optional<VersionBanco> version(Long vacanteId, String estado);

    /** Desde cuándo ya no se cambia el contenido: la primera postulación o la primera rendición. */
    protected abstract boolean laVaraNoSeMueve(Vacante vacante);

    /** El editor entero, como lo pinta el panel. */
    protected abstract EditorDePreguntas editor(ContextoUsuario quien, Vacante vacante);

    protected abstract VersionDePreguntas comoVersion(VersionBanco version);

    /** Todo lo que impide publicar el borrador, dicho entero. */
    protected abstract List<String> faltasDelBorrador(VersionBanco borrador);

    /** Copia el contenido de una versión en otra: es una copia, no un enlace. */
    protected abstract void copiarContenido(VersionBanco origen, VersionBanco destino);

    /** Un borrador se descarta entero: nada que alguien haya rendido apunta a él. */
    protected abstract void vaciarYBorrar(VersionBanco borrador);

    /** Las versiones publicadas de este propósito en la empresa, para copiar de ellas. */
    protected abstract List<VersionBanco> publicadasDe(Long organizacionId);

    /**
     * Por postulación, con qué guía se calculó cada nota de la IA que nadie ajustó a mano: la
     * gente a la que una guía nueva obliga a recalificar.
     */
    protected abstract Map<Long, List<Integer>> guiasDeLaIa(Vacante vacante, VersionBanco publicada);

    /** Cuántos entregaron lo que se rinde con esta versión. */
    protected abstract int cuantosRindieron(Vacante vacante, VersionBanco publicada);

    /** Cómo va la recalificación de cada postulación. */
    protected abstract Map<Long, ColaCalificacionIa.Seguimiento> seguimiento(List<Long> postulacionIds);

    /** Pone la recalificación en la cola; falso si ya había una en marcha. */
    protected abstract boolean recalificar(Long postulacionId);

    /** Lo que se suelta antes de borrar un criterio del borrador. Nada, salvo que se diga. */
    protected void antesDeQuitarElCriterio(Long criterioId) {
        // Las preguntas propias no tienen nada más que soltar: sus preguntas ya quedaron sin él.
    }

    // ============================== Leer ==============================

    @Override
    @Transactional(readOnly = true)
    public EditorDePreguntas ver(ContextoUsuario quien, Long vacanteId) {
        return editor(quien, laVisible(quien, vacanteId));
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
    public EditorDePreguntas descartarBorrador(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        version(vacante.getId(), BORRADOR).ifPresent(this::vaciarYBorrar);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas quitarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        CriterioBanco criterio = elCriterio(borrador, criterioId);
        // Sus preguntas no se borran: quedan «sin criterio» hasta que alguien las mueva, y
        // mientras tanto la versión no se publica. Borrarlas en cascada sería perder trabajo
        // por un clic en la X del bloque.
        PreguntaRepository preguntas = piezas().preguntas();
        List<Pregunta> suyas = preguntas.findByVersionBancoIdOrderByOrden(borrador.getId()).stream()
                .filter(p -> criterioId.equals(p.getCriterioBancoId()))
                .toList();
        int siguiente = siguienteOrden(borrador.getId(), null);
        for (Pregunta p : suyas) {
            p.setCriterioBancoId(null);
            p.setOrden(siguiente++);
        }
        preguntas.saveAllAndFlush(suyas);
        antesDeQuitarElCriterio(criterio.getId());
        piezas().criterios().delete(criterio);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas moverCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                          Mover datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        CriterioBancoRepository criterios = piezas().criterios();
        List<CriterioBanco> todos = new ArrayList<>(
                criterios.findByVersionBancoIdOrderByOrdenAscIdAsc(borrador.getId()));
        int i = indiceDe(todos, criterioId, CriterioBanco::getId, "Criterio");
        int j = i + paso(datos);
        if (j >= 0 && j < todos.size()) {
            renumerar(todos, i, j, CriterioBanco::setOrden);
            criterios.saveAll(todos);
        }
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas quitarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId) {
        Vacante vacante = laEditable(quien, vacanteId);
        Pregunta pregunta = laPregunta(elBorradorQueYaExiste(vacante), preguntaId);
        piezas().opciones().deleteByPreguntaIdIn(List.of(pregunta.getId()));
        piezas().preguntas().delete(pregunta);
        return editor(quien, vacante);
    }

    @Override
    @Transactional
    public EditorDePreguntas moverPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                          Mover datos) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco borrador = elBorradorQueYaExiste(vacante);
        Pregunta pregunta = laPregunta(borrador, preguntaId);
        PreguntaRepository preguntas = piezas().preguntas();
        // Se mueve dentro de su criterio: pasarla a otro es el selector de la pregunta.
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

    @Override
    @Transactional
    public EditorDePreguntas publicar(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        Textos textos = textos();
        VersionBanco borrador = version(vacante.getId(), BORRADOR)
                .orElseThrow(() -> new IllegalStateException(textos.nadaQuePublicar()));
        exigirSinFaltas(textos.noSePublica(), faltasDelBorrador(borrador));

        // Publicar reemplaza a la anterior, que se archiva (nada se borra, RF-138). Desde que
        // la vara ya no se mueve, no.
        VersionBancoRepository versiones = piezas().versiones();
        Optional<VersionBanco> anterior = version(vacante.getId(), PUBLICADA);
        if (anterior.isPresent()) {
            if (laVaraNoSeMueve(vacante)) {
                throw new IllegalStateException(textos.varaQuieta());
            }
            VersionBanco saliente = anterior.get();
            saliente.setEstado("ARCHIVADA");
            // saveAndFlush: el índice «una publicada por vacante y propósito» no perdona que el
            // borrador pase a PUBLICADA antes de que esta se archive (Hibernate inserta y
            // actualiza en su propio orden).
            versiones.saveAndFlush(saliente);
        }
        borrador.setEstado(PUBLICADA);
        borrador.setPublicadaPorUsuarioId(quien.usuarioId());
        borrador.setPublicadaEn(Instant.now());
        versiones.save(borrador);
        piezas().auditoria().registrar(quien.organizacionId(), quien, textos.alPublicar(),
                "version_banco", borrador.getId(), Map.of("estado", BORRADOR),
                Map.of("estado", PUBLICADA, "vacante", vacante.getId()), null);
        return editor(quien, vacante);
    }

    // ============================== Con la versión publicada ==============================

    /**
     * Corrige la guía, el «qué evalúa» de los criterios y el «qué debe tener» de las abiertas
     * de la versión publicada, más lo que el editor corrija aparte. Sin saldo o con la IA
     * apagada NO se guarda nada: guardarlo sin recalificar dejaría a unos medidos con una guía
     * y a otros con otra.
     */
    protected CambioAplicado corregir(ContextoUsuario quien, Long vacanteId, String guia,
                                      List<TextoDe> criterios, List<TextoDe> abiertas,
                                      MasInstrucciones mas) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco publicada = laPublicada(vacante);
        Map<Long, List<Integer>> conNota = guiasDeLaIa(vacante, publicada);
        exigirQueNoHayaRecalificacionEnCurso(conNota.keySet());
        Piezas piezas = piezas();
        if (!conNota.isEmpty()) {
            String motivo = piezas.cola().porQueNoSePuedeUsarLaIa(vacante.getOrganizacionId());
            if (motivo != null) {
                throw new IllegalStateException(motivo + " No se guardó el cambio: guardarlo "
                        + "sin recalificar dejaría a unos candidatos medidos con una guía y a "
                        + "otros con otra.");
            }
        }

        Map<String, Object> antes = new LinkedHashMap<>();
        Map<String, Object> despues = new LinkedHashMap<>();
        if (guia != null) {
            String nueva = textoONulo(guia);
            if (!Objects.equals(nueva, publicada.getGuiaCalificacion())) {
                antes.put("guiaCalificacion", String.valueOf(publicada.getGuiaCalificacion()));
                despues.put("guiaCalificacion", String.valueOf(nueva));
                publicada.setGuiaCalificacion(nueva);
            }
        }
        String suPublicada = textos().suPublicada();
        Map<Long, CriterioBanco> suyos = piezas.criterios()
                .findByVersionBancoIdOrderByOrdenAscIdAsc(publicada.getId()).stream()
                .collect(Collectors.toMap(CriterioBanco::getId, Function.identity()));
        for (TextoDe t : lista(criterios)) {
            CriterioBanco c = suyos.get(t.id());
            if (c == null) {
                throw new IllegalArgumentException("El criterio " + t.id() + " no es de " + suPublicada);
            }
            String nuevo = textoONulo(t.texto());
            if (!Objects.equals(nuevo, c.getQueEvalua())) {
                antes.put("criterio " + c.getId() + " · qué evalúa", String.valueOf(c.getQueEvalua()));
                despues.put("criterio " + c.getId() + " · qué evalúa", String.valueOf(nuevo));
                c.setQueEvalua(nuevo);
                piezas.criterios().save(c);
            }
        }
        Map<Long, Pregunta> suyas = piezas.preguntas().findByVersionBancoIdOrderByOrden(publicada.getId())
                .stream().collect(Collectors.toMap(Pregunta::getId, Function.identity()));
        for (TextoDe t : lista(abiertas)) {
            Pregunta p = suyas.get(t.id());
            if (p == null || !ReglasDePuntos.ABIERTA.equals(p.getTipo())) {
                throw new IllegalArgumentException("La pregunta " + t.id() + " no es una abierta de "
                        + suPublicada);
            }
            String nuevo = textoONulo(t.texto());
            if (!Objects.equals(nuevo, p.getQueDebeTener())) {
                antes.put("pregunta " + p.getId() + " · qué debe tener", String.valueOf(p.getQueDebeTener()));
                despues.put("pregunta " + p.getId() + " · qué debe tener", String.valueOf(nuevo));
                p.setQueDebeTener(nuevo);
                piezas.preguntas().save(p);
            }
        }
        mas.corregir(publicada, antes, despues);
        if (antes.isEmpty()) {
            return new CambioAplicado(0);
        }

        // Cada guía tiene su número; cada nota guarda con cuál se calculó. Un resultado que
        // llegue calculado con la anterior se descarta y se vuelve a pedir.
        int anterior = publicada.getVersionGuia() == null ? 1 : publicada.getVersionGuia();
        publicada.setVersionGuia(anterior + 1);
        piezas.versiones().save(publicada);
        antes.put("versionGuia", anterior);
        despues.put("versionGuia", anterior + 1);
        piezas.auditoria().registrar(quien.organizacionId(), quien, textos().alCorregir(),
                "version_banco", publicada.getId(), antes, despues, null);

        int encoladas = 0;
        for (Long postulacionId : conNota.keySet()) {
            if (recalificar(postulacionId)) {
                encoladas++;
            }
        }
        log.info("Instrucciones de la IA corregidas ({}) en la vacante {} (guía {}): {} personas a "
                        + "recalificar, {} encoladas", textos().proposito(), vacante.getId(), anterior + 1,
                conNota.size(), encoladas);
        return new CambioAplicado(conNota.size());
    }

    @Override
    @Transactional
    public CambioAplicado reintentarRecalificacion(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = laEditable(quien, vacanteId);
        VersionBanco publicada = laPublicada(vacante);
        int vigente = publicada.getVersionGuia() == null ? 1 : publicada.getVersionGuia();
        Map<Long, List<Integer>> conNota = guiasDeLaIa(vacante, publicada);
        Map<Long, ColaCalificacionIa.Seguimiento> deCadaUno = seguimiento(List.copyOf(conNota.keySet()));
        List<Long> pendientes = new ArrayList<>();
        conNota.forEach((postulacionId, guias) -> {
            boolean viejas = guias.stream().anyMatch(g -> !Objects.equals(g, vigente));
            ColaCalificacionIa.Seguimiento s = deCadaUno.get(postulacionId);
            if (viejas && (s == null || !EN_CURSO.equals(s.estado()))) {
                pendientes.add(postulacionId);
            }
        });
        if (pendientes.isEmpty()) {
            return new CambioAplicado(0);
        }
        // Con la IA apagada, la empresa suspendida o su tope del mes agotado no se encola a
        // nadie, y se dice por qué: contestar «0 personas» a secas hacía creer que ya no
        // quedaba nadie pendiente.
        String motivo = piezas().cola().porQueNoSePuedeUsarLaIa(vacante.getOrganizacionId());
        if (motivo != null) {
            return new CambioAplicado(0, motivo + " No se volvió a pedir la recalificación: "
                    + personas(pendientes.size(), "sigue", "siguen")
                    + " con la nota de la guía anterior.");
        }
        int encoladas = 0;
        for (Long postulacionId : pendientes) {
            if (recalificar(postulacionId)) {
                encoladas++;
            }
        }
        int sinEncolar = pendientes.size() - encoladas;
        return new CambioAplicado(encoladas, sinEncolar == 0 ? null
                : personas(sinEncolar, "ya tenía", "ya tenían")
                        + " una recalificación en marcha: no se pidió otra.");
    }

    /** En proporción al máximo nuevo, con dos decimales; de un máximo 0 no hay proporción. */
    public static BigDecimal escalar(BigDecimal nota, int maximoAnterior, int maximoNuevo) {
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
        List<VersionBanco> publicadas = publicadasDe(quien.organizacionId())
                .stream().filter(v -> !destino.getId().equals(v.getVacanteId())).toList();
        if (publicadas.isEmpty()) {
            return List.of();
        }
        Piezas piezas = piezas();
        Map<Long, Vacante> vacantesPorId = piezas.vacantes().findAllById(publicadas.stream()
                        .map(VersionBanco::getVacanteId).collect(Collectors.toSet())).stream()
                // Las eliminadas no: se crearon por error. Las cerradas y archivadas sí: lo
                // habitual es repetir una contratación que ya terminó.
                .filter(v -> quien.organizacionId().equals(v.getOrganizacionId())
                        && v.getEliminadaEn() == null)
                .collect(Collectors.toMap(Vacante::getId, Function.identity()));
        Map<Long, String> nivelPorPuesto = piezas.puestos().findAllById(vacantesPorId.values().stream()
                        .map(Vacante::getPuestoId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().filter(p -> quien.organizacionId().equals(p.getOrganizacionId()))
                .collect(Collectors.toMap(Puesto::getId, Puesto::getNivelPuestoCodigo));
        List<Long> versionIds = publicadas.stream().map(VersionBanco::getId).toList();
        Map<Long, Long> criteriosPorVersion = piezas.criterios().findByVersionBancoIdIn(versionIds).stream()
                .collect(Collectors.groupingBy(CriterioBanco::getVersionBancoId, Collectors.counting()));
        Map<Long, Long> preguntasPorVersion = piezas.preguntas().findByVersionBancoIdIn(versionIds).stream()
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
                // Las más recientes primero; las que nunca se publicaron, al final.
                .sorted(Comparator.comparing(VacanteCopiable::publicadaEn,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(VacanteCopiable::vacanteId, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VersionDePreguntas vistaPrevia(ContextoUsuario quien, Long vacanteId, Long vacanteOrigenId) {
        laVisible(quien, vacanteId);
        return comoVersion(laPublicadaDeOtraVacante(quien, vacanteOrigenId));
    }

    @Override
    @Transactional
    public EditorDePreguntas copiar(ContextoUsuario quien, Long vacanteId, CopiarDeOtraVacante datos) {
        Vacante destino = laEditable(quien, vacanteId);
        if (destino.getId().equals(datos.vacanteOrigenId())) {
            throw new IllegalArgumentException("Se copia de otra vacante, no de esta misma");
        }
        // La lectura va con el mismo corte por empresa que la vista previa: lo ajeno es 404
        // y no se crea nada.
        VersionBanco origen = laPublicadaDeOtraVacante(quien, datos.vacanteOrigenId());
        if (version(destino.getId(), PUBLICADA).isPresent() && laVaraNoSeMueve(destino)) {
            throw new IllegalStateException(textos().varaQuieta());
        }
        // El borrador que hubiera se reemplaza: el panel ya pidió confirmarlo.
        version(destino.getId(), BORRADOR).ifPresent(this::vaciarYBorrar);
        VersionBanco borrador = nuevoBorrador(destino);
        copiarContenido(origen, borrador);
        piezas().auditoria().registrar(quien.organizacionId(), quien, textos().alCopiar(),
                "version_banco", borrador.getId(), null,
                Map.of("desdeVacante", datos.vacanteOrigenId(), "desdeVersion", origen.getId()), null);
        return editor(quien, destino);
    }

    // ============================== Recomendaciones por IA ==============================

    /**
     * Lo último de pedir recomendaciones: si la cola no aceptó el trabajo, la propuesta pedida
     * queda FALLIDA con su porqué; si lo aceptó, se dice cuánto completará la IA.
     */
    protected RecomendacionPedida enLaCola(PropuestaPreguntas pedida, boolean encolada, int faltan) {
        if (!encolada) {
            pedida.setEstado(PropuestaPreguntas.FALLIDA);
            pedida.setMotivoFallo("No se pudo poner en la cola: ya había una en marcha, o la IA "
                    + "está apagada.");
            pedida.setTerminadaEn(Instant.now());
            piezas().propuestas().save(pedida);
            return new RecomendacionPedida(false, pedida.getMotivoFallo());
        }
        return new RecomendacionPedida(true, "La IA completará los " + faltan
                + " puntos que faltan. Tarda unos segundos.");
    }

    // ============================== Apoyo: vacante y versiones ==============================

    protected Vacante laVisible(ContextoUsuario quien, Long vacanteId) {
        return piezas().alcance().laVacanteVisible(quien, vacanteId, PERMISO_VER);
    }

    protected Vacante laEditable(ContextoUsuario quien, Long vacanteId) {
        Vacante vacante = piezas().alcance().laVacanteVisible(quien, vacanteId, PERMISO_EDITAR);
        if (vacante.getArchivadaEn() != null || "CERRADA".equals(vacante.getEstado())) {
            throw new IllegalStateException("Una vacante cerrada o archivada no se edita");
        }
        return vacante;
    }

    protected boolean puedeEditar(ContextoUsuario quien, Vacante vacante) {
        if (!quien.tiene(PERMISO_EDITAR)) {
            return false;
        }
        Piezas piezas = piezas();
        FiltroAlcance alcanceDeEdicion = piezas.permisos().alcanceDe(PERMISO_EDITAR);
        return alcanceDeEdicion != null
                && vacante.getArchivadaEn() == null
                && !"CERRADA".equals(vacante.getEstado())
                && piezas.alcance().alcanzaALaVacante(quien, alcanceDeEdicion, vacante);
    }

    protected String nivelDe(Vacante vacante) {
        return vacante.getPuestoId() == null ? null
                : piezas().puestos().findByIdAndOrganizacionId(vacante.getPuestoId(), vacante.getOrganizacionId())
                        .map(Puesto::getNivelPuestoCodigo).orElse(null);
    }

    private static String estadoDe(Vacante v) {
        if (v.getArchivadaEn() != null) {
            return "ARCHIVADA";
        }
        return "CERRADA".equals(v.getEstado()) ? "CERRADA" : "ACTIVA";
    }

    protected VersionBanco laPublicada(Vacante vacante) {
        return version(vacante.getId(), PUBLICADA)
                .orElseThrow(() -> new IllegalStateException(textos().sinPublicada()));
    }

    /** La versión publicada de otra vacante de la MISMA empresa; lo demás es 404. */
    private VersionBanco laPublicadaDeOtraVacante(ContextoUsuario quien, Long vacanteOrigenId) {
        Vacante origen = piezas().vacantes()
                .findByIdAndOrganizacionIdAndEliminadaEnIsNull(vacanteOrigenId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteOrigenId));
        return version(origen.getId(), PUBLICADA)
                .orElseThrow(() -> new ResourceNotFoundException(textos().publicadaDeOtra(), "id",
                        vacanteOrigenId));
    }

    /**
     * El borrador de la vacante; si no hay, se abre. Con una versión publicada, el borrador
     * nace como copia suya —«abrir un borrador desde la publicada»—, y eso solo mientras la
     * vara se pueda mover.
     */
    protected VersionBanco elBorrador(Vacante vacante) {
        Optional<VersionBanco> borrador = version(vacante.getId(), BORRADOR);
        if (borrador.isPresent()) {
            return borrador.get();
        }
        Optional<VersionBanco> publicada = version(vacante.getId(), PUBLICADA);
        if (publicada.isPresent() && laVaraNoSeMueve(vacante)) {
            throw new IllegalStateException(textos().varaQuieta());
        }
        VersionBanco nuevo = nuevoBorrador(vacante);
        publicada.ifPresent(p -> copiarContenido(p, nuevo));
        return nuevo;
    }

    protected VersionBanco elBorradorQueYaExiste(Vacante vacante) {
        return version(vacante.getId(), BORRADOR)
                .orElseThrow(() -> {
                    boolean publicada = version(vacante.getId(), PUBLICADA).isPresent();
                    return new IllegalStateException(publicada && laVaraNoSeMueve(vacante)
                            ? textos().varaQuieta()
                            : textos().sinBorrador());
                });
    }

    private VersionBanco nuevoBorrador(Vacante vacante) {
        Textos textos = textos();
        return piezas().versiones().saveAndFlush(VersionBanco.builder()
                .organizacionId(vacante.getOrganizacionId())
                .tipoBanco("VACANTE")
                .nivelPuestoCodigo(nivelDe(vacante))
                .vacanteId(vacante.getId())
                .proposito(textos.proposito())
                .metodoCalificacion(CalificacionPorPuntos.METODO)
                .etiqueta(textos.etiqueta() + vacante.getTitulo())
                .estado(BORRADOR)
                .versionGuia(1)
                .creadoEn(Instant.now())
                .build());
    }

    // ============================== Apoyo: criterios y preguntas ==============================

    protected CriterioBanco elCriterio(VersionBanco version, Long criterioId) {
        return piezas().criterios().findById(criterioId)
                .filter(c -> version.getId().equals(c.getVersionBancoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Criterio", "id", criterioId));
    }

    protected Pregunta laPregunta(VersionBanco version, Long preguntaId) {
        return piezas().preguntas().findById(preguntaId)
                .filter(p -> version.getId().equals(p.getVersionBancoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta", "id", preguntaId));
    }

    protected Pregunta nuevaPregunta(VersionBanco borrador, String tipo, String enunciado, int puntos,
                                     Long criterioId, String queDebeTener,
                                     List<OpcionAValidar> suyasOpciones) {
        Pregunta pregunta = piezas().preguntas().save(Pregunta.builder()
                .versionBancoId(borrador.getId())
                .codigo(codigoNuevo(borrador))
                .tipo(tipo)
                .enunciado(enunciado.strip())
                // En el método PUNTOS decide `puntos`; esto solo evita dejar la columna
                // incoherente, y nada del método nuevo la lee.
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

    /**
     * Reescribe una pregunta del borrador, ya validada por su editor. Cambiar de criterio la
     * pone al final del nuevo, que es donde se busca al moverla; sus opciones se rehacen
     * enteras: en un borrador ninguna respuesta apunta a ellas.
     */
    protected void reescribirPregunta(VersionBanco borrador, Pregunta pregunta, PreguntaEscrita escrita) {
        Long criterioId = escrita.criterioId();
        if (criterioId != null) {
            elCriterio(borrador, criterioId);
        }
        if (!Objects.equals(pregunta.getCriterioBancoId(), criterioId)) {
            pregunta.setCriterioBancoId(criterioId);
            pregunta.setOrden(siguienteOrden(borrador.getId(), criterioId));
        }
        pregunta.setTipo(escrita.tipo());
        pregunta.setEnunciado(escrita.enunciado().strip());
        pregunta.setPuntos(escrita.puntos());
        pregunta.setEsPuntuable(escrita.puntos() > 0);
        pregunta.setQueDebeTener(ReglasDePuntos.ABIERTA.equals(escrita.tipo())
                ? textoONulo(escrita.queDebeTener()) : null);
        piezas().preguntas().save(pregunta);

        OpcionRepository opciones = piezas().opciones();
        opciones.deleteByPreguntaIdIn(List.of(pregunta.getId()));
        opciones.flush();
        guardarOpciones(pregunta, escrita.opciones());
    }

    /**
     * Las opciones de una cerrada, con su orden explícito y la letra que pone el servidor:
     * A, B, C… en las de opción; el número del nivel en la escala, donde el rótulo es opcional
     * y sin él se enseña el número.
     */
    private void guardarOpciones(Pregunta pregunta, List<OpcionAValidar> suyas) {
        if (!ReglasDePuntos.esCerrada(pregunta.getTipo())) {
            return;
        }
        OpcionRepository opciones = piezas().opciones();
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
        int mayor = piezas().preguntas().findByVersionBancoIdOrderByOrden(borrador.getId()).stream()
                .map(Pregunta::getCodigo)
                .filter(c -> c != null && c.matches("P\\d+"))
                .mapToInt(c -> Integer.parseInt(c.substring(1)))
                .max().orElse(0);
        return "P" + (mayor + 1);
    }

    private int siguienteOrden(Long versionId, Long criterioId) {
        return piezas().preguntas().findByVersionBancoIdOrderByOrden(versionId).stream()
                .filter(p -> Objects.equals(p.getCriterioBancoId(), criterioId))
                .map(Pregunta::getOrden).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    /** Si algo falta, 400 con la lista entera: «faltan 3 cosas» y cuáles. */
    protected static void exigirSinFaltas(String queNoSePudo, List<String> faltas) {
        if (!faltas.isEmpty()) {
            throw new PreguntasInvalidasException(queNoSePudo
                    + (faltas.size() == 1 ? "falta una cosa" : "faltan " + faltas.size() + " cosas"),
                    faltas);
        }
    }

    // ============================== Apoyo: recalificación ==============================

    protected void exigirQueNoHayaRecalificacionEnCurso(Collection<Long> postulacionIds) {
        boolean enCurso = seguimiento(List.copyOf(postulacionIds)).values().stream()
                .anyMatch(s -> EN_CURSO.equals(s.estado()));
        if (enCurso) {
            throw new IllegalStateException("Hay una recalificación de la IA en curso: espera a "
                    + "que termine para volver a cambiar la guía o los puntos.");
        }
    }

    /** Cómo va la recalificación tras la última guía: al día, en curso o pendientes y por qué. */
    protected Recalificacion recalificacionDe(Vacante vacante, VersionBanco publicada) {
        int vigente = publicada.getVersionGuia() == null ? 1 : publicada.getVersionGuia();
        Map<Long, List<Integer>> conNota = guiasDeLaIa(vacante, publicada);
        int rindieron = cuantosRindieron(vacante, publicada);
        if (conNota.isEmpty()) {
            return new Recalificacion(rindieron, 0, 0, 0, 0, List.of());
        }
        Map<Long, ColaCalificacionIa.Seguimiento> deCadaUno = seguimiento(List.copyOf(conNota.keySet()));
        int alDia = 0;
        int recalificando = 0;
        int pendientes = 0;
        Set<String> motivos = new LinkedHashSet<>();
        for (Map.Entry<Long, List<Integer>> e : conNota.entrySet()) {
            boolean alDiaEsta = e.getValue().stream().allMatch(g -> Objects.equals(g, vigente));
            ColaCalificacionIa.Seguimiento s = deCadaUno.get(e.getKey());
            if (alDiaEsta) {
                alDia++;
            } else if (s != null && EN_CURSO.equals(s.estado())) {
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

    /** «1 persona sigue», «3 personas siguen». */
    private static String personas(int cuantas, String verboUna, String verboVarias) {
        return cuantas == 1 ? "1 persona " + verboUna : cuantas + " personas " + verboVarias;
    }

    protected static int paso(Mover datos) {
        return switch (datos.direccion().toUpperCase(Locale.ROOT)) {
            case "ARRIBA" -> -1;
            case "ABAJO" -> 1;
            default -> throw new IllegalArgumentException("La dirección es ARRIBA o ABAJO");
        };
    }

    protected static <T> int indiceDe(List<T> lista, Long id, Function<T, Long> suId, String que) {
        for (int i = 0; i < lista.size(); i++) {
            if (id.equals(suId.apply(lista.get(i)))) {
                return i;
            }
        }
        throw new ResourceNotFoundException(que, "id", id);
    }

    /** Intercambia dos puestos y renumera todo de 1 en adelante: el orden queda sin huecos. */
    protected static <T> void renumerar(List<T> lista, int i, int j, BiConsumer<T, Integer> ponerOrden) {
        T a = lista.get(i);
        lista.set(i, lista.get(j));
        lista.set(j, a);
        for (int k = 0; k < lista.size(); k++) {
            ponerOrden.accept(lista.get(k), k + 1);
        }
    }

    protected static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private static String sinTildes(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    protected static <T> List<T> lista(List<T> valor) {
        return valor == null ? List.of() : valor;
    }
}
