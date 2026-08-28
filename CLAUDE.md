# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

码上评（msp / `com.mashangping`）：高校编程作业自动评测平台。学生在线读题、提交代码，系统用一次性 Docker 沙箱判题（C/C++、Java、Python），教师端管理课程/题库/作业/成绩。单机单体，目标规模 <200 学生。

- `server/` — Java 17 + Spring Boot 3.3.4 单体后端（Maven，无 wrapper，用系统 mvn）
- `frontend/` — React 18 + Vite + TS + AntD 5 前端（计划6教师端控制台已合入 main）；CI 门禁 `.github/workflows/frontend-ci.yml`（npm ci+build+test）
- `deploy/docker/judge/{cc,java,python}/` — 三个判题沙箱镜像的 Dockerfile
- `docs/superpowers/{specs,plans}/` — 每个「计划N」一对设计规格与实现计划，是事实上的架构文档与需求来源
- 文档、代码注释、commit message、错误文案全部中文；标识符/包名英文

## 常用命令

### 后端（在 `server/` 下执行，除非另注）

```bash
# 开发 MySQL（宿主机 3306 被占，映射到 3307）—— 在仓库根执行
docker compose -f docker-compose.dev.yml up -d

# 构建并启动（端口 8080）。必须 java -jar：中文路径下 mvn spring-boot:run 会 ClassNotFoundException
mvn package -DskipTests
java -jar target/msp-server-*.jar
# 启用判题调度器（默认关闭）：java -jar ... --msp.judge.enabled=true
# 启用判题前必须先构建三个镜像：
docker build -t msp-judge-cc deploy/docker/judge/cc      # 同理 msp-judge-java / msp-judge-python

# 测试（集成测试用 Testcontainers 自起 MySQL，不依赖 3307 开发库；需 Docker Desktop 运行中。
# Docker 不可用时用例被跳过而非失败——"绿"可能含跳过，注意甄别）
mvn test                                  # 全量回归
mvn test -Dtest=OutputComparatorTest      # 单个测试类
mvn test -Dtest=OutputComparatorTest#方法名
mvn test -Dtest=SandboxJudgeIT            # 真沙箱 IT（surefire 默认不跑 *IT，必须显式执行；内联构建镜像，需 Docker）
```

### 前端（在 `frontend/` 下执行）

```bash
npm run dev      # Vite 开发服务器，/api 与 /ws 代理到 localhost:8080
npm run build    # tsc -b && vite build
npm test         # vitest run
```

## 架构

后端分层：`XxxController → XxxService → XxxMapper`（MyBatis-Plus `BaseMapper`，无 XML）。实体 `@TableName` + 独立只读 View record；请求 DTO 带 Bean Validation。Flyway 迁移在 `server/src/main/resources/db/migration/`（V1–V6），**已提交的迁移不可修改**，改表结构一律新增 `Vn__`。

领域包（`com.mashangping` 下）：`auth`（登录签发 JWT）、`register`/`emailverify`/`mail`（邮箱验证码注册）、`user`（ADMIN/TEACHER/STUDENT 三角色；`DataInitializer` 空库自动播种 `admin/admin123`）、`course`（课程+Enrollment 花名册+Excel 导入）、`problem`（题库：Problem/CourseProblem/TestCase/Languages）、`assignment`（作业：Assignment/AssignmentProblem/AssignmentStatus 状态机）、`judging`、`ws`。

### 判题管线（核心，全进程内，无外部判题服务器）

1. `judging/SubmissionService`「九道门」校验（作业 `assignmentProblemId` / 练习 `problemId` 双锚分流、语言白名单、限流 40018、代码 ≤64KB 40019），同事务插入 `Submission(PENDING)` + `JudgeTask(PENDING)` 后唤醒调度器（另有 1s `@Scheduled` 兜底轮询）。
2. `judging/JudgeScheduler`：MySQL 表作队列，原子 UPDATE 抢占 PENDING→RUNNING，Semaphore 并发 3，基础设施故障重试 ≤2 后永久失败（submission SYSTEM_ERROR）；终态写入（judge_detail 组 + submission 终态 + task DONE）为单事务。聚合严重度 AC<WA<MLE<TLE<RE；作业路径全 AC 得 `AssignmentProblem.score` 否则 0，练习分数恒 NULL。启动时复位遗留 RUNNING。
3. `judging/DockerJudgeExecutor`：每次提交一个一次性容器（uid 2000 非 root、network none、只读 rootfs、exec-tmpfs `/work` `/tmp`、内存=题目限制+语言开销、GNU `timeout` 包裹：exit 124→TLE、信号→MLE/RE）。`JudgeLanguage` 持编译/运行命令矩阵；`OutputComparator` 判输出。
4. `ws/`：STOMP 端点 `/ws/judge` —— HTTP 握手**故意 permitAll**（浏览器握手带不了 Authorization），鉴权只在 STOMP CONNECT 帧的 Bearer JWT（`WsAuthChannelInterceptor`）；进度推 `/user/{uid}/queue/judge-progress`（best-effort）。

