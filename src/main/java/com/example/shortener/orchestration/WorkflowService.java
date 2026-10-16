
package com.example.shortener.orchestration;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class WorkflowService {

    private final ExecutorService executor =
            Executors.newFixedThreadPool(4);

    private final ConcurrentHashMap<String, Run> runs =
            new ConcurrentHashMap<>();

    public Run start() {
        String id = UUID.randomUUID().toString();
        Run run = new Run(id, defaultStages());

        runs.put(id, run);
        executor.submit(() -> execute(run));

        return run;
    }

    public Run get(String id) {
        Run run = runs.get(id);

        if (run == null) {
            throw new NoSuchElementException(
                    "Run not found: " + id);
        }

        synchronized (run) {
            return run;
        }
    }

    public Run approve(String id, String stageId) {
        Run run = get(id);

        synchronized (run) {
            Stage stage = requireStage(run, stageId);

            if (stage.status != Status.WAITING_FOR_APPROVAL) {
                throw new IllegalStateException(
                        "Stage not awaiting approval: " + stageId);
            }

            stage.approved = true;
            stage.status = Status.PENDING;

            event(run, "Approval granted for " + stageId);
        }

        executor.submit(() -> execute(run));
        return run;
    }

    private void execute(Run run) {
        while (true) {
            Stage next;

            synchronized (run) {
                if (run.stages.stream()
                        .anyMatch(s -> s.status == Status.FAILED)) {
                    run.status = "FAILED";
                    return;
                }

                next = run.stages.stream()
                        .filter(s -> s.status == Status.PENDING)
                        .filter(s -> run.stages.stream()
                                .filter(d -> s.dependsOn.contains(d.id))
                                .allMatch(d ->
                                        d.status == Status.SUCCEEDED))
                        .findFirst()
                        .orElse(null);

                if (next == null) {
                    if (run.stages.stream()
                            .allMatch(s ->
                                    s.status == Status.SUCCEEDED)) {
                        run.status = "SUCCEEDED";
                    } else if (run.stages.stream()
                            .anyMatch(s -> s.status ==
                                    Status.WAITING_FOR_APPROVAL)) {
                        run.status = "WAITING_FOR_APPROVAL";
                    } else {
                        run.status = "PENDING";
                    }
                    return;
                }

                if (next.requiresApproval && !next.approved) {
                    next.status = Status.WAITING_FOR_APPROVAL;
                    run.status = "WAITING_FOR_APPROVAL";

                    event(run,
                            "Waiting for approval: " + next.id);
                    return;
                }

                next.status = Status.RUNNING;
                run.status = "RUNNING";

                event(run, "Started stage: " + next.id);
            }

            try {
                runStage(run, next);

                synchronized (run) {
                    next.status = Status.SUCCEEDED;
                    event(run, "Completed stage: " + next.id);
                }
            } catch (Exception e) {
                synchronized (run) {
                    next.status = Status.FAILED;
                    next.error = e.getMessage() != null
                            ? e.getMessage()
                            : e.getClass().getSimpleName();

                    run.status = "FAILED";
                    event(run, "Failed stage: " + next.id
                            + " - " + next.error);
                }
                return;
            }
        }
    }

    private void runStage(Run run, Stage stage) {
        // Placeholder for actual URL-shortening logic.
        // Currently simulates successful execution.
    }

    private List<Stage> defaultStages() {
        List<Stage> stages = new ArrayList<>();

        stages.add(new Stage(
                "validate",
                "Validate Request",
                List.of(),
                false));

        stages.add(new Stage(
                "shorten",
                "Shorten URL",
                List.of("validate"),
                true));

        stages.add(new Stage(
                "complete",
                "Complete Workflow",
                List.of("shorten"),
                false));

        return stages;
    }

    private Stage requireStage(Run run, String stageId) {
        return run.stages.stream()
                .filter(s -> s.id.equals(stageId))
                .findFirst()
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Stage not found: " + stageId));
    }

    private void event(Run run, String message) {
        run.events.add(Instant.now() + " - " + message);
    }

    public enum Status {
        PENDING,
        RUNNING,
        WAITING_FOR_APPROVAL,
        SUCCEEDED,
        FAILED
    }

    public static class Run {
        public String id;
        public String status = "PENDING";
        public List<Stage> stages;
        public List<String> events = new ArrayList<>();

        public Run(String id, List<Stage> stages) {
            this.id = id;
            this.stages = stages;
        }
    }

    public static class Stage {
        public String id;
        public String name;
        public Status status = Status.PENDING;
        public List<String> dependsOn;
        public boolean requiresApproval;
        public boolean approved = false;
        public String error;

        public Stage(
                String id,
                String name,
                List<String> dependsOn,
                boolean requiresApproval) {
            this.id = id;
            this.name = name;
            this.dependsOn = new ArrayList<>(dependsOn);
            this.requiresApproval = requiresApproval;
        }
    }
}
