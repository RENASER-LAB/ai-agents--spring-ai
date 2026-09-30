package com.renaser.ai.ai_engine.colaborador.service;

import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.ErrorDeCarga;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La plantilla de la carga de colaboradores y su lectura.
 *
 * <p>Tres hojas: «Colaboradores», donde se escribe; «Instrucciones»; y «Valores», con lo que
 * vale en cada columna de lista en el momento de descargarla. Las columnas se reconocen por su
 * encabezado y no por su orden: quien reordena o borra columnas opcionales no rompe nada, y
 * un encabezado que no es de la plantilla es un error, no una columna que se ignora en
 * silencio.
 *
 * <p>Lee en memoria y no guarda el archivo en ningún sitio, como la importación del banco.
 */
public final class LibroDeColaboradores {

    private LibroDeColaboradores() {}

    public static final String HOJA = "Colaboradores";
    public static final String HOJA_INSTRUCCIONES = "Instrucciones";
    public static final String HOJA_VALORES = "Valores";
    public static final int MAXIMO_DE_FILAS = 5_000;
    public static final long MAXIMO_DE_BYTES = 10L * 1024 * 1024;

    /** Qué pide cada columna. Las de lista toman sus valores de la hoja «Valores». */
    public enum Columna {
        TIPO_DOCUMENTO("Tipo de documento", true, false, true),
        NUMERO_DOCUMENTO("Número de documento", true, false, false),
        NOMBRES("Nombres", true, false, false),
        APELLIDO_PATERNO("Apellido paterno", true, false, false),
        APELLIDO_MATERNO("Apellido materno", false, false, false),
        FECHA_NACIMIENTO("Fecha de nacimiento", true, false, false),
        SEXO("Sexo", true, false, true),
        ESTADO_CIVIL("Estado civil", false, false, true),
        NACIONALIDAD("Nacionalidad", false, false, false),
        CELULAR("Celular", false, false, false),
        CORREO_PERSONAL("Correo personal", false, false, false),
        CORREO_CORPORATIVO("Correo corporativo", false, false, false),
        DIRECCION("Dirección", false, false, false),
        PROVINCIA("Provincia", false, false, false),
        NIVEL_EDUCATIVO("Nivel educativo", false, false, true),
        FECHA_INGRESO("Fecha de ingreso", true, true, false),
        SEDE("Sede", true, true, true),
        AREA("Área", true, true, true),
        CARGO("Cargo", true, true, true),
        JEFE_TIPO("Tipo de documento del jefe", false, true, true),
        JEFE_NUMERO("Número de documento del jefe", false, true, false),
        TIPO_CONTRATO("Tipo de contrato", true, true, true),
        FIN_CONTRATO("Fin del contrato", false, true, false),
        FIN_PRUEBA("Fin del periodo de prueba", false, true, false),
        REGIMEN("Régimen laboral", true, true, true),
        SUELDO("Sueldo base", false, true, false),
        MONEDA("Moneda", false, true, true);

        public final String titulo;
        /** Obligatoria en un alta. En una actualización, una celda vacía no borra nada. */
        public final boolean obligatoria;
        /** De puesto y contrato: en una actualización, si trae otro valor, la fila da error. */
        public final boolean dePuesto;
        public final boolean deLista;

        Columna(String titulo, boolean obligatoria, boolean dePuesto, boolean deLista) {
            this.titulo = titulo;
            this.obligatoria = obligatoria;
            this.dePuesto = dePuesto;
            this.deLista = deLista;
        }

        public boolean esDeSueldo() {
            return this == SUELDO || this == MONEDA;
        }

        public String encabezado() {
            return obligatoria ? titulo + " *" : titulo;
        }

        public boolean esFecha() {
            return this == FECHA_NACIMIENTO || this == FECHA_INGRESO || this == FIN_CONTRATO
                    || this == FIN_PRUEBA;
        }
    }

    /** Las opciones válidas hoy, por columna de lista. */
    public record Valores(List<String> tiposDocumento, List<String> sexos, List<String> estadosCiviles,
                          List<String> nivelesEducativos, List<String> sedes, List<String> areas,
                          List<String> cargos, List<String> tiposContrato, List<String> regimenes,
                          List<String> monedas) {}

    /** Una celda leída: lo que se escribió, como texto, y la fecha si la celda era de fecha. */
    public record Celda(String texto, LocalDate fecha, Double numero) {
        public boolean vacia() {
            return texto == null || texto.isBlank();
        }
    }

    public record Fila(int numero, Map<Columna, Celda> celdas) {
        public Celda de(Columna columna) {
            return celdas.getOrDefault(columna, new Celda(null, null, null));
        }

