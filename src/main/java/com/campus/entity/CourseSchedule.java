package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 课表实体类
 */
@TableName("course_schedule")
public class CourseSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private String studentId;

    @TableField("course_name")
    private String courseName;

    @TableField("teacher")
    private String teacher;

    @TableField("classroom")
    private String classroom;

    @TableField("day_of_week")
    private Integer dayOfWeek;

    @TableField("time_slot")
    private String timeSlot;

    @TableField("week_range")
    private String weekRange;

    // ==================== 构造函数 ====================

    public CourseSchedule() {}

    public CourseSchedule(Long id, String studentId, String courseName, String teacher,
                          String classroom, Integer dayOfWeek, String timeSlot, String weekRange) {
        this.id = id;
        this.studentId = studentId;
        this.courseName = courseName;
        this.teacher = teacher;
        this.classroom = classroom;
        this.dayOfWeek = dayOfWeek;
        this.timeSlot = timeSlot;
        this.weekRange = weekRange;
    }

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }

    public String getClassroom() { return classroom; }
    public void setClassroom(String classroom) { this.classroom = classroom; }

    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }

    public String getWeekRange() { return weekRange; }
    public void setWeekRange(String weekRange) { this.weekRange = weekRange; }
}
