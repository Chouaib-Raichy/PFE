package ma.fsac.pfeecom.product.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import ma.fsac.pfeecom.product.exception.InvalidImageException;
import ma.fsac.pfeecom.product.exception.ResourceNotFoundException;
import ma.fsac.pfeecom.product.exception.StorageUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Implementation S3 de {@link StorageService} reposant sur le client MinIO.
 *
 * <p>Quand {@code app.storage.enabled=false} (tests, stockage non configure),
 * toute operation echoue en {@link StorageUnavailableException} (HTTP 503) : le
 * reste du service produit continue de fonctionner sans stockage objet.</p>
 *
 * <p>Les cles d'objet sont generees EXCLUSIVEMENT par le serveur
 * ({@code products/{id}/{horodatage}-{aleatoire}.{ext}}) : aucune donnee fournie
 * par le client n'entre dans le nom d'objet (pas de traversee de chemin).</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Slf4j
@Service
public class MinioStorageService implements StorageService {

    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final boolean enabled;
    private final String bucket;
    private final long maxBytes;
    private final MinioClient client;

    public MinioStorageService(
            @Value("${app.storage.enabled:false}") boolean enabled,
            @Value("${app.storage.endpoint:}") String endpoint,
            @Value("${app.storage.access-key:}") String accessKey,
            @Value("${app.storage.secret-key:}") String secretKey,
            @Value("${app.storage.bucket:}") String bucket,
            @Value("${app.storage.max-image-bytes:5242880}") long maxBytes) {
        this.enabled = enabled;
        this.bucket = bucket;
        this.maxBytes = maxBytes;
        this.client = enabled ? MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build() : null;
    }

    @Override
    public String storeImage(Long productId, MultipartFile file) {
        requireEnabled();
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("Aucun fichier image fourni.");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidImageException(
                    "Image trop volumineuse (maximum " + (maxBytes / (1024 * 1024)) + " Mo).");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("Lecture du fichier image impossible.");
        }
        // Le type reel est deduit des octets (signature magique), jamais du nom de fichier.
        String detected = detectContentType(data);
        if (detected == null) {
            throw new InvalidImageException("Format d'image non supporte (JPEG, PNG, WEBP ou GIF attendus).");
        }
        String declared = file.getContentType();
        if (declared != null && !declared.isBlank() && !declared.equalsIgnoreCase(detected)) {
            throw new InvalidImageException(
                    "Le type annonce (" + declared + ") ne correspond pas au contenu (" + detected + ").");
        }
        String key = buildKey(productId, detected);
        try (InputStream in = new ByteArrayInputStream(data)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, data.length, -1)
                    .contentType(detected)
                    .build());
        } catch (Exception e) {
            throw new StorageUnavailableException("Enregistrement de l'image impossible", e);
        }
        return key;
    }

    @Override
    public StoredImage loadImage(String key) {
        requireEnabled();
        if (key == null || key.isBlank()) {
            throw new ResourceNotFoundException("Image introuvable.");
        }
        try {
            StatObjectResponse stat = client.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(key).build());
            try (InputStream in = client.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(key).build())) {
                byte[] data = in.readAllBytes();
                String contentType = stat.contentType() != null ? stat.contentType() : "application/octet-stream";
                return new StoredImage(data, contentType);
            }
        } catch (ErrorResponseException e) {
            if (e.errorResponse() != null && "NoSuchKey".equals(e.errorResponse().code())) {
                throw new ResourceNotFoundException("Image introuvable dans le stockage objet.");
            }
            throw new StorageUnavailableException("Lecture de l'image impossible", e);
        } catch (Exception e) {
            throw new StorageUnavailableException("Lecture de l'image impossible", e);
        }
    }

    @Override
    public void deleteImage(String key) {
        if (!enabled || client == null || key == null || key.isBlank()) {
            return;
        }
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            // Suppression au mieux : une image orpheline ne doit pas faire echouer l'operation metier.
            log.warn("Echec de la suppression de l'objet '{}' : {}", key, e.getMessage());
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new StorageUnavailableException("Le stockage d'images est desactive.");
        }
    }

    private String buildKey(Long productId, String contentType) {
        String stamp = LocalDateTime.now().format(KEY_TIME);
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "products/" + productId + "/" + stamp + "-" + random + "." + extensionFor(contentType);
    }

    static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "bin";
        };
    }

    /**
     * Deduit le type MIME reel a partir des octets d'en-tete (signature magique).
     *
     * @return le type MIME reconnu, ou {@code null} si le contenu n'est pas une image supportee
     */
    static String detectContentType(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if (u(data[0]) == 0xFF && u(data[1]) == 0xD8 && u(data[2]) == 0xFF) {
            return "image/jpeg";
        }
        if (u(data[0]) == 0x89 && u(data[1]) == 0x50 && u(data[2]) == 0x4E && u(data[3]) == 0x47
                && u(data[4]) == 0x0D && u(data[5]) == 0x0A && u(data[6]) == 0x1A && u(data[7]) == 0x0A) {
            return "image/png";
        }
        if (u(data[0]) == 0x47 && u(data[1]) == 0x49 && u(data[2]) == 0x46 && u(data[3]) == 0x38) {
            return "image/gif";
        }
        if (u(data[0]) == 0x52 && u(data[1]) == 0x49 && u(data[2]) == 0x46 && u(data[3]) == 0x46
                && u(data[8]) == 0x57 && u(data[9]) == 0x45 && u(data[10]) == 0x42 && u(data[11]) == 0x50) {
            return "image/webp";
        }
        return null;
    }

    private static int u(byte b) {
        return b & 0xFF;
    }
}
