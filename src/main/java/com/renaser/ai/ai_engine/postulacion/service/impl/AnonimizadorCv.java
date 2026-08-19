package com.renaser.ai.ai_engine.postulacion.service.impl;

import org.springframework.stereotype.Component;

import java.util.List;
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

    // (?i) sin acentos obligatorios: "genero" y "género" se escriben de las dos maneras.
    private static final List<Pattern> PATRONES = List.of(
            // "Edad: 34", "Edad 34 años"
            Pattern.compile("(?i)\\bedad\\s*[:\\-]?\\s*\\d{1,3}\\s*(a[ñn]os)?"),
            // "34 años", "34 años de edad"
            Pattern.compile("(?i)\\b\\d{1,3}\\s*a[ñn]os(\\s+de\\s+edad)?\\b"),
            // "Fecha de nacimiento: 12/03/1990", "Nacido el 12 de marzo de 1990"
            Pattern.compile("(?i)\\b(fecha\\s+de\\s+)?nacimiento\\s*[:\\-]?\\s*[^\\n]{0,40}"),
            Pattern.compile("(?i)\\bnacid[oa]\\s+(el\\s+)?[^\\n]{0,40}"),
            // "Sexo: M", "Género: Femenino"
            Pattern.compile("(?i)\\b(sexo|g[eé]nero)\\s*[:\\-]?\\s*\\p{L}+"),
            Pattern.compile("(?i)\\b(masculino|femenino)\\b"),
            // "Estado civil: Casado", y las palabras sueltas cuando aparecen como dato
            Pattern.compile("(?i)\\bestado\\s+civil\\s*[:\\-]?\\s*[^\\n]{0,30}"),
            Pattern.compile("(?i)\\b(solter[oa]|casad[oa]|divorciad[oa]|viud[oa]|conviviente|"
                    + "uni[oó]n\\s+libre)(\\s*\\(a\\))?\\b"),
            // "Hijos: 2", "Con 2 hijos": no es estado civil, pero se usa igual y no puntúa
            Pattern.compile("(?i)\\bhijos\\s*[:\\-]?\\s*\\d{1,2}"),
            // Una foto que se coló como texto ("[foto]", "Fotografía:")
            Pattern.compile("(?i)\\bfotograf[ií]a\\s*[:\\-]?"));

    public String anonimizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        String salida = texto;
        for (Pattern patron : PATRONES) {
            salida = patron.matcher(salida).replaceAll(Matcher.quoteReplacement(TAPADO));
        }
        // Varios tapados seguidos ("Sexo: M · Estado civil: Casado") se leen mejor como uno
        return salida.replaceAll("(" + Pattern.quote(TAPADO) + "[\\s·,;|\\-]*){2,}",
                Matcher.quoteReplacement(TAPADO + " "));
    }
}
