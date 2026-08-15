// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com

package com.mewcode.memory;

import com.mewcode.conversation.ConversationManager;
import com.mewcode.conversation.Message;
import com.mewcode.llm.LlmClient;
import com.mewcode.llm.StreamEvent;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Stream;

/**
 * 记忆管理器，使用独立 .md 文件 + MEMORY.md 索引的统一存储格式。
 *
 * <p>存储结构：
 * <ul>
 *   <li>用户级 (~/.mewcode/memory/)：存放 type=user / type=feedback 的记忆文件</li>
 *   <li>项目级 (.mewcode/memory/)：存放 type=project / type=reference 的记忆文件</li>
 * </ul>
 *
 * <p>每条记忆是一个独立的 .md 文件，包含 YAML frontmatter（name, description, type）。
 * 每个目录下有一个 MEMORY.md 索引文件，用一行指针格式 `- [Title](file.md) — description`
 * 汇总该目录下的所有记忆。
 */
public class MemoryManager {

    /** MEMORY.md 索引文件名 */
    private static final String ENTRYPOINT_NAME = "MEMORY.md";
    private static final int EXTRACTION_INTERVAL = 1;
    private static final String MEMORY_DIR = ".mewcode/memory";

    // user/feedback 跟随用户；project/reference 跟随项目
    private static final Set<String> USER_TYPES = Set.of("user", "feedback");
    private static final Set<String> PROJECT_TYPES = Set.of("project", "reference");

    private final Path userMemDirPath;
    private final Path projectMemDirPath;
    private int turnCount;

    public MemoryManager(String workDir) {
        this.projectMemDirPath = Path.of(workDir, MEMORY_DIR);
        this.userMemDirPath = Path.of(System.getProperty("user.home"), MEMORY_DIR);
        // 确保目录存在，让 Agent 的 Write 工具可以直接写入
        ensureDir(userMemDirPath);
        ensureDir(projectMemDirPath);
    }

    // ---- Directory accessors (for memory recall) ----

    /** 返回用户级记忆目录（~/.mewcode/memory/） */
    public Path userMemDir() {
        return userMemDirPath;
    }

    /** 返回项目级记忆目录（.mewcode/memory/） */
    public Path projectMemDir() {
        return projectMemDirPath;
    }

    /** 返回项目级 MEMORY.md 的路径 */
    public Path entrypointPath() {
        return projectMemDirPath.resolve(ENTRYPOINT_NAME);
    }

    /** 返回用户级 MEMORY.md 的路径 */
    public Path userEntrypointPath() {
        return userMemDirPath.resolve(ENTRYPOINT_NAME);
    }

    // ---- Accessors ----

    /**
     * 返回所有记忆的摘要行，格式为 "[type] name — description"。
     * 扫描两个目录下的 .md 文件（不含 MEMORY.md），按文件名排序。
     */
    public List<String> getMemories() {
        var files = loadAll();
        var out = new ArrayList<String>();
        for (var f : files) {
            String typeTag = f.type().isEmpty() ? "?" : f.type();
            String desc = f.description().isEmpty() ? f.filename() : f.description();
            out.add("[%s] %s — %s".formatted(typeTag, f.name(), desc));
        }
        return out;
    }

    public boolean shouldExtract() {
        turnCount++;
        return turnCount % EXTRACTION_INTERVAL == 0;
    }

    /**
     * 清除两个目录下的所有 .md 文件（包括 MEMORY.md）。
     */
    public void clear() {
        clearDir(userMemDirPath);
        clearDir(projectMemDirPath);
    }

    // ---- Memory file record ----

    /** 一个记忆文件的元数据 */
    public record MemoryFile(String path, String filename, String name, String description, String type) {}

    /**
     * 扫描两个目录，加载所有记忆文件的 frontmatter 元数据。
     * 用户级在前，项目级在后。
     */
    List<MemoryFile> loadAll() {
        var out = new ArrayList<MemoryFile>();
        out.addAll(loadDir(userMemDirPath));
        out.addAll(loadDir(projectMemDirPath));
        return out;
    }

