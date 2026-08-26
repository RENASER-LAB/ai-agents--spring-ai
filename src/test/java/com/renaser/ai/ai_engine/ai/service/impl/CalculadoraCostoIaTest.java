package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TarifaModelo;
import com.renaser.ai.ai_engine.ai.repository.TarifaModeloRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El precio de una llamada al modelo (pieza E).
 *
 * <p>Lo que se protege: que la tarifa que rige sea la vigente <b>en el momento del
 * cierre</b> —un cambio de precios no reescribe el pasado—, y que la contabilidad jamás
 * rompa una calificación: sin tarifa o sin tokens, el costo queda vacío y se sigue.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La calculadora del costo de cada llamada al modelo")
class CalculadoraCostoIaTest {

    @Mock private TarifaModeloRepository tarifas;

    private CalculadoraCostoIa calculadora() {
        return new CalculadoraCostoIa(tarifas);
    }

    private TarifaModelo tarifa(String entrada, String salida) {
        return TarifaModelo.builder()
                .proveedor("deepseek").modelo("deepseek-v4-flash")
                .precioEntradaPorMillon(new BigDecimal(entrada))
                .precioSalidaPorMillon(new BigDecimal(salida))
                .vigenteDesde(Instant.now().minusSeconds(3600))
                .build();
    }

    @Test
    void elCostoEsTokensPorPrecioEntreUnMillon() {
        when(tarifas.findFirstByProveedorIgnoreCaseAndModeloIgnoreCaseAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                eq("deepseek"), eq("deepseek-v4-flash"), any(Instant.class)))
                .thenReturn(Optional.of(tarifa("0.27", "1.10")));

        BigDecimal costo = calculadora().costoDe("deepseek", "deepseek-v4-flash",
                1_000_000, 100_000, Instant.now());

        // 1M de entrada a 0.27 + 100k de salida a 1.10 = 0.27 + 0.11
        assertThat(costo).isEqualByComparingTo("0.38");
    }

    @Test
    void laTarifaQueRigeEsLaVigenteEnElMomentoDelCierre() {
        // El repositorio recibe el momento del cierre, no «la última»: es la consulta la
        // que resuelve la vigencia (vigente_desde más reciente <= momento), y aquí se
        // comprueba que el momento viaja hasta ella — sin él, un cambio de precios de
        // hoy reescribiría lo ejecutado ayer.
        Instant cierre = Instant.parse("2026-08-15T12:00:00Z");
        when(tarifas.findFirstByProveedorIgnoreCaseAndModeloIgnoreCaseAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                "deepseek", "deepseek-v4-flash", cierre))
                .thenReturn(Optional.of(tarifa("0.27", "1.10")));

        calculadora().costoDe("deepseek", "deepseek-v4-flash", 10, 10, cierre);

        verify(tarifas)
                .findFirstByProveedorIgnoreCaseAndModeloIgnoreCaseAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                        "deepseek", "deepseek-v4-flash", cierre);
    }

    @Test
    void sinTarifaElCostoQuedaVacioYNadaRevienta() {
        when(tarifas.findFirstByProveedorIgnoreCaseAndModeloIgnoreCaseAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                any(), any(), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThat(calculadora().costoDe("deepseek", "modelo-nuevo", 100, 100, Instant.now()))
                .isNull();
    }

    @Test
    void sinNingunTokenNoHayNadaQueCobrarNiTarifaQueBuscar() {
        // El proveedor no devolvió el uso: no se midió nada y no se inventa un precio.
        assertThat(calculadora().costoDe("deepseek", "deepseek-v4-flash", null, null, Instant.now()))
                .isNull();
        verifyNoInteractions(tarifas);
    }

    @Test
    void unConteoAusenteValeCeroYElOtroSeCobra() {
        // El embedding de Google no devuelve tokens de salida: su costo es solo la entrada.
        when(tarifas.findFirstByProveedorIgnoreCaseAndModeloIgnoreCaseAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                eq("google"), eq("gemini-embedding-2"), any(Instant.class)))
                .thenReturn(Optional.of(TarifaModelo.builder()
                        .proveedor("google").modelo("gemini-embedding-2")
                        .precioEntradaPorMillon(new BigDecimal("0.15"))
                        .precioSalidaPorMillon(BigDecimal.ZERO)
                        .vigenteDesde(Instant.now().minusSeconds(60))
                        .build()));

        assertThat(calculadora().costoDe("google", "gemini-embedding-2",
                2_000_000, null, Instant.now()))
                .isEqualByComparingTo("0.30");
    }
}
