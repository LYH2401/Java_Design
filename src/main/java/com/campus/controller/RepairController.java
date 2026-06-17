package com.campus.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.dto.R;
import com.campus.dto.RepairOrderCreateDTO;
import com.campus.entity.Maintainer;
import com.campus.service.RepairService;
import com.campus.vo.RepairOrderVO;
import com.campus.vo.RepairStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repair")
@Tag(name = "校园报修", description = "报修单提交、查询、派单、接单、完成、评价等接口")
public class RepairController {

    private static final Logger log = LoggerFactory.getLogger(RepairController.class);

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @PostMapping("/orders")
    @Operation(summary = "提交报修单")
    public R<RepairOrderVO> createOrder(
            @Parameter(description = "用户ID（默认1）") @RequestParam(defaultValue = "1") Long userId,
            @Valid @RequestBody RepairOrderCreateDTO dto) {
        return R.ok(repairService.createOrder(userId, dto));
    }

    @GetMapping("/orders")
    @Operation(summary = "分页查询报修单列表")
    public R<Map<String, Object>> listOrders(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "用户ID（可选）") @RequestParam(required = false) Long userId,
            @Parameter(description = "状态筛选（可选）") @RequestParam(required = false) String status) {

        Page<RepairOrderVO> mpPage = new Page<>(page, pageSize);
        var result = repairService.listOrders(mpPage, userId, status);
        return R.ok(Map.of(
                "records", result.getRecords(),
                "total", result.getTotal(),
                "page", result.getCurrent(),
                "pageSize", result.getSize()
        ));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "查询报修单详情")
    public R<RepairOrderVO> getOrderDetail(
            @Parameter(description = "报修单ID") @PathVariable Long id) {
        return R.ok(repairService.getOrderDetail(id));
    }

    @PutMapping("/orders/{id}/assign")
    @Operation(summary = "派单给维修员")
    public R<Void> assignOrder(
            @Parameter(description = "报修单ID") @PathVariable Long id,
            @Parameter(description = "维修员ID") @RequestParam Long maintainerId) {
        repairService.assignOrder(id, maintainerId);
        return R.ok();
    }

    @PutMapping("/orders/{id}/accept")
    @Operation(summary = "维修员接单")
    public R<Void> acceptOrder(
            @Parameter(description = "报修单ID") @PathVariable Long id,
            @Parameter(description = "维修员ID") @RequestParam Long maintainerId) {
        repairService.acceptOrder(id, maintainerId);
        return R.ok();
    }

    @PutMapping("/orders/{id}/complete")
    @Operation(summary = "完成维修")
    public R<Void> completeOrder(
            @Parameter(description = "报修单ID") @PathVariable Long id,
            @Parameter(description = "维修员ID") @RequestParam Long maintainerId) {
        repairService.completeOrder(id, maintainerId);
        return R.ok();
    }

    @PutMapping("/orders/{id}/cancel")
    @Operation(summary = "取消报修单")
    public R<Void> cancelOrder(
            @Parameter(description = "报修单ID") @PathVariable Long id,
            @Parameter(description = "用户ID") @RequestParam Long userId) {
        repairService.cancelOrder(id, userId);
        return R.ok();
    }

    @PostMapping("/orders/{id}/review")
    @Operation(summary = "评价报修单")
    public R<Void> reviewOrder(
            @Parameter(description = "报修单ID") @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
            Long userId = body.get("userId") instanceof Number n ? n.longValue() : 1L;
        Integer rating = body.get("rating") instanceof Number n ? n.intValue() : null;
        String comment = body.get("comment") instanceof String s ? s : null;
        repairService.reviewOrder(id, userId, rating, comment);
        return R.ok();
    }

    @GetMapping("/stats")
    @Operation(summary = "报修统计概览")
    public R<RepairStatsVO> getStats() {
        return R.ok(repairService.getStats());
    }

    @GetMapping("/maintainers")
    @Operation(summary = "查询可用维修员列表")
    public R<List<Maintainer>> listAvailableMaintainers(
            @Parameter(description = "技能分类（可选）") @RequestParam(required = false) String skillCategory) {
        return R.ok(repairService.listAvailableMaintainers(skillCategory));
    }
}
