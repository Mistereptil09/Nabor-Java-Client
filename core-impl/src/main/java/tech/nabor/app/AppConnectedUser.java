package tech.nabor.app;

import tech.nabor.api.ConnectedUser;

public class AppConnectedUser implements ConnectedUser {
    private final String userId;
    private final String email;
    private final String role;

    public AppConnectedUser(String userId, String email, String role) {
        this.userId = userId;
        this.email = email;
        this.role = role;
    }

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
        return true;
    }
}
