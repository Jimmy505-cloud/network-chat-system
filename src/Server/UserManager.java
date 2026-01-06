package Server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.*;

/**
 * 用户管理器类
 * 负责处理用户注册、登录、登出和在线状态管理
 */
public class UserManager {
    // 存储所有用户信息的集合
    private Map<String, User> users;
    // 存储在线用户的集合
    private Map<String, UserSession> onlineUsers;
    // 用户状态变化监听器列表
    private List<UserStatusListener> statusListeners;
    // 用户数据持久化文件路径
    private static final String USER_DATA_FILE = "users.dat";

    /**
     * 构造函数，初始化用户管理器
     */
    public UserManager() {
        this.users = new ConcurrentHashMap<>();
        this.onlineUsers = new ConcurrentHashMap<>();
        this.statusListeners = new CopyOnWriteArrayList<>();
        loadUsersFromFile();
    }

    /**
     * 用户注册方法
     * @param username 用户名
     * @param password 密码
     * @return 注册是否成功
     */
    public synchronized boolean register(String username, String password) {
        // 检查用户名是否已存在
        if (users.containsKey(username)) {
            System.out.println("用户 " + username + " 已存在");
            return false;
        }

        // 验证用户名和密码的有效性
        if (!isValidUsername(username)) {
            System.out.println("用户名无效：" + username);
            return false;
        }

        if (!isValidPassword(password)) {
            System.out.println("密码不符合要求");
            return false;
        }

        // 创建新用户
        User newUser = new User(username, password);
        users.put(username, newUser);

        // 保存用户信息到文件
        saveUsersToFile();

        System.out.println("用户 " + username + " 注册成功");
        notifyUserRegistered(username);
        return true;
    }

    /**
     * 用户登录方法
     * @param username 用户名
     * @param password 密码
     * @return 登录是否成功
     */
    public synchronized boolean login(String username, String password) {
        // 检查用户是否存在
        if (!users.containsKey(username)) {
            System.out.println("用户不存在：" + username);
            return false;
        }

        // 获取用户对象
        User user = users.get(username);

        // 验证密码是否正确
        if (!user.verifyPassword(password)) {
            System.out.println("用户 " + username + " 密码错误");
            return false;
        }

        // 检查用户是否已在线
        if (onlineUsers.containsKey(username)) {
            System.out.println("用户 " + username + " 已在线");
            return false;
        }

        // 创建新的用户会话
        UserSession session = new UserSession(username, System.currentTimeMillis());
        onlineUsers.put(username, session);

        // 更新用户的最后登录时间
        user.setLastLoginTime(System.currentTimeMillis());

        System.out.println("用户 " + username + " 登录成功");
        notifyUserOnline(username);
        return true;
    }

    /**
     * 用户登出方法
     * @param username 用户名
     * @return 登出是否成功
     */
    public synchronized boolean logout(String username) {
        // 检查用户是否在线
        if (!onlineUsers.containsKey(username)) {
            System.out.println("用户 " + username + " 未在线");
            return false;
        }

        // 移除用户会话
        UserSession session = onlineUsers.remove(username);

        // 更新用户的最后登出时间
        if (users.containsKey(username)) {
            users.get(username).setLastLogoutTime(System.currentTimeMillis());
        }

        System.out.println("用户 " + username + " 登出成功");
        notifyUserOffline(username);
        return true;
    }

    /**
     * 检查用户是否在线
     * @param username 用户名
     * @return 用户是否在线
     */
    public boolean isUserOnline(String username) {
        return onlineUsers.containsKey(username);
    }

    /**
     * 获取所有在线用户列表
     * @return 在线用户名列表
     */
    public List<String> getOnlineUsers() {
        return new ArrayList<>(onlineUsers.keySet());
    }

    /**
     * 获取用户的在线时长（秒）
     * @param username 用户名
     * @return 在线时长，如果用户不在线返回-1
     */
    public long getOnlineTime(String username) {
        if (!onlineUsers.containsKey(username)) {
            return -1;
        }

        UserSession session = onlineUsers.get(username);
        return (System.currentTimeMillis() - session.getLoginTime()) / 1000;
    }

    /**
     * 获取用户信息
     * @param username 用户名
     * @return 用户对象，如果用户不存在返回null
     */
    public User getUser(String username) {
        return users.get(username);
    }

    /**
     * 获取所有用户数量
     * @return 用户总数
     */
    public int getTotalUsers() {
        return users.size();
    }

    /**
     * 获取在线用户数量
     * @return 在线用户数
     */
    public int getOnlineUserCount() {
        return onlineUsers.size();
    }

    /**
     * 改变用户密码
     * @param username 用户名
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 密码修改是否成功
     */
    public synchronized boolean changePassword(String username, String oldPassword, String newPassword) {
        // 检查用户是否存在
        if (!users.containsKey(username)) {
            System.out.println("用户不存在：" + username);
            return false;
        }

        User user = users.get(username);

        // 验证旧密码
        if (!user.verifyPassword(oldPassword)) {
            System.out.println("用户 " + username + " 旧密码错误");
            return false;
        }

        // 验证新密码有效性
        if (!isValidPassword(newPassword)) {
            System.out.println("新密码不符合要求");
            return false;
        }

        // 设置新密码
        user.setPassword(newPassword);

        // 保存更新到文件
        saveUsersToFile();

        System.out.println("用户 " + username + " 密码修改成功");
        return true;
    }

