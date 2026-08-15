// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com

package com.mewcode.remote;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 内嵌的 Web UI 前端 HTML 页面。
 * 内容完全复制自 Go 版 internal/remote/web.go 的 indexHTML 常量，
 * 存储在 resources/com/mewcode/remote/index.html 中，启动时加载。
 */
public final class WebContent {

    private WebContent() {}

    /** 完整的 HTML 页面内容，包含 CSS + JavaScript */
    public static final String INDEX_HTML;

    static {
        try (InputStream is = WebContent.class.getResourceAsStream("index.html")) {
            if (is == null) {
                throw new RuntimeException("index.html resource not found");
            }
            INDEX_HTML = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load index.html", e);
        }
    }
}
