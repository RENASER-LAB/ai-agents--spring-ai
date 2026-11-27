package com.renaser.ai.ai_engine.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que no haya dos migraciones peleándose el mismo número.
 *
 * <p><b>Es el único conflicto de este repositorio que git no sabe ver.</b> Dos ramas que
 * añaden {@code V40__una_cosa.sql} y {@code V40__otra_cosa.sql} son, para git, dos archivos
 * distintos: las fusiona sin pedir permiso y sin marcar conflicto. Nadie se entera hasta que
 * Flyway arranca en el despliegue y se niega —«Found more than one migration with version
 * 40»— con la aplicación ya caída.
 *
 * <p>Pasó de verdad el 26/08/2026, y con tres ramas reclamando el 37 a la vez. Aquella vez
 * solo tumbó el arranque local, que se arregla borrando una fila; la siguiente puede tumbar
 * el despliegue, que no.
 *
 * <p><b>Para que avise a tiempo hace falta algo más que esta prueba:</b> cada rama pasa el
 * CI en verde por su cuenta, porque por separado cada una tiene un solo V40. El choque nace
 * al fusionar la segunda. Que salte antes del merge exige que GitHub obligue a la rama a
 * estar al día con {@code main} —así el CI corre sobre el resultado de la fusión—; sin eso,
 * esta prueba avisa igual, pero cuando el número repetido ya está en {@code main}.
 */
@DisplayName("Las migraciones no se pelean el número")
class MigracionesSinChoqueTest {

    private static final Path CARPETA = Path.of("src/main/resources/db/migration");

    /** {@code V37__la_plataforma_y_sus_empresas.sql} → versión 37, y el resto es el nombre. */
    private static final Pattern NOMBRE = Pattern.compile("^V(\\d+)__(.+)\\.sql$");

    /**
     * Los números que faltan a propósito, cada uno con su porqué. Un salto solo se tolera si
     * <em>todos</em> los números que se salta están aquí: así un hueco documentado no tapa otro
     * que no lo está (si se perdiera la V86, el salto de V85 a V89 seguiría avisando), y da igual
     * en qué orden lleguen las ramas que tienen un número reservado.
     */
    private static final Set<Integer> HUECOS_DELIBERADOS = Set.of(
            // V81 a V83 no se usarán nunca. Eran reservas de las specs de la reunión del
            // 13/11/2026, pero la del informe en PDF por etapa (V84, los textos del redactor)
            // llegó antes a producción y Flyway no aplica una versión menor que la mayor ya
            // aplicada: las specs que las tenían subieron de número al construirse.
            81, 82, 83,
            // V87 y V88 las tienen reservadas dos specs aún sin fusionar: la convocatoria desde el
            // propósito (V87) y el video de la empresa por webcam (V88). La de las incompletas en
            // el ranking y más tiempo (V89) se construyó antes. Si entran antes de que la V89
            // llegue a producción, el hueco se cierra solo; si no, tendrán que subir de número
            // y V87–V88 quedarán como V81–V83.
            87, 88,
            // V90 y V91 las tienen reservadas dos specs aún sin fusionar: el seguimiento de
            // pendientes entre etapas (V90) y el video de la empresa (V91). Las notificaciones del
            // candidato (V92) se construyeron antes; mismo trato que V87–V88.
            90, 91,
            // V94 la tiene reservada el seguimiento de pendientes entre etapas, que se construye a
            // la vez que las etapas por vacante (V95). Si entra antes, el hueco se cierra solo; si
            // no, sube de número al sincronizar con main. Mismo trato que V87–V88.
            94);

    @Test
    @DisplayName("Ningún número de versión se repite: git no ve ese conflicto, Flyway sí")
    void ningunNumeroSeRepite() throws IOException {
        Map<Integer, List<String>> porVersion = leerlas();

        List<String> repetidos = porVersion.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> "V" + e.getKey() + " la reclaman " + e.getValue())
                .toList();

        assertThat(repetidos)
                .as("Dos migraciones con el mismo número tumban el arranque de Flyway. "
                        + "Suele venir de dos ramas que eligieron el número a la vez: la que "
                        + "llegue después renumera a la siguiente libre")
                .isEmpty();
    }

    @Test
    @DisplayName("Los nombres siguen el formato que Flyway espera")
    void todasSeLlamanComoDeben() throws IOException {
        List<String> raros;
        try (Stream<Path> archivos = Files.list(CARPETA)) {
            raros = archivos
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".sql"))
                    .filter(n -> !NOMBRE.matcher(n).matches())
                    .toList();
        }
        // Un archivo mal nombrado no lo aplica nadie y tampoco se queja nadie: se queda ahí,
        // pareciendo trabajo hecho, hasta que alguien busca por qué falta esa columna.
        assertThat(raros)
                .as("Formato esperado: V<numero>__<descripcion_con_guiones_bajos>.sql")
                .isEmpty();
    }

    @Test
    @DisplayName("La numeración no deja huecos: un salto suele ser una migración perdida")
    void laNumeracionEsSeguida() throws IOException {
        List<Integer> versiones = new ArrayList<>(leerlas().keySet());

        List<String> huecos = new ArrayList<>();
        for (int i = 1; i < versiones.size(); i++) {
            int anterior = versiones.get(i - 1);
            int actual = versiones.get(i);
            boolean deliberado = IntStream.range(anterior + 1, actual)
                    .allMatch(HUECOS_DELIBERADOS::contains);
            if (!deliberado) {
                huecos.add("entre V" + anterior + " y V" + actual);
            }
        }
        // Flyway aplica igual con huecos, así que esto no es una regla suya sino nuestra: un
        // salto casi siempre significa que alguien renumeró a medias, o que una migración se
        // perdió en un rebase. Un hueco deliberado se documenta, número a número, en
        // HUECOS_DELIBERADOS.
        assertThat(huecos)
                .as("Hay saltos en la numeración de las migraciones")
                .isEmpty();
    }

    /** Las migraciones agrupadas por su número, en orden. */
    private Map<Integer, List<String>> leerlas() throws IOException {
        Map<Integer, List<String>> porVersion = new TreeMap<>();
        try (Stream<Path> archivos = Files.list(CARPETA)) {
            archivos.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".sql"))
                    .forEach(nombre -> {
                        Matcher m = NOMBRE.matcher(nombre);
                        if (m.matches()) {
                            porVersion.computeIfAbsent(Integer.parseInt(m.group(1)),
                                    k -> new ArrayList<>()).add(nombre);
                        }
                    });
        }
        assertThat(porVersion)
                .as("No se encontró ninguna migración en " + CARPETA.toAbsolutePath()
                        + ": si la carpeta se movió, esta prueba pasaría en verde sin mirar nada")
                .isNotEmpty();
        return porVersion;
    }
}
