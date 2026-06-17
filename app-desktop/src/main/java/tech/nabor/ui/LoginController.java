package tech.nabor.ui;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import tech.nabor.api.model.local.LocalAccount;
import tech.nabor.service.AuthService;
import tech.nabor.ui.i18n.I18nManager;
import tech.nabor.ui.util.QRCodeUtil;

public class LoginController {

    private static final int QR_SIZE = 240;
    private static final Duration POLL_INTERVAL = Duration.seconds(2);
    private static final Duration QR_TTL = Duration.minutes(2);

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label statusLabel;
    @FXML private ImageView qrImage;
    @FXML private Button refreshButton;
    @FXML private Button devLoginButton;
    @FXML private Button openBrowserButton;

    private AuthService auth;
    private I18nManager i18n;
    private Consumer<AuthService.Session> onAuthenticated;
    private Consumer<LocalAccount> onDeleteAccount;
    private List<LocalAccount> accounts;

    // Picker UI (built programmatically)
    private VBox pickerBox;

    private AuthService.QrChallenge challenge;
    private Timeline pollTimeline;
    private Timeline expiryTimeline;

    // ── Init ──────────────────────────────────────────────────────────────────

    /** Show account picker if accounts exist, otherwise show QR login. */
    public void init(AuthService auth, I18nManager i18n,
                     Consumer<AuthService.Session> onAuthenticated,
                     Consumer<LocalAccount> onDeleteAccount,
                     List<LocalAccount> accounts) {
        this.auth = auth;
        this.i18n = i18n;
        this.onAuthenticated = onAuthenticated;
        this.onDeleteAccount = onDeleteAccount;
        this.accounts = accounts != null ? accounts : List.of();
        statusLabel.setWrapText(true);
        applyTexts();

        if (!this.accounts.isEmpty()) {
            showPicker();
        } else {
            startChallenge();
        }
    }

    // ── Account picker ──────────────────────────────────────────────────────

    private void showPicker() {
        titleLabel.setText(i18n.t("login.picker.title"));
        subtitleLabel.setText(i18n.t("login.picker.subtitle"));
        hideQrElements();

        if (pickerBox == null) {
            pickerBox = new VBox(10);
            pickerBox.setPadding(new Insets(16, 0, 16, 0));
            VBox parent = (VBox) titleLabel.getParent();
            int idx = parent.getChildren().indexOf(devLoginButton);
            parent.getChildren().add(idx, pickerBox);
        }

        pickerBox.getChildren().clear();
        for (LocalAccount a : accounts) {
            Button loginBtn = new Button(a.displayName() + "  (" + a.email() + ")");
            loginBtn.setMaxWidth(Double.MAX_VALUE);
            loginBtn.getStyleClass().add("nav-button");
            loginBtn.setOnAction(e -> tryLogin(a));

            Button delBtn = new Button("✕");
            delBtn.getStyleClass().add("danger-button");
            delBtn.setMinWidth(40);
            delBtn.setMinHeight(40);
            delBtn.setOnAction(e -> deleteAccount(a));

            HBox row = new HBox(6, loginBtn, delBtn);
            HBox.setHgrow(loginBtn, Priority.ALWAYS);
            pickerBox.getChildren().add(row);
        }
        Button newBtn = new Button(i18n.t("login.picker.new"));
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.getStyleClass().add("accent-button");
        newBtn.setOnAction(e -> startChallenge());
        pickerBox.getChildren().add(newBtn);
    }

    private void deleteAccount(LocalAccount account) {
        if (onDeleteAccount != null) onDeleteAccount.accept(account);
        accounts = accounts.stream()
                .filter(a -> !a.userId().equals(account.userId()))
                .toList();
        if (accounts.isEmpty()) {
            hidePicker();
            startChallenge();
        } else {
            showPicker();
        }
    }

