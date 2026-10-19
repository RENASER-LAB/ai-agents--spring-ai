package com.renaser.ai.ai_engine.whatsapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Las referencias opacas de la consulta v2 (V76): C-1 y el cursor de C-3. */
@DisplayName("Las referencias opacas de la consulta v2")
class ReferenciasOpacasTest {

    private static final String SECRETO = "secreto-de-pruebas-desde-el-bot-de-whatsapp";
    private static final String ANA = "+51987654321";
    private static final String BEA = "+51912345678";

    @Test
    @DisplayName("AC-02 · el cursor vuelve a dar su posición, pero solo para el número que lo pidió")
    void elCursorEsDelNumero() {
        String cursor = ReferenciasOpacas.cursor(SECRETO, ANA, 1234L);

        assertThat(cursor).hasSizeLessThanOrEqualTo(ReferenciasOpacas.LARGO_DEL_CURSOR).doesNotContain("1234");
        assertThat(ReferenciasOpacas.leerCursor(SECRETO, ANA, cursor)).hasValue(1234L);
        assertThat(ReferenciasOpacas.leerCursor(SECRETO, BEA, cursor)).isEmpty();
        assertThat(ReferenciasOpacas.leerCursor("otro-secreto-de-pruebas-de-32-bytes", ANA, cursor)).isEmpty();
    }

    @Test
    @DisplayName("Un cursor alterado, inventado o vacío no se lee")
    void cursoresQueNoValen() {
        String bueno = ReferenciasOpacas.cursor(SECRETO, ANA, 1234L);
        String otraPosicion = bueno.replaceFirst("\\.[0-9a-z]+\\.", ".zz.");

        assertThat(ReferenciasOpacas.leerCursor(SECRETO, ANA, otraPosicion)).isEmpty();
        assertThat(ReferenciasOpacas.leerCursor(SECRETO, ANA, "c1.1.abc")).isEmpty();
        assertThat(ReferenciasOpacas.leerCursor(SECRETO, ANA, "cualquier cosa")).isEmpty();
        assertThat(ReferenciasOpacas.leerCursor(SECRETO, ANA, "c1..")).isEmpty();
        assertThat(ReferenciasOpacas.leerCursor(SECRETO, ANA, null)).isEmpty();
        assertThat(ReferenciasOpacas.leerCursor(SECRETO, ANA, "c1." + "a".repeat(250))).isEmpty();
    }

    @Test
    @DisplayName("C-1 · la referencia de la vacante es opaca y estable, y no deja ver su id")
    void laDeLaVacante() {
        String ref = ReferenciasOpacas.deLaVacante(SECRETO, 42L);

        assertThat(ref).matches("vc_[0-9a-f]{32}").doesNotContain("42");
        assertThat(ReferenciasOpacas.deLaVacante(SECRETO, 42L)).isEqualTo(ref);
        assertThat(ReferenciasOpacas.deLaVacante(SECRETO, 43L)).isNotEqualTo(ref);
        // Sin secreto no revienta: la puerta ya está cerrada por la firma.
        assertThat(ReferenciasOpacas.deLaVacante(null, 42L)).startsWith("vc_");
    }

    @Test
    @DisplayName("C-1 · solo «pw_» y 32 cifras hexadecimales parecen una referencia de vínculo")
    void pareceReferencia() {
        assertThat(ReferenciasOpacas.pareceReferencia("pw_" + "0123456789abcdef".repeat(2))).isTrue();
        assertThat(ReferenciasOpacas.pareceReferencia("pw_" + "0123456789ABCDEF".repeat(2))).isFalse();
        assertThat(ReferenciasOpacas.pareceReferencia("pw_123")).isFalse();
        assertThat(ReferenciasOpacas.pareceReferencia("70")).isFalse();
        assertThat(ReferenciasOpacas.pareceReferencia(null)).isFalse();
    }
}
