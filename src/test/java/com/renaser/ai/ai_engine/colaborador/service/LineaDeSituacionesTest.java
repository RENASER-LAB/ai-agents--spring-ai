package com.renaser.ai.ai_engine.colaborador.service;

import com.renaser.ai.ai_engine.colaborador.entity.SituacionLaboral;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("La línea de tiempo de un periodo: qué situación rige cada día")
class LineaDeSituacionesTest {

    private static final LocalDate HOY = LocalDate.now();

    private static SituacionLaboral situacion(long id, LocalDate desde, String motivo) {
        return SituacionLaboral.builder().id(id).vigenteDesde(desde).tipoMotivo(motivo).build();
    }

    @Test
    @DisplayName("cada una rige hasta el día antes de la siguiente, y la última hasta el cese")
    void losTramos() {
        List<SituacionLaboral> todas = new ArrayList<>(List.of(
                situacion(1, HOY.minusMonths(6), "INGRESO"),
                situacion(2, HOY.minusMonths(1), "PROMOCION")));
        LineaDeSituaciones.recalcular(todas, HOY.plusDays(10));
        assertThat(todas.get(0).getVigenteHasta()).isEqualTo(HOY.minusMonths(1).minusDays(1));
        assertThat(todas.get(1).getVigenteHasta()).isEqualTo(HOY.plusDays(10));
        assertThat(LineaDeSituaciones.vigente(todas, HOY)).get().extracting(SituacionLaboral::getId).isEqualTo(2L);
    }

    @Test
    @DisplayName("un cambio el mismo día que el ingreso lo sustituye sin borrarlo del historial")
    void elMismoDia() {
        List<SituacionLaboral> todas = new ArrayList<>(List.of(
                situacion(1, HOY, "INGRESO"),
                situacion(2, HOY, "CORRECCION")));
        LineaDeSituaciones.recalcular(todas, null);
        assertThat(todas.get(0).esTramoVacio()).isTrue();
        assertThat(LineaDeSituaciones.vivas(todas)).hasSize(2);
        assertThat(LineaDeSituaciones.vigente(todas, HOY)).get().extracting(SituacionLaboral::getId).isEqualTo(2L);
    }

    @Test
    @DisplayName("un cambio programado no rige hasta su día; uno anulado no rige nunca")
    void programadoYAnulado() {
        SituacionLaboral anulada = situacion(3, HOY.plusDays(20), "TRASLADO");
        anulada.setAnuladaEn(Instant.now());
        List<SituacionLaboral> todas = new ArrayList<>(List.of(
                situacion(1, HOY.minusYears(1), "INGRESO"),
                situacion(2, HOY.plusDays(5), "RENOVACION_CONTRATO"),
                anulada));
        LineaDeSituaciones.recalcular(todas, null);
        assertThat(LineaDeSituaciones.vigente(todas, HOY)).get().extracting(SituacionLaboral::getId).isEqualTo(1L);
        assertThat(LineaDeSituaciones.programadas(todas, HOY)).extracting(SituacionLaboral::getId).containsExactly(2L);
        assertThat(LineaDeSituaciones.vigente(todas, HOY.plusDays(5))).get()
                .extracting(SituacionLaboral::getId).isEqualTo(2L);
        assertThat(todas.get(0).getVigenteHasta()).isEqualTo(HOY.plusDays(4));
        assertThat(anulada.getVigenteHasta()).isNull();
    }

    private static SituacionLaboral conPuesto(long id, LocalDate desde, long cargo, String sueldo) {
        return SituacionLaboral.builder().id(id).vigenteDesde(desde).tipoMotivo("OTRO").sedeId(1L).areaId(2L)
                .puestoId(cargo).tipoContrato("01").regimenLaboral("01")
                .sueldoBase(sueldo == null ? null : new java.math.BigDecimal(sueldo))
                .moneda(sueldo == null ? null : "PEN").build();
    }

