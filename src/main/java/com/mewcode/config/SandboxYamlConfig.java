// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com

package com.mewcode.config;

// 沙箱 YAML 配置节点，对应 config.yaml 中的 sandbox: 块
public class SandboxYamlConfig {
    private boolean enabled;
    private boolean autoAllow = true;
    private boolean networkEnabled;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoAllow() { return autoAllow; }
    public void setAutoAllow(boolean autoAllow) { this.autoAllow = autoAllow; }

    public boolean isNetworkEnabled() { return networkEnabled; }
    public void setNetworkEnabled(boolean networkEnabled) { this.networkEnabled = networkEnabled; }
}
