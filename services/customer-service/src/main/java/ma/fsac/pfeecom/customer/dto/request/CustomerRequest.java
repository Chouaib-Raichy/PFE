package ma.fsac.pfeecom.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de requête pour la création/modification d'un client.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record CustomerRequest(

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 150, message = "Le prénom ne doit pas dépasser 150 caractères")
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
        String lastName,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email doit être valide")
        @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
        String email,

        @Size(max = 30, message = "Le téléphone ne doit pas dépasser 30 caractères")
        String phone
) {}