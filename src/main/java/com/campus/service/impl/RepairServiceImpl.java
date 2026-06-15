package com.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.dto.BusinessException;
import com.campus.dto.RepairOrderCreateDTO;
import com.campus.dto.ResultCode;
import com.campus.entity.DispatchLog;
import com.campus.entity.Maintainer;
import com.campus.entity.RepairOrder;
import com.campus.entity.RepairReview;
import com.campus.repository.DispatchLogMapper;
import com.campus.repository.MaintainerMapper;
import com.campus.repository.RepairOrderMapper;
import com.campus.repository.RepairReviewMapper;
import com.campus.service.RepairService;
import com.campus.vo.RepairOrderVO;
import com.campus.vo.RepairStatsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class RepairServiceImpl implements RepairService {

    private static final Logger log = LoggerFactory.getLogger(RepairServiceImpl.class);

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final RepairOrderMapper repairOrderMapper;
    private final MaintainerMapper maintainerMapper;
    private final DispatchLogMapper dispatchLogMapper;
    private final RepairReviewMapper repairReviewMapper;

    public RepairServiceImpl(RepairOrderMapper repairOrderMapper,
                             MaintainerMapper maintainerMapper,
                             DispatchLogMapper dispatchLogMapper,
                             RepairReviewMapper repairReviewMapper) {
        this.repairOrderMapper = repairOrderMapper;
        this.maintainerMapper = maintainerMapper;
        this.dispatchLogMapper = dispatchLogMapper;
        this.repairReviewMapper = repairReviewMapper;
    }

    @Override
    @Transactional
    public RepairOrderVO createOrder(Long userId, RepairOrderCreateDTO dto) {
        RepairOrder order = new RepairOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTitle(dto.getTitle());
        order.setDescription(dto.getDescription());
        order.setLocation(dto.getLocation());
        order.setUrgencyLevel(dto.getUrgencyLevel() != null ? dto.getUrgencyLevel() : "NORMAL");
        order.setStatus("PENDING");
        order.setCreateTime(LocalDateTime.now());
        repairOrderMapper.insert(order);

        log.info("创建报修单: orderId={}, orderNo={}, userId={}", order.getId(), order.getOrderNo(), userId);
        return toVO(order);
    }

    @Override
    public RepairOrderVO getOrderDetail(Long orderId) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报修单不存在");
        }
        return toVO(order);
    }

    @Override
    public IPage<RepairOrderVO> listOrders(Page<RepairOrderVO> page, Long userId, String status) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(RepairOrder::getUserId, userId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(RepairOrder::getStatus, status);
        }
        wrapper.orderByDesc(RepairOrder::getCreateTime);

        Page<RepairOrder> mpPage = new Page<>(page.getCurrent(), page.getSize());
        Page<RepairOrder> resultPage = repairOrderMapper.selectPage(mpPage, wrapper);

        List<RepairOrderVO> voList = resultPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        IPage<RepairOrderVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional
    public void assignOrder(Long orderId, Long maintainerId) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报修单不存在");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅待派单状态的报修单可派单");
        }

        Maintainer maintainer = maintainerMapper.selectById(maintainerId);
        if (maintainer == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "维修员不存在");
        }
        if (!"AVAILABLE".equals(maintainer.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该维修员当前不可用");
        }

        order.setStatus("ASSIGNED");
        order.setAssignedTo(maintainerId);
        order.setAssignedTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        repairOrderMapper.updateById(order);

        DispatchLog dispatchLog = new DispatchLog();
        dispatchLog.setOrderId(orderId);
        dispatchLog.setMaintainerId(maintainerId);
        dispatchLog.setDispatchTime(LocalDateTime.now());
        dispatchLog.setStatus("DISPATCHED");
        dispatchLogMapper.insert(dispatchLog);

        maintainer.setStatus("BUSY");
        maintainerMapper.updateById(maintainer);

        log.info("派单成功: orderId={}, maintainerId={}", orderId, maintainerId);
    }

    @Override
    @Transactional
    public void acceptOrder(Long orderId, Long maintainerId) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报修单不存在");
        }
        if (!"ASSIGNED".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已派单状态的报修单可接单");
        }

        List<DispatchLog> logs = dispatchLogMapper.selectList(
                new LambdaQueryWrapper<DispatchLog>()
                        .eq(DispatchLog::getOrderId, orderId)
                        .eq(DispatchLog::getMaintainerId, maintainerId)
                        .eq(DispatchLog::getStatus, "DISPATCHED")
        );
        if (logs.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不存在待接单的派单记录");
        }

        DispatchLog dispatchLog = logs.get(0);
        dispatchLog.setAcceptTime(LocalDateTime.now());
        dispatchLog.setStatus("ACCEPTED");
        dispatchLogMapper.updateById(dispatchLog);

        order.setStatus("REPAIRING");
        order.setUpdateTime(LocalDateTime.now());
        repairOrderMapper.updateById(order);

        log.info("接单成功: orderId={}, maintainerId={}", orderId, maintainerId);
    }

    @Override
    @Transactional
    public void completeOrder(Long orderId, Long maintainerId) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报修单不存在");
        }
        if (!"REPAIRING".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅维修中状态的报修单可完成");
        }

        order.setStatus("COMPLETED");
        order.setCompletedTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        repairOrderMapper.updateById(order);

        List<DispatchLog> logs = dispatchLogMapper.selectList(
                new LambdaQueryWrapper<DispatchLog>()
                        .eq(DispatchLog::getOrderId, orderId)
                        .eq(DispatchLog::getMaintainerId, maintainerId)
                        .eq(DispatchLog::getStatus, "ACCEPTED")
                        .orderByDesc(DispatchLog::getId)
                        .last("LIMIT 1")
        );
        if (!logs.isEmpty()) {
            DispatchLog dispatchLog = logs.get(0);
            dispatchLog.setCompleteTime(LocalDateTime.now());
            dispatchLog.setStatus("COMPLETED");
            dispatchLogMapper.updateById(dispatchLog);
        }

        Maintainer maintainer = maintainerMapper.selectById(maintainerId);
        if (maintainer != null) {
            Long activeCount = dispatchLogMapper.selectCount(
                    new LambdaQueryWrapper<DispatchLog>()
                            .eq(DispatchLog::getMaintainerId, maintainerId)
                            .eq(DispatchLog::getStatus, "ACCEPTED")
            );
            if (activeCount == 0) {
                maintainer.setStatus("AVAILABLE");
                maintainerMapper.updateById(maintainer);
            }
        }

        log.info("维修完成: orderId={}, maintainerId={}", orderId, maintainerId);
    }

    @Override
    @Transactional
    public void reviewOrder(Long orderId, Long userId, Integer rating, String comment) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报修单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能评价自己创建的报修单");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已完成状态的报修单可评价");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "评分必须在1-5之间");
        }

        Long count = repairReviewMapper.selectCount(
                new LambdaQueryWrapper<RepairReview>().eq(RepairReview::getOrderId, orderId)
        );
        if (count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该报修单已评价，不可重复评价");
        }

        RepairReview review = new RepairReview();
        review.setOrderId(orderId);
        review.setUserId(userId);
        review.setRating(rating);
        review.setComment(comment);
        review.setCreateTime(LocalDateTime.now());
        repairReviewMapper.insert(review);

        log.info("评价成功: orderId={}, rating={}", orderId, rating);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报修单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能取消自己创建的报修单");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅待派单状态的报修单可取消");
        }

        order.setStatus("CANCELLED");
        order.setUpdateTime(LocalDateTime.now());
        repairOrderMapper.updateById(order);

        log.info("取消报修单: orderId={}, userId={}", orderId, userId);
    }

    @Override
    public RepairStatsVO getStats() {
        RepairStatsVO stats = new RepairStatsVO();

        stats.setTotalOrders(repairOrderMapper.selectCount(null));

        stats.setPendingCount(repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().in(RepairOrder::getStatus, "PENDING", "ASSIGNED", "REPAIRING")
        ));

        stats.setCompletedCount(repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, "COMPLETED")
        ));

        List<RepairReview> reviews = repairReviewMapper.selectList(null);
        if (reviews != null && !reviews.isEmpty()) {
            OptionalDouble avg = reviews.stream()
                    .mapToInt(RepairReview::getRating)
                    .average();
            stats.setAvgRating(Math.round(avg.orElse(0.0) * 10.0) / 10.0);
        } else {
            stats.setAvgRating(0.0);
        }

        stats.setAvgResponseTime(calcAvgResponseTime());

        return stats;
    }

    @Override
    public List<Maintainer> listAvailableMaintainers(String skillCategory) {
        LambdaQueryWrapper<Maintainer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Maintainer::getStatus, "AVAILABLE");
        if (skillCategory != null && !skillCategory.isEmpty()) {
            wrapper.eq(Maintainer::getSkillCategory, skillCategory);
        }
        return maintainerMapper.selectList(wrapper);
    }

    @Override
    public List<Map<String, Object>> getTrend(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<RepairOrder> orders = repairOrderMapper.selectList(
                new LambdaQueryWrapper<RepairOrder>()
                        .ge(RepairOrder::getCreateTime, startDate.atStartOfDay())
        );

        Map<String, Long> dateCount = new LinkedHashMap<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            dateCount.put(d.toString(), 0L);
        }

        for (RepairOrder order : orders) {
            if (order.getCreateTime() != null) {
                String date = order.getCreateTime().toLocalDate().toString();
                dateCount.merge(date, 1L, Long::sum);
            }
        }

        return dateCount.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", e.getKey());
                    item.put("count", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Long> getStatusDistribution() {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("PENDING", repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, "PENDING")));
        map.put("ASSIGNED", repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, "ASSIGNED")));
        map.put("REPAIRING", repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, "REPAIRING")));
        map.put("COMPLETED", repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, "COMPLETED")));
        map.put("CANCELLED", repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, "CANCELLED")));
        return map;
    }

    @Override
    public Map<String, Long> getUrgencyDistribution() {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("URGENT", repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getUrgencyLevel, "URGENT")));
        map.put("HIGH", repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getUrgencyLevel, "HIGH")));
        map.put("NORMAL", repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getUrgencyLevel, "NORMAL")));
        map.put("LOW", repairOrderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getUrgencyLevel, "LOW")));
        return map;
    }

    // ==================== 私有方法 ====================

    private String generateOrderNo() {
        String timePart = LocalDateTime.now().format(ORDER_NO_FORMATTER);
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "REP" + timePart + random;
    }

    private RepairOrderVO toVO(RepairOrder order) {
        RepairOrderVO vo = new RepairOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setTitle(order.getTitle());
        vo.setDescription(order.getDescription());
        vo.setLocation(order.getLocation());
        vo.setUrgencyLevel(order.getUrgencyLevel());
        vo.setStatus(order.getStatus());
        vo.setImageUrls(order.getImageUrls());
        vo.setCreatedBy(order.getCreatedBy());
        vo.setAssignedTo(order.getAssignedTo());
        vo.setAssignedTime(order.getAssignedTime());
        vo.setCompletedTime(order.getCompletedTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        if (order.getAssignedTo() != null) {
            Maintainer maintainer = maintainerMapper.selectById(order.getAssignedTo());
            if (maintainer != null) {
                vo.setMaintainerName(maintainer.getName());
            }
        }

        List<RepairReview> reviews = repairReviewMapper.selectList(
                new LambdaQueryWrapper<RepairReview>().eq(RepairReview::getOrderId, order.getId())
        );
        if (!reviews.isEmpty()) {
            RepairReview review = reviews.get(0);
            vo.setReviewRating(review.getRating());
            vo.setReviewComment(review.getComment());
        }

        return vo;
    }

    private Long calcAvgResponseTime() {
        List<DispatchLog> completedLogs = dispatchLogMapper.selectList(
                new LambdaQueryWrapper<DispatchLog>()
                        .eq(DispatchLog::getStatus, "COMPLETED")
                        .isNotNull(DispatchLog::getDispatchTime)
                        .isNotNull(DispatchLog::getAcceptTime)
        );
        if (completedLogs.isEmpty()) {
            return 0L;
        }

        long totalMinutes = 0;
        int count = 0;
        for (DispatchLog log : completedLogs) {
            long minutes = java.time.Duration.between(log.getDispatchTime(), log.getAcceptTime()).toMinutes();
            totalMinutes += minutes;
            count++;
        }
        return count > 0 ? totalMinutes / count : 0L;
    }
}
