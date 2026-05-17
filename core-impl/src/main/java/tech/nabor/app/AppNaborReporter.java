package tech.nabor.app;

import tech.nabor.api.error.NaborException;
import tech.nabor.api.error.NaborReporter;

public class AppNaborReporter implements NaborReporter {
    
    @Override
    public void reportError(NaborException e) {
        System.err.println("[Reporter] ERROR (" + e.getKind() + "): " + e.getMessage());
        if (e.getCause() != null) {
            e.getCause().printStackTrace();
        }
    }

    @Override
    public void reportInfo(String message) {
        System.out.println("[Reporter] INFO: " + message);
    }

    @Override
    public void reportWarning(String message) {
        System.out.println("[Reporter] WARNING: " + message);
    }
}
