package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fecha en que la prueba se cierra para todos (V68): la ponen la configuración del editor
 * y «Plazos de la prueba», y las dos la mueven igual a los que ya están dentro.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("La fecha límite de la vacante y los que ya están dentro")
class FechaLimiteDeLaVacanteTest {

    private static final long VACANTE = 40L;

    @Mock private VacanteRepository vacantes;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private VersionPlantillaPruebaRepository versionesPrueba;
    @Mock private VersionBancoRepository versionesBanco;
    @InjectMocks private FechaLimiteDeLaVacante fecha;

    private final Vacante vacante = Vacante.builder().id(VACANTE).build();
    private final Instant empezo = Instant.now().minus(1, ChronoUnit.HOURS);

    @Test
    @DisplayName("Se guarda en la vacante y mueve a los abiertos; a quien tiene plazo propio, no (AC-10)")
    void mueveALosAbiertosSinPlazoPropio() {
        Instant viernes = Instant.now().plus(5, ChronoUnit.DAYS);
        IntentoPrueba sinPropio = IntentoPrueba.builder().id(1L).plazoPropio(false).build();
        Instant suya = viernes.plus(2, ChronoUnit.DAYS);
        IntentoPrueba conPropio = IntentoPrueba.builder().id(2L).plazoPropio(true).venceEn(suya).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(sinPropio, conPropio));

        FechaLimiteDeLaVacante.Movidos movidos = fecha.fijar(vacante, viernes);

