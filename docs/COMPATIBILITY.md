# 兼容性说明

## 平台与依赖

| 项目 | 要求 |
| --- | --- |
| Android | 12 或更高版本 |
| 编译环境 | JDK 17、Android SDK 35 |
| Root 框架 | Magisk 或 KernelSU |
| Hook 框架 | LSPosed |
| 目标包名 | `com.heytap.speechassist` |

## 小布助手版本

| 版本 | TTS 路由 |
| --- | --- |
| 11.8.3 | WebSocket TTS |
| 12.9.9 | Engine 与流式 TTS |

模块只为已定义的版本路由安装 Hook。目标版本、类或方法不匹配时，对应能力不会启用。

## GPT-SoVITS 服务

服务需要支持以下接口：

| 接口 | 用途 |
| --- | --- |
| `GET /character_list` | 获取角色与情感目录 |
| `POST /tts` | 请求语音合成 |

模块支持 WAV、PCM、MP3 和 OGG 音频响应，并可配置流式请求、超时和生成参数。
