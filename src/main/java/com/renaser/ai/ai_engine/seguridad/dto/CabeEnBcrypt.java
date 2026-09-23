package com.renaser.ai.ai_engine.seguridad.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Una contraseña que BCrypt puede guardar: 72 bytes en UTF-8 como mucho.
 *
 * <p>Es un tope del algoritmo, no una regla del negocio, y se cuenta en bytes y no en
 * caracteres: una letra con tilde o una «ñ» ocupan dos, un emoji cuatro. Pasado el tope,
 * Spring Security lanza una excepción con el texto «password cannot be more than 72 bytes»,
 * que acababa tal cual, en inglés, bajo el campo. Aquí se rechaza antes y con un motivo en
 * español que no habla de bytes.
 *
 * <p>Un nulo pasa: de la contraseña vacía se ocupa {@code @NotBlank}.
 *
 * <p>⚠️ Solo lo llevan, por ahora, las pantallas de contraseña nueva por enlace. El registro
 * del portal y la invitación del panel tienen el mismo tope sin esta validación.
 */
@Documented
@Constraint(validatedBy = CabeEnBcrypt.Validador.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CabeEnBcrypt {

    /** Lo más largo que BCrypt acepta, en bytes UTF-8. El frontend usa el mismo número. */
    int MAXIMO_BYTES = 72;

    /** El mismo texto que muestra el frontend al comprobarlo antes de enviar. */
    String MENSAJE = "La contraseña es demasiado larga. Usa como máximo 72 caracteres; "
            + "las letras con tilde, la ñ y los emojis cuentan por más de uno.";

    String message() default MENSAJE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validador implements ConstraintValidator<CabeEnBcrypt, String> {

        /** Si BCrypt puede cifrarla sin lanzar nada. La usa también el servicio. */
        public static boolean cabe(String contrasena) {
            return contrasena == null
                    || contrasena.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES;
        }

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return cabe(valor);
        }
    }
}
