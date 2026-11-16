package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.ArchivoArmado;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las piezas pequeñas que comparten los tres informes: cómo se escriben las notas, fechas y
 * duraciones, los nombres de archivo, quién puso una nota, qué etapa pide una ruta y el archivo
 * armado que se descarga.
 */
@DisplayName("El formato y los nombres del informe")
class FormatoDelInformeTest {

    @Test
    @DisplayName("una nota que falta es un hueco, nunca un cero; la del anillo va entera")
    void notas() {
        assertThat(FormatoDelInforme.nota(null)).isEqualTo(FormatoDelInforme.HUECO);
        assertThat(FormatoDelInforme.nota(BigDecimal.valueOf(74.00))).isEqualTo("74");
        assertThat(FormatoDelInforme.notaRedonda(null)).isEqualTo(FormatoDelInforme.HUECO);
        assertThat(FormatoDelInforme.notaRedonda(new BigDecimal("74.5"))).isEqualTo("75");
    }

    @Test
    @DisplayName("la barra se llena de 0 a 100 y sin máximo, o sin nota, no se llena")
    void porcentaje() {
        BigDecimal diez = BigDecimal.TEN;
        assertThat(FormatoDelInforme.porcentaje(BigDecimal.valueOf(5), diez)).isEqualTo(50);
        assertThat(FormatoDelInforme.porcentaje(BigDecimal.valueOf(12), diez)).isEqualTo(100);
        assertThat(FormatoDelInforme.porcentaje(BigDecimal.valueOf(-1), diez)).isZero();
        assertThat(FormatoDelInforme.porcentaje(null, diez)).isNull();
        assertThat(FormatoDelInforme.porcentaje(diez, null)).isNull();
        assertThat(FormatoDelInforme.porcentaje(diez, BigDecimal.ZERO)).isNull();
    }

    @Test
    @DisplayName("las fechas van en hora de Lima y, sin fecha, con el hueco")
    void fechas() {
        Instant instante = Instant.parse("2026-11-01T03:30:00Z");
        assertThat(FormatoDelInforme.fecha(instante)).isEqualTo("31/10/2026");
        assertThat(FormatoDelInforme.fechaHora(instante)).isEqualTo("31/10/2026 · 22:30");
        assertThat(FormatoDelInforme.diaYHora(instante)).isEqualTo("31/10 a las 22:30");
        assertThat(FormatoDelInforme.fecha(null)).isEqualTo(FormatoDelInforme.HUECO);
        assertThat(FormatoDelInforme.fechaHora(null)).isEqualTo(FormatoDelInforme.HUECO);
        assertThat(FormatoDelInforme.diaYHora(null)).isEqualTo(FormatoDelInforme.HUECO);
    }

    @Test
    @DisplayName("las duraciones se dicen en años y meses, en singular cuando toca")
    void duraciones() {
        assertThat(FormatoDelInforme.duracion(null)).isNull();
        assertThat(FormatoDelInforme.duracion(1)).isEqualTo("1 mes");
        assertThat(FormatoDelInforme.duracion(8)).isEqualTo("8 meses");
        assertThat(FormatoDelInforme.duracion(12)).isEqualTo("1 año");
        assertThat(FormatoDelInforme.duracion(13)).isEqualTo("1 año y 1 mes");
        assertThat(FormatoDelInforme.duracion(50)).isEqualTo("4 años y 2 meses");
        assertThat(FormatoDelInforme.duracion(24)).isEqualTo("2 años");
    }

    @Test
    @DisplayName("el nombre de un archivo no lleva barras ni signos que el disco rechace, ni espacios dobles")
    void paraArchivo() {
        assertThat(FormatoDelInforme.paraArchivo(null)).isEmpty();
        assertThat(FormatoDelInforme.paraArchivo(" Ana/Torres: \"RR.HH.\" <jefa>|?*\t\\ "))
                .isEqualTo("Ana Torres RR.HH. jefa");
        assertThat(FormatoDelInforme.limpio("  ")).isNull();
        assertThat(FormatoDelInforme.limpio(" a ")).isEqualTo("a");
    }

