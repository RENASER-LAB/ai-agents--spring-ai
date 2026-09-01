package com.renaser.ai.ai_engine.arquitectura;

import com.renaser.ai.ai_engine.usuario.entity.Persona;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que anonimizar vacíe TODOS los datos personales de {@code persona}, no los que había el día
 * que alguien escribió el método.
 *
 * <p><b>Por qué existe.</b> Al añadir {@code ciudadUbigeo} se olvidó justamente eso: el campo
 * entró en la entidad, en la migración y en el alta, y el borrado siguió limpiando los cinco de
 * siempre. La ciudad sobrevivía al anonimizado, y con la provincia más el resto del expediente
 * —vacante, fechas, notas— se vuelve a señalar a una persona concreta.
 *
 * <p>No lo cazó nadie porque no había nada que mirase las dos listas a la vez. Esto lo hace: lee
 * los campos de la entidad y los contrasta con las líneas {@code persona.setX(null)} del
 * servicio. El siguiente que añada un dato personal y no toque el borrado, falla aquí.
 *
 * <p>Se lee el FUENTE y no se ejecuta el servicio a propósito: montar sus repositorios pediría
 * una docena de dobles, y lo que hay que comprobar es una omisión, que se ve leyendo.
 */
@DisplayName("Anonimizar no se deja ningún dato personal de la persona")
class AnonimizarNoOlvidaCamposTest {

    private static final Path BORRADO = Path.of(
            "src/main/java/com/renaser/ai/ai_engine/administracion/service/impl",
            "ServicioBorradoDatosImpl.java");

    /**
     * Lo que NO es un dato personal y por tanto no hay que vaciar: la clave, la marca de que
     * ya se anonimizó —que se escribe, no se borra— y cuándo se creó la fila.
     */
    private static final Set<String> NO_SON_IDENTIDAD = Set.of("id", "anonimizadoEn", "creadoEn");

    @Test
    @DisplayName("cada campo de identidad de Persona se pone a null al borrar")
    void cadaCampoDeIdentidadSeVacia() throws IOException {
        String fuente = Files.readString(BORRADO, StandardCharsets.UTF_8);

        List<String> identidad = List.of(Persona.class.getDeclaredFields()).stream()
                .filter(f -> !f.isSynthetic() && !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .filter(nombre -> !NO_SON_IDENTIDAD.contains(nombre))
                .toList();

        assertThat(identidad)
                .as("si Persona se queda sin campos de identidad, esta prueba dejó de probar algo")
                .isNotEmpty();

        List<String> olvidados = identidad.stream()
                .filter(nombre -> !fuente.contains("persona.set" + mayuscula(nombre) + "(null)"))
                .toList();

        assertThat(olvidados)
                .as("Persona.%s se guarda pero el borrado no lo vacía: o se anonimiza, "
                        + "o se declara en NO_SON_IDENTIDAD diciendo por qué no lo es", olvidados)
                .isEmpty();
    }

    @Test
    @DisplayName("y la marca de anonimizado se escribe, que es lo que dice que ya pasó")
    void seDejaLaMarcaDeAnonimizado() throws IOException {
        assertThat(Files.readString(BORRADO, StandardCharsets.UTF_8))
                .contains("persona.setAnonimizadoEn(Instant.now())");
    }

    private static String mayuscula(String nombre) {
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
    }
}
