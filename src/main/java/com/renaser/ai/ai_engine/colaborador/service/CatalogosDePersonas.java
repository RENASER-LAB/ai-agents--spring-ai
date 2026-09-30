package com.renaser.ai.ai_engine.colaborador.service;

import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Opcion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Los catálogos cerrados de la ficha del colaborador, con su texto.
 *
 * <p>Se guardan los códigos de SUNAT (tablas 3, 12, 17 y 33) para que Planillas los lea sin
 * migrar valores, y en pantalla se enseña el texto, nunca el código. Esta clase es el único
 * sitio que los traduce: el panel los pide por la API en vez de escribirlos a mano, y los
 * CHECK de la V64 son la misma lista del lado de la base.
 */
public final class CatalogosDePersonas {

    private CatalogosDePersonas() {}

    public static final String DNI = "01";

    /** Tabla 3 de SUNAT, las cinco que se usan en una planilla peruana. */
    public static final Map<String, String> TIPOS_DOCUMENTO = ordenado(
            "01", "DNI",
            "04", "Carné de extranjería",
            "07", "Pasaporte",
            "23", "PTP",
            "26", "CPP");

    public static final Map<String, String> SEXOS = ordenado(
            "M", "Masculino",
            "F", "Femenino");

    public static final Map<String, String> ESTADOS_CIVILES = ordenado(
            "SOLTERO", "Soltero(a)",
            "CASADO", "Casado(a)",
            "CONVIVIENTE", "Conviviente",
            "DIVORCIADO", "Divorciado(a)",
            "VIUDO", "Viudo(a)");

    /** Tabla 12 de SUNAT, más el convenio de prácticas como opción propia. */
    public static final Map<String, String> TIPOS_CONTRATO = ordenado(
            "01", "A plazo indeterminado",
            "02", "A tiempo parcial",
            "03", "Por inicio o incremento de actividad",
            "04", "Por necesidades del mercado",
            "05", "Por reconversión empresarial",
            "06", "Ocasional",
            "07", "De suplencia",
            "08", "De emergencia",
            "09", "Para obra determinada o servicio específico",
            "10", "Intermitente",
            "11", "De temporada",
            "99", "Otro",
            "PRACTICAS", "Convenio de prácticas");

    /** El indeterminado no admite fecha de fin. */
    public static final String INDETERMINADO = "01";

    /**
     * Los contratos a plazo (sujetos a modalidad) y el de temporada: su fecha de fin es
     * obligatoria. Tiempo parcial, «otro» y prácticas la admiten sin exigirla.
     */
    public static final Set<String> CONTRATOS_CON_FIN =
            Set.of("03", "04", "05", "06", "07", "08", "09", "10", "11");

    /** Tabla 33 de SUNAT. */
    public static final Map<String, String> REGIMENES = ordenado(
            "01", "General",
            "16", "Microempresa",
            "17", "Pequeña empresa",
            "18", "Agrario (Ley 27360)",
            "26", "Agrario (Ley 31110)",
            "20", "Minero",
            "21", "Construcción civil",
            "99", "Otro");

    public static final Map<String, String> MONEDAS = ordenado(
            "PEN", "Soles (S/)",
            "USD", "Dólares (US$)");

    /** Los motivos que se eligen al registrar un cambio. */
    public static final Map<String, String> MOTIVOS_CAMBIO = ordenado(
            "PROMOCION", "Promoción",
            "TRASLADO", "Traslado",
            "CAMBIO_JEFE", "Cambio de jefe",
            "RENOVACION_CONTRATO", "Renovación de contrato",
            "AJUSTE_REMUNERACION", "Ajuste de remuneración",
            "CORRECCION", "Corrección",
            "OTRO", "Otro");

    /** Los que pone el sistema: no se eligen, abren un periodo. */
    public static final Map<String, String> MOTIVOS_DE_APERTURA = ordenado(
            "INGRESO", "Ingreso",
            "CARGA_INICIAL", "Carga inicial",
            "REINGRESO", "Reingreso");

    /** Tabla 17 de SUNAT, más 99 «otro», que la tabla no tiene como cajón genérico. */
    public static final Map<String, String> MOTIVOS_CESE = ordenado(
            "01", "Renuncia",
            "02", "Renuncia con incentivos",
            "03", "Despido",
            "04", "Cese colectivo",
            "05", "Jubilación",
            "06", "Invalidez",
            "07", "Fin del contrato o de la obra",
            "08", "Mutuo disenso",
            "09", "Fallecimiento",
            "17", "No se inició la relación laboral",
            "99", "Otro");

    public static String textoDe(Map<String, String> catalogo, String codigo) {
        return codigo == null ? null : catalogo.getOrDefault(codigo, codigo);
    }

    public static String motivoDeLaSituacion(String codigo) {
        if (codigo == null) return null;
        String apertura = MOTIVOS_DE_APERTURA.get(codigo);
        return apertura != null ? apertura : MOTIVOS_CAMBIO.getOrDefault(codigo, codigo);
    }

    public static List<Opcion> opciones(Map<String, String> catalogo) {
        return catalogo.entrySet().stream().map(e -> new Opcion(e.getKey(), e.getValue())).toList();
    }

    private static Map<String, String> ordenado(String... pares) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put(pares[i], pares[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(mapa);
    }
}
