package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.GuardarBrochure;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.GuardarPresentacion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Qué se puede guardar en la presentación de una empresa (V85; misión, visión, plantilla y color
 * del brochure desde la V86), campo a campo.
 *
 * <p>Todo es opcional. Los espacios de los bordes se quitan y un campo vacío es «sin dato». Si
 * algo no vale, no se guarda nada: se juntan todos los errores en una
 * {@link PresentacionInvalidaException} para que el panel los marque a la vez.
 *
 * <p>El panel tiene las mismas reglas y las mismas palabras en
 * {@code src/dominio/presentacionEmpresa.ts}. No hay contrato que las amarre: cada lado tiene sus
 * pruebas. Cambiar una regla es cambiarla en los dos.
 */
public final class ReglasDeLaPresentacion {

    public static final int MAXIMO_TEXTO_LARGO = 1500;
    public static final int MAXIMO_SECTOR = 60;
    public static final int MAXIMO_BENEFICIOS = 12;
    public static final int MAXIMO_BENEFICIO = 40;
    public static final int ANIO_MINIMO = 1800;
    /** Misión y visión (V86). */
    public static final int MAXIMO_MISION = 400;

    public static final String MENSAJE_PROPOSITO = "El propósito admite hasta 1500 caracteres";
    public static final String MENSAJE_QUIENES = "«Quiénes somos» admite hasta 1500 caracteres";
    public static final String MENSAJE_SECTOR = "El sector admite hasta 60 caracteres";
    public static final String MENSAJE_TAMANO = "Elige uno de los tamaños de la lista";
    public static final String MENSAJE_BENEFICIOS = "Puedes añadir hasta 12 beneficios";
    public static final String MENSAJE_BENEFICIO = "Cada beneficio admite hasta 40 caracteres";
    public static final String MENSAJE_RED_DESCONOCIDA = "Esa red no está entre las que se admiten";
    public static final String MENSAJE_MISION = "La misión admite hasta 400 caracteres";
    public static final String MENSAJE_VISION = "La visión admite hasta 400 caracteres";
    public static final String MENSAJE_PLANTILLA = "Esa plantilla no está entre las que se ofrecen";
    public static final String MENSAJE_COLOR = "Ese color no está entre los que se ofrecen";

    private ReglasDeLaPresentacion() {
    }

    /**
     * Lo que queda para guardar, ya limpio y comprobado. Vacío es nulo; las listas, vacías.
     *
     * @param plantillaBrochure la elegida, o nula si no vino: entonces se queda la que había
     * @param colorBrochure     el elegido, o nulo si no vino: entonces se queda el que había
     */
    public record PresentacionLimpia(String sector, String tamano, Integer anioFundacion,
                                     String quienesSomos, String propositoEmpresa,
                                     List<String> beneficios, String videoPresentacion,
                                     String paginaWeb, Map<RedSocial, String> redes,
                                     String mision, String vision,
                                     PlantillaDelBrochure plantillaBrochure,
                                     ColorDelBrochure colorBrochure) {}

    /** La plantilla y el color que guarda la pestaña «Brochure», ya comprobados. */
    public record BrochureLimpio(PlantillaDelBrochure plantilla, ColorDelBrochure color) {}

    /** El mensaje del año, con el año actual de Lima: «Escribe un año entre 1800 y 2026». */
    public static String mensajeAnio(int anioActual) {
        return "Escribe un año entre " + ANIO_MINIMO + " y " + anioActual;
    }

    /**
     * Limpia y comprueba todo junto.
     *
     * @param anioActual el año de hoy en Lima: el techo del año de fundación vive aquí y no en
     *                   la base (ver la V85)
     * @throws PresentacionInvalidaException con todos los campos que fallan
     */
    public static PresentacionLimpia limpiar(GuardarPresentacion datos, int anioActual) {
        Map<String, String> errores = new LinkedHashMap<>();

        String sector = texto(datos.sector());
        if (sector != null && sector.length() > MAXIMO_SECTOR) {
            errores.put("sector", MENSAJE_SECTOR);
        }
        String tamano = texto(datos.tamano());
        if (tamano != null && TamanoDeEmpresa.deCodigo(tamano).isEmpty()) {
            errores.put("tamano", MENSAJE_TAMANO);
        }
        Integer anio = datos.anioFundacion();
        if (anio != null && (anio < ANIO_MINIMO || anio > anioActual)) {
            errores.put("anioFundacion", mensajeAnio(anioActual));
        }
        String quienes = texto(datos.quienesSomos());
        if (quienes != null && quienes.length() > MAXIMO_TEXTO_LARGO) {
            errores.put("quienesSomos", MENSAJE_QUIENES);
        }
        String proposito = texto(datos.propositoEmpresa());
        if (proposito != null && proposito.length() > MAXIMO_TEXTO_LARGO) {
            errores.put("propositoEmpresa", MENSAJE_PROPOSITO);
        }
        String mision = texto(datos.mision());
        if (mision != null && mision.length() > MAXIMO_MISION) {
            errores.put("mision", MENSAJE_MISION);
        }
        String vision = texto(datos.vision());
        if (vision != null && vision.length() > MAXIMO_MISION) {
            errores.put("vision", MENSAJE_VISION);
        }
        List<String> beneficios = beneficios(datos.beneficios(), errores);

        String video = EnlacesDeLaEmpresa.normalizar(datos.videoPresentacion());
        poner(errores, "videoPresentacion", EnlacesDeLaEmpresa.errorDelVideo(video));
        String web = EnlacesDeLaEmpresa.normalizar(datos.paginaWeb());
        poner(errores, "paginaWeb", EnlacesDeLaEmpresa.errorDelEnlace(web));
        Map<RedSocial, String> redes = redes(datos.redes(), errores);

        // La plantilla y el color: fuera del catálogo es un error; ausentes, se queda lo de antes.
        String plantillaCruda = texto(datos.plantillaBrochure());
        PlantillaDelBrochure plantilla = PlantillaDelBrochure.deCodigo(plantillaCruda).orElse(null);
        if (plantillaCruda != null && plantilla == null) {
            errores.put("plantillaBrochure", MENSAJE_PLANTILLA);
        }
        String colorCrudo = texto(datos.colorBrochure());
        ColorDelBrochure color = ColorDelBrochure.deCodigo(colorCrudo).orElse(null);
        if (colorCrudo != null && color == null) {
            errores.put("colorBrochure", MENSAJE_COLOR);
        }

        if (!errores.isEmpty()) {
            throw new PresentacionInvalidaException(errores);
        }
        return new PresentacionLimpia(sector, tamano, anio, quienes, proposito, beneficios, video,
                web, redes, mision, vision, plantilla, color);
    }

