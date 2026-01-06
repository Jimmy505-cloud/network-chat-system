package Server;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * 服务器线程类 - 处理单个客户端连接
 * 每个客户端连接都会创建一个独立的ServerThread线程来处理通信
 */
public class ServerThread extends Thread {
    
    // 客户端Socket连接
    private Socket socket;
    
    // 输入流 - 用于接收客户端消息
    private BufferedReader in;
    
    // 输出流 - 用于向客户端发送消息
    private PrintWriter out;
    
    // 客户端用户名
    private String username;
    
    // 服务器实例 - 用于访问共享资源
    private ChatServer server;
    
    // 客户端是否在线的标志
    private boolean isConnected;

    /**
     * 构造函数
     * @param socket 客户端Socket连接
     * @param server 服务器实例
     */
    public ServerThread(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        this.isConnected = true;
        this.username = null;
        
        try {
            // 初始化输入输出流
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        } catch (IOException e) {
            System.err.println("初始化客户端流失败: " + e.getMessage());
            isConnected = false;
        }
    }

    /**
     * 线程运行方法 - 处理客户端消息
     */
    @Override
    public void run() {
        try {
            // 等待客户端发送用户名
            String clientMessage;
            
            while (isConnected && (clientMessage = in.readLine()) != null) {
                // 解析消息格式
                String[] messageParts = clientMessage.split(":", 2);
                
                if (messageParts.length > 0) {
                    String messageType = messageParts[0];
                    String messageContent = messageParts.length > 1 ? messageParts[1] : "";
                    
                    // 根据消息类型处理
                    switch (messageType.toUpperCase()) {
                        case "LOGIN":
                            // 处理登录请求
                            handleLogin(messageContent);
                            break;
                            
                        case "MESSAGE":
                            // 处理普通消息
                            handleMessage(messageContent);
                            break;
                            
                        case "BROADCAST":
                            // 处理广播消息
                            handleBroadcast(messageContent);
                            break;
                            
                        case "LOGOUT":
                            // 处理注销请求
                            handleLogout();
                            break;
                            
                        case "USERLIST":
                            // 处理用户列表请求
                            handleUserListRequest();
                            break;
                            
                        default:
                            // 未知消息类型
                            sendMessage("ERROR", "未知的消息类型: " + messageType);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("读取客户端消息出错: " + e.getMessage());
        } finally {
            // 关闭连接
            closeConnection();
        }
    }

    /**
     * 处理客户端登录请求
     * @param username 客户端用户名
     */
    private void handleLogin(String username) {
        // 验证用户名是否为空
        if (username == null || username.trim().isEmpty()) {
            sendMessage("LOGIN_FAILED", "用户名不能为空");
            return;
        }
        
        username = username.trim();
        
        // 检查用户名是否已被使用
        if (server.isUsernameTaken(username)) {
            sendMessage("LOGIN_FAILED", "用户名已被使用: " + username);
            return;
        }
        
        // 设置当前线程的用户名
        this.username = username;
        
        // 将此线程添加到服务器的客户端列表中
        server.addClient(this);
        
        // 发送登录成功消息给客户端
        sendMessage("LOGIN_SUCCESS", "欢迎 " + username + " 加入聊天室");
        
        // 广播用户上线消息给所有客户端
        server.broadcastMessage(username + " 上线了");
        
        System.out.println("客户端登录成功: " + username);
    }

    /**
     * 处理普通消息（点对点消息）
     * @param content 消息内容，格式为 "目标用户名|消息内容"
     */
    private void handleMessage(String content) {
        // 检查用户是否已登录
        if (username == null) {
            sendMessage("ERROR", "请先登录");
            return;
        }
        
        // 解析消息内容
        String[] parts = content.split("\\|", 2);
        if (parts.length < 2) {
            sendMessage("ERROR", "消息格式错误");
            return;
        }
        
        String targetUsername = parts[0].trim();
        String message = parts[1];
        
        // 发送点对点消息
        if (server.sendPrivateMessage(username, targetUsername, message)) {
            sendMessage("MESSAGE_SUCCESS", "消息已发送给 " + targetUsername);
        } else {
            sendMessage("MESSAGE_FAILED", "用户不存在或离线: " + targetUsername);
        }
    }

    /**
     * 处理广播消息 - 发送给所有在线用户
     * @param content 消息内容
     */
    private void handleBroadcast(String content) {
        // 检查用户是否已登录
        if (username == null) {
            sendMessage("ERROR", "请先登录");
            return;
        }
        
        // 广播消息
        server.broadcastMessage("[" + username + "]: " + content);
        
        System.out.println("广播消息 - " + username + ": " + content);
    }

    /**
     * 处理注销请求 - 用户下线
     */
    private void handleLogout() {
        if (username != null) {
            System.out.println("客户端注销: " + username);
            server.broadcastMessage(username + " 离线了");
        }
        
        isConnected = false;
        closeConnection();
    }

    /**
     * 处理用户列表请求
     * 返回所有在线用户列表
     */
    private void handleUserListRequest() {
        List<String> userList = server.getOnlineUsers();
        
        StringBuilder userListStr = new StringBuilder();
        for (String user : userList) {
            userListStr.append(user).append(",");
        }
        
        // 移除最后一个逗号
        if (userListStr.length() > 0) {
            userListStr.deleteCharAt(userListStr.length() - 1);
        }
        
        sendMessage("USERLIST", userListStr.toString());
    }

    /**
     * 发送消息给客户端
     * @param messageType 消息类型
     * @param content 消息内容
     */
    public void sendMessage(String messageType, String content) {
        if (out != null && !out.checkError()) {
            out.println(messageType + ":" + content);
            out.flush();
        }
    }

    /**
     * 关闭客户端连接
     * 释放所有相关资源
     */
    private void closeConnection() {
        try {
            // 从服务器的客户端列表中移除
            if (username != null) {
                server.removeClient(this);
            }
            
            // 关闭流
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            
            // 关闭Socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            System.out.println("客户端连接已关闭: " + (username != null ? username : "未登录用户"));
        } catch (IOException e) {
            System.err.println("关闭连接时出错: " + e.getMessage());
        }
    }

    /**
     * 获取客户端用户名
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 检查客户端是否仍然连接
     * @return 连接状态
     */
    public boolean isConnected() {
        return isConnected && socket != null && !socket.isClosed();
    }
}
