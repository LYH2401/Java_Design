package com.campus.tool;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.entity.CampusLocation;
import com.campus.repository.CampusLocationMapper;

/**
 * 校园导航工具
 * 提供地点查询和路径导航功能，供 Agent 调用
 */
@Component
public class NavigationTool {

    private static final Logger log = LoggerFactory.getLogger(NavigationTool.class);

    private final CampusLocationMapper campusLocationMapper;
    private final ToolExecutionTracker toolTracker;

    public NavigationTool(CampusLocationMapper campusLocationMapper,
                          ToolExecutionTracker toolTracker) {
        this.campusLocationMapper = campusLocationMapper;
        this.toolTracker = toolTracker;
    }

    /**
     * 查询校园地点信息
     * 根据地点名称或分类进行模糊查询
     *
     * @param name     地点名称或关键词（如"图书馆"、"食堂"），可选
     * @param category 分类筛选（如"教学楼"、"食堂"、"宿舍"、"体育"），可选
     * @return JSON 格式的地点信息列表
     */
    public String queryLocation(String name, String category) {

        log.info("Tool调用 [queryLocation]: name={}, category={}", name, category);
        long startTime = System.currentTimeMillis();
        String params = "{\"name\":\"" + escapeJson(name) + "\",\"category\":\"" + escapeJson(category) + "\"}";

        try {
            LambdaQueryWrapper<CampusLocation> wrapper = new LambdaQueryWrapper<>();

            if (name != null && !name.trim().isEmpty()) {
                wrapper.like(CampusLocation::getName, name.trim());
            }
            if (category != null && !category.trim().isEmpty()) {
                wrapper.eq(CampusLocation::getCategory, category.trim());
            }

            List<CampusLocation> locations = campusLocationMapper.selectList(wrapper);

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"query\": \"").append(escapeJson(name)).append("\", ");
            json.append("\"category\": \"").append(category != null ? escapeJson(category) : "").append("\", ");
            json.append("\"count\": ").append(locations.size()).append(", ");
            json.append("\"locations\": [");

            for (int i = 0; i < locations.size(); i++) {
                if (i > 0) json.append(", ");
                CampusLocation loc = locations.get(i);
                json.append("{");
                json.append("\"name\": \"").append(escapeJson(loc.getName())).append("\", ");
                json.append("\"category\": \"").append(escapeJson(loc.getCategory())).append("\", ");
                json.append("\"description\": \"").append(escapeJson(loc.getDescription())).append("\", ");
                json.append("\"coordinateX\": ").append(loc.getCoordinateX()).append(", ");
                json.append("\"coordinateY\": ").append(loc.getCoordinateY());
                json.append("}");
            }

            json.append("]}");
            String result = json.toString();

            long duration = System.currentTimeMillis() - startTime;
            toolTracker.record("queryLocation", params,
                    result.length() > 500 ? result.substring(0, 500) + "..." : result,
                    duration, true);
            return result;
        } catch (Exception e) {
            log.error("queryLocation 执行异常", e);
            long duration = System.currentTimeMillis() - startTime;
            String errorResult = "{\"error\": \"查询校园地点失败: " + e.getMessage() + "\"}";
            toolTracker.record("queryLocation", params, errorResult, duration, false);
            return errorResult;
        }
    }

    /**
     * 校园路径导航
     * 计算从起点到终点的最优路径，给出导航指引
     *
     * @param from 起点名称
     * @param to   终点名称
     * @return JSON 格式的导航信息（距离、方向、途经点）
     */
    public String navigate(String from, String to) {

        log.info("Tool调用 [navigate]: from={}, to={}", from, to);
        long startTime = System.currentTimeMillis();
        String params = "{\"from\":\"" + escapeJson(from) + "\",\"to\":\"" + escapeJson(to) + "\"}";

        try {
            // 1. 查找起点
            CampusLocation fromLoc = findLocation(from);
            if (fromLoc == null) {
                long duration = System.currentTimeMillis() - startTime;
                String errorResult = "{\"error\": \"未找到起点「" + escapeJson(from) + "」，请检查地点名称是否正确\"}";
                toolTracker.record("navigate", params, errorResult, duration, false);
                return errorResult;
            }

            // 2. 查找终点
            CampusLocation toLoc = findLocation(to);
            if (toLoc == null) {
                long duration = System.currentTimeMillis() - startTime;
                String errorResult = "{\"error\": \"未找到终点「" + escapeJson(to) + "」，请检查地点名称是否正确\"}";
                toolTracker.record("navigate", params, errorResult, duration, false);
                return errorResult;
            }

            // 3. 计算导航信息
            double deltaX = toLoc.getCoordinateX() - fromLoc.getCoordinateX();
            double deltaY = toLoc.getCoordinateY() - fromLoc.getCoordinateY();
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

            // 方向描述
            String direction = buildDirection(deltaX, deltaY);

            // 途经建议
            String via = suggestVia(fromLoc, toLoc);

            // 估算时间（步行 80m/min，骑行 250m/min）
            int walkMin = (int) Math.ceil(distance / 80.0);
            int bikeMin = (int) Math.ceil(distance / 250.0);

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"from\": \"").append(escapeJson(fromLoc.getName())).append("\", ");
            json.append("\"to\": \"").append(escapeJson(toLoc.getName())).append("\", ");
            json.append("\"distanceMeters\": ").append(String.format("%.0f", distance)).append(", ");
            json.append("\"direction\": \"").append(escapeJson(direction)).append("\", ");
            json.append("\"walkingTimeMinutes\": ").append(walkMin).append(", ");
            json.append("\"bikingTimeMinutes\": ").append(bikeMin).append(", ");
            json.append("\"via\": \"").append(escapeJson(via)).append("\", ");
            json.append("\"fromCoord\": { \"x\": ").append(fromLoc.getCoordinateX())
                    .append(", \"y\": ").append(fromLoc.getCoordinateY()).append(" }, ");
            json.append("\"toCoord\": { \"x\": ").append(toLoc.getCoordinateX())
                    .append(", \"y\": ").append(toLoc.getCoordinateY()).append(" }");
            json.append("}");

            String result = json.toString();
            long duration = System.currentTimeMillis() - startTime;
            toolTracker.record("navigate", params,
                    result.length() > 500 ? result.substring(0, 500) + "..." : result,
                    duration, true);
            return result;
        } catch (Exception e) {
            log.error("navigate 执行异常", e);
            long duration = System.currentTimeMillis() - startTime;
            String errorResult = "{\"error\": \"导航计算失败: " + e.getMessage() + "\"}";
            toolTracker.record("navigate", params, errorResult, duration, false);
            return errorResult;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据名称模糊查找地点
     */
    private CampusLocation findLocation(String name) {
        if (name == null || name.trim().isEmpty()) return null;

        String keyword = name.trim();

        // 精确匹配
        List<CampusLocation> exact = campusLocationMapper.selectList(
                new LambdaQueryWrapper<CampusLocation>()
                        .eq(CampusLocation::getName, keyword)
        );
        if (!exact.isEmpty()) return exact.get(0);

        // 模糊匹配
        List<CampusLocation> fuzzy = campusLocationMapper.selectList(
                new LambdaQueryWrapper<CampusLocation>()
                        .like(CampusLocation::getName, keyword)
        );
        if (!fuzzy.isEmpty()) return fuzzy.get(0);

        return null;
    }

    /**
     * 根据坐标差构建方向描述
     */
    private String buildDirection(double deltaX, double deltaY) {
        StringBuilder dir = new StringBuilder();

        if (Math.abs(deltaY) > Math.abs(deltaX)) {
            // 以南北为主
            if (deltaY > 0) dir.append("向北");
            else dir.append("向南");
            if (Math.abs(deltaX) > 5) {
                dir.append(deltaX > 0 ? "偏东" : "偏西");
            }
        } else {
            // 以东西为主
            if (deltaX > 0) dir.append("向东");
            else dir.append("向西");
            if (Math.abs(deltaY) > 5) {
                dir.append(deltaY > 0 ? "偏北" : "偏南");
            }
        }

        return dir.toString();
    }

    /**
     * 建议途经地标
     */
    private String suggestVia(CampusLocation from, CampusLocation to) {
        // 如果起点和终点都在校园内，给一些通用建议
        String fromName = from.getName();
        String toName = to.getName();

        if (fromName.contains("图书馆") || toName.contains("图书馆")) {
            return "图书馆是校园中心地标，以此为参照沿主干道行走";
        }
        if (fromName.contains("食堂") || toName.contains("食堂")) {
            return "食堂区域标识明显，可询问附近同学确认方向";
        }
        if (fromName.contains("教学楼") || toName.contains("教学楼")) {
            return "教学楼之间由连廊连接，可从任意教学楼内部穿行";
        }

        // 根据位置提供通用建议
        double midX = (from.getCoordinateX() + to.getCoordinateX()) / 2;
        double midY = (from.getCoordinateY() + to.getCoordinateY()) / 2;

        // 寻找附近的 landmark
        List<CampusLocation> all = campusLocationMapper.selectList(null);
        CampusLocation nearest = null;
        double minDist = Double.MAX_VALUE;
        for (CampusLocation loc : all) {
            if (loc.getId().equals(from.getId()) || loc.getId().equals(to.getId())) continue;
            double d = Math.sqrt(
                    Math.pow(loc.getCoordinateX() - midX, 2) +
                            Math.pow(loc.getCoordinateY() - midY, 2));
            if (d < minDist) {
                minDist = d;
                nearest = loc;
            }
        }

        if (nearest != null && minDist < 80) {
            return "途经" + nearest.getName() + "附近，可在此确认方向";
        }

        return "沿校园主干道行走，参照路牌指示";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
