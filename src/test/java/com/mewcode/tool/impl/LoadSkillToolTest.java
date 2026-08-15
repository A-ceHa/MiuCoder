// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com

package com.mewcode.tool.impl;

import com.mewcode.skill.SkillCatalog;
import com.mewcode.skill.SkillCatalog.Skill;
import com.mewcode.skill.SkillCatalog.SkillMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoadSkillToolTest {

    private LoadSkillTool tool;
    private SkillCatalog catalog;
    private List<String> activatedSkills;

    @BeforeEach
    void setUp() {
        tool = new LoadSkillTool();
        catalog = new SkillCatalog();
        activatedSkills = new ArrayList<>();

        tool.setCatalog(catalog);
        tool.setOnActivate((name, body) -> activatedSkills.add(name + ":" + body));
    }

    @Test
    void 正常激活skill返回完整SOP() {
        var meta = new SkillMeta("commit", "生成规范的 commit message", null, List.of(), "inline", null, null);
        var skill = new Skill(meta, "请按照以下步骤生成 commit message...", null, true);
        catalog.register(skill);

        var result = tool.execute(Map.of("name", "commit"));

        assertFalse(result.isError());
        assertTrue(result.output().contains("# Skill: commit"));
        assertTrue(result.output().contains("请按照以下步骤生成 commit message"));
        assertEquals(1, activatedSkills.size());
        assertTrue(activatedSkills.get(0).startsWith("commit:"));
    }

    @Test
    void name为空返回错误() {
        var result = tool.execute(Map.of("name", ""));
        assertTrue(result.isError());
        assertTrue(result.output().contains("name is required"));
    }

    @Test
    void 不存在的skill返回错误() {
        var result = tool.execute(Map.of("name", "nonexistent"));
        assertTrue(result.isError());
        assertTrue(result.output().contains("unknown skill"));
    }

    @Test
    void body为空的skill返回错误() {
        var meta = new SkillMeta("empty-skill", "空 skill", null, List.of(), "inline", null, null);
        var skill = new Skill(meta, "", null, true);
        catalog.register(skill);

        var result = tool.execute(Map.of("name", "empty-skill"));
        assertTrue(result.isError());
        assertTrue(result.output().contains("empty body"));
    }

    @Test
    void catalog未设置返回错误() {
        var noWireTool = new LoadSkillTool();
        var result = noWireTool.execute(Map.of("name", "commit"));
        assertTrue(result.isError());
        assertTrue(result.output().contains("not initialized"));
    }

    @Test
    void 未设置onActivate回调也能正常返回结果() {
        var noCallbackTool = new LoadSkillTool();
        noCallbackTool.setCatalog(catalog);

        var meta = new SkillMeta("review", "代码审查", null, List.of(), "inline", null, null);
        var skill = new Skill(meta, "审查步骤...", null, true);
        catalog.register(skill);

        var result = noCallbackTool.execute(Map.of("name", "review"));
        assertFalse(result.isError());
        assertTrue(result.output().contains("审查步骤"));
    }
}
