package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.CrearEmpresa;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaCreada;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaPanel;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.ServicioPlataforma;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.InvitacionCreada;
import com.renaser.ai.ai_engine.seguridad.service.ServicioInvitaciones;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Ver {@link ServicioPlataforma}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioPlataformaImpl implements ServicioPlataforma {

    private final OrganizacionRepository organizaciones;
    private final ServicioInvitaciones invitaciones;
    private final ServicioAuditoria auditoria;

    // La siembra copia tablas de cuatro dominios distintos (roles, parámetros, textos
    // legales, correos). Va por SQL directo estilo V17 —INSERT…SELECT idempotente— y no
    // por repositorios: son copias fila a fila sin ninguna regla de negocio encima, y
    // traer aquí los repos de media aplicación acoplaría este servicio con todo.
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public EmpresaCreada crearEmpresa(ContextoUsuario quien, CrearEmpresa datos) {
        Long plataformaId = laPlataformaDe(quien);

        organizaciones.findByCodigo(datos.codigo().trim()).ifPresent(o -> {
            throw new IllegalStateException("Ya existe una organización con el código «"
                    + datos.codigo().trim() + "»");
        });

        Organizacion empresa = organizaciones.save(Organizacion.builder()
                .codigo(datos.codigo().trim())
                .nombre(datos.nombre().trim())
                .esActiva(true)
                .creadoEn(Instant.now())
                .build());

        sembrar(empresa.getId(), plataformaId);

        // La invitación va al final, cuando la empresa ya tiene su plantilla de correo
        // copiada: si fuera antes, el aviso saldría con la plantilla de nadie y se
        // perdería en silencio (ServicioCorreo anota el error y sigue).
        InvitacionCreada invitacion = invitaciones.crearParaOrganizacion(
                quien, empresa.getId(), datos.correoAdministrador(), List.of("ADMINISTRADOR"));

        auditoria.registrar(quien.organizacionId(), quien, "crear_empresa",
                "organizacion", empresa.getId(), null,
                Map.of("codigo", empresa.getCodigo(), "nombre", empresa.getNombre()), null);
        log.info("Empresa {} creada con su siembra completa · invitación {} al administrador",
                empresa.getId(), invitacion.id());

        return new EmpresaCreada(empresa.getId(), invitacion.id(),
                invitacion.url(), invitacion.venceEn());
    }

    @Override
    public List<EmpresaPanel> empresas(ContextoUsuario quien) {
        laPlataformaDe(quien);
        return organizaciones.findAll().stream()
                .filter(o -> !o.isEsPlataforma())
                .map(o -> new EmpresaPanel(o.getId(), o.getCodigo(), o.getNombre(),
                        o.isEsActiva(), o.getCreadoEn()))
                .toList();
    }

    /**
     * El día uno de la empresa (spec A §4): lee el método de la plataforma sin copiar
     * nada —eso lo resuelven las banderas apagadas— y se lleva copia de lo que jamás se
     * comparte entre empresas.
     */
    private void sembrar(Long empresaId, Long plataformaId) {
        // Los cinco roles de sistema, tal cual los tiene la plataforma.
        jdbc.update("""
                INSERT INTO rol (organizacion_id, codigo, nombre, descripcion, es_sistema, creado_en)
                SELECT ?, r.codigo, r.nombre, r.descripcion, r.es_sistema, now()
                FROM rol r
                WHERE r.organizacion_id = ?
                  AND NOT EXISTS (SELECT 1 FROM rol ya
                                  WHERE ya.organizacion_id = ? AND ya.codigo = r.codigo)""",
                empresaId, plataformaId, empresaId);

        // La matriz permiso-alcance completa, salvo administrar_plataforma: dar de alta
        // empresas es de la dueña de la plataforma, no una función de cualquier panel.
        jdbc.update("""
                INSERT INTO rol_permiso (rol_id, permiso_id, alcance, creado_en)
                SELECT nuevo.id, rp.permiso_id, rp.alcance, now()
                FROM rol_permiso rp
                JOIN rol origen ON origen.id = rp.rol_id AND origen.organizacion_id = ?
                JOIN rol nuevo  ON nuevo.organizacion_id = ? AND nuevo.codigo = origen.codigo
                JOIN permiso p  ON p.id = rp.permiso_id AND p.codigo <> 'administrar_plataforma'
                WHERE NOT EXISTS (SELECT 1 FROM rol_permiso ya
                                  WHERE ya.rol_id = nuevo.id AND ya.permiso_id = rp.permiso_id)""",
                plataformaId, empresaId);

        // Los parámetros, con los valores actuales de la plataforma como punto de
        // partida. Obligatorio y no cortesía: editarParametro no crea filas, así que un
        // parámetro no sembrado sería un parámetro que la empresa jamás podría tocar.
        jdbc.update("""
                INSERT INTO parametro (organizacion_id, codigo, valor, tipo, descripcion)
                SELECT ?, p.codigo, p.valor, p.tipo, p.descripcion
                FROM parametro p
                WHERE p.organizacion_id = ?
                ON CONFLICT (organizacion_id, codigo) DO NOTHING""",
                empresaId, plataformaId);

        // Los textos legales, como BORRADOR (publicado_en vacío): nombran a Renaser y la
        // ley 29733 obliga a nombrar a quien trata los datos. Nadie puede operar con el
        // consentimiento de otro; la empresa los reescribe y publica con su nombre.
        jdbc.update("""
                INSERT INTO texto_consentimiento (organizacion_id, tipo, version, texto, hash, publicado_en)
                SELECT ?, t.tipo, t.version, t.texto, t.hash, NULL
                FROM texto_consentimiento t
                WHERE t.organizacion_id = ?
                  AND t.publicado_en IS NOT NULL
                  AND t.publicado_en = (SELECT max(t2.publicado_en) FROM texto_consentimiento t2
                                        WHERE t2.organizacion_id = t.organizacion_id
                                          AND t2.tipo = t.tipo AND t2.publicado_en IS NOT NULL)""",
                empresaId, plataformaId);

        // Los correos, ACTIVOS: al revés que los textos legales, los avisos de sus
        // vacantes tienen que salir desde el primer candidato, y un texto genérico que
        // sale vale más que uno perfecto que no existe. La empresa los personaliza después.
        jdbc.update("""
                INSERT INTO plantilla_correo (organizacion_id, codigo, version, asunto, cuerpo, es_activa)
                SELECT ?, pc.codigo, pc.version, pc.asunto, pc.cuerpo, true
                FROM plantilla_correo pc
                WHERE pc.organizacion_id = ? AND pc.es_activa""",
                empresaId, plataformaId);
    }

    /** La segunda llave: además del permiso, hay que SER de la plataforma. */
    private Long laPlataformaDe(ContextoUsuario quien) {
        Organizacion plataforma = organizaciones.findByEsPlataformaTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "Ninguna organización está marcada como plataforma"));
        if (!plataforma.getId().equals(quien.organizacionId())) {
            throw new AccessDeniedException("Solo la plataforma puede administrar empresas");
        }
        return plataforma.getId();
    }
}
