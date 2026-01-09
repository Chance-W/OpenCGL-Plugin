package com.opencgl.template5;

import com.opencgl.api.PluginUI;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * 插件开发模板 5：完全 Swing 集成的基础样例。
 * type() = SWING，createView() 返回 JComponent，由主程序通过 SwingNode 嵌入。
 * 不接入主题与语言切换，仅演示最小可运行结构。
 */
public class TemplatePlugin5Swing implements PluginUI {

    private JPanel panel;

    @Override
    public String directoryName() {
        return "插件开发模板";
    }

    @Override
    public String name() {
        return "Template 5 - Swing";
    }

    @Override
    public URL iconPath() {
        return null;
    }

    @Override
    public UIType type() {
        return UIType.SWING;
    }

    @Override
    public Object createView() {
        if (panel != null) {
            return panel;
        }
        panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Swing 插件模板 5 - 基础样例");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField input = new JTextField(20);
        JButton btn = new JButton("确定");
        btn.addActionListener(e -> JOptionPane.showMessageDialog(panel, "输入: " + input.getText()));
        center.add(new JLabel("输入:"));
        center.add(input);
        center.add(btn);
        panel.add(center, BorderLayout.CENTER);

        JLabel footer = new JLabel("纯 Swing 组件，由主程序 SwingNode 嵌入。");
        footer.setForeground(Color.GRAY);
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    @Override
    public void dispose() {
        panel = null;
    }
}
