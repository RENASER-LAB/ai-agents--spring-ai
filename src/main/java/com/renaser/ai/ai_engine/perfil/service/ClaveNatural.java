package com.renaser.ai.ai_engine.perfil.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * La forma en que el perfil decide que dos textos son «el mismo»: sin mayúsculas, sin tildes
 * y sin espacios de más.
 *
 * <p>Vive aparte porque la usan dos sitios que tienen que coincidir: el merge del currículum
 * («Analista Senior» del modelo y «analista senior» de la persona son el mismo empleo) y el
 * alta de idiomas del portal. Cuando cada uno tenía la suya, «ingles» entraba como un idioma
 * distinto de «Inglés».
 */
public final class ClaveNatural {

    private ClaveNatural() {
    }

    public static String de(String texto) {
        if (texto == null) {
            return "";
        }
        String plano = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return plano.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