    @Test
    @DisplayName("un cambio de solo sueldo se reconoce contra la viva anterior, aunque haya una anulada en medio")
    void soloSueldo() {
        SituacionLaboral ingreso = conPuesto(1, HOY.minusYears(1), 10, "4000");
        SituacionLaboral anulada = conPuesto(2, HOY.plusDays(5), 20, "4000");
        anulada.setAnuladaEn(Instant.now());
        SituacionLaboral ajuste = conPuesto(3, HOY.plusDays(20), 10, "4600.00");
        SituacionLaboral promocion = conPuesto(4, HOY.plusDays(30), 20, "4600");
        List<SituacionLaboral> todas = List.of(ingreso, anulada, ajuste, promocion);

        assertThat(LineaDeSituaciones.anteriorViva(todas, ajuste)).isSameAs(ingreso);
        assertThat(LineaDeSituaciones.anteriorViva(todas, anulada)).isSameAs(ingreso);
        assertThat(LineaDeSituaciones.anteriorViva(todas, ingreso)).isNull();
        assertThat(LineaDeSituaciones.soloTocaElSueldo(todas, ajuste)).isTrue();
        assertThat(LineaDeSituaciones.soloTocaElSueldo(todas, promocion)).isFalse();
        // La primera no es un cambio: nunca se oculta.
        assertThat(LineaDeSituaciones.soloTocaElSueldo(todas, ingreso)).isFalse();
        // Mismo importe con otra escala: no cambia nada.
        assertThat(LineaDeSituaciones.soloCambiaElSueldo(conPuesto(5, HOY, 10, "4600"), ajuste)).isFalse();
        // De no tener sueldo a tenerlo también es solo sueldo.
        assertThat(LineaDeSituaciones.soloCambiaElSueldo(conPuesto(6, HOY, 10, null), ajuste)).isTrue();
    }

    @Test
    @DisplayName("para quien no ve sueldos, la última se salta los ajustes de solo sueldo, rijan ya o todavía no")
    void ultimaSinSueldo() {
        SituacionLaboral ingreso = conPuesto(1, HOY.minusYears(1), 10, "4000");
        SituacionLaboral yaRige = conPuesto(2, HOY.minusDays(3), 10, "4300");
        SituacionLaboral programado = conPuesto(3, HOY.plusDays(20), 10, "4600");
        List<SituacionLaboral> todas = List.of(ingreso, yaRige, programado);

        assertThat(LineaDeSituaciones.ultima(todas)).get().isSameAs(programado);
        // El que ya rige tampoco existe para él: su fecha delataría el ajuste (§D.16).
        assertThat(LineaDeSituaciones.ultimaSinSueldo(todas)).get().isSameAs(ingreso);
        // Un cambio programado que toca algo más sí cuenta.
        SituacionLaboral traslado = conPuesto(4, HOY.plusDays(40), 20, "4600");
        assertThat(LineaDeSituaciones.ultimaSinSueldo(List.of(ingreso, programado, traslado))).get()
                .isSameAs(traslado);
        // Quien todavía no entra conserva su situación de entrada.
        SituacionLaboral entrada = conPuesto(5, HOY.plusDays(7), 10, "4000");
        assertThat(LineaDeSituaciones.ultimaSinSueldo(List.of(entrada, conPuesto(6, HOY.plusDays(30), 10, "5000"))))
                .get().isSameAs(entrada);
    }

    @Test
    @DisplayName("para quien no ve sueldos, la vigente es el último cambio que ve, y rige hasta el siguiente que ve")
    void vigenteSinSueldo() {
        SituacionLaboral ingreso = conPuesto(1, HOY.minusYears(1), 10, "4000");
        SituacionLaboral ajuste = conPuesto(2, HOY.minusDays(10), 10, "4300");
        SituacionLaboral otroAjuste = conPuesto(3, HOY.minusDays(3), 10, "4400");
        SituacionLaboral programado = conPuesto(4, HOY.plusDays(20), 10, "4600");
        SituacionLaboral traslado = conPuesto(5, HOY.plusDays(40), 20, "4600");
        List<SituacionLaboral> todas = new ArrayList<>(List.of(ingreso, ajuste, otroAjuste, programado, traslado));
        LineaDeSituaciones.recalcular(todas, null);

        assertThat(LineaDeSituaciones.vigente(todas, HOY)).get().isSameAs(otroAjuste);
        // Dos ajustes seguidos se saltan juntos: se ve el ingreso, con su fecha y su motivo.
        assertThat(LineaDeSituaciones.visibleSinSueldo(todas, otroAjuste)).isSameAs(ingreso);
        assertThat(LineaDeSituaciones.visibleSinSueldo(todas, traslado)).isSameAs(traslado);
        // El «hasta» guardado del ingreso acaba el día antes del primer ajuste; el que ve él, el día
        // antes del traslado.
        assertThat(ingreso.getVigenteHasta()).isEqualTo(HOY.minusDays(11));
        assertThat(LineaDeSituaciones.hastaSinSueldo(todas, ingreso, null)).isEqualTo(HOY.plusDays(39));
        assertThat(LineaDeSituaciones.hastaSinSueldo(todas, traslado, HOY.plusDays(90))).isEqualTo(HOY.plusDays(90));
        // Sin nada que vea detrás, rige hasta el cese.
        List<SituacionLaboral> sinTraslado = List.of(ingreso, ajuste, programado);
        assertThat(LineaDeSituaciones.hastaSinSueldo(sinTraslado, ingreso, HOY.plusDays(60))).isEqualTo(HOY.plusDays(60));
        assertThat(LineaDeSituaciones.hastaSinSueldo(sinTraslado, ingreso, null)).isNull();
    }

