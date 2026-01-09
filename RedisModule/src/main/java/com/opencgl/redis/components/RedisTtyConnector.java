package com.opencgl.redis.components;

import com.techsenger.jeditermfx.core.Questioner;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.opencgl.redis.service.RedisConnectionManager;
import com.opencgl.redis.model.RedisWidgetDto;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A custom TtyConnector that simulates a basic Redis CLI shell over JediTermFX.
 */
public class RedisTtyConnector implements TtyConnector {

    private final RedisConnectionManager connectionManager;
    private final LinkedBlockingQueue<String> outQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-redis-cli");
        thread.setDaemon(true);
        return thread;
    });
    private String currentReadBuffer = null;
    private int currentReadOffset = 0;
    
    private final StringBuilder currentInput = new StringBuilder();
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private boolean isConnected = true;
    
    private final String prompt;
    
    // Auto-complete candidates
    private static final List<String> COMMON_COMMANDS = Arrays.asList(
            "GET", "SET", "DEL", "KEYS", "TYPE", "TTL", "EXPIRE",
            "HGET", "HSET", "HGETALL", "HDEL", "HKEYS", "HVALS",
            "LPUSH", "RPUSH", "LPOP", "RPOP", "LRANGE", "LLEN",
            "SADD", "SREM", "SMEMBERS", "SCARD",
            "ZADD", "ZREM", "ZRANGE", "ZCARD", "ZSCORE",
            "INFO", "PING", "DBSIZE", "FLUSHDB", "FLUSHALL",
            "SELECT", "AUTH", "CONFIG", "CLIENT", "MEMORY", "CLEAR", "HELP"
    );

    public RedisTtyConnector(RedisConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        
        String promptName = "localhost:6379";
        if (connectionManager != null && connectionManager.getConfig() != null) {
            RedisWidgetDto config = connectionManager.getConfig();
            if (config.getConnectionName() != null && !config.getConnectionName().isEmpty()) {
                promptName = config.getConnectionName();
            } else if (config.getHost() != null && !config.getHost().isEmpty() && !"localhost".equals(config.getHost())) {
                promptName = config.getHost() + ":" + config.getPort();
            } else if (config.getClusterNodes() != null && !config.getClusterNodes().isEmpty()) {
                promptName = config.getClusterNodes().split(",")[0];
            } else if (config.getSentinelNodes() != null && !config.getSentinelNodes().isEmpty()) {
                promptName = config.getSentinelNodes().split(",")[0];
            } else {
                promptName = config.getHost() + ":" + config.getPort();
            }
        }
        this.prompt = promptName + "> ";
        
        print("Welcome to Redis CLI\r\n");
        print(prompt);
    }

    @Override
    public boolean init(Questioner questioner) {
        return true;
    }

    @Override
    public void close() {
        isConnected = false;
        executor.shutdownNow();
        outQueue.add("\u0000"); // Unblock read
    }

    @Override
    public String getName() {
        return "Redis CLI";
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        if (!isConnected) return -1;
        
        if (length == 0) return 0;
        
        try {
            if (currentReadBuffer == null || currentReadOffset >= currentReadBuffer.length()) {
                currentReadBuffer = outQueue.take();
                currentReadOffset = 0;
            }
            
            if (!isConnected || currentReadBuffer.equals("\u0000")) return -1;
            
            int toCopy = Math.min(length, currentReadBuffer.length() - currentReadOffset);
            currentReadBuffer.getChars(currentReadOffset, currentReadOffset + toCopy, buf, offset);
            currentReadOffset += toCopy;
            
            if (currentReadOffset >= currentReadBuffer.length()) {
                currentReadBuffer = null;
            }
            
            return toCopy;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        write(new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public void write(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            handleChar(s.charAt(i));
        }
    }

    private int escapeState = 0; // 0=normal, 1=ESC, 2=ESC[, 3=ESC[...
    private int escapeParam = 0;
    private int cursorPos = 0; // Cursor position within currentInput
    

    private void handleChar(char c) {
        if (escapeState == 1) {
            if (c == '[' || c == 'O') escapeState = 2;
            else escapeState = 0;
            return;
        } else if (escapeState == 2) {
            if (c == 'A') handleUp();
            else if (c == 'B') handleDown();
            else if (c == 'C') handleRight();
            else if (c == 'D') handleLeft();
            else if (c == 'H') { cursorPos = 0; redrawInputLine(); } // Home
            else if (c == 'F') { cursorPos = currentInput.length(); redrawInputLine(); } // End
            else if (c >= '0' && c <= '9') {
                escapeState = 3;
                escapeParam = c - '0';
                return;
            }
            escapeState = 0;
            return;
        } else if (escapeState == 3) {
            if (c >= '0' && c <= '9') {
                escapeParam = escapeParam * 10 + (c - '0');
                return;
            } else if (c == '~') {
                if (escapeParam == 3) handleDelete(); // Forward delete
                else if (escapeParam == 1 || escapeParam == 7) { cursorPos = 0; redrawInputLine(); } // Home
                else if (escapeParam == 4 || escapeParam == 8) { cursorPos = currentInput.length(); redrawInputLine(); } // End
                escapeState = 0;
                return;
            }
            escapeState = 0;
            return;
        }

        if (c == 27) { // ESC
            escapeState = 1;
        } else if (c == '\r' || c == '\n') {
            print("\r\n");
            processCommand();
            cursorPos = 0;
        } else if (c == 127 || c == '\b') { // Backspace
            if (cursorPos > 0) {
                cursorPos--;
                currentInput.deleteCharAt(cursorPos);
                redrawInputLine();
            }
        } else if (c == 3) { // Ctrl+C
            print("^C\r\n" + prompt);
            currentInput.setLength(0);
            cursorPos = 0;
        } else if (c == '\t') { // Tab
            autoComplete();
        } else {
            // Printable
            if (c >= 32) {
                currentInput.insert(cursorPos, c);
                cursorPos++;
                redrawInputLine();
            }
        }
    }

    private void handleLeft() {
        if (cursorPos > 0) {
            cursorPos--;
            print("\u001B[D");
        }
    }

    private void handleRight() {
        if (cursorPos < currentInput.length()) {
            cursorPos++;
            print("\u001B[C");
        }
    }

    private void handleDelete() {
        if (cursorPos < currentInput.length()) {
            currentInput.deleteCharAt(cursorPos);
            redrawInputLine();
        }
    }

    private void redrawInputLine() {
        // 光标移到行首、清空行、打印 prompt 和输入、光标归位
        print("\r\u001B[K" + prompt + currentInput.toString());
        int diff = currentInput.length() - cursorPos;
        if (diff > 0) {
            print("\u001B[" + diff + "D");
        }
    }

    private void handleUp() {
        if (history.isEmpty()) return;
        if (historyIndex == -1) {
            historyIndex = history.size() - 1;
        } else if (historyIndex > 0) {
            historyIndex--;
        }
        setFromHistory(history.get(historyIndex));
    }

    private void handleDown() {
        if (history.isEmpty() || historyIndex == -1) return;
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            setFromHistory(history.get(historyIndex));
        } else {
            historyIndex = -1;
            setFromHistory("");
        }
    }

    private void setFromHistory(String text) {
        currentInput.setLength(0);
        currentInput.append(text);
        cursorPos = currentInput.length();
        redrawInputLine();
    }

    @Override
    public void resize(Dimension termSize, Dimension pixelSize) {
        // Nothing to do for simple shell
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public int waitFor() throws InterruptedException {
        while (isConnected) {
            Thread.sleep(100);
        }
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        return !outQueue.isEmpty();
    }

    private void print(String text) {
        if (!isConnected) return;
        outQueue.offer(text);
    }

    private void processCommand() {
        String cmd = currentInput.toString().trim();
        currentInput.setLength(0);

        if (cmd.isEmpty()) {
            print(prompt);
            return;
        }

        if (!history.isEmpty() && history.get(history.size() - 1).equals(cmd)) {
            // avoid duplicate consecutive history
        } else {
            history.add(cmd);
        }
        historyIndex = history.size();

        if ("clear".equalsIgnoreCase(cmd)) {
            // ANSI escape to clear screen and go to top left
            print("\033[H\033[2J");
            print(prompt);
            return;
        }

        if ("help".equalsIgnoreCase(cmd)) {
            print("Redis CLI Help\r\n");
            print("--------------------------------------------------\r\n");
            print("  GET <key>        - Get the value of a key\r\n");
            print("  SET <key> <val>  - Set the value of a key\r\n");
            print("  DEL <key>        - Delete a key\r\n");
            print("  KEYS <pattern>   - Find all keys matching the given pattern\r\n");
            print("  HGETALL <key>    - Get all fields and values in a hash\r\n");
            print("  INFO             - Get information and statistics about the server\r\n");
            print("  PING             - Ping the server\r\n");
            print("  CLEAR            - Clear the terminal screen\r\n");
            print("--------------------------------------------------\r\n");
            print("Tab key supports auto-completion for common commands.\r\n");
            print(prompt);
            return;
        }

        executeRedisCommand(cmd);
    }

    private void executeRedisCommand(String command) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            print("Error: Not connected to Redis.\r\n");
            print(prompt);
            return;
        }

        executor.execute(() -> {
            try {
                String result = connectionManager.executeCommand(command);
                // Convert \n to \r\n for raw terminal display
                String displayResult = result.replace("\r", "").replace("\n", "\r\n");
                if (!displayResult.endsWith("\r\n")) {
                    displayResult += "\r\n";
                }
                print(displayResult);
            } catch (Exception e) {
                print("Error: " + e.getMessage() + "\r\n");
            } finally {
                print(prompt);
            }
        });
    }

    private void autoComplete() {
        String input = currentInput.toString();
        // 如果输入中包含空格，说明正在输入参数，不再全词补全命令
        if (input.contains(" ")) return;
        
        String prefix = input.toUpperCase();
        if (prefix.isEmpty()) return;

        for (String cmd : COMMON_COMMANDS) {
            if (cmd.startsWith(prefix)) {
                // 回退删除屏幕上已经输入的小写字符
                for (int i = 0; i < currentInput.length(); i++) {
                    print("\b \b");
                }
                
                String fullCmd = cmd + " ";
                currentInput.setLength(0);
                currentInput.append(fullCmd); // 替换为完整的大写命令
                cursorPos = currentInput.length(); // Fix: Update cursorPos!
                print(fullCmd); // 打印新的全命令
                break;
            }
        }
    }
    
    // To handle up/down, we can inspect bytes directly in overridden write(byte[])
}
