// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com

package com.mewcode.memory;

import com.mewcode.agent.Agent;
import com.mewcode.agent.AgentEvent;
import com.mewcode.config.ProviderConfig;
import com.mewcode.conversation.ConversationManager;
import com.mewcode.llm.LlmClient;
import com.mewcode.permission.PermissionChecker;
import com.mewcode.permission.PermissionMode;
import com.mewcode.tool.ToolRegistry;
import com.mewcode.tool.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class MemoryConsolidatorTest {

    // =========================================================================
    // 门控逻辑单元测试
    // =========================================================================

    @Test
    void maybeRun_skipsWhenMemoryDirMissing(@TempDir Path dir) {
        var consolidator = new MemoryConsolidator(dir.toString());
        // 不应该抛异常
        consolidator.maybeRun(null, null, "anthropic");
    }

    @Test
    void maybeRun_skipsWhenTimeGateNotMet(@TempDir Path dir) throws IOException {
        Path memDir = dir.resolve(".mewcode/memory");
        Files.createDirectories(memDir);

        // 写一个 1 小时前的锁文件（不满足 24 小时门控）
        Path lockFile = memDir.resolve(".consolidate-lock");
        Files.writeString(lockFile, "");
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        Files.setLastModifiedTime(lockFile,
                java.nio.file.attribute.FileTime.from(oneHourAgo));

        var consolidator = new MemoryConsolidator(dir.toString());
        // 不应该触发（直接返回）
        consolidator.maybeRun(null, null, "anthropic");
    }

    @Test
    void maybeRun_skipsWhenSessionGateNotMet(@TempDir Path dir) throws IOException {
        Path memDir = dir.resolve(".mewcode/memory");
        Path sessDir = dir.resolve(".mewcode/sessions");
        Files.createDirectories(memDir);
        Files.createDirectories(sessDir);

        // 只创建 2 个会话（不满足 5 个门控）
        Files.writeString(sessDir.resolve("s1.jsonl"),
                "{\"role\":\"user\",\"content\":\"a\",\"ts\":1}\n");
        Files.writeString(sessDir.resolve("s2.jsonl"),
                "{\"role\":\"user\",\"content\":\"b\",\"ts\":2}\n");

        var consolidator = new MemoryConsolidator(dir.toString(), 0, 5);
        consolidator.maybeRun(null, null, "anthropic");
        // 不应该触发
    }

    // =========================================================================
    // E2E 测试：真实 LLM 整理
    // =========================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "MEWCODE_TEST_API_KEY", matches = ".+")
    void e2e_consolidationMergesDuplicates(@TempDir Path dir) throws Exception {
        String apiKey = System.getenv("MEWCODE_TEST_API_KEY");
        String baseUrl = System.getenv().getOrDefault("MEWCODE_TEST_BASE_URL", "https://api.minimaxi.com/v1");
        String model = System.getenv().getOrDefault("MEWCODE_TEST_MODEL", "MiniMax-M3");

        Path memDir = dir.resolve(".mewcode/memory");
        Files.createDirectories(memDir);

        // 写两个重复的记忆
        writeMemory(memDir, "feedback_no_push.md", "feedback", "no-push",
                "Don't push without asking", "用户不希望自动 push 代码");
        writeMemory(memDir, "feedback_auto_push.md", "feedback", "auto-push",
                "Don't auto push code", "用户不喜欢自动 push，每次都要先问一下");
        writeMemory(memDir, "user_role.md", "user", "user-role",
                "User is a backend engineer", "用户是后端工程师，主要用 Go 和 Java");

        Files.writeString(memDir.resolve("MEMORY.md"),
                "- [No push](feedback_no_push.md) — 不要自动 push\n" +
                "- [Auto push](feedback_auto_push.md) — 不要自动 push 代码\n" +
                "- [User role](user_role.md) — 后端工程师\n");

        System.out.println("Before consolidation:");
        System.out.println("  Files: " + listFiles(memDir));
        System.out.println("  MEMORY.md: " + Files.readString(memDir.resolve("MEMORY.md")));

        // 构建 LLM 客户端
        ProviderConfig cfg = new ProviderConfig();
        cfg.setProtocol("openai-compat");
        cfg.setBaseUrl(baseUrl);
        cfg.setApiKey(apiKey);
        cfg.setModel(model);
        cfg.setContextWindow(200000);

        LlmClient client = LlmClient.create(cfg, "");

        // 构建工具注册表
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());
        registry.register(new EditFileTool());
        registry.register(new GlobTool());
        registry.register(new GrepTool());
        registry.register(new BashTool());

        PermissionChecker checker = new PermissionChecker(PermissionMode.BYPASS, memDir);

        // 构建整理 prompt
        var consolidator = new MemoryConsolidator(dir.toString());
        // 用反射或直接构造 prompt
        String prompt = buildPrompt(memDir.toString());

        ConversationManager conv = new ConversationManager();
        conv.addUserMessage(prompt);

        Agent subAgent = new Agent(client, registry, "openai-compat", cfg);
        subAgent.setChecker(checker);
        subAgent.setWorkDir(dir.toString());
        subAgent.setMaxIterations(15);

        // 驱动子 Agent 到完成
        BlockingQueue<AgentEvent> queue = subAgent.run(conv);
        while (true) {
            AgentEvent event = queue.take();
            if (event instanceof AgentEvent.LoopComplete) break;
            if (event instanceof AgentEvent.ErrorEvent e) {
                System.err.println("Agent error: " + e.message());
                break;
            }
        }

        System.out.println("\nAfter consolidation:");
        System.out.println("  Files: " + listFiles(memDir));
        String indexContent = Files.readString(memDir.resolve("MEMORY.md"));
        System.out.println("  MEMORY.md:\n" + indexContent);

        long indexLines = indexContent.lines()
                .filter(l -> !l.isBlank())
                .count();
        System.out.println("  Index lines: " + indexLines);

        assertTrue(indexLines <= 3, "expected ≤3 index lines, got " + indexLines);
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    private static void writeMemory(Path dir, String filename, String type,
                                     String name, String desc, String body) throws IOException {
        String content = String.format("""
                ---
                name: %s
                description: %s
                metadata:
                  type: %s
                ---

                %s
                """, name, desc, type, body);
        Files.writeString(dir.resolve(filename), content);
    }

    private static List<String> listFiles(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static String buildPrompt(String memDir) {
        return """
                # Dream: Memory Consolidation

                You are performing a dream — a reflective pass over your memory files.

                Project memory directory: `%s`
                The memory directory already exists — write to it directly.

                ## Phase 1 — Orient
                - `ls` the memory directory to see what already exists
                - Read `MEMORY.md` to understand the current index
                - Skim existing topic files so you improve them rather than creating duplicates

                ## Phase 2 — Gather recent signal
                Look for new information worth persisting.

                ## Phase 3 — Consolidate
                Focus on:
                - Merging new signal into existing topic files rather than creating near-duplicates
                - Deleting contradicted facts

                ## Phase 4 — Prune and index
                Update `MEMORY.md` so it stays under 200 lines AND under ~25KB.
                - Remove pointers to memories that are now stale, wrong, or superseded
                - Add pointers to newly important memories
                - Resolve contradictions

                **Tool constraints:** Bash is restricted to read-only commands.

                Return a brief summary of what you consolidated, updated, or pruned.
                """.formatted(memDir);
    }
}
