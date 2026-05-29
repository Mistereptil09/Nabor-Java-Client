package tech.nabor;

import javafx.application.Application;

/**
 * Lanceur de l'application.
 *
 * <p>Classe séparée de {@link NaborApp} (qui étend {@link Application}) :
 * c'est le contournement habituel pour qu'un fat-jar démarre sans l'erreur
 * « JavaFX runtime components are missing ». Le {@code mainClass} Gradle
 * pointe sur {@code tech.nabor.Main}.</p>
 */
public class Main {
    public static void main(String[] args) {
        Application.launch(NaborApp.class, args);
    }
}
