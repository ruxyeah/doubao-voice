# 豆包实时语音对话 API 对接指南

## 目录

1. [整体架构](#1-整体架构)
2. [快速开始](#2-快速开始)
3. [REST API 接口](#3-rest-api-接口)
4. [WebSocket 接口](#4-websocket-接口)
5. [音频处理原理](#5-音频处理原理)
6. [完整对接流程](#6-完整对接流程)
7. [前端代码示例](#7-前端代码示例)
8. [常见问题](#8-常见问题)

---

## 1. 整体架构

### 1.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              前端应用                                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  麦克风采集  │  │  音频重采样  │  │  WebSocket  │  │  音频播放   │    │
│  │  (48kHz)    │─▶│  48k→16k   │─▶│   客户端    │◀─│  (24kHz)    │    │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │
└────────────────────────────────────────┬────────────────────────────────┘
                                         │ WebSocket (ws://localhost:8080/ws/voice)
                                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Java API 服务层                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  REST API   │  │  WebSocket  │  │   会话管理   │  │  协议编解码  │    │
│  │  Controller │  │   Handler   │  │   Manager   │  │   Codec     │    │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │
└────────────────────────────────────────┬────────────────────────────────┘
                                         │ WebSocket (wss://豆包API)
                                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         豆包实时语音 API                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │    ASR      │  │    LLM      │  │    TTS      │  │   VAD       │    │
│  │  语音识别   │─▶│  大语言模型  │─▶│  语音合成   │  │  语音检测   │    │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 数据流向

```
用户说话 → 麦克风采集(48kHz) → 重采样(16kHz) → PCM16编码 → Base64
    → WebSocket → Java服务 → 二进制协议编码 → 豆包API

豆包API → ASR识别 → LLM生成回复 → TTS合成 → Float32 PCM(24kHz)
    → 二进制协议 → Java服务 → WebSocket → 前端播放
```

---

## 2. 快速开始

### 2.1 前置条件

- JDK 17+
- Maven 3.6+
- 现代浏览器（Chrome/Firefox/Edge）
- 豆包 API 凭证（App ID、Access Key）

### 2.2 配置凭证

编辑 `application.yml`：

```yaml
doubao:
  api:
    app-id: ${DOUBAO_APP_ID:你的AppID}
    access-key: ${DOUBAO_ACCESS_KEY:你的AccessKey}
```

或设置环境变量：

```bash
export DOUBAO_APP_ID=你的AppID
export DOUBAO_ACCESS_KEY=你的AccessKey
```

### 2.3 启动服务

```bash
cd doubao-voice-api
mvn spring-boot:run
```

### 2.4 访问测试页面

打开浏览器访问：`http://localhost:8080`

---

## 3. REST API 接口

### 3.1 创建会话

创建一个新的语音对话会话。

**请求**

```http
POST /api/v1/voice/sessions
Content-Type: application/json

{
  "speaker": "zh_female_vv_jupiter_bigtts",
  "botName": "豆包",
  "systemRole": "你是一个友好的AI助手",
  "model": "O"
}
```

**参数说明**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| speaker | string | 否 | 音色，默认 zh_female_vv_jupiter_bigtts |
| botName | string | 否 | 机器人名称，默认"豆包" |
| systemRole | string | 否 | 系统角色设定 |
| model | string | 否 | 模型版本：O/SC/1.2.1.0/2.2.0.0 |

**响应**

```json
{
  "sessionId": "uuid-xxx-xxx",
  "state": "CREATED",
  "createdAt": "2024-01-27T12:00:00"
}
```

### 3.2 获取会话状态

```http
GET /api/v1/voice/sessions/{sessionId}
```

**响应**

```json
{
  "sessionId": "uuid-xxx-xxx",
  "state": "SESSION_STARTED",
  "dialogId": "dialog-xxx",
  "createdAt": "2024-01-27T12:00:00"
}
```

**会话状态说明**

| 状态 | 说明 |
|------|------|
| CREATED | 会话已创建，尚未连接 |
| CONNECTING | 正在连接豆包API |
| CONNECTED | 已连接，等待开始会话 |
| SESSION_STARTED | 会话已开始，可以对话 |
| SESSION_ENDED | 会话已结束 |
| ERROR | 发生错误 |

### 3.3 发送文本消息

通过REST API发送文本消息（非语音）。

```http
POST /api/v1/voice/sessions/{sessionId}/text
Content-Type: application/json

{
  "text": "你好，请介绍一下你自己"
}
```

### 3.4 结束会话

```http
POST /api/v1/voice/sessions/{sessionId}/end
```

### 3.5 删除会话

```http
DELETE /api/v1/voice/sessions/{sessionId}
```

---

## 4. WebSocket 接口

### 4.1 连接地址

```
ws://localhost:8080/ws/voice?sessionId={sessionId}
```

**注意**：必须先通过 REST API 创建会话，获取 sessionId 后再连接 WebSocket。

### 4.2 客户端发送消息格式

#### 4.2.1 控制命令

**开始会话**

```json
{
  "type": "control",
  "action": "start",
  "config": {
    "speaker": "zh_female_vv_jupiter_bigtts",
    "botName": "豆包",
    "systemRole": "你是一个友好的助手",
    "speakingStyle": "",
    "model": "O",
    "enableWebSearch": false
  }
}
```

**结束会话**

```json
{
  "type": "control",
  "action": "end"
}
```

#### 4.2.2 发送音频数据

```json
{
  "type": "audio",
  "data": "Base64编码的PCM16音频数据"
}
```

**音频格式要求**：
- 格式：PCM16（16位有符号整数）
- 采样率：16kHz
- 声道：单声道（Mono）
- 字节序：小端序（Little-Endian）

#### 4.2.3 发送文本消息

```json
{
  "type": "text",
  "text": "你好"
}
```

### 4.3 服务端推送消息格式

#### 4.3.1 状态变更

```json
{
  "type": "status",
  "status": "session_started",
  "dialogId": "dialog-xxx-xxx"
}
```

| status | 说明 |
|--------|------|
| connected | WebSocket已连接 |
| connection_started | 豆包API连接已建立 |
| session_started | 对话会话已开始 |
| session_ended | 对话会话已结束 |
| error | 发生错误 |

#### 4.3.2 ASR 语音识别结果

```json
{
  "type": "asr",
  "event": "result",
  "text": "你好",
  "isFinal": true
}
```

| 字段 | 说明 |
|------|------|
| text | 识别出的文本 |
| isFinal | true=最终结果，false=中间结果（实时识别） |

#### 4.3.3 AI 回复文本

```json
{
  "type": "chat",
  "event": "response",
  "text": "你好！我是豆包，很高兴认识你！",
  "questionId": "q-xxx",
  "replyId": "r-xxx"
}
```

#### 4.3.4 TTS 音频数据

服务端会以**二进制消息**发送 TTS 音频数据。

**音频格式**：
- 格式：Float32 PCM
- 采样率：24kHz
- 声道：单声道

#### 4.3.5 错误消息

```json
{
  "type": "error",
  "message": "错误描述"
}
```

---

## 5. 音频处理原理

### 5.1 音频采集与发送

#### 5.1.1 浏览器音频采集

```javascript
// 1. 获取麦克风权限
const mediaStream = await navigator.mediaDevices.getUserMedia({
    audio: {
        channelCount: 1,      // 单声道
        sampleRate: 48000,    // 浏览器通常采集48kHz
        echoCancellation: true,
        noiseSuppression: true
    }
});

// 2. 创建音频处理上下文
const audioContext = new AudioContext({ sampleRate: 48000 });
const source = audioContext.createMediaStreamSource(mediaStream);
const processor = audioContext.createScriptProcessor(4096, 1, 1);

// 3. 处理音频数据
processor.onaudioprocess = (e) => {
    const inputData = e.inputBuffer.getChannelData(0); // Float32Array
    // ... 处理并发送
};
```

#### 5.1.2 重采样（48kHz → 16kHz）

豆包 API 要求输入音频为 16kHz，但浏览器通常采集 48kHz。需要进行重采样：

```javascript
function resample(inputData, fromRate, toRate) {
    const ratio = fromRate / toRate;
    const newLength = Math.floor(inputData.length / ratio);
    const result = new Float32Array(newLength);

    for (let i = 0; i < newLength; i++) {
        // 线性插值
        const srcIndex = i * ratio;
        const srcIndexFloor = Math.floor(srcIndex);
        const srcIndexCeil = Math.min(srcIndexFloor + 1, inputData.length - 1);
        const t = srcIndex - srcIndexFloor;
        result[i] = inputData[srcIndexFloor] * (1 - t) + inputData[srcIndexCeil] * t;
    }
    return result;
}
```

#### 5.1.3 Float32 → PCM16 转换

```javascript
function convertToPCM16(float32Array) {
    const int16Array = new Int16Array(float32Array.length);
    for (let i = 0; i < float32Array.length; i++) {
        // 将 [-1, 1] 范围映射到 [-32768, 32767]
        const sample = Math.max(-1, Math.min(1, float32Array[i]));
        int16Array[i] = sample < 0 ? sample * 0x8000 : sample * 0x7FFF;
    }
    return new Uint8Array(int16Array.buffer);
}
```

### 5.2 音频接收与播放

#### 5.2.1 接收 Float32 PCM 数据

服务端返回的 TTS 音频是 Float32 格式（24kHz），直接以二进制形式通过 WebSocket 发送。

```javascript
ws.onmessage = (event) => {
    if (event.data instanceof ArrayBuffer) {
        // 二进制数据 = TTS 音频
        const float32Data = new Float32Array(event.data);
        playAudio(float32Data);
    } else {
        // JSON 消息
        const message = JSON.parse(event.data);
        handleMessage(message);
    }
};
```

#### 5.2.2 播放音频

```javascript
async function playAudio(float32Data) {
    const audioContext = new AudioContext({ sampleRate: 24000 });

    // 创建音频缓冲区
    const audioBuffer = audioContext.createBuffer(1, float32Data.length, 24000);
    audioBuffer.getChannelData(0).set(float32Data);

    // 播放
    const source = audioContext.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(audioContext.destination);
    source.start();
}
```

### 5.3 音频格式对照表

| 环节 | 格式 | 采样率 | 位深 | 说明 |
|------|------|--------|------|------|
| 浏览器采集 | Float32 | 48kHz | 32bit | 原始采集数据 |
| 发送到服务器 | PCM16 | 16kHz | 16bit | 重采样+格式转换 |
| 豆包 ASR 输入 | PCM16 | 16kHz | 16bit | 语音识别要求 |
| 豆包 TTS 输出 | Float32 | 24kHz | 32bit | 语音合成输出 |
| 浏览器播放 | Float32 | 24kHz | 32bit | 直接播放 |

---

## 6. 完整对接流程

### 6.1 时序图

```
┌──────┐          ┌──────────┐          ┌──────────┐          ┌──────────┐
│ 前端 │          │ Java服务 │          │ 豆包API  │          │   用户   │
└──┬───┘          └────┬─────┘          └────┬─────┘          └────┬─────┘
   │                   │                     │                     │
   │  1. POST /sessions│                     │                     │
   │──────────────────>│                     │                     │
   │  返回 sessionId   │                     │                     │
   │<──────────────────│                     │                     │
   │                   │                     │                     │
   │  2. WS连接        │                     │                     │
   │   /ws/voice       │                     │                     │
   │──────────────────>│  3. WS连接豆包API   │                     │
   │                   │────────────────────>│                     │
   │                   │  CONNECTION_STARTED │                     │
   │                   │<────────────────────│                     │
   │  status:connected │                     │                     │
   │<──────────────────│                     │                     │
   │                   │                     │                     │
   │  4. control:start │                     │                     │
   │──────────────────>│  5. START_SESSION   │                     │
   │                   │────────────────────>│                     │
   │                   │  SESSION_STARTED    │                     │
   │                   │<────────────────────│                     │
   │  status:started   │                     │                     │
   │<──────────────────│                     │                     │
   │                   │                     │                     │
   │                   │                     │     6. 用户说话     │
   │                   │                     │<────────────────────│
   │  7. audio:base64  │                     │                     │
   │──────────────────>│  8. 音频数据        │                     │
   │                   │────────────────────>│                     │
   │                   │                     │                     │
   │                   │  9. ASR_RESPONSE    │                     │
   │                   │<────────────────────│                     │
   │  asr:识别结果     │                     │                     │
   │<──────────────────│                     │                     │
   │                   │                     │                     │
   │                   │  10. CHAT_RESPONSE  │                     │
   │                   │<────────────────────│                     │
   │  chat:AI回复      │                     │                     │
   │<──────────────────│                     │                     │
   │                   │                     │                     │
   │                   │  11. TTS音频数据    │                     │
   │                   │<────────────────────│                     │
   │  binary:音频      │                     │                     │
   │<──────────────────│                     │     12. 播放语音    │
   │                   │                     │────────────────────>│
   │                   │                     │                     │
```

### 6.2 详细步骤说明

#### 步骤 1：创建会话

```javascript
const response = await fetch('/api/v1/voice/sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        speaker: 'zh_female_vv_jupiter_bigtts',
        botName: '豆包',
        model: 'O'
    })
});
const { sessionId } = await response.json();
```

#### 步骤 2：建立 WebSocket 连接

```javascript
const ws = new WebSocket(`ws://localhost:8080/ws/voice?sessionId=${sessionId}`);

ws.onopen = () => {
    console.log('WebSocket 已连接');
};
```

#### 步骤 3-4：等待连接就绪，开始会话

```javascript
ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);

    if (msg.type === 'status' && msg.status === 'connection_started') {
        // 豆包 API 连接就绪，开始会话
        ws.send(JSON.stringify({
            type: 'control',
            action: 'start',
            config: {
                speaker: 'zh_female_vv_jupiter_bigtts',
                botName: '豆包'
            }
        }));
    }

    if (msg.type === 'status' && msg.status === 'session_started') {
        // 会话已开始，可以发送音频
        enableMicrophone();
    }
};
```

#### 步骤 5-6：发送音频数据

```javascript
function sendAudioData(pcm16Data) {
    const base64 = arrayBufferToBase64(pcm16Data.buffer);
    ws.send(JSON.stringify({
        type: 'audio',
        data: base64
    }));
}
```

#### 步骤 7-8：接收并处理响应

```javascript
ws.onmessage = (event) => {
    if (event.data instanceof ArrayBuffer) {
        // TTS 音频数据
        playAudioData(new Float32Array(event.data));
        return;
    }

    const msg = JSON.parse(event.data);

    switch (msg.type) {
        case 'asr':
            // 显示语音识别结果
            displayASR(msg.text, msg.isFinal);
            break;

        case 'chat':
            // 显示 AI 回复
            displayChat(msg.text);
            break;

        case 'error':
            console.error('错误:', msg.message);
            break;
    }
};
```

---

## 7. 前端代码示例

### 7.1 完整示例

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>语音对话示例</title>
</head>
<body>
    <button id="btnStart">开始对话</button>
    <button id="btnMic" disabled>开始录音</button>
    <div id="messages"></div>

    <script>
        let ws = null;
        let sessionId = null;
        let mediaStream = null;
        let audioContext = null;
        let isRecording = false;

        // 开始对话
        document.getElementById('btnStart').onclick = async () => {
            // 1. 创建会话
            const res = await fetch('/api/v1/voice/sessions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ speaker: 'zh_female_vv_jupiter_bigtts' })
            });
            const data = await res.json();
            sessionId = data.sessionId;

            // 2. 连接 WebSocket
            ws = new WebSocket(`ws://localhost:8080/ws/voice?sessionId=${sessionId}`);
            ws.binaryType = 'arraybuffer';

            ws.onmessage = handleMessage;
            ws.onopen = () => log('WebSocket 已连接');
            ws.onerror = (e) => log('错误: ' + e);
        };

        // 处理消息
        function handleMessage(event) {
            if (event.data instanceof ArrayBuffer) {
                // 播放 TTS 音频
                playAudio(new Float32Array(event.data));
                return;
            }

            const msg = JSON.parse(event.data);

            if (msg.type === 'status') {
                if (msg.status === 'connection_started') {
                    // 开始会话
                    ws.send(JSON.stringify({
                        type: 'control',
                        action: 'start',
                        config: { speaker: 'zh_female_vv_jupiter_bigtts' }
                    }));
                }
                if (msg.status === 'session_started') {
                    document.getElementById('btnMic').disabled = false;
                    log('会话已开始，可以录音了');
                }
            }

            if (msg.type === 'asr') {
                log(`识别: ${msg.text} ${msg.isFinal ? '(最终)' : '(临时)'}`);
            }

            if (msg.type === 'chat') {
                log(`AI: ${msg.text}`);
            }
        }

        // 录音控制
        document.getElementById('btnMic').onclick = () => {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        };

        // 开始录音
        async function startRecording() {
            mediaStream = await navigator.mediaDevices.getUserMedia({
                audio: { channelCount: 1, sampleRate: 48000 }
            });

            audioContext = new AudioContext({ sampleRate: 48000 });
            const source = audioContext.createMediaStreamSource(mediaStream);
            const processor = audioContext.createScriptProcessor(4096, 1, 1);

            processor.onaudioprocess = (e) => {
                if (!isRecording) return;

                const input = e.inputBuffer.getChannelData(0);
                const resampled = resample(input, 48000, 16000);
                const pcm16 = toPCM16(resampled);

                ws.send(JSON.stringify({
                    type: 'audio',
                    data: btoa(String.fromCharCode(...pcm16))
                }));
            };

            source.connect(processor);
            processor.connect(audioContext.destination);
            isRecording = true;
            document.getElementById('btnMic').textContent = '停止录音';
        }

        // 停止录音
        function stopRecording() {
            isRecording = false;
            mediaStream?.getTracks().forEach(t => t.stop());
            audioContext?.close();
            document.getElementById('btnMic').textContent = '开始录音';
        }

        // 重采样
        function resample(data, from, to) {
            const ratio = from / to;
            const result = new Float32Array(Math.floor(data.length / ratio));
            for (let i = 0; i < result.length; i++) {
                result[i] = data[Math.floor(i * ratio)];
            }
            return result;
        }

        // Float32 -> PCM16
        function toPCM16(float32) {
            const pcm = new Int16Array(float32.length);
            for (let i = 0; i < float32.length; i++) {
                pcm[i] = Math.max(-32768, Math.min(32767, float32[i] * 32768));
            }
            return new Uint8Array(pcm.buffer);
        }

        // 播放音频
        async function playAudio(float32Data) {
            const ctx = new AudioContext({ sampleRate: 24000 });
            const buffer = ctx.createBuffer(1, float32Data.length, 24000);
            buffer.getChannelData(0).set(float32Data);

            const source = ctx.createBufferSource();
            source.buffer = buffer;
            source.connect(ctx.destination);
            source.start();
        }

        // 日志
        function log(msg) {
            const div = document.getElementById('messages');
            div.innerHTML += `<p>${msg}</p>`;
        }
    </script>
</body>
</html>
```

---

## 8. 常见问题

### 8.1 无法访问麦克风

**错误信息**：`Cannot read properties of undefined (reading 'getUserMedia')`

**原因**：
1. 使用 HTTP（非 HTTPS）且非 localhost 访问
2. 浏览器版本过旧

**解决方案**：
- 使用 `http://localhost:8080` 或 `http://127.0.0.1:8080` 访问
- 或配置 HTTPS
- 使用最新版 Chrome/Firefox/Edge

### 8.2 连接被拒绝 (403 Forbidden)

**错误信息**：`requested resource not granted`

**原因**：App ID 未授权使用实时语音对话服务

**解决方案**：
1. 登录 [火山引擎控制台](https://console.volcengine.com/)
2. 进入 语音技术 → 实时语音对话
3. 开通服务并为应用授权

### 8.3 消息解码失败

**错误信息**：`decode ws request failed: unable to decode`

**原因**：发送的消息格式不正确

**解决方案**：
- 确保音频格式为 PCM16 16kHz 单声道
- 检查 Base64 编码是否正确

### 8.4 没有声音播放

**可能原因**：
1. 浏览器自动播放策略限制
2. 音频格式解析错误

**解决方案**：
- 确保在用户交互后播放音频
- 检查接收的数据是否为有效的 Float32 数组

### 8.5 延迟过高

**优化建议**：
1. 减小音频采集缓冲区（如 2048 或 1024）
2. 使用更快的网络连接
3. 考虑使用 WebRTC 替代 ScriptProcessor（已废弃）

---

## 附录

### A. 支持的音色列表

| 音色ID | 说明 | 适用版本 |
|--------|------|----------|
| zh_female_vv_jupiter_bigtts | 女声-甜美 | O |
| zh_male_rap_luna_bigtts | 男声-说唱 | O |
| zh_female_shuangkuaisisi_moon_bigtts | 女声-爽快思思 | O |
| ICL_zh_female_* | 女声克隆音色 | SC |
| ICL_zh_male_* | 男声克隆音色 | SC |
| saturn_zh_female_* | 女声克隆音色 | SC2.0 |
| saturn_zh_male_* | 男声克隆音色 | SC2.0 |

### B. 模型版本说明

| 版本 | 说明 |
|------|------|
| O | 基础版本，支持精品音色 |
| SC | 支持声音复刻 |
| 1.2.1.0 | O2.0 版本 |
| 2.2.0.0 | SC2.0 版本 |

### C. 事件类型完整列表

| 事件ID | 名称 | 说明 |
|--------|------|------|
| 1 | START_CONNECTION | 开始连接 |
| 2 | FINISH_CONNECTION | 结束连接 |
| 50 | CONNECTION_STARTED | 连接已建立 |
| 100 | START_SESSION | 开始会话 |
| 102 | FINISH_SESSION | 结束会话 |
| 150 | SESSION_STARTED | 会话已开始 |
| 152 | SESSION_FINISHED | 会话已结束 |
| 200 | TASK_REQUEST | 音频任务请求 |
| 350 | TTS_SENTENCE_START | TTS句子开始 |
| 351 | TTS_SENTENCE_END | TTS句子结束 |
| 352 | TTS_RESPONSE | TTS音频响应 |
| 359 | TTS_ENDED | TTS播放结束 |
| 450 | ASR_INFO | 用户开始说话 |
| 451 | ASR_RESPONSE | ASR识别结果 |
| 459 | ASR_ENDED | 用户停止说话 |
| 501 | CHAT_TEXT_QUERY | 文本查询 |
| 550 | CHAT_RESPONSE | AI回复 |
| 559 | CHAT_ENDED | AI回复结束 |
