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

import java.util.List;

/**
 * 问题求解服务实现
 * 使用分步推理策略，引导学生逐步解决问题而非直接给答案
 */
@Service
public class SolverServiceImpl implements SolverService {

    private static final Logger log = LoggerFactory.getLogger(SolverServiceImpl.class);

    /** 历史轮次限制 */
    private static final int MAX_HISTORY_ROUNDS = 5;

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
        // 1. 保存用户消息
        saveMessage(conversationId, "USER", userMessage);

        // 2. 加载历史上下文
        String historyContext = buildHistoryContext(conversationId);

        // 3. 构建解题 Prompt
        String prompt = buildSolverPrompt(historyContext, userMessage);

        // 4. 选择模型
        ChatClient client = "deepseek".equalsIgnoreCase(model) ? deepseekChatClient : chatClient;

        // 5. 流式调用
        StringBuilder fullResponse = new StringBuilder();

        return client.prompt()
                .user(prompt)
                .stream()
                .content()
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
     * 构建问题求解专用 Prompt
     */
    private String buildSolverPrompt(String historyContext, String userMessage) {
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
