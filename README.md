# MiuCoder

轻量级终端 Coding Agent，基于 ReAct 与 Plan Mode 双模式驱动 LLM 自主完成编程任务。

- 技术栈：Java 21, Gradle, MCP, ReAct, Skill, Multi-Agent
- 交互、引擎、工具、记忆、安全五层分层架构
- 支持 Anthropic、OpenAI 双协议、MCP 工具扩展、Skill 技能包、跨会话记忆、多 Agent 并行协作

## 快速开始

### 第一步：配置 LLM 和 MCP

编辑用户家目录下的 `.mewcode/config.yaml`（Windows 即 `C:\Users\<用户名>\.mewcode\config.yaml`），若没有该目录则新建，填入你的 LLM 提供商信息：

```yaml
providers:
  - name: anthropic-official
    protocol: anthropic                    # 支持 anthropic / openai 两种协议
    base_url: https://your-api-provider.com/api/anthropic
    api_key: "your-api-key-here"
    model: claude-sonnet-4-6
    thinking: true                         # 是否开启 extended thinking

mcp_servers:
  - name: context7
    command: npx
    args: ["-y", "@upstash/context7-mcp"]
```

配置说明：

| 配置项 | 说明 |
| --- | --- |
| `protocol` | 填 `anthropic` 或 `openai`，取决于你的提供商兼容哪种 API |
| `base_url` | 你的 API 地址 |
| `api_key` | 你的 API Key |
| `model` | 模型名称 |
| `mcp_servers` | MCP Server 列表，每项需要 `name`、`command` 和 `args` |

### 第二步：环境要求

- Java 21+（项目自带 Gradle Wrapper，无需单独安装）
- Windows 推荐在 PowerShell 中使用

### 第三步：构建

> 目前已经构建好了，可以直接运行；如需重新构建，执行：

```powershell
.\gradlew.bat shadowJar
```

### 第四步：运行

```powershell
java -jar build/libs/mewcode.jar
```
