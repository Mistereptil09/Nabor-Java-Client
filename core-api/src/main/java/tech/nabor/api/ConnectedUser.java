package tech.nabor.api;

public interface ConnectedUser {
    String getUserId();
    String getEmail();
    String getRole();       // "admin", "moderator", "resident"
    boolean isOnline();
}