package com.renaser.ai.ai_engine.colaborador.service;

import com.renaser.ai.ai_engine.colaborador.service.LibroDeColaboradores.Columna;
import com.renaser.ai.ai_engine.colaborador.service.LibroDeColaboradores.Lectura;
import com.renaser.ai.ai_engine.colaborador.service.LibroDeColaboradores.Valores;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("La plantilla de la carga de colaboradores y su lectura")
class LibroDeColaboradoresTest {

    private static final Valores VALORES = new Valores(List.of("DNI", "Pasaporte"), List.of("Masculino", "Femenino"),
            List.of("Soltero(a)"), List.of("Universitaria"), List.of("Lima"), List.of("Finanzas"),
            List.of("Analista"), List.of("A plazo indeterminado"), List.of("General"), List.of("Soles (S/)"));

    @Test
    @DisplayName("tres hojas, los obligatorios marcados, y la columna de sueldo solo con ver_sueldos")
    void laPlantilla() throws IOException {
        try (XSSFWorkbook sin = new XSSFWorkbook(new ByteArrayInputStream(LibroDeColaboradores.plantilla(VALORES, false)));
             XSSFWorkbook con = new XSSFWorkbook(new ByteArrayInputStream(LibroDeColaboradores.plantilla(VALORES, true)))) {
            assertThat(sin.getNumberOfSheets()).isEqualTo(3);
            assertThat(sin.getSheetName(0)).isEqualTo("Colaboradores");
            assertThat(sin.getSheetName(1)).isEqualTo("Instrucciones");
            assertThat(sin.getSheetName(2)).isEqualTo("Valores");
            Row cabecera = sin.getSheet("Colaboradores").getRow(0);
            List<String> titulos = new java.util.ArrayList<>();
            cabecera.forEach(c -> titulos.add(c.getStringCellValue()));
            assertThat(titulos).contains("Tipo de documento *", "Apellido materno", "Sede *");
            assertThat(titulos).noneMatch(t -> t.startsWith("Sueldo") || t.startsWith("Moneda"));
            assertThat(sin.getSheet("Colaboradores").getDataValidations()).isNotEmpty();

            List<String> conSueldo = new java.util.ArrayList<>();
            con.getSheet("Colaboradores").getRow(0).forEach(c -> conSueldo.add(c.getStringCellValue()));
            assertThat(conSueldo).contains("Sueldo base", "Moneda");
        }
    }

    @Test
    @DisplayName("las columnas se reconocen por su encabezado y no por su orden; las filas vacías se ignoran")
    void porEncabezado() throws IOException {
        byte[] archivo = libro(List.of("Número de documento *", "TIPO DE DOCUMENTO", "Nombres"),
                List.of("45123456", "DNI", "Ana"), List.of("", "", ""), List.of("40111222", "DNI", "Luis"));
        Lectura lectura = LibroDeColaboradores.leer(archivo, "carga.xlsx");
        assertThat(lectura.errores()).isEmpty();
        assertThat(lectura.filas()).hasSize(2);
        assertThat(lectura.filas().get(0).de(Columna.NUMERO_DOCUMENTO).texto()).isEqualTo("45123456");
        assertThat(lectura.filas().get(1).numero()).isEqualTo(4);
    }

    @Test
    @DisplayName("un encabezado desconocido es un error con su columna")
    void encabezadoDesconocido() throws IOException {
        byte[] archivo = libro(List.of("Tipo de documento", "Número de documento", "Talla de polo"),
                List.of("DNI", "45123456", "M"));
        Lectura lectura = LibroDeColaboradores.leer(archivo, "carga.xlsx");
        assertThat(lectura.errores()).singleElement()
                .satisfies(e -> {
                    assertThat(e.fila()).isEqualTo(1);
                    assertThat(e.columna()).isEqualTo("Talla de polo");
                    assertThat(e.mensaje()).contains("Encabezado desconocido");
                });
    }

    @Test
    @DisplayName("otro formato, o un libro sin la hoja «Colaboradores», se rechaza con un mensaje claro")
    void formatoYHoja() throws IOException {
        assertThat(LibroDeColaboradores.leer(new byte[] {1, 2, 3}, "carga.csv").errores())
                .singleElement().satisfies(e -> assertThat(e.mensaje()).contains(".xlsx"));
        assertThat(LibroDeColaboradores.leer(new byte[] {1, 2, 3}, "carga.xlsx").errores())
                .singleElement().satisfies(e -> assertThat(e.mensaje()).contains("No se pudo leer"));
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.createSheet("Hoja1");
            libro.write(salida);
            assertThat(LibroDeColaboradores.leer(salida.toByteArray(), "carga.xlsx").errores())
                    .singleElement().satisfies(e -> assertThat(e.mensaje()).contains("Falta la hoja"));
        }
    }

    @Test
    @DisplayName("más de 5.000 filas se rechaza sin leer el resto")
    void demasiadasFilas() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Colaboradores");
            Row cabecera = hoja.createRow(0);
            cabecera.createCell(0).setCellValue("Tipo de documento");
            cabecera.createCell(1).setCellValue("Número de documento");
            for (int i = 1; i <= 5_001; i++) {
                Row fila = hoja.createRow(i);
                fila.createCell(0).setCellValue("DNI");
                fila.createCell(1).setCellValue(String.valueOf(10_000_000 + i));
            }
            libro.write(salida);
            Lectura lectura = LibroDeColaboradores.leer(salida.toByteArray(), "grande.xlsx");
            assertThat(lectura.errores()).singleElement().satisfies(e -> assertThat(e.mensaje()).contains("5.000"));
            assertThat(lectura.filas()).isEmpty();
        }
    }

    @SafeVarargs
    private static byte[] libro(List<String> cabecera, List<String>... filas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Colaboradores");
            Row r = hoja.createRow(0);
            for (int i = 0; i < cabecera.size(); i++) r.createCell(i).setCellValue(cabecera.get(i));
            for (int f = 0; f < filas.length; f++) {
                Row fila = hoja.createRow(f + 1);
                for (int i = 0; i < filas[f].size(); i++) fila.createCell(i).setCellValue(filas[f].get(i));
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