    @Test
    @DisplayName("un cambio parte de la que rige su día, contando la del mismo día; copiar el puesto no toca el sueldo")
    void deLaQueParte() {
        SituacionLaboral ingreso = conPuesto(1, HOY.minusYears(1), 10, "4000");
        SituacionLaboral ajuste = conPuesto(2, HOY.plusDays(20), 10, "4600");
        List<SituacionLaboral> todas = List.of(ingreso, ajuste);
        assertThat(LineaDeSituaciones.deLaQueParte(todas, HOY.plusDays(10))).get().isSameAs(ingreso);
        assertThat(LineaDeSituaciones.deLaQueParte(todas, HOY.plusDays(20))).get().isSameAs(ajuste);
        assertThat(LineaDeSituaciones.deLaQueParte(todas, HOY.minusYears(2))).isEmpty();

        SituacionLaboral traslado = conPuesto(3, HOY.plusDays(10), 30, "4000");
        traslado.setSedeId(9L);
        traslado.setJefeColaboradorId(77L);
        LineaDeSituaciones.copiarPuesto(traslado, ajuste);
        assertThat(ajuste.getPuestoId()).isEqualTo(30L);
        assertThat(ajuste.getSedeId()).isEqualTo(9L);
        assertThat(ajuste.getJefeColaboradorId()).isEqualTo(77L);
        assertThat(ajuste.getSueldoBase()).isEqualByComparingTo("4600");
        assertThat(LineaDeSituaciones.soloCambiaElSueldo(traslado, ajuste)).isTrue();
    }

    @Test
    @DisplayName("antes de entrar se enseña la situación con la que entra, y no cuenta como programada")
    void porIngresar() {
        List<SituacionLaboral> todas = new ArrayList<>(List.of(
                situacion(1, HOY.plusDays(7), "INGRESO"),
                situacion(2, HOY.plusDays(30), "PROMOCION")));
        LineaDeSituaciones.recalcular(todas, null);
        assertThat(LineaDeSituaciones.vigente(todas, HOY)).get().extracting(SituacionLaboral::getId).isEqualTo(1L);
        assertThat(LineaDeSituaciones.programadas(todas, HOY)).extracting(SituacionLaboral::getId).containsExactly(2L);
        assertThat(LineaDeSituaciones.ultima(todas)).get().extracting(SituacionLaboral::getId).isEqualTo(2L);
    }

    // ============ Lo que cambiaba un cambio anulado (QA-PER-10) ============

    private static SituacionLaboral enArea(long id, LocalDate desde, long area, long cargo, String sueldo) {
        SituacionLaboral s = conPuesto(id, desde, cargo, sueldo);
        s.setAreaId(area);
        return s;
    }

    private static void anular(List<SituacionLaboral> todas, SituacionLaboral... porAnular) {
        LineaDeSituaciones.guardarLoDeAntes(todas, List.of(porAnular));
        for (SituacionLaboral s : porAnular) {
            s.setAnuladaEn(Instant.now());
        }
    }