    private void tryLogin(LocalAccount account) {
        statusLabel.setText(i18n.t("login.status.connecting"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return auth.refresh(account.refreshToken());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(session -> Platform.runLater(() -> {
            statusLabel.setText("");
            onAuthenticated.accept(session);
        })).exceptionally(ex -> {
            Platform.runLater(() ->
                    statusLabel.setText(loginErrorMessage(ex)));
            return null;
        });
    }

    // ── QR challenge ────────────────────────────────────────────────────────

    private void startChallenge() {
        stopTimers();
        hidePicker();
        showQrElements();

        titleLabel.setText(i18n.t("login.title"));
        subtitleLabel.setText(i18n.t("login.subtitle"));

        try {
            challenge = auth.newChallenge();
            qrImage.setImage(QRCodeUtil.generate(challenge.qrPayload(), QR_SIZE));
            statusLabel.setText(i18n.t("login.status.waiting"));

            pollTimeline = new Timeline(new KeyFrame(POLL_INTERVAL, e -> poll()));
            pollTimeline.setCycleCount(Timeline.INDEFINITE);
            pollTimeline.play();

            expiryTimeline = new Timeline(new KeyFrame(QR_TTL, e -> expire()));
            expiryTimeline.play();
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText(loginErrorMessage(e));
        }
    }

    private void poll() {
        CompletableFuture.supplyAsync(() -> auth.pollStatus(challenge.tokenUuid()))
                .thenAccept(result -> Platform.runLater(() -> {
                    try {
                        if (result.status() == AuthService.Status.VALIDATED) {
                            stopTimers();
                            onAuthenticated.accept(result.session());
                        } else if (result.status() == AuthService.Status.EXPIRED) {
                            expire();
                        }
                    } catch (Exception ex) {
                        statusLabel.setText("Redirection error: " + ex.getMessage());
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                            statusLabel.setText(loginErrorMessage(ex)));
                    return null;
                });
    }

    private void expire() {
        stopTimers();
        statusLabel.setText(i18n.t("login.status.expired"));
    }

    // ── UI toggles ───────────────────────────────────────────────────────────

    private void hidePicker() {
        if (pickerBox != null) {
            pickerBox.setVisible(false);
            pickerBox.setManaged(false);
        }
    }

    private void hideQrElements() {
        qrImage.setVisible(false);
        qrImage.setManaged(false);
        refreshButton.setVisible(false);
        refreshButton.setManaged(false);
        if (openBrowserButton != null) {
            openBrowserButton.setVisible(false);
            openBrowserButton.setManaged(false);
        }
    }

    private void showQrElements() {
        qrImage.setVisible(true);
        qrImage.setManaged(true);
        refreshButton.setVisible(true);
        refreshButton.setManaged(true);
        if (openBrowserButton != null) {
            openBrowserButton.setVisible(true);
            openBrowserButton.setManaged(true);
        }
    }

    // ── Error mapping ────────────────────────────────────────────────────────

    /**
     * Inspects the exception chain and returns a user-friendly message
     * distinguishing API-down, timeout, DNS failure, auth error, etc.
     */
    private String loginErrorMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.net.ConnectException) {
                return i18n.t("login.error.api_down");
            }
            if (cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException) {
                return i18n.t("login.error.timeout");
            }
            if (cause instanceof java.net.UnknownHostException) {
                return i18n.t("login.error.dns");
            }
            if (cause instanceof java.io.IOException) {
                String msg = cause.getMessage();
                if (msg != null) {
                    if (msg.contains("401") || msg.contains("403")) {
                        return i18n.t("login.error.auth");
                    }
                    if (msg.contains("500") || msg.contains("502") || msg.contains("503")) {
                        return i18n.t("login.error.server", msg.replaceAll(".*?(\\d{3}).*", "$1"));
                    }
                }
                return i18n.t("login.error.network", msg != null ? msg : cause.getClass().getSimpleName());
            }
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return i18n.t("login.error.timeout");
            }
            cause = cause.getCause();
        }
        // Fallback: unwrap the wrapper exception
        Throwable root = ex.getCause() != null ? ex.getCause() : ex;
        return i18n.t("login.error.network", root.getMessage() != null
                ? root.getMessage() : root.getClass().getSimpleName());
    }

    // ── Button actions ───────────────────────────────────────────────────────

    @FXML private void onRefresh() { startChallenge(); }

    @FXML private void onDevLogin() {
        stopTimers();
        onAuthenticated.accept(auth.simulateDevLogin());
    }

    @FXML private void onOpenBrowser() {
        if (challenge != null && challenge.scanUrl() != null) {
            CompletableFuture.runAsync(() -> {
                try { Desktop.getDesktop().browse(new URI(challenge.scanUrl())); }
                catch (Exception e) {
                    System.err.println("Cannot open browser: " + e.getMessage());
                }
            });
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void stopTimers() {
        if (pollTimeline != null) pollTimeline.stop();
        if (expiryTimeline != null) expiryTimeline.stop();
    }

    private void applyTexts() {
        titleLabel.setText(i18n.t("login.title"));
        subtitleLabel.setText(i18n.t("login.subtitle"));
        refreshButton.setText(i18n.t("login.refresh"));
        devLoginButton.setText(i18n.t("login.dev"));
        if (openBrowserButton != null) openBrowserButton.setText("Open in browser");
    }
}
