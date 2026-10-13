package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.whatsapp.entity.AvisoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.AvisoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.FalloDelBot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que EX le manda al bot y el bot no ha confirmado todavía (V73): los avisos «te toca», los
 * anuncios que no llegaron al primer intento y las revocaciones.
 *
 * <p>Lo llama {@code SondeoWhatsapp} cada pocos segundos, y el botón y «Mi cuenta» para el primer
 * intento. Cada envío va en su propia transacción y con su fila bloqueada sin esperar
 * ({@code SKIP LOCKED}): si otro sondeo la tiene, este la salta, y así un aviso no sale dos veces.
 * Aunque saliera —un reinicio en mitad del envío—, el bot lo reconoce por su {@code avisoId}.
 *
 * <p>Reintenta con espera creciente —30 s, 2 min, 8 min, 32 min— hasta
 * {@code app.whatsapp.maximo-de-intentos} y luego lo da por fallido. Un 4xx del bot es definitivo
 * a la primera: repetir lo mismo daría lo mismo. Nada de esto toca la transición: el correo y la
 * campana ya salieron.
 */
@Component
@Slf4j
public class EnvioAlBot {

    private static final int TANDA = 50;
    private static final int LARGO_DEL_ERROR = 500;

    private final AvisoWhatsappRepository avisos;
    private final VinculoWhatsappRepository vinculos;
    private final BajaWhatsappRepository bajas;
    private final ClienteConversacion cliente;
    private final PropiedadesWhatsapp propiedades;
    private final TransactionTemplate enSuTransaccion;

