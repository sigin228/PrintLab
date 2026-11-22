package kz.printlab.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Administrative mutations are protected at the service as well as the HTTP layer. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {
    private final JdbcTemplate jdbc;
    private final PaymentService payments;
    public AdminService(JdbcTemplate jdbc, PaymentService payments) { this.jdbc = jdbc; this.payments = payments; }

    @Transactional(readOnly=true)
    public Map<String,Object> dashboard() {
        Map<String,Object> stats = new LinkedHashMap<>();
        stats.put("orders", count("SELECT count(*) FROM orders"));
        stats.put("newOrders", count("SELECT count(*) FROM orders WHERE status='AWAITING_PAYMENT'"));
        stats.put("paidOrders", count("SELECT count(*) FROM orders WHERE status='PAID_SIMULATED'"));
        stats.put("production", count("SELECT count(*) FROM orders WHERE status='IN_PRODUCTION'"));
        stats.put("completed", count("SELECT count(*) FROM orders WHERE status='COMPLETED'"));
        stats.put("users", count("SELECT count(*) FROM users"));
        stats.put("products", count("SELECT count(*) FROM products WHERE active"));
        stats.put("revenue", jdbc.queryForObject("SELECT COALESCE(sum(amount_kzt),0) FROM payments WHERE status='APPROVED'", java.math.BigDecimal.class));
        return stats;
    }
    private long count(String sql) { return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class)); }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> products() { return jdbc.queryForList("SELECT id,name,category,description,base_price_kzt,image_url,active FROM products ORDER BY id DESC"); }
    @Transactional(readOnly=true)
    public Map<String,Object> product(long id) { return one("SELECT id,name,category,description,base_price_kzt,image_url,active FROM products WHERE id=?", id); }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> variants(long id) { return jdbc.queryForList("SELECT id,product_id,sku,size,color,extra_price_kzt,stock_quantity,active FROM product_variants WHERE product_id=? ORDER BY id", id); }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> orders(String status, String search) {
        String s = status == null ? "" : status.trim();
        String q = search == null ? "" : search.trim();
        if (!s.isEmpty() && !Set.of("AWAITING_PAYMENT","PAID_SIMULATED","IN_PRODUCTION","READY","COMPLETED","CANCELLED").contains(s)) throw bad("Неизвестный статус");
        if (!q.isEmpty() && !q.matches("[0-9]{1,18}")) throw bad("Введите номер заказа");
        return jdbc.queryForList("SELECT o.id,o.status,o.total_kzt,o.created_at,u.email FROM orders o JOIN users u ON u.id=o.user_id WHERE (?='' OR o.status=?) AND (?='' OR CAST(o.id AS text)=?) ORDER BY o.created_at DESC LIMIT 200", s,s,q,q);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> order(long id) { return one("SELECT o.*,u.email FROM orders o JOIN users u ON u.id=o.user_id WHERE o.id=?", id); }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> orderItems(long id) { return jdbc.queryForList("SELECT id,product_name_snapshot,size_snapshot,color_snapshot,quantity,unit_price_kzt,line_total_kzt,design_snapshot FROM order_items WHERE order_id=? ORDER BY id", id); }
    private Map<String,Object> one(String sql, long id) { var rows=jdbc.queryForList(sql,id); if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return rows.get(0); }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
    private String required(String value,int max,String label) { if(value==null || value.isBlank() || value.trim().length()>max) throw bad("Проверьте поле: "+label); return value.trim(); }

    @Transactional
    public long saveProduct(Long id,String name,String description,String category,long price,String imageUrl,boolean active) {
        name=required(name,150,"название"); category=required(category,20,"категория");
        if(!Set.of("TSHIRT","HOODIE","SWEATSHIRT").contains(category)) throw bad("Неизвестная категория");
        if(description != null && description.length()>5000) throw bad("Описание слишком длинное");
        if(price<0 || price>1_000_000_000L) throw bad("Некорректная цена");
        if(imageUrl!=null && !imageUrl.isBlank() && !imageUrl.matches("/images/catalog/[A-Za-z0-9._/-]+|/product-images/[0-9a-f-]{36}")) throw bad("Используйте загруженное изображение");
        if(id==null) return Objects.requireNonNull(jdbc.queryForObject("INSERT INTO products(name,category,description,base_price_kzt,image_url,active,created_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP) RETURNING id",Long.class,name,category,description,price,imageUrl,active));
        int n=jdbc.update("UPDATE products SET name=?,category=?,description=?,base_price_kzt=?,image_url=?,active=? WHERE id=?",name,category,description,price,imageUrl,active,id);
        if(n!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return id;
    }
    @Transactional
    public void saveVariant(long productId,Long id,String sku,String size,String color,long extraPrice,int stock,Integer expectedStock,boolean active) {
        sku=required(sku,60,"SKU"); size=required(size,10,"размер"); color=required(color,40,"цвет");
        if(extraPrice<0 || extraPrice>1_000_000_000L || stock<0 || stock>1_000_000) throw bad("Некорректная цена или остаток");
        if(jdbc.queryForObject("SELECT count(*) FROM products WHERE id=?",Long.class,productId)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(id==null) jdbc.update("INSERT INTO product_variants(product_id,sku,size,color,extra_price_kzt,stock_quantity,active) VALUES (?,?,?,?,?,?,?)",productId,sku,size,color,extraPrice,stock,active);
        else {
            if(expectedStock==null || expectedStock<0) throw bad("Обновите страницу и проверьте остаток");
            int updated=jdbc.update("UPDATE product_variants SET sku=?,size=?,color=?,extra_price_kzt=?,stock_quantity=?,active=? WHERE id=? AND product_id=? AND stock_quantity=?",sku,size,color,extraPrice,stock,active,id,productId,expectedStock);
            if(updated!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Остаток изменился. Обновите страницу и проверьте новое значение.");
        }
    }
    @Transactional
    public void advanceOrder(long id,String next) {
        var rows=jdbc.queryForList("SELECT status FROM orders WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String status=Objects.toString(rows.get(0).get("status"));
        if(!Objects.equals(Map.of("PAID_SIMULATED","IN_PRODUCTION","IN_PRODUCTION","READY","READY","COMPLETED").get(status),next)) throw bad("Недопустимый переход статуса");
        jdbc.update("UPDATE orders SET status=? WHERE id=?",next,id);
    }
    @Transactional
    public void cancelOrder(long id) {
        var rows = jdbc.queryForList("SELECT user_id,status,stock_reserved FROM orders WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var order = rows.get(0);
        String status = Objects.toString(order.get("status"));
        if (status.equals("CANCELLED")) return;
        if (status.equals("PAID_SIMULATED")) {
            payments.cancelPaid(id, ((Number) order.get("user_id")).longValue());
            return;
        }
        if (!status.equals("AWAITING_PAYMENT")) throw bad("Этот заказ уже нельзя отменить");
        if (Boolean.TRUE.equals(order.get("stock_reserved"))) {
            var lines = jdbc.query("SELECT variant_id,SUM(quantity) FROM order_items WHERE order_id=? GROUP BY variant_id ORDER BY variant_id", (rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},id);
            for (long[] line : lines) jdbc.update("UPDATE product_variants SET stock_quantity=stock_quantity+? WHERE id=?",line[1],line[0]);
        }
        jdbc.update("UPDATE orders SET status='CANCELLED',stock_reserved=false WHERE id=?",id);
    }}
