package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.prueba.dto.DtosPrueba.*;
import com.renaser.ai.ai_engine.prueba.entity.Entregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VarianteCambio;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.ServicioPrueba;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La prueba del puesto desde el lado del candidato.
 *
 * <p>El reloj lo lleva el servidor: {@code venceEn} se calcula y se guarda una sola vez, al
 * iniciar. No hay pausas — cerrar la página no lo detiene — y cuando se acaba, el sondeo
 * ({@link #entregarVencidos()}) entrega lo que haya. No existe entregar tarde.
 *
 * <p><b>Cuánto dura la prueba lo puede decir la vacante</b> ({@code minutosEtapaTecnica}), y
 * entonces manda sobre lo que traiga la plantilla. Se lee <b>al empezar</b>, no al crear el
 * intento: así, mientras nadie haya abierto la suya, corregir el número en la ficha de la
 * vacante alcanza a todos. Congelarlo al crear dejaría a media tanda con el valor viejo sin
 * que nadie pudiera verlo desde el panel.
 */
@Service
@RequiredArgsConstructor
public class ServicioPruebaImpl implements ServicioPrueba {

    private final IntentoPruebaRepository intentos;
    private final VersionPlantillaPruebaRepository versiones;
    private final VacanteRepository vacantes;
    private final VarianteCambioRepository variantes;
    private final PreguntaVersionPlantillaRepository preguntasElegidas;
    private final PreguntaPruebaRepository preguntasCatalogo;
    private final EntregableRequeridoRepository entregablesRequeridos;
    private final EntregableRepository entregables;
    private final RespuestaPruebaRepository respuestas;
    private final PostulacionRepository postulaciones;
    private final AlmacenArchivos almacen;
    private final MaquinaEstados maquina;

    private final SecureRandom azar = new SecureRandom();

    @Override
    @Transactional
    public Long crearAlEntrar(Long organizacionId, Long postulacionId,
                              Long versionPlantillaPruebaId, Instant cierraEn) {
        // ⚠️ Solo si no tiene ya el suyo. Volver a entrar en la etapa —pasa cuando se
        // retrocede una postulación y se la vuelve a avanzar— chocaba contra la clave única
        // de la tabla: la inserción reventaba y el panel devolvía «ya existe un registro con
        // postulacion_id = X», que ni dice qué pasó ni deja seguir. Se reutiliza el que ya
        // hay, igual que el cuestionario técnico reutiliza el suyo en `confirmarAvance`.
        IntentoPrueba existente = intentos.findByPostulacionId(postulacionId).orElse(null);
        if (existente != null) {
            // Quien ya abrió su prueba se queda con la versión con la que la abrió (RF-90):
            // cambiársela debajo le movería el enunciado a mitad de camino. A quien no la ha
            // abierto se le pone la que la vacante rinde hoy —si no, cambiarle la prueba desde
            // la ficha no le llegaría nunca y rendiría la vieja sin que nadie lo viera— y con
            // ella la fecha de cierre, salvo que tenga concedida la suya (`plazoPropio`, V32).
            if (existente.getIniciadoEn() == null) {
                existente.setVersionPlantillaPruebaId(versionPlantillaPruebaId);
                if (!existente.isPlazoPropio()) {
                    existente.setVenceEn(cierraEn);
                }
                intentos.save(existente);
            }
            return existente.getId();
        }
        IntentoPrueba intento = intentos.save(IntentoPrueba.builder()
                .postulacionId(postulacionId)
                .versionPlantillaPruebaId(versionPlantillaPruebaId)
                // Si la vacante fija cuándo cierra su prueba, el intento nace ya con esa
                // fecha. Sin ella, `venceEn` queda vacío y se calcula al empezar.
                //
                // ⚠️ Empezar SÍ puede acercarla: si el reloj de la prueba se agota antes de
                // esa fecha, manda el reloj. Lo que no puede es alejarla — nadie gana tiempo
                // por abrir tarde — ni tocar la de quien tiene `plazoPropio`. Ver `iniciar`.
                .venceEn(cierraEn)
                .esEntregaAutomatica(false)
                .creadoEn(Instant.now())
                .build());
        return intento.getId();
    }

    @Override
    public MiPrueba ver(ContextoUsuario quien, UUID uuidPostulacion) {
        var par = laMia(quien, uuidPostulacion);
        return pintar(par.intento(), minutosDeLaVacante(par.postulacion()));
    }

    /**
     * Empezar la prueba: aquí es donde arranca el reloj y se fija cuándo vence.
     *
     * <p><b>Gana el plazo más cercano, y solo un cronómetro puede acercarlo.</b> Antes,
     * tener ya una fecha —la de cierre de la vacante— hacía salir sin mirar el reloj de la
     * prueba: una prueba de noventa minutos abierta el lunes duraba hasta el domingo. Ahora
     * se calcula el vencimiento por el reloj y se queda el que caiga antes, igual que
     * {@code ServicioEvaluacionImpl.iniciarTecnico}.
     *
     * <p>⚠️ <b>«El reloj» son MINUTOS, nunca los días de la plantilla.</b> Es la línea que
     * separa este método de una regresión silenciosa, y por eso está escrita aparte. La
     * fecha de cierre de la vacante existe para decir «esta convocatoria cierra el 30» a
     * todos a la vez (V32); comparar contra los días del plazo abierto se la recortaría a
     * quien abriera pronto —el día 2 se le cerraría el 9— y volverían los vencimientos
     * distintos por candidato que aquella migración vino a eliminar, sin que nadie lo
     * pidiera ni pudiera verlo en el panel. {@code iniciarTecnico} ya lo hace así
     * ({@code if (minutos != null)}); los dos instrumentos tienen que decidir igual.
     *
     * <p>⚠️ <b>{@code plazoPropio} no se toca.</b> Marca a quien se le concedió su propia
     * fecha a mano —«a esta persona, más horas» (V32)—; moverla aquí borraría en silencio esa
     * concesión, que es justo lo que la columna existe para impedir. Único matiz: si está
     * marcado pero no hay fecha que proteger, se calcula como con cualquiera — dejarlo vacío
     * sería un plazo infinito, que nadie concedió.
     */
    @Override
    @Transactional
    public MiPrueba iniciar(ContextoUsuario quien, UUID uuidPostulacion) {
        var par = laMia(quien, uuidPostulacion);
        IntentoPrueba intento = par.intento();
        exigirAbierto(intento);
        Integer minutosVacante = minutosDeLaVacante(par.postulacion());

        if (intento.getIniciadoEn() == null) {
            VersionPlantillaPrueba version = laVersion(intento);
            Instant ahora = Instant.now();

            intento.setIniciadoEn(ahora);
            boolean concedidaAMano = intento.isPlazoPropio() && intento.getVenceEn() != null;
            if (!concedidaAMano) {
                if (intento.getVenceEn() == null) {
                    // Sin fecha no hay nada que respetar: se cuenta el plazo que toque,
                    // minutos o días, como se ha hecho siempre.
                    intento.setVenceEn(cuandoVenceDesde(ahora, version, minutosVacante));
                } else {
                    // Ya tiene fecha —la de cierre de la convocatoria—. Solo un cronómetro
                    // la acerca; los días del plazo abierto no la tocan.
                    Integer minutos = minutosEfectivos(version, minutosVacante);
                    if (minutos != null) {
                        Instant porElReloj = ahora.plus(minutos, ChronoUnit.MINUTES);
                        if (porElReloj.isBefore(intento.getVenceEn())) {
                            intento.setVenceEn(porElReloj);
                        }
                    }
                }
            }
            sortearCambio(intento, version);
            intentos.save(intento);
        }
        return pintar(intento, minutosVacante);
    }

    @Override
    @Transactional
    public void responder(ContextoUsuario quien, UUID uuidPostulacion, Long preguntaId, Responder datos) {
        IntentoPrueba intento = laMia(quien, uuidPostulacion).intento();
        exigirAbierto(intento);
        exigirIniciado(intento);
        exigirQueLeToca(intento, preguntaId);

        RespuestaPrueba r = respuestas.findByIntentoPruebaIdAndPreguntaPruebaId(intento.getId(), preguntaId)
                .orElseGet(() -> RespuestaPrueba.builder()
                        .intentoPruebaId(intento.getId())
                        .preguntaPruebaId(preguntaId)
                        .build());
        r.setTexto(datos.texto());
        r.setRespondidaEn(Instant.now());
        respuestas.save(r);
    }

    @Override
    @Transactional
    public void subirEntregableArchivo(ContextoUsuario quien, UUID uuidPostulacion, Long entregableRequeridoId,
                                       MultipartFile archivo) {
        Par par = laMia(quien, uuidPostulacion);
        EntregableRequerido requerido = exigirFormato(par.intento(), entregableRequeridoId, "ARCHIVO");
        /*
         * ⚠️ **La organización de la VACANTE, no la de quien sube.** `quien` es el candidato, y
         * la suya es la plataforma: todas las cuentas del portal nacen ahí. El panel busca el
         * archivo con `findByIdAndOrganizacionId` usando la de la empresa de la vacante, así
         * que sellarlo con la del candidato lo deja invisible —404— para cualquier empresa que
         * no sea la plataforma. Hoy no se nota porque RENASER es las dos cosas.
         *
         * Es el mismo criterio que ya sigue el currículum al postular, y por el mismo motivo:
         * arrastra todo lo que el panel de la empresa tiene que poder ver.
         */
        Archivo guardado = almacen.guardar(par.postulacion().getOrganizacionId(), archivo);
        guardarEntregable(par.intento(), requerido, guardado.getId(), null);
    }

    @Override
    @Transactional
    public void subirEntregableEnlace(ContextoUsuario quien, UUID uuidPostulacion, Long entregableRequeridoId,
                                      SubirEntregableEnlace datos) {
        IntentoPrueba intento = laMia(quien, uuidPostulacion).intento();
        EntregableRequerido requerido = exigirFormato(intento, entregableRequeridoId, "ENLACE");
        guardarEntregable(intento, requerido, null, datos.enlace());
    }

    @Override
    @Transactional
    public EntregaResponse entregar(ContextoUsuario quien, UUID uuidPostulacion) {
        var par = laMia(quien, uuidPostulacion);
        IntentoPrueba intento = par.intento();
        exigirAbierto(intento);
        exigirIniciado(intento);

        List<String> faltan = obligatoriosFaltantes(intento);
        if (!faltan.isEmpty()) {
            throw new IllegalArgumentException(
                    "Faltan entregables obligatorios: " + String.join(", ", faltan));
        }

        cerrarIntento(intento, par.postulacion(), false);
        return new EntregaResponse("ENTREGADA", true, 0);
    }

    @Override
    @Transactional
    public void entregarVencidos() {
        List<IntentoPrueba> vencidos =
                intentos.findByEntregadoEnIsNullAndIniciadoEnIsNotNullAndVenceEnBefore(Instant.now());
        if (vencidos.isEmpty()) {
            return;
        }
        // Las postulaciones de la tanda entera, no una por intento. La tanda crece con los
        // candidatos que estén haciendo la prueba, y esta tarea corre sola cada poco: no
        // tiene por qué llevarse el pool de conexiones cada vez que despierta.
        Map<Long, Postulacion> porId = postulaciones
                .findAllById(vencidos.stream().map(IntentoPrueba::getPostulacionId).toList()).stream()
                .collect(Collectors.toMap(Postulacion::getId, Function.identity()));

        for (IntentoPrueba intento : vencidos) {
            Postulacion postulacion = porId.get(intento.getPostulacionId());
            if (postulacion == null) continue;
            cerrarIntento(intento, postulacion, true);
        }
    }

    // ============ Apoyo ============

    /**
     * Los minutos que la vacante fijó para su etapa técnica, o {@code null} si no fijó
     * ninguno y rige lo que traiga la plantilla.
     *
     * <p>Se resuelve desde la postulación que ya trae {@code laMia}: el intento no guarda la
     * vacante, y duplicarla aquí sería un segundo sitio donde se puede desincronizar.
     *
     * <p>⚠️ <b>Se busca por id Y organización</b>, no por id suelto. Es la regla de la pieza
     * B, y aquí el dueño sale de la propia postulación —que {@code laMia} ya comprobó que es
     * de quien pregunta—, no del contexto: así la cadena entera cuelga de una sola cosa
     * verificada.
     *
     * <p>Una vacante que ya no existe se lee como «sin minutos propios» en vez de reventar:
     * quien esté rindiendo tiene que poder terminar su prueba.
     */
    private Integer minutosDeLaVacante(Postulacion postulacion) {
        return vacantes.findByIdAndOrganizacionId(
                        postulacion.getVacanteId(), postulacion.getOrganizacionId())
                .map(Vacante::getMinutosEtapaTecnica)
                .orElse(null);
    }

    /**
     * Cuándo vence una prueba que se abre en {@code ahora}, según el reloj que le toca.
     *
     * <p>El orden es único a propósito: <b>los minutos de la vacante mandan</b> sobre los de
     * la plantilla, y solo cuando no hay ni unos ni otros se cuentan los días. De ahí sale
     * solo el caso de una plantilla de plazo abierto a la que la vacante le pone minutos: se
     * vuelve cronometrada de hecho, sin ninguna rama que lo diga.
     */
    private Instant cuandoVenceDesde(Instant ahora, VersionPlantillaPrueba version,
                                     Integer minutosVacante) {
        Integer minutos = minutosEfectivos(version, minutosVacante);
        return minutos != null
                ? ahora.plus(minutos, ChronoUnit.MINUTES)
                : ahora.plus(version.getPlazoDias(), ChronoUnit.DAYS);
    }

    /**
     * Los minutos que de verdad rigen: los de la vacante si los hay, y si no los de la
     * plantilla cuando es cronometrada. {@code null} = esta prueba se mide en días.
     *
     * <p>⚠️ <b>Es el mismo número que se le enseña al candidato</b> ({@code pintar}). Sacar
     * el de la pantalla de la plantilla y el del reloj de la vacante fue el fallo original:
     * la pantalla decía «60 minutos desde que empieces» y el servidor cerraba a los 90.
     */
    private Integer minutosEfectivos(VersionPlantillaPrueba version, Integer minutosVacante) {
        if (minutosVacante != null) {
            return minutosVacante;
        }
        return "CRONOMETRADA".equals(version.getModalidad()) ? version.getDuracionMinutos() : null;
    }

    /**
     * La modalidad que de verdad rige. Con minutos de la vacante siempre es cronometrada,
     * diga lo que diga la plantilla: el reloj corre desde que se abre.
     *
     * <p>Solo cambia lo que se le enseña al candidato. La fila de la plantilla no se toca —
     * la misma versión la puede rendir otra vacante que no fije minutos, y ahí sigue siendo
     * de plazo abierto.
     */
    private String modalidadEfectiva(VersionPlantillaPrueba version, Integer minutosVacante) {
        return minutosVacante != null ? "CRONOMETRADA" : version.getModalidad();
    }

    private void cerrarIntento(IntentoPrueba intento, Postulacion postulacion, boolean automatica) {
        intento.setEntregadoEn(Instant.now());
        intento.setEsEntregaAutomatica(automatica);
        intentos.save(intento);
        // Del sistema si es automática; si no, sigue siendo el candidato quien la
        // disparó, pero la transición en sí la hace el sistema: no hay motivo que pedir.
        maquina.transicionar(postulacion, "PRUEBA_CALIFICANDO", null, null, true, false, null);
    }

    private void sortearCambio(IntentoPrueba intento, VersionPlantillaPrueba version) {
        if (version.getMinutoCambioMin() == null || version.getMinutoCambioMax() == null) {
            return;
        }
        List<VarianteCambio> disponibles = variantes.findByVersionPlantillaPruebaId(version.getId());
        if (disponibles.isEmpty()) {
            return;
        }
        VarianteCambio elegida = disponibles.get(azar.nextInt(disponibles.size()));
        int min = version.getMinutoCambioMin();
        int max = version.getMinutoCambioMax();
        int minuto = max <= min ? min : min + azar.nextInt(max - min + 1);

        intento.setVarianteCambioId(elegida.getId());
        intento.setMinutoCambio(minuto);
    }

    private MiPrueba pintar(IntentoPrueba intento, Integer minutosVacante) {
        VersionPlantillaPrueba version = laVersion(intento);
        revelarCambioSiToca(intento, version);

        List<Long> idsElegidas = preguntasElegidas
                .findByVersionPlantillaPruebaIdOrderByOrden(version.getId()).stream()
                .map(PreguntaVersionPlantilla::getPreguntaPruebaId).toList();
        Map<Long, PreguntaPrueba> preguntasPorId = preguntasCatalogo.findByIdIn(idsElegidas).stream()
                .collect(Collectors.toMap(PreguntaPrueba::getId, Function.identity()));
        Map<Long, RespuestaPrueba> respuestaPorPregunta = respuestas
                .findByIntentoPruebaId(intento.getId()).stream()
                .collect(Collectors.toMap(RespuestaPrueba::getPreguntaPruebaId, Function.identity()));

        List<PreguntaCandidato> preguntas = new ArrayList<>();
        for (Long id : idsElegidas) {
            PreguntaPrueba p = preguntasPorId.get(id);
            if (p == null) continue;
            RespuestaPrueba r = respuestaPorPregunta.get(id);
            preguntas.add(new PreguntaCandidato(p.getId(), p.getTipo(), p.getEnunciado(),
                    r == null ? null : r.getTexto()));
        }

        List<Entregable> subidos = entregables.findByIntentoPruebaId(intento.getId());
        List<EntregableRequeridoCandidato> entregablesCandidato = entregablesRequeridos
                .findByVersionPlantillaPruebaIdOrderByOrden(version.getId()).stream()
                .map(e -> new EntregableRequeridoCandidato(e.getId(), e.getNombre(), e.getDetalle(),
                        e.getFormato(), e.isEsObligatorio(),
                        subidos.stream().anyMatch(s -> s.getEntregableRequeridoId().equals(e.getId()))))
                .toList();

        String estado = intento.getEntregadoEn() != null ? "ENTREGADA"
                : intento.getIniciadoEn() != null ? "EN_CURSO" : "PENDIENTE";
        String cambioTexto = intento.getCambioMostradoEn() == null ? null
                : variantes.findById(intento.getVarianteCambioId()).map(VarianteCambio::getTexto).orElse(null);

        // ⚠️ La duración y la modalidad que viajan son las EFECTIVAS, no las de la
        // plantilla. Es el número con el que la pantalla escribe «N minutos desde que
        // empieces»: si dijera 60 mientras el servidor cierra a los 90 de la vacante, la
        // pantalla estaría mintiendo — que es el fallo que esto arregla, no uno nuevo que
        // introducir en la otra dirección.
        //
        // Y la modalidad va con ella porque se leen juntas: el lateral del portal pinta
        // «Duración: 90 minutos» y justo debajo «Modalidad: PLAZO_ABIERTO», que es la misma
        // contradicción escrita dos veces. Si la vacante puso minutos, esta prueba es
        // cronometrada de hecho y así se dice.
        return new MiPrueba(intento.getId(), estado, modalidadEfectiva(version, minutosVacante),
                intento.getIniciadoEn(), intento.getVenceEn(),
                minutosEfectivos(version, minutosVacante),
                version.getEnunciado(), version.getMateriales(), version.getHerramientasPermitidas(),
                cambioTexto, preguntas, entregablesCandidato);
    }

    /**
     * Revela el cambio inesperado en el momento sorteado, no antes (RF-77). Si la plantilla
     * define minutos extra, se suman al plazo aquí, una sola vez: es el tiempo para adaptarse
     * tras el cambio, no parte del reloj original.
     */
    private void revelarCambioSiToca(IntentoPrueba intento, VersionPlantillaPrueba version) {
        if (intento.getIniciadoEn() == null || intento.getEntregadoEn() != null
                || intento.getVarianteCambioId() == null || intento.getCambioMostradoEn() != null) {
            return;
        }
        Instant momento = intento.getIniciadoEn().plus(intento.getMinutoCambio(), ChronoUnit.MINUTES);
        if (Instant.now().isBefore(momento)) {
            return;
        }
        intento.setCambioMostradoEn(Instant.now());
        if (version.getMinutosExtra() != null && intento.getVenceEn() != null) {
            intento.setVenceEn(intento.getVenceEn().plus(version.getMinutosExtra(), ChronoUnit.MINUTES));
        }
        intentos.save(intento);
    }

    private List<String> obligatoriosFaltantes(IntentoPrueba intento) {
        List<Entregable> subidos = entregables.findByIntentoPruebaId(intento.getId());
        return entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(intento.getVersionPlantillaPruebaId())
                .stream()
                .filter(EntregableRequerido::isEsObligatorio)
                .filter(e -> subidos.stream().noneMatch(s -> s.getEntregableRequeridoId().equals(e.getId())))
                .map(EntregableRequerido::getNombre)
                .toList();
    }

    private void guardarEntregable(IntentoPrueba intento, EntregableRequerido requerido,
                                   Long archivoId, String enlace) {
        int siguiente = entregables
                .findByIntentoPruebaIdAndEntregableRequeridoIdOrderByVersionDesc(intento.getId(), requerido.getId())
                .stream().findFirst().map(e -> e.getVersion() + 1).orElse(1);
        entregables.save(Entregable.builder()
                .intentoPruebaId(intento.getId())
                .entregableRequeridoId(requerido.getId())
                .archivoId(archivoId)
                .enlace(enlace)
                .version(siguiente)
                .subidoEn(Instant.now())
                .build());
    }

    private EntregableRequerido exigirFormato(IntentoPrueba intento, Long entregableRequeridoId, String formatoUsado) {
        exigirAbierto(intento);
        exigirIniciado(intento);
        EntregableRequerido requerido = entregablesRequeridos.findById(entregableRequeridoId)
                .filter(e -> e.getVersionPlantillaPruebaId().equals(intento.getVersionPlantillaPruebaId()))
                .orElseThrow(() -> new ResourceNotFoundException("Entregable requerido", "id", entregableRequeridoId));
        if (!"CUALQUIERA".equals(requerido.getFormato()) && !requerido.getFormato().equals(formatoUsado)) {
            throw new IllegalArgumentException(
                    "«%s» pide %s, no %s".formatted(requerido.getNombre(), requerido.getFormato(), formatoUsado));
        }
        return requerido;
    }

    private void exigirQueLeToca(IntentoPrueba intento, Long preguntaId) {
        boolean leToca = preguntasElegidas
                .findByVersionPlantillaPruebaIdOrderByOrden(intento.getVersionPlantillaPruebaId()).stream()
                .anyMatch(p -> p.getPreguntaPruebaId().equals(preguntaId));
        if (!leToca) {
            throw new ResourceNotFoundException("Pregunta de prueba", "id", preguntaId);
        }
    }

    private void exigirAbierto(IntentoPrueba intento) {
        if (intento.getEntregadoEn() != null) {
            throw new IllegalStateException("Esta prueba ya fue entregada");
        }
        if (intento.getVenceEn() != null && Instant.now().isAfter(intento.getVenceEn())) {
            throw new IllegalStateException("El tiempo para esta prueba ya se agotó");
        }
    }

    private void exigirIniciado(IntentoPrueba intento) {
        if (intento.getIniciadoEn() == null) {
            throw new IllegalStateException("Hay que empezar la prueba antes de continuar");
        }
    }

    private VersionPlantillaPrueba laVersion(IntentoPrueba intento) {
        return versiones.findById(intento.getVersionPlantillaPruebaId())
                .orElseThrow(() -> new IllegalStateException("La versión de esta prueba ya no existe"));
    }

    private Par laMia(ContextoUsuario quien, UUID uuid) {
        Postulacion postulacion = postulaciones.findByUuid(uuid)
                .filter(p -> p.getUsuarioId().equals(quien.usuarioId()))
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "código", uuid));
        IntentoPrueba intento = intentos.findByPostulacionId(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Prueba del puesto", "postulación", uuid));
        return new Par(postulacion, intento);
    }

    private record Par(Postulacion postulacion, IntentoPrueba intento) {}
}
