package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.GuardarPresentacion;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.PresentacionPanel;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.SedeDeLaPresentacion;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.entity.OrganizacionBeneficio;
import com.renaser.ai.ai_engine.organizacion.entity.OrganizacionRedSocial;
import com.renaser.ai.ai_engine.organizacion.entity.Sede;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionBeneficioRepository;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRedSocialRepository;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.repository.SedeRepository;
import com.renaser.ai.ai_engine.organizacion.service.ImagenesDeLaEmpresa;
import com.renaser.ai.ai_engine.organizacion.service.ImagenesDeLaEmpresa.Cual;
import com.renaser.ai.ai_engine.organizacion.service.ImagenesDeLaEmpresa.ImagenLimpia;
import com.renaser.ai.ai_engine.organizacion.service.RedSocial;
import com.renaser.ai.ai_engine.organizacion.service.ReglasDeLaPresentacion;
import com.renaser.ai.ai_engine.organizacion.service.ReglasDeLaPresentacion.PresentacionLimpia;
import com.renaser.ai.ai_engine.organizacion.service.SedesDeLaEmpresa;
import com.renaser.ai.ai_engine.organizacion.service.ServicioPresentacion;
import com.renaser.ai.ai_engine.organizacion.service.TamanoDeEmpresa;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ver {@link ServicioPresentacion}. */
@Service
@RequiredArgsConstructor
public class ServicioPresentacionImpl implements ServicioPresentacion {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final OrganizacionRepository organizaciones;
    private final OrganizacionBeneficioRepository beneficios;
    private final OrganizacionRedSocialRepository redes;
    private final SedeRepository sedes;
    private final CatalogosDelPerfil catalogos;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final ServicioAuditoria auditoria;

    @Override
    @Transactional(readOnly = true)
    public PresentacionPanel ver(ContextoUsuario quien) {
        return comoPanel(laMia(quien));
    }

    @Override
    @Transactional
    public PresentacionPanel guardar(ContextoUsuario quien, GuardarPresentacion datos) {
        // La fila primero, bloqueada: dos guardados a la vez pasan de uno en uno y gana el último.
        Organizacion org = laMiaParaEscribir(quien);
        // Todo se comprueba antes de tocar nada: un campo que no vale no deja media presentación.
        PresentacionLimpia limpia = ReglasDeLaPresentacion.limpiar(datos,
                LocalDate.now(LIMA).getYear());

        Map<String, Object> antes = comoMapa(org, textosDe(beneficios.findByOrganizacionIdOrderByOrden(org.getId())),
                redesDe(redes.findByOrganizacionId(org.getId())));

        org.setSector(limpia.sector());
        org.setTamano(limpia.tamano());
        org.setAnioFundacion(limpia.anioFundacion());
        org.setQuienesSomos(limpia.quienesSomos());
        org.setPropositoEmpresa(limpia.propositoEmpresa());
        org.setVideoPresentacion(limpia.videoPresentacion());
        org.setPaginaWeb(limpia.paginaWeb());
        organizaciones.save(org);

        beneficios.borrarLosDe(org.getId());
        List<OrganizacionBeneficio> nuevos = new ArrayList<>();
        for (int i = 0; i < limpia.beneficios().size(); i++) {
            nuevos.add(OrganizacionBeneficio.builder().organizacionId(org.getId())
                    .texto(limpia.beneficios().get(i)).orden(i + 1).build());
        }
        beneficios.saveAll(nuevos);

        redes.borrarLasDe(org.getId());
        List<OrganizacionRedSocial> nuevas = new ArrayList<>();
        limpia.redes().forEach((red, enlace) -> nuevas.add(OrganizacionRedSocial.builder()
                .organizacionId(org.getId()).red(red.name()).enlace(enlace).build()));
        redes.saveAll(nuevas);

        Map<String, String> redesNuevas = new LinkedHashMap<>();
        limpia.redes().forEach((red, enlace) -> redesNuevas.put(red.name(), enlace));
        Map<String, Object> despues = comoMapa(org, limpia.beneficios(), redesNuevas);
        // Reenviar lo mismo —el doble clic, el reintento— no vuelve a auditar.
        if (!antes.equals(despues)) {
            auditoria.registrar(org.getId(), quien, "editar_presentacion_empresa", "organizacion",
                    org.getId(), antes, despues, null);
        }
        return comoPanel(org);
    }

    @Override
    @Transactional
    public PresentacionPanel guardarImagen(ContextoUsuario quien, Cual cual, MultipartFile archivo) {
        Organizacion org = laMiaParaEscribir(quien);
        // Se limpia antes de guardar nada: si no vale, el logo de antes sigue donde estaba.
        ImagenLimpia limpia = ImagenesDeLaEmpresa.recodificar(archivo, cual);
        Archivo nuevo = almacen.guardarImagen(org.getId(), new ImagenEnMemoria(
                cual.codigo() + "." + limpia.extension(), limpia.tipo(), limpia.bytes()));
        Long anterior = idDe(org, cual);
        soltar(org.getId(), anterior);
        poner(org, cual, nuevo.getId());
        organizaciones.save(org);
        auditoria.registrar(org.getId(), quien, "cambiar_" + cual.codigo() + "_empresa",
                "organizacion", org.getId(), mapaDeImagen(cual, anterior),
                mapaDeImagen(cual, nuevo.getId()), null);
        return comoPanel(org);
    }

