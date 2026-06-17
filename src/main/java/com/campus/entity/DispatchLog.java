package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@TableName("dispatch_log")
public class DispatchLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("maintainer_id")
    private Long maintainerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("dispatch_time")
    private LocalDateTime dispatchTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("accept_time")
    private LocalDateTime acceptTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("complete_time")
    private LocalDateTime completeTime;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("status")
    private String status; // DISPATCHED / ACCEPTED / REJECTED / COMPLETED

    public DispatchLog() {}

    public DispatchLog(Long id, Long orderId, Long maintainerId, LocalDateTime dispatchTime,
                       LocalDateTime acceptTime, LocalDateTime completeTime, String rejectReason,
                       String status) {
        this.id = id;
        this.orderId = orderId;
        this.maintainerId = maintainerId;
        this.dispatchTime = dispatchTime;
        this.acceptTime = acceptTime;
        this.completeTime = completeTime;
        this.rejectReason = rejectReason;
        this.status = status;
    }

    // ---- Getters & Setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getMaintainerId() { return maintainerId; }
    public void setMaintainerId(Long maintainerId) { this.maintainerId = maintainerId; }

    public LocalDateTime getDispatchTime() { return dispatchTime; }
    public void setDispatchTime(LocalDateTime dispatchTime) { this.dispatchTime = dispatchTime; }

    public LocalDateTime getAcceptTime() { return acceptTime; }
    public void setAcceptTime(LocalDateTime acceptTime) { this.acceptTime = acceptTime; }

    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
