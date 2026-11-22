package kz.printlab.service;

import java.time.Instant;
import java.util.List;
import kz.printlab.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Simulated funds only. All balance changes and their ledger entries share a transaction. */
@Service
public class PaymentService {
    public record Transaction(long id, String operation, long amountKzt, Instant createdAt) {}
    public record WalletView(long balanceKzt, List<Transaction> transactions) {}
    private record Wallet(long id, long balance) {}
    private record Order(long id, String status, long total, boolean stockReserved) {}
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    public PaymentService(JdbcTemplate jdbc, UserRepository users) { this.jdbc = jdbc; this.users = users; }

    private long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).getId();
    }
    private Order lockOrder(long id, long userId) {
        return jdbc.query("SELECT id,status,total_kzt,stock_reserved FROM orders WHERE id=? AND user_id=? FOR UPDATE",
            (rs, n) -> new Order(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getBoolean(4)), id, userId)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private Wallet lockWallet(long userId) {
        // Insertion and initial ledger entry are atomic. Concurrent creation waits on the unique user_id.
        jdbc.update("WITH created AS (INSERT INTO demo_wallets(user_id,balance_kzt) VALUES (?,50000) ON CONFLICT (user_id) DO NOTHING RETURNING id) " +
            "INSERT INTO wallet_transactions(wallet_id,operation,amount_kzt) SELECT id,'CREDIT',50000 FROM created", userId);
        return jdbc.queryForObject("SELECT id,balance_kzt FROM demo_wallets WHERE user_id=? FOR UPDATE",
            (rs,n) -> new Wallet(rs.getLong(1),rs.getLong(2)), userId);
    }
    @Transactional
    public WalletView wallet() {
        Wallet w = lockWallet(currentUserId());
        var tx = jdbc.query("SELECT id,operation,amount_kzt,created_at FROM wallet_transactions WHERE wallet_id=? ORDER BY created_at DESC,id DESC",
            (rs,n) -> new Transaction(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getTimestamp(4).toInstant()), w.id());
        return new WalletView(w.balance(), tx);
    }
    @Transactional
    public void topUp(long amount) {
        if (amount < 1 || amount > 1_000_000) throw new IllegalArgumentException("Укажите сумму от 1 до 1 000 000 ₸.");
        Wallet w = lockWallet(currentUserId());
        long balance = Math.addExact(w.balance(), amount);
        jdbc.update("UPDATE demo_wallets SET balance_kzt=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",balance,w.id());
        jdbc.update("INSERT INTO wallet_transactions(wallet_id,operation,amount_kzt) VALUES (?,'CREDIT',?)",w.id(),amount);
    }
    /** Returns false on insufficient funds; the declined payment remains committed. */
    @Transactional
    public boolean pay(long orderId) {
        long userId = currentUserId();
        Order order = lockOrder(orderId, userId);
        if ("PAID_SIMULATED".equals(order.status())) return true;
        if (!"AWAITING_PAYMENT".equals(order.status())) throw new IllegalArgumentException("Этот заказ нельзя оплатить.");
        Wallet w = lockWallet(userId);
        if (w.balance() < order.total()) {
            jdbc.update("INSERT INTO payments(order_id,wallet_id,amount_kzt,status,failure_reason) VALUES (?,?,?,'DECLINED','Недостаточно средств')",order.id(),w.id(),order.total());
            return false;
        }
        long balance = Math.subtractExact(w.balance(), order.total());
        Long paymentId = jdbc.queryForObject("INSERT INTO payments(order_id,wallet_id,amount_kzt,status) VALUES (?,?,?,'APPROVED') RETURNING id",Long.class,order.id(),w.id(),order.total());
        jdbc.update("UPDATE demo_wallets SET balance_kzt=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",balance,w.id());
        if (order.total() > 0) jdbc.update("INSERT INTO wallet_transactions(wallet_id,payment_id,operation,amount_kzt) VALUES (?,?,'DEBIT',?)",w.id(),paymentId,order.total());
        jdbc.update("UPDATE orders SET status='PAID_SIMULATED' WHERE id=?",order.id());
        return true;
    }
    /** Called by OrderService within the cancellation transaction; it holds the order lock. */
    @Transactional
    public boolean cancelPaid(long orderId, long userId) {
        Order order = lockOrder(orderId,userId);
        if (!"PAID_SIMULATED".equals(order.status())) return false;
        Wallet w = lockWallet(userId);
        var payments = jdbc.query("SELECT id,amount_kzt FROM payments WHERE order_id=? AND wallet_id=? AND status='APPROVED' FOR UPDATE",
            (rs,n) -> new long[]{rs.getLong(1),rs.getLong(2)}, orderId,w.id());
        if (payments.size() != 1) throw new IllegalStateException("Нет подтверждённого платежа для возврата.");
        long[] p = payments.get(0);
        long balance = Math.addExact(w.balance(),p[1]);
        jdbc.update("UPDATE demo_wallets SET balance_kzt=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",balance,w.id());
        if (p[1] > 0) jdbc.update("INSERT INTO wallet_transactions(wallet_id,payment_id,operation,amount_kzt) VALUES (?,?,'REFUND',?)",w.id(),p[0],p[1]);
        jdbc.update("UPDATE payments SET status='REFUNDED' WHERE id=?",p[0]);
        if (order.stockReserved()) {
            var lines = jdbc.query("SELECT variant_id,SUM(quantity) FROM order_items WHERE order_id=? GROUP BY variant_id ORDER BY variant_id", (rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},orderId);
            for (long[] line : lines) jdbc.update("UPDATE product_variants SET stock_quantity=stock_quantity+? WHERE id=?",line[1],line[0]);
        }
        jdbc.update("UPDATE orders SET status='CANCELLED',stock_reserved=false WHERE id=?",orderId);
        return true;
    }
}
