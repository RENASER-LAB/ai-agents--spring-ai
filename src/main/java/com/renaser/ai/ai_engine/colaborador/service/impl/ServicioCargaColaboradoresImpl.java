package com.renaser.ai.ai_engine.colaborador.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.ErrorDeCarga;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.ResultadoCarga;
import com.renaser.ai.ai_engine.colaborador.entity.Colaborador;
import com.renaser.ai.ai_engine.colaborador.entity.PeriodoLaboral;
import com.renaser.ai.ai_engine.colaborador.entity.SituacionLaboral;
import com.renaser.ai.ai_engine.colaborador.repository.ColaboradorRepository;
import com.renaser.ai.ai_engine.colaborador.repository.ListadoDeColaboradoresRepository;
import com.renaser.ai.ai_engine.colaborador.repository.ListadoDeColaboradoresRepository.Actual;
import com.renaser.ai.ai_engine.colaborador.repository.PeriodoLaboralRepository;
import com.renaser.ai.ai_engine.colaborador.repository.SituacionLaboralRepository;
import com.renaser.ai.ai_engine.colaborador.service.CargaInvalidaException;
import com.renaser.ai.ai_engine.colaborador.service.CatalogosDePersonas;
import com.renaser.ai.ai_engine.colaborador.service.HoyEnLima;
import com.renaser.ai.ai_engine.colaborador.service.JefesSinCirculo;
import com.renaser.ai.ai_engine.colaborador.service.LibroDeColaboradores;
import com.renaser.ai.ai_engine.colaborador.service.LibroDeColaboradores.Celda;
import com.renaser.ai.ai_engine.colaborador.service.LibroDeColaboradores.Columna;
import com.renaser.ai.ai_engine.colaborador.service.LibroDeColaboradores.Fila;
import com.renaser.ai.ai_engine.colaborador.service.LibroDeColaboradores.Lectura;
import com.renaser.ai.ai_engine.colaborador.service.LibroDeColaboradores.Valores;
import com.renaser.ai.ai_engine.colaborador.service.ReglasDelColaborador;
import com.renaser.ai.ai_engine.colaborador.service.ServicioCargaColaboradores;
import com.renaser.ai.ai_engine.organizacion.entity.Area;
import com.renaser.ai.ai_engine.organizacion.entity.Sede;
import com.renaser.ai.ai_engine.organizacion.repository.AreaRepository;
import com.renaser.ai.ai_engine.organizacion.repository.SedeRepository;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionCatalogo;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La carga por Excel. Valida el archivo entero, junta todos los errores y solo si no hay
 * ninguno aplica todas las filas en una transacción.
 *
 * <p>⚠️ Sin {@code ver_sueldos}, un archivo con columna de sueldo se rechaza entero y el
 * mensaje no repite ningún valor: el sueldo tampoco viaja en los errores.
 */
@Service
@RequiredArgsConstructor
public class ServicioCargaColaboradoresImpl implements ServicioCargaColaboradores {

