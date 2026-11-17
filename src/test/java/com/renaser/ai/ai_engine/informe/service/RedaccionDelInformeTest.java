package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PreguntaAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.Insumo;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.Respuesta;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.Textos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que el código comprueba de los textos del redactor antes de guardarlos (AC-01j): lo que no
 * pasa se descarta y se usa la explicación de la IA. Ninguna nota cambia: aquí solo hay frases.
 */
@DisplayName("Los textos del redactor del informe")
class RedaccionDelInformeTest {

    private static final String DIAGNOSTICO = "Diagnóstico del problema";
    private static final String PRESUPUESTO = "Presupuesto del plan";

    private final Informe base = InformesDePrueba.prueba(List.of(
            new PreguntaAnexo(1, "¿Dónde está el problema?", "Abierta",
                    "El 70 % de las salidas pasa en los 3 primeros meses, sobre todo en Ate.", null, null),
            new PreguntaAnexo(2, "Calcula el costo mensual de la planilla", "Abierta",
                    "Sueldo, gratificación, CTS y EsSalud: S/ 31 400 al mes.", null, null)));
    private final Insumo insumo = RedaccionDelInforme.insumo(base);

    @Test
    @DisplayName("AC-01j · guarda el resumen, una línea por criterio y una por respuesta cuando todo cuadra")
    void guardaLoQueCuadra() {
        Textos t = RedaccionDelInforme.validar(insumo, respuesta(
                "Entregó el plan completo y ubicó la rotación en los 3 primeros meses.",
                Map.of(DIAGNOSTICO, "Sobre el diagnóstico: ubicó la rotación en los 3 primeros meses con los datos del caso.",
                        PRESUPUESTO, "Sobre el presupuesto: se calificó solo con el resumen porque la hoja no abrió."),
                "Sobre el problema de rotación: el 70 % de las salidas ocurre en Ate en los primeros meses.",
                "Sobre la planilla: suma sueldo, gratificación, CTS y EsSalud y llega a S/ 31.400 al mes."));

        assertThat(t.resumen()).startsWith("Entregó el plan completo");
        assertThat(t.criterios()).containsOnlyKeys(DIAGNOSTICO, PRESUPUESTO);
        assertThat(t.preguntas()).doesNotContainNull().hasSize(2);
        assertThat(t.descartados()).isZero();
    }

    @Test
    @DisplayName("AC-01j · una línea con una cifra que no está en los datos se descarta y queda la explicación")
    void cifraInventada() {
        Textos t = RedaccionDelInforme.validar(insumo, respuesta(null,
                Map.of(DIAGNOSTICO, "Sobre el diagnóstico: detectó que el 45 % de la gente se va en Ate."),
                "Sobre el problema: ubica la salida en los 3 primeros meses.",
                "Sobre la planilla: calcula S/ 52 000 al mes."));

        assertThat(t.criterios()).doesNotContainKey(DIAGNOSTICO);
        assertThat(t.preguntas()).containsExactly("Sobre el problema: ubica la salida en los 3 primeros meses.", null);

        Informe aplicado = RedaccionDelInforme.aplicar(base, t);
        Criterio diagnostico = aplicado.evaluado().criterios().get(0);
        // Ninguna nota cambia, y bajo la barra queda la explicación de la IA.
        assertThat(diagnostico.nota()).isEqualByComparingTo(base.evaluado().criterios().get(0).nota());
        assertThat(diagnostico.texto()).isEqualTo(diagnostico.explicacion());
        assertThat(aplicado.anexo().preguntas().get(0).loQueDemostro())
                .isEqualTo("Sobre el problema: ubica la salida en los 3 primeros meses.");
        assertThat(aplicado.anexo().preguntas().get(1).loQueDemostro()).isNull();
    }

    @Test
    @DisplayName("Se descarta la línea que repite la nota, la que trae «…» y la que nombra a alguien que no está en los datos")
    void otrasReglas() {
        Textos t = RedaccionDelInforme.validar(insumo, respuesta(
                "Sacó 78 de 100 en la prueba.",
                Map.of(DIAGNOSTICO, "Su nota de 23 en diagnóstico es buena.",
                        PRESUPUESTO, "Sobre el presupuesto: según Rodríguez, la hoja no abrió."),
                "Sobre el problema: ubica la salida…",
                "Sobre la planilla: suma los conceptos de ley."));
        assertThat(t.resumen()).isNull();
        assertThat(t.criterios()).isEmpty();
        assertThat(t.preguntas()).containsExactly(null, "Sobre la planilla: suma los conceptos de ley.");
    }