    /**
     * 删除用户账户
     * @param username 用户名
     * @param password 密码验证
     * @return 删除是否成功
     */
    public synchronized boolean deleteUser(String username, String password) {
        // 检查用户是否存在
        if (!users.containsKey(username)) {
            System.out.println("用户不存在：" + username);
            return false;
        }

        User user = users.get(username);

        // 验证密码
        if (!user.verifyPassword(password)) {
            System.out.println("用户 " + username + " 密码错误，无法删除账户");
            return false;
        }

        // 如果用户在线，先登出
        if (onlineUsers.containsKey(username)) {
            logout(username);
        }

        // 删除用户
        users.remove(username);

        // 保存更新到文件
        saveUsersToFile();

        System.out.println("用户 " + username + " 账户已删除");
        notifyUserDeleted(username);
        return true;
    }

    /**
     * 验证用户名有效性
     * @param username 用户名
     * @return 用户名是否有效
     */
    private boolean isValidUsername(String username) {
        // 用户名长度应在3-20字符之间
        if (username == null || username.length() < 3 || username.length() > 20) {
            return false;
        }

        // 用户名只能包含字母、数字和下划线
        return username.matches("^[a-zA-Z0-9_]+$");
    }

    /**
     * 验证密码有效性
     * @param password 密码
     * @return 密码是否有效
     */
    private boolean isValidPassword(String password) {
        // 密码长度应至少为6个字符
        return password != null && password.length() >= 6;
    }

    /**
     * 从文件加载用户数据
     */
    private void loadUsersFromFile() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(USER_DATA_FILE))) {
            // 读取序列化的用户数据
            Map<String, User> loadedUsers = (Map<String, User>) ois.readObject();
            users.putAll(loadedUsers);
            System.out.println("用户数据从文件加载成功，共 " + users.size() + " 个用户");
        } catch (FileNotFoundException e) {
            System.out.println("用户数据文件不存在，将创建新文件");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("加载用户数据时出错：" + e.getMessage());
        }
    }

    /**
     * 保存用户数据到文件
     */
    private void saveUsersToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USER_DATA_FILE))) {
            // 序列化用户数据到文件
            oos.writeObject(users);
            oos.flush();
            System.out.println("用户数据保存成功");
        } catch (IOException e) {
            System.out.println("保存用户数据时出错：" + e.getMessage());
        }
    }

    /**
     * 添加用户状态变化监听器
     * @param listener 监听器对象
     */
    public void addStatusListener(UserStatusListener listener) {
        statusListeners.add(listener);
    }

    /**
     * 移除用户状态变化监听器
     * @param listener 监听器对象
     */
    public void removeStatusListener(UserStatusListener listener) {
        statusListeners.remove(listener);
    }

    /**
     * 通知所有监听器用户已注册
     * @param username 用户名
     */
    private void notifyUserRegistered(String username) {
        for (UserStatusListener listener : statusListeners) {
            listener.onUserRegistered(username);
        }
    }

    /**
     * 通知所有监听器用户已上线
     * @param username 用户名
     */
    private void notifyUserOnline(String username) {
        for (UserStatusListener listener : statusListeners) {
            listener.onUserOnline(username);
        }
    }

    /**
     * 通知所有监听器用户已下线
     * @param username 用户名
     */
    private void notifyUserOffline(String username) {
        for (UserStatusListener listener : statusListeners) {
            listener.onUserOffline(username);
        }
    }

    /**
     * 通知所有监听器用户已删除
     * @param username 用户名
     */
    private void notifyUserDeleted(String username) {
        for (UserStatusListener listener : statusListeners) {
            listener.onUserDeleted(username);
        }
    }

    /**
     * 内部类：用户信息类
     */
    public static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String username;
        private String password;
        private long registrationTime;
        private long lastLoginTime;
        private long lastLogoutTime;

        /**
         * 构造函数
         * @param username 用户名
         * @param password 密码
         */
        public User(String username, String password) {
            this.username = username;
            this.password = password;
            this.registrationTime = System.currentTimeMillis();
            this.lastLoginTime = -1;
            this.lastLogoutTime = -1;
        }

        /**
         * 验证密码
         * @param password 密码
         * @return 密码是否正确
         */
        public boolean verifyPassword(String password) {
            return this.password.equals(password);
        }

        /**
         * 设置新密码
         * @param password 新密码
         */
        public void setPassword(String password) {
            this.password = password;
        }

        // Getter 方法

        public String getUsername() {
            return username;
        }

        public long getRegistrationTime() {
            return registrationTime;
        }

        public long getLastLoginTime() {
            return lastLoginTime;
        }

        public void setLastLoginTime(long time) {
            this.lastLoginTime = time;
        }

        public long getLastLogoutTime() {
            return lastLogoutTime;
        }

        public void setLastLogoutTime(long time) {
            this.lastLogoutTime = time;
        }
    }

    /**
     * 内部类：用户会话类
     */
    public static class UserSession implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String username;
        private long loginTime;
        private String sessionId;

        /**
         * 构造函数
         * @param username 用户名
         * @param loginTime 登录时间
         */
        public UserSession(String username, long loginTime) {
            this.username = username;
            this.loginTime = loginTime;
            // 生成唯一的会话ID
            this.sessionId = UUID.randomUUID().toString();
        }

        // Getter 方法

        public String getUsername() {
            return username;
        }

        public long getLoginTime() {
            return loginTime;
        }

        public String getSessionId() {
            return sessionId;
        }
    }

    /**
     * 用户状态监听器接口
     */
    public interface UserStatusListener {
        // 用户注册时调用
        void onUserRegistered(String username);

        // 用户上线时调用
        void onUserOnline(String username);

        // 用户下线时调用
        void onUserOffline(String username);

        // 用户删除时调用
        void onUserDeleted(String username);
    }
}
