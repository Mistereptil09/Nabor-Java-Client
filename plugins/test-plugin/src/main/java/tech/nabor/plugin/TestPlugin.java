package tech.nabor.plugin;

import javafx.scene.Node;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;
import tech.nabor.api.error.NaborException;
import tech.nabor.api.model.user.User;

import java.util.Optional;

public class TestPlugin implements NaborPlugin {

    private PluginContext ctx;

    @Override public String getId() { return "test-plugin"; }
    @Override public String getDisplayName() { return "Test Plugin"; }

    @Override
    public void initialize(PluginContext ctx) {
        this.ctx = ctx;
        ctx.getReporter().reportInfo("[TestPlugin] Initializing...");

        ctx.getEventBus().subscribe("test.ping", (payload) -> {
            ctx.getReporter().reportInfo("[TestPlugin] Received test.ping with payload: " + payload);
            ctx.getEventBus().publish("test.pong", "Pong from TestPlugin!");
        });

        try {
            String userId = ctx.getConnectedUser().getUserId();
            Optional<User> user = ctx.getDb().users().findById(userId);
            user.ifPresentOrElse(
                u -> ctx.getReporter().reportInfo("[TestPlugin] Found connected user in DB: " + u.email()),
                () -> ctx.getReporter().reportInfo("[TestPlugin] Connected user not found in DB"));
            int incidentCount = ctx.getDb().incidents().findByReporterId(userId).size();
            ctx.getReporter().reportInfo("[TestPlugin] Found " + incidentCount + " incidents reported by user in DB");
        } catch (Exception e) {
            ctx.getReporter().reportWarning("[TestPlugin] Error while accessing repositories: " + e.getMessage());
        }
        ctx.getReporter().reportInfo("[TestPlugin] Initialized successfully!");
    }

    @Override
    public Optional<Node> getView() {
        if (ctx != null) ctx.getReporter().reportInfo("[TestPlugin] getView() called - returning empty for headless mode");
        return Optional.empty();
    }

    @Override
    public void shutdown() {
        if (ctx != null) ctx.getReporter().reportInfo("[TestPlugin] Shutting down...");
    }
}
