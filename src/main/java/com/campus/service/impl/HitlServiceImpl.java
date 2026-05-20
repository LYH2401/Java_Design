package com.campus.service.impl;

import com.campus.service.HitlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Human-in-the-Loop 人机协同服务实现
 *
 * 流程：
 * 1. Agent 生成回复，若包含敏感操作则附加 "[需要确认] 操作描述"
 * 2. 前端/Agent 检测到 "[需要确认]" 标记，提示用户确认
 * 3. 用户回复 "是/否/yes/no/确认/取消"
 * 4. 系统根据确认结果决定是执行操作还是取消
 */
@Service
public class HitlServiceImpl implements HitlService {

    private static final Logger log = LoggerFactory.getLogger(HitlServiceImpl.class);

    /** "[需要确认]" 标记正则 */
    private static final Pattern CONFIRM_PATTERN =
            Pattern.compile("\\[需要确认\\]\\s*(.+?)(?:\\n|$)");

    /** 肯定回复关键词 */
    private static final Set<String> CONFIRMED_WORDS = Set.of(
            "是", "yes", "y", "确认", "同意", "好的", "可以", "确定", "ok", "好", "1", "true", "confirm");

    /** 否定回复关键词 */
    private static final Set<String> CANCELLED_WORDS = Set.of(
            "否", "no", "n", "取消", "拒绝", "不行", "不要", "不", "0", "false", "cancel");

    /** 待确认操作映射：conversationId → 操作描述 */
    private final Map<Long, String> pendingConfirmations = new ConcurrentHashMap<>();

    @Override
    public boolean requiresConfirmation(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        return response.contains("[需要确认]");
    }

    @Override
    public String extractActionDescription(String response) {
        if (response == null) return null;
        Matcher m = CONFIRM_PATTERN.matcher(response);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    @Override
    public void registerPending(Long conversationId, String actionDescription) {
        if (conversationId == null || actionDescription == null) return;
        pendingConfirmations.put(conversationId, actionDescription);
        log.info("注册待确认操作: conversationId={}, action={}", conversationId, actionDescription);
    }

    @Override
    public String processConfirmation(Long conversationId, String userReply) {
        if (conversationId == null || userReply == null) return null;

        String pending = pendingConfirmations.get(conversationId);
        if (pending == null) {
            log.debug("无待确认操作: conversationId={}", conversationId);
            return null;
        }

        String trimmed = userReply.trim().toLowerCase();
        boolean confirmed = CONFIRMED_WORDS.stream().anyMatch(w ->
                trimmed.equals(w) || trimmed.startsWith(w));
        boolean cancelled = CANCELLED_WORDS.stream().anyMatch(w ->
                trimmed.equals(w) || trimmed.startsWith(w));

        if (!confirmed && !cancelled) {
            // 无法识别，继续等待确认
            log.info("无法识别的确认回复: conversationId={}, reply={}", conversationId, userReply);
            return null;
        }

        // 清理待确认状态
        pendingConfirmations.remove(conversationId);

        if (confirmed) {
            log.info("操作已确认: conversationId={}, action={}", conversationId, pending);
            return "CONFIRMED:" + pending;
        } else {
            log.info("操作已取消: conversationId={}, action={}", conversationId, pending);
            return "CANCELLED:" + pending;
        }
    }

    @Override
    public boolean hasPendingConfirmation(Long conversationId) {
        return conversationId != null && pendingConfirmations.containsKey(conversationId);
    }

    @Override
    public String tryResolveConfirmation(Long conversationId, String userMessage) {
        if (!hasPendingConfirmation(conversationId)) {
            return null;
        }

        // 尝试解析确认/取消
        String result = processConfirmation(conversationId, userMessage);
        if (result != null) {
            return result;
        }

        // 无法识别 → 提示用户
        String pending = pendingConfirmations.get(conversationId);
        if (pending != null) {
            log.info("确认回复无法识别，提示用户: conversationId={}, reply={}", conversationId, userMessage);
            return "PENDING_RETRY:" + pending;
        }
        return null;
    }
}
