package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.GuardarVacante;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qué cambió al guardar una vacante, y qué se le cuenta al candidato.
 *
 * <p>Lo que se protege aquí es la <b>diferencia entre los tres tipos de campo</b>. Un campo
 * corto se cuenta entero —el horario nuevo no se entiende sin el viejo—, uno largo solo se
 * nombra —dos párrafos en una campana tapan en vez de informar— y uno interno no sale de la
 * empresa. Equivocarse en la clasificación no rompe nada: simplemente le llega al candidato
 * una noticia que no le sirve, o no le llega la que sí.
 *
 * <p>Y lo otro: <b>los espacios del principio y del final no son un cambio</b>. Es lo que
 * hace que guardar dos veces lo mismo sea inofensivo, y sin ello un copiar y pegar con un
 * salto de línea de más le escribiría a cuarenta personas.
 */
@DisplayName("Los cambios de una vacante")
class CambiosDeLaVacanteTest {

    private Vacante laVacante() {
        return Vacante.builder()
                .id(7L)
                .titulo("Coordinador de sede")
                .descripcion("Lleva la operación de la sede")
                .proposito("Sostener el servicio")
                .responsabilidades("Coordinar al equipo")
                .requisitos("Tres años de experiencia")
                .modalidad("Presencial")
                .horario("L-V de 9 a 6")
                .ubicacion("Lima")
                .tipoCierre("PERMANENTE")
                .responsableUsuarioId(3L)
                .estado("PUBLICADA")
                .build();
    }

    /** El mismo formulario que ya está guardado: nada cambia salvo lo que se le diga. */
    private GuardarVacante comoEsta(String... cambios) {
        Vacante v = laVacante();
        String titulo = v.getTitulo();
        String descripcion = v.getDescripcion();
        String horario = v.getHorario();
        String ubicacion = v.getUbicacion();
        for (int i = 0; i < cambios.length; i += 2) {
            switch (cambios[i]) {
                case "titulo" -> titulo = cambios[i + 1];
                case "descripcion" -> descripcion = cambios[i + 1];
                case "horario" -> horario = cambios[i + 1];
                case "ubicacion" -> ubicacion = cambios[i + 1];
                default -> throw new IllegalArgumentException("campo desconocido: " + cambios[i]);
            }
        }
        return new GuardarVacante(30L, 5L, titulo, descripcion, v.getProposito(),
                v.getResponsabilidades(), v.getRequisitos(), v.getModalidad(), horario,
                ubicacion, null, null, v.getTipoCierre(), v.getPlazas(), v.getAbreEn(),
                v.getCierraEn(), v.getResponsableUsuarioId(), null);
    }

    private CambiosDeLaVacante comparar(GuardarVacante datos) {
        return CambiosDeLaVacante.entre(laVacante(), datos, "S/ 3 000", "S/ 3 000", "", "");
    }

    @Nested
    @DisplayName("Qué cuenta como un cambio")
    class QueCuenta {

        @Test
        @DisplayName("el mismo formulario, tal cual, no cambió nada")
        void elMismoFormularioNoEsUnCambio() {
            assertThat(comparar(comoEsta()).hayCambios()).isFalse();
        }

        @Test
        @DisplayName("los espacios del principio y del final no cuentan")
        void losEspaciosNoCuentan() {
            // Es lo que deja copiar y pegar, y lo que hace que un reintento no vuelva a
            // avisar a nadie.
            CambiosDeLaVacante cambios = comparar(
                    comoEsta("horario", "  L-V de 9 a 6  ", "titulo", " Coordinador de sede"));

            assertThat(cambios.hayCambios()).isFalse();
            assertThat(cambios.cuerpoDelAviso())
                    .isEqualTo("Tu postulación sigue su curso y no tienes que hacer nada.");
        }

        @Test
        @DisplayName("un campo vacío y uno en blanco son lo mismo")
        void elBlancoEsVacio() {
            Vacante v = laVacante();
            v.setUbicacion("   ");
            GuardarVacante datos = comoEsta("ubicacion", null);

            assertThat(CambiosDeLaVacante.entre(v, datos, "S/ 3 000", "S/ 3 000", "", "").hayCambios())
                    .isFalse();
        }