    @Test
    @DisplayName("Una línea con la marca [DATO NO UTILIZABLE] se descarta: habla de un dato que la IA no vio")
    void laMarcaDelAnonimizador() {
        Textos t = RedaccionDelInforme.validar(insumo, respuesta(
                "Tiene más de [DATO NO UTILIZABLE] de experiencia en Recursos Humanos.",
                Map.of(DIAGNOSTICO, "Sobre el diagnóstico: con [DATO NO UTILIZABLE] en el sector, ubicó la rotación."),
                "Sobre el problema: ubica la salida en los 3 primeros meses.",
                "Sobre la planilla: suma los conceptos de ley."));

        assertThat(t.resumen()).isNull();
        assertThat(t.criterios()).doesNotContainKey(DIAGNOSTICO);
        assertThat(t.preguntas()).doesNotContainNull();
        assertThat(t.descartados()).isEqualTo(2);
    }

    @Test
    @DisplayName("Si no trae una línea por respuesta, o las claves no son los criterios reales, eso se descarta")
    void cuentasYClaves() {
        Map<String, String> criterios = new LinkedHashMap<>();
        criterios.put("Diagnostico inventado", "Una línea cualquiera sin cifras.");
        Textos t = RedaccionDelInforme.validar(insumo, new Respuesta("Resumen sin cifras.", criterios,
                List.of("Solo una línea.")));
        assertThat(t.criterios()).isEmpty();
        assertThat(t.preguntas()).containsExactly(null, null);
        assertThat(t.descartados()).isEqualTo(2);
    }

    @Test
    @DisplayName("La huella cambia si cambia una nota o una explicación: un texto viejo no acompaña a una nota nueva")
    void laHuellaSigueALosDatos() {
        String antes = RedaccionDelInforme.huella(insumo);
        Informe otraNota = InformesDePrueba.prueba(List.of());
        assertThat(RedaccionDelInforme.huella(RedaccionDelInforme.insumo(otraNota))).isNotEqualTo(antes);
        assertThat(RedaccionDelInforme.huella(RedaccionDelInforme.insumo(base))).isEqualTo(antes);
    }

    @Test
    @DisplayName("«Qué buscar en el CV» no le llega al redactor: de los agentes solo lo ve la búsqueda")
    void loBuscadoNoViajaAlRedactor() {
        Informe cv = InformesDePrueba.cv(1);
        Informe conLoBuscado = InformesDePrueba.conBloque(cv, new Bloque(ArmadoDelCv.BLOQUE_LO_BUSCADO,
                "Qué buscar en el CV", "NEUTRO", "Sin datos", List.of(
                        new Punto("Controla costos y mermas XQ7", "«Bajó la merma del 9 % al 4 %»", null, "Lo tiene", null),
                        new Punto("Certificado BPM vigente", "¿Tienes el certificado BPM?", null, "No aparece", null))));

        Insumo insumo = RedaccionDelInforme.insumo(conLoBuscado);

        assertThat(insumo.hallazgos()).extracting(RedaccionDelInforme.PuntoInsumo::bloque)
                .doesNotContain("Qué buscar en el CV")
                .contains("Lo que el CV demuestra", "No cuadra");
        assertThat(insumo.hallazgos()).extracting(RedaccionDelInforme.PuntoInsumo::texto)
                .noneMatch(t -> t.contains("XQ7") || t.contains("BPM"));
        // Lo demás llega igual, y la huella no cambia por un bloque que no se manda
        assertThat(insumo.criterios()).hasSameSizeAs(RedaccionDelInforme.insumo(cv).criterios());
        assertThat(RedaccionDelInforme.huella(insumo))
                .isEqualTo(RedaccionDelInforme.huella(RedaccionDelInforme.insumo(cv)));
    }

    @Test
    @DisplayName("Las cifras se comparan sin separadores: «S/ 31.400» es la cifra 31400")
    void cifrasSinSeparadores() {
        assertThat(RedaccionDelInforme.cifras("S/ 31.400 y 2,5 y 70 %")).containsExactlyInAnyOrder("31400", "25", "70");
        assertThat(RedaccionDelInforme.traeUnNombreInventado("Sobre el caso: habló con Pérez.", "el caso"))
                .isTrue();
        assertThat(RedaccionDelInforme.traeUnNombreInventado("Trabajó en Ate. Luego mejoró.", "ate")).isFalse();
        assertThat(RedaccionDelInforme.traeUnNombreInventado("Analista de RR. HH. con foco.", "analista")).isFalse();
    }

    private static Respuesta respuesta(String resumen, Map<String, String> criterios, String... preguntas) {
        return new Respuesta(resumen, criterios, Arrays.asList(preguntas));
    }
}
