package kz.printlab.service;

import java.util.*;
import kz.printlab.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderService {
    private final JdbcTemplate jdbc;
    private final PricingService pricing;
    private final DesignStorageService storage;
    private final ObjectMapper mapper;
    private final PaymentService payments;
    private final DeliveryAddressService addresses;
    public OrderService(JdbcTemplate jdbc, PricingService pricing, DesignStorageService storage, ObjectMapper mapper, PaymentService payments, DeliveryAddressService addresses) {
        this.jdbc=jdbc; this.pricing=pricing; this.storage=storage; this.mapper=mapper; this.payments=payments; this.addresses=addresses;
    }
    private long userId(boolean lock) {
        String email=SecurityContextHolder.getContext().getAuthentication().getName();
        var ids=jdbc.query("select id from users where email=?"+(lock?" for update":""), (rs,n)->rs.getLong(1),email);
        if(ids.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return ids.getFirst();
    }
    private static final String ORDER_COLUMNS="id,user_id,status,subtotal_kzt,discount_kzt,total_kzt,delivery_name,delivery_phone,delivery_address,created_at,stock_reserved";
    private OrderDto readOrder(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new OrderDto(rs.getLong("id"),rs.getLong("user_id"),rs.getString("status"),rs.getLong("subtotal_kzt"),rs.getLong("discount_kzt"),rs.getLong("total_kzt"),rs.getString("delivery_name"),rs.getString("delivery_phone"),rs.getString("delivery_address"),rs.getTimestamp("created_at").toInstant(),rs.getBoolean("stock_reserved"));
    }
    private OrderDto owned(long id,long user,boolean lock) {
        var rows=jdbc.query("select "+ORDER_COLUMNS+" from orders where id=? and user_id=?"+(lock?" for update":""),this::readOrder,id,user);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }
    @Transactional(readOnly=true) public List<OrderDto> list() { return jdbc.query("select "+ORDER_COLUMNS+" from orders where user_id=? order by created_at desc,id desc",this::readOrder,userId(false)); }
    @Transactional(readOnly=true) public OrderDto get(long id) { return owned(id,userId(false),false); }
    @Transactional(readOnly=true) public List<OrderItemDto> items(long id) {
        owned(id,userId(false),false);
        return jdbc.query("select id,variant_id,product_name_snapshot,size_snapshot,color_snapshot,image_url_snapshot,design_snapshot::text,quantity,unit_price_kzt,line_total_kzt from order_items where order_id=? order by id",
            (rs,n)->new OrderItemDto(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getInt(8),rs.getLong(9),rs.getLong(10),imageIds(rs.getString(7))),id);
    }
    private List<String> imageIds(String snapshot) {
        if(snapshot==null) return List.of();
        var ids=new LinkedHashSet<String>(); JsonNode state=mapper.readTree(snapshot);
        for(String side:List.of("front","back")) for(JsonNode element:state.path(side))
            if("image".equals(element.path("type").asText()) && element.path("fileId").isTextual()) ids.add(element.path("fileId").asText());
        return List.copyOf(ids);
    }
    @Transactional(readOnly=true) public byte[] image(long orderId,UUID fileId) {
        return imageForOrder(owned(orderId,userId(false),false),fileId);
    }
    @Transactional(readOnly=true) public byte[] adminImage(long orderId,UUID fileId) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var rows=jdbc.query("select "+ORDER_COLUMNS+" from orders where id=?",this::readOrder,orderId);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return imageForOrder(rows.getFirst(),fileId);
    }
    private byte[] imageForOrder(OrderDto order,UUID fileId) {
        long orderId=order.id();
        var snapshots=jdbc.query("select design_snapshot::text from order_items where order_id=? and design_snapshot is not null",(rs,n)->rs.getString(1),orderId);
        for(String snapshot:snapshots) {
            JsonNode state=mapper.readTree(snapshot);
            for(String side:List.of("front","back")) for(JsonNode element:state.path(side))
                if("image".equals(element.path("type").asText()) && fileId.toString().equals(element.path("fileId").asText())) return storage.loadAuthorizedImage(order.userId(),fileId);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    private record Line(long variantId,Long designId,String name,String size,String color,String image,String state,int quantity,long unit) { }
    private List<Line> cartLines(long user) {
        return jdbc.query("select v.id variant_id,ci.design_id requested_design_id,d.id design_id,p.name,v.size,v.color,p.image_url,d.editor_state::text state,ci.quantity,p.base_price_kzt,v.extra_price_kzt,coalesce(d.print_price_kzt,0) print_price,v.active and p.active available from carts c join cart_items ci on ci.cart_id=c.id join product_variants v on v.id=ci.variant_id join products p on p.id=v.product_id left join designs d on d.id=ci.design_id and d.user_id=c.user_id where c.user_id=? order by ci.id",
            (rs,n)->{
                if(!rs.getBoolean("available")) throw new IllegalArgumentException("Один из товаров больше недоступен.");
                if(rs.getObject("requested_design_id")!=null && rs.getObject("design_id")==null) throw new IllegalArgumentException("Дизайн в корзине недоступен.");
                long unit=Math.addExact(Math.addExact(rs.getLong("base_price_kzt"),rs.getLong("extra_price_kzt")),rs.getLong("print_price"));
                return new Line(rs.getLong("variant_id"),(Long)rs.getObject("design_id"),rs.getString("name"),rs.getString("size"),rs.getString("color"),rs.getString("image_url"),rs.getString("state"),rs.getInt("quantity"),unit);
            },user);
    }
    private long subtotal(List<Line> lines) { long total=0; for(Line line:lines) total=Math.addExact(total,Math.multiplyExact(line.unit,line.quantity)); return total; }
    @Transactional(readOnly=true) public PricingDto preview(String code) {
        var lines=cartLines(userId(false)); if(lines.isEmpty()) throw new IllegalArgumentException("Корзина пуста.");
        return pricing.calculate(subtotal(lines),code,false);
    }
    @Transactional public long checkout(UUID key,String name,String phone,String address,String code,String lat,String lon,String apartment) {
        if(key==null) throw new IllegalArgumentException("Обновите страницу оформления заказа.");
        long user=userId(true);
        var existing=jdbc.query("select id,user_id from orders where checkout_key=?",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},key);
        if(!existing.isEmpty()) { if(existing.getFirst()[1]!=user) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return existing.getFirst()[0]; }
        name=required(name,150,"имя получателя"); phone=KazakhstanPhone.normalize(phone); address=addresses.complete(lat,lon,apartment);
        var variants=jdbc.query("select distinct ci.variant_id from carts c join cart_items ci on ci.cart_id=c.id where c.user_id=? order by ci.variant_id",(rs,n)->rs.getLong(1),user);
        if(variants.isEmpty()) throw new IllegalArgumentException("Корзина пуста.");
        for(long variant:variants) jdbc.queryForObject("select id from product_variants where id=? for update",Long.class,variant);
        List<Line> lines=cartLines(user);
        Map<Long,Long> quantities=new TreeMap<>(); for(Line line:lines) quantities.merge(line.variantId,(long)line.quantity,Math::addExact);
        for(var e:quantities.entrySet()) {
            int changed=jdbc.update("update product_variants set stock_quantity=stock_quantity-? where id=? and active=true and stock_quantity>=?",e.getValue(),e.getKey(),e.getValue());
            if(changed!=1) throw new IllegalArgumentException("Недостаточно товара в наличии.");
        }
        PricingDto calculated=pricing.calculate(subtotal(lines),code,true);
        Long promo=calculated.promoCode().isEmpty()?null:jdbc.queryForObject("select id from promo_codes where code=?",Long.class,calculated.promoCode());
        Long order=jdbc.queryForObject("insert into orders(user_id,promo_code_id,status,subtotal_kzt,discount_kzt,total_kzt,delivery_name,delivery_phone,delivery_address,checkout_key,stock_reserved) values (?,?,'AWAITING_PAYMENT',?,?,?,?,?,?,?,true) returning id",Long.class,user,promo,calculated.subtotalKzt(),calculated.discountKzt(),calculated.totalKzt(),name,phone,address,key);
        for(Line line:lines) jdbc.update("insert into order_items(order_id,variant_id,design_id,product_name_snapshot,size_snapshot,color_snapshot,image_url_snapshot,design_snapshot,quantity,unit_price_kzt,line_total_kzt) values (?,?,?,?,?,?,?,cast(? as jsonb),?,?,?)",order,line.variantId,line.designId,line.name,line.size,line.color,line.image,line.state,line.quantity,line.unit,Math.multiplyExact(line.unit,line.quantity));
        jdbc.update("delete from cart_items where cart_id in (select id from carts where user_id=?)",user);
        return Objects.requireNonNull(order);
    }
    private String required(String value,int max,String label) { if(value==null || value.isBlank() || value.trim().length()>max) throw new IllegalArgumentException("Укажите корректно "+label+"."); return value.trim(); }
    @Transactional public void cancel(long id) {
        long user=userId(false); OrderDto order=owned(id,user,true);
        if(order.status().equals("CANCELLED")) return;
        if(order.status().equals("PAID_SIMULATED")) { payments.cancelPaid(id,user); return; }
        if(!order.status().equals("AWAITING_PAYMENT")) throw new IllegalArgumentException("Этот заказ уже нельзя отменить.");
        if(order.stockReserved()) {
            var variants=jdbc.query("select variant_id,sum(quantity) amount from order_items where order_id=? group by variant_id order by variant_id",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},id);
            for(var v:variants) jdbc.queryForObject("select id from product_variants where id=? for update",Long.class,v[0]);
            for(var v:variants) jdbc.update("update product_variants set stock_quantity=stock_quantity+? where id=?",v[1],v[0]);
        }
        jdbc.update("update orders set status='CANCELLED',stock_reserved=false where id=?",id);
    }
}
