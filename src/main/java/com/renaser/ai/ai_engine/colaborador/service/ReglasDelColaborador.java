package com.renaser.ai.ai_engine.colaborador.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Las reglas de la ficha que valen igual para el alta a mano, el reingreso, los cambios y la
 * carga por Excel.
 *
 * <p>Cada una devuelve el mensaje del error o {@code null} si está bien, y no lanza: el alta
 * a mano se para en el primero, pero la carga por Excel tiene que juntar <b>todos</b> los de
 * un archivo y enseñarlos de una vez. Escritas una sola vez, las dos puertas no pueden
 * empezar a aceptar cosas distintas.
 */
public final class ReglasDelColaborador {

    private ReglasDelColaborador() {}

    public static final int EDAD_MINIMA = 14;

    private static final Pattern DNI = Pattern.compile("^[0-9]{8}$");
    private static final Pattern OTRO_DOCUMENTO = Pattern.compile("^[A-Z0-9]{4,15}$");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * El número tal como se guarda: sin espacios, puntos ni guiones, y en mayúsculas. Un DNI
     * copiado como «45.123.456» es el mismo DNI.
     */
    public static String limpiarDocumento(String numero) {
        if (numero == null) return null;
        return numero.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);
    }

    /** Un texto recortado, o null si no queda nada: un nombre de solo espacios no es un nombre. */
    public static String texto(String valor) {
        if (valor == null) return null;
        String limpio = valor.strip();
        return limpio.isEmpty() ? null : limpio;
    }

    public static String errorDelDocumento(String tipo, String numeroLimpio) {
        if (tipo == null || !CatalogosDePersonas.TIPOS_DOCUMENTO.containsKey(tipo)) {
            return "El tipo de documento no es válido";
        }
        if (numeroLimpio == null || numeroLimpio.isEmpty()) {
            return "Falta el número de documento";
        }
        if (CatalogosDePersonas.DNI.equals(tipo)) {
            return DNI.matcher(numeroLimpio).matches() ? null : "El DNI tiene que tener 8 dígitos";
        }
        return OTRO_DOCUMENTO.matcher(numeroLimpio).matches() ? null
                : "El " + CatalogosDePersonas.TIPOS_DOCUMENTO.get(tipo)
                  + " admite de 4 a 15 letras o dígitos";
    }

    public static String errorDelNacimiento(LocalDate nacimiento, LocalDate hoy) {
        if (nacimiento == null) {
            return "Falta la fecha de nacimiento";
        }
        if (nacimiento.isAfter(hoy)) {
            return "La fecha de nacimiento no puede ser futura";
        }
        if (nacimiento.plusYears(EDAD_MINIMA).isAfter(hoy)) {
            return "Tiene que tener al menos " + EDAD_MINIMA + " años";
        }
        return null;
    }

    public static String errorDelCorreo(String correo) {
        if (correo == null) return null;
        return CORREO.matcher(correo).matches() ? null : "El correo «" + correo + "» no es válido";
    }

    /**
     * El tipo de contrato y su fecha de fin, contra la fecha de ingreso del periodo.
     *
     * <p>A plazo y de temporada la exigen; el indeterminado no la admite; tiempo parcial,
     * «otro» y prácticas la admiten sin pedirla.
     */
    public static String errorDelContrato(String tipo, LocalDate fin, LocalDate ingreso) {
        if (tipo == null || !CatalogosDePersonas.TIPOS_CONTRATO.containsKey(tipo)) {
            return "El tipo de contrato no es válido";
        }
        String nombre = CatalogosDePersonas.TIPOS_CONTRATO.get(tipo).toLowerCase(Locale.ROOT);
        if (CatalogosDePersonas.CONTRATOS_CON_FIN.contains(tipo) && fin == null) {
            return "Un contrato " + nombre + " necesita la fecha de fin";
        }
        if (CatalogosDePersonas.INDETERMINADO.equals(tipo) && fin != null) {
            return "Un contrato a plazo indeterminado no lleva fecha de fin";
        }
        if (fin != null && ingreso != null && !fin.isAfter(ingreso)) {
            return "El fin del contrato tiene que ser posterior a la fecha de ingreso";
        }
        return null;
    }

    public static String errorDelPeriodoDePrueba(LocalDate fin, LocalDate ingreso) {
        if (fin != null && ingreso != null && !fin.isAfter(ingreso)) {
            return "El fin del periodo de prueba tiene que ser posterior a la fecha de ingreso";
        }
        return null;
    }

    public static String errorDelRegimen(String regimen) {
        return regimen != null && CatalogosDePersonas.REGIMENES.containsKey(regimen) ? null
                : "El régimen laboral no es válido";
    }

    public static String errorDelSueldo(BigDecimal sueldo, String moneda) {
        if (sueldo == null) {
            return null;
        }
        if (sueldo.signum() < 0) {
            return "El sueldo no puede ser negativo";
        }
        if (sueldo.scale() > 2 || sueldo.precision() - sueldo.scale() > 10) {
            return "El sueldo admite hasta 10 cifras y 2 decimales";
        }
        if (moneda != null && !CatalogosDePersonas.MONEDAS.containsKey(moneda)) {
            return "La moneda tiene que ser soles o dólares";
        }
        return null;
    }
}
