package ma.fsac.pfeflow.repository;

import ma.fsac.pfeflow.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectIdOrderById(Long projectId);

    long countByDone(boolean done);

    void deleteByProjectId(Long projectId);
}