        assertThat(vacante.getPruebaCierraEn()).isEqualTo(viernes);
        verify(vacantes).save(vacante);
        assertThat(sinPropio.getVenceEn()).isEqualTo(viernes);
        assertThat(conPropio.getVenceEn()).isEqualTo(suya);
        assertThat(movidos).isEqualTo(new FechaLimiteDeLaVacante.Movidos(1, 1));
        verify(intentos, never()).save(conPropio);
    }

    @Test
    @DisplayName("Alargarla no alarga el reloj de quien ya empezó una cronometrada; sin cronómetro, a la nueva fecha")
    void alargarlaNoAlargaElReloj() {
        Instant nueva = Instant.now().plus(30, ChronoUnit.DAYS);
        // Empezó hace una hora con 90 minutos: le quedan 30, y así se queda.
        IntentoPrueba enCurso = IntentoPrueba.builder().id(1L).plazoPropio(false).versionBancoId(80L)
                .iniciadoEn(empezo).venceEn(empezo.plus(90, ChronoUnit.MINUTES)).build();
        // Se le acabó el reloj y el barrido aún no pasó: mover la fecha no lo resucita.
        Instant hace3Horas = Instant.now().minus(3, ChronoUnit.HOURS);
        IntentoPrueba sinReloj = IntentoPrueba.builder().id(2L).plazoPropio(false).versionBancoId(80L)
                .iniciadoEn(hace3Horas).venceEn(hace3Horas.plus(90, ChronoUnit.MINUTES)).build();
        IntentoPrueba sinEmpezar = IntentoPrueba.builder().id(3L).plazoPropio(false).versionBancoId(80L)
                .venceEn(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        IntentoPrueba sinCronometro = IntentoPrueba.builder().id(4L).plazoPropio(false).versionBancoId(82L)
                .iniciadoEn(empezo).venceEn(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        // Una de antes de la V68, con días: con fecha, los días no cuentan.
        IntentoPrueba conDias = IntentoPrueba.builder().id(5L).plazoPropio(false).versionBancoId(81L)
                .iniciadoEn(empezo).venceEn(empezo.plus(3, ChronoUnit.DAYS)).build();
        IntentoPrueba sinVersion = IntentoPrueba.builder().id(6L).plazoPropio(false).versionBancoId(99L)
                .iniciadoEn(empezo).venceEn(empezo.plus(90, ChronoUnit.MINUTES)).build();
        // La plantilla sigue como hoy: a la nueva fecha.
        IntentoPrueba deUnaPlantilla = IntentoPrueba.builder().id(7L).plazoPropio(false)
                .versionPlantillaPruebaId(31L).iniciadoEn(empezo).venceEn(empezo.plus(90, ChronoUnit.MINUTES))
                .build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(enCurso, sinReloj, sinEmpezar,
                sinCronometro, conDias, sinVersion, deUnaPlantilla));
        when(versionesBanco.findById(80L)).thenReturn(Optional.of(VersionBanco.builder().id(80L)
                .modalidad("CRONOMETRADA").duracionMinutos(90).build()));
        when(versionesBanco.findById(81L)).thenReturn(Optional.of(VersionBanco.builder().id(81L)
                .modalidad("PLAZO_ABIERTO").plazoDias(3).build()));
        when(versionesBanco.findById(82L)).thenReturn(Optional.of(VersionBanco.builder().id(82L)
                .modalidad("PLAZO_ABIERTO").build()));
        when(versionesBanco.findById(99L)).thenReturn(Optional.empty());
        when(versionesPrueba.findById(31L)).thenReturn(Optional.of(VersionPlantillaPrueba.builder().id(31L)
                .modalidad("CRONOMETRADA").duracionMinutos(90).build()));

        FechaLimiteDeLaVacante.Movidos movidos = fecha.fijar(vacante, nueva);

        assertThat(enCurso.getVenceEn()).isEqualTo(empezo.plus(90, ChronoUnit.MINUTES));
        assertThat(sinReloj.getVenceEn()).isEqualTo(hace3Horas.plus(90, ChronoUnit.MINUTES));
        assertThat(sinEmpezar.getVenceEn()).as("su reloj se calcula al empezar").isEqualTo(nueva);
        assertThat(sinCronometro.getVenceEn()).isEqualTo(nueva);
        assertThat(conDias.getVenceEn()).isEqualTo(nueva);
        assertThat(sinVersion.getVenceEn()).isEqualTo(nueva);
        assertThat(deUnaPlantilla.getVenceEn()).isEqualTo(nueva);
        assertThat(movidos).isEqualTo(new FechaLimiteDeLaVacante.Movidos(7, 0));
    }

    @Test
    @DisplayName("Acortarla antes de que se acabe el reloj corta la cronometrada en la nueva fecha")
    void acortarlaCortaElReloj() {
        Instant pronto = Instant.now().plus(10, ChronoUnit.MINUTES);
        IntentoPrueba enCurso = IntentoPrueba.builder().id(1L).plazoPropio(false).versionBancoId(80L)
                .iniciadoEn(empezo).venceEn(empezo.plus(90, ChronoUnit.MINUTES)).build();
        Instant suya = Instant.now().plus(2, ChronoUnit.DAYS);
        IntentoPrueba conPropio = IntentoPrueba.builder().id(2L).plazoPropio(true).versionBancoId(80L)
                .iniciadoEn(empezo).venceEn(suya).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(enCurso, conPropio));
        when(versionesBanco.findById(80L)).thenReturn(Optional.of(VersionBanco.builder().id(80L)
                .modalidad("CRONOMETRADA").duracionMinutos(90).build()));

        FechaLimiteDeLaVacante.Movidos movidos = fecha.fijar(vacante, pronto);

        assertThat(enCurso.getVenceEn()).isEqualTo(pronto);
        assertThat(conPropio.getVenceEn()).isEqualTo(suya);
        assertThat(movidos).isEqualTo(new FechaLimiteDeLaVacante.Movidos(1, 1));
    }

    @Test
    @DisplayName("Quitarla deja vacío a quien no empezó y devuelve su reloj a quien ya está dentro")
    void quitarlaDevuelveElRelojDeCadaUno() {
        IntentoPrueba sinEmpezar = IntentoPrueba.builder().id(1L).plazoPropio(false).venceEn(Instant.now()).build();
        IntentoPrueba cronometrada = IntentoPrueba.builder().id(2L).plazoPropio(false).versionBancoId(80L)
                .iniciadoEn(empezo).build();
        IntentoPrueba conDias = IntentoPrueba.builder().id(3L).plazoPropio(false).versionBancoId(81L)
                .iniciadoEn(empezo).build();
        Instant laQueTenia = Instant.now().plus(1, ChronoUnit.DAYS);
        IntentoPrueba sinCronometro = IntentoPrueba.builder().id(4L).plazoPropio(false).versionBancoId(82L)
                .iniciadoEn(empezo).venceEn(laQueTenia).build();
        IntentoPrueba deUnaPlantilla = IntentoPrueba.builder().id(5L).plazoPropio(false)
                .versionPlantillaPruebaId(31L).iniciadoEn(empezo).build();
        IntentoPrueba plantillaPerdida = IntentoPrueba.builder().id(6L).plazoPropio(false)
                .versionPlantillaPruebaId(32L).iniciadoEn(empezo).venceEn(laQueTenia).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(sinEmpezar, cronometrada, conDias,
                sinCronometro, deUnaPlantilla, plantillaPerdida));
        when(versionesBanco.findById(80L)).thenReturn(Optional.of(VersionBanco.builder().id(80L)
                .modalidad("CRONOMETRADA").duracionMinutos(90).build()));
        when(versionesBanco.findById(81L)).thenReturn(Optional.of(VersionBanco.builder().id(81L)
                .modalidad("PLAZO_ABIERTO").plazoDias(3).build()));
        when(versionesBanco.findById(82L)).thenReturn(Optional.of(VersionBanco.builder().id(82L)
                .modalidad("PLAZO_ABIERTO").build()));
        when(versionesPrueba.findById(31L)).thenReturn(Optional.of(VersionPlantillaPrueba.builder().id(31L)
                .modalidad("PLAZO_ABIERTO").plazoDias(7).build()));
        when(versionesPrueba.findById(32L)).thenReturn(Optional.empty());

        FechaLimiteDeLaVacante.Movidos movidos = fecha.fijar(vacante, null);

        assertThat(sinEmpezar.getVenceEn()).isNull();
        assertThat(cronometrada.getVenceEn()).isEqualTo(empezo.plus(90, ChronoUnit.MINUTES));
        assertThat(conDias.getVenceEn()).isEqualTo(empezo.plus(3, ChronoUnit.DAYS));
        assertThat(sinCronometro.getVenceEn()).as("sin días ni minutos se le deja la que tenía").isEqualTo(laQueTenia);
        assertThat(deUnaPlantilla.getVenceEn()).isEqualTo(empezo.plus(7, ChronoUnit.DAYS));
        assertThat(plantillaPerdida.getVenceEn()).isEqualTo(laQueTenia);
        assertThat(movidos.movidos()).isEqualTo(6);
    }
}
