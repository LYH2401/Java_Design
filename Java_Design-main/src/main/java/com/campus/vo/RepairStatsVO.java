package com.campus.vo;

public class RepairStatsVO {

    private Long totalOrders;
    private Long pendingCount;
    private Long completedCount;
    private Double avgRating;
    private Long avgResponseTime;

    public RepairStatsVO() {}

    public RepairStatsVO(Long totalOrders, Long pendingCount, Long completedCount,
                         Double avgRating, Long avgResponseTime) {
        this.totalOrders = totalOrders;
        this.pendingCount = pendingCount;
        this.completedCount = completedCount;
        this.avgRating = avgRating;
        this.avgResponseTime = avgResponseTime;
    }

    // ---- Getters & Setters ----

    public Long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(Long totalOrders) { this.totalOrders = totalOrders; }

    public Long getPendingCount() { return pendingCount; }
    public void setPendingCount(Long pendingCount) { this.pendingCount = pendingCount; }

    public Long getCompletedCount() { return completedCount; }
    public void setCompletedCount(Long completedCount) { this.completedCount = completedCount; }

    public Double getAvgRating() { return avgRating; }
    public void setAvgRating(Double avgRating) { this.avgRating = avgRating; }

    public Long getAvgResponseTime() { return avgResponseTime; }
    public void setAvgResponseTime(Long avgResponseTime) { this.avgResponseTime = avgResponseTime; }
}
