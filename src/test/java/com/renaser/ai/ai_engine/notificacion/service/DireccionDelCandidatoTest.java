package com.renaser.ai.ai_engine.notificacion.service;

import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A qué dirección se le escribe a un candidato.
 *
 * <p>El fallo que esto evita no da síntoma: mandar a {@code @cv-convocatoria.local} no da error
 * en ninguna parte —el servidor acepta la dirección, la fila queda como enviada— y el candidato
 * simplemente nunca recibe nada. Por eso casi todas las pruebas de aquí miran el caso feo, no
 * el bueno.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La dirección del candidato")
class DireccionDelCandidatoTest {

    private static final Long POSTULACION = 42L;

    @Mock private DatoCvRepository datosCv;
    @InjectMocks private DireccionDelCandidato direcciones;

    private Usuario conCorreo(String correo) {
        Usuario u = new Usuario();
        u.setCorreo(correo);
        return u;
    }

    private void elCurriculumDice(String email) {
        lenient().when(datosCv.findByPostulacionId(POSTULACION))
                .thenReturn(Optional.of(DatoCv.builder().email(email).build()));
    }

    @Nested
    @DisplayName("cuando la cuenta tiene una dirección de verdad")
    class CuentaBuena {

        @Test
        @DisplayName("se usa esa, y ni se mira el currículum")
        void ganaLaDeLaCuenta() {
            String elegida = direcciones.de(conCorreo("ana@ejemplo.com"), POSTULACION);

            assertThat(elegida).isEqualTo("ana@ejemplo.com");
            // Importa que no se consulte: la de la cuenta es la que la persona eligió y por la
            // que entra al portal. Preferir la del currículum sería cambiarle la dirección a
            // alguien que ya dijo cuál era la suya.
            verifyNoInteractions(datosCv);
        }
    }

    @Nested
    @DisplayName("cuando la cuenta lleva la dirección inventada por el cargador")
    class CuentaInventada {

        @Test
        @DisplayName("se cae a la que el agente sacó del currículum")
        void cambiaALaDelCurriculum() {
            elCurriculumDice("ana.torres@gmail.com");

            assertThat(direcciones.de(conCorreo("ana.torres@cv-convocatoria.local"), POSTULACION))
                    .isEqualTo("ana.torres@gmail.com");
        }

        @Test
        @DisplayName("el dominio inventado se reconoce venga como venga escrito")
        void noSeEscapaPorLasMayusculas() {
            elCurriculumDice("ana@gmail.com");

            assertThat(direcciones.de(conCorreo("Ana.Torres@CV-Convocatoria.LOCAL"), POSTULACION))
                    .isEqualTo("ana@gmail.com");
        }

        @Test
        @DisplayName("si el currículum tampoco tiene correo, no hay a dónde escribir")
        void sinCurriculumNoHayNada() {
            when(datosCv.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

            // Nulo es una respuesta válida: ServicioCorreo guarda el texto igual y lo marca
            // NO_ENVIADO. Queda escrito qué se le habría dicho y queda claro que no le llegó.
            assertThat(direcciones.de(conCorreo("x@cv-convocatoria.local"), POSTULACION)).isNull();
        }
    }

    @Nested
    @DisplayName("cuando lo que trae el currículum no es una dirección")
    class CurriculumSucio {

        // El agente lee un PDF: lo que saca no siempre es un correo. Pero la regla es floja a
        // propósito — solo descarta lo que seguro no se puede entregar. Una regla estricta
        // rechazaría direcciones reales y raras, que es peor que dejar pasar una que rebota.
        @Test
        @DisplayName("se descartan las que no se pueden entregar")
        void descartaLaBasura() {
            for (String basura : new String[] {
                    null, "", "   ", "no tiene arroba", "@sindestino.com",
                    "ana@sinpunto", "ana@ejemplo.com.", "ana torres@ejemplo.com" }) {
                elCurriculumDice(basura);

                assertThat(direcciones.de(conCorreo("x@cv-convocatoria.local"), POSTULACION))
                        .as("«%s» no se puede entregar", basura)
                        .isNull();
            }
        }

        @Test
        @DisplayName("los espacios de alrededor se recortan, no la descalifican")
        void recortaLosEspacios() {
            elCurriculumDice("  ana@ejemplo.com \n");

            assertThat(direcciones.de(conCorreo("x@cv-convocatoria.local"), POSTULACION))
                    .isEqualTo("ana@ejemplo.com");
        }
    }

    @Nested
    @DisplayName("cuando falta la cuenta")
    class SinCuenta {

        @Test
        @DisplayName("todavía se puede escribir si el currículum trae la dirección")
        void tiraDelCurriculum() {
            elCurriculumDice("ana@ejemplo.com");

            assertThat(direcciones.de(null, POSTULACION)).isEqualTo("ana@ejemplo.com");
        }

        @Test
        @DisplayName("una cuenta sin correo se trata igual que una inventada")
        void correoNuloEnLaCuenta() {
            elCurriculumDice("ana@ejemplo.com");

            assertThat(direcciones.de(conCorreo(null), POSTULACION)).isEqualTo("ana@ejemplo.com");
        }
    }
}
