package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.BancoLeido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.ErrorDeImportacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaPregunta;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee el Excel del banco CAZATALENTOS y lo convierte en filas planas.
 *
 * <p>Es el hermano de {@link LectorPlantillaBanco} para el otro formato de archivo: el que
 * mandó la clienta (docs/insumos/CAZATALENTOS-*.xlsx), cuya hoja «Prueba RENASER» trae una
 * pregunta abierta por fila con su pilar, su C3 esperado, su C4 esperado, su señal de 0, su
 * peso y su marca de eliminatoria. No hay opciones, campos, rangos ni pares: un banco leído
 * aquí solo llena {@code preguntas} y {@code errores}.
 *
 * <p>Mismas reglas que el otro lector: sin estado y sin base de datos, todos los errores se
 * juntan con su hoja y su fila, y si hay uno solo, lo leído no se usa.
 */
@Component
public class LectorBancoCazatalentos {

    /** La hoja que delata el formato. Si el libro la trae, el archivo es de este lector. */
    public static final String HOJA = "Prueba RENASER";

    /**
     * El pilar (columna B: «1 Iniciativa» … «7 Integridad») → el código de dimensión con
     * que se agrega y pondera. Se resuelve por el número, que es lo estable: la clienta
     * reescribe nombres, no la numeración.
     */
    private static final Map<String, String> PILARES = Map.of(
            "1", "PIL_INICIATIVA",
            "2", "PIL_RESOLUCION",
            "3", "PIL_EXCELENCIA",
            "4", "PIL_SERVICIO",
            "5", "PIL_RESPONSABILIDAD",
            "6", "PIL_DIRECCION",
            "7", "PIL_INTEGRIDAD");

