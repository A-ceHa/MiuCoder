// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com

package com.mewcode.skill;

import com.mewcode.skill.SkillCatalog.Skill;

import java.util.*;

/**
 * 内置 skill 加载器。
 * 当前版本不包含任何内置 skill，所有 skill 通过用户目录或项目目录加载。
 */
public final class BuiltinSkills {

    private BuiltinSkills() {}

    /**
     * 返回空列表——不再内置任何 skill。
     */
    public static List<Skill> load() {
        return List.of();
    }
}
