package com.campus.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.entity.CourseSchedule;
import com.campus.entity.CampusLocation;
import com.campus.repository.CourseScheduleMapper;
import com.campus.repository.CampusLocationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 课程与教室查询工具
 * 提供课表查询和空教室查询功能，供 Agent 调用
 */
@Component
public class CourseTool {

    private static final Logger log = LoggerFactory.getLogger(CourseTool.class);

    /** 星期映射 */
    private static final Map<String, Integer> DAY_MAP = new HashMap<>();
    static {
        DAY_MAP.put("一", 1); DAY_MAP.put("二", 2); DAY_MAP.put("三", 3);
        DAY_MAP.put("四", 4); DAY_MAP.put("五", 5); DAY_MAP.put("六", 6);
        DAY_MAP.put("日", 7);
        DAY_MAP.put("1", 1); DAY_MAP.put("2", 2); DAY_MAP.put("3", 3);
        DAY_MAP.put("4", 4); DAY_MAP.put("5", 5); DAY_MAP.put("6", 6);
        DAY_MAP.put("7", 7);
    }

    private final CourseScheduleMapper courseScheduleMapper;
    private final CampusLocationMapper campusLocationMapper;
    private final ToolExecutionTracker toolTracker;

    public CourseTool(CourseScheduleMapper courseScheduleMapper,
                      CampusLocationMapper campusLocationMapper,
                      ToolExecutionTracker toolTracker) {
        this.courseScheduleMapper = courseScheduleMapper;
        this.campusLocationMapper = campusLocationMapper;
        this.toolTracker = toolTracker;
    }

    /**
     * 查询学生课表
     * 根据学号和可选的日期，返回该学生的课程列表
     *
     * @param studentId 学号（如 2024001）
     * @param date      日期（可选），格式：星期X 或 星期几，如"星期一"、"周三"、"3"
     * @return JSON 格式的课程列表字符串
     */
    @Tool(name = "queryCourse", description = "查询指定学生的课表。根据学号查询该学生选修的所有课程信息。可指定星期几来筛选某一天的课程。")
    public String queryCourse(
            @ToolParam(description = "学生学号，如 2024001") String studentId,
            @ToolParam(description = "查询日期（可选），如：星期一、周三、3（数字1-7）。不传则返回全部课表") String date) {

        log.info("Tool调用 [queryCourse]: studentId={}, date={}", studentId, date);
        long startTime = System.currentTimeMillis();
        String params = "{\"studentId\":\"" + escapeJson(studentId) + "\",\"date\":\"" + escapeJson(date) + "\"}";

        try {
            LambdaQueryWrapper<CourseSchedule> wrapper = new LambdaQueryWrapper<CourseSchedule>()
                    .eq(CourseSchedule::getStudentId, studentId);

            // 如果指定了日期，按星期几筛选
            if (date != null && !date.trim().isEmpty()) {
                Integer day = parseDayOfWeek(date.trim());
                if (day != null) {
                    wrapper.eq(CourseSchedule::getDayOfWeek, day);
                }
            }

            wrapper.orderByAsc(CourseSchedule::getDayOfWeek, CourseSchedule::getTimeSlot);

            List<CourseSchedule> courses = courseScheduleMapper.selectList(wrapper);

            String result;
            if (courses.isEmpty()) {
                result = formatResult(Collections.emptyList(), studentId, date);
            } else {
                result = formatResult(courses, studentId, date);
            }

            long duration = System.currentTimeMillis() - startTime;
            toolTracker.record("queryCourse", params, result.length() > 500 ? result.substring(0, 500) + "..." : result, duration, true);
            return result;
        } catch (Exception e) {
            log.error("queryCourse 执行异常", e);
            long duration = System.currentTimeMillis() - startTime;
            String errorResult = "{\"error\": \"查询课表失败: " + e.getMessage() + "\"}";
            toolTracker.record("queryCourse", params, errorResult, duration, false);
            return errorResult;
        }
    }

