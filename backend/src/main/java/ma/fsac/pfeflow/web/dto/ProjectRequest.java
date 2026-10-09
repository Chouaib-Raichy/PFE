package ma.fsac.pfeflow.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.fsac.pfeflow.domain.ProjectStatus;

import java.time.LocalDate;

public record ProjectRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 120) String student,
        @NotBlank @Size(max = 120) String supervisor,
        ProjectStatus status,
        @Size(max = 2000) String description,
        LocalDate defenseDate) {
}