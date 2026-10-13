package ma.fsac.pfeecom.product.service.impl;

import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.product.domain.entity.Category;
import ma.fsac.pfeecom.product.domain.entity.Product;
import ma.fsac.pfeecom.product.dto.request.ProductRequest;
import ma.fsac.pfeecom.product.dto.response.ProductResponse;
import ma.fsac.pfeecom.product.exception.ResourceNotFoundException;
import ma.fsac.pfeecom.product.mapper.ProductMapper;
import ma.fsac.pfeecom.product.repository.CategoryRepository;
import ma.fsac.pfeecom.product.repository.ProductRepository;
import ma.fsac.pfeecom.product.repository.SearchOutboxRepository;
import ma.fsac.pfeecom.product.service.ProductService;
import ma.fsac.pfeecom.product.storage.StorageService;
import ma.fsac.pfeecom.product.storage.StoredImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final SearchOutboxRepository searchOutboxRepository;
    private final StorageService storageService;
    @Value("${app.search.enabled:false}")
    private boolean searchEnabled;
    @Override
    @Cacheable(value = "products", key = "#page + '-' + #size", unless = "#result.totalElements == 0")
    public Page<ProductResponse> findAll(int page, int size) {
        return productRepository.findAll(PageRequest.of(page, size)).map(productMapper::toResponse);
    }
    @Override
    @Cacheable(value = "product", key = "#id", unless = "#result == null")
    public ProductResponse findById(Long id) {
        return productMapper.toResponse(findProduct(id));
    }
    @Override
    @Transactional
    @CacheEvict(value = {"products", "product"}, allEntries = true)
    public ProductResponse create(ProductRequest request) {
        Product product = productMapper.toEntity(request);
        applyCategory(product, request.categoryId());
        Product saved = productRepository.save(product);
        enqueueForSearch(saved.getId());
        return productMapper.toResponse(saved);
    }
    @Override
    @Transactional
    @CacheEvict(value = {"products", "product"}, allEntries = true)
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProduct(id);
        productMapper.updateFromRequest(request, product);
        applyCategory(product, request.categoryId());
        Product saved = productRepository.save(product);
        enqueueForSearch(saved.getId());
        return productMapper.toResponse(saved);
    }
    @Override
    @Transactional
    @CacheEvict(value = {"products", "product"}, allEntries = true)
    public void delete(Long id) {
        productRepository.delete(findProduct(id));
        enqueueForSearch(id);
    }
    @Override
    @Transactional
    @CacheEvict(value = {"products", "product"}, allEntries = true)
    public ProductResponse uploadImage(Long id, MultipartFile file) {
        Product product = findProduct(id);
        String previousKey = product.getImageKey();
        String newKey = storageService.storeImage(id, file);
        product.setImageKey(newKey);
        product.setImageUrl(imageUrl(id));
        try {
            Product saved = productRepository.save(product);
            enqueueForSearch(saved.getId());
            if (previousKey != null && !previousKey.equals(newKey)) {
                deleteAfterCommit(previousKey);
            }
            return productMapper.toResponse(saved);
        } catch (RuntimeException ex) {
            storageService.deleteImage(newKey);
            throw ex;
        }
    }
    @Override
    @Transactional
    @CacheEvict(value = {"products", "product"}, allEntries = true)
    public ProductResponse removeImage(Long id) {
        Product product = findProduct(id);
        String previousKey = product.getImageKey();
        product.setImageKey(null);
        product.setImageUrl(null);
        Product saved = productRepository.save(product);
        enqueueForSearch(saved.getId());
        if (previousKey != null) {
            deleteAfterCommit(previousKey);
        }
        return productMapper.toResponse(saved);
    }
    @Override
    public StoredImage loadImage(Long id) {
        Product product = findProduct(id);
        if (product.getImageKey() == null || product.getImageKey().isBlank()) {
            throw ResourceNotFoundException.of("Image du produit", id);
        }
        return storageService.loadImage(product.getImageKey());
    }
    private String imageUrl(Long productId) {
        return "/api/products/" + productId + "/image";
    }
    private Product findProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Produit", id));
    }

    private void applyCategory(Product product, Long categoryId) {
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> ResourceNotFoundException.of("CatÃƒâ€¡Ã‚Â¸gorie", categoryId));
            product.setCategoryId(category.getId());
            product.setCategoryName(category.getName());
        } else {
            product.setCategoryId(null);
            product.setCategoryName(null);
        }
    }

    private void enqueueForSearch(Long productId) {
        if (!searchEnabled) {
            return;
        }
        searchOutboxRepository.enqueue(productId);
    }

    private void deleteAfterCommit(String imageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.deleteImage(imageKey);
                } catch (Exception ignored) {
                }
            }
        });
    }
}

