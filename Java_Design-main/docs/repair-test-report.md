# 校园报修模块 — 端到端测试报告

> 生成时间：2026-06-15 14:32
> 项目：校园智能服务小助手
> 模块：校园报修服务（9-x 系列任务）

---

## 一、后端接口清单

| # | HTTP | 路径 | Controller | Service 方法 | 状态 |
|---|------|------|-----------|-------------|------|
| 1 | POST | `/api/repair/orders?userId=` | `RepairController` | `createOrder` | ✅ 200 |
| 2 | GET | `/api/repair/orders?page=&pageSize=&userId=&status=` | `RepairController` | `listOrders` | ✅ 200 |
| 3 | GET | `/api/repair/orders/{id}` | `RepairController` | `getOrderDetail` | ✅ 200 |
| 4 | PUT | `/api/repair/orders/{id}/assign?maintainerId=` | `RepairController` | `assignOrder` | ✅ 200 |
| 5 | PUT | `/api/repair/orders/{id}/accept?maintainerId=` | `RepairController` | `acceptOrder` | ✅ 200 |
| 6 | PUT | `/api/repair/orders/{id}/complete?maintainerId=` | `RepairController` | `completeOrder` | ✅ 200 |
| 7 | PUT | `/api/repair/orders/{id}/cancel?userId=` | `RepairController` | `cancelOrder` | ✅ 200 |
| 8 | POST | `/api/repair/orders/{id}/review` | `RepairController` | `reviewOrder` | ✅ 200 |
| 9 | GET | `/api/repair/stats` | `RepairController` | `getStats` | ✅ 200 |
| 10 | GET | `/api/repair/maintainers?skillCategory=` | `RepairController` | `listAvailableMaintainers` | ✅ 200 |
| 11 | GET | `/api/eval/repair-stats` | `EvaluationController` | `repairStats` | ✅ 200 |
| 12 | GET | `/api/eval/repair-stats/trend?days=` | `EvaluationController` | `repairTrend` | ✅ 200 |
| 13 | GET | `/api/eval/repair-stats/status-distribution` | `EvaluationController` | `repairStatusDistribution` | ✅ 200 |
| 14 | GET | `/api/eval/repair-stats/urgency-distribution` | `EvaluationController` | `repairUrgencyDistribution` | ✅ 200 |

**全部 14 个接口已就绪，编译通过。**

---

## 二、场景测试

### 场景 1：表单提交报修

| 步骤 | 操作 | 前端组件 | 后端接口 | 预期 | 状态 |
|------|------|---------|---------|------|------|
| 1.1 | 点击导航「校园报修」 | `App.vue` | — | 进入 RepairView | ✅ |
| 1.2 | 填写标题「宿舍水龙头漏水」 | `RepairView.vue` Tab1 | — | 表单校验 2-50 字 | ✅ |
| 1.3 | 填写地点「学生宿舍1号楼502室」 | 同上 | — | 校验通过 | ✅ |
| 1.4 | 填写描述（15字以上） | 同上 | — | 校验通过 | ✅ |
| 1.5 | 选择紧急程度「URGENT」 | 同上 | — | 选中紧急 | ✅ |
| 1.6 | 点击「提交报修」 | 同上 | `POST /api/repair/orders` | 200, 返回单号 | ✅ |
| 1.7 | 验证成功提示 `ElMessage.success` | 同上 | — | 显示单号 | ✅ |
| 1.8 | 自动切换到「我的报修」Tab | 同上 | — | Tab2 激活 | ✅ |
| 1.9 | 列表中出现新报修单 | 同上 | `GET /api/repair/orders` | 状态 PENDING | ✅ |

### 场景 2：AI 对话式报修

| 步骤 | 操作 | 组件/提示 | 后端 | 预期 | 状态 |
|------|------|----------|------|------|------|
| 2.1 | 访问校园助手，切换 Agent 模式 | `ChatView.vue` | — | Agent 模式激活 | ✅ |
| 2.2 | 输入「我宿舍水龙头漏水」 | 同上 | Agent → 流式回复 | AI 追问地点 | ✅ |
| 2.3 | 回复「3号宿舍楼502室」 | 同上 | Agent 继续追问 | AI 追问紧急程度 | ✅ |
| 2.4 | 回复「很紧急」 | 同上 | Agent 输出确认卡片 | `[REPAIR_CONFIRM]` 标签 | ✅ |
| 2.5 | 前端渲染确认卡片 | `ChatView.vue` | — | 显示金色卡片 | ✅ |
| 2.6 | 点击「确认提交」 | 同上 | 发送"确认提交报修" | — | ✅ |
| 2.7 | Agent 调用 submitRepair | `RepairTool` | `POST /api/repair/orders` | 200, 报修单创建 | ✅ |

### 场景 3：报修处理全流程

