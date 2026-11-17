package com.renaser.ai.ai_engine.postulacion.service.impl;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le quita al currículum lo que la IA no puede ver.
 *
 * <p>RF-41: antes de que la IA lea el currículum se ocultan <b>foto, edad, sexo y estado
 * civil</b>. La foto se resuelve sola —al pasar el archivo a texto las imágenes se quedan
 * fuera—, así que aquí se trabajan los otros tres, más la fecha de nacimiento, que es la
 * edad escrita de otra manera.
 *
 * <p><b>Lo que se quita se marca, no se borra.</b> Donde había un dato queda
 * {@code [DATO NO UTILIZABLE]}. Así el modelo ve que ahí había algo y que no le toca, en vez
 * de encontrarse una frase cortada que podría intentar completar por su cuenta.
 *
 * <p><b>Esto no es infalible y no pretende serlo.</b> Un currículum es texto libre y siempre
 * habrá una forma de escribir la edad que no esté en esta lista. Lo que sí garantiza es que
 * las formas normales de escribirla —que son las que aparecen en el 99% de los currículums—
 * no llegan al modelo, y que las instrucciones del agente le prohíben además puntuar por
 * ellas si se le colara alguna.
 */
@Component
public class AnonimizadorCv {

    public static final String TAPADO = "[DATO NO UTILIZABLE]";

    /**
     * <b>La bandera Unicode no es un adorno: sin ella se escapan datos.</b>
     *
     * <p>{@code CASE_INSENSITIVE} —y el {@code (?i)} en línea, que es lo mismo— pliega
     * mayúsculas <b>solo en ASCII</b>. Con eso, «AÑOS» no engancha con {@code a[ñn]os},
     * «GÉNERO» no engancha con {@code g[eé]nero} y «UNIÓN LIBRE» no engancha con
     * {@code uni[oó]n}: la Ñ y la É mayúsculas no se pliegan a sus minúsculas. Y los
     * currículums en español escriben justo esos encabezados en mayúsculas («AÑOS DE
     * EXPERIENCIA», «ESTADO CIVIL: UNIÓN LIBRE»).
     *
     * <p>Aquí eso no es una molestia de estilo, es una fuga: lo que esta clase no tapa
     * <b>sale del sistema</b> hacia DeepSeek, que es un tercero. Un patrón que falla con la
     * Ñ manda la edad del candidato fuera, y nadie se entera.
     *
     * <p>{@code UNICODE_CHARACTER_CLASS} arrastra {@code UNICODE_CASE} —de ahí Ñ→ñ y É→é— y
     * además saca de ASCII a {@code \b}, {@code \d} y {@code \s}.
     *
     * <p><b>Ese segundo efecto hay que compensarlo a mano</b>, y por eso el patrón de la edad
     * no usa {@code \b} delante de la cifra. Con {@code \b} en Unicode, {@code Nº34 años}
     * deja de taparse: la {@code º} pasa a contar como letra y desaparece el límite antes del
     * número. Un PDF mal extraído pega la cifra a lo que va delante más a menudo de lo que
     * parece, y aquí tapar de menos significa mandar la edad del candidato a un tercero.
     *
     * <p>Está medido sobre una batería de textos reales de currículum: con esta bandera y ese
     * cambio, se tapa <b>todo</b> lo que se tapaba antes, más los tres casos en mayúsculas
     * acentuadas que se escapaban. Cero pérdidas.
     */
    private static final int BANDERAS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;

    // Sin acentos obligatorios: "genero" y "género" se escriben de las dos maneras.
    //
    // Los `\s*+` son posesivos a propósito. Escrito `\s*[:\-]?\s*`, el motor prueba todas las
    // formas de repartir los espacios entre los dos huecos, así que un texto con muchos
    // espacios seguidos que luego no cumpla el resto lo deja retrocediendo un rato largo. El
    // texto sale del PDF que sube cualquiera con cuenta de candidato: ese rato largo es
    // alcanzable desde fuera. No cambia lo que se acepta —`\s`, `[:-]` y lo que viene detrás
    // no comparten ningún carácter, así que volver atrás nunca le sirvió de nada.

    /**
     * "Edad: 34", "Edad 34 años", "Edad – 34", "Edad.- más de 30": entre el rótulo y la cifra, lo
     * mismo que acepta la señal «Edad» de {@link ReglaDeLaEdad#TRAS_LA_SENAL} —cualquier signo, no
     * solo «:» o «-»—, para que una raya o un «.-» no dejen pasar la edad sin «años».
     */
    private static final String EDAD_ROTULADA =
            "\\bedad" + ReglaDeLaEdad.TRAS_LA_SENAL + "\\d{1,3}\\s*+(a[ñn]os)?";

