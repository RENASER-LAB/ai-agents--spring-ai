package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.colaborador.repository.SituacionLaboralRepository;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.CargoPanel;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.CrearCargo;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.GuardarSede;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.ListaDeCargos;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.ListaDeSedes;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.SedePanel;
import com.renaser.ai.ai_engine.organizacion.entity.Sede;
import com.renaser.ai.ai_engine.organizacion.repository.SedeRepository;
import com.renaser.ai.ai_engine.organizacion.service.ServicioEstructura;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Familia;
import com.renaser.ai.ai_engine.vacante.entity.NivelPuesto;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.repository.FamiliaRepository;
import com.renaser.ai.ai_engine.vacante.repository.NivelPuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioEstructuraImpl implements ServicioEstructura {

    static final String EDITAR = "editar_estructura";

    private final SedeRepository sedes;
    private final PuestoRepository puestos;
    private final NivelPuestoRepository niveles;
    private final FamiliaRepository familias;
    private final VacanteRepository vacantes;
    private final SituacionLaboralRepository situaciones;
    private final CatalogosDelPerfil catalogos;
    private final ServicioAuditoria auditoria;

    // ============ Sedes ============

    @Override
    public ListaDeSedes sedes(ContextoUsuario quien) {
        Map<String, OpcionUbigeo> ciudades = catalogos.ciudadesPorCodigo();
        List<SedePanel> lista = sedes.findByOrganizacionIdOrderByNombre(quien.organizacionId()).stream()
                .map(s -> {
                    OpcionUbigeo provincia = s.getProvinciaUbigeo() == null ? null : ciudades.get(s.getProvinciaUbigeo());
                    return new SedePanel(s.getId(), s.getNombre(), s.getDireccion(), s.getProvinciaUbigeo(),
                            provincia == null ? null : provincia.nombre(), s.getCodigoSunat(), s.isEsActiva());
                })
                .toList();
        return new ListaDeSedes(quien.tiene(EDITAR), lista);
    }

    @Override
    @Transactional
    public Long crearSede(ContextoUsuario quien, GuardarSede datos) {
        SedeLimpia limpia = validar(quien.organizacionId(), datos, null);
        Sede sede = sedes.save(Sede.builder()
                .organizacionId(quien.organizacionId()).nombre(limpia.nombre()).direccion(limpia.direccion())
                .provinciaUbigeo(limpia.provincia()).codigoSunat(limpia.codigoSunat())
                .esActiva(true).creadoEn(Instant.now()).build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_sede", "sede", sede.getId(), null,
                comoMapa(sede), null);
        return sede.getId();
    }

    @Override
    @Transactional
    public void editarSede(ContextoUsuario quien, Long id, GuardarSede datos) {
        Sede sede = laSede(quien, id);
        SedeLimpia limpia = validar(quien.organizacionId(), datos, id);
        Map<String, Object> antes = comoMapa(sede);
        sede.setNombre(limpia.nombre());
        sede.setDireccion(limpia.direccion());
        sede.setProvinciaUbigeo(limpia.provincia());
        sede.setCodigoSunat(limpia.codigoSunat());
        sedes.save(sede);
        auditoria.registrar(quien.organizacionId(), quien, "editar_sede", "sede", sede.getId(), antes,
                comoMapa(sede), null);
    }

    @Override
    @Transactional
    public void activarSede(ContextoUsuario quien, Long id, boolean activa) {
        Sede sede = laSede(quien, id);
        if (sede.isEsActiva() == activa) {
            throw new IllegalStateException(activa ? "La sede ya está activa" : "La sede ya está desactivada");
        }
        sede.setEsActiva(activa);
        sedes.save(sede);
        auditoria.registrar(quien.organizacionId(), quien, activa ? "reactivar_sede" : "desactivar_sede",
                "sede", sede.getId(), Map.of("esActiva", !activa), Map.of("esActiva", activa), null);
    }

    private record SedeLimpia(String nombre, String direccion, String provincia, String codigoSunat) {}

    private SedeLimpia validar(Long org, GuardarSede datos, Long excepto) {
        String nombre = texto(datos.nombre());
        if (nombre == null) {
            throw new IllegalArgumentException("La sede necesita un nombre");
        }
        if (sedes.existeConElNombre(org, nombre, excepto)) {
            throw new IllegalStateException("Ya hay una sede llamada «" + nombre + "»");
        }
        String provincia = texto(datos.provinciaUbigeo());
        if (provincia != null && !catalogos.esCiudadElegible(provincia)) {
            throw new IllegalArgumentException("Esa provincia no está en el catálogo");
        }
        String codigo = texto(datos.codigoSunat());
        if (codigo != null && !codigo.matches("^[0-9]{4}$")) {
            throw new IllegalArgumentException("El código de establecimiento de SUNAT tiene 4 dígitos");
        }
        return new SedeLimpia(nombre, texto(datos.direccion()), provincia, codigo);
    }

    private Sede laSede(ContextoUsuario quien, Long id) {
        return sedes.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Sede", "id", id));
    }

    private static Map<String, Object> comoMapa(Sede s) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put("nombre", s.getNombre());
        mapa.put("direccion", s.getDireccion());
        mapa.put("provinciaUbigeo", s.getProvinciaUbigeo());
        mapa.put("codigoSunat", s.getCodigoSunat());
        return mapa;
    }

    // ============ Cargos ============

    @Override
    public ListaDeCargos cargos(ContextoUsuario quien) {
        Long org = quien.organizacionId();
        List<Puesto> todos = puestos.findByOrganizacionIdOrderByNombre(org);
        Map<String, String> nombresDeNivel = niveles.findAll().stream()
                .collect(Collectors.toMap(NivelPuesto::getCodigo, NivelPuesto::getNombre));
        Map<String, String> nombresDeFamilia = familias.findAll().stream()
                .collect(Collectors.toMap(Familia::getCodigo, Familia::getNombre));
        Map<Long, Long> enVacantes = contar(vacantes.vacantesPorPuesto(org));
        Map<Long, Long> enPersonas = todos.isEmpty() ? Map.of()
                : contar(situaciones.personasPorCargo(todos.stream().map(Puesto::getId).toList()));
        return new ListaDeCargos(quien.tiene(EDITAR), todos.stream()
                .map(p -> new CargoPanel(p.getId(), p.getNombre(), p.getNivelPuestoCodigo(),
                        nombresDeNivel.getOrDefault(p.getNivelPuestoCodigo(), p.getNivelPuestoCodigo()),
                        p.getFamiliaCodigo(), nombresDeFamilia.getOrDefault(p.getFamiliaCodigo(), p.getFamiliaCodigo()),
                        p.isEsActivo(), enVacantes.getOrDefault(p.getId(), 0L),
                        enPersonas.getOrDefault(p.getId(), 0L)))
                .toList());
    }

    @Override
    @Transactional
    public Long crearCargo(ContextoUsuario quien, CrearCargo datos) {
        Long org = quien.organizacionId();
        String nombre = nombreLibre(org, datos.nombre(), null);
        if (!niveles.existsById(datos.nivelPuestoCodigo())) {
            throw new IllegalArgumentException("Ese nivel no existe");
        }
        if (!familias.existsById(datos.familiaCodigo())) {
            throw new IllegalArgumentException("Esa familia no existe");
        }
        Puesto puesto = puestos.save(Puesto.builder()
                .organizacionId(org).codigo(codigoDisponible(org, nombre)).nombre(nombre)
                .nivelPuestoCodigo(datos.nivelPuestoCodigo()).familiaCodigo(datos.familiaCodigo())
                .esActivo(true).creadoEn(Instant.now()).build());
        auditoria.registrar(org, quien, "crear_puesto", "puesto", puesto.getId(), null,
                Map.of("codigo", puesto.getCodigo(), "nombre", nombre), null);
        return puesto.getId();
    }

    @Override
    @Transactional
    public void renombrarCargo(ContextoUsuario quien, Long id, String nombreNuevo) {
        Puesto puesto = elCargo(quien, id);
        String nombre = nombreLibre(quien.organizacionId(), nombreNuevo, id);
        String antes = puesto.getNombre();
        if (antes.equals(nombre)) {
            return;
        }
        puesto.setNombre(nombre);
        puestos.save(puesto);
        auditoria.registrar(quien.organizacionId(), quien, "renombrar_puesto", "puesto", puesto.getId(),
                Map.of("nombre", antes), Map.of("nombre", nombre), null);
    }

    @Override
    @Transactional
    public void activarCargo(ContextoUsuario quien, Long id, boolean activo) {
        Puesto puesto = elCargo(quien, id);
        if (puesto.isEsActivo() == activo) {
            throw new IllegalStateException(activo ? "El cargo ya está activo" : "El cargo ya está desactivado");
        }
        puesto.setEsActivo(activo);
        puestos.save(puesto);
        auditoria.registrar(quien.organizacionId(), quien, activo ? "reactivar_puesto" : "desactivar_puesto",
                "puesto", puesto.getId(), Map.of("esActivo", !activo), Map.of("esActivo", activo), null);
    }

    private Puesto elCargo(ContextoUsuario quien, Long id) {
        return puestos.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Cargo", "id", id));
    }

    /**
     * Un nombre que no tenga otro cargo de la empresa, sin distinguir mayúsculas: la carga por
     * Excel busca los cargos por su nombre, y dos iguales harían ambigua cada fila.
     */
    private String nombreLibre(Long org, String nombre, Long excepto) {
        String limpio = texto(nombre);
        if (limpio == null) {
            throw new IllegalArgumentException("El cargo necesita un nombre");
        }
        if (puestos.existeConElNombre(org, limpio, excepto)) {
            throw new IllegalStateException("Ya hay un cargo llamado «" + limpio + "»");
        }
        return limpio;
    }

    /** El mismo código que pone el alta de puestos desde la vacante: el nombre en mayúsculas. */
    private String codigoDisponible(Long org, String nombre) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        if (base.isBlank()) {
            base = "PUESTO";
        }
        String candidato = base;
        int sufijo = 2;
        while (puestos.existsByOrganizacionIdAndCodigo(org, candidato)) {
            candidato = base + "_" + sufijo++;
        }
        return candidato;
    }

    private static Map<Long, Long> contar(List<Object[]> filas) {
        return filas.stream().filter(f -> f[0] != null)
                .collect(Collectors.toMap(f -> ((Number) f[0]).longValue(), f -> ((Number) f[1]).longValue(),
                        Long::sum));
    }

    private static String texto(String valor) {
        if (valor == null) return null;
        String limpio = valor.strip();
        return limpio.isEmpty() ? null : limpio;
    }
}
