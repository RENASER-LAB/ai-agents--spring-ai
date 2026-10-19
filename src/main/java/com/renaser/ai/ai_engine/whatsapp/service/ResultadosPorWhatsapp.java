package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.service.AvisoDeEtapaEnLaCampana;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.entity.EventoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.ResultadoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.EventoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.ResultadoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Los hechos finales de una postulación con WhatsApp y su autorización de aviso (V76, contrato
 * v2, C-2 y C-4 a C-7).
 *
 * <p>Separa tres cosas que antes eran una sola —cerrar el vínculo—:
 * <ol>
 *   <li><b>el cierre ordinario del vínculo</b>, que sigue haciendo {@link AvisosPorWhatsapp};</li>
 *   <li><b>el derecho a consultar el resultado</b> durante 30 días ({@link ResultadoWhatsapp}),
 *       solo para quien tenía el vínculo ACTIVO al terminar;</li>
 *   <li><b>la autorización limitada de avisarlo</b>: un evento con {@code aviso.autorizado} y una
 *       caducidad de como mucho 24 horas, que no reactiva los avisos de avance.</li>
 * </ol>
 *
 * <p>⚠️ <b>Lo que escribe {@link #alTerminar} y {@link #alCerrarLaConvocatoria} va dentro de la
 * transacción del hecho</b> (bandeja de salida transaccional): un reinicio después de confirmar no
 * pierde el resultado ni su pendiente, y si el hecho se deshace, ellos también. El cierre por
 * plazo vencido, que corre solo cada minuto, pasa por la misma máquina de estados y deja lo suyo.
 *
 * <p>Con el productor v2 apagado ({@code app.whatsapp.productor-v2}) no nace ningún evento: el
 * vínculo se revoca como en v1. El resultado consultable sí se guarda —la consulta es de EX y no
 * manda nada—, y al encender el productor no sale ningún hecho anterior.
 */
@Component
@Slf4j
public class ResultadosPorWhatsapp {

    /** La autorización del aviso final dura como mucho esto desde el hecho (C-6). */
    public static final Duration VIDA_DEL_AVISO = Duration.ofHours(24);

    /** La revisión del primer resultado y la del cierre de una convocatoria (que no se reabre). */
    static final int PRIMERA_REVISION = 1;

    private final VinculoWhatsappRepository vinculos;
    private final ResultadoWhatsappRepository resultados;
    private final EventoWhatsappRepository eventos;
    private final BajaWhatsappRepository bajas;
    private final VacanteRepository vacantes;
    private final PersonaRepository personas;
    private final BetaWhatsapp beta;
    private final PropiedadesWhatsapp propiedades;
    private final EnvioAlBot envio;

    @SuppressWarnings("java:S107") // Cada uno es una tabla o una regla que el hecho final toca.
    public ResultadosPorWhatsapp(VinculoWhatsappRepository vinculos, ResultadoWhatsappRepository resultados,
                                 EventoWhatsappRepository eventos, BajaWhatsappRepository bajas,
                                 VacanteRepository vacantes, PersonaRepository personas,
                                 BetaWhatsapp beta, PropiedadesWhatsapp propiedades, EnvioAlBot envio) {
        this.vinculos = vinculos;
        this.resultados = resultados;
        this.eventos = eventos;
        this.bajas = bajas;
        this.vacantes = vacantes;
        this.personas = personas;
        this.beta = beta;
        this.propiedades = propiedades;
        this.envio = envio;
    }

    // ============ dentro de la transacción del hecho ============

    /**
     * La postulación acaba de llegar a un estado final. Si tenía el vínculo ACTIVO, queda su
     * resultado consultable y —con el productor v2 encendido— el evento RESULTADO por mandar.
     *
     * @param ocurridaEn cuándo ocurrió la transición: es la fecha del resultado
     * @param seLeAvisa  falso si el equipo decidió no contárselo por ningún canal: el evento sale
     *                   igual (registra el resultado y cierra el vínculo) pero sin autorización
     */
    public void alTerminar(Postulacion postulacion, Instant ocurridaEn, boolean seLeAvisa) {
        String tipo = postulacion.getEstadoCodigo();
        if (!ClienteConversacion.TIPOS_DE_RESULTADO.contains(tipo)) {
            return;
        }
        // ACTIVO y no PENDIENTE: quien pulsó el botón y nunca escribió no tiene nada que consultar.
        VinculoWhatsapp vinculo = vinculos.vivoDe(postulacion.getId())
                .filter(VinculoWhatsapp::estaActivo).orElse(null);
        if (vinculo == null || bajas.existsById(vinculo.getTelefono())
                || resultados.existsByPostulacionId(postulacion.getId())) {
            return;
        }
        Vacante vacante = vacantes.findById(postulacion.getVacanteId()).orElse(null);
        // Eliminar una vacante la retira de todas las pantallas (V60): su cierre lo cuenta la
        // campana, y por aquí se queda como en v1.
        if (vacante == null || vacante.getEliminadaEn() != null) {
            return;
        }
        Instant ahora = Instant.now();
        Instant fecha = LoQueLeToca.alMilisegundo(ocurridaEn == null ? ahora : ocurridaEn);
        Instant hasta = fecha.plus(Duration.ofDays(ResultadoWhatsapp.DIAS_DE_CONSULTA));
        resultados.save(ResultadoWhatsapp.builder()
                .postulacionId(postulacion.getId())
                .vinculoWhatsappId(vinculo.getId())
                .tipo(tipo)
                .fecha(fecha)
                .revision(PRIMERA_REVISION)
                .esCorreccion(false)
                .consultableHasta(hasta)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build());
        if (!productorEncendido(postulacion.getOrganizacionId())) {
            return;
        }
        EventoWhatsapp evento = nuevo(vinculo, postulacion.getId(), EventoWhatsapp.RESULTADO,
                vacante, fecha, seLeAvisa, ahora);
        evento.setResultadoTipo(tipo);
        evento.setResultadoFecha(fecha);
        evento.setResultadoEsCorreccion(false);
        evento.setResultadoConsultableHasta(hasta);
        evento.setCierraVinculo(true);
        eventos.save(evento);
    }

    /**
     * La vacante acaba de cerrar su convocatoria. A cada postulación que sigue en carrera con un
     * vínculo ACTIVO le queda un evento CONVOCATORIA_CERRADA: la postulación sigue, el vínculo no
     * se cierra y su acción pendiente tampoco (C-4).
     */
    public void alCerrarLaConvocatoria(Vacante vacante) {
        if (vacante == null || !productorEncendido(vacante.getOrganizacionId())) {
            return;
        }
        Instant ahora = Instant.now();
        Instant cerradaEn = LoQueLeToca.alMilisegundo(vacante.getCerradaEn() == null ? ahora
                : vacante.getCerradaEn());
        for (VinculoWhatsapp vinculo : vinculos.activosEnCarreraDeLaVacante(vacante.getId())) {
            if (bajas.existsById(vinculo.getTelefono()) || eventos.existsByPostulacionIdAndTipoAndRevision(
                    vinculo.getPostulacionId(), EventoWhatsapp.CONVOCATORIA_CERRADA, PRIMERA_REVISION)) {
                continue;
            }
            EventoWhatsapp evento = nuevo(vinculo, vinculo.getPostulacionId(),
                    EventoWhatsapp.CONVOCATORIA_CERRADA, vacante, cerradaEn, true, ahora);
            evento.setConvocatoriaCerradaEn(cerradaEn);
            evento.setCierraVinculo(false);
            eventos.save(evento);
        }
    }

    // ============ el cierre del vínculo y su revocación ============

    /**
     * El vínculo se acaba de cerrar por fin del proceso. Si su evento RESULTADO está pendiente, la
     * revocación PROCESO_TERMINADO no sale: la hace el evento ({@code cierraVinculo}, C-7). Si el
     * bot ya lo aceptó, ya está contado. Sin evento, la revocación de siempre.
     */
    public void alCerrarElVinculo(VinculoWhatsapp vinculo, Instant ahora) {
        List<EventoWhatsapp> suyos = eventos.findByVinculoWhatsappIdAndTipo(vinculo.getId(),
                EventoWhatsapp.RESULTADO);
        if (suyos.stream().anyMatch(e -> EventoWhatsapp.ACEPTADO.equals(e.getEstado()))) {
            vinculo.setRevocacionEnviadaEn(ahora);
            vinculo.setRevocacionProximoIntentoEn(null);
        } else if (suyos.stream().anyMatch(EventoWhatsapp::estaPendiente)) {
            vinculo.setRevocacionProximoIntentoEn(null);
        }
    }

    /**
     * Un evento RESULTADO dejó de estar pendiente. Aceptado, el bot cerró el vínculo con él;
     * fallido u omitido, el vínculo cerrado recupera su revocación PROCESO_TERMINADO de v1, que es
     * el respaldo: el bot no se queda con un vínculo abierto.
     */
    public void trasElEvento(EventoWhatsapp evento, Instant ahora) {
        if (!evento.esResultado() || evento.estaPendiente()) {
            return;
        }
        // Aún vivo: lo resuelve su cierre, y no se bloquea (una BAJA en curso lo tiene bloqueado y
        // espera a este mismo evento). Ya contado (o una BAJA): nada que hacer.
        VinculoWhatsapp visto = vinculos.findById(evento.getVinculoWhatsappId()).orElse(null);
        if (visto == null || visto.getCerradoEn() == null || visto.getRevocacionEnviadaEn() != null) {
            return;
        }
        VinculoWhatsapp vinculo = vinculos.paraEscribir(visto.getId()).orElse(null);
        if (vinculo == null || vinculo.getRevocacionEnviadaEn() != null) {
            return;
        }
        if (EventoWhatsapp.ACEPTADO.equals(evento.getEstado())) {
            vinculo.setRevocacionEnviadaEn(ahora);
            vinculo.setRevocacionProximoIntentoEn(null);
        } else if (vinculo.getRevocacionProximoIntentoEn() == null) {
            vinculo.setRevocacionProximoIntentoEn(ahora);
        } else {
            return;
        }
        vinculos.save(vinculo);
    }

    /**
     * Por qué este evento ya no se manda, o nulo si se manda (C-8): caducó su autorización, el
     * número escribió BAJA, o se retiró el acceso (cambió el celular, dejó de recibir, el vínculo
     * ya no está activo o el borrado se llevó el resultado). Se mira justo antes de cada intento.
     */
    public String motivoParaNoEnviar(EventoWhatsapp evento, Instant ahora) {
        if (!evento.getAvisoCaducaEn().isAfter(ahora)) {
            return EventoWhatsapp.POR_CADUCAR;
        }
        if (bajas.existsById(evento.getTelefono())) {
            return EventoWhatsapp.POR_BAJA;
        }
        if (evento.esResultado()) {
            boolean conAcceso = resultados.findByPostulacionId(evento.getPostulacionId())
                    .filter(r -> r.getAccesoRevocadoEn() == null
                            && Objects.equals(r.getVinculoWhatsappId(), evento.getVinculoWhatsappId()))
                    .isPresent();
            return conAcceso ? null : EventoWhatsapp.POR_REVOCACION;
        }
        return vinculos.findById(evento.getVinculoWhatsappId()).filter(VinculoWhatsapp::estaActivo)
                .isPresent() ? null : EventoWhatsapp.POR_REVOCACION;
    }

    // ============ lo que retira el acceso ============

    /**
     * El número escribió BAJA: sus resultados dejan de consultarse y sus eventos pendientes no
     * salen. Las BAJA prevalecen aunque lleguen antes que el evento.
     */
    public void alDarseDeBaja(String numero, Instant ahora) {
        List<Long> suyos = vinculos.findByTelefono(numero).stream().map(VinculoWhatsapp::getId).toList();
        if (suyos.isEmpty()) {
            return;
        }
        resultados.revocarDe(suyos, VinculoWhatsapp.POR_BAJA, ahora);
        eventos.omitirPendientesDe(suyos, EventoWhatsapp.POR_BAJA, ahora);
    }

    /**
     * La cuenta cambió de celular: lo del número anterior deja de consultarse y sus avisos finales
     * se cancelan. Uno que el bot aún no recibió no sale (y su vínculo recupera la revocación de
     * siempre); uno que ya aceptó se cancela contándole el cambio de celular, después del commit.
     */
    public void alCambiarElCelular(Long personaId, String nuevo, Instant ahora) {
        List<VinculoWhatsapp> delAnterior = vinculos.findByPersonaId(personaId).stream()
                .filter(v -> !Objects.equals(v.getTelefono(), nuevo))
                .toList();
        if (delAnterior.isEmpty()) {
            return;
        }
        List<Long> ids = delAnterior.stream().map(VinculoWhatsapp::getId).toList();
        List<EventoWhatsapp> vigentes = eventos.avisosFinalesVigentesDe(ids, ahora);
        resultados.revocarDe(ids, VinculoWhatsapp.POR_CAMBIO_DE_CELULAR, ahora);
        eventos.omitirPendientesDe(ids, EventoWhatsapp.POR_REVOCACION, ahora);
        for (EventoWhatsapp pendiente : vigentes.stream().filter(EventoWhatsapp::estaPendiente).toList()) {
            vinculos.paraEscribir(pendiente.getVinculoWhatsappId()).ifPresent(v -> {
                if (v.getCerradoEn() != null && v.getRevocacionEnviadaEn() == null
                        && v.getRevocacionProximoIntentoEn() == null) {
                    v.setRevocacionProximoIntentoEn(ahora);
                    vinculos.save(v);
                }
            });
        }
        List<VinculoWhatsapp> yaAceptados = delAnterior.stream()
                .filter(v -> vigentes.stream().anyMatch(e -> EventoWhatsapp.ACEPTADO.equals(e.getEstado())
                        && e.getVinculoWhatsappId().equals(v.getId())))
                .toList();
        if (!yaAceptados.isEmpty()) {
            AvisoDeEtapaEnLaCampana.alConfirmar(() -> yaAceptados.forEach(v -> envio.revocarSinFila(
                            v.getTelefono(), v.getReferencia(), VinculoWhatsapp.POR_CAMBIO_DE_CELULAR)),
                    "WhatsApp: cancelar el aviso final del celular anterior", null);
        }
    }

    /**
     * El borrado de datos se lleva los resultados y los eventos de estos vínculos, dentro de su
     * transacción y sin dejar copia. Devuelve los vínculos cuyo aviso final el bot ya aceptó y
     * sigue vigente: a esos hay que contarles el borrado para que no salga.
     */
    public Set<Long> alBorrarLosDatos(Collection<Long> vinculoIds) {
        if (vinculoIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> conAvisoAceptado = eventos.avisosFinalesVigentesDe(vinculoIds, Instant.now()).stream()
                .filter(e -> EventoWhatsapp.ACEPTADO.equals(e.getEstado()))
                .map(EventoWhatsapp::getVinculoWhatsappId)
                .collect(Collectors.toSet());
        eventos.borrarDe(vinculoIds);
        resultados.borrarDe(vinculoIds);
        return conAvisoAceptado;
    }

    /** Los resultados que pasaron sus 30 días ya no se consultan: se retiran. */
    public int retirarVencidos(Instant ahora) {
        return resultados.borrarVencidos(ahora);
    }

    // ============ ayudas ============

    /** El productor v2, la beta y un bot detrás: las tres, o no nace ningún evento. */
    private boolean productorEncendido(Long organizacionId) {
        return propiedades.isProductorV2() && beta.ofrecePara(organizacionId);
    }

    private EventoWhatsapp nuevo(VinculoWhatsapp vinculo, Long postulacionId, String tipo, Vacante vacante,
                                 Instant ocurrido, boolean autorizado, Instant ahora) {
        String nombre = personas.findById(vinculo.getPersonaId())
                .map(Persona::getNombre)
                .map(n -> TextosDeWhatsapp.cortar(n, TextosDeWhatsapp.LARGO_DEL_NOMBRE))
                .orElse("");
        String titulo = vacante.getTitulo() == null ? ""
                : TextosDeWhatsapp.cortar(vacante.getTitulo(), TextosDeWhatsapp.LARGO_DE_LA_VACANTE);
        return EventoWhatsapp.builder()
                .id(UuidV7.nuevo())
                .postulacionId(postulacionId)
                .vinculoWhatsappId(vinculo.getId())
                .tipo(tipo)
                .revision(PRIMERA_REVISION)
                .estado(EventoWhatsapp.PENDIENTE)
                .telefono(vinculo.getTelefono())
                .referencia(vinculo.getReferencia())
                .nombre(nombre)
                .vacante(titulo)
                .ocurridoEn(ocurrido)
                .avisoAutorizado(autorizado)
                .avisoCaducaEn(ocurrido.plus(VIDA_DEL_AVISO))
                .intentos(0)
                .proximoIntentoEn(ahora)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build();
    }
}
