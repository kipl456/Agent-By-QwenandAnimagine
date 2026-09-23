# 🎨 Qwen + Animagine 智能图片生成系统

用**中文自然语言描述画面**，由大模型理解并翻译成英文提示词，再交给动漫文生图模型生成图片。支持**多轮对话记忆**与**历史画廊**。

> 输入「银发少女穿着和服站在樱花树下」→ 系统自动生成英文 tags → 输出一张动漫图。

---

## ✨ 功能特性

- **自然语言出图**：无需手写英文提示词，中文描述即可
- **LLM 提示词工程**：Qwen 按系统提示词把中文翻译成 Animagine 认得的英文 tag（逗号分隔 + 质量词）
- **多轮对话记忆**：结合上下文连续改图（如「把和服换成蓝色」），记忆存于 Redis，带 7 天 TTL
- **历史画廊**：每次生成的图片记录（提示词 / 尺寸 / seed / 时间）持久化到 MySQL，前端可浏览、点击看大图
- **前后端分离**：Vue3 开发期通过 Vite 代理直连后端，无跨域问题

---

## 🏗 系统架构

```
浏览器 (Vue3 + Vite, :5173)
      │   POST /api/chat · GET /api/history · GET /images/{name}
      ▼
Spring Boot 4 后端 (Java 21, :8080)
      ├── Redis      ← 多轮会话记忆 (key: conv:default, TTL 7 天)
      ├── MySQL      ← 图片记录持久化 (表: image_record)
      ├── Spring AI (Ollama)
      │        └── HTTP → Ollama :11434 → Qwen3.5-9B   「中文 → 英文提示词」
      └── HTTP POST /generate
               └── FastAPI :8000 → diffusers → Animagine XL 4.0 (GPU)  「提示词 → 图片」
```

**数据流**：

1. 前端把用户的中文描述 `POST /api/chat`
2. 后端从 Redis 读回该会话历史
3. 把 `系统提示词 + 历史 + 当前消息` 一起交给 Ollama 里的 Qwen，得到英文 tags
4. 把英文 tags 发给 Python 画图服务，拿到图片的 base64
5. 图片落盘到 `./generated`，并在 MySQL 记一条图片记录
6. 本轮对话写回 Redis（出图成功后写入）
7. 返回 `{ prompt, imageUrl }` 给前端展示

---

## 🧰 技术栈

### 后端
| 技术 | 版本 | 用途 |
|---|---|---|
| Java | 21 | 运行环境 |
| Spring Boot | 4.1.1 | Web 框架 |
| Spring AI | 2.0.1 | 统一调用本地大模型（`spring-ai-starter-model-ollama`） |
| Spring Data JPA | — | 图片记录持久化 |
| Spring Data Redis | — | 会话记忆存储 |
| MySQL Connector/J | — | MySQL 驱动 |
| Jackson | 3（`tools.jackson.*`） | JSON 序列化（Spring Boot 4 默认） |
| Maven | — | 依赖与构建（含 `mvnw` 包装器） |

### 前端
| 技术 | 版本 |
|---|---|
| Vue | 3.5 |
| Vite | 8.x |
| TypeScript | 6.x |
| Node.js | ^22.18.0 \|\| >=24.12.0 |

### AI 推理侧
| 技术 | 用途 |
|---|---|
| Ollama | 承载本地 Qwen 模型 |
| Qwen3.5-9B | 中文 → 英文提示词 |
| Python + FastAPI | 画图服务 HTTP 封装 |
| diffusers + PyTorch | 加载并运行文生图模型 |
| Animagine XL 4.0 | Stable Diffusion XL 架构的动漫风格模型 |

### 中间件
| 技术 | 用途 |
|---|---|
| Redis | 会话记忆（含 TTL 自动过期） |
| MySQL | 图片记录持久化 |

---

## 📁 目录结构

