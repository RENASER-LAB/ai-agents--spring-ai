package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Anuncios;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.FalloDelBot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lo que el bot recoge cuando le escribe un número que no conoce: los anuncios vigentes de ese
 * número que EX no le pudo empujar (el CRM apagado de noche, una caída o un despliegue agotan los
 * reintentos de {@link EnvioAlBot}). Así el vínculo se resuelve antes de contestar, llegue primero
 * el anuncio o el mensaje.
 *
 * <p>Cada anuncio entregado queda confirmado, como si el empuje hubiera llegado: el sondeo deja de
 * reintentarlo. Un vínculo sin su consentimiento no se entrega (el bot no puede guardar una espera
 * sin evidencia).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnunciosPendientes {

    /** Lo que el bot acepta en una respuesta: más no cabe en su contrato. */
    static final int MAXIMO = 20;

    private final VinculoWhatsappRepository vinculos;
    private final EnvioAlBot envio;

    /**
     * @throws IllegalArgumentException si el teléfono no es E.164 (400)
     */
    @Transactional
    public Anuncios delNumero(String telefono) {
        String numero = Celular.delBot(telefono).orElseThrow(() -> new IllegalArgumentException(
                "El teléfono tiene que venir en formato internacional (E.164), como +51987654321"));
        Instant ahora = Instant.now();
        List<Map<String, Object>> esperas = new ArrayList<>();
        for (VinculoWhatsapp vinculo : vinculos.vivosDelNumeroParaEscribir(numero)) {
            if (esperas.size() >= MAXIMO) {
                break;
            }
            if (!vinculo.esperaSuMensaje(ahora)) {
                continue;
            }
            try {
                esperas.add(envio.esperaDe(vinculo).comoCuerpo());
            } catch (FalloDelBot fallo) {
                log.warn("El anuncio {} no se entrega al bot: {}", vinculo.getReferencia(), fallo.getMessage());
                continue;
            }
            if (vinculo.getAnuncioConfirmadoEn() == null) {
                vinculo.setAnuncioConfirmadoEn(ahora);
                vinculo.setAnuncioProximoIntentoEn(null);
                vinculos.save(vinculo);
                log.info("El bot recogió el anuncio {} que no le había llegado", vinculo.getReferencia());
            }
        }
        return new Anuncios(esperas);
    }
}
