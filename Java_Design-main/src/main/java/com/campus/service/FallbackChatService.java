package com.campus.service;

import com.campus.entity.AlertLog;
import com.campus.repository.AlertLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 降级服务：当 DashScope API 不可用时，使用关键词匹配返回预设回答
 */
@Service
public class FallbackChatService {

    private static final Logger log = LoggerFactory.getLogger(FallbackChatService.class);

    private final AlertLogMapper alertLogMapper;

    /** 关键词 → 回答映射 */
    private static final LinkedHashMap<Pattern, String> FALLBACK_RULES = new LinkedHashMap<>();

    static {
        FALLBACK_RULES.put(Pattern.compile(".*(图书馆|借书|还书|自习|阅览).*"),
                "图书馆开放时间：周一至周五 8:00-22:00，周六周日 9:00-21:00。\n"
                + "借阅规则：本科生最多借5本/30天，研究生最多借10本/60天。\n"
                + "一层为自习区，二至四层为各类图书区，五层为学术报告厅。");

        FALLBACK_RULES.put(Pattern.compile(".*(校园卡|一卡通|充值|挂失|补办).*"),
                "校园一卡通服务：\n"
                + "● 充值：微信关注「智慧校园」公众号 → 校园服务 → 一卡通充值\n"
                + "● 挂失：通过公众号立即挂失\n"
                + "● 补办：携带身份证到行政楼一楼，工本费20元\n"
                + "● 人工充值：行政楼一楼财务处（工作日 9:00-16:30）");

        FALLBACK_RULES.put(Pattern.compile(".*(食堂|吃饭|餐厅|就餐).*"),
                "学校共有三个食堂：\n"
                + "● 第一食堂（东区）：一楼大众餐、二楼风味小吃\n"
                + "● 第二食堂（西区）：一楼自助餐、二楼清真餐厅\n"
                + "● 第三食堂（北区）：特色小炒、麻辣烫\n"
                + "营业时间：早餐 6:30-9:00 / 午餐 11:00-13:00 / 晚餐 17:00-19:30");

        FALLBACK_RULES.put(Pattern.compile(".*(课表|课程|上课|教室|选课).*"),
                "教务系统相关：\n"
                + "● 课表查询：登录教务系统 jwxt.example.edu.cn\n"
                + "● 选课时间：每学期第1-2周\n"
                + "● 教室查询：教学楼A区（文科）/ B区（理工科）\n"
                + "如需详细课表信息，请在教务系统登录后查询。");

        FALLBACK_RULES.put(Pattern.compile(".*(体育馆|运动|篮球|游泳|健身|操场).*"),
                "体育馆位于校园西北角，设施包括：\n"
                + "● 篮球场（室内+室外）\n"
                + "● 羽毛球场\n"
                + "● 游泳池（夏季开放）\n"
                + "● 健身房（凭校园卡进入）\n"
                + "开放时间：6:00-22:00");

        FALLBACK_RULES.put(Pattern.compile(".*(校医院|看病|急诊|就医|医保).*"),
                "校医院位于行政楼东侧：\n"
                + "● 门诊时间：8:00-17:00（工作日）/ 8:00-12:00（周末）\n"
                + "● 24小时急诊电话：027-12345678\n"
                + "● 就医请携带校园卡和身份证\n"
                + "● 医保报销请到校医院二楼医保办公室办理");

        FALLBACK_RULES.put(Pattern.compile(".*(宿舍|寝室|住宿|水电|报修).*"),
                "宿舍管理：\n"
                + "● 宿舍楼门禁：6:00-23:00\n"
                + "● 水电充值：各宿舍楼一楼自助充值机\n"
                + "● 报修：关注「智慧校园」公众号 → 后勤服务 → 在线报修\n"
                + "● 宿舍管理中心电话：027-12345679");

        FALLBACK_RULES.put(Pattern.compile(".*(你好|hello|hi|帮助|help).*"),
                "你好！我是校园智能服务小助手 🎓，可以帮你解答以下问题：\n"
                + "📚 图书馆 | 💳 校园卡 | 🍽️ 食堂 | 📅 课表\n"
                + "🏀 体育馆 | 🏥 校医院 | 🏠 宿舍 | 🗺️ 校园导航\n"
                + "请告诉我你需要什么帮助？");
    }

    public FallbackChatService(AlertLogMapper alertLogMapper) {
        this.alertLogMapper = alertLogMapper;
    }

    /**
     * 使用关键词匹配返回预设回答
     */
    public String getFallbackResponse(String userMessage) {
        log.warn("触发降级服务: userMessage={}", userMessage);
        saveAlert("FALLBACK_TRIGGERED", "降级服务被触发，用户消息: " + truncate(userMessage, 200));

        for (Map.Entry<Pattern, String> entry : FALLBACK_RULES.entrySet()) {
            if (entry.getKey().matcher(userMessage).matches()) {
                return entry.getValue();
            }
        }

        // 默认回复
        return "抱歉，AI 服务暂时不可用，我目前只能提供基础的校园信息查询。\n\n"
                + "你可以尝试询问以下内容：\n"
                + "📚 图书馆相关问题\n"
                + "💳 校园卡服务\n"
                + "🍽️ 食堂信息\n"
                + "📅 课表查询\n"
                + "🏀 体育馆设施\n"
                + "🏥 校医院信息\n"
                + "🏠 宿舍管理\n"
                + "\n或稍后重试，届时将恢复完整的 AI 对话能力。";
    }

    private void saveAlert(String alertType, String message) {
        try {
            AlertLog alert = new AlertLog();
            alert.setAlertType(alertType);
            alert.setMessage(message);
            alert.setCreateTime(LocalDateTime.now());
            alertLogMapper.insert(alert);
        } catch (Exception e) {
            log.error("保存告警日志失败", e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {return "";}
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