    /**
     * 查询空闲教室
     * 根据日期和节次，返回当前未被占用的教室列表
     *
     * @param date     查询日期，如"星期一"、"周三"、"3"
     * @param timeSlot 节次，如"1-2"、"3-4"、"5-6"、"7-8"
     * @return JSON 格式的空闲教室列表
     */
    @Tool(name = "queryClassroom", description = "查询指定时间段的空闲教室。根据星期几和节次，找出当前没有被课程占用的教室。")
    public String queryClassroom(
            @ToolParam(description = "查询日期，如：星期一、周三、3（数字1-7）") String date,
            @ToolParam(description = "节次，如：1-2、3-4、5-6、7-8") String timeSlot) {

        log.info("Tool调用 [queryClassroom]: date={}, timeSlot={}", date, timeSlot);
        long startTime = System.currentTimeMillis();
        String params = "{\"date\":\"" + escapeJson(date) + "\",\"timeSlot\":\"" + escapeJson(timeSlot) + "\"}";

        try {
            Integer day = parseDayOfWeek(date);
            if (day == null) {
                long duration = System.currentTimeMillis() - startTime;
                String errorResult = "{\"error\": \"无法识别的日期格式，请使用「星期一」或「周一」或数字1-7\"}";
                toolTracker.record("queryClassroom", params, errorResult, duration, false);
                return errorResult;
            }

            // 1. 查询该时段已被占用的教室
            List<CourseSchedule> occupied = courseScheduleMapper.selectList(
                    new LambdaQueryWrapper<CourseSchedule>()
                            .eq(CourseSchedule::getDayOfWeek, day)
                            .eq(CourseSchedule::getTimeSlot, timeSlot)
            );
            Set<String> occupiedRooms = occupied.stream()
                    .map(CourseSchedule::getClassroom)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 2. 查询所有教室类型的地点
            List<CampusLocation> allLocations = campusLocationMapper.selectList(
                    new LambdaQueryWrapper<CampusLocation>()
                            .in(CampusLocation::getCategory, "教学楼", "实验室")
            );
            Set<String> allRooms = allLocations.stream()
                    .map(CampusLocation::getName)
                    .collect(Collectors.toSet());

            // 3. 计算空闲教室
            List<String> freeRooms = new ArrayList<>();
            for (String room : allRooms) {
                // 检查该教室是否被占用（名称包含关系）
                boolean isOccupied = occupiedRooms.stream()
                        .anyMatch(occ -> occ.contains(room) || room.contains(occ));
                if (!isOccupied) {
                    freeRooms.add(room);
                }
            }

            String result;
            // 如果教室表为空，至少返回已占用的课程中的教室作为参考
            if (allRooms.isEmpty() && !occupiedRooms.isEmpty()) {
                List<String> occupiedList = new ArrayList<>(occupiedRooms);
                result = String.format(
                        "{\"date\": \"%s\", \"timeSlot\": \"%s\", \"dayOfWeek\": %d, "
                                + "\"occupiedClassrooms\": %s, \"freeClassrooms\": [], "
                                + "\"note\": \"教室数据库暂无数据，仅展示已占用教室\"}",
                        escapeJson(date), escapeJson(timeSlot), day,
                        toJsonArray(occupiedList));
            } else {
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"date\": \"").append(escapeJson(date)).append("\", ");
                json.append("\"timeSlot\": \"").append(escapeJson(timeSlot)).append("\", ");
                json.append("\"dayOfWeek\": ").append(day).append(", ");
                json.append("\"totalClassrooms\": ").append(allRooms.size()).append(", ");
                json.append("\"freeCount\": ").append(freeRooms.size()).append(", ");
                json.append("\"freeClassrooms\": ").append(toJsonArray(freeRooms)).append(", ");
                json.append("\"occupiedClassrooms\": ").append(toJsonArray(new ArrayList<>(occupiedRooms)));
                json.append("}");
                result = json.toString();
            }

            long duration = System.currentTimeMillis() - startTime;
            toolTracker.record("queryClassroom", params, result.length() > 500 ? result.substring(0, 500) + "..." : result, duration, true);
            return result;
        } catch (Exception e) {
            log.error("queryClassroom 执行异常", e);
            long duration = System.currentTimeMillis() - startTime;
            String errorResult = "{\"error\": \"查询空教室失败: " + e.getMessage() + "\"}";
            toolTracker.record("queryClassroom", params, errorResult, duration, false);
            return errorResult;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析星期几为数字 1-7
     */
    private Integer parseDayOfWeek(String date) {
        if (date == null || date.trim().isEmpty()) return null;

        String s = date.trim();
        // 直接数字
        Integer num = DAY_MAP.get(s);
        if (num != null) return num;

        // "星期一" → 去掉"星期"提取数字
        if (s.startsWith("星期") && s.length() == 3) {
            return DAY_MAP.get(s.substring(2));
        }

        // "周一" → 去掉"周"提取数字
        if (s.startsWith("周") && s.length() == 2) {
            return DAY_MAP.get(s.substring(1));
        }

        // "Monday" 等英文
        Map<String, Integer> enMap = Map.of(
                "monday", 1, "tuesday", 2, "wednesday", 3, "thursday", 4,
                "friday", 5, "saturday", 6, "sunday", 7
        );
        Integer en = enMap.get(s.toLowerCase());
        if (en != null) return en;

        return null;
    }

    private String formatResult(List<CourseSchedule> courses, String studentId, String date) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"studentId\": \"").append(escapeJson(studentId)).append("\", ");
        json.append("\"date\": \"").append(date != null ? escapeJson(date) : "全部").append("\", ");
        json.append("\"count\": ").append(courses.size()).append(", ");
        json.append("\"courses\": [");

        for (int i = 0; i < courses.size(); i++) {
            if (i > 0) json.append(", ");
            CourseSchedule c = courses.get(i);
            json.append("{");
            json.append("\"courseName\": \"").append(escapeJson(c.getCourseName())).append("\", ");
            json.append("\"teacher\": \"").append(escapeJson(c.getTeacher())).append("\", ");
            json.append("\"classroom\": \"").append(escapeJson(c.getClassroom())).append("\", ");
            json.append("\"dayOfWeek\": ").append(c.getDayOfWeek()).append(", ");
            json.append("\"timeSlot\": \"").append(escapeJson(c.getTimeSlot())).append("\", ");
            json.append("\"weekRange\": \"").append(escapeJson(c.getWeekRange())).append("\"");
            json.append("}");
        }

        json.append("]}");
        return json.toString();
    }

    private String toJsonArray(List<String> items) {
        if (items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escapeJson(items.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
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
