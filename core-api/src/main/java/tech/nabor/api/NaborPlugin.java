package tech.nabor.api;

import java.util.Optional;
import javafx.scene.Node;

public interface NaborPlugin {
    String getId();
    String getDisplayName();
    void initialize(PluginContext ctx);
    Optional<Node> getView();
    void shutdown();
}