# Agentic URL Shortener Implementation

This document contains the complete source code for the Agentic URL Shortener and SDLC Orchestrator. You can import this project into IntelliJ IDEA by creating the directory structure and files as defined below.

## Project Structure
```text
agentic-url-shortener/
├── pom.xml
└── src/main/
    ├── java/com/example/shortener/
    │   ├── ShortenerApplication.java
    │   ├── UrlController.java
    │   ├── UrlService.java
    │   └── orchestration/
    │       ├── OrchestrationController.java
    │       └── WorkflowService.java
    └── resources/
        └── application.properties
```

---

## 1. Maven Configuration (`pom.xml`)
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>agentic-url-shortener</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 2. Main Application (`ShortenerApplication.java`)
```java
package com.example.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShortenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShortenerApplication.class, args);
    }
}
```

---

## 3. URL Service (`UrlService.java`)
```java
package com.example.shortener;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Service
public class UrlService {
    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, Link> links = new ConcurrentHashMap<>();

    public Link create(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String scheme = uri.getScheme();

        if (scheme == null ||
                !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) ||
                uri.getHost() == null) {
            throw new IllegalArgumentException("URL must be a valid HTTP or HTTPS URL");
        }

        String code;
        do {
            code = randomCode(7);
        } while (links.containsKey(code));

        Link link = new Link(code, uri.toString());
        links.put(code, link);
        return link;
    }

    public Link find(String code) {
        Link link = links.get(code);
        if (link == null) {
            throw new LinkNotFoundException();
        }
        return link;
    }

    private String randomCode(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }

    public static final class Link {
        private final String code;
        private final String originalUrl;
        private final LongAdder clicks = new LongAdder();

        Link(String code, String originalUrl) {
            this.code = code;
            this.originalUrl = originalUrl;
        }

        public String code() { return code; }
        public String originalUrl() { return originalUrl; }
        public long clicks() { return clicks.sum(); }
        public void recordClick() { clicks.increment(); }
    }

    public static final class LinkNotFoundException extends RuntimeException {}
}
```

---

## 4. URL Controller (`UrlController.java`)
```java
package com.example.shortener;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class UrlController {
    private final UrlService service;

    public UrlController(UrlService service) {
        this.service = service;
    }

    @PostMapping("/api/links")
    public LinkResponse create(@Valid @RequestBody CreateLinkRequest request) {
        UrlService.Link link = service.create(request.url());
        return new LinkResponse(link.code(), link.originalUrl(),
                "http://localhost:8080/" + link.code());
    }

    @GetMapping("/{code}")
    public RedirectView redirect(@PathVariable String code) {
        UrlService.Link link = service.find(code);
        link.recordClick();
        return new RedirectView(link.originalUrl());
    }

    @GetMapping("/api/links/{code}")
    public LinkResponse details(@PathVariable String code) {
        UrlService.Link link = service.find(code);
        return new LinkResponse(link.code(), link.originalUrl(), link.clicks());
    }

    @ExceptionHandler(UrlService.LinkNotFoundException.class)
    public ResponseEntity<String> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Short link not found");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    public record CreateLinkRequest(@NotBlank String url) {}
    public record LinkResponse(String code, String originalUrl, Object shortUrlOrClicks) {}
}
```

---

