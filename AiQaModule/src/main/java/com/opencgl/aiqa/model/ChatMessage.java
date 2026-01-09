package com.opencgl.aiqa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天消息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    public enum Role {
        SYSTEM, USER, ASSISTANT
    }
    
    private Role role;
    private String content;
    
    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }
    
    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }
    
    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}
