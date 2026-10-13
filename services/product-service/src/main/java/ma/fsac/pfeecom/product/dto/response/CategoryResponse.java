package ma.fsac.pfeecom.product.dto.response;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO de rǸponse exposant une catǸgorie via l'API REST.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record CategoryResponse(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt
) implements Serializable {}