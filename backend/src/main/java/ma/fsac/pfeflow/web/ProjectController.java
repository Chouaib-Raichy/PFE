package ma.fsac.pfeflow.web;

import jakarta.validation.Valid;
import ma.fsac.pfeflow.domain.ProjectStatus;
import ma.fsac.pfeflow.service.ProjectService;
import ma.fsac.pfeflow.web.dto.ProjectRequest;
import ma.fsac.pfeflow.web.dto.ProjectResponse;
import ma.fsac.pfeflow.web.dto.StatsResponse;
import ma.fsac.pfeflow.web.dto.TaskRequest;
import ma.fsac.pfeflow.web.dto.TaskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping("/projects")
    public List<ProjectResponse> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) ProjectStatus status) {
        return service.list(q, status);
    }

    @GetMapping("/projects/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request) {
        return service.create(request);
    }

    @PutMapping("/projects/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/projects/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse addTask(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return service.addTask(id, request);
    }

    @PostMapping("/tasks/{id}/toggle")
    public TaskResponse toggleTask(@PathVariable Long id) {
        return service.toggleTask(id);
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return service.stats();
    }
}