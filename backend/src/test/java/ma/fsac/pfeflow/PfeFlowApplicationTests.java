package ma.fsac.pfeflow;

import ma.fsac.pfeflow.domain.ProjectStatus;
import ma.fsac.pfeflow.repository.ProjectRepository;
import ma.fsac.pfeflow.repository.TaskRepository;
import ma.fsac.pfeflow.web.dto.ProjectRequest;
import ma.fsac.pfeflow.web.dto.ProjectResponse;
import ma.fsac.pfeflow.web.dto.StatsResponse;
import ma.fsac.pfeflow.web.dto.TaskRequest;
import ma.fsac.pfeflow.web.dto.TaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PfeFlowApplicationTests {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private TaskRepository tasks;

    private RestClient client;

    private static final String BASE = "/api/projects";

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        tasks.deleteAll();
        projects.deleteAll();
    }

    private ProjectResponse create(String title, String student) {
        ProjectRequest request = new ProjectRequest(title, student, "Pr. Alami", null, "desc",
                LocalDate.of(2026, 10, 12));
        var response = client.post().uri(BASE).body(request).retrieve().toEntity(ProjectResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    @Test
    void createAndGetProject() {
        ProjectResponse created = create("Pipeline CI/CD", "Amine");
        assertNotNull(created.id());
        assertEquals(ProjectStatus.EN_COURS, created.status());
        assertEquals(0, created.tasks().size());

        ProjectResponse fetched = client.get().uri(BASE + "/" + created.id())
                .retrieve().body(ProjectResponse.class);
        assertEquals("Pipeline CI/CD", fetched.title());
        assertEquals("Amine", fetched.student());
    }

    @Test
    void listSearchAndFilter() {
        create("Pipeline CI/CD", "Amine");
        create("Kubernetes", "Sara");

        ProjectResponse[] all = client.get().uri(BASE).retrieve().body(ProjectResponse[].class);
        assertEquals(2, all.length);

        ProjectResponse[] searched = client.get().uri(BASE + "?q=kuber")
                .retrieve().body(ProjectResponse[].class);
        assertEquals(1, searched.length);
        assertEquals("Kubernetes", searched[0].title());

        ProjectResponse[] filtered = client.get().uri(BASE + "?status=EN_COURS")
                .retrieve().body(ProjectResponse[].class);
        assertEquals(2, filtered.length);
    }

    @Test
    void updateProject() {
        ProjectResponse created = create("Projet", "Amine");
        ProjectRequest update = new ProjectRequest("Projet", "Amine", "Pr. Alami",
                ProjectStatus.VALIDE, "terminé", LocalDate.of(2026, 9, 1));
        client.put().uri(BASE + "/" + created.id()).body(update).retrieve().toBodilessEntity();

        ProjectResponse fetched = client.get().uri(BASE + "/" + created.id())
                .retrieve().body(ProjectResponse.class);
        assertEquals(ProjectStatus.VALIDE, fetched.status());
        assertEquals("terminé", fetched.description());
    }

    @Test
    void addTaskAndToggle() {
        ProjectResponse project = create("Projet", "Amine");
        TaskResponse task = client.post().uri(BASE + "/" + project.id() + "/tasks")
                .body(new TaskRequest("Finaliser le rapport")).retrieve().body(TaskResponse.class);
        assertNotNull(task);
        assertFalse(task.done());

        TaskResponse toggled = client.post().uri("/api/tasks/" + task.id() + "/toggle")
                .retrieve().body(TaskResponse.class);
        assertTrue(toggled.done());

        ProjectResponse detail = client.get().uri(BASE + "/" + project.id())
                .retrieve().body(ProjectResponse.class);
        assertEquals(1, detail.tasks().size());
        assertEquals(1, detail.doneTasks());
    }

    @Test
    void deleteProjectReturns404Afterwards() {
        ProjectResponse project = create("Projet", "Amine");
        HttpStatusCode deleteStatus = client.delete().uri(BASE + "/" + project.id())
                .exchange((request, response) -> response.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, deleteStatus);

        HttpStatusCode missing = client.get().uri(BASE + "/" + project.id())
                .exchange((request, response) -> response.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, missing);
    }

    @Test
    void unknownProjectIs404() {
        HttpStatusCode status = client.get().uri(BASE + "/9999")
                .exchange((request, response) -> response.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, status);
    }

    @Test
    void invalidRequestIs400() {
        ProjectRequest invalid = new ProjectRequest("", "Amine", "Pr.", null, null, null);
        HttpStatusCode status = client.post().uri(BASE).body(invalid)
                .exchange((request, response) -> response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, status);
    }

    @Test
    void statsAggregatesCounters() {
        ProjectResponse project = create("Projet", "Amine");
        TaskResponse task = client.post().uri(BASE + "/" + project.id() + "/tasks")
                .body(new TaskRequest("Tâche")).retrieve().body(TaskResponse.class);
        client.post().uri("/api/tasks/" + task.id() + "/toggle").retrieve().toBodilessEntity();

        StatsResponse stats = client.get().uri("/api/stats").retrieve().body(StatsResponse.class);
        assertEquals(1, stats.projects());
        assertEquals(1, stats.tasks());
        assertEquals(1, stats.done());
    }

    @Test
    void actuatorHealthAndMetricsAreExposed() {
        String health = client.get().uri("/actuator/health").retrieve().body(String.class);
        assertTrue(health.contains("\"UP\""));

        String prometheus = client.get().uri("/actuator/prometheus").retrieve().body(String.class);
        assertTrue(prometheus.contains("http_server_requests_seconds"));
    }
}