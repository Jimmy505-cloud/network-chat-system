package Common;

/**
 * Command class for network chat system
 * Defines common command types and structures for communication between clients and server
 */
public class Command {
    // Command types
    public static final int LOGIN = 1;
    public static final int LOGOUT = 2;
    public static final int MESSAGE = 3;
    public static final int USER_LIST = 4;
    public static final int PRIVATE_MESSAGE = 5;
    public static final int NOTIFICATION = 6;
    public static final int ERROR = 7;
    
    private int commandType;
    private String content;
    private String sender;
    private String recipient;
    
    /**
     * Default constructor
     */
    public Command() {
    }
    
    /**
     * Constructor with command type and content
     */
    public Command(int commandType, String content) {
        this.commandType = commandType;
        this.content = content;
    }
    
    /**
     * Constructor with command type, sender, and content
     */
    public Command(int commandType, String sender, String content) {
        this.commandType = commandType;
        this.sender = sender;
        this.content = content;
    }
    
    /**
     * Constructor with all parameters
     */
    public Command(int commandType, String sender, String recipient, String content) {
        this.commandType = commandType;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
    }
    
    // Getters and Setters
    public int getCommandType() {
        return commandType;
    }
    
    public void setCommandType(int commandType) {
        this.commandType = commandType;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getSender() {
        return sender;
    }
    
    public void setSender(String sender) {
        this.sender = sender;
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
    
    @Override
    public String toString() {
        return "Command{" +
                "commandType=" + commandType +
                ", sender='" + sender + '\'' +
                ", recipient='" + recipient + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
