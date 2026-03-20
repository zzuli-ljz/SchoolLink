# 家校通管理系统 (SchoolLink)

## 📖 项目简介
本项目是一个基于 **Spring Boot** 的全栈家校沟通与管理平台，旨在连接教师、学生与家长，提供高效的班级管理、教学互动与信息同步服务。系统采用轻量级架构，部署便捷，功能覆盖教、学、管、评全流程。

## 🛠 技术栈
### 后端 (Backend)
- **核心框架**: Spring Boot 3.2.5
- **Web 框架**: Spring MVC (RESTful API)
- **持久层**: Spring Data JPA (Hibernate)
- **数据库**: H2 Database (嵌入式文件型数据库，无需安装 MySQL)
- **安全认证**: Spring Security + JWT (JSON Web Token)
- **工具库**: Jackson, Lombok, Validation

### 前端 (Frontend)
- **页面技术**: 原生 HTML5 + CSS3 (Flexbox/Grid)
- **脚本语言**: Vanilla JavaScript (ES6+)
- **网络请求**: Fetch API (封装统一拦截器)
- **设计模式**: 模块化 CSS，事件委托，自定义 Modal 组件

## ✨ 核心功能模块

### 👨‍🏫 教师端 (Teacher)
1.  **班级管理**：创建班级、生成邀请码、管理学生名册。
2.  **作业管理**：
    *   发布作业（支持富文本/附件描述）。
    *   作业批改：在线打分、打回重做、设置延期。
    *   两栏式批改视图（已提交/未提交）。
3.  **考勤管理**：按日历视图录入/修改考勤，生成报表。
4.  **请假审批**：
    *   全局弹窗审批机制，支持查看详情、批准/拒绝。
    *   状态实时同步至家长端。
5.  **成绩管理**：支持多学科成绩录入与自动合并（唯一性约束）。
6.  **消息通知**：发送班级公告或个人私信。

### 👨‍🎓 学生端 (Student)
1.  **作业中心**：查看待办/已过期作业，在线提交作业内容。
2.  **个人中心**：查看个人考勤记录、请假申请状态。
3.  **成绩查询**：查看各次考试的各科成绩。
4.  **消息接收**：实时接收老师发布的通知和作业提醒。

### 👨‍👩‍👧 家长端 (Parent)
1.  **账号关联**：通过邀请码绑定子女账号。
2.  **学情监控**：实时查看孩子的作业完成率、考勤异常及成绩单。
3.  **请假申请**：代孩子发起请假申请，并接收审批结果通知。

## 🚀 快速开始

### 1. 环境要求
- **JDK**: 17 或更高版本
- **Maven**: 3.6+
- **浏览器**: Chrome / Edge / Firefox (现代浏览器)

### 2. 启动服务
本项目内置 H2 数据库，无需额外部署数据库服务。

**方式一：使用 IDE 运行**
1.  使用 IntelliJ IDEA 或 Eclipse 打开 `server` 目录。
2.  等待 Maven 依赖下载完成。
3.  运行 `com.schoollink.Application` 主类。

**方式二：命令行运行**
```bash
cd server
mvn spring-boot:run
```

### 3. 访问系统
服务启动后，默认端口为 **8081**。
打开浏览器访问：[http://localhost:8081/](http://localhost:8081/)

**测试账号**（如果使用初始化数据）：
- 注册新账号即可开始测试，系统开放注册。

### 4. 数据库控制台 (可选)
如果需要查看底层数据：
- **地址**: [http://localhost:8081/h2-console](http://localhost:8081/h2-console)
- **JDBC URL**: `jdbc:h2:file:./data/schoollink-db-v2`
- **用户名**: `sa`
- **密码**: (留空)

## 📂 项目结构
```
Code/
├── server/
│   ├── src/main/
│   │   ├── java/com/schoollink/
│   │   │   ├── auth/          # 认证模块 (Controller, Service, JWT)
│   │   │   ├── assignment/    # 作业模块
│   │   │   ├── attendance/    # 考勤模块
│   │   │   ├── grade/         # 成绩模块
│   │   │   ├── leave/         # 请假模块
│   │   │   └── ...
│   │   └── resources/
│   │       ├── static/        # 前端静态资源
│   │       │   ├── home/      # 各角色主页 (teacher.html, etc.)
│   │       │   ├── js/        # 公共脚本 (dashboard.js, auth.js)
│   │       │   └── assets/    # 样式表
│   │       └── application.yml # 配置文件
│   └── pom.xml                # Maven 依赖配置
└── README.md                  # 项目说明文档
```

## 📊 代码统计
- **总代码行数**: ~18,700 行
- **文件分布**: HTML (62%), Java (33%), JS (4%)
