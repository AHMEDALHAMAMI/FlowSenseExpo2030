package com.tuwaiq.flowsense.Controller;

import com.tuwaiq.flowsense.Api.ApiResponse;
import com.tuwaiq.flowsense.Model.Pavilion;
import com.tuwaiq.flowsense.Service.NudgeService;
import com.tuwaiq.flowsense.Service.PavilionService;
import com.tuwaiq.flowsense.Service.WaitTimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/pavilion")
@RequiredArgsConstructor
public class PavilionController {

    private final PavilionService pavilionService;
    private final WaitTimeService waitTimeService;
    private final NudgeService nudgeService;

    @GetMapping("/get")
    public ResponseEntity<?> getAllPavilions() {
        return ResponseEntity.status(200).body(pavilionService.getAllPavilions());
    }

    @PostMapping("/add")
    public ResponseEntity<?> addPavilion(@RequestBody @Valid Pavilion pavilion) {
        pavilionService.addPavilion(pavilion);
        return ResponseEntity.status(200).body(new ApiResponse("Pavilion added"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updatePavilion(@PathVariable Integer id, @RequestBody @Valid Pavilion pavilion) {
        pavilionService.updatePavilion(id, pavilion);
        return ResponseEntity.status(200).body(new ApiResponse("Pavilion updated"));
    }

    @PutMapping("/status/{id}/{status}")
    public ResponseEntity<?> changeStatus(@PathVariable Integer id, @PathVariable String status) {
        pavilionService.changeStatus(id, status);
        return ResponseEntity.status(200).body(new ApiResponse("Pavilion status changed to " + status));
    }

    // Supervisor opens / closes the overflow lane (the Apply button on a recommendation)
    @PutMapping("/overflow/{id}/{lane}")
    public ResponseEntity<?> changeOverflow(@PathVariable Integer id, @PathVariable String lane) {
        pavilionService.changeOverflow(id, lane);
        return ResponseEntity.status(200).body(new ApiResponse("Overflow lane " + lane.toLowerCase()));
    }

    // Visitors who switched between pavilions in the last 15 minutes (moving lines on the map)
    @GetMapping("/flows/get")
    public ResponseEntity<?> getFlows() {
        return ResponseEntity.status(200).body(nudgeService.getFlows());
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deletePavilion(@PathVariable Integer id) {
        pavilionService.deletePavilion(id);
        return ResponseEntity.status(200).body(new ApiResponse("Pavilion deleted"));
    }

    // Live wait times for every pavilion (visitor list + ops dashboard)
    @GetMapping("/wait-time/get")
    public ResponseEntity<?> getAllWaitTimes() {
        return ResponseEntity.status(200).body(waitTimeService.getAllWaitTimes());
    }

    @GetMapping("/wait-time/get/{id}")
    public ResponseEntity<?> getWaitTime(@PathVariable Integer id) {
        return ResponseEntity.status(200).body(waitTimeService.getWaitTime(id));
    }

    @GetMapping("/summary/get")
    public ResponseEntity<?> getSummary() {
        return ResponseEntity.status(200).body(waitTimeService.getSummary());
    }
}