        @Test
        @DisplayName("cambiar el sueldo es un cambio visible, aunque el formulario esté igual")
        void elSueldoEsUnCambioVisible() {
            CambiosDeLaVacante cambios =
                    CambiosDeLaVacante.entre(laVacante(), comoEsta(), "S/ 3 000", "S/ 3 500", "", "");

            assertThat(cambios.hayCambios()).isTrue();
            assertThat(cambios.cambioElSueldo()).isTrue();
            assertThat(cambios.hayVisibles()).isTrue();
            assertThat(cambios.cuerpoDelAviso()).contains("Remuneración: S/ 3 000 → S/ 3 500");
        }
    }

    @Nested
    @DisplayName("Qué sale en el aviso")
    class QueSaleEnElAviso {

        @Test
        @DisplayName("los campos cortos van con su antes y su ahora")
        void losCortosLlevanLasDosPuntas() {
            CambiosDeLaVacante cambios = comparar(comoEsta("horario", "L-V de 8 a 5"));

            assertThat(cambios.cuerpoDelAviso())
                    .isEqualTo("Horario: L-V de 9 a 6 → L-V de 8 a 5. "
                            + "Tu postulación sigue su curso y no tienes que hacer nada.");
        }

        @Test
        @DisplayName("un campo que antes no decía nada lo dice, en vez de dejar un hueco")
        void loVacioSeNombra() {
            Vacante v = laVacante();
            v.setUbicacion(null);

            CambiosDeLaVacante cambios = CambiosDeLaVacante.entre(v,
                    comoEsta("ubicacion", "Arequipa"), "S/ 3 000", "S/ 3 000", "", "");

            // «Zona o referencia:  → Arequipa» se lee como un fallo de la pantalla. Y desde la
            // V62 el campo se llama por lo que es: la ciudad ya no vive aquí.
            assertThat(cambios.cuerpoDelAviso())
                    .contains("Zona o referencia: sin indicar → Arequipa");
        }

        @Test
        @DisplayName("los campos largos solo se nombran: el texto no se pega en la campana")
        void losLargosSoloSeNombran() {
            CambiosDeLaVacante cambios = comparar(
                    comoEsta("descripcion", "Otra cosa completamente distinta"));

            assertThat(cambios.cuerpoDelAviso())
                    .isEqualTo("Se actualizó la descripción. "
                            + "Tu postulación sigue su curso y no tienes que hacer nada.");
            assertThat(cambios.cuerpoDelAviso()).doesNotContain("Otra cosa");
        }

        @Test
        @DisplayName("varios largos se enumeran en una sola frase")
        void variosLargosSeEnumeran() {
            GuardarVacante datos = new GuardarVacante(30L, 5L, "Coordinador de sede",
                    "Otra descripción", "Sostener el servicio", "Coordinar al equipo",
                    "Cinco años de experiencia", "Presencial", "L-V de 9 a 6", "Lima", null, null,
                    "PERMANENTE", null, null, null, 3L, null);

            assertThat(comparar(datos).cuerpoDelAviso())
                    .startsWith("Se actualizaron la descripción y los requisitos.");
        }

        @Test
        @DisplayName("cortos y largos juntos: primero lo que se puede decir entero")
        void losDosTiposJuntos() {
            CambiosDeLaVacante cambios = comparar(
                    comoEsta("horario", "Turnos rotativos", "descripcion", "Otra descripción"));

            assertThat(cambios.cuerpoDelAviso())
                    .isEqualTo("Horario: L-V de 9 a 6 → Turnos rotativos · "
                            + "Se actualizó la descripción. "
                            + "Tu postulación sigue su curso y no tienes que hacer nada.");
        }

        @Test
        @DisplayName("el título del aviso lleva el nombre que la vacante tiene ahora")
        void elTituloLlevaElNombreDeAhora() {
            assertThat(CambiosDeLaVacante.tituloDelAviso("Coordinador de sede senior"))
                    .isEqualTo("Se actualizó la vacante «Coordinador de sede senior»");
        }
    }

    /**
     * La ciudad del catálogo (V62): se compara por su código y se cuenta por su nombre.
     *
     * <p>Los nombres los pone el servicio, que es quien tiene el catálogo; aquí llegan como
     * dos cadenas más, igual que el sueldo. Lo que se protege es que un cambio de código con
     * el mismo nombre siga siendo un cambio, que el aviso escriba el nombre y no el código, y
     * que la ciudad vacía se escriba «—», que es lo que fija la spec.
     */
    @Nested
    @DisplayName("La ciudad del catálogo")
    class LaCiudad {

