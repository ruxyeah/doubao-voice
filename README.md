# 豆包实时语音对话API服务

基于Spring Boot 3 + JDK 17实现的豆包端到端实时语音大模型Java API服务。

## 功能特性

- ✅ 完整实现豆包二进制协议编解码
- ✅ WebSocket连接豆包API
- ✅ 多用户会话管理
- ✅ REST API接口
- ✅ WebSocket流式接口
- ✅ 支持语音对话和文本对话
- ✅ 支持多种音色和模型版本
![ScreenShot_2026-01-28_085752_128.png](docs%2FScreenShot_2026-01-28_085752_128.png)
![ScreenShot_2026-01-28_085958_390.png](docs%2FScreenShot_2026-01-28_085958_390.png)
## 技术栈

- JDK 17
- Spring Boot 3.2.0
- OkHttp 4.12.0 (WebSocket客户端)
- Jackson (JSON处理)
- Lombok

## 快速开始

### 1. 配置

修改 `application.yml` 中的豆包API配置：

```yaml
doubao:
  api:
    app-id: your-app-id
    access-key: your-access-key
```

或通过环境变量配置：

```bash
export DOUBAO_APP_ID=your-app-id
export DOUBAO_ACCESS_KEY=your-access-key
```

### 2. 运行

```bash
cd doubao-voice-api
mvn spring-boot:run
```

### 3. 使用

服务启动后，访问 http://localhost:8080

## API文档

### REST API

#### 创建会话
```http
POST /api/v1/voice/sessions
Content-Type: application/json

{
  "speaker": "zh_female_vv_jupiter_bigtts",
  "botName": "豆包",
  "model": "O"
}
```

#### 启动会话
```http
POST /api/v1/voice/sessions/{sessionId}/start
Content-Type: application/json

{
  "speaker": "zh_female_vv_jupiter_bigtts",
  "systemRole": "你是一个友好的助手"
}
```

#### 获取会话状态
```http
GET /api/v1/voice/sessions/{sessionId}
```

#### 发送文本查询
```http
POST /api/v1/voice/sessions/{sessionId}/text
Content-Type: application/json

{
  "text": "你好，请介绍一下你自己"
}
```

#### 结束会话
```http
POST /api/v1/voice/sessions/{sessionId}/end
```

#### 删除会话
```http
DELETE /api/v1/voice/sessions/{sessionId}
```

### WebSocket API

连接地址: `ws://localhost:8080/ws/voice?sessionId={sessionId}`

#### 客户端发送消息格式

**发送音频数据:**
```json
{
  "type": "audio",
  "data": "base64编码的PCM16音频数据"
}
```

**发送文本查询:**
```json
{
  "type": "text",
  "text": "你好"
}
```

**控制命令:**
```json
{
  "type": "control",
  "action": "start",
  "config": {
    "speaker": "zh_female_vv_jupiter_bigtts",
    "botName": "豆包"
  }
}
```

#### 服务端推送消息格式

**ASR识别结果:**
```json
{
  "type": "asr",
  "event": "result",
  "text": "你好",
  "isInterim": false
}
```

**AI回复:**
```json
{
  "type": "chat",
  "event": "response",
  "text": "你好！我是豆包...",
  "questionId": "xxx",
  "replyId": "xxx"
}
```

**TTS音频:** 二进制消息（Float32 PCM 24kHz）

**状态变更:**
```json
{
  "type": "status",
  "status": "session_started",
  "dialogId": "xxx"
}
```

## 音频格式

### 输入音频（发送到服务器）
- 格式: PCM16
- 采样率: 16kHz
- 声道: 单声道
- 字节序: 小端序

### 输出音频（从服务器接收）
- 格式: Float32 PCM
- 采样率: 24kHz
- 声道: 单声道

## 支持的音色

### O版本（精品音色）
- zh_female_vv_jupiter_bigtts（默认）
- 更多音色请参考豆包官方文档

### SC版本（声音复刻）
- ICL_zh_female_aojiaonvyou_tob
- ICL_zh_male_aiqilingren_tob
- 等21种官方克隆音色

### SC2.0版本
- saturn_zh_female_aojiaonvyou_tob
- saturn_zh_male_aiqilingren_tob
- 等21种官方克隆音色

## 模型版本

| 版本 | 说明 |
|------|------|
| O | 支持精品音色 |
| SC | 支持声音复刻 |
| 1.2.1.0 | O2.0版本 |
| 2.2.0.0 | SC2.0版本 |

## 项目结构

```
src/main/java/com/doubao/voice/
├── DoubaoVoiceApplication.java     # 启动类
├── config/                          # 配置
│   ├── DoubaoProperties.java       # 配置属性
│   └── WebSocketConfig.java        # WebSocket配置
├── protocol/                        # 协议层
│   ├── constants/                  # 常量定义
│   ├── codec/                      # 编解码器
│   └── message/                    # 消息模型
├── client/                          # 豆包客户端
│   ├── DoubaoWebSocketClient.java  # WebSocket客户端
│   └── DoubaoClientListener.java   # 事件监听器
├── session/                         # 会话管理
│   ├── VoiceSession.java           # 会话实体
│   ├── VoiceSessionManager.java    # 会话管理器
│   └── SessionConfig.java          # 会话配置
├── service/                         # 业务服务
│   ├── VoiceService.java           # 服务接口
│   └── VoiceServiceImpl.java       # 服务实现
├── api/                             # API层
│   ├── rest/                       # REST接口
│   └── websocket/                  # WebSocket接口
└── exception/                       # 异常处理
```

## 配置说明

```yaml
doubao:
  api:
    url: wss://openspeech.bytedance.com/api/v3/realtime/dialogue
    app-id: your-app-id          # 必填
    access-key: your-access-key  # 必填
    connect-timeout: 10000       # 连接超时（毫秒）
    read-timeout: 30000          # 读取超时（毫秒）

  session:
    timeout: 300000              # 会话超时（毫秒）
    max-sessions: 100            # 最大并发会话数

  tts:
    default-speaker: zh_female_vv_jupiter_bigtts
    sample-rate: 24000

  dialog:
    default-bot-name: 豆包
    default-model: O
```

## 注意事项

1. 需要在豆包开放平台申请API凭证
2. 音频格式必须严格按照规范
3. 注意会话状态管理，避免在错误状态下发送消息
4. 生产环境建议配置合理的会话超时和最大并发数

## License

MIT
