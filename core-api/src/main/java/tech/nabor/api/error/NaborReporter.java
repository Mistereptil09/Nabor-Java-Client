package tech.nabor.api.error;

// core-api/src/main/java/tech/nabor/api/error/ErrorReporter.java
public interface NaborReporter {
    void reportError(NaborException e);   // affiche dans l'UI + log
    void reportInfo(String message);      // notification neutre
    void reportWarning(String message);   // avertissement
}