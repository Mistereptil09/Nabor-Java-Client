package tech.nabor;

import tech.nabor.api.ConnectedUser;

/**
 * Détenteur mutable de l'utilisateur connecté.
 *
 * <p>Le {@link tech.nabor.api.PluginContext} est construit une seule fois au
 * bootstrap avec ce détenteur (initialement anonyme). Après la connexion SSO,
 * {@link #connect(String, String, String)} le renseigne, puis les plugins sont
 * chargés — ils voient ainsi le vrai utilisateur sans reconstruire le contexte.</p>
 */
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
