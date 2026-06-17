package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 校园地点实体类
 */
@TableName("campus_location")
public class CampusLocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("category")
    private String category;

    @TableField("description")
    private String description;

    @TableField("coordinate_x")
    private Double coordinateX;

    @TableField("coordinate_y")
    private Double coordinateY;

    // ==================== 构造函数 ====================

    public CampusLocation() {}

    public CampusLocation(Long id, String name, String category, String description,
                          Double coordinateX, Double coordinateY) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.coordinateX = coordinateX;
        this.coordinateY = coordinateY;
    }

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getCoordinateX() { return coordinateX; }
    public void setCoordinateX(Double coordinateX) { this.coordinateX = coordinateX; }

    public Double getCoordinateY() { return coordinateY; }
    public void setCoordinateY(Double coordinateY) { this.coordinateY = coordinateY; }
}
