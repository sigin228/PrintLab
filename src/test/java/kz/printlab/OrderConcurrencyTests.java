package kz.printlab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import kz.printlab.service.OrderService;
import kz.printlab.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Independent committed fixtures ensure PostgreSQL locks are exercised across transactions. */
@SpringBootTest
class OrderConcurrencyTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean kz.printlab.service.DeliveryAddressService addresses;
    @org.junit.jupiter.api.BeforeEach void mockAddress() { org.mockito.Mockito.when(addresses.complete(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn("Астана, Тестовая, 1"); }
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    record Fixture(long first, long second, String firstEmail, String secondEmail, long product, long variant, long promo, String code) {}
    private Fixture fixture(int stock) { return java.util.Objects.requireNonNull(transactions.execute(status -> {
        String suffix=UUID.randomUUID().toString().replace("-", "");
        String a="race-a-"+suffix+"@example.com", b="race-b-"+suffix+"@example.com";
        long u1=user(a), u2=user(b);
        long product=jdbc.queryForObject("INSERT INTO products(name,category,base_price_kzt) VALUES ('Concurrency test','TSHIRT',5000) RETURNING id",Long.class);
        long variant=jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,size,color,stock_quantity) VALUES (?,?,'M','Black',?) RETURNING id",Long.class,product,"race-"+suffix,stock);
        for(long u:new long[]{u1,u2}) {
            long cart=jdbc.queryForObject("INSERT INTO carts(user_id) VALUES (?) RETURNING id",Long.class,u);
            jdbc.update("INSERT INTO cart_items(cart_id,variant_id,quantity) VALUES (?,?,1)",cart,variant);
        }
        String code="R"+suffix.toUpperCase(Locale.ROOT);
        long promo=jdbc.queryForObject("INSERT INTO promo_codes(code,discount_type,discount_value,usage_limit) VALUES (?,'PERCENT',10,1) RETURNING id",Long.class,code);
        return new Fixture(u1,u2,a,b,product,variant,promo,code);
    })); }
    private long user(String email) { return jdbc.queryForObject("INSERT INTO users(email,password_hash,role,enabled,created_at) VALUES (?,'hash','CUSTOMER',true,current_timestamp) RETURNING id",Long.class,email); }
    private void auth(String email) { SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email,"",List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))); }
    private void cleanup(Fixture f) {
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT id FROM demo_wallets WHERE user_id IN (?,?))",f.first(),f.second());
        jdbc.update("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))",f.first(),f.second());
        jdbc.update("DELETE FROM demo_wallets WHERE user_id IN (?,?)",f.first(),f.second());
        jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))",f.first(),f.second());
        jdbc.update("DELETE FROM orders WHERE user_id IN (?,?)",f.first(),f.second());
        jdbc.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id IN (?,?))",f.first(),f.second());
        jdbc.update("DELETE FROM carts WHERE user_id IN (?,?)",f.first(),f.second());
        jdbc.update("DELETE FROM promo_codes WHERE id=?",f.promo());
        jdbc.update("DELETE FROM product_variants WHERE id=?",f.variant());
        jdbc.update("DELETE FROM products WHERE id=?",f.product());
        jdbc.update("DELETE FROM users WHERE id IN (?,?)",f.first(),f.second());
    }
    private boolean checkout(String email,String code,CountDownLatch start) throws Exception {
        auth(email);
        try { if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("start timeout"); orders.checkout(UUID.randomUUID(),"Name","+77011234567","Address",code,"51.1","71.4",""); return true; }
        catch(IllegalArgumentException expected) { return false; }
        finally { SecurityContextHolder.clearContext(); }
    }
    private void competingCheckouts(int stock,boolean usePromo) throws Exception {
        Fixture f=fixture(stock); ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start=new CountDownLatch(1); String code=usePromo?f.code():"";
            Future<Boolean> a=executor.submit(()->checkout(f.firstEmail(),code,start));
            Future<Boolean> b=executor.submit(()->checkout(f.secondEmail(),code,start));
            start.countDown(); boolean first=a.get(20,TimeUnit.SECONDS),second=b.get(20,TimeUnit.SECONDS);
            assertNotEquals(first,second,"Exactly one checkout must succeed");
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id IN (?,?)",Integer.class,f.first(),f.second()));
            assertEquals(stock-1,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=?",Integer.class,f.variant()));
            if(usePromo) assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM orders WHERE promo_code_id=? AND status<>'CANCELLED'",Integer.class,f.promo()));
        } finally { executor.shutdownNow(); executor.awaitTermination(30,TimeUnit.SECONDS); cleanup(f); }
    }
    @Test void competingForLastItemCannotOversell() throws Exception { competingCheckouts(1,false); }
    @Test void competingForSingleUsePromoCannotUseItTwice() throws Exception { competingCheckouts(2,true); }
    @Test void concurrentPaymentAndCancelNeverLeaveACharge() throws Exception {
        Fixture f=fixture(1); ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            auth(f.firstEmail()); long order=orders.checkout(UUID.randomUUID(),"Name","+77011234567","Address","","51.1","71.4",""); SecurityContextHolder.clearContext();
            CountDownLatch start=new CountDownLatch(1);
            Future<Boolean> pay=executor.submit(()->{ auth(f.firstEmail()); try { if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("start timeout"); try { return payments.pay(order); } catch(IllegalArgumentException cancelledFirst) { return false; } } finally { SecurityContextHolder.clearContext(); } });
            Future<?> cancel=executor.submit(()->{ auth(f.firstEmail()); try { if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("start timeout"); orders.cancel(order); return null; } finally { SecurityContextHolder.clearContext(); } });
            start.countDown(); pay.get(20,TimeUnit.SECONDS); cancel.get(20,TimeUnit.SECONDS);
            assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order));
            assertEquals(1,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=?",Integer.class,f.variant()));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=? AND status='APPROVED'",Integer.class,order));
            var balances=jdbc.query("SELECT balance_kzt FROM demo_wallets WHERE user_id=?",(rs,n)->rs.getLong(1),f.first());
            if(!balances.isEmpty()) assertEquals(50000L,balances.getFirst());
        } finally { SecurityContextHolder.clearContext(); executor.shutdownNow(); executor.awaitTermination(30,TimeUnit.SECONDS); cleanup(f); }
    }
}