    public EnvioAlBot(AvisoWhatsappRepository avisos, VinculoWhatsappRepository vinculos,
                      BajaWhatsappRepository bajas, ClienteConversacion cliente,
                      PropiedadesWhatsapp propiedades, PlatformTransactionManager transacciones) {
        this.avisos = avisos;
        this.vinculos = vinculos;
        this.bajas = bajas;
        this.cliente = cliente;
        this.propiedades = propiedades;
        this.enSuTransaccion = new TransactionTemplate(transacciones);
        this.enSuTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Una vuelta del sondeo: lo que toca mandar ya, de cada tipo. */
    public void enviarPendientes() {
        if (!cliente.estaEncendido()) {
            return;
        }
        Instant ahora = Instant.now();
        for (UUID id : avisos.pendientesPorEnviar(ahora, PageRequest.of(0, TANDA))) {
            sinRomper(() -> enSuTransaccion.executeWithoutResult(t -> enviarAviso(id)), "aviso", id);
        }
        for (Long id : vinculos.anunciosPorReintentar(ahora, PageRequest.of(0, TANDA))) {
            sinRomper(() -> enSuTransaccion.executeWithoutResult(t -> anunciar(id)), "anuncio", id);
        }
        for (Long id : vinculos.revocacionesPorEnviar(ahora, PageRequest.of(0, TANDA))) {
            sinRomper(() -> enSuTransaccion.executeWithoutResult(t -> revocar(id)), "revocación", id);
        }
    }

    /** El primer intento del anuncio, justo después de pulsar el botón. */
    public void anunciarAhora(Long vinculoId) {
        sinRomper(() -> enSuTransaccion.executeWithoutResult(t -> anunciar(vinculoId)),
                "anuncio", vinculoId);
    }

    /** El primer intento de una revocación pedida por el candidato (dejar de recibir, otro celular). */
    public void revocarAhora(Long vinculoId) {
        sinRomper(() -> enSuTransaccion.executeWithoutResult(t -> revocar(vinculoId)),
                "revocación", vinculoId);
    }

    /**
     * La revocación de un vínculo que ya no existe en la base: el borrado de datos se lleva las
     * filas, así que se intenta una vez y, si el bot no responde, queda escrito en el registro.
     */
    public void revocarSinFila(String telefono, String referencia, String motivo) {
        if (!cliente.estaEncendido()) {
            return;
        }
        try {
            cliente.revocar(new ClienteConversacion.Revocacion(telefono, referencia, motivo));
        } catch (RuntimeException e) {
            log.error("No se pudo avisar al bot de la revocación {} por {}: {}", referencia, motivo,
                    e.getMessage());
        }
    }

    // ============ cada envío, dentro de su transacción ============

    private void enviarAviso(UUID id) {
        AvisoWhatsapp aviso = avisos.paraElSondeo(id).orElse(null);
        if (aviso == null || !AvisoWhatsapp.PENDIENTE.equals(aviso.getEstado())) {
            return;
        }
        Instant ahora = Instant.now();
        VinculoWhatsapp vinculo = vinculos.findById(aviso.getVinculoWhatsappId()).orElse(null);
        // El vínculo se cerró después de encolarlo (dejó de recibir, el proceso terminó, BAJA):
        // ya no toca.
        if (vinculo == null || !vinculo.estaActivo() || bajas.existsById(aviso.getTelefono())) {
            aviso.setEstado(AvisoWhatsapp.OMITIDO);
            aviso.setProximoIntentoEn(null);
            aviso.setActualizadoEn(ahora);
            avisos.save(aviso);
            return;
        }
        aviso.setIntentos(aviso.getIntentos() + 1);
        aviso.setActualizadoEn(ahora);
        try {
            cliente.avisar(new ClienteConversacion.Aviso(aviso.getId(), aviso.getTelefono(),
                    aviso.getReferencia(), aviso.getTipo(), aviso.getNombre(), aviso.getVacante(),
                    aviso.getTexto()));
            aviso.setEstado(AvisoWhatsapp.ENVIADO);
            aviso.setEnviadoEn(ahora);
            aviso.setProximoIntentoEn(null);
            aviso.setUltimoError(null);
        } catch (FalloDelBot fallo) {
            aviso.setUltimoError(recortar(fallo.getMessage()));
            Instant siguiente = siguienteIntento(fallo, aviso.getIntentos(), ahora);
            if (siguiente == null) {
                aviso.setEstado(AvisoWhatsapp.FALLIDO);
                log.warn("El aviso {} de la postulación {} quedó fallido tras {} intento(s): {}",
                        aviso.getId(), aviso.getPostulacionId(), aviso.getIntentos(), fallo.getMessage());
            }
            aviso.setProximoIntentoEn(siguiente);
        }
        avisos.save(aviso);
    }

    private void anunciar(Long vinculoId) {
        VinculoWhatsapp vinculo = vinculos.paraElSondeo(vinculoId).orElse(null);
        Instant ahora = Instant.now();
        if (vinculo == null || !vinculo.esperaSuMensaje(ahora) || vinculo.getAnuncioConfirmadoEn() != null) {
            return;
        }
        vinculo.setAnuncioIntentos(vinculo.getAnuncioIntentos() + 1);
        try {
            cliente.anunciar(new ClienteConversacion.Espera(vinculo.getTelefono(),
                    vinculo.getReferencia(), vinculo.getAnuncioCaducaEn()));
            vinculo.setAnuncioConfirmadoEn(ahora);
            vinculo.setAnuncioProximoIntentoEn(null);
        } catch (FalloDelBot fallo) {
            Instant siguiente = siguienteIntento(fallo, vinculo.getAnuncioIntentos(), ahora);
            vinculo.setAnuncioProximoIntentoEn(siguiente);
            log.warn("El bot no recibió el anuncio {} (intento {}): {}{}", vinculo.getReferencia(),
                    vinculo.getAnuncioIntentos(), fallo.getMessage(),
                    siguiente == null ? ". No se reintenta más" : "");
        }
        vinculos.save(vinculo);
    }

    private void revocar(Long vinculoId) {
        VinculoWhatsapp vinculo = vinculos.paraElSondeo(vinculoId).orElse(null);
        if (vinculo == null || vinculo.getCerradoEn() == null || vinculo.getRevocacionEnviadaEn() != null) {
            return;
        }
        Instant ahora = Instant.now();
        vinculo.setRevocacionIntentos(vinculo.getRevocacionIntentos() + 1);
        try {
            cliente.revocar(new ClienteConversacion.Revocacion(vinculo.getTelefono(),
                    vinculo.getReferencia(), vinculo.getMotivoCierre()));
            vinculo.setRevocacionEnviadaEn(ahora);
            vinculo.setRevocacionProximoIntentoEn(null);
        } catch (FalloDelBot fallo) {
            Instant siguiente = siguienteIntento(fallo, vinculo.getRevocacionIntentos(), ahora);
            vinculo.setRevocacionProximoIntentoEn(siguiente);
            log.warn("El bot no recibió la revocación {} (intento {}): {}{}", vinculo.getReferencia(),
                    vinculo.getRevocacionIntentos(), fallo.getMessage(),
                    siguiente == null ? ". No se reintenta más" : "");
        }
        vinculos.save(vinculo);
    }

    /**
     * Cuándo volver a probar, o {@code null} si ya no: un fallo definitivo, o se acabaron los
     * intentos. La espera crece por cuatro: 30 s, 2 min, 8 min, 32 min.
     */
    Instant siguienteIntento(FalloDelBot fallo, int intentosHechos, Instant ahora) {
        if (!fallo.esReintentable() || intentosHechos >= propiedades.getMaximoDeIntentos()) {
            return null;
        }
        long base = Math.max(1, propiedades.getSegundosAntesDelPrimerReintento());
        long espera = base * (1L << (2 * Math.max(0, intentosHechos - 1)));
        return ahora.plus(Duration.ofSeconds(espera));
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() > LARGO_DEL_ERROR ? texto.substring(0, LARGO_DEL_ERROR) : texto;
    }

    private static void sinRomper(Runnable envio, String queEs, Object id) {
        try {
            envio.run();
        } catch (RuntimeException e) {
            log.error("Falló el envío al bot de WhatsApp ({} {}); se reintenta en la próxima vuelta: {}",
                    queEs, id, e.getMessage());
        }
    }
}
