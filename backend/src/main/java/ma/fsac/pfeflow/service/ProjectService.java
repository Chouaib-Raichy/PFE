package ma.fsac.pfeflow.service;

import ma.fsac.pfeflow.domain.Project;
import ma.fsac.pfeflow.domain.ProjectStatus;
import ma.fsac.pfeflow.domain.Task;
import ma.fsac.pfeflow.repository.ProjectRepository;
import ma.fsac.pfeflow.repository.TaskRepository;
import ma.fsac.pfeflow.web.dto.ProjectRequest;
import ma.fsac.pfeflow.web.dto.ProjectResponse;
import ma.fsac.pfeflow.web.dto.StatsResponse;
import ma.fsac.pfeflow.web.dto.TaskRequest;
import ma.fsac.pfeflow.web.dto.TaskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projects;
    private final TaskRepository tasks;

    public ProjectService(ProjectRepository projects, TaskRepository tasks) {
        this.projects = projects;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(String q, ProjectStatus status) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        return projects.search(query, status).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        return toResponse(find(id));
    }

    public ProjectResponse create(ProjectRequest request) {
        Project project = new Project(request.title().trim(), request.student().trim(),
                request.supervisor().trim());
        apply(project, request);
        return toResponse(projects.save(project));
    }

    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = find(id);
        project.setTitle(request.title().trim());
        project.setStudent(request.student().trim());
        project.setSupervisor(request.supervisor().trim());
        apply(project, request);
        return toResponse(projects.save(project));
    }

    public void delete(Long id) {
        if (!projects.existsById(id)) {
            throw notFound(id);
        }
        tasks.deleteByProjectId(id);
        projects.deleteById(id);
    }

    public TaskResponse addTask(Long projectId, TaskRequest request) {
        find(projectId);
        Task task = tasks.save(new Task(projectId, request.label().trim()));
        return toTask(task);
    }

    public TaskResponse toggleTask(Long taskId) {
        Task task = tasks.findById(taskId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tâche " + taskId + " introuvable"));
        task.setDone(!task.isDone());
        return toTask(tasks.save(task));
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        return new StatsResponse(projects.count(), tasks.count(), tasks.countByDone(true));
    }

    private Project find(Long id) {
        return projects.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet " + id + " introuvable"));
    }

    private void apply(Project project, ProjectRequest request) {
        project.setStatus(request.status() == null ? ProjectStatus.EN_COURS : request.status());
        project.setDescription(request.description() == null ? "" : request.description().trim());
        project.setDefenseDate(request.defenseDate());
    }

    private ProjectResponse toResponse(Project project) {
        List<Task> projectTasks = tasks.findByProjectIdOrderById(project.getId());
        return new ProjectResponse(
                project.getId(), project.getTitle(), project.getStudent(), project.getSupervisor(),
                project.getStatus(), project.getDescription(), project.getDefenseDate(),
                projectTasks.stream().map(this::toTask).toList(),
                projectTasks.stream().filter(Task::isDone).count());
    }

    private TaskResponse toTask(Task task) {
        return new TaskResponse(task.getId(), task.getLabel(), task.isDone());
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet " + id + " introuvable");
    }
}