    @Test
    @DisplayName("quién puso la nota: un ajuste a mano es de una persona; si no, según el origen")
    void quien() {
        assertThat(MotivosDelInforme.quien("AGENTE", true)).isEqualTo("Persona");
        assertThat(MotivosDelInforme.quien(null, false)).isNull();
        assertThat(MotivosDelInforme.quien("agente", false)).isEqualTo("IA");
        assertThat(MotivosDelInforme.quien("IA", false)).isEqualTo("IA");
        assertThat(MotivosDelInforme.quien("SISTEMA", false)).isEqualTo("Sistema");
        assertThat(MotivosDelInforme.quien("AUTOMATICA", false)).isEqualTo("Sistema");
        assertThat(MotivosDelInforme.quien("automatico", false)).isEqualTo("Sistema");
        assertThat(MotivosDelInforme.quien("HUMANO", false)).isEqualTo("Persona");
    }

    @Test
    @DisplayName("el nombre del PDF lleva la etapa, el candidato y, si la hay, la vacante")
    void nombreDeArchivo() {
        Base conVacante = base(Vacante.builder().titulo("Analista").build());
        Base sinVacante = base(null);
        assertThat(MotivosDelInforme.nombreDeArchivo(EtapaDelInforme.CV, conVacante))
                .isEqualTo("Informe CV - Ana Torres - Analista.pdf");
        assertThat(MotivosDelInforme.nombreDeArchivo(EtapaDelInforme.PRUEBA, sinVacante))
                .isEqualTo("Informe prueba - Ana Torres.pdf");
    }

    @Test
    @DisplayName("la etapa se pide por su ruta o por su nombre; cualquier otra cosa dice cuáles hay")
    void etapaDeLaRuta() {
        assertThat(EtapaDelInforme.de(" Evaluacion ")).isEqualTo(EtapaDelInforme.EVALUACION);
        assertThat(EtapaDelInforme.de("PRUEBA")).isEqualTo(EtapaDelInforme.PRUEBA);
        assertThat(EtapaDelInforme.CV.ruta()).isEqualTo("cv");
        assertThatThrownBy(() -> EtapaDelInforme.de("entrevista"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cv, evaluacion y prueba");
        assertThatThrownBy(() -> EtapaDelInforme.de(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dos archivos armados son iguales si lo son su nombre, su tipo y sus bytes; el texto no vuelca el contenido")
    void archivoArmado() {
        byte[] bytes = "%PDF".getBytes(StandardCharsets.UTF_8);
        ArchivoArmado uno = new ArchivoArmado("a.pdf", "application/pdf", bytes);
        ArchivoArmado igual = new ArchivoArmado("a.pdf", "application/pdf", bytes.clone());

        assertThat(uno).isEqualTo(igual).hasSameHashCodeAs(igual).isEqualTo(uno);
        assertThat(uno).isNotEqualTo(new ArchivoArmado("a.pdf", "application/pdf", new byte[] {1}));
        assertThat(uno).isNotEqualTo(new ArchivoArmado("b.pdf", "application/pdf", bytes));
        assertThat(uno).isNotEqualTo(new ArchivoArmado("a.pdf", "application/zip", bytes));
        Object otroTipo = "a.pdf";
        assertThat(uno.equals(otroTipo)).isFalse();
        assertThat(uno.toString()).isEqualTo("ArchivoArmado[nombre=a.pdf, tipo=application/pdf, contenido=4 bytes]");
        assertThat(new ArchivoArmado(null, null, null).toString()).contains("contenido=null");
        assertThat(new ArchivoArmado(null, null, null)).isEqualTo(new ArchivoArmado(null, null, null));
    }

    private static Base base(Vacante vacante) {
        return new Base(Postulacion.builder().id(1L).build(), vacante, null, null, "Ana Torres", null, null, null,
                List.of(), null, List.of(), List.of(), null);
    }
}