        public boolean tiene(Columna columna) {
            return !de(columna).vacia();
        }
    }

    /** El archivo leído: sus columnas, sus filas no vacías, y lo que impidió leerlo. */
    public record Lectura(List<Columna> columnas, List<Fila> filas, List<ErrorDeCarga> errores) {}

    // ============ La plantilla ============

    public static byte[] plantilla(Valores valores, boolean conSueldo) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(HOJA);
            Sheet instrucciones = libro.createSheet(HOJA_INSTRUCCIONES);
            Sheet hojaValores = libro.createSheet(HOJA_VALORES);

            CellStyle cabecera = libro.createCellStyle();
            Font negrita = libro.createFont();
            negrita.setBold(true);
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setBorderBottom(BorderStyle.THIN);
            CellStyle texto = libro.createCellStyle();
            texto.setDataFormat(libro.createDataFormat().getFormat("@"));
            CellStyle fecha = libro.createCellStyle();
            fecha.setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy"));

            List<Columna> columnas = columnasDeLaPlantilla(conSueldo);
            Row fila = hoja.createRow(0);
            for (int i = 0; i < columnas.size(); i++) {
                Columna columna = columnas.get(i);
                Cell celda = fila.createCell(i);
                celda.setCellValue(columna.encabezado());
                celda.setCellStyle(cabecera);
                hoja.setColumnWidth(i, Math.max(14, columna.encabezado().length() + 4) * 256);
                // Los números de documento y el celular, como texto: un DNI que empieza por
                // cero perdería su primera cifra si Excel lo tomara por número.
                if (columna == Columna.NUMERO_DOCUMENTO || columna == Columna.JEFE_NUMERO
                        || columna == Columna.CELULAR) {
                    hoja.setDefaultColumnStyle(i, texto);
                } else if (columna.esFecha()) {
                    hoja.setDefaultColumnStyle(i, fecha);
                }
            }
            hoja.createFreezePane(0, 1);

            Map<Columna, List<String>> listas = listas(valores);
            Row titulos = hojaValores.createRow(0);
            int columnaDeValores = 0;
            DataValidationHelper ayuda = hoja.getDataValidationHelper();
            Map<String, String> rangoPorLista = new LinkedHashMap<>();
            for (Map.Entry<Columna, List<String>> lista : listas.entrySet()) {
                if (lista.getKey().esDeSueldo() && !conSueldo) continue;
                String titulo = lista.getKey() == Columna.JEFE_TIPO ? "Tipo de documento del jefe"
                        : lista.getKey().titulo;
                if (lista.getKey() == Columna.JEFE_TIPO) {
                    // Comparte la lista del tipo de documento: no se repite en «Valores».
                    continue;
                }
                Cell celdaTitulo = titulos.createCell(columnaDeValores);
                celdaTitulo.setCellValue(titulo);
                celdaTitulo.setCellStyle(cabecera);
                List<String> opciones = lista.getValue();
                for (int i = 0; i < opciones.size(); i++) {
                    Row r = hojaValores.getRow(i + 1) == null ? hojaValores.createRow(i + 1) : hojaValores.getRow(i + 1);
                    r.createCell(columnaDeValores).setCellValue(opciones.get(i));
                }
                hojaValores.setColumnWidth(columnaDeValores, 34 * 256);
                String letra = letraDeColumna(columnaDeValores);
                rangoPorLista.put(lista.getKey().name(), "'" + HOJA_VALORES + "'!$" + letra + "$2:$" + letra + "$"
                        + Math.max(2, opciones.size() + 1));
                columnaDeValores++;
            }
            rangoPorLista.put(Columna.JEFE_TIPO.name(), rangoPorLista.get(Columna.TIPO_DOCUMENTO.name()));

            for (int i = 0; i < columnas.size(); i++) {
                Columna columna = columnas.get(i);
                String rango = rangoPorLista.get(columna.name());
                if (!columna.deLista || rango == null) continue;
                DataValidationConstraint restriccion = ayuda.createFormulaListConstraint(rango);
                DataValidation validacion = ayuda.createValidation(restriccion,
                        new CellRangeAddressList(1, MAXIMO_DE_FILAS, i, i));
                validacion.setShowErrorBox(true);
                validacion.setSuppressDropDownArrow(true);
                hoja.addValidationData(validacion);
            }