    private static List<MemoryFile> loadDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> mdFiles;
        try (Stream<Path> stream = Files.list(dir)) {
            mdFiles = stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".md") && !n.equals(ENTRYPOINT_NAME);
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }

        var out = new ArrayList<MemoryFile>();
        for (Path fp : mdFiles) {
            try {
                String content = Files.readString(fp);
                var fm = MemoryScanner.parseFrontmatter(content);
                String name = fm.name().isEmpty()
                        ? fp.getFileName().toString().replace(".md", "")
                        : fm.name();
                out.add(new MemoryFile(
                        fp.toAbsolutePath().toString(),
                        fp.getFileName().toString(),
                        name, fm.description(), fm.type()));
            } catch (IOException ignored) {
                // 跳过不可读的文件
            }
        }
        return out;
    }

    private static void clearDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(".md"))
                  .forEach(p -> {
                      try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                  });
        } catch (IOException ignored) {}
    }

    // ---- Build system-reminder section ----

    /**
     * 构建记忆系统的 system-reminder 部分，包含 MEMORY.md 索引内容。
     * 确保两个目录都存在后读取各自的 MEMORY.md。
     */
    public String buildSystemReminder() {
        ensureDir(userMemDirPath);
        ensureDir(projectMemDirPath);

        var sb = new StringBuilder();
        sb.append("# auto memory\n\n");

        // 用户级 MEMORY.md
        appendEntrypoint(sb, "User-level", userMemDirPath);
        sb.append("\n\n");
        // 项目级 MEMORY.md
        appendEntrypoint(sb, "Project-level", projectMemDirPath);

        return sb.toString();
    }

    private static void appendEntrypoint(StringBuilder sb, String scopeLabel, Path memDir) {
        Path ep = memDir.resolve(ENTRYPOINT_NAME);
        sb.append("## %s %s (`%s`)\n\n".formatted(scopeLabel, ENTRYPOINT_NAME, ep));
        try {
            String content = Files.readString(ep).strip();
            if (!content.isEmpty()) {
                sb.append(content);
            } else {
                sb.append("This %s is currently empty.".formatted(ENTRYPOINT_NAME));
            }
        } catch (IOException e) {
            sb.append("This %s is currently empty.".formatted(ENTRYPOINT_NAME));
        }
    }

    // ---- Extraction via LLM ----

    /**
     * 扫描已有记忆文件，生成 manifest 给 LLM 做去重。
     */
    private String scanExistingMemories() {
        var entries = new ArrayList<String>();
        for (Path dir : List.of(userMemDirPath, projectMemDirPath)) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(f -> f.toString().endsWith(".md") && !f.getFileName().toString().equals(ENTRYPOINT_NAME))
                     .sorted()
                     .forEach(f -> {
                         try {
                             String content = Files.readString(f);
                             // 简单解析 frontmatter
                             String type = extractField(content, "type");
                             String desc = extractField(content, "description");
                             if (type.isEmpty()) type = "?";
                             if (desc.isEmpty()) desc = f.getFileName().toString();
                             entries.add("- [%s] %s: %s".formatted(type, f.getFileName(), desc));
                         } catch (IOException ignored) {}
                     });
            } catch (IOException ignored) {}
        }
        return String.join("\n", entries);
    }

    /**
     * 通过 LLM 从对话中提取记忆（参照 Go 版 extractor.go）。
     * 发送已有记忆 manifest 做去重，使用 MEMORY_NAME/TYPE/DESC/BODY 格式解析输出。
     */
    public void extract(LlmClient client, ConversationManager conv) {
        List<Message> messages = conv.getMessages();
        if (messages.size() < 4) return;

        // 只取最近 40 条消息
        int start = Math.max(0, messages.size() - 40);
        var sb = new StringBuilder();
        for (int i = start; i < messages.size(); i++) {
            var msg = messages.get(i);
            sb.append('[').append(msg.getRole()).append("]: ").append(msg.getContent()).append('\n');
        }

        // 扫描已有记忆做去重
        String manifest = scanExistingMemories();
        String manifestSection = manifest.isEmpty() ? "" :
                "\n\n## Existing memory files\n\n" + manifest +
                "\n\nCheck this list before creating — update an existing file rather than creating a duplicate.";

        ConversationManager extractConv = new ConversationManager();
        extractConv.addUserMessage(
                "Analyze the conversation below and extract memories worth saving.\n\n"
                + "For each memory, output in this exact format:\n"
                + "MEMORY_NAME: <kebab-case-name>\n"
                + "MEMORY_TYPE: <user|feedback|project|reference>\n"
                + "MEMORY_DESC: <one-line description>\n"
                + "MEMORY_BODY: <content>\n"
                + "---\n\n"
                + "Types:\n"
                + "- user/feedback → save to " + userMemDirPath + "\n"
                + "- project/reference → save to " + projectMemDirPath + "\n\n"
                + "What NOT to save:\n"
                + "- Code patterns derivable from reading the project\n"
                + "- Git history, debugging solutions\n"
                + "- Ephemeral task details\n\n"
                + "If nothing is worth saving, output NONE." + manifestSection + "\n\n"
                + "Conversation:\n" + sb
        );

        BlockingQueue<StreamEvent> events = client.stream(extractConv, null);
        var result = new StringBuilder();
        try {
            while (true) {
                StreamEvent event = events.take();
                if (event instanceof StreamEvent.TextDelta td) {
                    result.append(td.text());
                } else if (event instanceof StreamEvent.StreamEnd || event instanceof StreamEvent.Error) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String output = result.toString().trim();
        if (output.isEmpty() || output.equals("NONE") || !output.contains("MEMORY_NAME:")) return;

        // 解析 MEMORY_NAME/TYPE/DESC/BODY 格式
        for (String block : output.split("---")) {
            if (!block.contains("MEMORY_NAME:")) continue;
            String name = extractField(block, "MEMORY_NAME");
            String type = extractField(block, "MEMORY_TYPE");
            String desc = extractField(block, "MEMORY_DESC");
            String body = extractField(block, "MEMORY_BODY");
            if (name.isEmpty() || body.isEmpty()) continue;
            if (!USER_TYPES.contains(type) && !PROJECT_TYPES.contains(type)) type = "reference";

            Path targetDir = USER_TYPES.contains(type) ? userMemDirPath : projectMemDirPath;
            writeMemoryFile(targetDir, name, type, desc, body);
        }
    }

    private static String extractField(String block, String field) {
        var m = java.util.regex.Pattern.compile(field + ":\\s*(.+?)(?:\\n|$)").matcher(block);
        return m.find() ? m.group(1).trim() : "";
    }

    /**
     * 将一条记忆写为独立的 .md 文件，并在 MEMORY.md 索引中追加指针。
     */
    private void writeMemoryFile(Path dir, String name, String type, String description, String body) {
        ensureDir(dir);
        String filename = name + ".md";
        Path filePath = dir.resolve(filename);

        String fileContent = "---\nname: %s\ndescription: %s\nmetadata:\n  type: %s\n---\n\n%s\n"
                .formatted(name, description, type, body);
        try {
            Files.writeString(filePath, fileContent);
        } catch (IOException e) {
            return;
        }

        // 更新 MEMORY.md 索引
        Path entrypoint = dir.resolve(ENTRYPOINT_NAME);
        String pointer = "- [%s](%s) — %s\n".formatted(name, filename, description);
        try {
            String existing = Files.exists(entrypoint) ? Files.readString(entrypoint) : "";
            if (!existing.contains(filename)) {
                Files.writeString(entrypoint, existing + pointer);
            }
        } catch (IOException ignored) {}
    }

    /**
     * 按 `### <type>` 分组解析 LLM 提取输出。
     * 大小写不敏感，归一化为小写。
     */
    static Map<String, String> parseTypedSections(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        String currentType = null;
        StringBuilder buf = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("### ")) {
                if (currentType != null) {
                    String body = buf.toString().trim();
                    if (!body.isEmpty()) {
                        out.merge(currentType, body, (a, b) -> a + "\n" + b);
                    }
                }
                currentType = trimmed.substring(4).trim().toLowerCase(Locale.ROOT);
                buf.setLength(0);
            } else if (currentType != null) {
                buf.append(line).append('\n');
            }
        }
        if (currentType != null) {
            String body = buf.toString().trim();
            if (!body.isEmpty()) {
                out.merge(currentType, body, (a, b) -> a + "\n" + b);
            }
        }
        return out;
    }

    // ---- Injection ----

    /**
     * 向对话注入已有的记忆内容（MEMORY.md 索引）。
     */
    public void injectMemories(ConversationManager conv) {
        String reminder = buildSystemReminder();
        if (reminder.isBlank()) {
            return;
        }
        if (conv.getMessages().isEmpty()) {
            conv.addUserMessage(reminder);
            conv.addAssistantMessage("Understood, I'll keep this context in mind.");
        }
    }

    // ---- Custom instructions ----

    /**
     * 加载指令文件：支持用户级（~/.mewcode/MEWCODE.md）、项目级（git root 到 workDir 逐层）、
     * 兼容旧版 INSTRUCTIONS.md、私有 MEWCODE.local.md，以及 @include 递归展开。
     * 委托给 {@link InstructionLoader} 实现完整的发现和展开逻辑。
     */
    public static String loadInstructions(String workDir) {
        return InstructionLoader.loadInstructions(workDir);
    }

    // ---- Helpers ----

    private static void ensureDir(Path dir) {
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
    }
}
