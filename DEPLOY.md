# SZU-RAG 部署指南（OpenCloudOS 从零部署）

> 本文档适用于一台全新的 OpenCloudOS（兼容 CentOS/RHEL）Linux 服务器，从零开始部署 SZU-RAG 全栈项目。

## 项目架构概览

| 服务 | 技术栈 | 端口 |
|------|--------|------|
| 前端 | React + Vite + Nginx | 80 |
| 后端 | Spring Boot 3.5.7 (Java 17) | 8088 |
| 爬虫 | FastAPI + Scrapy (Python 3.11) | 8090 |
| MySQL | 8.0 | 3306 |
| Redis | 7 | 6379 |
| Milvus | 2.4.17 (向量数据库) | 19530 |
| etcd | 3.5.5 (Milvus 依赖) | 2379 |
| MinIO | (Milvus 依赖) | 9000 |

---

## 第一步：系统初始化

### 1.1 更新系统 & 安装基础工具

```bash
# 更新系统
sudo yum update -y

# 安装常用工具
sudo yum install -y git curl wget vim tar yum-utils
```

### 1.2 配置防火墙（开放端口）

```bash
# 开放所需端口
sudo firewall-cmd --permanent --add-port=80/tcp      # 前端
sudo firewall-cmd --permanent --add-port=8088/tcp    # 后端 API
sudo firewall-cmd --permanent --add-port=8090/tcp    # 爬虫服务

# 重载防火墙
sudo firewall-cmd --reload

# 验证
sudo firewall-cmd --list-ports
```

> 如果使用云服务器（腾讯云/阿里云等），还需要在安全组中放行以上端口。

---

## 第二步：安装 Docker & Docker Compose

### 2.1 安装 Docker

```bash
# 添加 Docker 阿里云镜像仓库（国内服务器速度快）
sudo yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo

# 安装 Docker
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动 Docker 并设置开机自启
sudo systemctl start docker
sudo systemctl enable docker

# 验证
docker --version
docker compose version
```

### 2.2 配置 Docker 镜像加速（国内服务器必须）

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me",
    "https://docker.m.daocloud.io",
    "https://ccr.ccs.tencentyun.com"
  ]
}
EOF

sudo systemctl daemon-reload
sudo systemctl restart docker
```

---

## 第三步：安装 JDK 17 和 Maven（后端构建需要）

```bash
# 安装 JDK 17
sudo yum install -y java-17-openjdk java-17-openjdk-devel

# 验证
java -version
# 应输出 openjdk version "17.x.x"

# 安装 Maven（用 yum 安装，避免从 Apache 官方下载慢/中断）
sudo yum install -y maven

# 验证
mvn -version
```

### 配置 Maven 国内镜像加速

```bash
sudo mkdir -p /root/.m2
sudo tee /root/.m2/settings.xml <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF

# 如果当前用户不是 root，也给自己配置一份
mkdir -p ~/.m2
cp /root/.m2/settings.xml ~/.m2/settings.xml 2>/dev/null || true
```

---

## 第四步：上传项目代码到服务器

### 方式一：从本地 scp 上传（推荐）

在你的 **Mac 本地终端** 执行：

```bash
scp -r /Users/wenhaoxuan/Desktop/SZU-RAG root@<你的服务器IP>:/opt/SZU-RAG
```

### 方式二：从 Git 仓库拉取

如果项目在 Git 远程仓库中：

```bash
cd /opt
git clone <你的仓库地址> SZU-RAG
```

---

## 第五步：构建后端 JAR 包

> Dockerfile 要求后端先构建好 JAR 包。

```bash
cd /opt/SZU-RAG/szu-rag-backend

# Maven 打包（跳过测试）
mvn clean package -DskipTests

# 验证 JAR 包是否生成
ls -lh target/szu-rag-backend-1.0.0-SNAPSHOT.jar
```

> 首次构建会下载大量依赖，耗时约 3-10 分钟（取决于网络）。如果下载缓慢，确认 Maven 镜像配置正确。

---

## 第六步：配置环境变量

```bash
cd /opt/SZU-RAG

# 创建 .env 文件
cat > .env <<'EOF'
# MySQL 密码
MYSQL_ROOT_PASSWORD=szu_rag_2024

# 智谱 AI API Key（GLM-4-Plus）
ZHIPU_API_KEY=29b0ca5a0e04462cbff4fa84221e72fd.ZID61wTu5lN5YqL3

# 阿里百炼 API Key（text-embedding-v3）
BAILIAN_API_KEY=sk-98cdd33ac81348cf8d302b6094c60dbb
EOF

# 确认文件内容
cat .env
```

---

## 第七步：一键启动所有服务

```bash
cd /opt/SZU-RAG

# 构建并启动所有容器（后台运行）
docker compose up -d --build
```

> 首次启动会拉取所有镜像并构建，耗时约 10-20 分钟。Milvus 启动较慢，需要等待健康检查通过。

### 查看启动状态

```bash
# 查看所有容器状态
docker compose ps

# 期望输出（所有服务 healthy 或 running）：
# szu-mysql       running (healthy)
# szu-redis       running (healthy)
# szu-milvus-etcd running (healthy)
# szu-milvus-minio running (healthy)
# szu-milvus      running (healthy)
# szu-backend     running
# szu-frontend    running
# szu-crawler     running
```

### 实时查看日志

```bash
# 查看所有服务日志
docker compose logs -f

# 只看某个服务日志
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f crawler
```

---

## 第八步：验证部署

### 8.1 检查各服务

```bash
# 检查前端（返回 HTML 页面）
curl -s http://localhost | head -5

# 检查后端健康
curl -s http://localhost:8088/api/health || echo "后端可能还在启动中..."

