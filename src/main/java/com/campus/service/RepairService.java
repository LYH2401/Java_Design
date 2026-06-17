package com.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.dto.RepairOrderCreateDTO;
import com.campus.entity.Maintainer;
import com.campus.vo.RepairOrderVO;
import com.campus.vo.RepairStatsVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface RepairService {

    RepairOrderVO createOrder(Long userId, RepairOrderCreateDTO dto);

    RepairOrderVO getOrderDetail(Long orderId);

    IPage<RepairOrderVO> listOrders(Page<RepairOrderVO> page, Long userId, String status);

    void assignOrder(Long orderId, Long maintainerId);

    void acceptOrder(Long orderId, Long maintainerId);

    void completeOrder(Long orderId, Long maintainerId);

    void reviewOrder(Long orderId, Long userId, Integer rating, String comment);

    void cancelOrder(Long orderId, Long userId);

    RepairStatsVO getStats();

    List<Maintainer> listAvailableMaintainers(String skillCategory);

    List<Map<String, Object>> getTrend(int days);

    Map<String, Long> getStatusDistribution();

    Map<String, Long> getUrgencyDistribution();
}
