package kz.printlab.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "designs")
public class Design {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 150) private String title;
    @Column(name = "print_text", columnDefinition = "text") private String printText;
    @Column(name = "image_path", columnDefinition = "text") private String imagePath;
    @Column(nullable = false, length = 20) private String placement = "BOTH";
    @Column(name = "print_price_kzt", nullable = false) private long printPriceKzt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "editor_state", columnDefinition = "jsonb", nullable = false) private String editorState = "{}";
    @Column(name = "preview_front_path", columnDefinition = "text") private String previewFrontPath;
    @Column(name = "preview_back_path", columnDefinition = "text") private String previewBackPath;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "editor_version", nullable = false) private int editorVersion = 1;

    @PrePersist void createTimestamps() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public User getUser() { return user; } public void setUser(User user) { this.user = user; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getPrintText() { return printText; } public void setPrintText(String printText) { this.printText = printText; }
    public String getImagePath() { return imagePath; } public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getPlacement() { return placement; } public void setPlacement(String placement) { this.placement = placement; }
    public long getPrintPriceKzt() { return printPriceKzt; } public void setPrintPriceKzt(long price) { this.printPriceKzt = price; }
    public String getEditorState() { return editorState; } public void setEditorState(String state) { this.editorState = state; }
    public String getPreviewFrontPath() { return previewFrontPath; } public void setPreviewFrontPath(String path) { this.previewFrontPath = path; }
    public String getPreviewBackPath() { return previewBackPath; } public void setPreviewBackPath(String path) { this.previewBackPath = path; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
    public int getEditorVersion() { return editorVersion; } public void setEditorVersion(int version) { this.editorVersion = version; }
}
