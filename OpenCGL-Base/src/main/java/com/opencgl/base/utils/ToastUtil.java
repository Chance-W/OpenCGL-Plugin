package com.opencgl.base.utils;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * 通用 Toast 状态提示工具类 (Enhanced Version)
 * <p>
 * 提供流式 API 构建器 {@link ToastBuilder}，动态向 {@link StackPane} 等容器注入浮动提示。
 * </p>
 *
 * <h3>功能特性：</h3>
 * <ul>
 *     <li><b>多方向定位</b>：支持 上/下/左/右 四个方向 ({@link Position})。</li>
 *     <li><b>贴边模式 (Edge Docking)</b>：移除默认外边距，使 Toast 紧贴容器边缘。</li>
 *     <li><b>全宽模式 (Full Width)</b>：横向撑满父容器宽度 (如果是上下方向)。</li>
 *     <li><b>持久化模式 (Sticky/Persistent)</b>：不会自动消失，提供关闭按钮（或点击关闭）供用户手动关闭。</li>
 * </ul>
 *
 * <h3>未来可提升点分析 (Analysis of Potential Improvements)：</h3>
 * <ol>
 *     <li><b>动画策略 (Animations)</b>：目前仅支持淡入淡出 (Fade)。未来可支持 Slide-In (从边缘滑入)、Scale (缩放弹出) 等，增强动态视觉体验。</li>
 *     <li><b>多 Toast 堆叠 (Stacking)</b>：当前实现是覆盖式或重叠式。如果业务需要同时显示多条消息，可以引入 {@code VBox} 容器队列管理，实现类似系统通知的向下推挤效果。</li>
 *     <li><b>图标支持 (Iconography)</b>：可以在文本左侧自动根据类型 (Success/Error) 添加对号或叉号图标，增强语义识别度。</li>
 *     <li><b>交互按钮 (Action Button)</b>：对于持久化 Toast，支持添加 "撤销"、"重试" 等操作按钮，实现 Snackbar 的功能。</li>
 *     <li><b>样式主题化 (Theming)</b>：目前颜色硬编码。未来应支持从 CSS 变量读取颜色，以更好地支持动态换肤。</li>
 * </ol>
 *
 * @author OpenCGL Team
 */
public class ToastUtil {

    // ─── Constants ───────────────────────────────────────────────────────

    private static final String COLOR_SUCCESS = "#2e7d32";
    private static final String COLOR_ERROR   = "#c62828";
    private static final String COLOR_WARNING = "#e65100";
    private static final String COLOR_INFO    = "#0288d1";

    /** 基础样式：圆角、字体、内边距 (非贴边时使用) */
    private static final String BASE_STYLE_FLOATING =
            "-fx-padding: 8 20 8 20; -fx-font-size: 13px; -fx-background-radius: 4; -fx-text-fill: white; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 2);";

    /** 基础样式：无圆角 (贴边/全宽时推荐使用) */
    private static final String BASE_STYLE_DOCKED =
            "-fx-padding: 10 20 10 20; -fx-font-size: 13px; -fx-background-radius: 0; -fx-text-fill: white;";

    // ─── API Entry Points ────────────────────────────────────────────────

    /**
     * 获取一个 Toast 构建器
     *
     * @param parent 目标父容器 (推荐 StackPane)
     * @return ToastBuilder 实例
     */
    public static ToastBuilder builder(Pane parent) {
        return new ToastBuilder(parent);
    }

    /**
     * 快捷调用：底部展示成功信息
     */
    public static void show(Pane parent, String message, boolean success) {
        builder(parent).message(message).success(success).show();
    }

    /**
     * 快捷调用：底部展示警告信息
     */
    public static void showWarning(Pane parent, String message) {
        builder(parent).message(message).warning().show();
    }

    /**
     * 快捷调用：指定方向展示信息
     */
    public static void show(Pane parent, String message, boolean success, Position position) {
        builder(parent).message(message).success(success).position(position).show();
    }

    // ─── Enums ───────────────────────────────────────────────────────────

    public enum Position {
        TOP, BOTTOM, LEFT, RIGHT, CENTER
    }

    // ─── Builder Class ───────────────────────────────────────────────────

    public static class ToastBuilder {
        private final Pane parent;
        private String message = "";
        private String bgColor = COLOR_INFO;
        private Position position = Position.BOTTOM;
        private double durationSeconds = 2.5;
        private boolean isSticky = false;       // 是否持久化（不自动消失）
        private boolean isEdgeDocked = false;   // 是否贴边（去除Margin）
        private boolean isFullWidth = false;    // 是否占满宽度

        public ToastBuilder(Pane parent) {
            this.parent = parent;
        }

        /**
         * 设置消息文本
         */
        public ToastBuilder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * 设置为成功样式 (绿色)
         */
        public ToastBuilder success() {
            this.bgColor = COLOR_SUCCESS;
            return this;
        }

        /**
         * 设置为错误样式 (红色)
         */
        public ToastBuilder error() {
            this.bgColor = COLOR_ERROR;
            return this;
        }

        /**
         * 设置为失败样式 (与 error 同义)
         */
        public ToastBuilder success(boolean isSuccess) {
            return isSuccess ? success() : error();
        }

        /**
         * 设置为警告样式 (橙色)
         */
        public ToastBuilder warning() {
            this.bgColor = COLOR_WARNING;
            return this;
        }

        /**
         * 自定义背景色 CSS 值
         */
        public ToastBuilder color(String cssColor) {
            this.bgColor = cssColor;
            return this;
        }

