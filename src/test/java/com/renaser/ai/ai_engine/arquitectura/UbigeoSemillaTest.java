package com.renaser.ai.ai_engine.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que la semilla de ubigeo de la V46 siga siendo el mapa del Perú y no una lista cualquiera.
 *
 * <p><b>Por qué hace falta una prueba para 222 filas escritas a mano.</b> Nadie revisa 196
 * provincias leyéndolas. Cuando alguien añada, corrija o mueva una —y va a pasar: los
 * códigos del INEI cambian cuando se crea una provincia nueva— lo hará mirando su línea, no
 * las 195 de al lado. El error típico no es un nombre mal escrito, que se ve; es un código
 * que se salta un número, o que cuelga del departamento equivocado. Ninguna de las dos
 * cosas rompe nada al arrancar: la fila entra, la clave ajena la acepta, y el desplegable
 * enseña Camaná dentro de Áncash sin que se caiga nada.
 *
 * <p>La invariante que lo caza es la que hace que la semilla se pueda revisar contando 25
 * cifras en vez de leyendo 196 filas: <b>dentro de cada departamento, las dos últimas cifras
 * corren de 01 hacia arriba sin huecos.</b> Un hueco, un repetido o un padre que no cuadra
 * con el prefijo del código fallan aquí.
 *
 * <p>Lee el archivo, no la base. La del puerto 5433 la comparten nueve worktrees y arrancar
 * Flyway para contar filas es justo lo que puede estropearle la tarde a otro; además la
 * pregunta es sobre lo que dice la migración, que es lo que se despliega.
 */
@DisplayName("La semilla de ubigeo sigue siendo el mapa del Perú")
class UbigeoSemillaTest {

    private static final Path V46 = Path.of(
            "src/main/resources/db/migration/V46__la_ciudad_del_candidato.sql");

    /** Fuera del Perú: nivel 1 y sin padre, pero no es un departamento. */
    private static final String EXTRANJERO = "EXT";

    private static final int DEPARTAMENTOS = 25;
    private static final int PROVINCIAS = 196;

    /**
     * Una tupla del insert: {@code ('0402', 2, '04', 'Camaná')}.
     *
     * <p>El nombre es {@code [^']*} y no {@code \w+} porque ahí dentro hay tildes, espacios
     * y puntos —{@code 'Prov. Const. del Callao'}, {@code 'Páucar del Sara Sara'}—. El padre
     * admite {@code null} literal, que es lo que llevan los departamentos.
     */
    private static final Pattern FILA = Pattern.compile(
            "\\('([^']*)',\\s*(\\d+),\\s*(?:null|'([^']*)'),\\s*'([^']*)'\\)");

    private record Lugar(String codigo, int nivel, String padre, String nombre) {}

    // ========================================================================
    // Los tres recuentos
    // ========================================================================

    @Test
    @DisplayName("Están los 25 departamentos, ni uno más, y EXT aparte")
    void los25DepartamentosMasElExtranjero() {
        List<Lugar> semilla = leerla();

        List<String> departamentos = semilla.stream()
                .filter(l -> l.nivel() == 1 && !EXTRANJERO.equals(l.codigo()))
                .map(Lugar::codigo)
                .sorted()
                .toList();

        assertThat(departamentos)
                .as("El Perú tiene 25 departamentos. Si esta cuenta cambia es que se añadió "
                        + "una fila de nivel 1 que no lo es, o que se perdió una")
                .hasSize(DEPARTAMENTOS);
        // Del 01 al 25 y sin saltos: es lo que hace que el código de una provincia se pueda
        // leer de un vistazo —las dos primeras cifras son su departamento— sin consultar nada.
        assertThat(departamentos)
                .containsExactlyElementsOf(numerosSeguidos(DEPARTAMENTOS, 2));

        Lugar extranjero = semilla.stream()
                .filter(l -> EXTRANJERO.equals(l.codigo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Falta la fila EXT. Sin ella, quien vive fuera del Perú no puede "
                                + "crear cuenta: la ciudad es obligatoria y no tendría cuál elegir"));
        assertThat(extranjero.nivel()).isEqualTo(1);
        assertThat(extranjero.padre())
                .as("EXT no cuelga de ningún departamento: es la opción de escape")
                .isNull();
    }

