package ma.fsac.pfeecom.product.storage;

import ma.fsac.pfeecom.product.exception.InvalidImageException;
import ma.fsac.pfeecom.product.exception.StorageUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires de {@link MinioStorageService} : detection du type MIME reel,
 * validation des fichiers (rejet avant tout appel reseau) et comportement quand
 * le stockage est desactive. Aucun MinIO n'est requis.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
class MinioStorageServiceTest {

    private static final long MAX = 5L * 1024 * 1024;

    /** Service active mais pointe vers un port injoignable : la validation precede tout appel reseau. */
    private final MinioStorageService service =
            new MinioStorageService(true, "http://localhost:1", "k", "s", "bucket", MAX);

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    private static byte[] webp() {
        return new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    }

    private static byte[] gif() {
        return new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0};
    }

    @Test
    void detectsSupportedImageTypes() {
        assertThat(MinioStorageService.detectContentType(png())).isEqualTo("image/png");
        assertThat(MinioStorageService.detectContentType(jpeg())).isEqualTo("image/jpeg");
        assertThat(MinioStorageService.detectContentType(gif())).isEqualTo("image/gif");
        assertThat(MinioStorageService.detectContentType(webp())).isEqualTo("image/webp");
    }

    @Test
    void rejectsNonImageContent() {
        assertThat(MinioStorageService.detectContentType("not an image".getBytes())).isNull();
        assertThat(MinioStorageService.detectContentType(new byte[3])).isNull();
    }

    @Test
    void extensionMapping() {
        assertThat(MinioStorageService.extensionFor("image/jpeg")).isEqualTo("jpg");
        assertThat(MinioStorageService.extensionFor("image/png")).isEqualTo("png");
        assertThat(MinioStorageService.extensionFor("image/webp")).isEqualTo("webp");
        assertThat(MinioStorageService.extensionFor("image/gif")).isEqualTo("gif");
    }

    @Test
    void rejectsNonImageFile() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> service.storeImage(1L, file)).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void rejectsTypeDeclaredInconsistentWithContent() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", png());
        assertThatThrownBy(() -> service.storeImage(1L, file)).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> service.storeImage(1L, file)).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void validImageButUnreachableStorage_isUnavailable() {
        MockMultipartFile file = new MockMultipartFile("file", "ok.png", "image/png", png());
        assertThatThrownBy(() -> service.storeImage(1L, file)).isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void disabledStorage_isUnavailable() {
        MinioStorageService disabled = new MinioStorageService(false, "", "", "", "", MAX);
        MockMultipartFile file = new MockMultipartFile("file", "ok.png", "image/png", png());
        assertThatThrownBy(() -> disabled.storeImage(1L, file)).isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> disabled.loadImage("products/1/x.png"))
                .isInstanceOf(StorageUnavailableException.class);
    }
}
