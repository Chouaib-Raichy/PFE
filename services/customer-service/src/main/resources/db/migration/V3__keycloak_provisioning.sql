-- Provisionnement automatique d'un profil client a l'inscription.
-- keycloak_sub = identifiant immuable de l'utilisateur Keycloak (claim "sub").
-- Nullable : les clients de demonstration (yassine/sarah) existent deja et sont
-- rattaches via leur attribut Keycloak "customer_id". La contrainte UNIQUE
-- garantit qu'un utilisateur ne peut obtenir qu'un seul profil (idempotence).

ALTER TABLE customers ADD COLUMN keycloak_sub VARCHAR(64);

ALTER TABLE customers ADD CONSTRAINT uq_customers_keycloak_sub UNIQUE (keycloak_sub);
