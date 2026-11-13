package com.substring.auth.app.officer.controller;

import com.substring.auth.app.officer.dto.OfficerDirectoryDto;
import com.substring.auth.app.officer.service.OfficerDirectoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/officers")
public class OfficerController {

    private final OfficerDirectoryService officerDirectoryService;

    public OfficerController(OfficerDirectoryService officerDirectoryService) {
        this.officerDirectoryService = officerDirectoryService;
    }

    @GetMapping
    public ResponseEntity<List<OfficerDirectoryDto>> getAllOfficers() {
        return ResponseEntity.ok(officerDirectoryService.getAllOfficersCached());
    }

    @GetMapping("/available")
    public ResponseEntity<List<OfficerDirectoryDto>> getAvailableOfficers(
            @RequestParam(required = false) UUID teamId
    ) {
        if (teamId != null) {
            return ResponseEntity.ok(officerDirectoryService.getAvailableOfficersForTeam(teamId));
        }
        return ResponseEntity.ok(officerDirectoryService.getAllOfficersCached());
    }

    @PostMapping("/cache/evict")
    public ResponseEntity<Void> evictCache() {
        officerDirectoryService.evictDirectoryCache();
        return ResponseEntity.noContent().build();
    }
}
