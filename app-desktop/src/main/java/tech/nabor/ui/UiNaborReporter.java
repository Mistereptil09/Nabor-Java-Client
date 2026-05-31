package tech.nabor.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import tech.nabor.api.error.NaborException;
import tech.nabor.api.error.NaborReporter;


public class UiNaborReporter implements NaborReporter {

    private enum Severity { INFO, WARNING, ERROR }

    private static final Duration VISIBLE_FOR = Duration.seconds(3.5);
    private static final Duration FADE_OUT = Duration.millis(400);

    private final NaborReporter console;
    private VBox toastBox;

    public UiNaborReporter(NaborReporter console) {
        this.console = console;
    }

    public void attachToastLayer(StackPane overlay) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.BOTTOM_RIGHT);
        box.setPadding(new Insets(16));
        box.setMouseTransparent(true);   
        box.setPickOnBounds(false);
        StackPane.setAlignment(box, Pos.BOTTOM_RIGHT);
        overlay.getChildren().add(box);
        this.toastBox = box;
    }

    @Override
    public void reportError(NaborException e) {
        console.reportError(e);
        String message = e.getMessage() != null ? e.getMessage() : e.getKind().toString();
        showToast(message, Severity.ERROR);
    }

    @Override
    public void reportInfo(String message) {
        console.reportInfo(message);
        showToast(message, Severity.INFO);
    }

    @Override
    public void reportWarning(String message) {
        console.reportWarning(message);
        showToast(message, Severity.WARNING);
    }

    private void showToast(String message, Severity severity) {
        if (toastBox == null) {
            return; 
        }
        if (Platform.isFxApplicationThread()) {
            displayToast(message, severity);
        } else {
            Platform.runLater(() -> displayToast(message, severity));
        }
    }

    private void displayToast(String message, Severity severity) {
        Label toast = new Label(message);
        toast.setWrapText(true);
        toast.setMaxWidth(360);
        toast.setPadding(new Insets(10, 14, 10, 14));
        toast.getStyleClass().addAll("toast", styleClassFor(severity));
        toast.setStyle(inlineStyleFor(severity));

        toastBox.getChildren().add(toast);

        PauseTransition stay = new PauseTransition(VISIBLE_FOR);
        stay.setOnFinished(ev -> {
            FadeTransition fade = new FadeTransition(FADE_OUT, toast);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> toastBox.getChildren().remove(toast));
            fade.play();
        });
        stay.play();
    }

    private String styleClassFor(Severity severity) {
        return switch (severity) {
            case INFO -> "toast-info";
            case WARNING -> "toast-warning";
            case ERROR -> "toast-error";
        };
    }

    private String inlineStyleFor(Severity severity) {
        String background = switch (severity) {
            case INFO -> "#0F2A5E";     
            case WARNING -> "#F7931E";  
            case ERROR -> "#E8534A";   
        };
        return "-fx-background-color: " + background + ";"
                + "-fx-text-fill: white;"
                + "-fx-background-radius: 8;"
                + "-fx-font-size: 13px;";
    }
}
