package kz.printlab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import kz.printlab.service.PaymentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class PaymentIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentService payments;
    long userId, otherId;
    String email;
    @BeforeEach void setup() {
        email = UUID.randomUUID() + "@example.com";
        userId = user(email); otherId = user("other-" + email); auth(email);
    }
    long user(String e) { return jdbc.queryForObject("INSERT INTO users(email,password_hash,role,enabled,created_at) VALUES (?,'hash','CUSTOMER',true,current_timestamp) RETURNING id",Long.class,e); }
    void auth(String e) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(e,"unused",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))); }
    long order(long owner,long total) { return jdbc.queryForObject("INSERT INTO orders(user_id,status,subtotal_kzt,discount_kzt,total_kzt,delivery_name,delivery_phone,delivery_address) VALUES (?,'AWAITING_PAYMENT',?,0,?,'Name','123','Address') RETURNING id",Long.class,owner,total,total); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void initializationIsOnceAndTopUpIsRecorded() {
        assertEquals(50000,payments.wallet().balanceKzt());
        assertEquals(50000,payments.wallet().balanceKzt());
        assertEquals(1,payments.wallet().transactions().size());
        payments.topUp(3000);
        assertEquals(53000,payments.wallet().balanceKzt());
        assertEquals(2,payments.wallet().transactions().size());
    }
    @Test void retryAfterDeclineAndRepeatedSuccessfulPaymentDoNotDoubleCharge() {
        long id = order(userId,60000);
        assertFalse(payments.pay(id));
        assertEquals(50000,payments.wallet().balanceKzt());
        assertEquals("DECLINED",jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,id));
        payments.topUp(10000);
        assertTrue(payments.pay(id)); assertTrue(payments.pay(id));
        assertEquals(0,payments.wallet().balanceKzt());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=? AND status='APPROVED'",Integer.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM wallet_transactions WHERE operation='DEBIT' AND wallet_id=(SELECT id FROM demo_wallets WHERE user_id=? )",Integer.class,userId));
        assertEquals("PAID_SIMULATED",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,id));
    }
    @Test void paidCancellationRefundsOnce() {
        long id = order(userId,12500); assertTrue(payments.pay(id));
        assertTrue(payments.cancelPaid(id,userId)); assertFalse(payments.cancelPaid(id,userId));
        assertEquals(50000,payments.wallet().balanceKzt());
        assertEquals("REFUNDED",jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,id));
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM wallet_transactions WHERE operation='REFUND' AND wallet_id=(SELECT id FROM demo_wallets WHERE user_id=? )",Integer.class,userId));
    }
    @Test void anotherUsersOrderCannotBePaid() {
        long id=order(otherId,100);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->payments.pay(id)).getStatusCode().value());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=?",Integer.class,id));
    }
    @Test void invalidTopUpsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->payments.topUp(0));
        assertThrows(IllegalArgumentException.class,()->payments.topUp(1_000_001));
    }
}
