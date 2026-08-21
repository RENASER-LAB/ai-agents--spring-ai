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
 */
@Service
@RequiredArgsConstructor
public class ServicioPruebaImpl implements ServicioPrueba {

    private final IntentoPruebaRepository intentos;
    private final VersionPlantillaPruebaRepository versiones;
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
    public Long crearAlEntrar(Long organizacionId, Long postulacionId, Long versionPlantillaPruebaId) {
        IntentoPrueba intento = intentos.save(IntentoPrueba.builder()
                .postulacionId(postulacionId)
                .versionPlantillaPruebaId(versionPlantillaPruebaId)
                .esEntregaAutomatica(false)
                .creadoEn(Instant.now())
                .build());
        return intento.getId();
    }

    @Override
    public MiPrueba ver(ContextoUsuario quien, UUID uuidPostulacion) {
        return pintar(laMia(quien, uuidPostulacion).intento());
    }

    @Override
    @Transactional
    public MiPrueba iniciar(ContextoUsuario quien, UUID uuidPostulacion) {
        IntentoPrueba intento = laMia(quien, uuidPostulacion).intento();
        exigirAbierto(intento);

        if (intento.getIniciadoEn() == null) {
            VersionPlantillaPrueba version = laVersion(intento);
            Instant ahora = Instant.now();
            Instant vence = "CRONOMETRADA".equals(version.getModalidad())
                    ? ahora.plus(version.getDuracionMinutos(), ChronoUnit.MINUTES)
                    : ahora.plus(version.getPlazoDias(), ChronoUnit.DAYS);

            intento.setIniciadoEn(ahora);
            intento.setVenceEn(vence);
            sortearCambio(intento, version);
            intentos.save(intento);
        }
        return pintar(intento);
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
        IntentoPrueba intento = laMia(quien, uuidPostulacion).intento();
        EntregableRequerido requerido = exigirFormato(intento, entregableRequeridoId, "ARCHIVO");
        Archivo guardado = almacen.guardar(quien.organizacionId(), archivo);
        guardarEntregable(intento, requerido, guardado.getId(), null);
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

    private MiPrueba pintar(IntentoPrueba intento) {
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

        return new MiPrueba(intento.getId(), estado, version.getModalidad(),
                intento.getIniciadoEn(), intento.getVenceEn(), version.getDuracionMinutos(),
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