```
.
├── QwenAndAnimagine/            # Spring Boot 后端
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   └── src/main/
│       ├── java/com/example/qwenandanimagine/
│       │   ├── QwenAndAnimagineApplication.java
│       │   └── backend/
│       │       ├── config/       # AppConfig
│       │       ├── controller/   # ChatController / ImageController / TestController
│       │       ├── dto/          # ChatRequest / ChatResponse / ChatMessage
│       │       ├── entity/       # ImageRecord
│       │       ├── mem/          # ConversationMemoryStore (Redis 记忆)
│       │       ├── repo/         # ImageRecordRepository
│       │       └── service/      # QwenService / ImageService
│       └── resources/application.yml
├── vue/                         # Vue3 前端
│   ├── vite.config.ts           # 开发代理 /api、/images → :8080
│   └── src/App.vue              # 聊天 + 最新生成 + 历史画廊 + 大图预览
└── animagine-service/
    └── main.py                  # FastAPI 画图服务（diffusers + Animagine XL 4.0）
```

---

## 🚀 快速开始

### 0. 前置要求

- **JDK 21**、**Maven**（或用自带的 `mvnw`）
- **Node.js** ≥ 22.18（推荐 24.x）
- **Python 3.11+**
- **NVIDIA 显卡，显存 ≥ 8GB**（Animagine 是 SDXL 模型；代码已用 `enable_model_cpu_offload()` 把显存峰值压到约 2~3GB）
- **Redis**、**MySQL**（Docker 最方便）
- **Ollama**，并已拉取 Qwen 模型

---

### 1️⃣ 启动中间件（Redis + MySQL）

```bash
docker run -d --name anime-redis -p 6379:6379 redis:7
docker run -d --name anime-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e MYSQL_DATABASE=animegen \
  mysql:8 --character-set-server=utf8mb4
```

> 数据库 `animegen` 会自动创建；表结构由 JPA 的 `ddl-auto: update` 首次启动时自动建。

---

### 2️⃣ 启动 Ollama 并准备模型

```bash
ollama pull <你的Qwen模型名>
ollama list          # 确认模型名，和 application.yml 里保持一致
```

**让 16K 上下文生效**（否则长对话会被截断）：
```bash
ollama show --modelfile <模型名> > Modelfile
# 在 Modelfile 中加一行：PARAMETER num_ctx 16384
ollama create qwen-16k -f Modelfile
```

---

### 3️⃣ 启动 Python 画图服务

准备 Python 环境：

```bash
python -m venv .venv
.venv\Scripts\activate            # Windows
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
pip install "diffusers[torch]" transformers accelerate safetensors pillow fastapi uvicorn
python -c "import torch; print(torch.cuda.is_available())"   # 应输出 True
```

> ⚠️ **需先修改配置**：`animagine-service/main.py` 中通过 `from_single_file()` 加载**本地权重文件**，
> 默认路径指向作者本机的 ComfyUI 目录，请改成你自己的 Animagine XL 4.0 `.safetensors` 路径。
> （若没有本地权重，也可改用 `from_pretrained("cagliostrolab/animagine-xl-4.0")` 从 HuggingFace 下载。）

启动：

```bash
cd animagine-service
uvicorn main:app --host 127.0.0.1 --port 8000
```

验证：浏览器打开 <http://127.0.0.1:8000/docs>，用 `/generate` 手动生成一张图；或访问 `/health`。

---

### 4️⃣ 启动后端

先按需修改 `QwenAndAnimagine/src/main/resources/application.yml`：

```yaml
spring:
  ai:
    ollama:
      base-url: http://127.0.0.1:11434
      chat:
        model: "你的模型名"        # 必须与 ollama list 一致
        num-ctx: 16384
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/animegen?...
    username: root
    password: 你的密码
  data:
    redis:
      host: 127.0.0.1
      port: 6379
animagine:
  base-url: http://127.0.0.1:8000   # Python 画图服务
image:
  storage-dir: ./generated          # 图片落盘目录
```

启动：

```bash
cd QwenAndAnimagine
mvnw spring-boot:run          # 或 mvn spring-boot:run
```

---

### 5️⃣ 启动前端

