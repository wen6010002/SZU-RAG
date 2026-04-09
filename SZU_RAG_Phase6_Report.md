# SZU-RAG MVP — Phase 6-7 执行总结报告

**状态**: 已完成

## Phase 6: 前端对话界面

### 完成清单
- [x] React 18 + Vite 6 + TypeScript 项目搭建
- [x] SSE 流式消息接收 (fetch + ReadableStream)
- [x] Zustand 状态管理 (对话列表、消息、流式状态)
- [x] 聊天组件 (ChatWindow + MessageBubble + ChatInput)
- [x] Markdown 渲染 (react-markdown + remark-gfm)
- [x] 来源引用展示
- [x] Vite API 代理配置
- [x] TailwindCSS 样式
- [x] `npm run build` 编译通过

### 文件结构
```
szu-rag-frontend/
├── Dockerfile, nginx.conf, vite.config.ts
├── src/
│   ├── App.tsx
│   ├── main.tsx, index.css
│   ├── api/chat.ts
│   ├── store/chatStore.ts
│   └── components/
│       ├── ChatWindow.tsx
│       ├── MessageBubble.tsx
│       └── ChatInput.tsx
```

### 修复记录
- Vite 8 需 Node 20.19+，降级到 Vite 6.4.2 适配 Node 20.15
- TS 严格模式修复: type-only imports, unused variables, union type narrowing
- Message interface 增加 `isStreaming` 和 `sources?: Source[] | string` 类型

## Phase 7: 部署与集成

### 完成清单
- [x] Frontend Dockerfile (multi-stage: node build + nginx serve)
- [x] Nginx 配置 (SPA fallback + API 代理 + SSE 无缓冲)
- [x] Backend application-prod.yml (环境变量注入)
- [x] 全栈 docker-compose.yml (9 个服务)
- [x] .env.example 环境变量模板
- [x] 项目启动文档

### 全栈服务编排
| 服务 | 端口 | 说明 |
|------|------|------|
| mysql | 3306 | MySQL 8.0 数据库 |
| redis | 6379 | Redis 7 缓存 |
| milvus | 19530/9091 | Milvus v2.4.17 向量数据库 |
| milvus-etcd | - | etcd v3.5.5 (Milvus 元数据) |
| milvus-minio | - | MinIO (Milvus 对象存储) |
| backend | 8088 | Java 后端 API |
| frontend | 80 | React 前端 + Nginx |
| crawler | 8090 | Python 爬虫 |

### 已知限制 (MVP)
- Sa-Token 认证已配置但未实现登录接口 (架构中标注为简化版，无 RBAC)
- RocketMQ 未集成 (MVP 使用同步推送)
- 无用户注册/登录功能