    private static final String CAMBIOS_EN_LA_FICHA = "Los cambios de puesto y contrato se registran en su ficha";
    private static final List<DateTimeFormatter> FORMATOS_DE_FECHA = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE);

    private final ColaboradorRepository colaboradores;
    private final PeriodoLaboralRepository periodos;
    private final SituacionLaboralRepository situaciones;
    private final ListadoDeColaboradoresRepository listado;
    private final SedeRepository sedes;
    private final AreaRepository areas;
    private final PuestoRepository puestos;
    private final CatalogosDelPerfil catalogosDelPerfil;
    private final ServicioAuditoria auditoria;
    private final HoyEnLima reloj;

    @Override
    public byte[] plantilla(ContextoUsuario quien) {
        exigirTodo(quien, ServicioColaboradoresImpl.EDITAR);
        Long org = quien.organizacionId();
        Valores valores = new Valores(
                List.copyOf(CatalogosDePersonas.TIPOS_DOCUMENTO.values()),
                List.copyOf(CatalogosDePersonas.SEXOS.values()),
                List.copyOf(CatalogosDePersonas.ESTADOS_CIVILES.values()),
                catalogosDelPerfil.nivelesEducativos().stream().map(OpcionCatalogo::nombre).toList(),
                sedes.findByOrganizacionIdAndEsActivaTrueOrderByNombre(org).stream().map(Sede::getNombre).toList(),
                areas.findByOrganizacionIdAndEsActivaTrueOrderByNombre(org).stream().map(Area::getNombre).toList(),
                puestos.findByOrganizacionIdAndEsActivoTrueOrderByNombre(org).stream().map(Puesto::getNombre).toList(),
                List.copyOf(CatalogosDePersonas.TIPOS_CONTRATO.values()),
                List.copyOf(CatalogosDePersonas.REGIMENES.values()),
                List.copyOf(CatalogosDePersonas.MONEDAS.values()));
        return LibroDeColaboradores.plantilla(valores, alcanceTodo(quien, ServicioColaboradoresImpl.VER_SUELDOS));
    }

    @Override
    @Transactional
    public ResultadoCarga cargar(ContextoUsuario quien, String nombreDelArchivo, byte[] contenido) {
        exigirTodo(quien, ServicioColaboradoresImpl.EDITAR);
        boolean sueldos = alcanceTodo(quien, ServicioColaboradoresImpl.VER_SUELDOS);
        Lectura lectura = LibroDeColaboradores.leer(contenido, nombreDelArchivo);
        if (lectura.columnas().stream().anyMatch(Columna::esDeSueldo) && !sueldos) {
            throw new CargaInvalidaException(List.of(new ErrorDeCarga(1, Columna.SUELDO.titulo, "",
                    "No tienes permiso para cargar sueldos")));
        }
        boolean sinClave = !lectura.columnas().contains(Columna.TIPO_DOCUMENTO)
                || !lectura.columnas().contains(Columna.NUMERO_DOCUMENTO);
        if (sinClave || (lectura.filas().isEmpty() && !lectura.errores().isEmpty())) {
            throw new CargaInvalidaException(lectura.errores().isEmpty()
                    ? List.of(new ErrorDeCarga(0, "(archivo)", nombreDelArchivo, "El archivo no se pudo leer"))
                    : lectura.errores());
        }
        if (lectura.filas().isEmpty()) {
            throw new CargaInvalidaException(List.of(new ErrorDeCarga(2, "(archivo)", "",
                    "La hoja «Colaboradores» no tiene ninguna fila con datos")));
        }

        // Cerrojo por empresa: dos cargas a la vez validarían contra la misma foto y la segunda
        // chocaría al guardar con un error de integridad en vez de con su lista de errores.
        colaboradores.esperarTurno(("carga-colaboradores:" + quien.organizacionId()).hashCode());

        Validacion v = new Validacion(quien.organizacionId(), reloj.hoy(), sueldos, lectura);
        v.validar();
        if (!v.errores.isEmpty()) {
            v.errores.sort(Comparator.comparingInt(ErrorDeCarga::fila)
                    .thenComparing(e -> ordenDeColumna(e.columna())));
            throw new CargaInvalidaException(v.errores);
        }
        ResultadoCarga resultado = v.aplicar(quien);
        auditoria.registrar(quien.organizacionId(), quien, "cargar_colaboradores_excel", "colaborador",
                null, null, Map.of("archivo", nombreDelArchivo == null ? "" : nombreDelArchivo,
                        "altas", resultado.altas(), "actualizados", resultado.actualizados()), null);
        return resultado;
    }

    private static int ordenDeColumna(String titulo) {
        for (Columna c : Columna.values()) {
            if (c.titulo.equals(titulo)) return c.ordinal();
        }
        return -1;
    }

    // ============ Una pasada de validación ============

    /** Lo que una fila deja listo para guardar. */
    private static final class FilaValida {
        final Fila fila;
        final String clave;
        final Actual existente;
        String tipo;
        String numero;
        String nombres;
        String paterno;
        String materno;
        LocalDate nacimiento;
        String sexo;
        String estadoCivil;
        String nacionalidad;
        String celular;
        String correoPersonal;
        String correoCorporativo;
        String direccion;
        String provincia;
        String nivelEducativo;
        LocalDate ingreso;
        Long sedeId;
        Long areaId;
        Long cargoId;
        String jefeClave;
        String tipoContrato;
        LocalDate finContrato;
        LocalDate finPrueba;
        String regimen;
        BigDecimal sueldo;
        String moneda;

        FilaValida(Fila fila, String clave, Actual existente) {
            this.fila = fila;
            this.clave = clave;
            this.existente = existente;
        }

        boolean esAlta() {
            return existente == null;
        }
    }

    private final class Validacion {
        final Long org;
        final LocalDate hoy;
        final boolean sueldos;
        final Lectura lectura;
        final List<ErrorDeCarga> errores;
        final List<FilaValida> validas = new ArrayList<>();
        final Map<String, FilaValida> porClave = new LinkedHashMap<>();
        final Map<String, Actual> actualPorClave;
        final Map<Long, Actual> actualPorId;
        final Map<String, List<Sede>> sedesPorNombre;
        final Map<String, List<Area>> areasPorNombre;
        final Map<String, List<Puesto>> cargosPorNombre;
        final Map<String, List<String>> provinciasPorNombre = new HashMap<>();
        final Map<String, String> nivelesPorNombre = new HashMap<>();

        Validacion(Long org, LocalDate hoy, boolean sueldos, Lectura lectura) {
            this.org = org;
            this.hoy = hoy;
            this.sueldos = sueldos;
            this.lectura = lectura;
            this.errores = new ArrayList<>(lectura.errores());
            List<Actual> actuales = listado.actuales(org, hoy);
            this.actualPorClave = actuales.stream().collect(Collectors.toMap(
                    a -> clave(a.tipoDocumento(), a.numeroDocumento()), Function.identity(), (a, b) -> a));
            this.actualPorId = actuales.stream().collect(Collectors.toMap(Actual::id, Function.identity()));
            this.sedesPorNombre = sedes.findByOrganizacionIdOrderByNombre(org).stream()
                    .collect(Collectors.groupingBy(s -> LibroDeColaboradores.normalizar(s.getNombre())));
            this.areasPorNombre = areas.findByOrganizacionIdOrderByNombre(org).stream()
                    .collect(Collectors.groupingBy(a -> LibroDeColaboradores.normalizar(a.getNombre())));
            this.cargosPorNombre = puestos.findByOrganizacionIdOrderByNombre(org).stream()
                    .collect(Collectors.groupingBy(p -> LibroDeColaboradores.normalizar(p.getNombre())));
            for (OpcionUbigeo u : catalogosDelPerfil.ubigeo()) {
                provinciasPorNombre.computeIfAbsent(LibroDeColaboradores.normalizar(u.nombre()),
                        k -> new ArrayList<>()).add(u.codigo());
                if (u.departamento() != null) {
                    provinciasPorNombre.computeIfAbsent(LibroDeColaboradores.normalizar(
                            u.nombre() + " (" + u.departamento() + ")"), k -> new ArrayList<>()).add(u.codigo());
                }
                provinciasPorNombre.computeIfAbsent(LibroDeColaboradores.normalizar(u.codigo()),
                        k -> new ArrayList<>()).add(u.codigo());
            }
            for (OpcionCatalogo n : catalogosDelPerfil.nivelesEducativos()) {
                nivelesPorNombre.put(LibroDeColaboradores.normalizar(n.nombre()), n.codigo());
                nivelesPorNombre.put(LibroDeColaboradores.normalizar(n.codigo()), n.codigo());
            }
        }

        void validar() {
            // 1 · La clave de cada fila, y los documentos repetidos.
            Map<String, List<Fila>> filasPorClave = new LinkedHashMap<>();
            Map<Fila, String> claveDe = new HashMap<>();
            for (Fila fila : lectura.filas()) {
                String tipo = deLista(fila, Columna.TIPO_DOCUMENTO, CatalogosDePersonas.TIPOS_DOCUMENTO, true);
                String numero = ReglasDelColaborador.limpiarDocumento(fila.de(Columna.NUMERO_DOCUMENTO).texto());
                if (tipo == null) {
                    continue;
                }
                String error = ReglasDelColaborador.errorDelDocumento(tipo, numero);
                if (error != null) {
                    errorEn(fila, Columna.NUMERO_DOCUMENTO, error);
                    continue;
                }
                String clave = clave(tipo, numero);
                claveDe.put(fila, clave);
                filasPorClave.computeIfAbsent(clave, k -> new ArrayList<>()).add(fila);
            }
            for (List<Fila> repetidas : filasPorClave.values()) {
                if (repetidas.size() < 2) continue;
                String numeros = repetidas.stream().map(f -> String.valueOf(f.numero()))
                        .collect(Collectors.joining(", "));
                for (Fila f : repetidas) {
                    errorEn(f, Columna.NUMERO_DOCUMENTO, "El documento se repite en las filas " + numeros);
                    claveDe.remove(f);
                }
            }

            // 2 · Las columnas obligatorias que faltan en el archivo, una vez y no por fila.
            boolean hayAltas = claveDe.values().stream().anyMatch(c -> !actualPorClave.containsKey(c));
            List<Columna> faltan = new ArrayList<>();
            if (hayAltas) {
                for (Columna c : Columna.values()) {
                    if (c.obligatoria && !lectura.columnas().contains(c)) {
                        faltan.add(c);
                        errores.add(new ErrorDeCarga(1, c.titulo, "", "Falta la columna «" + c.titulo
                                + "»: es obligatoria para dar de alta"));
                    }
                }
            }

            // 3 · Cada fila, alta o actualización.
            for (Fila fila : lectura.filas()) {
                String clave = claveDe.get(fila);
                if (clave == null) continue;
                Actual existente = actualPorClave.get(clave);
                if (existente != null && "CESADO".equals(existente.estado())) {
                    errorEn(fila, Columna.NUMERO_DOCUMENTO, "Está cesado. Reingrésalo desde su ficha");
                    continue;
                }
                FilaValida v = new FilaValida(fila, clave, existente);
                String[] partes = clave.split(":", 2);
                v.tipo = partes[0];
                v.numero = partes[1];
                personales(v, faltan);
                if (v.esAlta()) {
                    puestoDelAlta(v, faltan);
                } else {
                    puestoSinCambios(v);
                }
                v.jefeClave = claveDelJefe(fila);
                validas.add(v);
                porClave.put(clave, v);
            }

            // 4 · Los jefes, contra el archivo y la empresa, y los círculos.
            jefes();
        }

        private void personales(FilaValida v, List<Columna> faltan) {
            Fila f = v.fila;
            boolean alta = v.esAlta();
            v.nombres = ReglasDelColaborador.texto(f.de(Columna.NOMBRES).texto());
            v.paterno = ReglasDelColaborador.texto(f.de(Columna.APELLIDO_PATERNO).texto());
            v.materno = ReglasDelColaborador.texto(f.de(Columna.APELLIDO_MATERNO).texto());
            if (alta && v.nombres == null && !faltan.contains(Columna.NOMBRES)) {
                errorEn(f, Columna.NOMBRES, "Falta el dato obligatorio");
            }
            if (alta && v.paterno == null && !faltan.contains(Columna.APELLIDO_PATERNO)) {
                errorEn(f, Columna.APELLIDO_PATERNO, "Falta el dato obligatorio");
            }
            v.nacimiento = fecha(f, Columna.FECHA_NACIMIENTO, alta && !faltan.contains(Columna.FECHA_NACIMIENTO));
            if (v.nacimiento != null) {
                String error = ReglasDelColaborador.errorDelNacimiento(v.nacimiento, hoy);
                if (error != null) errorEn(f, Columna.FECHA_NACIMIENTO, error);
            }
            v.sexo = deLista(f, Columna.SEXO, CatalogosDePersonas.SEXOS, alta && !faltan.contains(Columna.SEXO));
            v.estadoCivil = deLista(f, Columna.ESTADO_CIVIL, CatalogosDePersonas.ESTADOS_CIVILES, false);
            v.nacionalidad = ReglasDelColaborador.texto(f.de(Columna.NACIONALIDAD).texto());
            v.celular = ReglasDelColaborador.texto(f.de(Columna.CELULAR).texto());
            v.correoPersonal = correo(f, Columna.CORREO_PERSONAL);
            v.correoCorporativo = correo(f, Columna.CORREO_CORPORATIVO);
            v.direccion = ReglasDelColaborador.texto(f.de(Columna.DIRECCION).texto());
            if (f.tiene(Columna.PROVINCIA)) {
                List<String> codigos = provinciasPorNombre.getOrDefault(
                        LibroDeColaboradores.normalizar(f.de(Columna.PROVINCIA).texto()), List.of());
                if (codigos.isEmpty()) {
                    errorEn(f, Columna.PROVINCIA, "No existe la provincia «" + f.de(Columna.PROVINCIA).texto() + "»");
                } else if (codigos.stream().distinct().count() > 1) {
                    errorEn(f, Columna.PROVINCIA, "Hay varias provincias con ese nombre: escribe «Provincia "
                            + "(Departamento)»");
                } else {
                    v.provincia = codigos.get(0);
                }
            }
            if (f.tiene(Columna.NIVEL_EDUCATIVO)) {
                v.nivelEducativo = nivelesPorNombre.get(
                        LibroDeColaboradores.normalizar(f.de(Columna.NIVEL_EDUCATIVO).texto()));
                if (v.nivelEducativo == null) {
                    errorEn(f, Columna.NIVEL_EDUCATIVO, "No es un nivel educativo de la lista");
                }
            }
        }

        private void puestoDelAlta(FilaValida v, List<Columna> faltan) {
            Fila f = v.fila;
            v.ingreso = fecha(f, Columna.FECHA_INGRESO, !faltan.contains(Columna.FECHA_INGRESO));
            Sede sede = unico(f, Columna.SEDE, sedesPorNombre, "la sede", !faltan.contains(Columna.SEDE));
            if (sede != null) {
                if (!sede.isEsActiva()) errorEn(f, Columna.SEDE, "La sede «" + sede.getNombre() + "» está desactivada");
                v.sedeId = sede.getId();
            }
            Area area = unico(f, Columna.AREA, areasPorNombre, "el área", !faltan.contains(Columna.AREA));
            if (area != null) {
                if (!area.isEsActiva()) errorEn(f, Columna.AREA, "El área «" + area.getNombre() + "» está desactivada");
                v.areaId = area.getId();
            }
            Puesto cargo = unico(f, Columna.CARGO, cargosPorNombre, "el cargo", !faltan.contains(Columna.CARGO));
            if (cargo != null) {
                if (!cargo.isEsActivo()) errorEn(f, Columna.CARGO, "El cargo «" + cargo.getNombre() + "» está desactivado");
                v.cargoId = cargo.getId();
            }
            v.tipoContrato = deLista(f, Columna.TIPO_CONTRATO, CatalogosDePersonas.TIPOS_CONTRATO,
                    !faltan.contains(Columna.TIPO_CONTRATO));
            v.finContrato = fecha(f, Columna.FIN_CONTRATO, false);
            v.finPrueba = fecha(f, Columna.FIN_PRUEBA, false);
            v.regimen = deLista(f, Columna.REGIMEN, CatalogosDePersonas.REGIMENES, !faltan.contains(Columna.REGIMEN));
            if (v.tipoContrato != null) {
                String error = ReglasDelColaborador.errorDelContrato(v.tipoContrato, v.finContrato, v.ingreso);
                if (error != null) {
                    errorEn(f, v.finContrato == null ? Columna.TIPO_CONTRATO : Columna.FIN_CONTRATO, error);
                }
            }
            String prueba = ReglasDelColaborador.errorDelPeriodoDePrueba(v.finPrueba, v.ingreso);
            if (prueba != null) errorEn(f, Columna.FIN_PRUEBA, prueba);
            sueldo(v);
        }

        /** Alguien que ya existe: lo de puesto y contrato que traiga tiene que ser lo vigente. */
        private void puestoSinCambios(FilaValida v) {
            Fila f = v.fila;
            Actual a = v.existente;
            LocalDate ingreso = fecha(f, Columna.FECHA_INGRESO, false);
            if (ingreso != null && !ingreso.equals(a.fechaIngreso())) {
                errorEn(f, Columna.FECHA_INGRESO, CAMBIOS_EN_LA_FICHA);
            }
            Sede sede = unico(f, Columna.SEDE, sedesPorNombre, "la sede", false);
            if (sede != null && !sede.getId().equals(a.sedeId())) errorEn(f, Columna.SEDE, CAMBIOS_EN_LA_FICHA);
            Area area = unico(f, Columna.AREA, areasPorNombre, "el área", false);
            if (area != null && !area.getId().equals(a.areaId())) errorEn(f, Columna.AREA, CAMBIOS_EN_LA_FICHA);
            Puesto cargo = unico(f, Columna.CARGO, cargosPorNombre, "el cargo", false);
            if (cargo != null && !cargo.getId().equals(a.cargoId())) errorEn(f, Columna.CARGO, CAMBIOS_EN_LA_FICHA);
            String contrato = deLista(f, Columna.TIPO_CONTRATO, CatalogosDePersonas.TIPOS_CONTRATO, false);
            if (contrato != null && !contrato.equals(a.tipoContrato())) {
                errorEn(f, Columna.TIPO_CONTRATO, CAMBIOS_EN_LA_FICHA);
            }
            LocalDate fin = fecha(f, Columna.FIN_CONTRATO, false);
            if (fin != null && !fin.equals(a.finContrato())) errorEn(f, Columna.FIN_CONTRATO, CAMBIOS_EN_LA_FICHA);
            LocalDate prueba = fecha(f, Columna.FIN_PRUEBA, false);
            if (prueba != null && !prueba.equals(a.finPeriodoPrueba())) {
                errorEn(f, Columna.FIN_PRUEBA, CAMBIOS_EN_LA_FICHA);
            }
            String regimen = deLista(f, Columna.REGIMEN, CatalogosDePersonas.REGIMENES, false);
            if (regimen != null && !regimen.equals(a.regimenLaboral())) errorEn(f, Columna.REGIMEN, CAMBIOS_EN_LA_FICHA);
            sueldo(v);
            if (v.sueldo != null && !ServicioColaboradoresImpl.mismoSueldo(v.sueldo, v.moneda, a.sueldoBase(), a.moneda())) {
                errorEn(f, Columna.SUELDO, CAMBIOS_EN_LA_FICHA);
            }
            v.sueldo = null;
            v.moneda = null;
        }

        private void sueldo(FilaValida v) {
            if (!sueldos) return;
            Fila f = v.fila;
            if (f.tiene(Columna.SUELDO)) {
                Celda celda = f.de(Columna.SUELDO);
                BigDecimal monto = null;
                if (celda.numero() != null) {
                    monto = BigDecimal.valueOf(celda.numero());
                } else {
                    String limpio = celda.texto().replaceAll("(?i)s/|us\\$|\\s", "").replace(",", "");
                    try {
                        monto = new BigDecimal(limpio);
                    } catch (NumberFormatException e) {
                        errorEn(f, Columna.SUELDO, "El sueldo tiene que ser un número");
                    }
                }
                if (monto != null) {
                    monto = monto.stripTrailingZeros();
                    if (monto.scale() < 0) monto = monto.setScale(0);
                    String error = ReglasDelColaborador.errorDelSueldo(monto, null);
                    if (error != null) errorEn(f, Columna.SUELDO, error);
                    else v.sueldo = monto;
                }
            }
            if (f.tiene(Columna.MONEDA)) {
                v.moneda = moneda(f.de(Columna.MONEDA).texto());
                if (v.moneda == null) errorEn(f, Columna.MONEDA, "La moneda tiene que ser soles o dólares");
            }
            if (v.sueldo != null && v.moneda == null && !f.tiene(Columna.MONEDA)) {
                v.moneda = "PEN";
            }
        }

        private String claveDelJefe(Fila f) {
            boolean hayTipo = f.tiene(Columna.JEFE_TIPO);
            boolean hayNumero = f.tiene(Columna.JEFE_NUMERO);
            if (!hayTipo && !hayNumero) return null;
            if (!hayNumero) {
                errorEn(f, Columna.JEFE_NUMERO, "Falta el número de documento del jefe");
                return null;
            }
            String tipo = hayTipo ? deLista(f, Columna.JEFE_TIPO, CatalogosDePersonas.TIPOS_DOCUMENTO, false)
                    : null;
            if (!hayTipo) {
                errorEn(f, Columna.JEFE_TIPO, "Falta el tipo de documento del jefe");
                return null;
            }
            if (tipo == null) return null;
            String numero = ReglasDelColaborador.limpiarDocumento(f.de(Columna.JEFE_NUMERO).texto());
            String error = ReglasDelColaborador.errorDelDocumento(tipo, numero);
            if (error != null) {
                errorEn(f, Columna.JEFE_NUMERO, error);
                return null;
            }
            return clave(tipo, numero);
        }

        private void jefes() {
            Map<String, String> jefeDe = new HashMap<>();
            Map<Long, String> clavePorId = new HashMap<>();
            actualPorClave.forEach((clave, a) -> clavePorId.put(a.id(), clave));
            actualPorClave.forEach((clave, a) -> {
                if (a.jefeId() != null && clavePorId.containsKey(a.jefeId())) {
                    jefeDe.put(clave, clavePorId.get(a.jefeId()));
                }
            });
            for (FilaValida v : validas) {
                if (v.esAlta() && v.jefeClave != null) jefeDe.put(v.clave, v.jefeClave);
            }

            for (FilaValida v : validas) {
                if (v.jefeClave == null) continue;
                Fila f = v.fila;
                if (v.jefeClave.equals(v.clave)) {
                    errorEn(f, Columna.JEFE_NUMERO, "Nadie puede ser su propio jefe directo");
                    continue;
                }
                FilaValida enElArchivo = porClave.get(v.jefeClave);
                Actual enLaEmpresa = actualPorClave.get(v.jefeClave);
                if (!v.esAlta()) {
                    // Quien ya existe no cambia de jefe por aquí: tiene que ser el que ya tiene.
                    Long jefeActual = v.existente.jefeId();
                    boolean mismo = enLaEmpresa != null && enLaEmpresa.id().equals(jefeActual);
                    if (!mismo) errorEn(f, Columna.JEFE_NUMERO, CAMBIOS_EN_LA_FICHA);
                    continue;
                }
                if (enElArchivo == null && enLaEmpresa == null) {
                    errorEn(f, Columna.JEFE_NUMERO,
                            "No hay ningún colaborador con ese documento, ni en la empresa ni en este archivo");
                    continue;
                }
                if (enElArchivo == null && "CESADO".equals(enLaEmpresa.estado())) {
                    errorEn(f, Columna.JEFE_NUMERO, "El jefe está cesado: tiene que estar activo o por ingresar");
                    continue;
                }
                Map<String, String> sinEsta = new HashMap<>(jefeDe);
                sinEsta.remove(v.clave);
                if (JefesSinCirculo.creaCirculo(v.clave, v.jefeClave, sinEsta)) {
                    errorEn(f, Columna.JEFE_NUMERO, enElArchivo != null
                            ? "Crea un círculo con la fila " + enElArchivo.fila.numero()
                            : "Crea un círculo de jefes");
                }
            }
        }

        // ============ Aplicar ============

        ResultadoCarga aplicar(ContextoUsuario quien) {
            Instant ahora = Instant.now();
            int actualizados = 0;
            List<FilaValida> actualizaciones = validas.stream().filter(v -> !v.esAlta()).toList();
            if (!actualizaciones.isEmpty()) {
                Map<Long, Colaborador> fichas = colaboradores.findByOrganizacionIdAndIdIn(org,
                                actualizaciones.stream().map(v -> v.existente.id()).toList())
                        .stream().collect(Collectors.toMap(Colaborador::getId, Function.identity()));
                List<Colaborador> cambiadas = new ArrayList<>();
                for (FilaValida v : actualizaciones) {
                    Colaborador c = fichas.get(v.existente.id());
                    if (c != null && actualizar(c, v)) {
                        c.setActualizadoEn(ahora);
                        cambiadas.add(c);
                    }
                }
                colaboradores.saveAll(cambiadas);
                actualizados = cambiadas.size();
            }

            List<FilaValida> altas = validas.stream().filter(FilaValida::esAlta).toList();
            Map<String, Long> idPorClave = new HashMap<>();
            actualPorClave.forEach((clave, a) -> idPorClave.put(clave, a.id()));
            List<Colaborador> nuevas = new ArrayList<>();
            for (FilaValida v : altas) {
                nuevas.add(Colaborador.builder()
                        .organizacionId(org).tipoDocumento(v.tipo).numeroDocumento(v.numero)
                        .nombres(v.nombres).apellidoPaterno(v.paterno).apellidoMaterno(v.materno)
                        .fechaNacimiento(v.nacimiento).sexo(v.sexo).estadoCivil(v.estadoCivil)
                        .nacionalidad(v.nacionalidad).celular(v.celular).correoPersonal(v.correoPersonal)
                        .correoCorporativo(v.correoCorporativo).direccion(v.direccion)
                        .provinciaUbigeo(v.provincia).nivelEducativoCodigo(v.nivelEducativo)
                        .creadoPorUsuarioId(quien.usuarioId()).creadoEn(ahora).actualizadoEn(ahora)
                        .build());
            }
            List<Colaborador> guardadas = colaboradores.saveAllAndFlush(nuevas);
            for (int i = 0; i < altas.size(); i++) {
                idPorClave.put(altas.get(i).clave, guardadas.get(i).getId());
            }
            List<PeriodoLaboral> nuevosPeriodos = new ArrayList<>();
            for (int i = 0; i < altas.size(); i++) {
                nuevosPeriodos.add(PeriodoLaboral.builder().colaboradorId(guardadas.get(i).getId())
                        .fechaIngreso(altas.get(i).ingreso).creadoPorUsuarioId(quien.usuarioId())
                        .creadoEn(ahora).build());
            }
            List<PeriodoLaboral> periodosGuardados = periodos.saveAll(nuevosPeriodos);
            List<SituacionLaboral> nuevas_situaciones = new ArrayList<>();
            for (int i = 0; i < altas.size(); i++) {
                FilaValida v = altas.get(i);
                nuevas_situaciones.add(SituacionLaboral.builder()
                        .colaboradorId(guardadas.get(i).getId()).periodoId(periodosGuardados.get(i).getId())
                        .vigenteDesde(v.ingreso).sedeId(v.sedeId).areaId(v.areaId).puestoId(v.cargoId)
                        .jefeColaboradorId(v.jefeClave == null ? null : idPorClave.get(v.jefeClave))
                        .tipoContrato(v.tipoContrato).finContrato(v.finContrato).finPeriodoPrueba(v.finPrueba)
                        .regimenLaboral(v.regimen).sueldoBase(v.sueldo).moneda(v.sueldo == null ? null : v.moneda)
                        .tipoMotivo("CARGA_INICIAL").registradoPorUsuarioId(quien.usuarioId())
                        .registradoEn(ahora).build());
            }
            situaciones.saveAll(nuevas_situaciones);
            return new ResultadoCarga(altas.size(), actualizados);
        }

        /** Lo que trae la fila y no está vacío. Devuelve si cambió algo. */
        private boolean actualizar(Colaborador c, FilaValida v) {
            boolean cambio = false;
            cambio |= poner(v.nombres, c.getNombres(), c::setNombres);
            cambio |= poner(v.paterno, c.getApellidoPaterno(), c::setApellidoPaterno);
            cambio |= poner(v.materno, c.getApellidoMaterno(), c::setApellidoMaterno);
            cambio |= poner(v.nacimiento, c.getFechaNacimiento(), c::setFechaNacimiento);
            cambio |= poner(v.sexo, c.getSexo(), c::setSexo);
            cambio |= poner(v.estadoCivil, c.getEstadoCivil(), c::setEstadoCivil);
            cambio |= poner(v.nacionalidad, c.getNacionalidad(), c::setNacionalidad);
            cambio |= poner(v.celular, c.getCelular(), c::setCelular);
            cambio |= poner(v.correoPersonal, c.getCorreoPersonal(), c::setCorreoPersonal);
            cambio |= poner(v.correoCorporativo, c.getCorreoCorporativo(), c::setCorreoCorporativo);
            cambio |= poner(v.direccion, c.getDireccion(), c::setDireccion);
            cambio |= poner(v.provincia, c.getProvinciaUbigeo(), c::setProvinciaUbigeo);
            cambio |= poner(v.nivelEducativo, c.getNivelEducativoCodigo(), c::setNivelEducativoCodigo);
            return cambio;
        }

        private <T> boolean poner(T nuevo, T actual, java.util.function.Consumer<T> asignar) {
            if (nuevo == null || Objects.equals(nuevo, actual)) return false;
            asignar.accept(nuevo);
            return true;
        }

        // ============ Leer celdas ============

        private void errorEn(Fila fila, Columna columna, String mensaje) {
            String valor = fila.de(columna).texto();
            // El sueldo no se repite en los mensajes: quien lo lea puede no tener ver_sueldos.
            errores.add(new ErrorDeCarga(fila.numero(), columna.titulo,
                    columna == Columna.SUELDO ? "" : valor == null ? "" : valor, mensaje));
        }

        private LocalDate fecha(Fila f, Columna columna, boolean obligatoria) {
            Celda celda = f.de(columna);
            if (celda.vacia()) {
                if (obligatoria) errorEn(f, columna, "Falta el dato obligatorio");
                return null;
            }
            if (celda.fecha() != null) return celda.fecha();
            if (celda.numero() != null && celda.numero() > 0 && celda.numero() < 2_958_466) {
                // Un número de serie de Excel en una celda sin formato de fecha.
                return DateUtil.getLocalDateTime(celda.numero()).toLocalDate();
            }
            String texto = celda.texto().strip();
            for (DateTimeFormatter formato : FORMATOS_DE_FECHA) {
                try {
                    return LocalDate.parse(texto, formato.withResolverStyle(java.time.format.ResolverStyle.STRICT));
                } catch (DateTimeParseException e) {
                    // se prueba el siguiente formato
                }
            }
            errorEn(f, columna, "No es una fecha válida: usa dd/mm/aaaa");
            return null;
        }

        private String deLista(Fila f, Columna columna, Map<String, String> catalogo, boolean obligatoria) {
            Celda celda = f.de(columna);
            if (celda.vacia()) {
                if (obligatoria) errorEn(f, columna, "Falta el dato obligatorio");
                return null;
            }
            String buscado = LibroDeColaboradores.normalizar(celda.texto());
            for (Map.Entry<String, String> opcion : catalogo.entrySet()) {
                if (alias(opcion.getKey(), opcion.getValue()).contains(buscado)) {
                    return opcion.getKey();
                }
            }
            errorEn(f, columna, "No es un valor de la lista: mira la hoja «Valores»");
            return null;
        }

        private <T> T unico(Fila f, Columna columna, Map<String, List<T>> porNombre, String que,
                            boolean obligatoria) {
            Celda celda = f.de(columna);
            if (celda.vacia()) {
                if (obligatoria) errorEn(f, columna, "Falta el dato obligatorio");
                return null;
            }
            List<T> encontrados = porNombre.getOrDefault(LibroDeColaboradores.normalizar(celda.texto()), List.of());
            if (encontrados.isEmpty()) {
                errorEn(f, columna, "No existe " + que + " «" + celda.texto() + "»");
                return null;
            }
            if (encontrados.size() > 1) {
                errorEn(f, columna, "Hay más de un" + (que.startsWith("la") ? "a " : " ") + que.substring(3)
                        + " con ese nombre: corrígelo en Configuración");
                return null;
            }
            return encontrados.get(0);
        }

        private String correo(Fila f, Columna columna) {
            String correo = ReglasDelColaborador.texto(f.de(columna).texto());
            String error = ReglasDelColaborador.errorDelCorreo(correo);
            if (error != null) {
                errorEn(f, columna, error);
                return null;
            }
            return correo;
        }
    }

    /** Lo que se acepta en una celda de lista: el texto, el código y variantes obvias. */
    private static List<String> alias(String codigo, String nombre) {
        List<String> alias = new ArrayList<>();
        alias.add(LibroDeColaboradores.normalizar(codigo));
        String n = LibroDeColaboradores.normalizar(nombre);
        alias.add(n);
        if (n.endsWith("(a)")) {
            String sin = n.substring(0, n.length() - 3).strip();
            alias.add(sin);
            if (sin.endsWith("o")) alias.add(sin.substring(0, sin.length() - 1) + "a");
        }
        return alias;
    }

    private static String moneda(String texto) {
        String n = LibroDeColaboradores.normalizar(texto);
        return switch (n) {
            case "pen", "s/", "s/.", "soles", "sol", "soles (s/)" -> "PEN";
            case "usd", "us$", "$", "dolares", "dolar", "dolares (us$)" -> "USD";
            default -> null;
        };
    }

    private static String clave(String tipo, String numero) {
        return tipo + ":" + numero;
    }

    private static boolean alcanceTodo(ContextoUsuario quien, String permiso) {
        return "TODO".equals(quien.alcance(permiso));
    }

    private static void exigirTodo(ContextoUsuario quien, String permiso) {
        String alcance = quien.alcance(permiso);
        if (alcance == null) {
            throw new AccessDeniedException("No tienes el permiso «" + permiso + "»");
        }
        if (!"TODO".equals(alcance)) {
            throw new ResourceNotFoundException("Colaborador", "alcance", permiso);
        }
    }
}