    @Test
    @DisplayName("un anulado se mide contra lo que tenía detrás al anularse, aunque después se registre otro antes de su fecha")
    void elAnuladoNoSeMideContraLoPosterior() {
        SituacionLaboral ingreso = enArea(1, HOY.minusDays(60), 2, 10, "4100");
        SituacionLaboral traslado = enArea(2, HOY.plusDays(15), 3, 10, "4100");
        List<SituacionLaboral> todas = new ArrayList<>(List.of(ingreso, traslado));
        anular(todas, traslado);

        SituacionLaboral promocion = enArea(3, HOY, 2, 20, "5000");
        todas.add(promocion);
        // La viva que hoy le queda delante es la promoción: medir contra ella le atribuía el cargo y
        // el sueldo de la promoción.
        assertThat(LineaDeSituaciones.anteriorViva(todas, traslado)).isSameAs(promocion);

        SituacionLaboral frente = LineaDeSituaciones.frenteA(todas, traslado);
        assertThat(frente.getAreaId()).isEqualTo(2L);
        assertThat(frente.getPuestoId()).isEqualTo(10L);
        assertThat(frente.getSueldoBase()).isEqualByComparingTo("4100");
        assertThat(LineaDeSituaciones.soloTocaElSueldo(todas, traslado)).isFalse();
        // Las vivas se siguen midiendo contra la viva anterior.
        assertThat(LineaDeSituaciones.frenteA(todas, promocion)).isSameAs(ingreso);
    }

    @Test
    @DisplayName("dos que se anulan juntas, como al registrar un cese, se miden como estaban una detrás de otra")
    void dosAnuladasJuntas() {
        SituacionLaboral ingreso = enArea(1, HOY.minusDays(60), 2, 10, "4000");
        SituacionLaboral traslado = enArea(2, HOY.plusDays(10), 3, 10, "4000");
        SituacionLaboral promocion = enArea(3, HOY.plusDays(20), 3, 20, "4000");
        List<SituacionLaboral> todas = List.of(ingreso, traslado, promocion);
        anular(todas, traslado, promocion);

        assertThat(LineaDeSituaciones.frenteA(todas, traslado).getAreaId()).isEqualTo(2L);
        SituacionLaboral detrasDeLaPromocion = LineaDeSituaciones.frenteA(todas, promocion);
        assertThat(detrasDeLaPromocion.getAreaId()).isEqualTo(3L);
        assertThat(detrasDeLaPromocion.getPuestoId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("si lo de detrás se arrastra después, el anulado conserva lo que tenía, y uno de solo sueldo lo sigue siendo")
    void loDeDetrasSeArrastraDespues() {
        SituacionLaboral ingreso = enArea(1, HOY.minusDays(60), 2, 10, "4000");
        SituacionLaboral ajuste = enArea(2, HOY.plusDays(10), 2, 10, "4600");
        SituacionLaboral traslado = enArea(3, HOY.plusDays(20), 3, 10, "4600");
        SituacionLaboral otroAjuste = enArea(4, HOY.plusDays(30), 3, 10, "4800");
        List<SituacionLaboral> todas = new ArrayList<>(List.of(ingreso, ajuste, traslado, otroAjuste));
        anular(todas, otroAjuste);
        anular(todas, traslado);

        // Un cambio anterior al ajuste lo arrastra: su fila ya no dice lo que decía.
        SituacionLaboral promocion = enArea(5, HOY.plusDays(5), 2, 30, "4000");
        todas.add(promocion);
        LineaDeSituaciones.copiarPuesto(promocion, ajuste);

        SituacionLaboral frente = LineaDeSituaciones.frenteA(todas, traslado);
        assertThat(frente.getPuestoId()).isEqualTo(10L);
        assertThat(frente.getAreaId()).isEqualTo(2L);
        assertThat(LineaDeSituaciones.soloTocaElSueldo(todas, otroAjuste)).isTrue();
        assertThat(LineaDeSituaciones.frenteA(todas, otroAjuste).getSueldoBase()).isEqualByComparingTo("4600");
    }

    @Test
    @DisplayName("un anulado sin copia de lo de detrás se sigue midiendo contra la viva anterior")
    void unAnuladoSinCopia() {
        SituacionLaboral ingreso = enArea(1, HOY.minusDays(60), 2, 10, "4000");
        SituacionLaboral traslado = enArea(2, HOY.plusDays(15), 3, 10, "4000");
        traslado.setAnuladaEn(Instant.now());
        List<SituacionLaboral> todas = List.of(ingreso, traslado);
        assertThat(traslado.getAntesAlAnular()).isNull();
        assertThat(LineaDeSituaciones.frenteA(todas, traslado)).isSameAs(ingreso);
    }
}