        /**
         * 设置弹出位置
         */
        public ToastBuilder position(Position position) {
            this.position = position;
            return this;
        }

        /**
         * 设置显示时长 (秒)。如果 <= 0，则强制视为持久化模式 (Sticky)。
         */
        public ToastBuilder duration(double seconds) {
            this.durationSeconds = seconds;
            if (seconds <= 0) {
                this.isSticky = true;
            }
            return this;
        }

        /**
         * 开启持久化/手动关闭模式。
         * <p>开启后，Toast 不会自动消失，右侧会出现关闭按钮（或支持点击关闭）。</p>
         */
        public ToastBuilder sticky(boolean sticky) {
            this.isSticky = sticky;
            return this;
        }

        /**
         * 开启贴边模式。
         * <p>开启后，去除默认的 20px 外边距，Toast 紧贴容器边缘。</p>
         */
        public ToastBuilder edgeDocked(boolean docked) {
            this.isEdgeDocked = docked;
            return this;
        }

        /**
         * 开启全宽模式。
         * <p>开启后，Toast 将尝试撑满容器的宽度 (Top/Bottom) 或高度 (Left/Right)。通常配合 edgeDocked 使用效果更佳。</p>
         */
        public ToastBuilder fullWidth(boolean full) {
            this.isFullWidth = full;
            return this;
        }

        /**
         * 构建并显示 Toast
         */
        public void show() {
            if (parent == null) return;

            // 1. 创建核心内容 Label
            Label msgLabel = new Label(message);
            msgLabel.setWrapText(true);
            msgLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            // 字体样式在 Container 统一设置，这里仅负责文本
            
            // 2. 创建容器 (HBox 用于布局 文本 + 关闭按钮)
            HBox valueContainer = new HBox(10);
            valueContainer.setAlignment(Pos.CENTER_LEFT);
            valueContainer.setOpacity(0.0); // 初始透明，用于淡入动画

            // 样式选择：贴边模式通常没有圆角
            String styleBase = isEdgeDocked ? BASE_STYLE_DOCKED : BASE_STYLE_FLOATING;
            valueContainer.setStyle(styleBase + "-fx-background-color: " + bgColor + ";");

            // 3. 处理全宽逻辑
            if (isFullWidth) {
                valueContainer.setMaxWidth(Double.MAX_VALUE);
                valueContainer.setMaxHeight(Region.USE_PREF_SIZE);
                HBox.setHgrow(msgLabel, Priority.ALWAYS); // 文本撑开
            } else {
                valueContainer.setMaxWidth(Region.USE_PREF_SIZE);
                valueContainer.setMaxHeight(Region.USE_PREF_SIZE);
            }

            // 4. 处理持久化/关闭逻辑
            if (isSticky) {
                valueContainer.getChildren().add(msgLabel);
                
                // 添加关闭按钮 (简单的 X 文本或者图标)
                Label closeBtn = new Label("✕");
                closeBtn.setStyle("-fx-cursor: hand; -fx-text-fill: rgba(255,255,255,0.7); -fx-font-weight: bold; padding: 0 5;");
                closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-cursor: hand; -fx-text-fill: white; -fx-font-weight: bold; padding: 0 5;"));
                closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-cursor: hand; -fx-text-fill: rgba(255,255,255,0.7); -fx-font-weight: bold; padding: 0 5;"));
                
                // 点击关闭逻辑
                closeBtn.setOnMouseClicked(e -> fadeOutAndRemove(valueContainer, parent));
                valueContainer.getChildren().add(closeBtn);
            } else {
                // 非持久化，纯文本
                msgLabel.setAlignment(Pos.CENTER);
                if (isFullWidth) msgLabel.setMaxWidth(Double.MAX_VALUE);
                valueContainer.getChildren().add(msgLabel);
            }

            // 5. 定位逻辑 (StackPane)
            if (parent instanceof StackPane) {
                Pos align = switch (position) {
                    case TOP -> Pos.TOP_CENTER;
                    case LEFT -> Pos.CENTER_LEFT;
                    case RIGHT -> Pos.CENTER_RIGHT;
                    case CENTER -> Pos.CENTER;
                    default -> Pos.BOTTOM_CENTER;
                };
                StackPane.setAlignment(valueContainer, align);
                StackPane.setMargin(valueContainer, getMargins());
            }

            // 6. 添加到父容器
            parent.getChildren().add(valueContainer);

            // 7. 动画逻辑
            fadeIn(valueContainer);

            if (!isSticky) {
                PauseTransition pause = new PauseTransition(Duration.seconds(durationSeconds));
                pause.setOnFinished(e -> fadeOutAndRemove(valueContainer, parent));
                pause.play();
            }
        }

        private Insets getMargins() {
            if (isEdgeDocked) return Insets.EMPTY;
            // 默认浮动边距
            return switch (position) {
                case TOP -> new Insets(20, 20, 0, 20);
                case LEFT -> new Insets(0, 0, 0, 20);
                case RIGHT -> new Insets(0, 20, 0, 0);
                case CENTER -> Insets.EMPTY;
                default -> new Insets(0, 20, 20, 20); // BOTTOM
            };
        }

        private void fadeIn(Node node) {
            FadeTransition ft = new FadeTransition(Duration.millis(300), node);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();
        }

        private void fadeOutAndRemove(Node node, Pane parent) {
            FadeTransition ft = new FadeTransition(Duration.millis(300), node);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.setOnFinished(e -> {
                if (parent.getChildren().contains(node)) {
                    parent.getChildren().remove(node);
                }
            });
            ft.play();
        }
    }
}
