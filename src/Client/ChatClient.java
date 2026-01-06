package Client;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网络聊天系统客户端类
 * 
 * 功能描述：
 * - 与服务器建立TCP连接
 * - 处理用户登录和登出
 * - 发送和接收聊天消息
 * - 支持用户交互和消息同步
 * 
 * @author Jimmy505-cloud
 * @version 1.0
 * @since 2026-01-06
 */
public class ChatClient {
    
    // ==================== 类常量 ====================
    /** 默认服务器地址 */
    private static final String DEFAULT_SERVER_HOST = "localhost";
    
    /** 默认服务器端口 */
    private static final int DEFAULT_SERVER_PORT = 9999;
    
    /** 日期时间格式化器 */
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // ==================== 实例变量 ====================
    
    /** 与服务器通信的Socket连接 */
    private Socket socket;
    
    /** 发送消息的输出流 */
    private PrintWriter out;
    
    /** 接收消息的输入流 */
    private BufferedReader in;
    
    /** 服务器地址 */
    private String serverHost;
    
    /** 服务器端口 */
    private int serverPort;
    
    /** 当前登录的用户名 */
    private String currentUsername;
    
    /** 标记客户端是否已登录 */
    private AtomicBoolean isLoggedIn;
    
    /** 标记客户端是否正在运行 */
    private AtomicBoolean isRunning;
    
    /** 消息接收线程 */
    private Thread messageReceiverThread;
    
    // ==================== 构造方法 ====================
    
    /**
     * 默认构造方法
     * 使用默认的服务器地址和端口初始化客户端
     */
    public ChatClient() {
        this(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
    }
    
    /**
     * 参数化构造方法
     * 
     * @param serverHost 服务器地址
     * @param serverPort 服务器端口
     */
    public ChatClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.currentUsername = null;
        this.isLoggedIn = new AtomicBoolean(false);
        this.isRunning = new AtomicBoolean(false);
    }
    
    // ==================== 连接管理方法 ====================
    
    /**
     * 连接到服务器
     * 建立Socket连接并初始化输入输出流
     * 
     * @return 连接是否成功
     */
    public boolean connect() {
        try {
            // 创建Socket连接
            this.socket = new Socket(serverHost, serverPort);
            
            // 初始化输出流（用于发送数据）
            this.out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream()),
                true
            );
            
