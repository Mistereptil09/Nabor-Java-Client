package tech.nabor;

import tech.nabor.api.ConnectedUser;


public class MutableConnectedUser implements ConnectedUser {

    private volatile String userId;
    private volatile String email;
    private volatile String role;
    private volatile boolean online;

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    public void connect(String userId, String email, String role) {
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.online = true;
    }

    public boolean isAuthenticated() {
        return userId != null;
    }
}