# 检查爬虫服务
curl -s http://localhost:8090/docs || echo "爬虫服务可能还在启动中..."
```

### 8.2 浏览器访问

在浏览器中打开：

```
http://<你的服务器IP>
```

默认管理员账号：
- 用户名：`admin`
- 密码：`admin123`

---

## 常用运维命令

### 服务管理

```bash
cd /opt/SZU-RAG

# 停止所有服务
docker compose down

# 重启某个服务
docker compose restart backend
docker compose restart crawler

# 重新构建并启动（代码更新后）
docker compose up -d --build

# 只重建某个服务
docker compose up -d --build backend
```

### 查看日志

```bash
# 实时追踪某服务日志
docker compose logs -f --tail=100 backend

# 查看最近 50 行日志
docker compose logs --tail=50 backend
```

### 数据管理

```bash
# 查看 Docker 卷（持久化数据）
docker volume ls | grep szu

# 备份 MySQL 数据
docker exec szu-mysql mysqldump -uroot -pszu_rag_2024 szu_rag > backup_$(date +%Y%m%d).sql

# 恢复 MySQL 数据
docker exec -i szu-mysql mysql -uroot -pszu_rag_2024 szu_rag < backup_20260408.sql
```

### 清理

```bash
# 停止并删除所有容器（数据卷保留）
docker compose down

# 停止并删除所有容器和数据卷（⚠️ 会丢失数据）
docker compose down -v
```

---

## 服务器配置要求

| 项目 | 最低配置 | 推荐配置 |
|------|----------|----------|
| CPU | 2 核 | 4 核+ |
| 内存 | 4 GB | 8 GB+ |
| 磁盘 | 40 GB | 80 GB+ |
| 系统 | OpenCloudOS / CentOS 8+ | OpenCloudOS 9 |

> Milvus 向量数据库比较吃内存，建议至少 4GB 内存。如果内存不足，Milvus 可能启动失败。

---

## 常见问题排查

### 1. Milvus 启动失败 / 健康检查不通过

```bash
# 查看 Milvus 日志
docker compose logs milvus

# 常见原因：内存不足
free -h

# 解决方案：增加 swap
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
```

### 2. 后端连接数据库失败

```bash
# 确认 MySQL 已启动
docker compose ps mysql

# 手动测试 MySQL 连接
docker exec -it szu-mysql mysql -uroot -pszu_rag_2024 -e "SHOW DATABASES;"
```

### 3. 前端页面白屏 / API 502

```bash
# 检查后端是否运行
docker compose ps backend

# 检查 Nginx 配置
docker exec szu-frontend cat /etc/nginx/conf.d/default.conf

# 确认后端日志无报错
docker compose logs --tail=50 backend
```

### 4. Maven 构建失败（依赖下载超时）

```bash
# 确认 Maven 镜像配置
cat /root/.m2/settings.xml

# 清理并重试
cd /opt/SZU-RAG/szu-rag-backend
mvn clean package -DskipTests -U
```

### 5. Docker 镜像拉取失败

```bash
# 确认镜像加速配置
cat /etc/docker/daemon.json

# 重启 Docker 使配置生效
sudo systemctl restart docker

# 手动拉取测试
docker pull mysql:8.0
docker pull redis:7-alpine

# 如果所有镜像源都拉不动，尝试逐个测试并保留可用的
# 测试某个镜像源是否可用：
curl -I https://docker.1ms.run/v2/
```

### 6. yum install 下载慢

```bash
# OpenCloudOS 默认用的腾讯源，如果慢可以换成阿里云
sudo sed -i 's/mirrors.cloud.tencent.com/mirrors.aliyun.com/g' /etc/yum.repos.d/*.repo
sudo yum makecache
```

---

## 一键部署脚本（懒人版）

如果你想一键搞定，把以下内容保存为 `deploy.sh` 并执行：

```bash
#!/bin/bash
set -e

echo "===== SZU-RAG 一键部署 ====="

# 1. 更新系统
echo "[1/7] 更新系统..."
yum update -y

# 2. 安装 Docker
echo "[2/7] 安装 Docker..."
yum install -y yum-utils
yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl start docker
systemctl enable docker

# 3. 配置 Docker 镜像加速
echo "[3/7] 配置 Docker 镜像加速..."
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'DAEMON'
{"registry-mirrors": ["https://docker.1ms.run", "https://docker.xuanyuan.me"]}
DAEMON
systemctl daemon-reload
systemctl restart docker

# 4. 安装 JDK 17
echo "[4/7] 安装 JDK 17..."
yum install -y java-17-openjdk java-17-openjdk-devel

# 5. 安装 Maven
echo "[5/7] 安装 Maven..."
yum install -y maven

# 配置 Maven 阿里云镜像
mkdir -p /root/.m2
cp /root/.m2/settings.xml /root/.m2/settings.xml.bak 2>/dev/null || true
cat > /root/.m2/settings.xml <<'MAVEN'
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
MAVEN

# 6. 构建后端
echo "[6/7] 构建后端 JAR..."
cd /opt/SZU-RAG/szu-rag-backend
mvn clean package -DskipTests -q

# 7. 启动所有服务
echo "[7/7] 启动 Docker Compose..."
cd /opt/SZU-RAG
docker compose up -d --build

echo ""
echo "===== 部署完成 ====="
echo "前端: http://<服务器IP>"
echo "后端: http://<服务器IP>:8088"
echo "爬虫: http://<服务器IP>:8090"
echo "管理员: admin / admin123"
echo ""
echo "查看状态: cd /opt/SZU-RAG && docker compose ps"
echo "查看日志: cd /opt/SZU-RAG && docker compose logs -f"
```

使用方式：

```bash
# 先上传项目到 /opt/SZU-RAG，然后执行：
chmod +x deploy.sh
sudo bash deploy.sh
```
