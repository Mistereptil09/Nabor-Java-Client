package tech.nabor.ui;

import java.util.function.Consumer;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
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

    private AuthService auth;
    private I18nManager i18n;
    private Consumer<AuthService.Session> onAuthenticated;

    private AuthService.QrChallenge challenge;
    private Timeline pollTimeline;
    private Timeline expiryTimeline;

    public void init(AuthService auth, I18nManager i18n, Consumer<AuthService.Session> onAuthenticated) {
        this.auth = auth;
        this.i18n = i18n;
        this.onAuthenticated = onAuthenticated;
        applyTexts();
        startChallenge();
    }

    private void applyTexts() {
        titleLabel.setText(i18n.t("login.title"));
        subtitleLabel.setText(i18n.t("login.subtitle"));
        refreshButton.setText(i18n.t("login.refresh"));
        devLoginButton.setText(i18n.t("login.dev"));
    }

    private void startChallenge() {
        stopTimers();

        challenge = auth.newChallenge();
        qrImage.setImage(QRCodeUtil.generate(challenge.qrPayload(), QR_SIZE));
        statusLabel.setText(i18n.t("login.status.waiting"));

        pollTimeline = new Timeline(new KeyFrame(POLL_INTERVAL, e -> poll()));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();

        expiryTimeline = new Timeline(new KeyFrame(QR_TTL, e -> expire()));
        expiryTimeline.play();
    }

    private void poll() {
        if (auth.pollStatus(challenge.tokenUuid()) == AuthService.Status.VALIDATED) {
            stopTimers();
        }
    }

    private void expire() {
        stopTimers();
        statusLabel.setText(i18n.t("login.status.expired"));
    }

    @FXML
    private void onRefresh() {
        startChallenge();
    }

    @FXML
    private void onDevLogin() {
        stopTimers();
        onAuthenticated.accept(auth.simulateDevLogin());
    }

    private void stopTimers() {
        if (pollTimeline != null) {
            pollTimeline.stop();
        }
        if (expiryTimeline != null) {
            expiryTimeline.stop();
        }
    }
}
