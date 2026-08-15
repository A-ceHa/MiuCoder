// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com

package com.mewcode.tui.tea;

// 鼠标事件，目前只关心滚轮上下
public class MouseMessage implements Message {

    public enum MouseButton {
        MouseButtonWheelUp,
        MouseButtonWheelDown,
        OTHER
    }

    private final MouseButton button;

    public MouseMessage(MouseButton button) {
        this.button = button;
    }

    public MouseButton getButton() {
        return button;
    }
}
