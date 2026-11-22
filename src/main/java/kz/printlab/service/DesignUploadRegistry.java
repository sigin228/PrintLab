package kz.printlab.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Tracks only uploads made after the registry migration; legacy files are never collected. */
@Service
public class DesignUploadRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(DesignUploadRegistry.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final DesignStorageService storage;

    public DesignUploadRegistry(JdbcTemplate jdbc, TransactionTemplate transactions, DesignStorageService storage) {
        this.jdbc = jdbc; this.transactions = transactions; this.storage = storage;
    }
    private void lockUser(long userId) {
        if (jdbc.query("select id from users where id=? for update", (rs,n) -> rs.getLong(1), userId).isEmpty())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    @Transactional
    public UUID store(long userId, MultipartFile file) {
        lockUser(userId);
        UUID id = storage.storeUploadedImage(userId, file);
        // If DB commit fails the file may remain unregistered; it is never eligible for deletion.
        jdbc.update("insert into design_uploads(file_id,user_id,lease_until) values (?,?,current_timestamp + interval '24 hours')", id, userId);
        return id;
    }
    @Transactional
    public void renew(long userId, UUID id) {
        lockUser(userId);
        if (!storage.belongsTo(userId, id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        // A legacy unregistered file is kept indefinitely; it does not need a lease.
        jdbc.update("update design_uploads set lease_until=current_timestamp + interval '24 hours' where file_id=? and user_id=?", id, userId);
    }
    private record Candidate(UUID id, long userId) {}
    @Scheduled(initialDelayString = "${printlab.upload-cleanup-interval-ms:3600000}", fixedDelayString = "${printlab.upload-cleanup-interval-ms:3600000}")
    public void cleanupExpired() {
        List<Candidate> candidates = jdbc.query("select file_id,user_id from design_uploads where lease_until < current_timestamp order by lease_until limit 100", (rs,n) -> new Candidate(rs.getObject(1, UUID.class), rs.getLong(2)));
        cleanupCandidates(candidates);
    }
    void cleanupExpiredForUser(long userId) {
        cleanupCandidates(jdbc.query("select file_id,user_id from design_uploads where user_id=? and lease_until < current_timestamp order by lease_until limit 100", (rs,n) -> new Candidate(rs.getObject(1, UUID.class), rs.getLong(2)), userId));
    }
    private void cleanupCandidates(List<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            try { transactions.executeWithoutResult(status -> cleanupOne(candidate)); }
            catch (RuntimeException e) { LOG.warn("Temporary design upload cleanup failed; file retained when possible", e); }
        }
    }
    private void cleanupOne(Candidate candidate) {
        lockUser(candidate.userId());
        var expired = jdbc.query("select file_id from design_uploads where file_id=? and user_id=? and lease_until < current_timestamp for update", (rs,n) -> rs.getObject(1, UUID.class), candidate.id(), candidate.userId());
        if (expired.isEmpty()) return;
        // Match fileId recursively; checking all order snapshots is deliberately conservative.
        String predicate = "jsonb_path_exists(%s, '$.** ? (@.fileId == $fid)', jsonb_build_object('fid', cast(? as text)))";
        boolean usedByDesign = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from designs where user_id=? and " + predicate.formatted("editor_state") + ")", Boolean.class, candidate.userId(), candidate.id().toString()));
        boolean usedByOrder = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from order_items where design_snapshot is not null and " + predicate.formatted("design_snapshot") + ")", Boolean.class, candidate.id().toString()));
        if (usedByDesign || usedByOrder) {
            // Defer re-check so protected rows cannot starve other cleanup candidates.
            jdbc.update("update design_uploads set lease_until=current_timestamp + interval '24 hours' where file_id=?", candidate.id());
            return;
        }
        try {
            // A missing file may mean a wrongly configured upload root; retain the registry entry.
            if (!storage.deleteRegisteredImage(candidate.userId(), candidate.id())) return;
        }
        catch (IOException e) { throw new IllegalStateException("Unable to remove temporary design upload", e); }
        jdbc.update("delete from design_uploads where file_id=? and user_id=?", candidate.id(), candidate.userId());
    }
    @Configuration @EnableScheduling static class SchedulingConfiguration { }
}
