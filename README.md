# SchoolLink · 家校通管理系统

<div align="center">

**教师、学生、家长、学校管理员在同一个系统里，把作业、考勤、成绩、请假、通知走完**

![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge\&logo=openjdk)

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6db33f?style=for-the-badge\&logo=spring-boot)

![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-Hibernate-59666c?style=for-the-badge\&logo=hibernate)

![JWT](https://img.shields.io/badge/JWT-jjwt%200.11.5-d63aff?style=for-the-badge\&logo=jsonwebtokens)



![H2](https://img.shields.io/badge/H2%20%7C%20MySQL%20%7C%20PostgreSQL-多库支持-4479a1?style=for-the-badge\&logo=postgresql)

![Vanilla JS](https://img.shields.io/badge/Frontend-Vanilla%20JS-f7df1e?style=for-the-badge\&logo=javascript)

</div>

---

## 📖 项目简介

班级群 + Excel 是现在家校沟通的真实形态：老师把作业发群里、家长在群里翻记录、考勤靠班长点名、成绩用 Excel 挨个私发、请假要在群里 @ 老师等人回。

SchoolLink 把这套流程做成了一个系统。五种角色，各自的首页、权限、能做的事都不一样：

| 角色                   | 进来之后能做什么                           |
| -------------------- | ---------------------------------- |
| 教师 `TEACHER`         | 建班级、发作业、批改、录考勤、审批请假、录成绩、发通知        |
| 学生 `STUDENT`         | 看待办作业、在线提交、查成绩、查考勤、申请请假            |
| 家长 `PARENT`          | 绑定孩子、看学情（作业完成率 / 考勤异常 / 成绩单）、代孩子请假 |
| 学校管理员 `SCHOOL_ADMIN` | 管教师账号、管班级、把任课教师挂到班上、看全校统计          |
| 系统管理员 `SYSTEM_ADMIN` | 开关系统配置、管全部用户、重置密码、批量导入用户           |

登录不看菜单，看角色——登录成功后后端直接返回该角色的 `homepagePath()`，五种角色五套界面，互不串门。

---

## ✨ 功能特性

**作业闭环**

- 发布作业（标题 / 内容 / 截止时间），前端按班级聚合待办；
- 学生在线提交文本内容，教师端两栏视图区分**已交 / 未交**；
- 批改支持打分与"打回重做"（`/submissions/{id}/return`），统计接口直接给出提交率。

**考勤**

- 按班级 + 日期批量打卡（`/api/attendance/batch`），一次请求提交全班状态，避免逐人请求；
- 学生 / 家长端可查个人考勤明细，教师端出班级统计。

**成绩**

- 教师下载 CSV 模板 → 填写 → 上传 → 预览确认 → 入库，一条流水线；
- 成绩分析按考试聚合（班级均分、分布），教师端与管理员端各有一套导出。

**请假**

- 家长代孩子发起 / 学生自己发起，教师端统一待办列表，通过或驳回都写通知回执。

**通知与报名**

- 班级通知面向全班广播，也支持"需要报名"的通知（如家长会），学生 / 家长在线报名，教师查看报名名单。

**班级与账号体系**

- 建班生成 6 位邀请码，学生 / 家长凭码入学；支持班主任转让、班级解散；
- 自助注册**只开放给家长和学生，且必须携带邀请码**；教师账号由学校管理员创建——避免了"谁都能注册成老师"的问题；
- 任课教师与班级是多对多（`ClassTeacher`），一个老师可带多个班，一个班可挂多个科目老师。

---

## 🔍 几个实现细节

### 成绩 CSV 导入：先猜编码，再解析

教务处导出的 CSV 在中文 Windows 上默认是 **GBK**，直接按 UTF-8 读会全是乱码。所以上传接口先做编码探测再解析：

```java
// GradeController#detectEncoding
private Charset detectEncoding(byte[] bytes) {
    // 先按 UTF-8 严格解码，抛异常说明不是 UTF-8
    // 再退化到 GBK —— 覆盖 Excel / WPS 另存为 CSV 的默认行为
    ...
    return Charset.forName("GBK");
}
```

配套的还有模板下载（`/api/grades/template`）和"上传 → 预览 → 确认入库"两段式提交：上传只解析不落库，确认后才写，避免一份格式错误的表污染成绩。

### 无状态鉴权：JWT 手写实现

项目没有引入 Spring Security，鉴权是一条自己实现的轻量链路：

- `JwtService` 用 jjwt 0.11.5 签发 HS256 token，`role` 放在 claim 里，有效期 120 分钟；
- 前端登录后把 token 存 `localStorage`，所有请求经 `apiFetch` 统一注入 `Authorization: Bearer`；
- 每个角色页面进入时先调 `/api/auth/verify`，`requireRole()` 校验角色不符直接踢回登录页；
- 后端接口从 `@RequestHeader("Authorization")` 解析 claims 再做角色判断。

好处是没有引入整套 Security 过滤链，五角色权限差异用显式判断表达得更直白；代价是权限校验散落在各 Controller 里，这点在下面的"已知取舍"里说。

### 多数据库切换

`H2 / MySQL / PostgreSQL` 三个驱动都在依赖里，默认跑 H2 文件库（免安装），切换只需换 profile：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql       # 或 postgres
```

`application-mysql.yml` / `application-postgres.yml` 各自维护方言与连接串，JPA 层不用改一行代码。

---

## 🛠 技术栈

| 层次  | 选型                             | 说明                                                         |
| --- | ------------------------------ | ---------------------------------------------------------- |
| 框架  | Spring Boot 3.2.5 / Java 17    | 单体应用，分层为 Controller → Repository（业务逻辑较薄，直接写在 Controller 里） |
| 持久层 | Spring Data JPA + Hibernate    | `ddl-auto: update`，实体驱动建表                                  |
| 数据库 | H2（默认）/ MySQL / PostgreSQL     | H2 开控制台便于调试，另两套走 profile                                   |
| 认证  | jjwt 0.11.5（HS256）             | 自实现 JWT 签发与解析，无 Spring Security                            |
| 校验  | spring-boot-starter-validation | 注册 / 表单参数校验                                                |
| 前端  | 原生 HTML + CSS + ES6            | 无构建步骤，`apiFetch` 统一封装鉴权与错误                                 |
| 部署  | Dockerfile（多阶段）                | 先 `dependency:go-offline` 预热依赖层，再打包                        |

规模：70 个 Java 文件约 7.2k 行，10 个页面约 6.3k 行 HTML，公共 JS/CSS 约 500 行。

---

## 🚀 快速开始

环境：JDK 17+、Maven 3.6+。

```bash
cd server
mvn spring-boot:run
```

启动后访问 \*\*<http://localhost:8081/\*\*，H2> 控制台在 `/h2-console`（JDBC URL：`jdbc:h2:file:./data/schoollink-db-v2`，用户 `sa`，密码空）。

内置账号（`SeedConfig` 首次启动自动创建）：

| 角色    | 用户名        | 密码          |
| ----- | ---------- | ----------- |
| 系统管理员 | `sysadmin` | `admin123`  |
| 学校管理员 | `school`   | `school123` |

教师 / 学生 / 家长：教师由学校管理员在后台创建，学生与家长用学校管理员或班主任生成的 6 位邀请码自助注册。

Docker 部署：

```bash
cd server
docker build -t schoollink .
docker run -p 8081:8081 schoollink
```

---

## 📂 项目结构

```
server/
├── Dockerfile
├── pom.xml
└── src/main/
    ├── java/com/schoollink/
    │   ├── auth/         # 登录注册、JWT 签发与解析
    │   ├── assignment/   # 作业与提交（发布 / 提交 / 批改 / 打回 / 统计）
    │   ├── attendance/   # 考勤（批量打卡、班级统计）
    │   ├── grade/        # 考试、成绩录入与 CSV 导入导出
    │   ├── leave/        # 请假申请与审批
    │   ├── notice/       # 通知与报名
    │   ├── message/      # 私信
    │   ├── notification/ # 站内通知
    │   ├── invite/       # 邀请码
    │   ├── school/       # 班级、学生、任课教师、全校统计
    │   ├── system/       # 系统管理员（配置、用户、导入）
    │   ├── user/         # 用户与角色
    │   └── config/       # CORS、全局异常、种子数据
    └── resources/
        ├── application.yml / -mysql.yml / -postgres.yml
        └── static/       # 首页、注册页、五个角色 dashboard、样式与脚本
```

---

## 🌍 English

### Overview

**SchoolLink** is a home-school communication and class management system built with Spring Boot 3.2.5 and Java 17. Five roles — teacher, student, parent, school admin, system admin — each get their own dashboard and permission scope, covering assignments, attendance, grades, leave requests and notices.

### Highlights

- **Assignment loop** — publish → submit → grade or send back for rework, with submitted/missing views and completion stats.
- **Grade import pipeline** — CSV template download, upload with **encoding auto-detection (UTF-8 → GBK)** because Chinese Excel exports are GBK by default, then a two-phase preview-then-confirm write so a malformed sheet never pollutes the grade table.
- **Lightweight stateless auth** — hand-rolled JWT (jjwt 0.11.5, HS256) with the role in the claims, a front-end `apiFetch` wrapper injecting `Bearer` tokens, and `requireRole()` guards per dashboard. No Spring Security filter chain: role checks are explicit but spread across controllers — a deliberate trade-off, see below.
- **Multi-database** — H2 file DB by default for zero-setup demos; MySQL and PostgreSQL via Spring profiles without touching the JPA layer.
- **Invite-code onboarding** — 6-digit class invite codes; self-registration is limited to students and parents and requires a code, so nobody can sign up as a teacher.

### Getting started

```bash
cd server && mvn spring-boot:run     # JDK 17+, Maven 3.6+
```

Open <http://localhost:8081/> — default accounts: `sysadmin` / `admin123` (system admin), `school` / `school123` (school admin).

### Known trade-offs

- Authorization is parsed per-endpoint from the `Authorization` header rather than centralised in a `HandlerInterceptor` or Security filter — simple and readable at 70 classes, but a cross-cutting concern that would need extraction before scaling.
- CORS is fully open (`allowedOriginPatterns("*")` with credentials) for LAN/demo convenience; must be narrowed for production.
- Teacher and school-admin dashboards are single HTML files with tab switching rather than client-side routing — no build step traded for larger files.
- JWT secret and datasource credentials currently live in `application*.yml`; move them to environment variables before any real deployment.

---

## 📝 备注

- 数据库文件在 `server/data/` 下，删掉即可重置；种子数据只补 `sysadmin` 与 `school` 两个账号，其余从界面创建；
- 想让某个教师成为班主任：校管理员在班级页把班级转让给该教师即可，邀请码随班级走；
- 生产的正确姿势：把 `security.jwt.secret` 和数据库账号改走环境变量，再把 CORS 白名单收紧到实际域名。
