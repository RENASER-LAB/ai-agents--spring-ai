package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoVacanteRepository;
import com.renaser.ai.ai_engine.notificacion.service.DireccionDelCandidato;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.service.AvisosPorWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.AvisosPorWhatsapp.DeLaTransicion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La máquina de estados le cuenta a WhatsApp lo que le toca (V73): los «te toca» con su
 * transición, el cierre del vínculo al terminar, y nada si se movió sin avisar (AC-08, AC-10).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("La máquina de estados y WhatsApp")
class MaquinaEstadosWhatsappTest {

    @Mock private EstadoPostulacionRepository estados;
    @Mock private PostulacionRepository postulaciones;
    @Mock private TransicionEstadoRepository transiciones;
    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;
    @Mock private VacanteRepository vacantes;
    @Mock private ServicioAuditoria auditoria;
    @Mock private ServicioCorreo correo;
    @Mock private DireccionDelCandidato direcciones;
    @Mock private ServicioEnlaceAcceso enlaces;
    @Mock private PlantillaCorreoVacanteRepository plantillasPorVacante;
    @Mock private VersionPlantillaPruebaRepository versionesDePrueba;
    @Mock private ServicioParametros parametros;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private FechaLimiteDeLaPersona fechasLimite;
    @Mock private AvisoDeEtapaEnLaCampana campana;
    @Mock private AvisosPorWhatsapp whatsapp;

    private MaquinaEstados maquina;

    @BeforeEach
    void montar() {
        maquina = new MaquinaEstados(estados, postulaciones, transiciones, usuarios, personas,
                vacantes, auditoria, correo, direcciones, enlaces, plantillasPorVacante,
                versionesDePrueba, parametros, versionesBanco, fechasLimite, campana, whatsapp);
        when(usuarios.findById(anyLong())).thenReturn(Optional.of(Usuario.builder().id(7L).personaId(7L).build()));
        when(personas.findById(anyLong())).thenReturn(Optional.of(Persona.builder().id(7L).nombre("Ana").build()));
        when(direcciones.de(any(), anyLong())).thenReturn("ana@ejemplo.test");
        when(enlaces.enlaceAlProceso(any())).thenReturn("https://portal.test/procesos/abc");
        when(parametros.texto(anyLong(), eq("whatsapp_evidencia"), anyString())).thenReturn("");
        when(vacantes.findById(40L)).thenReturn(Optional.of(Vacante.builder().id(40L)
                .titulo("Asistente Contable").instrumentoEtapaTecnica("PLANTILLA").build()));
        when(transiciones.save(any(TransicionEstado.class))).thenAnswer(i -> {
            TransicionEstado t = i.getArgument(0);
            t.setId(900L);
            return t;
        });
        when(estados.findById("PERFIL_POR_CONFIRMAR")).thenReturn(Optional.of(estado(
                "PERFIL_POR_CONFIRMAR", "PERFIL_INTEGRAL", "TALENTO", false)));
        when(estados.findById("PRUEBA_TURNO_CANDIDATO")).thenReturn(Optional.of(estado(
                "PRUEBA_TURNO_CANDIDATO", "PRUEBA_PUESTO", "CANDIDATO", false)));
        when(estados.findById("NO_CONTINUA")).thenReturn(Optional.of(estado("NO_CONTINUA", null, "NADIE", true)));
    }

    private static EstadoPostulacion estado(String codigo, String etapa, String esperaA, boolean fin) {
        return EstadoPostulacion.builder().codigo(codigo).nombre(codigo).etapaCodigo(etapa)
                .esperaA(esperaA).esFinal(fin).build();
    }

    private static Postulacion postulacionEn(String estado) {
        return Postulacion.builder().id(500L).organizacionId(1L).usuarioId(7L).vacanteId(40L)
                .uuid(UUID.randomUUID()).estadoCodigo(estado).build();
    }

    @Test
    @DisplayName("AC-08 · pasar a la prueba le pasa a WhatsApp el aviso con su transición, su nombre y su vacante")
    void laPruebaVaAWhatsapp() {
        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, "Pase automático", true, false, null);

        ArgumentCaptor<DeLaTransicion> contexto = ArgumentCaptor.forClass(DeLaTransicion.class);
        verify(whatsapp).alAvisar(any(Postulacion.class), eq("PRUEBA_DISPONIBLE"),
                any(EstadoPostulacion.class), contexto.capture());
        assertThat(contexto.getValue()).isEqualTo(
                new DeLaTransicion(900L, "Ana", "Asistente Contable", "PLANTILLA"));
        verify(whatsapp, never()).alTerminar(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("AC-08 · si el correo falla, la campana y WhatsApp salen igual")
    void elCorreoNoTumbaWhatsapp() {
        doThrow(new IllegalStateException("SMTP caído")).when(correo)
                .enviar(anyLong(), anyLong(), anyString(), anyString(), any());

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, "Pase automático", true, false, null);

        verify(campana).publicar(any(), eq("PRUEBA_DISPONIBLE"), any(), any(), any());
        verify(whatsapp).alAvisar(any(), eq("PRUEBA_DISPONIBLE"), any(), any());
    }

    @Test
    @DisplayName("AC-10 · movido «sin avisar»: nada para WhatsApp")
    void sinAvisarNoVa() {
        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, "Ya hablamos con ella", false, false, null, false);

        verify(whatsapp, never()).alAvisar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-12 · al terminar el proceso se cierra su vínculo, aunque se cierre sin avisar")
    void alTerminarSeCierraElVinculo() {
        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "NO_CONTINUA",
                null, "No cumple", false, false, null, false);

        // Con la fecha de la transición y sin autorización de aviso: se movió «sin avisar».
        verify(whatsapp).alTerminar(any(Postulacion.class), any(java.time.Instant.class), eq(false));
        verify(whatsapp, never()).alAvisar(any(), any(), any(), any());
    }
}