`msp.judge.*` 配置项默认不出现在 yml，默认值全在 `judging/JudgeProperties`；`msp.judge.enabled` 未设置 ⇒ 判题组件不装配（测试上下文也因此无后台线程）。

### API 约定（前后端共同遵守）

- 响应统一 `{code, message, data}`：成功 code=0；**业务错误返回 HTTP 200 + code≠0**（`BizException` + `ErrorCode`，400xx 域内/参数、40100/40300/40400 认证类、50000 系统）。前端 axios 拦截器按 code≠0 拒绝。只有 Spring Security 拒绝走真实 HTTP 401/403（`GlobalExceptionHandler` 重抛 `AccessDeniedException` 是刻意的，勿"修"）。
- 存在性隐藏：访问他人私有题目/未选课程/未发布作业一律 40400，绝不 40300。
- **隐藏测试点零泄漏红线**：学生可见视图不得泄露隐藏用例的任何信息（输入/输出/数量）。
- 认证：无状态 JWT（HS256，24h）；`JwtAuthFilter` 解析 token 后**从库实时加载角色/enabled**（30s 微缓存，支持实时吊销）；`@PreAuthorize(hasRole(...))` 方法级授权。
- `user` 是保留字：实体必须 `@TableName("`user`")`。LIKE 查询走 `LikeUtils` 转义 `_`/`%`。

### 前端约定

- axios 单例 `src/api/client.ts`：baseURL `/api`，自动附 Bearer；响应拦截器执行 code≠0 拒绝、401 清 token 跳 `/login`。
- 服务端状态用 TanStack Query（staleTime 30s）；路由 react-router v6 data router + `lazy()`；认证态 React Context（token 存 localStorage `msp_token`）。
- 测试 Vitest + Testing Library（jsdom、globals），用例与组件同目录 `*.test.tsx`；`src/test/setup.ts` 桩了 `matchMedia`（jsdom 下 antd 依赖）。路径别名 `@/*`。
- 无 ESLint/Prettier，规范靠 `tsc` strict（含 noUnusedLocals/noUnusedParameters）。

## 测试

- 集成测试基类 `IntegrationTestBase`：单例 Testcontainers `mysql:8.0`，`bearer(...)` 造真 JWT；`*SchemaTest` 钉死实体↔Flyway DDL 一致性。
- 后端命名全部 `*Test` 走 surefire；唯一例外 `judging/SandboxJudgeIT`（真 Docker 判题全链路）。

## 开发流程（SDD）

- 每个「计划N」先写 spec 再写 plan，落在 `docs/superpowers/{specs,plans}/`（文件名 `YYYY-MM-DD-*`）。plan 含全局约束（错误码规则、零泄漏红线、Flyway 纪律等）与分任务 TDD 步骤（写失败测试→确认 FAIL→实现→确认 PASS→全量回归→commit），用 checkbox 跟踪。
- 执行 plan 用 superpowers 技能（subagent-driven-development 或 executing-plans）；一计划一分支一 worktree（如 `feature/plan4-assignment`、`worktree-plan6-teacher-frontend`），完成后 PR 合入 main。
- commit 规范：conventional commits，type/scope 英文、描述中文，如 `feat(judging): ...`、`fix(course): ...`、`docs(plan): ...`；每个任务步骤一提交。
- `.superpowers/sdd/` 是 gitignore 的执行草稿区，执行状态不入库。
- 换行符：仓库内一律 LF（`.gitattributes` 强制），仅 `.bat`/`.cmd` 用 CRLF。

## 本机环境坑（Windows + 中文路径，勿"修复"）

- 不要把 `java -jar` 启动改回 `mvn spring-boot:run`（中文目录名导致派生 JVM ClassNotFoundException）；测试不受影响。
- 开发库端口固定 **3307**；`docker-compose.dev.yml` 的 `name: msp-dev` 必须保留（中文目录无法自动派生 compose 项目名）。
- pom 中 `<testcontainers.version>1.21.4</testcontainers.version>` 覆盖是给 Docker Engine 29.x（要求 API ≥1.44）打的补丁，勿删勿降级。
- Java **17** 而非 21：不要用 JDK 21+ API（如 `List.getFirst()`）。
- `msp.school.email-suffixes` 必须保持逗号分隔标量，不能改成 YAML 列表（绑定崩溃）。
- 在 `.claude/worktrees/*` 会话内操作主仓：先 ExitWorktree 退出会话——会话内 `git -C 主仓根` 会被沙箱拒绝，`cd` 出去也会被重置回来。
