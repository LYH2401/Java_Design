package com.campus.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SolverServiceImplTest {

    // ======================== DIRECT 模式 ========================

    @ParameterizedTest
    @ValueSource(strings = {
            "检查代码",
            "帮我检查一下",
            "找bug",
            "帮我找bug",
            "这段代码有什么问题",
            "这个对不对",
            "这个算法是否正确",
            "有错吗",
            "帮我改错",
            "如何修正这个bug",
            "查错",
            "debug一下",
            "哪里错了",
            "有无问题",
            "有问题吗"
    })
    @DisplayName("直接评估关键词 -> DIRECT")
    void directKeywords(String msg) {
        assertEquals("DIRECT", SolverServiceImpl.detectIntent(msg),
                "消息: '" + msg + "' 应识别为 DIRECT");
    }

    @Test
    @DisplayName("原始案例：检查代码 -> DIRECT")
    void originalCase() {
        String msg = "检查代码\n" +
                "#include\n" +
                "void test01(){\n" +
                "int n, m, a;\n" +
                "std::cin >> n >> m >> a;\n" +
                "int cel = (n + a - 1) / a;\n" +
                "int row = (m + a - 1) / a;\n" +
                "std::cout << cel * row << std::endl;\n" +
                "}";
        assertEquals("DIRECT", SolverServiceImpl.detectIntent(msg));
    }

    @Test
    @DisplayName("直接评估长消息 -> DIRECT")
    void directLongMessage() {
        String msg = "题目：A. Theatre Square... 代码：... 帮我检查一下这段代码有什么问题";
        assertEquals("DIRECT", SolverServiceImpl.detectIntent(msg));
    }

    // ======================== GUIDED 模式 ========================

    @ParameterizedTest
    @ValueSource(strings = {
            "为什么这个算法是对的",
            "教我动态规划",
            "解释一下这个原理",
            "帮我理解这个概念",
            "怎么学微积分",
            "解题思路是什么",
            "这个原理是什么",
            "推导一下公式",
            "讲解一下这道题",
            "入门机器学习需要什么基础"
    })
    @DisplayName("引导教学关键词 -> GUIDED")
    void guidedKeywords(String msg) {
        assertEquals("GUIDED", SolverServiceImpl.detectIntent(msg),
                "消息: '" + msg + "' 应识别为 GUIDED");
    }

    // ======================== 优先级 ========================

    @Test
    @DisplayName("DIRECT 优先于 GUIDED：同时出现时选 DIRECT")
    void directPriorityOverGuided() {
        assertEquals("DIRECT", SolverServiceImpl.detectIntent("帮我检查并解释为什么不对"));
        assertEquals("DIRECT", SolverServiceImpl.detectIntent("检查这段代码，教我正确写法"));
        assertEquals("DIRECT", SolverServiceImpl.detectIntent("找bug，顺便解释一下原理"));
    }

    // ======================== 默认 ========================

    @ParameterizedTest
    @ValueSource(strings = {
            "这道题怎么做",
            "1+1等于几",
            "",
            "求极限",
            "写一个排序算法",
            "写一个Hello World",
            "Python的list和tuple有什么区别"
    })
    @DisplayName("无明确关键词 -> 默认 GUIDED")
    void defaultGuided(String msg) {
        assertEquals("GUIDED", SolverServiceImpl.detectIntent(msg),
                "消息: '" + msg + "' 应为默认 GUIDED");
    }

    // ======================== 边界情况 ========================

    @Test
    @DisplayName("null 输入 -> GUIDED")
    void nullInput() {
        assertEquals("GUIDED", SolverServiceImpl.detectIntent(null));
    }

    @Test
    @DisplayName("不相关的「教」字不会误触发 GUIDED（如教材、教室）")
    void teachCharWithoutContext() {
        assertEquals("GUIDED", SolverServiceImpl.detectIntent("推荐一本教材"));
        assertEquals("GUIDED", SolverServiceImpl.detectIntent("教室在哪里"));
    }
}
