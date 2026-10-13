package ma.fsac.pfeecom.customer.dto.response;

import java.time.LocalDateTime;

/**
 * DTO de réponse exposant un client via l'API REST.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}