    /**
     * Comprueba la elección de la pestaña «Brochure». Aquí los dos son obligatorios: la pestaña
     * manda siempre la plantilla y el color que se ven, y ausente no es «lo de antes» sino un
     * error, como uno que no está en el catálogo.
     *
     * @throws PresentacionInvalidaException con la plantilla, el color o los dos
     */
    public static BrochureLimpio limpiarBrochure(GuardarBrochure datos) {
        Map<String, String> errores = new LinkedHashMap<>();
        PlantillaDelBrochure plantilla = PlantillaDelBrochure.deCodigo(datos.plantillaBrochure())
                .orElse(null);
        if (plantilla == null) {
            errores.put("plantillaBrochure", MENSAJE_PLANTILLA);
        }
        ColorDelBrochure color = ColorDelBrochure.deCodigo(datos.colorBrochure()).orElse(null);
        if (color == null) {
            errores.put("colorBrochure", MENSAJE_COLOR);
        }
        if (!errores.isEmpty()) {
            throw new PresentacionInvalidaException(errores);
        }
        return new BrochureLimpio(plantilla, color);
    }

    /**
     * Sin vacías ni repetidas (sin mirar mayúsculas), en su orden. Más de 12, o alguna de más de
     * 40, es un error: el panel no deja escribirlas, así que solo llegan por la API.
     */
    private static List<String> beneficios(List<String> crudos, Map<String, String> errores) {
        List<String> limpios = new ArrayList<>();
        if (crudos == null) {
            return limpios;
        }
        List<String> vistos = new ArrayList<>();
        for (String crudo : crudos) {
            String limpio = texto(crudo);
            if (limpio == null || vistos.contains(limpio.toLowerCase(Locale.ROOT))) {
                continue;
            }
            vistos.add(limpio.toLowerCase(Locale.ROOT));
            limpios.add(limpio);
            if (limpio.length() > MAXIMO_BENEFICIO) {
                errores.putIfAbsent("beneficios", MENSAJE_BENEFICIO);
            }
        }
        if (limpios.size() > MAXIMO_BENEFICIOS) {
            errores.put("beneficios", MENSAJE_BENEFICIOS);
        }
        return limpios;
    }

    /** Cada red con su enlace normalizado y comprobado contra su dominio. Vacía, no está. */
    private static Map<RedSocial, String> redes(Map<String, String> crudas,
                                                Map<String, String> errores) {
        Map<RedSocial, String> limpias = new EnumMap<>(RedSocial.class);
        if (crudas == null) {
            return limpias;
        }
        crudas.forEach((codigo, crudo) -> {
            String enlace = EnlacesDeLaEmpresa.normalizar(crudo);
            if (enlace == null) {
                return;
            }
            RedSocial.deCodigo(codigo).ifPresentOrElse(red -> {
                String error = EnlacesDeLaEmpresa.errorDeLaRed(red, enlace);
                if (error == null) {
                    limpias.put(red, enlace);
                } else {
                    errores.put(red.name(), error);
                }
            }, () -> errores.put(String.valueOf(codigo), MENSAJE_RED_DESCONOCIDA));
        });
        return limpias;
    }

    private static void poner(Map<String, String> errores, String campo, String error) {
        if (error != null) {
            errores.put(campo, error);
        }
    }

    /** Sin espacios en los bordes; en blanco es nulo. */
    private static String texto(String crudo) {
        if (crudo == null) {
            return null;
        }
        String limpio = crudo.strip();
        return limpio.isEmpty() ? null : limpio;
    }
}