    /**
     * La regla dura de R11 viene como prosa en la nota interna («REGLA DURA: sin ninguna
     * cifra, el máximo de esta pregunta es 2»). Se convierte en un marcador legible por el
     * motor, delante de la nota: la prosa se queda, el número deja de estar solo en prosa.
     */
    private static final Pattern REGLA_DURA =
            Pattern.compile("REGLA DURA.*m[áa]ximo[^0-9]*([0-9])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    public static final String MARCA_TOPE = "[TOPE_SIN_DATO=";

    /** ¿Este archivo es un banco CAZATALENTOS? Mira las hojas, no el contenido. */
    public boolean esSuyo(byte[] archivo) {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            return libro.getSheet(HOJA) != null;
        } catch (Exception e) {
            return false;       // si ni se puede abrir, que lo rechace el lector que toque
        }
    }

    public BancoLeido leer(InputStream archivo) {
        List<ErrorDeImportacion> errores = new ArrayList<>();
        List<FilaPregunta> preguntas = new ArrayList<>();
        try (XSSFWorkbook libro = new XSSFWorkbook(archivo)) {
            Sheet hoja = libro.getSheet(HOJA);
            if (hoja == null) {
                errores.add(new ErrorDeImportacion(HOJA, 0,
                        "el libro no tiene la hoja «" + HOJA + "»"));
                return new BancoLeido(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.copyOf(errores));
            }
            leerHoja(hoja, preguntas, errores);
        } catch (Exception e) {
            errores.add(new ErrorDeImportacion(HOJA, 0,
                    "no se pudo leer el archivo: " + e.getMessage()));
        }
        if (!errores.isEmpty()) {
            preguntas = List.of();
        }
        return new BancoLeido(List.copyOf(preguntas), List.of(), List.of(), List.of(),
                List.of(), List.copyOf(errores));
    }

    private void leerHoja(Sheet hoja, List<FilaPregunta> preguntas,
                          List<ErrorDeImportacion> errores) {
        // El encabezado no se asume en una fila fija: se busca por su ancla («Código» en la
        // columna A) y la fila de guía que le sigue se salta siempre. Igual que el otro lector.
        int filaEncabezado = -1;
        for (Row fila : hoja) {
            if ("codigo".equals(LectorPlantillaBanco.normalizar(textoDe(celda(fila, 0))))) {
                filaEncabezado = fila.getRowNum();
                break;
            }
        }
        if (filaEncabezado < 0) {
            errores.add(new ErrorDeImportacion(HOJA, 0,
                    "no se encontró la fila de encabezados (la que empieza con «Código»)"));
            return;
        }

        Map<String, Integer> filaPorCodigo = new LinkedHashMap<>();
        for (int i = filaEncabezado + 2; i <= hoja.getLastRowNum(); i++) {
            Row fila = hoja.getRow(i);
            if (filaVacia(fila)) {
                continue;
            }
            FilaPregunta leida = leerPregunta(fila, filaPorCodigo, errores);
            if (leida != null) {
                preguntas.add(leida);
            }
        }
        if (preguntas.isEmpty() && errores.isEmpty()) {
            errores.add(new ErrorDeImportacion(HOJA, filaEncabezado + 1,
                    "la hoja no tiene ninguna pregunta debajo del encabezado"));
        }
    }

    private FilaPregunta leerPregunta(Row fila, Map<String, Integer> filaPorCodigo,
                                      List<ErrorDeImportacion> errores) {
        int n = fila.getRowNum() + 1;
        String codigo = textoDe(celda(fila, 0));
        String pilar = textoDe(celda(fila, 1));
        String enunciado = textoDe(celda(fila, 2));
        String c3 = textoDe(celda(fila, 3));
        String c4 = textoDe(celda(fila, 4));
        String senal = textoDe(celda(fila, 5));
        String pesoCrudo = textoDe(celda(fila, 6));
        String eliminatoriaCruda = textoDe(celda(fila, 7));
        String notaInterna = textoDe(celda(fila, 8));

        boolean bien = true;
        if (codigo == null) {
            errores.add(new ErrorDeImportacion(HOJA, n, "falta el código (columna A)"));
            return null;
        }
        Integer filaAnterior = filaPorCodigo.putIfAbsent(codigo, n);
        if (filaAnterior != null) {
            errores.add(new ErrorDeImportacion(HOJA, n, "el código " + codigo
                    + " ya apareció en la fila " + filaAnterior + ": cada pregunta lleva uno único"));
            bien = false;
        }
        if (enunciado == null) {
            errores.add(new ErrorDeImportacion(HOJA, n, "falta la pregunta (columna C)"));
            bien = false;
        }

        Short peso = null;
        if (pesoCrudo == null || !pesoCrudo.matches("[012]")) {
            errores.add(new ErrorDeImportacion(HOJA, n,
                    "el peso debe ser 0, 1 o 2 y es «" + (pesoCrudo == null ? "" : pesoCrudo) + "»"));
            bien = false;
        } else {
            peso = Short.parseShort(pesoCrudo);
        }

        Boolean eliminatoria = switch (eliminatoriaCruda == null ? ""
                : eliminatoriaCruda.toLowerCase()) {
            case "sí", "si" -> true;
            case "no" -> false;
            default -> null;
        };
        if (eliminatoria == null) {
            errores.add(new ErrorDeImportacion(HOJA, n,
                    "¿Eliminatoria? debe ser «sí» o «no» y es «"
                            + (eliminatoriaCruda == null ? "" : eliminatoriaCruda) + "»"));
            bien = false;
        }

        boolean puntua = peso != null && peso > 0;
        String dimension = null;
        if (puntua) {
            // El método entero vive en estas tres columnas: sin ellas el evaluador
            // calificaría a ojo, que es justo lo que el instrumento prohíbe.
            if (c3 == null) {
                errores.add(new ErrorDeImportacion(HOJA, n,
                        codigo + " puntúa pero no declara su C3 · dato duro esperado (columna D)"));
                bien = false;
            }
            if (c4 == null) {
                errores.add(new ErrorDeImportacion(HOJA, n,
                        codigo + " puntúa pero no declara su C4 · incomodidad esperada (columna E)"));
                bien = false;
            }
            if (senal == null) {
                errores.add(new ErrorDeImportacion(HOJA, n,
                        codigo + " puntúa pero no declara su SEÑAL DE 0 (columna F)"));
                bien = false;
            }
            dimension = pilarADimension(pilar);
            if (dimension == null) {
                errores.add(new ErrorDeImportacion(HOJA, n, "el pilar «"
                        + (pilar == null ? "" : pilar) + "» no es ninguno de los 7 (1 Iniciativa … 7 Integridad)"));
                bien = false;
            }
        }
        if (!bien) {
            return null;
        }
        return new FilaPregunta(n, codigo, "ABIERTA", enunciado, null,
                peso, eliminatoria, dimension == null ? List.of() : List.of(dimension),
                null, null, null, conMarcaDeTope(notaInterna),
                c3, c4, senal);
    }

    /** «5 Responsabilidad y resultados» → PIL_RESPONSABILIDAD. El número manda. */
    private static String pilarADimension(String pilar) {
        if (pilar == null || pilar.isBlank()) {
            return null;
        }
        return PILARES.get(pilar.strip().split("\\s+")[0]);
    }

    /** Si la nota interna trae la regla dura, se le antepone el marcador con su tope. */
    static String conMarcaDeTope(String notaInterna) {
        if (notaInterna == null) {
            return null;
        }
        Matcher m = REGLA_DURA.matcher(notaInterna);
        if (!m.find()) {
            return notaInterna;
        }
        return MARCA_TOPE + m.group(1) + "] " + notaInterna;
    }

    /** El tope que la pregunta declara en su lógica interna, o null si no declara ninguno. */
    public static Integer topeSinDato(String logicaInterna) {
        if (logicaInterna == null || !logicaInterna.startsWith(MARCA_TOPE)) {
            return null;
        }
        // Esto corre en plena calificación: un marcador roto a mano (sin el cierre, o con
        // basura dentro) no puede tumbar la tanda entera — vale como «sin tope».
        int cierre = logicaInterna.indexOf(']');
        if (cierre < 0) {
            return null;
        }
        try {
            return Integer.parseInt(logicaInterna.substring(MARCA_TOPE.length(), cierre));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ============ Celdas (mismo comportamiento que LectorPlantillaBanco) ============

    private static Cell celda(Row fila, int columna) {
        return fila == null ? null : fila.getCell(columna);
    }

    private static boolean filaVacia(Row fila) {
        for (int i = 0; i < 9; i++) {
            if (textoDe(celda(fila, i)) != null) {
                return false;
            }
        }
        return true;
    }

    private static String textoDe(Cell celda) {
        if (celda == null) {
            return null;
        }
        CellType tipo = celda.getCellType() == CellType.FORMULA
                ? celda.getCachedFormulaResultType() : celda.getCellType();
        String texto = switch (tipo) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(celda.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
            case BOOLEAN -> celda.getBooleanCellValue() ? "sí" : "no";
            default -> null;
        };
        if (texto == null) {
            return null;
        }
        texto = texto.strip();
        return texto.isEmpty() ? null : texto;
    }
}