        private GuardarVacante conCiudad(String codigo) {
            Vacante v = laVacante();
            return new GuardarVacante(30L, 5L, v.getTitulo(), v.getDescripcion(),
                    v.getProposito(), v.getResponsabilidades(), v.getRequisitos(),
                    v.getModalidad(), v.getHorario(), v.getUbicacion(), codigo, null,
                    v.getTipoCierre(), v.getPlazas(), v.getAbreEn(), v.getCierraEn(),
                    v.getResponsableUsuarioId(), null);
        }

        @Test
        @DisplayName("ponerle ciudad a una vacante que no tenía es un cambio visible con «—» delante")
        void deNadaAArequipa() {
            CambiosDeLaVacante cambios = CambiosDeLaVacante.entre(laVacante(),
                    conCiudad("0401"), "S/ 3 000", "S/ 3 000", "", "Arequipa");

            assertThat(cambios.hayVisibles()).isTrue();
            assertThat(cambios.cuerpoDelAviso())
                    .isEqualTo("Ciudad: — → Arequipa. "
                            + "Tu postulación sigue su curso y no tienes que hacer nada.");
            assertThat(cambios.anterior()).containsEntry("ciudadUbigeo", "");
            assertThat(cambios.nuevo()).containsEntry("ciudadUbigeo", "Arequipa");
        }

        @Test
        @DisplayName("la misma ciudad, con o sin espacios alrededor del código, no es un cambio")
        void laMismaCiudadNoCambia() {
            Vacante v = laVacante();
            v.setCiudadUbigeo("1501");

            CambiosDeLaVacante cambios = CambiosDeLaVacante.entre(v, conCiudad(" 1501 "),
                    "S/ 3 000", "S/ 3 000", "Lima", "Lima");

            assertThat(cambios.hayCambios()).isFalse();
        }

        @Test
        @DisplayName("se compara el código, no el nombre: dos ciudades homónimas son dos ciudades")
        void seComparaElCodigo() {
            Vacante v = laVacante();
            v.setCiudadUbigeo("1501");

            CambiosDeLaVacante cambios = CambiosDeLaVacante.entre(v, conCiudad("9901"),
                    "S/ 3 000", "S/ 3 000", "Lima", "Lima");

            assertThat(cambios.hayCambios()).isTrue();
            assertThat(cambios.cuerpoDelAviso()).contains("Ciudad: Lima → Lima");
        }

        @Test
        @DisplayName("quitar la ciudad también se cuenta, y la zona se llama por su nombre nuevo")
        void quitarLaCiudadYCambiarLaZona() {
            Vacante v = laVacante();
            v.setCiudadUbigeo("0401");
            v.setUbicacion(null);
            GuardarVacante datos = new GuardarVacante(30L, 5L, v.getTitulo(),
                    v.getDescripcion(), v.getProposito(), v.getResponsabilidades(),
                    v.getRequisitos(), v.getModalidad(), v.getHorario(), "Selva Alegre", null,
                    null, v.getTipoCierre(), v.getPlazas(), v.getAbreEn(), v.getCierraEn(),
                    v.getResponsableUsuarioId(), null);

            CambiosDeLaVacante cambios = CambiosDeLaVacante.entre(v, datos,
                    "S/ 3 000", "S/ 3 000", "Arequipa", "");

            assertThat(cambios.cuerpoDelAviso())
                    .isEqualTo("Ciudad: Arequipa → — · "
                            + "Zona o referencia: sin indicar → Selva Alegre. "
                            + "Tu postulación sigue su curso y no tienes que hacer nada.");
        }
    }

    @Nested
    @DisplayName("Lo que el formulario no enseña")
    class LoQueElFormularioNoEnsena {

