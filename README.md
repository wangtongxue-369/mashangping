# 码上评（msp）

高校编程作业自动评测平台：学生在线读题、编写并提交代码，平台以一次性 Docker 沙箱评测（C / C++ / Java / Python）；教师管理课程、题库、作业与成绩，并提供成绩册、代码查重与图形化课程工作台。

单体单机架构，目标规模 <200 学生的教学场景。

## 功能总览

**学生端**

- 我的课程 → 课程作业列表（未开始/进行中/宽限中/已截止 + 截止倒计时）→ 作业题目列表 → 牛客式双栏编码页（左题面右 Monaco 深色编辑器）
- 作业状态贯穿作答：已截止不可提交并说明；宽限期提交前二次确认「将标记为迟交」；迟交历史标记
- 实时判题进度（WebSocket best-effort）+ 逐测试点详情（样例点完整输入输出与 WA「你的输出」；隐藏点仅状态/用时/内存，**零泄漏红线**）
- 得分语义：作业「最高分/满分」0 红/部分橙/满分绿；自由练习不计分，按「已通过 N 次」给正反馈
- 自由练习公开题库；按语言分存编辑草稿、模板/空代码提交确认
- 学生自助注册：邮箱验证码（仅学校域名白名单）+ 学号注册，注册即登录并自动激活教师预置名单

**教师端**

- 课程管理（增删改 + Excel 学生名单导入/模板下载/单加/移除；PENDING=待注册学生注册后自动激活）
- 题库管理（题目/语言/时空限制/公开/样例与隐藏测试点）
- 作业管理（发布开关/选题/改分/题序上移下移/删除；学生名单、成绩册、提交历史、查重入口）
- 成绩册矩阵（未提交 `—`、学号姓名列冻结、「只看未提交」筛选、CSV 导出带作业名、UTF-8 BOM）
- 提交历史（题目/学生筛选、迟交标记、详情含隐藏点完整诊断——教师属主视图）
- 代码查重（同作业同题两两相似度，Tree-sitter 语法归一 C/C++/Java/Python，最高分取样，阈值显式应用）
- 课程工作台（概览卡 + 成绩趋势/分数段/AC 率/状态分布/提交时间线五类图 + 查重风险直达）
- 全局三层面包屑（全部课程 → 课程工作台 → 当前页），深层链接也有课程/作业名上下文

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.3.4 · MyBatis-Plus（无 XML）· Spring Security + JWT · STOMP WebSocket · Flyway |
| 判题 | Docker 一次性容器（uid 2000、network none、只读 rootfs、GNU timeout、cgroup 限内存）；tree-sitter 语法树解析（查重） |
| 前端 | React 18 · Vite · TypeScript(strict) · Ant Design 5 · TanStack Query · react-router v6 · Monaco Editor · @ant-design/plots · Vitest |
| 数据 | MySQL 8.0（Testcontainers 用于集成测试） |

## 仓库结构

```text
server/                        后端单体（com.mashangping：auth/user/course/problem/assignment/
                                judging/gradebook/plagiarism/analytics/register/ws/common/security…）
frontend/                      前端（src/pages/{student,teacher}、src/api、src/layout、src/auth…）
deploy/docker/judge/{cc,java,python}/  判题沙箱 Dockerfile
docs/superpowers/{specs,plans}/       每「计划N」的设计规格与实现计划（事实上的架构文档）
docker-compose.dev.yml                开发 MySQL（compose 项目名 msp-dev，端口 3307）
CLAUDE.md                             仓库协作约定（环境坑/提交规范/红线，AI 与人均可读）
```

## 快速开始

前置：JDK 17、Node 18+、Docker Desktop。

```bash
# 1. 开发数据库（宿主 3306 若被占已映射到 3307）
docker compose -f docker-compose.dev.yml up -d

# 2. 判题沙箱镜像（启用判题前构建一次）
docker build -t msp-judge-cc   deploy/docker/judge/cc     # 同理 msp-judge-java / msp-judge-python

# 3. 后端（端口 8080）。必须 java -jar：中文路径下 mvn spring-boot:run 会 ClassNotFoundException
cd server && mvn package -DskipTests
java -jar target/msp-server-*.jar                    # 默认不启判题（提交排队不评测）
java -jar target/msp-server-*.jar --msp.judge.enabled=true   # 启用判题调度器

# 4. 前端（Vite 开发服务器，/api 与 /ws 代理到 8080）
cd frontend && npm install && npm run dev            # http://localhost:5173
```

