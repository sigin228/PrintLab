package kz.printlab.dto;
import tools.jackson.databind.JsonNode;
public record DesignRequest(String title, JsonNode editorState) {}
