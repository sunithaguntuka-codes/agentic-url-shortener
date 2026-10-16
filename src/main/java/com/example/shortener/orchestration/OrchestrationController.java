package com.example.shortener.orchestration;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows")
public class OrchestrationController {
    private final WorkflowService service;

    public OrchestrationController(WorkflowService service) {
        this.service = service;
    }

    @PostMapping
    public WorkflowService.Run start() {
        return service.start();
    }

    @GetMapping("/{id}")
    public WorkflowService.Run get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve/{stageId}")
    public WorkflowService.Run approve(@PathVariable String id, @PathVariable String stageId) {
        return service.approve(id, stageId);
    }
}
