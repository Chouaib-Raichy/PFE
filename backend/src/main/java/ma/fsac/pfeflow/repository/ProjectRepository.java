package ma.fsac.pfeflow.repository;

import ma.fsac.pfeflow.domain.Project;
import ma.fsac.pfeflow.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("""
            select p from Project p
            where (cast(:q as string) is null
                   or lower(p.title) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.student) like lower(concat('%', cast(:q as string), '%')))
              and (:status is null or p.status = :status)
            order by p.id desc
            """)
    List<Project> search(@Param("q") String q, @Param("status") ProjectStatus status);
}