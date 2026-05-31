package tech.nabor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import java.time.Instant;

import tech.nabor.api.ConnectedUser;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.error.NaborException;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.api.model.enums.MessagePolicy;
import tech.nabor.api.model.enums.UserRole;
import tech.nabor.api.model.enums.Visibility;
import tech.nabor.api.model.user.User;
import tech.nabor.service.AuthService;
import tech.nabor.ui.LoginController;
import tech.nabor.ui.MainController;
import tech.nabor.ui.UiNaborReporter;
import tech.nabor.ui.i18n.I18nManager;
import tech.nabor.ui.theme.ThemeManager;


public class NaborApp extends Application {

    private AppContext app;
    private I18nManager i18n;
    private ThemeManager theme;
    private StackPane contentHolder;

    @Override
    public void init() {
        app = Bootstrap.create();
    }

    @Override
    public void start(Stage stage) throws Exception {
        StackPane sceneRoot = new StackPane();
        contentHolder = new StackPane();
        sceneRoot.getChildren().add(contentHolder);

        NaborReporter reporter = app.pluginContext().getReporter();
        if (reporter instanceof UiNaborReporter uiReporter) {
            uiReporter.attachToastLayer(sceneRoot);
        }

        Scene scene = new Scene(sceneRoot, 1024, 720);

        theme = new ThemeManager(scene, app.settings());
        theme.applySaved();

        i18n = new I18nManager(app.settings());

        showLogin();

        stage.setTitle("Nabor Services");
        stage.setScene(scene);
        stage.show();
    }

    private void showLogin() throws Exception {
        AuthService auth = new AuthService(app.pluginContext().getHttpClient());
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login-view.fxml"));
        Parent root = loader.load();
        LoginController controller = loader.getController();
        controller.init(auth, i18n, this::onAuthenticated);
        contentHolder.getChildren().setAll(root);
    }

    private void onAuthenticated(AuthService.Session session) {
        ConnectedUser user = app.pluginContext().getConnectedUser();
        if (user instanceof MutableConnectedUser mutable) {
            mutable.connect(session.userId(), session.email(), session.role());
        }

        ensureLocalUser(session);

        app.registry().loadAll(app.pluginContext());

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
            Parent root = loader.load();
            MainController controller = loader.getController();
            controller.init(app, i18n);
            controller.setThemeManager(theme);
            contentHolder.getChildren().setAll(root);
        } catch (Exception e) {
            app.pluginContext().getReporter().reportError(new NaborException(
                    NaborException.Kind.HTTP_ERROR, "Échec du chargement de l'interface principale", e));
        }
    }

  
    private void ensureLocalUser(AuthService.Session session) {
        SqliteRepository db = app.pluginContext().getDb();
        if (db.users().findById(session.userId()).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        db.users().save(new User(
                session.userId(), "Dev", "Admin", session.email(),
                "", null, null, null,
                Visibility.public_, null, MessagePolicy.open, i18n.locale(),
                null, null, UserRole.valueOf(session.role()),
                now, null, now, null, null));
    }

    @Override
    public void stop() {
        if (app != null) {
            app.registry().shutdownAll();
        }
    }
}