        /**
         * La regresión del hallazgo QA-01.
         *
         * <p>El cuerpo del PUT es el formulario, y el formulario no enseña la fecha de
         * apertura ni las plazas de una vacante que no cierra por plazas. Leer esa ausencia
         * como un nulo convertía «abrir el lápiz y pulsar guardar» en un borrado silencioso
         * —con auditoría diciendo «plazas: 5 → ""» y el panel diciendo «Cambios guardados»—.
         */
        @Test
        @DisplayName("guardar sin tocar nada no cambia la apertura ni las plazas que no se enseñaron")
        void loQueNoViajaNoSeBorra() {
            Vacante v = laVacante();
            v.setAbreEn(Instant.parse("2026-09-23T03:35:54Z"));
            v.setPlazas(5);
            v.setCierraEn(Instant.parse("2026-10-20T00:00:00Z"));

            // El cuerpo exacto del panel: PERMANENTE, sin plazas, sin fechas.
            CambiosDeLaVacante cambios =
                    CambiosDeLaVacante.entre(v, comoEsta(), "S/ 3 000", "S/ 3 000", "", "");

            assertThat(cambios.hayCambios()).isFalse();
            assertThat(cambios.plazasQueQuedan()).isEqualTo(5);
            assertThat(cambios.cierreQueQueda())
                    .isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
        }

        @Test
        @DisplayName("cambiar la forma de cierre sí limpia lo que ya no rige, y se audita")
        void cambiarLaFormaLimpiaLoQueSobra() {
            Vacante v = laVacante();
            v.setTipoCierre("FECHA");
            v.setPlazas(5);
            v.setCierraEn(FormaDeCierre.alFinalDelDia(Instant.parse("2026-12-01T15:00:00Z")));

            GuardarVacante aPermanente = new GuardarVacante(30L, 5L, v.getTitulo(),
                    v.getDescripcion(), v.getProposito(), v.getResponsabilidades(),
                    v.getRequisitos(), v.getModalidad(), v.getHorario(), v.getUbicacion(), null,
                    null, "PERMANENTE", null, null, null, v.getResponsableUsuarioId(), null);

            CambiosDeLaVacante cambios =
                    CambiosDeLaVacante.entre(v, aPermanente, "S/ 3 000", "S/ 3 000", "", "");

            assertThat(cambios.plazasQueQuedan()).isNull();
            assertThat(cambios.cierreQueQueda()).isNull();
            assertThat(cambios.hayVisibles()).as("la forma de cierre es interna").isFalse();
            assertThat(cambios.anterior()).containsEntry("plazas", "5")
                    .containsEntry("tipoCierre", "FECHA");
            assertThat(cambios.nuevo()).containsEntry("plazas", "").containsEntry("cierraEn", "");
        }

        /**
         * La regresión del hallazgo QA-02, con la regla de la V78.
         *
         * <p>El formulario enseña el día. Lo que vuelve —la medianoche UTC del panel de antes, o
         * las 23:59:59 de Lima del de ahora— se guarda como las 23:59:59 de Lima de ese día, así
         * que guardar sin tocar el campo no es un cambio, no se audita ni mueve la hora.
         */
        @Test
        @DisplayName("la fecha de cierre se compara por día: guardar sin tocarla no es un cambio")
        void laFechaDeCierreSeComparaPorDia() {
            Instant guardada = Instant.parse("2026-12-02T04:59:59Z"); // 01/12, 23:59:59 de Lima
            for (String comoLlega : new String[] {"2026-12-01T00:00:00Z", "2026-12-02T04:59:59Z",
                    "2026-12-01T15:30:00Z"}) {
                Vacante v = laVacante();
                v.setTipoCierre("FECHA");
                v.setCierraEn(guardada);

                CambiosDeLaVacante cambios = CambiosDeLaVacante.entre(v, porFecha(comoLlega),
                        "S/ 3 000", "S/ 3 000", "", "");

                assertThat(cambios.hayCambios()).as("llegando como %s", comoLlega).isFalse();
                assertThat(cambios.cierreQueQueda()).isEqualTo(guardada);
            }
        }

        @Test
        @DisplayName("cambiar el día sí es un cambio, y se guarda ese día a las 23:59:59 de Lima")
        void cambiarElDiaSiEsUnCambio() {
            Vacante v = laVacante();
            v.setTipoCierre("FECHA");
            v.setCierraEn(Instant.parse("2026-12-02T04:59:59Z"));

            CambiosDeLaVacante cambios = CambiosDeLaVacante.entre(v,
                    porFecha("2026-12-15T00:00:00Z"), "S/ 3 000", "S/ 3 000", "", "");

            assertThat(cambios.hayCambios()).isTrue();
            assertThat(cambios.hayVisibles()).as("la fecha de cierre es interna").isFalse();
            assertThat(cambios.cierreQueQueda()).isEqualTo(Instant.parse("2026-12-16T04:59:59Z"));
            assertThat(cambios.anterior()).containsEntry("cierraEn", "2026-12-02T04:59:59Z");
        }

