package Server;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 消息处理器类
 * 负责处理客户端连接、消息接收、处理和转发
 * 每个连接客户端都会有一个对应的MessageHandler实例
 */
public class MessageHandler implements Runnable {
    
    // 客户端socket连接
    private Socket socket;
    
    // 输入流，用于接收来自客户端的消息
    private BufferedReader inputStream;
    
    // 输出流，用于向客户端发送消息
    private PrintWriter outputStream;
    
    // 服务器引用，用于访问消息分发和用户管理功能
    private ChatServer server;
    
    // 客户端用户名
    private String username;
    
    // 客户端是否已认证
    private boolean isAuthenticated;
    
    // 消息处理线程是否运行中
    private boolean isRunning;
    
    // 日期时间格式化器
    private static final DateTimeFormatter dateFormatter = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 构造函数
     * @param socket 客户端socket连接
     * @param server ChatServer引用
     */
    public MessageHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        this.isAuthenticated = false;
        this.isRunning = true;
        this.username = null;
        
        try {
            // 初始化输入输出流
            this.inputStream = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );
            this.outputStream = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream()),
                true  // 自动刷新
            );
        } catch (IOException e) {
            System.out.println("初始化流失败: " + e.getMessage());
            isRunning = false;
        }
    }
    
    /**
     * 运行方法 - 处理客户端连接的主逻辑
     * 在服务器线程池中执行
     */
    @Override
    public void run() {
        try {
            // 发送欢迎消息
            sendMessage("欢迎连接到聊天系统");
            
            String receivedMessage;
            // 持续监听来自客户端的消息
            while (isRunning && (receivedMessage = inputStream.readLine()) != null) {
                // 处理接收到的消息
                handleMessage(receivedMessage);
            }
        } catch (IOException e) {
            System.out.println("消息接收异常: " + e.getMessage());
        } finally {
            // 连接关闭，清理资源
            closeConnection();
        }
    }
    
    /**
     * 处理接收到的消息
     * 根据消息类型执行不同的操作
     * @param message 接收到的消息
     */
    private void handleMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        
        // 解析消息格式：命令:参数1:参数2...
        String[] parts = message.split(":");
        String command = parts[0].trim().toUpperCase();
        
        switch (command) {
            // 用户登录命令
            case "LOGIN":
                handleLogin(parts);
                break;
                
            // 发送私聊消息
            case "PRIVATE":
                handlePrivateMessage(parts);
                break;
                
            // 发送群聊消息
            case "PUBLIC":
                handlePublicMessage(parts);
                break;
                
            // 用户登出命令
            case "LOGOUT":
                handleLogout();
                break;
                
            // 获取在线用户列表
            case "LIST":
                handleListUsers();
                break;
                
            // 心跳/保活消息
            case "PING":
                handlePing();
                break;
                
            // 未知命令
            default:
                sendMessage("ERROR:未知命令");
        }
    }
    
    /**
     * 处理登录请求
     * 格式: LOGIN:username
     * @param parts 分割后的消息部分
     */
    private void handleLogin(String[] parts) {
        // 检查是否已登录
        if (isAuthenticated) {
            sendMessage("ERROR:已登录状态，请先登出");
            return;
        }
        
        // 验证参数数量
        if (parts.length < 2) {
            sendMessage("ERROR:登录格式错误，请使用 LOGIN:用户名");
            return;
        }
        
        String requestedUsername = parts[1].trim();
        
        // 验证用户名有效性
        if (requestedUsername.isEmpty() || requestedUsername.length() > 50) {
            sendMessage("ERROR:用户名长度必须在1-50个字符之间");
            return;
        }
        
        // 检查用户名是否已被使用
        if (server.isUserOnline(requestedUsername)) {
            sendMessage("ERROR:用户名已被使用");
            return;
        }
        
        // 认证成功
        this.username = requestedUsername;
        this.isAuthenticated = true;
        
        // 通知服务器有新用户上线
        server.userOnline(this);
        
        // 向客户端发送成功响应
        sendMessage("SUCCESS:登录成功，用户名: " + username);
        
        // 广播用户上线消息
        broadcastSystemMessage(username + " 已上线");
        
        System.out.println("[" + getCurrentTime() + "] 用户 " + username + " 登录成功");
    }
    
    /**
     * 处理群聊消息
     * 格式: PUBLIC:消息内容
     * @param parts 分割后的消息部分
     */
    private void handlePublicMessage(String[] parts) {
        // 验证用户���认证
        if (!isAuthenticated) {
            sendMessage("ERROR:请先登录");
            return;
        }
        
        // 验证参数
        if (parts.length < 2) {
            sendMessage("ERROR:消息格式错误");
            return;
        }
        
        // 重新组装消息内容（防止消息中包含冒号）
        StringBuilder messageContent = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (i > 1) messageContent.append(":");
            messageContent.append(parts[i]);
        }
        
        // 格式化消息
        String formattedMessage = formatMessage(username, messageContent.toString(), "public");
        
        // 广播群聊消息到所有在线用户
        server.broadcastMessage(formattedMessage);
        
        System.out.println("[" + getCurrentTime() + "] " + username + " 发送群聊: " + messageContent);
    }
    
    /**
     * 处理私聊消息
     * 格式: PRIVATE:目标用户名:消息内容
     * @param parts 分割后的消息部分
     */
    private void handlePrivateMessage(String[] parts) {
        // 验证用户已认证
        if (!isAuthenticated) {
            sendMessage("ERROR:请先登录");
            return;
        }
        
        // 验证参数
        if (parts.length < 3) {
            sendMessage("ERROR:私聊格式错误，请使用 PRIVATE:目标用户名:消息内容");
            return;
        }
        
        String targetUsername = parts[1].trim();
        
        // 验证目标用户名不为空
        if (targetUsername.isEmpty()) {
            sendMessage("ERROR:目标用户名不能为空");
            return;
        }
        
        // 防止自己给自己发送私聊
        if (targetUsername.equals(username)) {
            sendMessage("ERROR:不能给自己发送私聊消息");
            return;
        }
        
        // 重新组装消息内容
        StringBuilder messageContent = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            if (i > 2) messageContent.append(":");
            messageContent.append(parts[i]);
        }
        
        // 格式化私聊消息
        String formattedMessage = formatMessage(username, messageContent.toString(), "private");
        
        // 尝试发送私聊消息
        boolean sent = server.sendPrivateMessage(targetUsername, formattedMessage);
        
        if (sent) {
            // 向发送方确认消息已发送
            sendMessage("INFO:消息已发送给 " + targetUsername);
            System.out.println("[" + getCurrentTime() + "] " + username + " 私聊给 " + 
                             targetUsername + ": " + messageContent);
        } else {
            // 用户不在线
            sendMessage("ERROR:用户 " + targetUsername + " 不在线或不存在");
        }
    }
    
    /**
     * 处理登出请求
     */
    private void handleLogout() {
        if (!isAuthenticated) {
            sendMessage("ERROR:未登录状态");
            return;
        }
        
        String logoutUser = username;
        broadcastSystemMessage(logoutUser + " 已离线");
        
        // 通知服务器用户离线
        server.userOffline(this);
        
        // 关闭连接
        isRunning = false;
        closeConnection();
        
        System.out.println("[" + getCurrentTime() + "] 用户 " + logoutUser + " 登出");
    }
    
    /**
     * 处理在线用户列表请求
     * 格式: LIST
     */
    private void handleListUsers() {
        if (!isAuthenticated) {
            sendMessage("ERROR:请先登录");
            return;
        }
        
        // 获取在线用户列表
        List<String> onlineUsers = server.getOnlineUsers();
        
        if (onlineUsers.isEmpty()) {
            sendMessage("LIST:暂无其他在线用户");
        } else {
            // 格式化用户列表
            StringBuilder userList = new StringBuilder("LIST:");
            for (int i = 0; i < onlineUsers.size(); i++) {
                if (i > 0) userList.append(",");
                userList.append(onlineUsers.get(i));
            }
            sendMessage(userList.toString());
        }
    }
    
    /**
     * 处理心跳/保活消息
     * 格式: PING
     */
    private void handlePing() {
        sendMessage("PONG");
    }
    
    /**
     * 向客户端发送消息
     * @param message 要发送的消息
     */
    public void sendMessage(String message) {
        if (outputStream != null && !outputStream.checkError()) {
            outputStream.println(message);
        }
    }
    
    /**
     * 广播系统消息（用户上线/离线等）
     * @param systemMessage 系统消息内容
     */
    private void broadcastSystemMessage(String systemMessage) {
        String formatted = "[系统] " + getCurrentTime() + " - " + systemMessage;
        server.broadcastMessage(formatted);
    }
    
    /**
     * 格式化消息
     * @param sender 发送者用户名
     * @param content 消息内容
     * @param type 消息类型 (public/private)
     * @return 格式化后的消息
     */
    private String formatMessage(String sender, String content, String type) {
        String timeStamp = getCurrentTime();
        
        if ("private".equals(type)) {
            return "[私聊] " + timeStamp + " - " + sender + ": " + content;
        } else {
            return "[群聊] " + timeStamp + " - " + sender + ": " + content;
        }
    }
    
    /**
     * 获取当前时间戳
     * @return 格式化的时间字符串
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(dateFormatter);
    }
    
    /**
     * 关闭连接并释放资源
     */
    private void closeConnection() {
        try {
            // 关闭输入输出流
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            // 关闭socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            System.out.println("[" + getCurrentTime() + "] 连接已关闭");
        } catch (IOException e) {
            System.out.println("关闭连接异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取用户名
     * @return 当前处理器关联的用户名
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * 获取认证状态
     * @return 用户是否已认证
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
    
    /**
     * 检查连接是否有效
     * @return 连接是否运行中
     */
    public boolean isConnectionActive() {
        return isRunning && socket != null && !socket.isClosed();
    }
}
