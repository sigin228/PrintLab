package kz.printlab.service;

import java.net.URI;
import java.net.HttpURLConnection;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class DeliveryAddressService {
    public record Address(String address, double lat, double lon) { }
    private final String endpoint;
    private final ObjectMapper mapper;
    private long lastRequest;

    public DeliveryAddressService(@Value("${printlab.geocoder.reverse-url:https://photon.komoot.io/reverse}") String endpoint, ObjectMapper mapper) {
        this.endpoint = endpoint;
        this.mapper = mapper;
    }

    public Address resolve(String latText, String lonText) {
        double lat, lon;
        try { lat = Double.parseDouble(latText); lon = Double.parseDouble(lonText); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Выберите адрес на карте."); }
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < 50.9 || lat > 51.4 || lon < 71.1 || lon > 71.8)
            throw new IllegalArgumentException("Доставка доступна только в Астане. Выберите адрес на карте.");
        JsonNode features;
        try {
            synchronized (this) {
                long delay = 1100 - (System.currentTimeMillis() - lastRequest);
                if (delay > 0) Thread.sleep(delay);
                lastRequest = System.currentTimeMillis();
            }
            URI uri = URI.create(endpoint + (endpoint.contains("?") ? "&" : "?") + "lat=" + lat + "&lon=" + lon + "&limit=5");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "PrintLab/1.0 (delivery address verification)");
            try {
                if (connection.getResponseCode() != 200) throw new IllegalStateException("geocoder unavailable");
                try (var input = connection.getInputStream()) { features = mapper.readTree(input).path("features"); }
            } finally { connection.disconnect(); }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Не удалось проверить адрес. Повторите попытку позже.");
        } catch (Exception ex) { throw new IllegalArgumentException("Не удалось проверить адрес. Повторите попытку позже."); }
        for (JsonNode feature : features) {
            JsonNode p = feature.path("properties");
            String city = p.path("city").asText("").toLowerCase(Locale.ROOT);
            String street = p.path("street").asText("").trim();
            String house = p.path("housenumber").asText("").trim();
            JsonNode coords = feature.path("geometry").path("coordinates");
            if (!p.path("countrycode").asText("").equalsIgnoreCase("KZ") || !(city.equals("астана") || city.equals("astana")) || street.isBlank() || house.isBlank() || !coords.isArray() || coords.size() < 2) continue;
            double featureLon = coords.get(0).asDouble();
            double featureLat = coords.get(1).asDouble();
            double dLat = Math.toRadians(featureLat - lat), dLon = Math.toRadians(featureLon - lon);
            double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(featureLat)) * Math.pow(Math.sin(dLon / 2), 2);
            if (6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)) > 100) continue;
            return new Address("Астана, " + street + ", " + house, lat, lon);
        }
        throw new IllegalArgumentException("Не удалось найти дом по этой точке в Астане. Укажите точное расположение дома.");
    }
    public String complete(String lat, String lon, String apartment) {
        String address = resolve(lat, lon).address();
        if (apartment != null && !apartment.isBlank()) {
            apartment = apartment.trim();
            if (apartment.length() > 40 || apartment.matches(".*[\\r\\n].*")) throw new IllegalArgumentException("Укажите корректный номер квартиры или офиса.");
            address += ", кв./офис " + apartment;
        }
        return address;
    }
}
