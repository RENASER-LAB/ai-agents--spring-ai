package com.renaser.ai.ai_engine.organizacion.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Todo lo que lleva el brochure de una empresa (V86), ya leído y limpio: el perfil público pasado
 * a papel, más misión y visión, la plantilla, el color y la fecha del pie.
 *
 * <p><b>También es la huella del brochure.</b> Es un {@code record} sin bytes —del logo y la
 * portada solo lleva el id del archivo—, así que dos contenidos son iguales si y solo si el PDF
 * sería el mismo. {@code ServicioBrochureImpl} recuerda el último PDF de cada empresa junto a su
 * contenido y solo lo vuelve a armar cuando el contenido cambia: un «Guardar» del módulo, una
 * sede que se activa o se desactiva, otro logo o un día nuevo.
 *
 * @param nombre         el de la empresa; siempre está
 * @param provincias     las provincias de sus sedes activas, sin repetir y sin direcciones
 * @param contacto       el perfil en EX, la web, las redes y el video; solo los http o https
 * @param logoArchivoId  el archivo del logo, o nulo
 * @param fecha          la del pie, «dd/MM/aaaa» en Lima
 */
public record ContenidoDelBrochure(String nombre, String sector, String tamanoTexto,
                                   Integer anioFundacion, String quienesSomos, String proposito,
                                   String mision, String vision, List<String> beneficios,
                                   List<String> provincias, List<Contacto> contacto,
                                   Long logoArchivoId, Long portadaArchivoId,
                                   PlantillaDelBrochure plantilla, ColorDelBrochure color,
                                   String fecha) {

    /** Las formas societarias no cuentan para las iniciales, como en el portal. */
    private static final Set<String> FORMAS_SOCIETARIAS =
            Set.of("sac", "sa", "saa", "srl", "eirl", "sacs");

    /**
     * Un enlace de «Contacto», con lo que se lee y la dirección escrita para quien lo imprima.
     *
     * @param rotulo  «Perfil en EX», «Página web», «Facebook»…, «Video de presentación»
     * @param url     la dirección que se abre al pulsar; siempre http o https
     * @param escrito la misma dirección sin el esquema, para leerla en papel
     */
    public record Contacto(String rotulo, String url, String escrito) {}

    public ContenidoDelBrochure {
        beneficios = beneficios == null ? List.of() : List.copyOf(beneficios);
        provincias = provincias == null ? List.of() : List.copyOf(provincias);
        contacto = contacto == null ? List.of() : List.copyOf(contacto);
        plantilla = plantilla == null ? PlantillaDelBrochure.POR_DEFECTO : plantilla;
        color = color == null ? ColorDelBrochure.POR_DEFECTO : color;
    }

    /** «Sector · Tamaño · Fundada en AAAA», solo con lo que haya; vacío si no hay nada. */
    public String linea() {
        List<String> partes = new ArrayList<>();
        if (hay(sector)) {
            partes.add(sector.strip());
        }
        if (hay(tamanoTexto)) {
            partes.add(tamanoTexto.strip());
        }
        if (anioFundacion != null) {
            partes.add("Fundada en " + anioFundacion);
        }
        return String.join(" · ", partes);
    }

    /**
     * Las dos letras que van en lugar del logo: la primera de las dos primeras palabras, sin las
     * formas societarias («S.A.C.»), como las del portal. Un punto medio si no queda ninguna.
     */
    public String iniciales() {
        StringBuilder letras = new StringBuilder();
        for (String palabra : (nombre == null ? "" : nombre).split("\\s")) {
            String limpia = soloLetrasYCifras(palabra);
            if (limpia.isEmpty() || FORMAS_SOCIETARIAS.contains(limpia.toLowerCase(Locale.ROOT))) {
                continue;
            }
            letras.appendCodePoint(Character.toUpperCase(limpia.codePointAt(0)));
            if (letras.codePointCount(0, letras.length()) == 2) {
                break;
            }
        }
        return letras.isEmpty() ? "·" : letras.toString();
    }

    public boolean hayMision() {
        return hay(mision);
    }

    public boolean hayVision() {
        return hay(vision);
    }

    public boolean hayProposito() {
        return hay(proposito);
    }

    public static boolean hay(String texto) {
        return texto != null && !texto.isBlank();
    }

    private static String soloLetrasYCifras(String palabra) {
        StringBuilder salida = new StringBuilder();
        palabra.codePoints().filter(Character::isLetterOrDigit).forEach(salida::appendCodePoint);
        return salida.toString();
    }
}
