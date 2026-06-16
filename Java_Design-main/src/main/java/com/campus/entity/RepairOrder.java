package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@TableName("repair_order")
public class RepairOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("location")
    private String location;

    @TableField("urgency_level")
    private String urgencyLevel; // URGENT / HIGH / NORMAL / LOW

    @TableField("status")
    private String status; // PENDING / ASSIGNED / REPAIRING / COMPLETED / CANCELLED

    @TableField("image_urls")
    private String imageUrls; // JSON 字符串

    @TableField("created_by")
    private String createdBy;

    @TableField("assigned_to")
    private Long assignedTo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("assigned_time")
    private LocalDateTime assignedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("completed_time")
    private LocalDateTime completedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;

    public RepairOrder() {}

    public RepairOrder(Long id, String orderNo, Long userId, String title, String description,
                       String location, String urgencyLevel, String status, String imageUrls,
                       String createdBy, Long assignedTo, LocalDateTime assignedTime,
                       LocalDateTime completedTime, LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.orderNo = orderNo;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.location = location;
        this.urgencyLevel = urgencyLevel;
        this.status = status;
        this.imageUrls = imageUrls;
        this.createdBy = createdBy;
        this.assignedTo = assignedTo;
        this.assignedTime = assignedTime;
        this.completedTime = completedTime;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    // ---- Getters & Setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(String urgencyLevel) { this.urgencyLevel = urgencyLevel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getImageUrls() { return imageUrls; }
    public void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Long getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Long assignedTo) { this.assignedTo = assignedTo; }

    public LocalDateTime getAssignedTime() { return assignedTime; }
    public void setAssignedTime(LocalDateTime assignedTime) { this.assignedTime = assignedTime; }

    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
