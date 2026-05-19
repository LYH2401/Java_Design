package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("role")
    private String role; // USER / ASSISTANT / SYSTEM

    @TableField("content")
    private String content;

    @TableField("metadata")
    private String metadata; // JSON 字符串

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
