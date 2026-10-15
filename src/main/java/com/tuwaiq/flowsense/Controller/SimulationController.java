package com.tuwaiq.flowsense.Controller;

import com.tuwaiq.flowsense.Api.ApiResponse;
import com.tuwaiq.flowsense.Service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Demo only: generates realistic visitor scans so the dashboard has live data
@RestController
@RequestMapping("/v1/api/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.status(200).body(new ApiResponse(simulationService.isRunning() ? "RUNNING" : "STOPPED"));
    }

    @PostMapping("/start")
    public ResponseEntity<?> start() {
        simulationService.start();
        return ResponseEntity.status(200).body(new ApiResponse("Simulation started"));
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        simulationService.stop();
        return ResponseEntity.status(200).body(new ApiResponse("Simulation stopped"));
    }

    @PostMapping("/surge/{pavilionId}")
    public ResponseEntity<?> surge(@PathVariable Integer pavilionId) {
        int people = simulationService.surge(pavilionId);
        return ResponseEntity.status(200).body(new ApiResponse("A crowd of " + people + " visitors is arriving now"));
    }

    @DeleteMapping("/reset")
    public ResponseEntity<?> reset() {
        simulationService.reset();
        return ResponseEntity.status(200).body(new ApiResponse("All tickets, scans and history deleted"));
    }
}
