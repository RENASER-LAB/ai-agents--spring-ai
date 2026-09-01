package com.renaser.ai.ai_engine.perfilintegral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Lo que se pide y lo que se lleva quien quiere el ranking en una hoja de cálculo.
 *
 * <p>Vive aparte de {@code DtosPerfilIntegral} a propósito: aquello es el contrato de las
 * pantallas del panel, y esto es el de un archivo que se manda por correo y se abre en un
 * Excel. Mezclarlos haría que cualquier cambio del volcado tocara el contrato de la pantalla.
 */
public final class DtosExcelRanking {

    private DtosExcelRanking() {}

    /**
     * Qué volcar: la etapa cuya nota manda, y <b>los candidatos ya ordenados</b>.
     *
     * <p>El orden de {@code postulacionIds} es el del pedido y es lo único que decide en qué
     * fila cae cada uno. Quien filtra y ordena es el cliente —esa decisión ya está tomada—,
     * así que aquí no se vuelve a ordenar por nada: se copia el orden recibido tal cual.
     *
     * <p>{@code filtroDescrito} es la frase que el cliente pintó encima de su tabla («Ciudad:
     * Lima · Nota ≥ 60»). Viaja para que la hoja diga a qué tanda corresponde: un Excel con
     * cuarenta filas y sin decir de qué cuarenta se lee como si fueran todas.
     */
    public record PedidoExcelRanking(
            @NotBlank String etapa,
            @NotEmpty List<Long> postulacionIds,
            String filtroDescrito) {}

    /**
     * El archivo y cómo se llama. El nombre lleva la fecha, porque estas hojas se guardan.
     *
     * <p>Los tres métodos van escritos a mano y no son ceremonia: un {@code record} con un
     * array dentro compara la REFERENCIA del array, así que dos volcados idénticos saldrían
     * distintos y el mismo volcado guardado dos veces saldría igual solo por casualidad.
     * Nadie los compara hoy; la trampa se desarma antes de que alguien lo haga.
     *
     * <p>{@code toString} dice el nombre y cuánto pesa, y <b>nunca el contenido</b>: son
     * decenas de miles de bytes binarios, y un registro que los vuelque no se puede leer.
     */
    public record ExcelDeRanking(String nombreArchivo, byte[] contenido) {

        @Override
        public boolean equals(Object otro) {
            return otro instanceof ExcelDeRanking o
                    && Objects.equals(nombreArchivo, o.nombreArchivo)
                    && Arrays.equals(contenido, o.contenido);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(nombreArchivo) + Arrays.hashCode(contenido);
        }

        @Override
        public String toString() {
            return "ExcelDeRanking[nombreArchivo=" + nombreArchivo
                    + ", contenido=" + (contenido == null ? "ausente" : contenido.length + " bytes")
                    + "]";
        }
    }
}