- 注册/找回验证码邮件在开发模式不真发信（`msp.mail.enabled=false`），验证码直接打印到后端控制台/日志（见下「测试账号」）。
- 学校邮箱域名白名单：`server/src/main/resources/application.yml` → `msp.school.email-suffixes`（当前 `stu.example.edu.cn`，**必须保持逗号分隔标量**，不能写成 YAML 列表）。

## 测试账号

开发库空库启动时会自动播种管理员与演示数据（`DataInitializer`）；下述学生账号均已预置并**加入「数据结构（2026秋）」课程**（除注明外密码均为 `student123`）。

| 角色 | 用户名 | 密码 | 姓名/说明 |
|---|---|---|---|
| 管理员 | `admin` | `admin123` | 系统管理员（管理员端建设中，登录会提示改用教师/学生账号） |
| 教师 | `teacher01` | `teacher123` | 张老师（数据结构课程属主） |
| 学生 | `20260001` | `student123` | 张三（演示学生） |
| 学生 | `20260002` | `student123` | 李四（查重演示对成员） |
| 学生 | `20260003` | `student123` | 王五（查重演示对成员） |
| 学生 | `20260004` | `student123` | 陈晨（预置，可提交评测，已有 A+B AC 记录） |
| 学生 | `20260005` | `student123` | 刘洋 |
| 学生 | `20260006` | `student123` | 赵敏 |
| 学生 | `20260007` | `student123` | 孙悦 |
| 学生 | `20260008` | `student123` | 周航 |
| — | `20260009` | — | 钱小朵：**待激活（PENDING）**，未注册；用邮箱 `20260009@stu.example.edu.cn` 走注册流程后自动激活 |
| — | `20260010` | — | 郑一鸣：**待激活（PENDING）**，同上用 `20260010@stu.example.edu.cn` |

> 演示「注册即自动激活」：学生名单页可见两个「待激活」行 → 在登录页「注册学生账号」填对应学号/邮箱 → 验证码从后端日志取（搜索「验证码」即可，15 分钟内有效）→ 注册成功自动登录并加入课程。

**演示数据**：课程「数据结构（id=1，2026秋）」→ 作业「第一次作业（已发布，进行中）」→ 题目「A+B 问题」（满分 100，样例与隐藏点齐全）。当前课程内有 8 名在册学生；张三/李四/王五/陈晨已有 AC 提交，其中李四、王五、陈晨提交了同一份代码 → 教师端查重风险显示 3 对「高疑似」，可用于演示查重与工作台图表；另有 2 名「待激活」学生（见上表）。判题已启用，可直接用任一学生账号提交代码体验全链路。

## 测试

```bash
# 后端全量回归（集成测试用 Testcontainers 自动起 MySQL，需 Docker；无 Docker 时相关用例跳过）
cd server && mvn test

# 前端
cd frontend && npx tsc -b && npm test && npm run build
```

## 文档与设计

每个功能迭代（计划 N）都先在 `docs/superpowers/{specs,plans}/` 落地规格与实现计划，再按 TDD 推进并逐任务提交；规格是需求与架构的最终事实来源（判题九道门、作业状态机、查重/聚合口径、隐藏测试点零泄漏红线等均见 spec）。代码审查报告见 `docs/2026-09-06-前端-UIUX-审查报告.md`（含计划10 整改后记）。

## 本机环境坑（勿「修复」，是有意为之）

- **中文路径沙箱**：Maven 无全局 mvn 时用仓库内 `.tools/maven`；npm 需把缓存指到仓库内（`frontend/.npmrc`）；tree-sitter 原生库默认写 `~/.tree-sitter` 会被拒，运行/测试加 `-Dtree-sitter-lib=<仓库根>/.tools/tree-sitter-lib`（查重/评测读取用到）。
- 不要把 `java -jar` 启动改回 `mvn spring-boot:run`（中文目录名导致派生 JVM ClassNotFoundException）。
- 开发库端口固定 **3307**；`docker-compose.dev.yml` 的 `name: msp-dev` 必须保留。
- pom 中 `<testcontainers.version>1.21.4</testcontainers.version>` 覆盖是给 Docker Engine 29.x 的补丁，勿删勿降级。
- Java 17 而非 21：不要用 JDK 21+ API（如 `List.getFirst()`）。
- 仓库一律 LF 换行（`.gitattributes` 强制）。

## 说明

`student123` 等为本地开发/演示口令，仅用于教学环境；生产部署请更换默认播种口令与邮件/SMTP 配置。
