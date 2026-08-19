package com.renaser.ai.ai_engine.ai.controller;

import com.renaser.ai.ai_engine.ai.supabase.ActividadRecord;
import com.renaser.ai.ai_engine.ai.supabase.AvisoRecord;
import com.renaser.ai.ai_engine.ai.supabase.CobroRecord;
import com.renaser.ai.ai_engine.ai.supabase.EntregableRecord;
import com.renaser.ai.ai_engine.ai.supabase.EventoRecord;
import com.renaser.ai.ai_engine.ai.supabase.MotorRecord;
import com.renaser.ai.ai_engine.ai.supabase.ProspectoRecord;
import com.renaser.ai.ai_engine.ai.supabase.SupabaseDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/supabase")
@RequiredArgsConstructor
public class SupabaseController {

    private final SupabaseDataService supabaseDataService;

    @GetMapping("/motores")
    public ResponseEntity<List<MotorRecord>> motores() {
        return ResponseEntity.ok(supabaseDataService.getMotores());
    }

    @GetMapping("/cobros")
    public ResponseEntity<List<CobroRecord>> cobros(@RequestParam String cliente) {
        return ResponseEntity.ok(supabaseDataService.getCobrosByCliente(cliente));
    }

    @GetMapping("/actividades-bloqueadas")
    public ResponseEntity<List<ActividadRecord>> actividadesBloqueadas() {
        return ResponseEntity.ok(supabaseDataService.getActividadesBloqueadas());
    }

    @GetMapping("/prospectos")
    public ResponseEntity<List<ProspectoRecord>> prospectos() {
        return ResponseEntity.ok(supabaseDataService.getProspectos());
    }

    @GetMapping("/eventos")
    public ResponseEntity<List<EventoRecord>> evento(@RequestParam String nombre) {
        return ResponseEntity.ok(supabaseDataService.getEventoByNombre(nombre));
    }

    @GetMapping("/entregables-pendientes")
    public ResponseEntity<List<EntregableRecord>> entregablesPendientes() {
        return ResponseEntity.ok(supabaseDataService.getEntregablesPendientes());
    }

    @GetMapping("/avisos-activos")
    public ResponseEntity<List<AvisoRecord>> avisosActivos() {
        return ResponseEntity.ok(supabaseDataService.getAvisosActivos());
    }
}