    @Override
    @Transactional
    public PresentacionPanel quitarImagen(ContextoUsuario quien, Cual cual) {
        Organizacion org = laMiaParaEscribir(quien);
        Long anterior = idDe(org, cual);
        if (anterior == null) {
            return comoPanel(org);
        }
        soltar(org.getId(), anterior);
        poner(org, cual, null);
        organizaciones.save(org);
        auditoria.registrar(org.getId(), quien, "quitar_" + cual.codigo() + "_empresa",
                "organizacion", org.getId(), mapaDeImagen(cual, anterior),
                mapaDeImagen(cual, null), null);
        return comoPanel(org);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean tieneVideo(ContextoUsuario quien) {
        return laMia(quien).getVideoPresentacion() != null;
    }

    // ==================== Lo común ====================

    private Organizacion laMia(ContextoUsuario quien) {
        return organizaciones.findById(quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organización", "id",
                        quien.organizacionId()));
    }

    private Organizacion laMiaParaEscribir(ContextoUsuario quien) {
        return organizaciones.bloquear(quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organización", "id",
                        quien.organizacionId()));
    }

    private PresentacionPanel comoPanel(Organizacion org) {
        List<String> suyos = textosDe(beneficios.findByOrganizacionIdOrderByOrden(org.getId()));
        Map<String, String> suyas = redesDe(redes.findByOrganizacionId(org.getId()));
        List<Sede> activas = sedes.findByOrganizacionIdAndEsActivaTrueOrderByNombre(org.getId());
        Map<String, OpcionUbigeo> provincias = SedesDeLaEmpresa.algunaConProvincia(activas)
                ? catalogos.ciudadesPorCodigo() : Map.of();
        List<SedeDeLaPresentacion> lista = activas.stream()
                .map(s -> new SedeDeLaPresentacion(s.getNombre(),
                        SedesDeLaEmpresa.nombreDe(s.getProvinciaUbigeo(), provincias)))
                .toList();
        return new PresentacionPanel(org.getCodigo(), org.getNombre(), org.getSector(),
                org.getTamano(), TamanoDeEmpresa.textoDe(org.getTamano()), org.getAnioFundacion(),
                org.getQuienesSomos(), org.getPropositoEmpresa(), suyos,
                org.getVideoPresentacion(), org.getPaginaWeb(), suyas,
                ImagenesDeLaEmpresa.direccionPublica(org.getCodigo(), Cual.LOGO,
                        org.getLogoArchivoId()),
                ImagenesDeLaEmpresa.direccionPublica(org.getCodigo(), Cual.PORTADA,
                        org.getPortadaArchivoId()),
                lista, SedesDeLaEmpresa.ciudad(activas, provincias));
    }

    private static List<String> textosDe(List<OrganizacionBeneficio> filas) {
        return filas.stream().map(OrganizacionBeneficio::getTexto).toList();
    }

    /** Las redes en el orden de siempre (Facebook, Instagram, LinkedIn, TikTok, YouTube). */
    private static Map<String, String> redesDe(List<OrganizacionRedSocial> filas) {
        Map<String, String> mapa = new LinkedHashMap<>();
        filas.stream()
                .sorted(Comparator.comparingInt(r -> RedSocial.deCodigo(r.getRed())
                        .map(Enum::ordinal).orElse(Integer.MAX_VALUE)))
                .forEach(r -> mapa.put(r.getRed(), r.getEnlace()));
        return mapa;
    }

    /** Lo que la auditoría guarda de la presentación, antes y después. */
    private static Map<String, Object> comoMapa(Organizacion org, List<String> beneficios,
                                                Map<String, String> redes) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("sector", org.getSector());
        mapa.put("tamano", org.getTamano());
        mapa.put("anioFundacion", org.getAnioFundacion());
        mapa.put("quienesSomos", org.getQuienesSomos());
        mapa.put("propositoEmpresa", org.getPropositoEmpresa());
        mapa.put("beneficios", List.copyOf(beneficios));
        mapa.put("videoPresentacion", org.getVideoPresentacion());
        mapa.put("paginaWeb", org.getPaginaWeb());
        mapa.put("redes", new LinkedHashMap<>(redes));
        return mapa;
    }

    private static Map<String, Object> mapaDeImagen(Cual cual, Long archivoId) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put(cual.codigo() + "ArchivoId", archivoId);
        return mapa;
    }

    private static Long idDe(Organizacion org, Cual cual) {
        return cual == Cual.LOGO ? org.getLogoArchivoId() : org.getPortadaArchivoId();
    }

    private static void poner(Organizacion org, Cual cual, Long archivoId) {
        if (cual == Cual.LOGO) {
            org.setLogoArchivoId(archivoId);
        } else {
            org.setPortadaArchivoId(archivoId);
        }
    }

    /**
     * Suelta la imagen anterior: se borra su contenido y la fila se queda, como en el perfil del
     * candidato. Se busca con la organización: solo se suelta lo que es de esta empresa.
     */
    private void soltar(Long organizacionId, Long archivoId) {
        if (archivoId == null) {
            return;
        }
        archivos.findByIdAndOrganizacionId(archivoId, organizacionId)
                .filter(a -> a.getBorradoEn() == null)
                .ifPresent(almacen::borrarContenido);
    }
}