    @Test
    @DisplayName("Están las 196 provincias, y ninguna repetida")
    void las196Provincias() {
        List<Lugar> semilla = leerla();

        List<String> provincias = semilla.stream()
                .filter(l -> l.nivel() == 2)
                .map(Lugar::codigo)
                .toList();

        assertThat(provincias)
                .as("El Perú tiene 196 provincias. Añadir una de verdad exige cambiar este "
                        + "número a mano, y ese es el punto: obliga a mirar si es cierto")
                .hasSize(PROVINCIAS);
        // Un código repetido no lo dejaría entrar la clave primaria, pero fallaría al
        // desplegar y no aquí, con la migración a medio aplicar.
        assertThat(provincias).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Ningún código aparece dos veces en toda la semilla")
    void ningunCodigoRepetido() {
        assertThat(leerla().stream().map(Lugar::codigo).toList())
                .as("La clave primaria lo rechazaría, pero en mitad del despliegue")
                .doesNotHaveDuplicates();
    }

    // ========================================================================
    // La invariante que de verdad caza los errores
    // ========================================================================

    @Test
    @DisplayName("Cada provincia cuelga del departamento que dice su código")
    void elPadreCuadraConElPrefijoDelCodigo() {
        List<Lugar> semilla = leerla();
        List<String> departamentos = semilla.stream()
                .filter(l -> l.nivel() == 1 && !EXTRANJERO.equals(l.codigo()))
                .map(Lugar::codigo)
                .toList();

        List<String> torcidas = new ArrayList<>();
        for (Lugar provincia : semilla.stream().filter(l -> l.nivel() == 2).toList()) {
            String codigo = provincia.codigo();
            if (codigo.length() != 4) {
                torcidas.add(codigo + " (" + provincia.nombre() + ") no tiene 4 cifras");
                continue;
            }
            String prefijo = codigo.substring(0, 2);
            // Las dos comprobaciones son distintas y las dos hacen falta. El prefijo
            // torcido pone la provincia en el desplegable de otro departamento; el padre
            // torcido la pone en el bucle equivocado y le hace pasar la prueba de los
            // huecos sin merecerlo.
            if (!prefijo.equals(provincia.padre())) {
                torcidas.add(codigo + " (" + provincia.nombre() + ") dice padre «"
                        + provincia.padre() + "» pero su código empieza por «" + prefijo + "»");
            }
            if (!departamentos.contains(provincia.padre())) {
                torcidas.add(codigo + " (" + provincia.nombre() + ") cuelga de «"
                        + provincia.padre() + "», que no es ningún departamento sembrado");
            }
        }

        assertThat(torcidas)
                .as("Una provincia con el padre torcido entra en la base sin protestar —la "
                        + "clave ajena la acepta— y aparece en el desplegable del "
                        + "departamento equivocado")
                .isEmpty();
    }

    @Test
    @DisplayName("Dentro de cada departamento, las provincias corren de 01 hacia arriba SIN HUECOS")
    void lasProvinciasNoDejanHuecosDentroDeSuDepartamento() {
        Map<String, List<Integer>> porDepartamento = new TreeMap<>();
        for (Lugar provincia : leerla().stream().filter(l -> l.nivel() == 2).toList()) {
            porDepartamento
                    .computeIfAbsent(provincia.padre(), k -> new ArrayList<>())
                    .add(Integer.parseInt(provincia.codigo().substring(2)));
        }

        Map<String, String> rotos = new LinkedHashMap<>();
        porDepartamento.forEach((departamento, sufijos) -> {
            List<Integer> ordenados = sufijos.stream().sorted().toList();
            List<Integer> esperados = new ArrayList<>();
            for (int i = 1; i <= ordenados.size(); i++) esperados.add(i);
            // Comparar la lista ordenada contra 1..n caza las dos formas de romperlo de una
            // vez: un salto (falta el 07) y un repetido (dos 06), porque cualquiera de las
            // dos desplaza el resto de la comparación.
            if (!ordenados.equals(esperados)) {
                rotos.put(departamento, "tiene " + ordenados + " y debería tener " + esperados);
            }
        });

        assertThat(rotos)
                .as("Las dos últimas cifras del código INEI corren de 01 hacia arriba dentro "
                        + "de cada departamento. Un hueco significa que alguien añadió una "
                        + "provincia inventándose el número, o que se borró una del medio; "
                        + "en los dos casos el código deja de decir qué provincia es")
                .isEmpty();
    }

    // ========================================================================
    // Leer la semilla
    // ========================================================================

    /**
     * Las filas de ubigeo que siembra la V46.
     *
     * <p>Comprueba que encontró algo antes de devolverlo: si la migración se renombra o el
     * formato del insert cambia, un parser mudo dejaría todas las pruebas de arriba en verde
     * sin haber mirado una sola fila.
     */
    private List<Lugar> leerla() {
        String sql;
        try {
            sql = Files.readString(V46, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("No se pudo leer " + V46.toAbsolutePath()
                    + ". Si la migración se renombró, esta prueba se queda sin nada que "
                    + "comprobar y pasaría en verde mirando el vacío", e);
        }

        List<Lugar> lugares = new ArrayList<>();
        Matcher m = FILA.matcher(sql);
        while (m.find()) {
            lugares.add(new Lugar(m.group(1), Integer.parseInt(m.group(2)),
                    m.group(3), m.group(4)));
        }

        assertThat(lugares)
                .as("El parser no reconoció ninguna fila en " + V46 + ": o cambió el formato "
                        + "del insert, o la semilla ya no está ahí")
                .hasSize(DEPARTAMENTOS + 1 + PROVINCIAS);
        return lugares;
    }

    /** {@code ["01", "02", ... ]} hasta {@code cuantos}, con {@code cifras} posiciones. */
    private List<String> numerosSeguidos(int cuantos, int cifras) {
        List<String> seguidos = new ArrayList<>(cuantos);
        for (int i = 1; i <= cuantos; i++) {
            seguidos.add(String.format("%0" + cifras + "d", i));
        }
        return seguidos;
    }
}
