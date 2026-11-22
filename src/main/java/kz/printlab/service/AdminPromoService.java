package kz.printlab.service;

import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromoService {
    private final JdbcTemplate jdbc;
    public AdminPromoService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list() { return jdbc.queryForList("SELECT id,code,discount_type,discount_value,min_order_kzt,usage_limit,starts_at,expires_at,active FROM promo_codes ORDER BY id DESC LIMIT 200"); }
    @Transactional
    public void create(String code,String type,long value,long minimum,Integer limit) {
        code=code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if(!code.matches("[A-Z0-9_-]{2,60}")) throw new IllegalArgumentException("Код: 2–60 латинских букв, цифр, дефисов или подчёркиваний.");
        if(!Set.of("PERCENT","FIXED").contains(type) || value<=0 || ("PERCENT".equals(type) && value>100) || value>1_000_000_000L || minimum<0 || minimum>1_000_000_000L || (limit!=null && (limit<1 || limit>1_000_000))) throw new IllegalArgumentException("Проверьте тип, значение скидки, минимум и лимит.");
        try { jdbc.update("INSERT INTO promo_codes(code,discount_type,discount_value,min_order_kzt,usage_limit,active) VALUES (?,?,?,?,?,true)",code,type,value,minimum,limit); }
        catch(DataIntegrityViolationException ex) { throw new IllegalArgumentException("Такой промокод уже существует или параметры некорректны."); }
    }
    @Transactional
    public void setActive(long id,boolean active) { if(jdbc.update("UPDATE promo_codes SET active=? WHERE id=?",active,id)!=1) throw new IllegalArgumentException("Промокод не найден."); }
}