## 5. Workflow Service (`orchestration/WorkflowService.java`)
```java
package com.example.shortener.orchestration;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class WorkflowService {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ConcurrentHashMap<String, Run> runs = new ConcurrentHashMap<>();

    public Run start() {
        String id = UUID.randomUUID().toString();
        Run run = new Run(id, defaultStages());
        runs.put(id, run);
        executor.submit(() -> execute(run));
        return run;
    }

    public Run get(String id) {
        Run run = runs.get(id);
        if (run == null) throw new NoSuchElementException("Run not found");
        return run;
    }

    public Run approve(String id, String stageId) {
        Run run = get(id);
        synchronized (run) {
            Stage stage = requireStage(run, stageId);
            if (stage.status != Status.WAITING_FOR_APPROVAL) {
                throw new IllegalStateException("Stage is not awaiting approval");
            }
            stage.status = Status.PENDING;
            event(run, "Approval granted for " + stageId);
        }
        executor.submit(() -> execute(run));
        return run;
    }

    private void execute(Run run) {
        while (true) {
            List<Stage> ready;
            synchronized (run) {
                if (run.stages.stream().anyMatch(s -> s.status == Status.FAILED)) {
                    run.status = "FAILED";
                    return;
                }
                ready = run.stages.stream()
                        .filter(s -> s.status == Status.PENDING)
                        .filter(s -> run.stages.stream()
                                .filter(d -> s.dependsOn.contains(d.id))
                                .allMatch(d -> d.status == Status.SUCCEEDED))
                        .toList();

                if (ready.isEmpty()) {
                    if (run.stages.stream().allMatch(s -> s.status == Status.SUCCEEDED)) {
                        run.status = "SUCCEEDED";
                    } else if (run.stages.stream().anyMatch(
                            s -> s.status == Status.WAITING_FOR_APPROVAL)) {
                        run.status = "WAITING_FOR_APPROVAL";
                    }
                    return;
                }
                ready.forEach(s -> s.status = Status.RUNNING);
            }

            List<CompletableFuture<Void>> tasks = ready.stream()
                    .map(s -> CompletableFuture.runAsync(() -> runStage(run, s), executor))
                    .toList();
            tasks.forEach(CompletableFuture::join);
        }
    }

    private void runStage(Run run, Stage stage) {
        synchronized (run) {
            event(run, "Started " + stage.id);
            if (stage.requiresApproval) {
                stage.status = Status.WAITING_FOR_APPROVAL;
                event(run, "Waiting for approval: " + stage.id);
                return;
            }
        }

        for (int attempt = 1; attempt <= stage.maxAttempts; attempt++) {
            try {
                Thread.sleep(500); // Simulate work
                synchronized (run) {
                    stage.status = Status.SUCCEEDED;
                    event(run, "Completed " + stage.id);
                }
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                synchronized (run) {
                    stage.status = Status.FAILED;
                    event(run, "Interrupted " + stage.id);
                }
                return;
            } catch (RuntimeException e) {
                synchronized (run) {
                    event(run, "Attempt " + attempt + " failed for " + stage.id);
                }
            }
        }

        synchronized (run) {
            stage.status = Status.FAILED;
            event(run, "Retries exhausted for " + stage.id);
        }
    }

    private List<Stage> defaultStages() {
        return new ArrayList<>(List.of(
                new Stage("requirements", Set.of(), false),
                new Stage("design", Set.of("requirements"), false),
                new Stage("implementation", Set.of("design"), false),
                new Stage("human-review", Set.of("implementation"), true),
                new Stage("validation", Set.of("human-review"), false)
        ));
    }

    private Stage requireStage(Run run, String id) {
        return run.stages.stream().filter(s -> s.id.equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Stage not found"));
    }

    private void event(Run run, String message) {
        run.audit.add(new AuditEvent(Instant.now(), message));
    }

    public enum Status {
        PENDING, RUNNING, WAITING_FOR_APPROVAL, SUCCEEDED, FAILED
    }

    public static final class Stage {
        public final String id;
        public final Set<String> dependsOn;
        public final boolean requiresApproval;
        public final int maxAttempts = 3;
        public volatile Status status = Status.PENDING;

        Stage(String id, Set<String> dependsOn, boolean requiresApproval) {
            this.id = id;
            this.dependsOn = dependsOn;
            this.requiresApproval = requiresApproval;
        }
    }

    public static final class Run {
        public final String id;
        public volatile String status = "RUNNING";
        public final List<Stage> stages;
        public final List<AuditEvent> audit = new CopyOnWriteArrayList<>();

        Run(String id, List<Stage> stages) {
            this.id = id;
            this.stages = stages;
        }
    }

    public record AuditEvent(Instant timestamp, String message) {}
}
```

---

## 6. Orchestration Controller (`orchestration/OrchestrationController.java`)
```java
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
    public WorkflowService.Run approve(
            @PathVariable String id,
            @PathVariable String stageId) {
        return service.approve(id, stageId);
    }
}
```

---

## 7. Application Properties (`application.properties`)
```properties
server.port=8080
spring.application.name=agentic-url-shortener
```

---

## How to Run
1. Create a folder named `agentic-url-shortener`.
2. Create the `pom.xml` and the `src/main/java/...` file structure.
3. Paste the code into the respective files.
4. In IntelliJ, click **File > Open** and select the folder.
5. IntelliJ will detect the Maven project and download dependencies.
6. Run `ShortenerApplication.java`.
