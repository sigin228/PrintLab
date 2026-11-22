package kz.printlab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import kz.printlab.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Uses committed fixtures: distinct threads must observe the same rows and transaction locks. */
@SpringBootTest
class PaymentConcurrencyTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentService payments;
    @Test void concurrentPaymentsForSameOrderDebitOnce() throws Exception {
        String email=UUID.randomUUID()+"@example.com";
        long user=jdbc.queryForObject("INSERT INTO users(email,password_hash,role,enabled,created_at) VALUES (?,'hash','CUSTOMER',true,current_timestamp) RETURNING id",Long.class,email);
        Long order=null;
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            order=jdbc.queryForObject("INSERT INTO orders(user_id,status,subtotal_kzt,discount_kzt,total_kzt,delivery_name,delivery_phone,delivery_address) VALUES (?,'AWAITING_PAYMENT',12500,0,12500,'Name','123','Address') RETURNING id",Long.class,user);
            final long orderId=order;
            CountDownLatch start=new CountDownLatch(1);
            Callable<Boolean> task=()->{
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,"unused",List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
                try { if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout waiting for concurrent test"); return payments.pay(orderId); }
                finally { SecurityContextHolder.clearContext(); }
            };
            Future<Boolean> first=executor.submit(task), second=executor.submit(task);
            start.countDown();
            assertTrue(first.get(20,TimeUnit.SECONDS)); assertTrue(second.get(20,TimeUnit.SECONDS));
            assertEquals(37500L,jdbc.queryForObject("SELECT balance_kzt FROM demo_wallets WHERE user_id=?",Long.class,user));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=? AND status='APPROVED'",Integer.class,orderId));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM wallet_transactions WHERE wallet_id=(SELECT id FROM demo_wallets WHERE user_id=?) AND operation='DEBIT'",Integer.class,user));
        } finally {
            executor.shutdownNow(); executor.awaitTermination(30,TimeUnit.SECONDS);
            jdbc.update("DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT id FROM demo_wallets WHERE user_id=?)",user);
            jdbc.update("DELETE FROM payments WHERE wallet_id IN (SELECT id FROM demo_wallets WHERE user_id=?)",user);
            jdbc.update("DELETE FROM demo_wallets WHERE user_id=?",user);
            if(order!=null) jdbc.update("DELETE FROM orders WHERE id=?",order);
            jdbc.update("DELETE FROM users WHERE id=?",user);
        }
    }
}
