package Server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 聊天服务器类
 * 负责监听客户端连接，管理多个客户端会话，转发消息
 */
public class ChatServer {
    
    // 服务器监听端口
    private static final int SERVER_PORT = 8888;
    
    // 客户端连接线程池
    private static ExecutorService threadPool;
    
    // 存储所有客户端处理器的线程安全集合
    private static Set<ClientHandler> clientHandlers;
    
    // 服务器套接字
    private static ServerSocket serverSocket;
    
    /**
     * 服务器主方法
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 初始化客户端处理器集合（使用Collections.synchronizedSet保证线程安全）
        clientHandlers = Collections.synchronizedSet(new HashSet<>());
        
        // 创建线程池，用于处理客户端连接（核心线程数为10）
        threadPool = Executors.newFixedThreadPool(10);
        
        try {
            // 创建服务器套接字，监听指定端口
            serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("聊天服务器启动成功，监听端口：" + SERVER_PORT);
            System.out.println("服务器启动时间：" + new Date());
            
            // 持续接受客户端连接
            while (true) {
                // 等待客户端连接
                Socket clientSocket = serverSocket.accept();
                
                // 创建客户端处理器
                ClientHandler handler = new ClientHandler(clientSocket);
                
                // 将客户端处理器添加到集合中
                clientHandlers.add(handler);
                
                // 在线程池中执行客户端处理任务
                threadPool.execute(handler);
                
                System.out.println("新客户端连接，当前连接数：" + clientHandlers.size());
            }
        } catch (IOException e) {
            System.out.println("服务器错误：" + e.getMessage());
            e.printStackTrace();
        } finally {
            // 关闭服务器资源
            closeServer();
        }
    }
    
    /**
     * 关闭服务器
     */
    private static void closeServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("服务器套接字已关闭");
            }
            
            // 关闭线程池
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdown();
                System.out.println("线程池已关闭");
            }
        } catch (IOException e) {
            System.out.println("关闭服务器时出错：" + e.getMessage());
        }
    }
    
    /**
     * 广播消息给所有客户端
     * @param message 要广播的消息
     * @param sender 消息发送者
     */
    public static void broadcastMessage(String message, ClientHandler sender) {
        // 遍历所有客户端处理器
        for (ClientHandler handler : clientHandlers) {
            // 如果接收者不是发送者，则发送消息
            if (handler != sender) {
                handler.sendMessage(message);
            }
        }
    }
    
    /**
     * 从集合中移除断开连接的客户端
     * @param handler 要移除的客户端处理器
     */
    public static void removeClient(ClientHandler handler) {
        clientHandlers.remove(handler);
        System.out.println("客户端已断开连接，当前连接数：" + clientHandlers.size());
    }
    
    /**
     * 获取当前连接的客户端数量
     * @return 客户端数量
     */
    public static int getClientCount() {
        return clientHandlers.size();
    }
    
    /**
     * 内部类：客户端处理器
     * 负责处理单个客户端的连接和消息接收
     */
    private static class ClientHandler implements Runnable {
        
        // 客户端套接字
        private Socket socket;
        
        // 输入流
        private BufferedReader inputStream;
        
        // 输出流
        private PrintWriter outputStream;
        
        // 客户端用户名
        private String clientName;
        
        /**
         * 构造函数
         * @param socket 客户端套接字
         */
        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                // 初始化输入输出流
                this.inputStream = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
                );
                this.outputStream = new PrintWriter(
                    socket.getOutputStream(), true
                );
            } catch (IOException e) {
                System.out.println("初始化客户端流失败：" + e.getMessage());
            }
        }
        
        /**
         * 处理客户端连接的主方法
         */
        @Override
        public void run() {
            try {
                // 接收客户端的用户名
                this.clientName = inputStream.readLine();
                if (this.clientName != null) {
                    System.out.println("客户端 " + clientName + " 已连接");
                    
                    // 发送欢迎消息给客户端
                    outputStream.println("欢迎进入聊天室，用户名：" + clientName);
                    
                    // 通知其他客户端有新用户加入
                    broadcastMessage(clientName + " 加入了聊天室", this);
                }
                
                // 持续接收客户端消息
                String message;
                while ((message = inputStream.readLine()) != null) {
                    if (message.trim().isEmpty()) {
                        continue;
                    }
                    
                    // 处理退出命令
                    if (message.equalsIgnoreCase("exit") || message.equalsIgnoreCase("quit")) {
                        System.out.println("客户端 " + clientName + " 请求断开连接");
                        break;
                    }
                    
                    // 构建完整消息
                    String fullMessage = "[" + clientName + "]：" + message;
                    System.out.println(fullMessage);
                    
                    // 广播消息给其他客户端
                    broadcastMessage(fullMessage, this);
                }
                
            } catch (IOException e) {
                System.out.println("读取客户端消息出错：" + e.getMessage());
            } finally {
                // 清理资源
                closeConnection();
            }
        }
        
        /**
         * 向客户端发送消息
         * @param message 要发送的消息
         */
        public void sendMessage(String message) {
            if (outputStream != null) {
                outputStream.println(message);
            }
        }
        
        /**
         * 关闭客户端连接
         */
        private void closeConnection() {
            try {
                // 通知其他客户端该用户已离线
                broadcastMessage(clientName + " 离开了聊天室", this);
                
                // 从服务器的客户端集合中移除
                removeClient(this);
                
                // 关闭套接字和流
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                
                System.out.println("客户端 " + clientName + " 连接已关闭");
            } catch (IOException e) {
                System.out.println("关闭客户端连接时出错：" + e.getMessage());
            }
        }
    }
}
