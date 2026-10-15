package com.tuwaiq.flowsense.Controller;

import com.tuwaiq.flowsense.Api.ApiResponse;
import com.tuwaiq.flowsense.Service.AutopilotService;
import com.tuwaiq.flowsense.Service.OpsActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/autopilot")
@RequiredArgsConstructor
public class AutopilotController {

    private final AutopilotService autopilotService;
    private final OpsActionService opsActionService;

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.status(200).body(autopilotService.getStatus());
    }

    @PutMapping("/enable")
    public ResponseEntity<?> enable() {
        autopilotService.enable();
        return ResponseEntity.status(200).body(new ApiResponse("Autopilot launched"));
    }

    @PutMapping("/disable")
    public ResponseEntity<?> disable() {
        autopilotService.disable();
        return ResponseEntity.status(200).body(new ApiResponse("Back to manual control"));
    }

    // Decision log: every action by Autopilot, supervisors and gate staff, newest first
    @GetMapping("/actions/get")
    public ResponseEntity<?> getActions() {
        return ResponseEntity.status(200).body(opsActionService.getRecent());
    }
}
