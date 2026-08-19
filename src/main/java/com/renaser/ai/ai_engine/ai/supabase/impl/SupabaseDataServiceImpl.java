package com.renaser.ai.ai_engine.ai.supabase.impl;

import com.renaser.ai.ai_engine.ai.supabase.ActividadRecord;
import com.renaser.ai.ai_engine.ai.supabase.AvisoRecord;
import com.renaser.ai.ai_engine.ai.supabase.CobroRecord;
import com.renaser.ai.ai_engine.ai.supabase.EntregableRecord;
import com.renaser.ai.ai_engine.ai.supabase.EventoRecord;
import com.renaser.ai.ai_engine.ai.supabase.MotorRecord;
import com.renaser.ai.ai_engine.ai.supabase.ProspectoRecord;
import com.renaser.ai.ai_engine.ai.supabase.SupabaseDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupabaseDataServiceImpl implements SupabaseDataService {

    // Ninguna consulta puede crecer sin techo: lo que sale de aquí termina dentro del prompt,
    // y un prompt sin límite desborda la ventana de contexto del modelo.
    private static final int DETAIL_LIMIT = 15;
    private static final int AVISOS_LIMIT = 8;
    private static final int AGGREGATE_LIMIT = 1000;

    private final RestClient supabaseRestClient;

    @Override
    public List<MotorRecord> getMotores() {
        MotorRecord[] motores = supabaseRestClient.get()
                .uri("/motores?select=*&order=n")
                .retrieve()
                .body(MotorRecord[].class);
        return motores == null ? List.of() : List.of(motores);
    }

    @Override
    public List<CobroRecord> getCobrosByCliente(String cliente) {
        CobroRecord[] cobros = supabaseRestClient.get()
                .uri("/cobros?select=*&cliente=eq.{cliente}&order=vence.asc&limit={limit}",
                        cliente, DETAIL_LIMIT)
                .retrieve()
                .body(CobroRecord[].class);
        return cobros == null ? List.of() : List.of(cobros);
    }

    // prioridad_tipo está definido como ('alta','media','baja'): asc devuelve las altas
    // primero. Sin este order, el limit recortaba 15 filas arbitrarias de 700 — el agente
    // opinaba sobre una muestra al azar, no sobre lo más urgente.
    @Override
    public List<ActividadRecord> getActividadesBloqueadas() {
        ActividadRecord[] actividades = supabaseRestClient.get()
                .uri("/actividades?select=titulo,estado,prioridad,bloqueo,avance,limite"
                        + "&bloqueo=not.is.null&order=prioridad.asc,created_at.asc&limit={limit}", DETAIL_LIMIT)
                .retrieve()
                .body(ActividadRecord[].class);
        return actividades == null ? List.of() : List.of(actividades);
    }

    // Growth agrega por etapa aguas arriba, así que el tope aquí sólo protege la memoria del
    // proceso. Si el embudo supera AGGREGATE_LIMIT el agregado deja de ser exacto: a esa
    // escala corresponde una vista SQL (v_embudo) que agregue en Postgres, no en Java.
    @Override
    public List<ProspectoRecord> getProspectos() {
        ProspectoRecord[] prospectos = supabaseRestClient.get()
                .uri("/crm_prospectos?select=*&order=created_at.desc&limit={limit}", AGGREGATE_LIMIT)
                .retrieve()
                .body(ProspectoRecord[].class);
        return prospectos == null ? List.of() : List.of(prospectos);
    }

    @Override
    public List<EventoRecord> getEventoByNombre(String nombre) {
        EventoRecord[] eventos = supabaseRestClient.get()
                .uri("/eventos?select=*&nombre=eq.{nombre}", nombre)
                .retrieve()
                .body(EventoRecord[].class);
        return eventos == null ? List.of() : List.of(eventos);
    }

    @Override
    public List<EntregableRecord> getEntregablesPendientes() {
        EntregableRecord[] entregables = supabaseRestClient.get()
                .uri("/entregables?select=nombre,tipo,estado,version,created_at"
                        + "&estado=eq.pendiente_revision&order=created_at.asc&limit={limit}", DETAIL_LIMIT)
                .retrieve()
                .body(EntregableRecord[].class);
        return entregables == null ? List.of() : List.of(entregables);
    }

    // semaforo está definido como ('ok','warn','crit'): desc devuelve los críticos primero.
    @Override
    public List<AvisoRecord> getAvisosActivos() {
        AvisoRecord[] avisos = supabaseRestClient.get()
                .uri("/avisos?select=titulo,detalle,sev,area,responsable,leida"
                        + "&leida=eq.false&sev=neq.ok&order=sev.desc,created_at.desc&limit={limit}", AVISOS_LIMIT)
                .retrieve()
                .body(AvisoRecord[].class);
        return avisos == null ? List.of() : List.of(avisos);
    }
}
