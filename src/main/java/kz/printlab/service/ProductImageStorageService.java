package kz.printlab.service;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Independent, public-facing product-image storage. Uploaded data is decoded and re-encoded as PNG. */
@Service
public class ProductImageStorageService {
    private final DesignStorageService storage;
    public ProductImageStorageService(@Value("${printlab.product-upload-dir:local/product-uploads}") String root) {
        storage = new DesignStorageService(root);
    }
    public UUID store(MultipartFile file) { return storage.storeUploadedImage(0, file); }
    public byte[] load(UUID id) { return storage.loadAuthorizedImage(0,id); }
}
