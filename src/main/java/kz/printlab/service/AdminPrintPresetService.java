package kz.printlab.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPrintPresetService {
    private final JdbcTemplate jdbc;
    private final ProductImageStorageService storage;
    public AdminPrintPresetService(JdbcTemplate jdbc, ProductImageStorageService storage) { this.jdbc=jdbc; this.storage=storage; }
    @Transactional(readOnly=true) public List<Map<String,Object>> active() {
        return jdbc.queryForList("SELECT id,name FROM admin_print_presets WHERE active ORDER BY created_at DESC,id");
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> all() {
        return jdbc.queryForList("SELECT id,name,active,created_at FROM admin_print_presets ORDER BY created_at DESC,id");
    }
    @Transactional(readOnly=true) public boolean exists(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM admin_print_presets WHERE id=?)",Boolean.class,id));
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional public void create(String name, MultipartFile file) {
        if(name==null || name.isBlank() || name.trim().length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Укажите название принта до 100 символов.");
        if(file==null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Выберите PNG или JPEG.");
        UUID imageId=storage.store(file);
        jdbc.update("INSERT INTO admin_print_presets(id,name,image_id) VALUES (?,?,?)",UUID.randomUUID(),name.trim(),imageId);
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional public void deactivate(UUID id) {
        if(jdbc.update("UPDATE admin_print_presets SET active=false WHERE id=?",id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @Transactional(readOnly=true) public byte[] image(UUID id) {
        List<UUID> ids=jdbc.query("SELECT image_id FROM admin_print_presets WHERE id=?",(rs,n)->rs.getObject(1,UUID.class),id);
        if(ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return storage.load(ids.get(0));
    }
}
