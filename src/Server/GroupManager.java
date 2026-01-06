package Server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GroupManager handles all group-related operations in the chat system.
 * Manages group creation, deletion, member management, and group information.
 * Thread-safe implementation using concurrent collections.
 */
public class GroupManager {
    private final Map<String, Group> groups;
    private final Map<String, List<String>> userGroups; // userId -> list of groupIds
    private static GroupManager instance;
    
    /**
     * Group class represents a chat group with members and metadata
     */
    public static class Group {
        private final String groupId;
        private String groupName;
        private String description;
        private final String creatorId;
        private final long createdAt;
        private final List<String> members;
        private final Map<String, String> memberRoles; // memberId -> role (admin, member)
        
        public Group(String groupId, String groupName, String creatorId) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.creatorId = creatorId;
            this.createdAt = System.currentTimeMillis();
            this.members = new CopyOnWriteArrayList<>();
            this.memberRoles = new ConcurrentHashMap<>();
            this.description = "";
            
            // Add creator as admin member
            this.members.add(creatorId);
            this.memberRoles.put(creatorId, "admin");
        }
        
        // Getters
        public String getGroupId() { return groupId; }
        public String getGroupName() { return groupName; }
        public String getDescription() { return description; }
        public String getCreatorId() { return creatorId; }
        public long getCreatedAt() { return createdAt; }
        public List<String> getMembers() { return new ArrayList<>(members); }
        public Map<String, String> getMemberRoles() { return new HashMap<>(memberRoles); }
        