        @Test
        @DisplayName("de Permanente a Fecha límite se guarda el día elegido, a las 23:59:59 de Lima")
        void dePermanenteAFecha() {
            CambiosDeLaVacante cambios = CambiosDeLaVacante.entre(laVacante(),
                    porFecha("2026-12-02T04:59:59Z"), "S/ 3 000", "S/ 3 000", "", "");

            assertThat(cambios.cierreQueQueda()).isEqualTo(Instant.parse("2026-12-02T04:59:59Z"));
            assertThat(cambios.nuevo()).containsEntry("tipoCierre", "FECHA");
        }

        /** El formulario con forma «por fecha» y el día que se le diga, como lo manda el panel. */
        private GuardarVacante porFecha(String instante) {
            Vacante v = laVacante();
            return new GuardarVacante(30L, 5L, v.getTitulo(), v.getDescripcion(),
                    v.getProposito(), v.getResponsabilidades(), v.getRequisitos(),
                    v.getModalidad(), v.getHorario(), v.getUbicacion(), null, null, "FECHA", null,
                    null, Instant.parse(instante), v.getResponsableUsuarioId(), null);
        }

        @Test
        @DisplayName("unas plazas que lleguen en el cuerpo ya no se leen: ninguna forma las enseña")
        void lasPlazasYaNoSeLeen() {
            Vacante v = laVacante();
            v.setPlazas(5);

            GuardarVacante conPlazas = new GuardarVacante(30L, 5L, v.getTitulo(),
                    v.getDescripcion(), v.getProposito(), v.getResponsabilidades(),
                    v.getRequisitos(), v.getModalidad(), v.getHorario(), v.getUbicacion(), null,
                    null, "PERMANENTE", 9, null, null, v.getResponsableUsuarioId(), null);

            CambiosDeLaVacante cambios =
                    CambiosDeLaVacante.entre(v, conPlazas, "S/ 3 000", "S/ 3 000", "", "");

            assertThat(cambios.plazasQueQuedan()).isEqualTo(5);
            assertThat(cambios.hayCambios()).isFalse();
        }
    }

    @Nested
    @DisplayName("Lo interno")
    class LoInterno {

        @Test
        @DisplayName("cambiar solo el responsable, la forma de cierre o la fecha no le dice nada al candidato")
        void loInternoNoEsVisible() {
            GuardarVacante datos = new GuardarVacante(30L, 5L, "Coordinador de sede",
                    "Lleva la operación de la sede", "Sostener el servicio",
                    "Coordinar al equipo", "Tres años de experiencia", "Presencial",
                    "L-V de 9 a 6", "Lima", null, null, "FECHA", null,
                    null, Instant.parse("2026-12-02T04:59:59Z"), 9L, null);

            CambiosDeLaVacante cambios = comparar(datos);

            assertThat(cambios.hayCambios()).isTrue();
            assertThat(cambios.hayVisibles()).isFalse();
            // Pero queda todo escrito para la auditoría: que no se avise no es que no importe.
            assertThat(cambios.anterior()).containsKeys("tipoCierre", "cierraEn",
                    "responsableUsuarioId");
            assertThat(cambios.nuevo()).containsEntry("cierraEn", "2026-12-02T04:59:59Z")
                    .containsEntry("responsableUsuarioId", "9");
        }
    }

    @Nested
    @DisplayName("La auditoría")
    class LaAuditoria {

        @Test
        @DisplayName("guarda el valor de antes y el de ahora de cada campo que cambió, y solo de esos")
        void guardaLasDosCarasDeCadaCampo() {
            CambiosDeLaVacante cambios = comparar(
                    comoEsta("horario", "Turnos rotativos", "descripcion", "Otra descripción"));

            assertThat(cambios.anterior())
                    .containsEntry("horario", "L-V de 9 a 6")
                    .containsEntry("descripcion", "Lleva la operación de la sede")
                    .hasSize(2);
            assertThat(cambios.nuevo())
                    .containsEntry("horario", "Turnos rotativos")
                    .containsEntry("descripcion", "Otra descripción")
                    .hasSize(2);
        }
    }
}
