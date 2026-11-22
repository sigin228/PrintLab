package kz.printlab.dto;
import tools.jackson.databind.JsonNode;
public record DesignResponse(Long id, String title, JsonNode editorState, long printPriceKzt) {}