        // Setters
        public void setGroupName(String groupName) { this.groupName = groupName; }
        public void setDescription(String description) { this.description = description; }
    }
    
    /**
     * Private constructor for singleton pattern
     */
    private GroupManager() {
        this.groups = new ConcurrentHashMap<>();
        this.userGroups = new ConcurrentHashMap<>();
    }
    
    /**
     * Get singleton instance of GroupManager
     */
    public static synchronized GroupManager getInstance() {
        if (instance == null) {
            instance = new GroupManager();
        }
        return instance;
    }
    
    /**
     * Create a new group
     * @param groupName Name of the group
     * @param creatorId ID of the user creating the group
     * @return Group object if successful, null otherwise
     */
    public Group createGroup(String groupName, String creatorId) {
        if (groupName == null || groupName.trim().isEmpty() || creatorId == null) {
            return null;
        }
        
        String groupId = generateGroupId();
        Group group = new Group(groupId, groupName.trim(), creatorId);
        groups.put(groupId, group);
        userGroups.computeIfAbsent(creatorId, k -> new CopyOnWriteArrayList<>()).add(groupId);
        
        return group;
    }
    
    /**
     * Delete a group
     * @param groupId ID of the group to delete
     * @param userId ID of the user requesting deletion (must be creator or admin)
     * @return true if successful, false otherwise
     */
    public boolean deleteGroup(String groupId, String userId) {
        if (groupId == null || userId == null) {
            return false;
        }
        
        Group group = groups.get(groupId);
        if (group == null) {
            return false;
        }
        
        // Only creator can delete group
        if (!group.getCreatorId().equals(userId)) {
            return false;
        }
        
        // Remove group from all members
        for (String memberId : group.getMembers()) {
            List<String> memberGroupList = userGroups.get(memberId);
            if (memberGroupList != null) {
                memberGroupList.remove(groupId);
            }
        }
        
        groups.remove(groupId);
        return true;
    }
    
    /**
     * Add a member to a group
     * @param groupId ID of the group
     * @param memberId ID of the user to add
     * @param addedBy ID of the user performing the action
     * @return true if successful, false otherwise
     */
    public boolean addMember(String groupId, String memberId, String addedBy) {
        if (groupId == null || memberId == null || addedBy == null) {
            return false;
        }
        
        Group group = groups.get(groupId);
        if (group == null) {
            return false;
        }
        
        // Check if addedBy has permission (admin or creator)
        String role = group.memberRoles.get(addedBy);
        if (role == null || (!role.equals("admin") && !addedBy.equals(group.getCreatorId()))) {
            return false;
        }
        
        // Check if member already exists
        if (group.members.contains(memberId)) {
            return false;
        }
        
        // Add member with "member" role
        group.members.add(memberId);
        group.memberRoles.put(memberId, "member");
        userGroups.computeIfAbsent(memberId, k -> new CopyOnWriteArrayList<>()).add(groupId);
        
        return true;
    }
    
    /**
     * Remove a member from a group
     * @param groupId ID of the group
     * @param memberId ID of the user to remove
     * @param removedBy ID of the user performing the action
     * @return true if successful, false otherwise
     */
    public boolean removeMember(String groupId, String memberId, String removedBy) {
        if (groupId == null || memberId == null || removedBy == null) {
            return false;
        }
        
        Group group = groups.get(groupId);
        if (group == null) {
            return false;
        }
        
        // Check if member exists
        if (!group.members.contains(memberId)) {
            return false;
        }
        
        // Only creator and admins can remove members, and members can remove themselves
        String removerRole = group.memberRoles.get(removedBy);
        boolean isCreator = removedBy.equals(group.getCreatorId());
        boolean isAdmin = "admin".equals(removerRole);
        boolean removingSelf = removedBy.equals(memberId);
        
        if (!isCreator && !isAdmin && !removingSelf) {
            return false;
        }
        
        // Cannot remove creator
        if (group.getCreatorId().equals(memberId)) {
            return false;
        }
        
        // Remove member
        group.members.remove(memberId);
        group.memberRoles.remove(memberId);
        
        List<String> memberGroupList = userGroups.get(memberId);
        if (memberGroupList != null) {
            memberGroupList.remove(groupId);
        }
        
        return true;
    }
    
    /**
     * Promote a member to admin
     * @param groupId ID of the group
     * @param memberId ID of the member to promote
     * @param promotedBy ID of the user performing the action (must be creator)
     * @return true if successful, false otherwise
     */
    public boolean promoteToAdmin(String groupId, String memberId, String promotedBy) {
        if (groupId == null || memberId == null || promotedBy == null) {
            return false;
        }
        
        Group group = groups.get(groupId);
        if (group == null) {
            return false;
        }
        
        // Only creator can promote members
        if (!group.getCreatorId().equals(promotedBy)) {
            return false;
        }
        
        // Check if member exists
        if (!group.members.contains(memberId)) {
            return false;
        }
        
        // Cannot promote creator (already admin)
        if (group.getCreatorId().equals(memberId)) {
            return false;
        }
        
        group.memberRoles.put(memberId, "admin");
        return true;
    }
    
    /**
     * Demote an admin to regular member
     * @param groupId ID of the group
     * @param memberId ID of the admin to demote
     * @param demotedBy ID of the user performing the action (must be creator)
     * @return true if successful, false otherwise
     */
    public boolean demoteToMember(String groupId, String memberId, String demotedBy) {
        if (groupId == null || memberId == null || demotedBy == null) {
            return false;
        }
        
        Group group = groups.get(groupId);
        if (group == null) {
            return false;
        }
        
        // Only creator can demote members
        if (!group.getCreatorId().equals(demotedBy)) {
            return false;
        }
        
        // Check if member exists
        if (!group.members.contains(memberId)) {
            return false;
        }
        
        // Cannot demote creator
        if (group.getCreatorId().equals(memberId)) {
            return false;
        }
        
        group.memberRoles.put(memberId, "member");
        return true;
    }
    
    /**
     * Get a group by ID
     * @param groupId ID of the group
     * @return Group object or null if not found
     */
    public Group getGroup(String groupId) {
        return groups.get(groupId);
    }
    
    /**
     * Get all groups for a user
     * @param userId ID of the user
     * @return List of Group objects the user is member of
     */
    public List<Group> getUserGroups(String userId) {
        List<Group> userGroupsList = new ArrayList<>();
        List<String> groupIds = userGroups.get(userId);
        
        if (groupIds != null) {
            for (String groupId : groupIds) {
                Group group = groups.get(groupId);
                if (group != null) {
                    userGroupsList.add(group);
                }
            }
        }
        
        return userGroupsList;
    }
    
    /**
     * Check if a user is member of a group
     * @param groupId ID of the group
     * @param userId ID of the user
     * @return true if user is member, false otherwise
     */
    public boolean isMember(String groupId, String userId) {
        Group group = groups.get(groupId);
        return group != null && group.members.contains(userId);
    }
    
    /**
     * Get member role in a group
     * @param groupId ID of the group
     * @param userId ID of the user
     * @return Role string ("admin", "member") or null if not a member
     */
    public String getMemberRole(String groupId, String userId) {
        Group group = groups.get(groupId);
        if (group == null) {
            return null;
        }
        return group.memberRoles.get(userId);
    }
    
    /**
     * Get all groups (admin operation)
     * @return List of all groups
     */
    public List<Group> getAllGroups() {
        return new ArrayList<>(groups.values());
    }
    
    /**
     * Get member count of a group
     * @param groupId ID of the group
     * @return Number of members or -1 if group not found
     */
    public int getMemberCount(String groupId) {
        Group group = groups.get(groupId);
        return group != null ? group.members.size() : -1;
    }
    
    /**
     * Generate unique group ID
     * @return Generated group ID
     */
    private String generateGroupId() {
        return "GROUP_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Clear all groups (for testing/reset purposes)
     */
    public void clearAllGroups() {
        groups.clear();
        userGroups.clear();
    }
}
