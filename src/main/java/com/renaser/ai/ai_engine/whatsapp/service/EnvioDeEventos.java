package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.whatsapp.entity.EventoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.EventoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.FalloDelBot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El envío de los eventos v2 de la bandeja de salida a {@code POST /v1/ex/resultados} (V76).
 *
 * <p>Lo llama {@code SondeoWhatsapp} junto a {@link EnvioAlBot}, con sus mismas reglas: cada envío
 * en su transacción, con la fila bloqueada sin esperar ({@code SKIP LOCKED}), los mismos
 * reintentos con espera creciente y el mismo tope. Antes de cada intento vuelve a mirar si toca:
 * una autorización caducada, una BAJA o un acceso retirado lo dejan {@code OMITIDO} y no salen
 * nunca, aunque el bot vuelva a estar disponible.
 *
 * <p>Un {@code 404 ruta_no_disponible} (el bot sigue en la versión 1) es un 4xx: el evento queda
 * {@code FALLIDO} a la primera, sin reintentos en bucle. Un RESULTADO que no llega a aceptarse
 * devuelve a su vínculo la revocación PROCESO_TERMINADO de v1, para que el bot lo cierre igual.
 *
 * <p>Con el productor apagado no sale nada: los pendientes esperan y, al caducar, quedan
 * {@code OMITIDO} sin recurrir a tipos que el bot en v1 no entiende.
 */
@Component
@Slf4j
public class EnvioDeEventos {

    private static final int TANDA = 50;
    private static final int LARGO_DEL_ERROR = 500;

    private final EventoWhatsappRepository eventos;
    private final ResultadosPorWhatsapp resultados;
    private final ClienteConversacion cliente;
    private final PropiedadesWhatsapp propiedades;
    private final EnvioAlBot envio;
    private final TransactionTemplate enSuTransaccion;

    public EnvioDeEventos(EventoWhatsappRepository eventos, ResultadosPorWhatsapp resultados,
                          ClienteConversacion cliente, PropiedadesWhatsapp propiedades, EnvioAlBot envio,
                          PlatformTransactionManager transacciones) {
        this.eventos = eventos;
        this.resultados = resultados;
        this.cliente = cliente;
        this.propiedades = propiedades;
        this.envio = envio;
        this.enSuTransaccion = new TransactionTemplate(transacciones);
        this.enSuTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Una vuelta del sondeo: los eventos que tocan ya y lo que pasó sus 30 días. */
    public void enviarPendientes() {
        if (!cliente.estaEncendido()) {
            return;
        }
        Instant ahora = Instant.now();
        List<UUID> tanda = propiedades.isProductorV2()
                ? eventos.pendientesPorEnviar(ahora, PageRequest.of(0, TANDA))
                : eventos.caducadosPorCerrar(ahora, PageRequest.of(0, TANDA));
        for (UUID id : tanda) {
            try {
                enSuTransaccion.executeWithoutResult(t -> enviar(id));
            } catch (RuntimeException e) {
                log.error("Falló el envío del evento {} al bot de WhatsApp; se reintenta en la próxima "
                        + "vuelta: {}", id, e.getMessage());
            }
        }
        try {
            enSuTransaccion.executeWithoutResult(t -> resultados.retirarVencidos(ahora));
        } catch (RuntimeException e) {
            log.error("No se pudieron retirar los resultados de WhatsApp vencidos: {}", e.getMessage());
        }
    }

    // ============ cada envío, dentro de su transacción ============

    void enviar(UUID id) {
        EventoWhatsapp evento = eventos.paraElSondeo(id).orElse(null);
        if (evento == null || !evento.estaPendiente()) {
            return;
        }
        Instant ahora = Instant.now();
        String motivo = resultados.motivoParaNoEnviar(evento, ahora);
        if (motivo != null) {
            evento.setEstado(EventoWhatsapp.OMITIDO);
            evento.setMotivo(motivo);
            evento.setProximoIntentoEn(null);
            evento.setActualizadoEn(ahora);
            eventos.save(evento);
            resultados.trasElEvento(evento, ahora);
            return;
        }
        if (!propiedades.isProductorV2()) {
            // Vuelta a v1: se queda pendiente hasta que caduque o se vuelva a encender.
            return;
        }
        evento.setIntentos(evento.getIntentos() + 1);
        evento.setActualizadoEn(ahora);
        try {
            cliente.publicarEvento(comoEvento(evento));
            evento.setEstado(EventoWhatsapp.ACEPTADO);
            evento.setAceptadoEn(ahora);
            evento.setProximoIntentoEn(null);
            evento.setUltimoError(null);
        } catch (FalloDelBot fallo) {
            evento.setUltimoError(recortar(fallo.getMessage()));
            Instant siguiente = envio.siguienteIntento(fallo, evento.getIntentos(), ahora);
            if (siguiente == null) {
                evento.setEstado(EventoWhatsapp.FALLIDO);
                evento.setMotivo(fallo.codigo());
                log.warn("El evento {} ({}) de la postulación {} quedó fallido tras {} intento(s): {}",
                        evento.getId(), evento.getTipo(), evento.getPostulacionId(), evento.getIntentos(),
                        fallo.getMessage());
            }
            evento.setProximoIntentoEn(siguiente);
        }
        eventos.save(evento);
        resultados.trasElEvento(evento, ahora);
    }

    /** Lo que viaja, rehecho desde la fila: un reintento lleva exactamente lo mismo que el primero. */
    static ClienteConversacion.Evento comoEvento(EventoWhatsapp evento) {
        ClienteConversacion.ResultadoDelEvento resultado = evento.esResultado()
                ? new ClienteConversacion.ResultadoDelEvento(evento.getResultadoTipo(),
                        evento.getResultadoFecha(), evento.getRevision(), null,
                        evento.isResultadoEsCorreccion(), evento.getResultadoConsultableHasta())
                : null;
        ClienteConversacion.ConvocatoriaDelEvento convocatoria = evento.esResultado() ? null
                : new ClienteConversacion.ConvocatoriaDelEvento(evento.getConvocatoriaCerradaEn(),
                        evento.getRevision());
        return new ClienteConversacion.Evento(evento.getId(), evento.getTipo(), evento.getTelefono(),
                evento.getReferencia(), evento.getNombre(), evento.getVacante(), evento.getOcurridoEn(),
                resultado, convocatoria, evento.isAvisoAutorizado(), evento.getAvisoCaducaEn(),
                evento.isCierraVinculo());
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() > LARGO_DEL_ERROR ? texto.substring(0, LARGO_DEL_ERROR) : texto;
    }
}