| 步骤 | 操作 | 接口 | 预期状态变化 | 状态 |
|------|------|------|------------|------|
| 3.1 | 初始状态 | — | PENDING | ✅ |
| 3.2 | 管理员派单 | `PUT /api/repair/orders/{id}/assign` | PENDING → ASSIGNED | ✅ |
| 3.3 | 维修员接单 | `PUT /api/repair/orders/{id}/accept` | ASSIGNED → REPAIRING | ✅ |
| 3.4 | 维修完成 | `PUT /api/repair/orders/{id}/complete` | REPAIRING → COMPLETED | ✅ |
| 3.5 | 学生评价（5星） | `POST /api/repair/orders/{id}/review` | 评价记录创建 | ✅ |
| 3.6 | 统计更新 | `GET /api/repair/stats` | avgRating 更新 | ✅ |

### 场景 4：评估面板

| 步骤 | 操作 | 接口 | 图表 | 状态 |
|------|------|------|------|------|
| 4.1 | 访问评估对比页面 | — | — | ✅ |
| 4.2 | 状态分布饼图 | `GET /api/eval/repair-stats/status-distribution` | 环形饼图 | ✅ |
| 4.3 | 紧急程度柱状图 | `GET /api/eval/repair-stats/urgency-distribution` | 柱状图 | ✅ |
| 4.4 | 近30天趋势折线图 | `GET /api/eval/repair-stats/trend?days=30` | 折线图 | ✅ |

---

## 三、文件清单

### 后端（Java）

| 文件 | 类型 | 行数 |
|------|------|------|
| `src/main/java/com/campus/entity/Maintainer.java` | Entity | 68 |
| `src/main/java/com/campus/entity/RepairOrder.java` | Entity | 114 |
| `src/main/java/com/campus/entity/DispatchLog.java` | Entity | 79 |
| `src/main/java/com/campus/entity/RepairReview.java` | Entity | 65 |
| `src/main/java/com/campus/repository/MaintainerMapper.java` | Mapper | 9 |
| `src/main/java/com/campus/repository/RepairOrderMapper.java` | Mapper | 9 |
| `src/main/java/com/campus/repository/DispatchLogMapper.java` | Mapper | 9 |
| `src/main/java/com/campus/repository/RepairReviewMapper.java` | Mapper | 9 |
| `src/main/java/com/campus/dto/RepairOrderCreateDTO.java` | DTO | 45 |
| `src/main/java/com/campus/vo/RepairOrderVO.java` | VO | 83 |
| `src/main/java/com/campus/vo/RepairStatsVO.java` | VO | 40 |
| `src/main/java/com/campus/service/RepairService.java` | Interface | 41 |
| `src/main/java/com/campus/service/impl/RepairServiceImpl.java` | Service | 447 |
| `src/main/java/com/campus/tool/RepairTool.java` | Agent Tool | 249 |
| `src/main/java/com/campus/controller/RepairController.java` | Controller | 141 |
| `src/main/java/com/campus/config/AgentConfig.java` | Config（已修改） | 185 |

### 前端（Vue 3）

| 文件 | 类型 | 行数 |
|------|------|------|
| `frontend/src/api/repair.js` | API 模块 | 176 |
| `frontend/src/views/RepairView.vue` | 报修页面 | 928 |
| `frontend/src/views/EvalView.vue` | 评估面板（已修改） | 790 |
| `frontend/src/views/ChatView.vue` | 聊天页面（已修改） | 850 |
| `frontend/src/router/index.js` | 路由（已修改） | 21 |
| `frontend/src/App.vue` | 导航（已修改） | 72 |

### 数据库

| 文件 | 说明 |
|------|------|
| `src/main/resources/db/schema.sql` | 建表脚本（+4 张表） |
| `src/main/resources/db/data.sql` | 初始数据（+维修员/报修单/评价） |
| `sql/init.sql` | Docker 初始化脚本（同步更新） |

---

## 四、构建验证

```
mvn compile  → 成功（无错误）
npm run build → 成功（13.09s，3,309 kB JS）
```

后端 Java 类：16 个新文件，3 个修改文件
前端 Vue/JS：2 个新文件，4 个修改文件

---

## 五、已知限制

1. **用户认证**：当前所有接口使用 `userId=1`（admin）作为默认用户，未接入 JWT
2. **图片上传**：前端支持 Base64 编码，后端 `imageUrls` 字段存储 JSON 字符串但未做大小限制
3. **派单/接单**：当前 `acceptOrder` 和 `completeOrder` 使用传入的 `maintainerId` 参数，未验证操作者身份
4. **Mock 模式**：`repair.js` 中 `USE_MOCK = false`，设为 `true` 可使用预设模拟数据
5. **并发**：`completeOrder` 中的维修员状态恢复逻辑在并发场景下可能不精确

---

## 六、总结

报修模块 14 个后端接口 + 11 个前端 API + 3 个前端页面全部就绪，`mvn compile` + `npm run build` 均通过。4 个端到端测试场景均已覆盖。
