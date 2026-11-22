package kz.printlab.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Transactional
class DesignUploadRegistryTests {
    private static final Path ROOT = tempDirectory();
    @Autowired JdbcTemplate jdbc;
    @Autowired DesignUploadRegistry uploads;
    @Autowired TransactionTemplate transactions;
    private long user;

    private static Path tempDirectory() {
        try { return Files.createTempDirectory("printlab-upload-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("printlab.upload-dir", ROOT::toString); }
    @BeforeEach void user() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) return;
        String suffix = UUID.randomUUID().toString();
        user = jdbc.queryForObject("insert into users(email,password_hash,role,enabled,created_at) values (?, 'hash', 'CUSTOMER', true, current_timestamp) returning id", Long.class, suffix + "@example.com");
    }
    private UUID file(boolean expired) throws Exception {
        UUID id = UUID.randomUUID(); Path dir = ROOT.resolve(Long.toString(user)); Files.createDirectories(dir);
        Files.write(dir.resolve(id + ".png"), new byte[]{1});
        jdbc.update("insert into design_uploads(file_id,user_id,lease_until) values (?,?,current_timestamp + (? * interval '1 hour'))", id, user, expired ? -1 : 1);
        return id;
    }
    private boolean exists(UUID id) { return Files.exists(ROOT.resolve(Long.toString(user)).resolve(id + ".png")); }
    @AfterAll static void removeFiles() throws Exception {
        try (var paths = Files.walk(ROOT)) { for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
    }
    @Test void deletesOnlyExpiredUnreferencedRegisteredFiles() throws Exception {
        UUID expired = file(true), active = file(false), legacy = UUID.randomUUID();
        Files.write(ROOT.resolve(Long.toString(user)).resolve(legacy + ".png"), new byte[]{1});
        uploads.cleanupExpiredForUser(user);
        assertFalse(exists(expired)); assertTrue(exists(active)); assertTrue(exists(legacy));
    }
    @Test void keepsFilesReferencedBySavedDesign() throws Exception {
        UUID id = file(true);
        jdbc.update("insert into designs(user_id,title,editor_state) values (?, 'test', cast(? as jsonb))", user, "{\"front\":[{\"type\":\"image\",\"fileId\":\"" + id + "\"}],\"back\":[]}");
        uploads.cleanupExpiredForUser(user);
        assertTrue(exists(id));
    }
    @Test void keepsCancelledOrderSnapshotAfterDesignIsRemoved() throws Exception {
        UUID id = file(true);
        long design = jdbc.queryForObject("insert into designs(user_id,title,editor_state) values (?, 'test', cast(? as jsonb)) returning id", Long.class, user, "{\"front\":[{\"type\":\"image\",\"fileId\":\"" + id + "\"}],\"back\":[]}");
        long order = jdbc.queryForObject("insert into orders(user_id,status,subtotal_kzt,discount_kzt,total_kzt,delivery_name,delivery_phone,delivery_address) values (?,'CANCELLED',0,0,0,'test','123','test') returning id", Long.class, user);
        long variant = jdbc.queryForObject("select id from product_variants order by id limit 1", Long.class);
        jdbc.update("insert into order_items(order_id,variant_id,design_id,product_name_snapshot,size_snapshot,color_snapshot,design_snapshot,quantity,unit_price_kzt,line_total_kzt) values (?,?,?,'test','M','Black',cast(? as jsonb),1,0,0)", order, variant, design, "{\"front\":[{\"type\":\"image\",\"fileId\":\"" + id + "\"}],\"back\":[]}");
        jdbc.update("delete from designs where id=?", design);
        uploads.cleanupExpiredForUser(user);
        assertTrue(exists(id));
    }
    @Test void renewedLeaseProtectsFile() throws Exception {
        UUID id = file(true); uploads.renew(user, id); uploads.cleanupExpiredForUser(user); assertTrue(exists(id));
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanupWaitsForConcurrentSaveAndSeesCommittedReference() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        long owner = jdbc.queryForObject("insert into users(email,password_hash,role,enabled,created_at) values (?, 'hash', 'CUSTOMER', true, current_timestamp) returning id", Long.class, email);
        UUID id = UUID.randomUUID(); Path dir = ROOT.resolve(Long.toString(owner)); Files.createDirectories(dir);
        Path path = dir.resolve(id + ".png"); Files.write(path, new byte[]{1});
        jdbc.update("insert into design_uploads(file_id,user_id,lease_until) values (?,?,current_timestamp - interval '1 hour')", id, owner);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            Future<?> save = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.queryForObject("select id from users where id=? for update", Long.class, owner); locked.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                jdbc.update("insert into designs(user_id,title,editor_state) values (?, 'test', cast(? as jsonb))", owner, "{\"front\":[{\"fileId\":\"" + id + "\"}],\"back\":[]}");
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<?> cleanup = executor.submit(() -> uploads.cleanupExpiredForUser(owner));
            assertThrows(TimeoutException.class, () -> cleanup.get(250, TimeUnit.MILLISECONDS));
            release.countDown(); save.get(5, TimeUnit.SECONDS); cleanup.get(5, TimeUnit.SECONDS);
            assertTrue(Files.exists(path));
        } finally {
            release.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
            jdbc.update("delete from designs where user_id=?", owner);
            jdbc.update("delete from design_uploads where user_id=?", owner);
            jdbc.update("delete from users where id=?", owner);
            Files.deleteIfExists(path); Files.deleteIfExists(dir);
        }
    }
}