            // 初始化输入流（用于接收数据）
            this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );
            
            // 标记客户端正在运行
            this.isRunning.set(true);
            
            // 启动消息接收线程
            startMessageReceiver();
            
            printLog("✓ 已成功连接到服务器: " + serverHost + ":" + serverPort);
            return true;
            
        } catch (IOException e) {
            printError("✗ 连接失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 断开与服务器的连接
     * 关闭所有流和Socket连接
     */
    public void disconnect() {
        try {
            // 标记客户端停止运行
            isRunning.set(false);
            
            // 关闭登录状态
            isLoggedIn.set(false);
            
            // 关闭输入输出流
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            
            // 关闭Socket连接
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            // 等待消息接收线程结束
            if (messageReceiverThread != null && messageReceiverThread.isAlive()) {
                messageReceiverThread.join(1000);
            }
            
            printLog("✓ 已断开服务器连接");
            
        } catch (IOException | InterruptedException e) {
            printError("✗ 断开连接时出错: " + e.getMessage());
        }
    }
    
    // ==================== 用户认证方法 ====================
    
    /**
     * 用户登录
     * 向服务器发送登录请求
     * 
     * @param username 用户名
     * @param password 密码
     * @return 登录是否成功
     */
    public boolean login(String username, String password) {
        // 检查连接状态
        if (socket == null || socket.isClosed()) {
            printError("✗ 未连接到服务器，请先连接");
            return false;
        }
        
        // 检查是否已登录
        if (isLoggedIn.get()) {
            printError("✗ 您已经登录，无需重复登录");
            return false;
        }
        
        try {
            // 构造登录命令
            String loginCommand = "LOGIN:" + username + ":" + password;
            
            // 发送登录请求
            out.println(loginCommand);
            
            // 等待服务器响应
            String response = in.readLine();
            
            if (response != null && response.startsWith("LOGIN_SUCCESS:")) {
                // 提取用户名
                this.currentUsername = response.split(":")[1];
                this.isLoggedIn.set(true);
                
                printLog("✓ 登录成功！欢迎，" + currentUsername);
                return true;
                
            } else if (response != null && response.startsWith("LOGIN_FAILED:")) {
                // 提取错误信息
                String errorMsg = response.split(":", 2)[1];
                printError("✗ 登录失败: " + errorMsg);
                return false;
                
            } else {
                printError("✗ 服务器响应异常");
                return false;
            }
            
        } catch (IOException e) {
            printError("✗ 登录时发生网络错误: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 用户登出
     * 向服务器发送登出请求
     * 
     * @return 登出是否成功
     */
    public boolean logout() {
        // 检查登录状态
        if (!isLoggedIn.get()) {
            printError("✗ 您尚未登录");
            return false;
        }
        
        try {
            // 构造登出命令
            String logoutCommand = "LOGOUT:" + currentUsername;
            
            // 发送登出请求
            out.println(logoutCommand);
            
            // 等待服务器响应
            String response = in.readLine();
            
            if (response != null && response.startsWith("LOGOUT_SUCCESS")) {
                // 清空用户信息
                this.currentUsername = null;
                this.isLoggedIn.set(false);
                
                printLog("✓ 已登出");
                return true;
                
            } else {
                printError("✗ 登出失败");
                return false;
            }
            
        } catch (IOException e) {
            printError("✗ 登出时发生网络错误: " + e.getMessage());
            return false;
        }
    }
    
    // ==================== 消息发送方法 ====================
    
    /**
     * 发送聊天消息
     * 向服务器发送消息，由服务器转发给接收者
     * 
     * @param recipient 消息接收者用户名
     * @param content 消息内容
     * @return 发送是否成功
     */
    public boolean sendMessage(String recipient, String content) {
        // 检查登录状态
        if (!isLoggedIn.get()) {
            printError("✗ 请先登录");
            return false;
        }
        
        // 检查参数
        if (recipient == null || recipient.trim().isEmpty()) {
            printError("✗ 接收者不能为空");
            return false;
        }
        
        if (content == null || content.trim().isEmpty()) {
            printError("✗ 消息内容不能为空");
            return false;
        }
        
        try {
            // 获取当前时间戳
            String timestamp = LocalDateTime.now().format(FORMATTER);
            
            // 构造消息格式: MSG:sender:recipient:timestamp:content
            String message = "MSG:" + currentUsername + ":" + recipient + 
                           ":" + timestamp + ":" + content;
            
            // 发送消息
            out.println(message);
            
            printLog(">> [" + recipient + "] " + content + " (" + timestamp + ")");
            return true;
            
        } catch (Exception e) {
            printError("✗ 发送消息失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 广播消息给所有在线用户
     * 
     * @param content 消息内容
     * @return 发送是否成功
     */
    public boolean broadcastMessage(String content) {
        return sendMessage("ALL", content);
    }
    
    // ==================== 消息接收方法 ====================
    
    /**
     * 启动消息接收线程
     * 在后台线程中持续监听来自服务器的消息
     */
    private void startMessageReceiver() {
        // 创建新线程用于接收消息
        messageReceiverThread = new Thread(() -> {
            try {
                // 持续监听来自服务器的消息
                while (isRunning.get()) {
                    String message = in.readLine();
                    
                    // 如果连接断开
                    if (message == null) {
                        printError("✗ 与服务器的连接已断开");
                        isRunning.set(false);
                        isLoggedIn.set(false);
                        break;
                    }
                    
                    // 处理接收到的消息
                    processReceivedMessage(message);
                }
                
            } catch (SocketException e) {
                // Socket异常，通常表示连接已关闭
                if (isRunning.get()) {
                    printError("✗ 网络连接异常: " + e.getMessage());
                    isRunning.set(false);
                }
            } catch (IOException e) {
                // 其他IO异常
                if (isRunning.get()) {
                    printError("✗ 接收消息出错: " + e.getMessage());
                }
            }
        });
        
        // 设置为守护线程
        messageReceiverThread.setDaemon(true);
        messageReceiverThread.setName("ChatClient-MessageReceiver");
        
        // 启动线程
        messageReceiverThread.start();
    }
    
    /**
     * 处理来自服务器的消息
     * 
     * @param message 接收到的消息字符串
     */
    private void processReceivedMessage(String message) {
        try {
            // 解析消息类型
            if (message.startsWith("MSG:")) {
                // 处理聊天消息
                handleChatMessage(message);
                
            } else if (message.startsWith("SYSTEM:")) {
                // 处理系统消息
                handleSystemMessage(message);
                
            } else if (message.startsWith("NOTIFICATION:")) {
                // 处理通知消息
                handleNotification(message);
                
            } else if (message.startsWith("ERROR:")) {
                // 处理错误消息
                handleErrorMessage(message);
                
            } else {
                // 未知消息类型
                printLog("? [未知消息] " + message);
            }
            
        } catch (Exception e) {
            printError("✗ 处理消息出错: " + e.getMessage());
        }
    }
    
    /**
     * 处理聊天消息
     * 格式: MSG:sender:recipient:timestamp:content
     * 
     * @param message 消息字符串
     */
    private void handleChatMessage(String message) {
        try {
            // 分割消息各部分
            String[] parts = message.split(":", 5);
            
            if (parts.length >= 5) {
                String sender = parts[1];
                String recipient = parts[2];
                String timestamp = parts[3];
                String content = parts[4];
                
                // 输出接收到的消息
                if (recipient.equals("ALL")) {
                    // 广播消息
                    printLog("<< [全体消息] " + sender + ": " + content + 
                           " (" + timestamp + ")");
                } else {
                    // 私聊消息
                    printLog("<< [" + sender + "] " + content + 
                           " (" + timestamp + ")");
                }
            }
            
        } catch (Exception e) {
            printError("✗ 解析聊天消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理系统消息
     * 
     * @param message 消息字符串
     */
    private void handleSystemMessage(String message) {
        String content = message.substring("SYSTEM:".length());
        printLog("ℹ [系统] " + content);
    }
    
    /**
     * 处理通知消息
     * 
     * @param message 消息字符串
     */
    private void handleNotification(String message) {
        String content = message.substring("NOTIFICATION:".length());
        printLog("● [通知] " + content);
    }
    
    /**
     * 处理错误消息
     * 
     * @param message 消息字符串
     */
    private void handleErrorMessage(String message) {
        String content = message.substring("ERROR:".length());
        printError("✗ [错误] " + content);
    }
    
    // ==================== 状态查询方法 ====================
    
    /**
     * 获取当前登录状态
     * 
     * @return 是否已登录
     */
    public boolean isLoggedIn() {
        return isLoggedIn.get();
    }
    
    /**
     * 获取当前是否连接到服务器
     * 
     * @return 是否已连接
     */
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }
    
    /**
     * 获取当前登录的用户名
     * 
     * @return 用户名，如未登录则返回null
     */
    public String getCurrentUsername() {
        return currentUsername;
    }
    
    /**
     * 获取服务器地址
     * 
     * @return 服务器地址
     */
    public String getServerHost() {
        return serverHost;
    }
    
    /**
     * 获取服务器端口
     * 
     * @return 服务器端口
     */
    public int getServerPort() {
        return serverPort;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 输出普通日志信息
     * 
     * @param message 日志消息
     */
    private void printLog(String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        System.out.println("[" + timestamp + "] " + message);
    }
    
    /**
     * 输出错误日志信息
     * 
     * @param message 错误消息
     */
    private void printError(String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        System.err.println("[" + timestamp + "] " + message);
    }
    
    // ==================== 主程序 (测试) ====================
    
    /**
     * 主程序入口
     * 提供交互式命令行界面进行聊天
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 创建聊天客户端实例
        ChatClient client = new ChatClient();
        
        // 创建扫描器用于读取用户输入
        Scanner scanner = new Scanner(System.in);
        
        try {
            System.out.println("========================================");
            System.out.println("     网络聊天系统 - 客户端");
            System.out.println("========================================");
            System.out.println("命令列表:");
            System.out.println("  connect              - 连接到服务器");
            System.out.println("  login <username> <password> - 登录");
            System.out.println("  send <recipient> <message> - 发送私聊消息");
            System.out.println("  broadcast <message> - 广播消息");
            System.out.println("  logout               - 登出");
            System.out.println("  disconnect           - 断开连接");
            System.out.println("  status               - 显示当前状态");
            System.out.println("  exit                 - 退出程序");
            System.out.println("========================================\n");
            
            // 命令处理循环
            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();
                
                // 解析命令
                if (input.isEmpty()) {
                    continue;
                }
                
                String[] tokens = input.split(" ", 3);
                String command = tokens[0].toLowerCase();
                
                // 执行命令
                switch (command) {
                    case "connect":
                        client.connect();
                        break;
                        
                    case "login":
                        if (tokens.length >= 3) {
                            client.login(tokens[1], tokens[2]);
                        } else {
                            System.out.println("用法: login <username> <password>");
                        }
                        break;
                        
                    case "send":
                        if (tokens.length >= 3) {
                            String[] parts = input.split(" ", 3);
                            client.sendMessage(parts[1], parts[2]);
                        } else {
                            System.out.println("用法: send <recipient> <message>");
                        }
                        break;
                        
                    case "broadcast":
                        if (tokens.length >= 2) {
                            String content = input.substring("broadcast ".length());
                            client.broadcastMessage(content);
                        } else {
                            System.out.println("用法: broadcast <message>");
                        }
                        break;
                        
                    case "logout":
                        client.logout();
                        break;
                        
                    case "disconnect":
                        client.disconnect();
                        break;
                        
                    case "status":
                        displayStatus(client);
                        break;
                        
                    case "exit":
                        client.disconnect();
                        System.out.println("已退出程序");
                        return;
                        
                    default:
                        System.out.println("未知命令: " + command);
                }
            }
            
        } finally {
            scanner.close();
            client.disconnect();
        }
    }
    
    /**
     * 显示当前客户端状态
     * 
     * @param client 聊天客户端实例
     */
    private static void displayStatus(ChatClient client) {
        System.out.println("\n========== 客户端状态 ==========");
        System.out.println("连接状态: " + (client.isConnected() ? "✓ 已连接" : "✗ 未连接"));
        System.out.println("服务器: " + client.getServerHost() + ":" + client.getServerPort());
        System.out.println("登录状态: " + (client.isLoggedIn() ? "✓ 已登录" : "✗ 未登录"));
        if (client.isLoggedIn()) {
            System.out.println("当前用户: " + client.getCurrentUsername());
        }
        System.out.println("================================\n");
    }
}
