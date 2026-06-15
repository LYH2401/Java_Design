package com.campus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RepairOrderCreateDTO {

    @NotBlank(message = "报修标题不能为空")
    @Size(max = 256, message = "标题长度不能超过256个字符")
    private String title;

    @Size(max = 2000, message = "描述长度不能超过2000个字符")
    private String description;

    @Size(max = 256, message = "地点长度不能超过256个字符")
    private String location;

    private String urgencyLevel;

    public RepairOrderCreateDTO() {}

    public RepairOrderCreateDTO(String title, String description, String location, String urgencyLevel) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.urgencyLevel = urgencyLevel;
    }

    // ---- Getters & Setters ----

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(String urgencyLevel) { this.urgencyLevel = urgencyLevel; }
}
