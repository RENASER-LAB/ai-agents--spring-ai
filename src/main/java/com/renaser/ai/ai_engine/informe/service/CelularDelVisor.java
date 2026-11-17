package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Celular;

import java.util.Optional;

/**
 * El celular del candidato en el visor de informes: cuál se enseña, cómo se escribe y con qué
 * enlaces.
 *
 * <ul>
 *   <li><b>Cuál:</b> el de su cuenta ({@code persona.telefono}, el que escribió al registrarse).
 *       Si no tiene, el que la IA leyó del CV ({@code dato_cv.telefono}, tal cual venía). Si no
 *       hay ninguno, no sale nada.</li>
 *   <li><b>Cómo:</b> un celular peruano —nueve cifras que empiezan por 9, con o sin el 51
 *       delante— se escribe «987 654 321». Lo demás se enseña tal como está escrito.</li>
 *   <li><b>Enlaces:</b> siempre uno para llamar ({@code tel:}). El de WhatsApp
 *       ({@code https://wa.me/51…}) solo para un celular peruano: con un fijo o un número
 *       extranjero raro no se puede armar uno que sirva, y uno roto es peor que ninguno.</li>
 * </ul>
 *
 * <p>Sin estado ni base de datos: es una cuenta que se prueba sola.
 */
public final class CelularDelVisor {

    private static final String PREFIJO_PERU = "51";
    private static final int CIFRAS_CELULAR = 9;
    private static final int MINIMO_PARA_LLAMAR = 6;
    private static final int MAXIMO_PARA_LLAMAR = 15;

    private CelularDelVisor() {
    }

    /** El de la cuenta y, si no tiene, el del CV. */
    public static Optional<Celular> de(String deLaCuenta, String delCv) {
        Optional<Celular> suyo = deUno(deLaCuenta);
        return suyo.isPresent() ? suyo : deUno(delCv);
    }

    /**
     * Un número escrito de cualquier forma, o vacío si no trae cifras con que llamar.
     *
     * <p>Un CV a veces trae dos («01 456 7890 / 987 654 321»): si junto no es un número, se prueba
     * por partes y se prefiere el celular; si no hay ninguno, el primero con que se pueda llamar.
     */
    static Optional<Celular> deUno(String escrito) {
        if (escrito == null || escrito.isBlank()) {
            return Optional.empty();
        }
        Optional<Celular> entero = numero(escrito);
        if (entero.isPresent()) {
            return entero;
        }
        Optional<Celular> primero = Optional.empty();
        for (String parte : escrito.split("[/,;|]")) {
            Optional<Celular> suyo = numero(parte);
            if (suyo.isPresent() && suyo.get().whatsapp() != null) {
                return suyo;
            }
            if (primero.isEmpty()) {
                primero = suyo;
            }
        }
        return primero;
    }

    private static Optional<Celular> numero(String escrito) {
        if (escrito.isBlank()) {
            return Optional.empty();
        }
        String original = escrito.strip().replaceAll("\\s{2,}", " ");
        String cifras = soloCifras(original);
        String celular = celularPeruano(cifras);
        if (celular != null) {
            return Optional.of(new Celular(
                    celular.substring(0, 3) + " " + celular.substring(3, 6) + " " + celular.substring(6),
                    "tel:+" + PREFIJO_PERU + celular,
                    "https://wa.me/" + PREFIJO_PERU + celular));
        }
        if (cifras.length() < MINIMO_PARA_LLAMAR || cifras.length() > MAXIMO_PARA_LLAMAR) {
            return Optional.empty();
        }
        boolean internacional = original.startsWith("+") || original.startsWith("00");
        String paraLlamar = internacional && cifras.startsWith("00") ? cifras.substring(2) : cifras;
        return Optional.of(new Celular(original, "tel:" + (internacional ? "+" : "") + paraLlamar, null));
    }

    /** Las nueve cifras de un celular peruano («987654321»), o nulo si no lo es. */
    private static String celularPeruano(String cifras) {
        String sinPrefijo = cifras;
        if (sinPrefijo.startsWith("00" + PREFIJO_PERU)) {
            sinPrefijo = sinPrefijo.substring(4);
        } else if (sinPrefijo.length() == CIFRAS_CELULAR + 2 && sinPrefijo.startsWith(PREFIJO_PERU)) {
            sinPrefijo = sinPrefijo.substring(2);
        }
        return sinPrefijo.length() == CIFRAS_CELULAR && sinPrefijo.charAt(0) == '9' ? sinPrefijo : null;
    }

    private static String soloCifras(String texto) {
        StringBuilder cifras = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c >= '0' && c <= '9') {
                cifras.append(c);
            }
        }
        return cifras.toString();
    }
}
