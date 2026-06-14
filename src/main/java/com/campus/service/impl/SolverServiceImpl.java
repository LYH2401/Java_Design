package com.campus.service.impl;

import com.campus.entity.Message;
import com.campus.repository.MessageMapper;
import com.campus.service.ConversationService;
import com.campus.service.SolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 问题求解服务实现
 * 根据用户意图自动切换回答模式：
 * - 直接评估模式：用户要检查/找bug/验证对错时，直接给结论
 * - 引导教学模式：用户要学习/理解/请教时，分步引导
 */
@Service
public class SolverServiceImpl implements SolverService {

    private static final Logger log = LoggerFactory.getLogger(SolverServiceImpl.class);

    /** 历史轮次限制 */
    private static final int MAX_HISTORY_ROUNDS = 5;

    /** 直接评估模式关键词 */
    private static final Pattern DIRECT_EVAL_PATTERN = Pattern.compile(
            "检查|找bug|有什么问题|对不对|是否正确|有错吗|改错|修正|查错|debug|哪里错|有无问题|有问题吗");

    /** 引导教学模式关键词 */
    private static final Pattern GUIDED_PATTERN = Pattern.compile(
            "为什么|教我|解释|帮我理解|怎么学|思路|原理|推导|讲解|入门");

    private final ChatClient chatClient;
    private final ChatClient deepseekChatClient;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;

    public SolverServiceImpl(
            @Qualifier("chatClient") ChatClient chatClient,
            @Qualifier("deepseekChatClient") ChatClient deepseekChatClient,
            MessageMapper messageMapper,
            ConversationService conversationService) {
        this.chatClient = chatClient;
        this.deepseekChatClient = deepseekChatClient;
        this.messageMapper = messageMapper;
        this.conversationService = conversationService;
    }

    @Override
    public Flux<String> solveStream(Long conversationId, String userMessage, String model) {
        saveMessage(conversationId, "USER", userMessage);

        String historyContext = buildHistoryContext(conversationId);

        String prompt = buildSolverPrompt(historyContext, userMessage);

        ChatClient client = "deepseek".equalsIgnoreCase(model) ? deepseekChatClient : chatClient;

        StringBuilder fullResponse = new StringBuilder();

        return client.prompt()
                .user(prompt)
                .stream()
                .content()
                .buffer(Duration.ofMillis(80))
                .map(chunks -> String.join("", chunks))
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    String response = fullResponse.toString();
                    saveMessage(conversationId, "ASSISTANT", response);
                    log.debug("解题完成: conversationId={}, response长度={}", conversationId, response.length());
                })
                .doOnError(e -> {
                    log.error("解题调用异常: conversationId={}", conversationId, e);
                });
    }

    /**
     * 检测用户意图：直接评估 vs 引导教学
     * 规则：直接评估关键词优先；无明确意图时默认引导模式
     */
    static String detectIntent(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return "GUIDED";
        }
        if (DIRECT_EVAL_PATTERN.matcher(userMessage).find()) {
            return "DIRECT";
        }
        if (GUIDED_PATTERN.matcher(userMessage).find()) {
            return "GUIDED";
        }
        return "GUIDED";
    }

    private String buildSolverPrompt(String historyContext, String userMessage) {
        String intent = detectIntent(userMessage);
        log.debug("Solver intent detected: {} for message: {}", intent,
                userMessage != null ? userMessage.substring(0, Math.min(80, userMessage.length())) : "null");

        if ("DIRECT".equals(intent)) {
            return buildDirectEvalPrompt(historyContext, userMessage);
        } else {
            return buildGuidedPrompt(historyContext, userMessage);
        }
    }

    /**
     * 直接评估模式 Prompt：先给结论，后解释原因
     */
    private String buildDirectEvalPrompt(String historyContext, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位严格而专业的代码审查员与学习评估专家。请直接给出明确的评估结论。\n\n");
        sb.append("## 核心原则\n");
        sb.append("1. **先给结论**：第一句话就要明确回答「正确」还是「有问题」，不要铺垫\n");
        sb.append("2. **后解释原因**：结论之后，再逐步解释判断依据\n");
        sb.append("3. **精准定位**：如果有错误，必须精确指出错误位置和错误原因\n");
        sb.append("4. **给出修正**：如果代码有问题，直接给出修正后的正确写法\n");
        sb.append("5. **简洁专业**：用语精炼，不拐弯抹角，不要说「让我们一起来看看」之类的话\n\n");
        sb.append("## 禁止行为\n");
        sb.append("- 禁止用反问句代替结论（如「你觉得这里对吗？」）\n");
        sb.append("- 禁止说「先思考一下」或「你有什么想法」等拖延性语句\n");
        sb.append("- 禁止在给出结论前进行冗长的铺垫\n\n");
        sb.append("## 回复格式\n");
        sb.append("- 第一段：直接给出结论（正确/有错误/有改进空间）\n");
        sb.append("- 第二段：详细分析，逐一说明问题点和正确之处\n");
        sb.append("- 第三段：如有错误，给出修正代码或改进建议\n");
        sb.append("- 末尾：简要点评整体质量\n\n");

        if (historyContext != null && !historyContext.isEmpty()) {
            sb.append("## 对话历史\n").append(historyContext).append("\n\n");
        }

        sb.append("## 用户的问题\n").append(userMessage);
        return sb.toString();
    }

    /**
     * 引导教学模式 Prompt：分步推理，不直接给答案
     */
    private String buildGuidedPrompt(String historyContext, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位耐心的学习辅导老师，擅长引导学生独立思考、逐步解决问题。\n\n");
        sb.append("## 核心原则\n");
        sb.append("1. **不要直接给出答案**：引导学生通过思考自己找到答案\n");
        sb.append("2. **分步推理**：将复杂问题拆解为小步骤，逐步引导\n");
        sb.append("3. **启发式提问**：用提问的方式激发学生思考\n");
        sb.append("4. **确认理解**：每个步骤后确认学生是否理解\n");
        sb.append("5. **鼓励为主**：即使学生答错，也要先肯定努力再纠正\n\n");
        sb.append("## 回复格式\n");
        sb.append("- 先理解问题：复述学生的疑问，确认你理解正确\n");
        sb.append("- 拆解问题：将问题分解为可管理的子问题\n");
        sb.append("- 逐步引导：每次只给一个提示，等待学生回应\n");
        sb.append("- 总结归纳：问题解决后，总结关键知识点和方法\n\n");
        sb.append("## 适用领域\n");
        sb.append("数学、物理、化学、编程、英语、论文写作、考试备考等所有学科领域。\n\n");

        if (historyContext != null && !historyContext.isEmpty()) {
            sb.append("## 对话历史\n").append(historyContext).append("\n\n");
        }

        sb.append("## 学生的问题\n").append(userMessage);
        return sb.toString();
    }

    private String buildHistoryContext(Long conversationId) {
        List<Message> recent = conversationService.loadRecentRounds(conversationId, MAX_HISTORY_ROUNDS);
        if (recent.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Message msg : recent) {
            String role = "USER".equals(msg.getRole()) ? "学生" : "老师";
            sb.append(role).append("：").append(truncate(msg.getContent())).append("\n");
        }
        return sb.toString();
    }

    private void saveMessage(Long conversationId, String role, String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        messageMapper.insert(msg);
    }

    private String truncate(String content) {
        if (content == null) return "";
        return content.length() > 2000 ? content.substring(0, 2000) + "..." : content;
    }
}
