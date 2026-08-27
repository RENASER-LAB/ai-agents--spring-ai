package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.ConsumoAgente;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.ConsumoEmpresa;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.CrearEmpresa;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaCreada;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaPanel;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.Personalizacion;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        sembrarTopeIa(empresa.getId(), datos.topeMensualIa());

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
        // La ficha del continente: estado, tope, banderas y el gasto del mes corriente.
        // Es TODO lo que Renaser ve de una empresa — los candidatos, notas y decisiones
        // no tienen endpoint desde la plataforma, y esa ausencia es el diseño (pieza F).
        YearMonth mes = YearMonth.now(ZONA_LIMA);
        Instant desde = mes.atDay(1).atStartOfDay(ZONA_LIMA).toInstant();
        Instant hasta = mes.plusMonths(1).atDay(1).atStartOfDay(ZONA_LIMA).toInstant();
        Map<Long, BigDecimal> consumoPorOrganizacion = new LinkedHashMap<>();
        jdbc.query("""
                SELECT organizacion_id, coalesce(sum(costo), 0) AS costo
                  FROM ejecucion_ia
                 WHERE creado_en >= ? AND creado_en < ?
                 GROUP BY organizacion_id""",
                fila -> {
                    consumoPorOrganizacion.put(fila.getLong("organizacion_id"),
                            fila.getBigDecimal("costo"));
                },
                java.sql.Timestamp.from(desde), java.sql.Timestamp.from(hasta));
        Map<Long, String> topes = new LinkedHashMap<>();
        jdbc.query("SELECT organizacion_id, valor FROM parametro WHERE codigo = 'tope_mensual_ia'",
                fila -> {
                    topes.put(fila.getLong("organizacion_id"), fila.getString("valor"));
                });

        return organizaciones.findAll().stream()
                .filter(o -> !o.isEsPlataforma())
                .map(o -> new EmpresaPanel(o.getId(), o.getCodigo(), o.getNombre(),
                        o.isEsActiva(),
                        topes.get(o.getId()),
                        new Personalizacion(o.isBancoPropio(), o.isPesosPropios(),
                                o.isPlantillasEvaluacionPropias(), o.isPruebasPuestoPropias()),
                        consumoPorOrganizacion.getOrDefault(o.getId(), BigDecimal.ZERO),
                        o.getCreadoEn()))
                .toList();
    }

    @Override
    @Transactional
    public void suspender(ContextoUsuario quien, Long empresaId, String motivo) {
        laPlataformaDe(quien);
        Organizacion empresa = organizaciones.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", "id", empresaId));
        // Suspenderse a sí misma dejaría la plataforma sin nadie que pudiera reactivar
        // nada: el candado de la puerta no puede quedarse dentro de la casa.
        if (empresa.isEsPlataforma()) {
            throw new IllegalArgumentException("La plataforma no puede suspenderse a sí misma");
        }
        if (!empresa.isEsActiva()) {
            throw new IllegalStateException("La empresa «" + empresa.getNombre()
                    + "» ya está suspendida");
        }
        empresa.setEsActiva(false);
        organizaciones.save(empresa);
        // Congelada, no borrada: el login y el filtro de identidad dejan fuera a su
        // equipo, el tablón esconde sus vacantes, y TODO lo demás queda tal cual — los
        // candidatos que ya estaban dentro no pagan el problema comercial de la empresa.
        auditoria.registrar(quien.organizacionId(), quien, "suspender_empresa",
                "organizacion", empresaId,
                Map.of("esActiva", true), Map.of("esActiva", false), motivo);
        log.warn("Empresa {} ({}) suspendida por la plataforma", empresaId, empresa.getCodigo());
    }

    @Override
    @Transactional
    public void reactivar(ContextoUsuario quien, Long empresaId, String motivo) {
        laPlataformaDe(quien);
        Organizacion empresa = organizaciones.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", "id", empresaId));
        if (empresa.isEsActiva()) {
            throw new IllegalStateException("La empresa «" + empresa.getNombre()
                    + "» no está suspendida");
        }
        empresa.setEsActiva(true);
        organizaciones.save(empresa);
        auditoria.registrar(quien.organizacionId(), quien, "reactivar_empresa",
                "organizacion", empresaId,
                Map.of("esActiva", false), Map.of("esActiva", true), motivo);
        log.info("Empresa {} ({}) reactivada: todo vuelve tal cual", empresaId, empresa.getCodigo());
    }

    @Override
    @Transactional
    public void ponerTopeIa(ContextoUsuario quien, Long empresaId, String tope) {
        laPlataformaDe(quien);
        Organizacion empresa = organizaciones.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", "id", empresaId));
        // En blanco = sin tope: el valor vacío es «ausente» para ServicioParametros, y
        // así quitarle el tope a una empresa no borra la fila ni su historia.
        String valor = tope == null || tope.isBlank() ? "" : comoNumero(tope);
        String anterior = jdbc.query(
                "SELECT valor FROM parametro WHERE organizacion_id = ? AND codigo = 'tope_mensual_ia'",
                fila -> fila.next() ? fila.getString(1) : null, empresaId);
        jdbc.update("""
                INSERT INTO parametro (organizacion_id, codigo, valor, tipo, descripcion)
                VALUES (?, 'tope_mensual_ia', ?, 'TEXTO',
                        'Tope mensual de gasto en IA (USD). Lo administra Renaser; vacío = sin tope')
                ON CONFLICT (organizacion_id, codigo) DO UPDATE SET valor = excluded.valor""",
                empresaId, valor);
        // Los trabajos que la falta de cupo dejó EN_ESPERA no se despiertan aquí: los
        // despierta el sondeo de la cola en su próximo ciclo, que ya sabe hacerlo y es
        // el mismo camino del cambio de mes. Un solo sitio despierta.
        auditoria.registrar(quien.organizacionId(), quien, "poner_tope_ia",
                "organizacion", empresaId,
                Map.of("tope", anterior == null ? "" : anterior), Map.of("tope", valor),
                "Tope mensual de IA");
        log.info("Tope de IA de la empresa {} ({}): «{}»", empresaId, empresa.getCodigo(), valor);
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

    /**
     * El tope mensual de IA del alta (pieza E), si quien da el alta puso uno. Va como
     * parámetro de la empresa —es SU tope— pero lo administra Renaser: la copia de
     * parámetros no lo trae (la plataforma no se pone tope a sí misma por defecto), y
     * {@code editarParametro} se lo niega a la propia empresa.
     */
    private void sembrarTopeIa(Long empresaId, String tope) {
        if (tope == null || tope.isBlank()) {
            return;
        }
        jdbc.update("""
                INSERT INTO parametro (organizacion_id, codigo, valor, tipo, descripcion)
                VALUES (?, 'tope_mensual_ia', ?, 'TEXTO',
                        'Tope mensual de gasto en IA (USD). Lo administra Renaser; vacío = sin tope')
                ON CONFLICT (organizacion_id, codigo) DO UPDATE SET valor = excluded.valor""",
                empresaId, comoNumero(tope));
    }

    /** Valida que el tope sea un número no negativo y lo normaliza. */
    private static String comoNumero(String tope) {
        try {
            BigDecimal valor = new BigDecimal(tope.trim());
            if (valor.signum() < 0) {
                throw new IllegalArgumentException("El tope mensual de IA no puede ser negativo");
            }
            return valor.toPlainString();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "El tope mensual de IA debe ser un número, no «" + tope + "»");
        }
    }

    @Override
    public List<ConsumoEmpresa> consumo(ContextoUsuario quien, String mes) {
        laPlataformaDe(quien);
        YearMonth pedido;
        try {
            pedido = mes == null || mes.isBlank()
                    ? YearMonth.now(ZONA_LIMA) : YearMonth.parse(mes.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El mes va como YYYY-MM, no «" + mes + "»");
        }
        Instant desde = pedido.atDay(1).atStartOfDay(ZONA_LIMA).toInstant();
        Instant hasta = pedido.plusMonths(1).atDay(1).atStartOfDay(ZONA_LIMA).toInstant();

        // SQL directo sobre ejecucion_ia por lo mismo que la siembra: es una agregación
        // sin regla de negocio encima, y traer aquí el repositorio del motor de agentes
        // cruzaría la frontera de ArchUnit por una consulta de solo lectura.
        Map<Long, List<ConsumoAgente>> porOrganizacion = new LinkedHashMap<>();
        jdbc.query("""
                SELECT e.organizacion_id, e.agente_codigo,
                       coalesce(sum(e.costo), 0)          AS costo,
                       coalesce(sum(e.tokens_entrada), 0) AS tokens_entrada,
                       coalesce(sum(e.tokens_salida), 0)  AS tokens_salida,
                       count(*)                           AS llamadas
                  FROM ejecucion_ia e
                 WHERE e.creado_en >= ? AND e.creado_en < ?
                 GROUP BY e.organizacion_id, e.agente_codigo
                 ORDER BY e.organizacion_id, costo DESC""",
                fila -> {
                    porOrganizacion
                            .computeIfAbsent(fila.getLong("organizacion_id"), id -> new ArrayList<>())
                            .add(new ConsumoAgente(fila.getString("agente_codigo"),
                                    fila.getBigDecimal("costo"),
                                    fila.getLong("tokens_entrada"),
                                    fila.getLong("tokens_salida"),
                                    fila.getLong("llamadas")));
                },
                java.sql.Timestamp.from(desde), java.sql.Timestamp.from(hasta));

        Map<Long, String> nombres = new LinkedHashMap<>();
        organizaciones.findAllById(porOrganizacion.keySet())
                .forEach(o -> nombres.put(o.getId(), o.getNombre()));

        return porOrganizacion.entrySet().stream()
                .map(entrada -> new ConsumoEmpresa(entrada.getKey(),
                        nombres.getOrDefault(entrada.getKey(), ""),
                        entrada.getValue().stream()
                                .map(ConsumoAgente::costo)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        entrada.getValue()))
                .toList();
    }

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

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
