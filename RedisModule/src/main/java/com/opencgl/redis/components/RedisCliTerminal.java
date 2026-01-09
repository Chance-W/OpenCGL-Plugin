package com.opencgl.redis.components;

import com.opencgl.redis.i18n.I18N;
import com.opencgl.redis.service.RedisConnectionManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis CLI 终端组件
 */
public class RedisCliTerminal extends VBox {

    private final TextArea outputArea;
    private final TextField inputField;
    private final RedisConnectionManager connectionManager;

    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

    // 常用命令自动补全
    private static final String[] COMMON_COMMANDS = {
            "GET", "SET", "DEL", "KEYS", "TYPE", "TTL", "EXPIRE",
            "HGET", "HSET", "HGETALL", "HDEL", "HKEYS", "HVALS",
            "LPUSH", "RPUSH", "LPOP", "RPOP", "LRANGE", "LLEN",
            "SADD", "SREM", "SMEMBERS", "SCARD",
            "ZADD", "ZREM", "ZRANGE", "ZCARD", "ZSCORE",
            "INFO", "PING", "DBSIZE", "FLUSHDB", "FLUSHALL",
            "SELECT", "AUTH", "CONFIG", "CLIENT", "MEMORY"
    };

    public RedisCliTerminal(RedisConnectionManager connectionManager) {
        this.connectionManager = connectionManager;

        setSpacing(0);
        setStyle("-fx-background-color: #1e1e1e;");

        // 输出区域
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setStyle(
                "-fx-control-inner-background: #1e1e1e; " +
                        "-fx-text-fill: #00ff00; " +
                        "-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                        "-fx-font-size: 13px;");
        VBox.setVgrow(outputArea, Priority.ALWAYS);

        // 欢迎信息
        appendOutput(I18N.get("message.cli_welcome") + "\n");
        appendOutput(I18N.get("message.cli_help_tip") + "\n");
        appendOutput("----------------------------------------\n\n");

        // 输入区域
        HBox inputBox = new HBox(8);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(8, 12, 8, 12));
        inputBox.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #3d3d3d; -fx-border-width: 1 0 0 0;");

        Label promptLabel = new Label(I18N.get("label.redis_prompt"));
        promptLabel.setStyle("-fx-text-fill: #00ff00; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold;");

        inputField = new TextField();
        inputField.setStyle(
                "-fx-background-color: #2d2d2d; " +
                        "-fx-border-color: transparent; " +
                        "-fx-text-fill: #ffffff; " +
                        "-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                        "-fx-font-size: 13px;");
        inputField.setPromptText(I18N.get("prompt.input_command"));
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button executeBtn = new Button(I18N.get("button.execute"));
        executeBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white;");
        executeBtn.setOnAction(e -> executeCommand());

        Button clearBtn = new Button(I18N.get("button.clear"));
        clearBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");
        clearBtn.setOnAction(e -> outputArea.clear());

        inputBox.getChildren().addAll(promptLabel, inputField, executeBtn, clearBtn);

        // 键盘事件
        inputField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                executeCommand();
            } else if (event.getCode() == KeyCode.UP) {
                navigateHistory(-1);
            } else if (event.getCode() == KeyCode.DOWN) {
                navigateHistory(1);
            } else if (event.getCode() == KeyCode.TAB) {
                autoComplete();
                event.consume();
            }
        });

        getChildren().addAll(outputArea, inputBox);
    }

    private void executeCommand() {
        String command = inputField.getText().trim();
        if (command.isEmpty()) {
            return;
        }

        // 添加到历史
        commandHistory.add(command);
        historyIndex = commandHistory.size();

        // 显示命令
        appendOutput(I18N.get("label.redis_prompt") + " " + command + "\n");

        // 清空输入
        inputField.clear();

        // 特殊命令处理
        if ("help".equalsIgnoreCase(command)) {
            showHelp();
            return;
        }
        if ("clear".equalsIgnoreCase(command)) {
            outputArea.clear();
            return;
        }
        if ("history".equalsIgnoreCase(command)) {
            showHistory();
            return;
        }

        // 执行 Redis 命令
        if (connectionManager == null || !connectionManager.isConnected()) {
            appendOutput(I18N.get("message.error_not_connected") + "\n\n");
            return;
        }

        // 异步执行
        new Thread(() -> {
            String result = connectionManager.executeCommand(command);
            Platform.runLater(() -> {
                appendOutput(result + "\n\n");
            });
        }).start();
    }

    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) {
            return;
        }

        historyIndex += direction;

        if (historyIndex < 0) {
            historyIndex = 0;
        } else if (historyIndex >= commandHistory.size()) {
            historyIndex = commandHistory.size();
            inputField.clear();
            return;
        }

        inputField.setText(commandHistory.get(historyIndex));
        inputField.positionCaret(inputField.getText().length());
    }

    private void autoComplete() {
        String prefix = inputField.getText().toUpperCase();
        if (prefix.isEmpty()) {
            return;
        }

        for (String cmd : COMMON_COMMANDS) {
            if (cmd.startsWith(prefix)) {
                inputField.setText(cmd + " ");
                inputField.positionCaret(inputField.getText().length());
                break;
            }
        }
    }

    private void showHelp() {
        appendOutput(I18N.get("message.available_commands") + "\n");
        appendOutput("  " + I18N.get("message.help.command") + "\n");
        appendOutput("  " + I18N.get("message.help.clear") + "\n");
        appendOutput("  " + I18N.get("message.help.history") + "\n");
        appendOutput("\n" + I18N.get("message.common_redis_commands") + "\n");
        appendOutput("  GET, SET, DEL, KEYS, TYPE, TTL\n");
        appendOutput("  HGET, HSET, HGETALL, HDEL\n");
        appendOutput("  LPUSH, RPUSH, LRANGE, LLEN\n");
        appendOutput("  SADD, SREM, SMEMBERS\n");
        appendOutput("  ZADD, ZRANGE, ZSCORE\n");
        appendOutput("  INFO, PING, DBSIZE\n\n");
    }

    private void showHistory() {
        appendOutput(I18N.get("message.command_history") + "\n");
        for (int i = 0; i < commandHistory.size(); i++) {
            appendOutput("  " + (i + 1) + ") " + commandHistory.get(i) + "\n");
        }
        appendOutput("\n");
    }

    private void appendOutput(String text) {
        outputArea.appendText(text);
        outputArea.setScrollTop(Double.MAX_VALUE);
    }

    public void focus() {
        inputField.requestFocus();
    }
}
