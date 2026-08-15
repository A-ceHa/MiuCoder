// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com

package com.mewcode.permission;

import com.mewcode.tool.Tool;
import com.mewcode.tool.ToolCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PermissionCheckerTest {

    private static final Tool BASH_TOOL = new Tool() {
        @Override public String name() { return "Bash"; }
        @Override public String description() { return ""; }
        @Override public ToolCategory category() { return ToolCategory.COMMAND; }
        @Override public Map<String, Object> schema() { return Map.of(); }
        @Override public com.mewcode.tool.ToolResult execute(Map<String, Object> args) { return new com.mewcode.tool.ToolResult("", false); }
    };

    @Test
    void sandboxAutoAllowRespectsCompoundDeny(@TempDir Path tmpDir) throws IOException {
        Path rulesDir = tmpDir.resolve(".mewcode");
        Files.createDirectories(rulesDir);
        Files.writeString(rulesDir.resolve("permissions.yaml"),
                "- rule: \"Bash(rm -rf /)\"\n  effect: deny\n");

        PermissionChecker checker = new PermissionChecker(PermissionMode.DEFAULT, tmpDir);
        checker.setSandboxEnabled(true);

        var result = checker.check(BASH_TOOL, Map.of("command", "echo ok && rm -rf /"));
        assertEquals(PermissionMode.Decision.DENY, result.decision(),
                "compound command with denied subcommand should be deny");
    }

    @Test
    void sandboxAutoAllowAllowsSafeCommand(@TempDir Path tmpDir) throws IOException {
        Path rulesDir = tmpDir.resolve(".mewcode");
        Files.createDirectories(rulesDir);
        Files.writeString(rulesDir.resolve("permissions.yaml"),
                "- rule: \"Bash(rm -rf /)\"\n  effect: deny\n");

        PermissionChecker checker = new PermissionChecker(PermissionMode.DEFAULT, tmpDir);
        checker.setSandboxEnabled(true);

        var result = checker.check(BASH_TOOL, Map.of("command", "go test ./..."));
        assertEquals(PermissionMode.Decision.ALLOW, result.decision(),
                "safe command with sandbox should be allow");
    }

    @Test
    void sandboxAutoAllowRespectsAskRule(@TempDir Path tmpDir) throws IOException {
        Path rulesDir = tmpDir.resolve(".mewcode");
        Files.createDirectories(rulesDir);
        Files.writeString(rulesDir.resolve("permissions.yaml"),
                "- rule: \"Bash(git push origin main)\"\n  effect: ask\n");

        PermissionChecker checker = new PermissionChecker(PermissionMode.DEFAULT, tmpDir);
        checker.setSandboxEnabled(true);

        var result = checker.check(BASH_TOOL, Map.of("command", "git push origin main"));
        assertEquals(PermissionMode.Decision.ASK, result.decision(),
                "ask rule should not be overridden by sandbox");
    }
}
