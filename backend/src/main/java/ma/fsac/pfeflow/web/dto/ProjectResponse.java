package ma.fsac.pfeflow.web.dto;

import ma.fsac.pfeflow.domain.ProjectStatus;

import java.time.LocalDate;
import java.util.List;

public record ProjectResponse(
        Long id,
        String title,
        String student,
        String supervisor,
        ProjectStatus status,
        String description,
        LocalDate defenseDate,
        List<TaskResponse> tasks,
        long doneTasks) {
}