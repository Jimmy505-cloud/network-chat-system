package Common;

/**
 * User class for network chat system
 * Represents a user with username, password, status and online flag
 */
public class User {
    private String username;
    private String password;
    private String status;
    private boolean online;

    /**
     * Constructor for User class
     *
     * @param username the username of the user
     * @param password the password of the user
     * @param status   the status message of the user
     * @param online   the online flag indicating if user is online
     */
    public User(String username, String password, String status, boolean online) {
        this.username = username;
        this.password = password;
        this.status = status;
        this.online = online;
    }

    /**
     * Default constructor
     */
    public User() {
        this.username = "";
        this.password = "";
        this.status = "Available";
        this.online = false;
    }

    /**
     * Get the username of the user
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Set the username of the user
     *
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Get the password of the user
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Set the password of the user
     *
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Get the status of the user
     *
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Set the status of the user
     *
     * @param status the status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Check if user is online
     *
     * @return true if user is online, false otherwise
     */
    public boolean isOnline() {
        return online;
    }

    /**
     * Set the online flag
     *
     * @param online the online flag to set
     */
    public void setOnline(boolean online) {
        this.online = online;
    }

    /**
     * String representation of the User object
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", status='" + status + '\'' +
                ", online=" + online +
                '}';
    }

    /**
     * Check if two User objects are equal
     *
     * @param obj the object to compare
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        User user = (User) obj;
        return username.equals(user.username);
    }

    /**
     * Generate hash code for User object
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return username.hashCode();
    }
}
