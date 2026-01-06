package Common;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息类 - 用于表示聊天系统中的消息对象
 * @author Jimmy505-cloud
 * @date 2026-01-06
 */
public class Message implements Serializable {
    
    // 序列化版本号
    private static final long serialVersionUID = 1L;
    
    /**
     * 发送者用户名
     */
    private String sender;
    
    /**
     * 接收者用户名
     */
    private String recipient;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 消息时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 默认构造函数
     */
    public Message() {
    }
    
    /**
     * 全参构造函数
     * @param sender 发送者
     * @param recipient 接收者
     * @param content 消息内容
     * @param timestamp 时间戳
     */
    public Message(String sender, String recipient, String content, LocalDateTime timestamp) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.timestamp = timestamp;
    }
    
    // ==================== Getter 和 Setter 方法 ====================
    
    /**
     * 获取发送者
     * @return 发送者用户名
     */
    public String getSender() {
        return sender;
    }
    
    /**
     * 设置发送者
     * @param sender 发送者用户名
     */
    public void setSender(String sender) {
        this.sender = sender;
    }
    
    /**
     * 获取接收者
     * @return 接收者用户名
     */
    public String getRecipient() {
        return recipient;
    }
    
    /**
     * 设置接收者
     * @param recipient 接收者用户名
     */
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
    
    /**
     * 获取消息内容
     * @return 消息内容
     */
    public String getContent() {
        return content;
    }
    
    /**
     * 设置消息内容
     * @param content 消息内容
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * 获取时间戳
     * @return 消息时间戳
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * 设置时间戳
     * @param timestamp 消息时间戳
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    // ==================== 其他方法 ====================
    
    /**
     * 返回消息的字符串表示
     * @return 格式化的消息字符串
     */
    @Override
    public String toString() {
        return "Message{" +
                "sender='" + sender + '\'' +
                ", recipient='" + recipient + '\'' +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