            escribirInstrucciones(instrucciones, conSueldo);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la plantilla", e);
        }
    }

    public static List<Columna> columnasDeLaPlantilla(boolean conSueldo) {
        return java.util.Arrays.stream(Columna.values()).filter(c -> conSueldo || !c.esDeSueldo()).toList();
    }

    private static Map<Columna, List<String>> listas(Valores v) {
        Map<Columna, List<String>> listas = new LinkedHashMap<>();
        listas.put(Columna.TIPO_DOCUMENTO, v.tiposDocumento());
        listas.put(Columna.SEXO, v.sexos());
        listas.put(Columna.ESTADO_CIVIL, v.estadosCiviles());
        listas.put(Columna.NIVEL_EDUCATIVO, v.nivelesEducativos());
        listas.put(Columna.SEDE, v.sedes());
        listas.put(Columna.AREA, v.areas());
        listas.put(Columna.CARGO, v.cargos());
        listas.put(Columna.JEFE_TIPO, v.tiposDocumento());
        listas.put(Columna.TIPO_CONTRATO, v.tiposContrato());
        listas.put(Columna.REGIMEN, v.regimenes());
        listas.put(Columna.MONEDA, v.monedas());
        return listas;
    }

    private static void escribirInstrucciones(Sheet hoja, boolean conSueldo) {
        List<String> lineas = new ArrayList<>(List.of(
                "Cómo llenar la hoja «Colaboradores»",
                "",
                "· Una fila por persona. Cada fila se identifica por el tipo y el número de documento.",
                "· Si el documento no existe en la empresa, la fila es un alta. Las columnas con * son obligatorias.",
                "· Si ya existe y está activo o por ingresar, se actualizan sus datos personales, de contacto, "
                        + "domicilio y formación. Una celda vacía no borra nada.",
                "· Los cambios de puesto y contrato no se hacen aquí: se registran en su ficha. Si una fila "
                        + "trae otro cargo, otra sede u otro contrato para alguien que ya existe, esa fila da error.",
                "· Si la persona está cesada, la fila da error: se reingresa desde su ficha.",
                "· Sede, área y cargo se escriben con su nombre, tal como salen en la hoja «Valores».",
                "· El jefe directo se indica con su tipo y número de documento. Puede ser otra fila de este "
                        + "mismo archivo o alguien que ya es colaborador.",
                "· Fechas en formato dd/mm/aaaa.",
                "· Un contrato a plazo o de temporada necesita la fecha de fin; uno a plazo indeterminado no la lleva.",
                "· El DNI tiene 8 dígitos; los demás documentos, de 4 a 15 letras o dígitos.",
                "· Hasta 5.000 filas y 10 MB. Las filas vacías se ignoran.",
                "",
                "Todo o nada: si una sola fila tiene un error, no se guarda ninguna, y la pantalla enseña "
                        + "todos los errores con su fila y su columna."));
        if (conSueldo) {
            lineas.add("");
            lineas.add("· El sueldo base va en número, sin símbolo de moneda. Si no se indica la moneda, son soles.");
        }
        for (int i = 0; i < lineas.size(); i++) {
            hoja.createRow(i).createCell(0).setCellValue(lineas.get(i));
        }
        hoja.setColumnWidth(0, 120 * 256);
    }

    private static String letraDeColumna(int indice) {
        StringBuilder letras = new StringBuilder();
        int n = indice + 1;
        while (n > 0) {
            int resto = (n - 1) % 26;
            letras.insert(0, (char) ('A' + resto));
            n = (n - 1) / 26;
        }
        return letras.toString();
    }

    // ============ La lectura ============

    public static Lectura leer(byte[] contenido, String nombreDelArchivo) {
        List<ErrorDeCarga> errores = new ArrayList<>();
        String nombre = nombreDelArchivo == null ? "" : nombreDelArchivo.toLowerCase(Locale.ROOT);
        if (!nombre.endsWith(".xlsx")) {
            errores.add(new ErrorDeCarga(0, "(archivo)", nombreDelArchivo,
                    "El archivo tiene que ser un Excel .xlsx: descarga la plantilla y guárdala en ese formato"));
            return new Lectura(List.of(), List.of(), errores);
        }
        if (contenido.length > MAXIMO_DE_BYTES) {
            errores.add(new ErrorDeCarga(0, "(archivo)", nombreDelArchivo, "El archivo pesa más de 10 MB"));
            return new Lectura(List.of(), List.of(), errores);
        }
        XSSFWorkbook libro;
        try {
            libro = new XSSFWorkbook(new ByteArrayInputStream(contenido));
        } catch (Exception e) {
            errores.add(new ErrorDeCarga(0, "(archivo)", nombreDelArchivo,
                    "No se pudo leer como .xlsx: ¿es la plantilla guardada desde Excel o LibreOffice?"));
            return new Lectura(List.of(), List.of(), errores);
        }
        try (libro) {
            Sheet hoja = libro.getSheet(HOJA);
            if (hoja == null) {
                errores.add(new ErrorDeCarga(0, "(archivo)", nombreDelArchivo,
                        "Falta la hoja «" + HOJA + "»: usa la plantilla"));
                return new Lectura(List.of(), List.of(), errores);
            }
            return leerHoja(hoja, errores);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cerrar el libro leído", e);
        }
    }

    private static Lectura leerHoja(Sheet hoja, List<ErrorDeCarga> errores) {
        DataFormatter formato = new DataFormatter(Locale.forLanguageTag("es-PE"));
        Row cabecera = hoja.getRow(hoja.getFirstRowNum());
        if (cabecera == null) {
            errores.add(new ErrorDeCarga(1, "(encabezados)", "", "La hoja está vacía: falta la fila de encabezados"));
            return new Lectura(List.of(), List.of(), errores);
        }
        Map<Integer, Columna> porIndice = new LinkedHashMap<>();
        Map<Columna, Integer> vistas = new EnumMap<>(Columna.class);
        for (Cell celda : cabecera) {
            String titulo = formato.formatCellValue(celda);
            if (titulo == null || titulo.isBlank()) continue;
            Columna columna = columnaDe(titulo);
            if (columna == null) {
                errores.add(new ErrorDeCarga(cabecera.getRowNum() + 1, titulo.strip(), titulo.strip(),
                        "Encabezado desconocido: no es una columna de la plantilla"));
                continue;
            }
            if (vistas.containsKey(columna)) {
                errores.add(new ErrorDeCarga(cabecera.getRowNum() + 1, columna.titulo, titulo.strip(),
                        "La columna está repetida"));
                continue;
            }
            vistas.put(columna, celda.getColumnIndex());
            porIndice.put(celda.getColumnIndex(), columna);
        }
        for (Columna imprescindible : List.of(Columna.TIPO_DOCUMENTO, Columna.NUMERO_DOCUMENTO)) {
            if (!vistas.containsKey(imprescindible)) {
                errores.add(new ErrorDeCarga(cabecera.getRowNum() + 1, imprescindible.titulo, "",
                        "Falta la columna «" + imprescindible.titulo + "»: cada fila se identifica por ella"));
            }
        }

        List<Fila> filas = new ArrayList<>();
        for (int r = cabecera.getRowNum() + 1; r <= hoja.getLastRowNum(); r++) {
            Row fila = hoja.getRow(r);
            if (fila == null) continue;
            Map<Columna, Celda> celdas = new EnumMap<>(Columna.class);
            boolean vacia = true;
            for (Map.Entry<Integer, Columna> entrada : porIndice.entrySet()) {
                Celda celda = leerCelda(fila.getCell(entrada.getKey()), formato);
                if (!celda.vacia()) vacia = false;
                celdas.put(entrada.getValue(), celda);
            }
            if (vacia) continue;
            filas.add(new Fila(r + 1, celdas));
            if (filas.size() > MAXIMO_DE_FILAS) {
                errores.add(new ErrorDeCarga(r + 1, "(archivo)", "",
                        "El archivo tiene más de 5.000 filas: pártelo en varios"));
                return new Lectura(List.copyOf(vistas.keySet()), List.of(), errores);
            }
        }
        return new Lectura(List.copyOf(vistas.keySet()), filas, errores);
    }

    private static Celda leerCelda(Cell celda, DataFormatter formato) {
        if (celda == null) {
            return new Celda(null, null, null);
        }
        CellType tipo = celda.getCellType() == CellType.FORMULA ? celda.getCachedFormulaResultType()
                : celda.getCellType();
        if (tipo == CellType.NUMERIC) {
            double numero = celda.getNumericCellValue();
            LocalDate fecha = DateUtil.isCellDateFormatted(celda)
                    ? celda.getLocalDateTimeCellValue().toLocalDate() : null;
            String texto = fecha != null ? fecha.toString()
                    : (numero == Math.rint(numero) && Math.abs(numero) < 1e15
                            ? String.valueOf((long) numero) : String.valueOf(numero));
            return new Celda(texto, fecha, numero);
        }
        String texto = formato.formatCellValue(celda);
        return new Celda(texto == null ? null : texto.strip(), null, null);
    }

    /** El encabezado sin asterisco, sin tildes y sin mayúsculas, contra los de la plantilla. */
    public static Columna columnaDe(String encabezado) {
        String buscado = normalizar(encabezado.replace("*", ""));
        for (Columna columna : Columna.values()) {
            if (normalizar(columna.titulo).equals(buscado)) {
                return columna;
            }
        }
        return null;
    }

    public static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
