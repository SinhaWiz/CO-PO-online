package org.example.copo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.CO;
import org.example.copo.entity.PO;
import org.example.copo.service.OutcomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/outcomes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OutcomeController {

    private final OutcomeService outcomeService;

    @GetMapping("/co")
    public ResponseEntity<List<CO>> getAllCOs() {
        return ResponseEntity.ok(outcomeService.getAllCOs());
    }

    @PostMapping("/co")
    public ResponseEntity<CO> createCO(@Valid @RequestBody CO co) {
        return ResponseEntity.ok(outcomeService.createCO(co));
    }

    @DeleteMapping("/co/{id}")
    public ResponseEntity<?> deleteCO(@PathVariable Integer id) {
        outcomeService.deleteCO(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/po")
    public ResponseEntity<List<PO>> getAllPOs() {
        return ResponseEntity.ok(outcomeService.getAllPOs());
    }

    @PostMapping("/po")
    public ResponseEntity<PO> createPO(@Valid @RequestBody PO po) {
        return ResponseEntity.ok(outcomeService.createPO(po));
    }

    @DeleteMapping("/po/{id}")
    public ResponseEntity<?> deletePO(@PathVariable Integer id) {
        outcomeService.deletePO(id);
        return ResponseEntity.ok().build();
    }
}
