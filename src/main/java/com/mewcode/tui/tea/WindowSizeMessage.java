// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com

package com.mewcode.tui.tea;

// 终端窗口大小变化事件
public record WindowSizeMessage(int width, int height) implements Message {}
