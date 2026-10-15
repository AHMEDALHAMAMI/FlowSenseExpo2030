package com.tuwaiq.flowsense.Controller;

import com.tuwaiq.flowsense.Api.ApiResponse;
import com.tuwaiq.flowsense.Service.QueueTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/queue-ticket")
@RequiredArgsConstructor
public class QueueTicketController {

    private final QueueTicketService queueTicketService;

    @GetMapping("/get")
    public ResponseEntity<?> getActiveTickets() {
        return ResponseEntity.status(200).body(queueTicketService.getActiveTickets());
    }

    // ---- Visitor ----
    @PostMapping("/join/{pavilionId}/{ticketCode}/{lane}")
    public ResponseEntity<?> join(@PathVariable Integer pavilionId, @PathVariable String ticketCode, @PathVariable String lane) {
        queueTicketService.join(pavilionId, ticketCode, lane);
        return ResponseEntity.status(200).body(new ApiResponse("Your place is held. You are free to explore."));
    }

    @PutMapping("/switch/{ticketCode}/{targetPavilionId}")
    public ResponseEntity<?> switchPavilion(@PathVariable String ticketCode, @PathVariable Integer targetPavilionId) {
        queueTicketService.switchPavilion(ticketCode, targetPavilionId);
        return ResponseEntity.status(200).body(new ApiResponse("Switched. Your new place is held."));
    }

    @PutMapping("/leave/{ticketCode}")
    public ResponseEntity<?> leave(@PathVariable String ticketCode) {
        queueTicketService.leave(ticketCode);
        return ResponseEntity.status(200).body(new ApiResponse("You left the queue"));
    }

    @GetMapping("/status/{ticketCode}")
    public ResponseEntity<?> getStatus(@PathVariable String ticketCode) {
        return ResponseEntity.status(200).body(queueTicketService.getStatus(ticketCode));
    }

    // ---- Pavilion staff (gate) ----
    @PutMapping("/enter/{pavilionId}/{ticketCode}")
    public ResponseEntity<?> enter(@PathVariable Integer pavilionId, @PathVariable String ticketCode) {
        return ResponseEntity.status(200).body(new ApiResponse(queueTicketService.enter(pavilionId, ticketCode)));
    }

    @PutMapping("/enter-by-id/{pavilionId}/{ticketId}")
    public ResponseEntity<?> enterById(@PathVariable Integer pavilionId, @PathVariable Integer ticketId) {
        return ResponseEntity.status(200).body(new ApiResponse(queueTicketService.enterById(pavilionId, ticketId)));
    }

    @PostMapping("/walk-in/{pavilionId}")
    public ResponseEntity<?> walkIn(@PathVariable Integer pavilionId) {
        queueTicketService.walkIn(pavilionId);
        return ResponseEntity.status(200).body(new ApiResponse("Walk-in entry recorded"));
    }

    @GetMapping("/called/{pavilionId}")
    public ResponseEntity<?> getCalled(@PathVariable Integer pavilionId) {
        return ResponseEntity.status(200).body(queueTicketService.getCalled(pavilionId));
    }

    @GetMapping("/waiting-count/{pavilionId}")
    public ResponseEntity<?> countWaiting(@PathVariable Integer pavilionId) {
        return ResponseEntity.status(200).body(queueTicketService.countWaiting(pavilionId));
    }

    @PutMapping("/call-next/{pavilionId}")
    public ResponseEntity<?> callNext(@PathVariable Integer pavilionId) {
        queueTicketService.callNext(pavilionId);
        return ResponseEntity.status(200).body(new ApiResponse("Next visitor called"));
    }
}
