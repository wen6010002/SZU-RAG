# SZU-RAG MVP — Phase 1 执行总结报告

## 阶段：Step 1 - 项目骨架与框架层

**执行日期**: 2026-04-08
**状态**: 已完成

---

## 一、完成清单

### 1. Maven 项目创建
- [x] Spring Boot 3.5.7 项目骨架
- [x] 全部 Maven 依赖配置（MyBatis Plus、Milvus SDK、Tika、Sa-Token、Hutool、FastJSON2、MapStruct、ST4、Lombok）
- [x] Lombok 1.18.36 + MapStruct 注解处理器配置（兼容 Java 23 编译环境）

### 2. Docker Compose 基础设施
- [x] MySQL 8.0 (端口 3306) - healthy
- [x] Redis 7-alpine (端口 6379) - healthy
- [x] Milvus v2.4.17 (端口 19530/9091) - healthy（需 amd64 模拟）
- [x] etcd v3.5.5 + MinIO 作为 Milvus 依赖

### 3. Framework 层模块
- [x] **异常体系**: BaseException → ClientException / ServiceException / RemoteException + GlobalExceptionHandler
- [x] **统一响应**: Result\<T\> + PageResult\<T\> 分页封装
- [x] **Snowflake ID**: SnowflakeIdWorker（可配置 workerId）
- [x] **用户上下文**: UserContext (ThreadLocal) + LoginUser DTO
- [x] **SSE 发送器**: SseEmitterManager + SseEmitterSender（支持 content/thinking/sources/complete/error 事件）

### 4. 配置文件
- [x] application.yml（数据源、Redis、Milvus、AI 模型、RAG、Sa-Token）
- [x] schema.sql（6 张表 + 初始管理员）
- [x] Dockerfile
- [x] .gitignore

### 5. 验证结果
- [x] `mvn clean compile` 编译通过
- [x] Spring Boot 应用启动成功（1.2 秒）
- [x] `/health` 接口返回 `{"code":"200","message":"success","data":"SZU-RAG Backend is running"}`
- [x] MySQL 数据库 `szu_rag` 创建成功，6 张表全部存在
- [x] 初始管理员 admin/admin123 已插入

---

## 二、项目目录结构

```
szu-rag-backend/
├── pom.xml
├── Dockerfile
├── .gitignore
├── docker-compose.dev.yml
├── src/main/java/com/szu/rag/
│   ├── SzRagApplication.java
│   ├── HealthController.java
│   └── framework/
│       ├── exception/
│       │   ├── BaseException.java
│       │   ├── ClientException.java
│       │   ├── ServiceException.java
│       │   ├── RemoteException.java
│       │   └── GlobalExceptionHandler.java
│       ├── result/
│       │   ├── Result.java
│       │   └── PageResult.java
│       ├── context/
│       │   └── UserContext.java
│       ├── id/
│       │   └── SnowflakeIdWorker.java
│       └── sse/
│           ├── SseEmitterManager.java
│           └── SseEmitterSender.java
├── src/main/resources/
│   ├── application.yml
│   └── db/
│       └── schema.sql
```

---

## 三、遇到的问题及解决方案

| 问题 | 解决方案 |
|------|---------|
| Lombok 注解在 Java 23 下不生效 | 升级 Lombok 到 1.18.36 + 配置 maven-compiler-plugin annotationProcessorPaths |
| Milvus ARM64 镜像不兼容 | 使用 `platform: linux/amd64` + 显式 entrypoint |
| etcd 仅监听 127.0.0.1 | 配置 `ETCD_LISTEN_CLIENT_URLS: http://0.0.0.0:2379` |
| Docker 占用 8080 端口 | 应用端口改为 8088 |

---

## 四、下一步（Phase 2）

打开 `02_AI基础设施层.md`，实现：
- ChatClient 策略接口 + DeepSeek 实现
- EmbeddingClient 策略接口 + 阿里百炼实现
- OpenAI 风格 SSE 流式解析器
- Token 计数服务

---

## 五、启动命令

```bash
# 启动基础设施
cd szu-rag-backend
docker compose -f docker-compose.dev.yml up -d

# 编译
mvn clean compile

# 运行
mvn spring-boot:run

# 验证
curl http://localhost:8088/health
```
