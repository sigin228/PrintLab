package kz.printlab.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DesignStorageService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000;
    private final Path root;
    public DesignStorageService(@Value("${printlab.upload-dir:local/uploads}") String root) { this.root = Path.of(root).toAbsolutePath().normalize(); }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private Path path(long userId, UUID id) { return root.resolve(Long.toString(userId)).resolve(id + ".png"); }
    public UUID storeUploadedImage(long userId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw invalid("Файл должен быть размером до 10 МБ");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        String mime = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        boolean png = name.endsWith(".png") && mime.equals("image/png");
        boolean jpeg = (name.endsWith(".jpg") || name.endsWith(".jpeg")) && mime.equals("image/jpeg");
        if (!png && !jpeg) throw invalid("Разрешены только PNG и JPEG");
        try {
            byte[] bytes = file.getBytes();
            boolean pngSig = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
            boolean jpgSig = bytes.length >= 3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255;
            if (!(png && pngSig || jpeg && jpgSig)) throw invalid("Содержимое файла не соответствует формату");
            BufferedImage image;
            try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) throw invalid("Не удалось прочитать изображение");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!(png && format.equals("png") || jpeg && (format.equals("jpeg") || format.equals("jpg")))) throw invalid("Неверный формат изображения");
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    if (w < 1 || h < 1 || (long) w*h > MAX_PIXELS || w > 10000 || h > 10000) throw invalid("Слишком большое разрешение изображения");
                    image = reader.read(0);
                    if (image == null) throw invalid("Не удалось прочитать изображение");
                } finally { reader.dispose(); }
            }
            UUID id = UUID.randomUUID(); Path output = path(userId, id);
            Files.createDirectories(output.getParent());
            try (OutputStream out = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                if (!ImageIO.write(image, "png", out)) throw new IOException("PNG encoder unavailable");
            }
            return id;
        } catch (ResponseStatusException e) { throw e; }
          catch (IOException | RuntimeException e) { throw invalid("Не удалось обработать изображение"); }
    }
    public boolean belongsTo(long userId, UUID id) { return Files.isRegularFile(path(userId, id)); }
    public byte[] loadAuthorizedImage(long userId, UUID id) {
        try { return Files.readAllBytes(path(userId, id)); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }
}