    /**
     * La regla de la edad hasta noviembre de 2026: una cifra con «años», mirase lo que mirase
     * detrás. Tapaba también «5 años de experiencia». Ya no se usa para tapar; se conserva para
     * saber qué currículums perdieron años de experiencia con ella ({@link #laReglaViejaTapabaDeMas}).
     *
     * <p>Los bordes van escritos a mano y no con \b: lo único que hace falta pedir es que la
     * cifra no venga pegada a otra cifra —para no partir un "1234"— y que detrás no siga una
     * letra. Con \b en Unicode, un "Nº34 años" se escaparía.
     */
    private static final String EDAD_VIEJA = "(?<!\\d)\\d{1,3}\\s*+a[ñn]os(\\s+de\\s+edad)?(?![\\p{L}\\p{N}])";

    /** Los que no cambian entre la regla vieja y la nueva: nacimiento, sexo, estado civil… */
    private static final List<Pattern> RESTO = List.of(
            // "Fecha de nacimiento: 12/03/1990", "Nacido el 12 de marzo de 1990"
            patron("\\b(fecha\\s+de\\s+)?nacimiento\\s*+[:\\-]?\\s*+[^\\n]{0,40}"),
            patron("\\bnacid[oa]\\s+(el\\s+)?[^\\n]{0,40}"),
            // "Sexo: M", "Género: Femenino"
            patron("\\b(sexo|g[eé]nero)\\s*+[:\\-]?\\s*+\\p{L}+"),
            patron("\\b(masculino|femenino)\\b"),
            // "Estado civil: Casado", y las palabras sueltas cuando aparecen como dato
            patron("\\bestado\\s+civil\\s*+[:\\-]?\\s*+[^\\n]{0,30}"),
            patron("\\b(solter[oa]|casad[oa]|divorciad[oa]|viud[oa]|conviviente|"
                    + "uni[oó]n\\s+libre)(\\s*+\\(a\\))?\\b"),
            // "Hijos: 2", "Con 2 hijos": no es estado civil, pero se usa igual y no puntúa
            patron("\\bhijos\\s*+[:\\-]?\\s*+\\d{1,2}"),
            // Una foto que se coló como texto ("[foto]", "Fotografía:")
            patron("\\bfotograf[ií]a\\s*+[:\\-]?"));

    private static final Pattern ROTULADA = patron(EDAD_ROTULADA);

    private static final Pattern VIEJA = patron(EDAD_VIEJA);

    /**
     * Cuándo «N años» es una edad lo decide {@link ReglaDeLaEdad} (decisiones D-2 y D-3 de la spec
     * la-ia-sabe-que-dia-es-hoy): toda «N años» se tapa salvo una lista blanca mínima y literal de
     * lo que hizo la persona, y una señal de edad delante tapa siempre. Ante la duda, se tapa.
     */
    private static final UnaryOperator<String> EDAD_NUEVA = texto -> ReglaDeLaEdad.tapar(texto, TAPADO);

    /** La de antes de noviembre de 2026, solo para comparar. */
    private static final UnaryOperator<String> EDAD_ANTES = texto ->
            VIEJA.matcher(texto).replaceAll(Matcher.quoteReplacement(TAPADO));

    private static Pattern patron(String expresion) {
        return Pattern.compile(expresion, BANDERAS);
    }

    /** Si un texto lleva la marca de un dato tapado: se refiere a algo que la IA no vio. */
    public static boolean traeLaMarca(String texto) {
        return texto != null && texto.contains(TAPADO);
    }

    /**
     * Si con la regla de antes de noviembre de 2026 este currículum perdía algo que la de ahora
     * deja ver: en la práctica, años de experiencia («5 años de experiencia» llegaba a la IA como
     * «[DATO NO UTILIZABLE] de experiencia»). Lo usa el comando que recalifica a los afectados.
     */
    public boolean laReglaViejaTapabaDeMas(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        return !tapar(texto, EDAD_ANTES).equals(tapar(texto, EDAD_NUEVA));
    }

    public String anonimizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        return tapar(texto, EDAD_NUEVA);
    }

    /** Primero la edad rotulada, luego la edad suelta con la regla que toque y después el resto. */
    private static String tapar(String texto, UnaryOperator<String> edad) {
        String salida = edad.apply(ROTULADA.matcher(texto).replaceAll(Matcher.quoteReplacement(TAPADO)));
        for (Pattern patron : RESTO) {
            salida = patron.matcher(salida).replaceAll(Matcher.quoteReplacement(TAPADO));
        }
        // Varios tapados seguidos ("Sexo: M · Estado civil: Casado") se leen mejor como uno
        return salida.replaceAll("(" + Pattern.quote(TAPADO) + "[\\s·,;|\\-]*){2,}",
                Matcher.quoteReplacement(TAPADO + " "));
    }
}