```bash
cd vue
npm install
npm run dev
```

打开 <http://localhost:5173>，输入中文描述即可出图。

> `vite.config.ts` 已配置代理：`/api` 与 `/images` 转发到 `http://localhost:8080`，开发期无需处理跨域。
> 生产构建：`npm run build`，将 `dist/` 交给 Spring Boot 的静态资源目录托管。

---

## 🔌 API 说明

| 方法 | 路径 | 说明 | 请求 / 响应 |
|---|---|---|---|
| POST | `/api/chat` | 核心接口：中文描述 → 提示词 + 图片 | 请求 `{"message":"银发少女站在樱花树下"}`<br>响应 `{"prompt":"silver haired girl, ...","imageUrl":"/images/xxx.png"}` |
| GET | `/api/history` | 历史画廊，返回最近 50 条图片记录（按时间倒序） | 响应 `ImageRecord[]` |
| GET | `/images/{name}` | 读取生成的图片文件（PNG） | 响应图片二进制 |
| POST | `/test/qwen` | 临时联调接口：仅测试 Java → Qwen 的提示词生成（可删除） | 请求 `{"message":"..."}`<br>响应 `{"prompt":"..."}` |

**ImageRecord 字段**：`id`、`prompt`、`negativePrompt`、`width`、`height`、`seed`、`imageUrl`、`userText`、`createdAt`

---

## 🖼 界面说明

- **左侧**：聊天区（示例快捷输入）、最新生成的大图
- **右侧**：历史画廊（缩略图网格，可刷新）
- **点击任意图片**：弹出大图预览，附带用户原始描述、英文提示词与生成时间

---

## ⚙️ 关键设计说明

- **记忆的真相**：大模型本身无状态。所谓「跨会话记忆」是后端每次请求前从 Redis 读回最近 N 轮消息、重新拼进上下文。记忆窗口为最近 6 轮，TTL 7 天，防止无限膨胀。
- **提示词工程**：Qwen 的 system prompt 要求只输出一行英文 tags（主体 → 细节 → 风格 → 构图），结尾固定追加 `masterpiece, best quality, absurdres`，并单独提供 negative prompt。
- **画图参数**：默认 832×1216、步数 28、CFG 7.0、seed 随机（传 `-1`）。
- **图片访问**：图片落盘到 `./generated`，由 `GET /images/{name}` 暴露；该接口做了路径穿越防护（限定只能读取指定目录内的文件）。
- **失败不写记忆**：只有当出图成功后，才把本轮对话写入 Redis，避免模型「记住」一张其实没生成出来的画面。

---

## 🧩 常见问题

| 现象 | 排查方向 |
|---|---|
| Qwen 返回中文而非英文 tags | 加强 `QwenService` 中 system prompt 的「只输出英文」约束 |
| 图片糊 / 画质差 | 确认提示词含量质词、分辨率不要太低、negative prompt 已生效 |
| 画图服务报显存不足 | 调低 `width/height/steps`，避免并发请求 |
| 后端启动报 MySQL 连接失败 | 检查容器是否运行、库名/账号密码是否与 `application.yml` 一致 |
| 页面出图但图片裂开 | 后端 `image.storage-dir` 是否可写；`/images/{name}` 是否返回 200 |
| 长对话后模型「忘事」 | 确认 Ollama 侧 `num_ctx` 已设为 16384 |
| Spring AI 依赖报错 | Spring AI 版本需与 Spring Boot 版本匹配（本项目为 Boot 4.1.1 + Spring AI 2.0.1） |

---

## 📌 已知限制与后续计划

- 当前为**单用户版本**，会话记忆使用固定 key（`conv:default`），尚未引入用户体系与登录鉴权
- 出图为**同步调用**，生成期间请求会等待约 10~30 秒；后续可引入消息队列做异步任务 + 结果推送
- `TestController`（`/test/qwen`）为联调期临时接口，可删除
- 尚未补充单元测试与 CI 流程

---

## 📄 License

本项目仅用于个人学习与技术练习。
