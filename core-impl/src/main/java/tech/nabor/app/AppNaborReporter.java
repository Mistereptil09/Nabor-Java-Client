package tech.nabor.app;

import tech.nabor.api.error.NaborReporter;
import tech.nabor.api.error.NaborException;

public class AppNaborReporter implements NaborReporter {

    @Override
    public void reportError(NaborException e) {
        // TODO — show in JavaFX
        System.err.println("[ERROR] " + e.getKind() + " : " + e.getMessage());
    }

    @Override
    public void reportInfo(String message) {
        // TODO — show in JavaFX
        System.out.println("[INFO] " + message);
    }

    @Override
    public void reportWarning(String message) {
        // TODO — show in JavaFX
        System.out.println("[WARN] " + message);
    }
}