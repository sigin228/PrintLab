package kz.printlab.service;

import java.util.*;
import kz.printlab.dto.*;
import kz.printlab.entity.*;
import kz.printlab.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class DesignService {
    private final DesignRepository designs; private final UserRepository users; private final CartItemRepository cartItems;
    private final DesignStorageService storage; private final ObjectMapper mapper; private final ProductVariantRepository variants;
    public DesignService(DesignRepository designs, UserRepository users, CartItemRepository cartItems, DesignStorageService storage, ObjectMapper mapper, ProductVariantRepository variants) {
        this.designs=designs; this.users=users; this.cartItems=cartItems; this.storage=storage; this.mapper=mapper; this.variants=variants;
    }
    public User currentUser() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByEmail(authentication.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    private User lockedUser() { var authentication=SecurityContextHolder.getContext().getAuthentication(); if (authentication == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); return users.lockByEmail(authentication.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)); }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private Design owned(long id, long userId) { return designs.findByIdAndUserId(id,userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private DesignResponse response(Design design) { return new DesignResponse(design.getId(),design.getTitle(),mapper.readTree(design.getEditorState()),design.getPrintPriceKzt()); }
    @Transactional public DesignResponse createDesign(DesignRequest request) {
        User user=lockedUser(); Design design=new Design(); design.setUser(user); apply(design,request,user.getId()); return response(designs.saveAndFlush(design));
    }
    @Transactional public DesignResponse updateDesign(long id, DesignRequest request) {
        User user=lockedUser(); Design design=owned(id,user.getId());
        if (cartItems.existsByDesignId(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Дизайн находится в корзине: сначала удалите его из корзины");
        apply(design,request,user.getId()); return response(designs.saveAndFlush(design));
    }
    @Transactional(readOnly=true) public DesignResponse getDesignById(long id) { return response(owned(id,currentUser().getId())); }
    @Transactional(readOnly=true) public List<DesignListItem> getCurrentUserDesigns() { return designs.findByUserIdOrderByUpdatedAtDesc(currentUser().getId()).stream().map(d -> { JsonNode s=mapper.readTree(d.getEditorState()); return new DesignListItem(d.getId(),d.getTitle(),java.time.LocalDateTime.ofInstant(d.getUpdatedAt(),java.time.ZoneId.systemDefault()),s.path("productId").asLong(),s.path("variantId").asLong()); }).toList(); }
    @Transactional public void deleteDesign(long id) {
        Design design=owned(id,lockedUser().getId());
        if (cartItems.existsByDesignId(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Дизайн находится в корзине: сначала удалите его из корзины");
        designs.delete(design);
    }
    private void apply(Design design, DesignRequest request, long userId) {
        if (request == null || request.title() == null || request.title().isBlank() || request.title().trim().length() > 150) throw invalid("Укажите название дизайна (до 150 символов)");
        JsonNode state=request.editorState(); int count=validateEditorState(state,userId);
        design.setTitle(request.title().trim()); design.setEditorState(mapper.writeValueAsString(state)); design.setEditorVersion(1); design.setPrintPriceKzt(count > 0 ? 1000 : 0);
    }
    public int validateEditorState(JsonNode state, long userId) {
        if (state == null || !state.isObject() || state.size()!=5 || !state.has("version") || !state.get("version").isIntegralNumber() || state.get("version").intValue()!=1 || !state.has("front") || !state.has("back") || !state.get("front").isArray() || !state.get("back").isArray()) throw invalid("Неверный формат макета");
        JsonNode productId=state.get("productId"), variantId=state.get("variantId"); if (productId == null || !productId.isIntegralNumber() || !productId.canConvertToLong() || productId.longValue() <= 0 || variantId == null || !variantId.isIntegralNumber() || !variantId.canConvertToLong() || variantId.longValue() <= 0) throw invalid("Выберите товар и вариант"); ProductVariant variant=variants.findById(variantId.longValue()).orElseThrow(() -> invalid("Вариант товара недоступен")); if (!variant.isActive() || !variant.getProduct().isActive() || !variant.getProduct().getId().equals(productId.longValue())) throw invalid("Вариант товара недоступен"); int count=0;
        for (String side : List.of("front","back")) for (JsonNode element : state.get(side)) {
            if (++count > 100 || !element.isObject()) throw invalid("Слишком много элементов или неверный формат");
            String type=element.path("type").asText("");
            Set<String> allowed = new HashSet<>(List.of("type","x","y","scaleX","scaleY","rotation"));
            if (type.equals("text")) allowed.addAll(List.of("text","fontSize","color"));
            else if (type.equals("image")) allowed.add("fileId"); else throw invalid("Неизвестный тип элемента");
            element.propertyNames().forEach(key -> { if (!allowed.contains(key)) throw invalid("Недопустимое поле элемента"); });
            for (String key : List.of("x","y","scaleX","scaleY","rotation")) {
                JsonNode n=element.get(key); if (n==null || !n.isNumber() || !Double.isFinite(n.doubleValue()) || Math.abs(n.doubleValue()) > 10000) throw invalid("Неверные координаты элемента");
                if ((key.equals("scaleX") || key.equals("scaleY")) && (n.doubleValue() <= 0 || n.doubleValue()>100)) throw invalid("Неверный масштаб элемента");
            }
            if (type.equals("text")) {
                JsonNode text=element.get("text"), size=element.get("fontSize"), color=element.get("color");
                if (text==null || !text.isTextual() || text.textValue().length()>500 || size==null || !size.isNumber() || size.doubleValue()<8 || size.doubleValue()>300 || color==null || !color.isTextual() || !color.textValue().matches("#[0-9a-fA-F]{6}")) throw invalid("Неверные параметры текста");
            } else {
                try { JsonNode node=element.get("fileId"); if (node==null || !node.isTextual() || !storage.belongsTo(userId, UUID.fromString(node.textValue()))) throw invalid("Изображение недоступно"); }
                catch (IllegalArgumentException e) { throw invalid("Неверный идентификатор изображения"); }
            }
        }
        return count;
    }
}



