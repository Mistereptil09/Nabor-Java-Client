package tech.nabor.plugin.social;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.social.Follow;
import tech.nabor.api.model.social.Friendship;
import tech.nabor.api.model.user.User;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;

public class SocialPlugin implements NaborPlugin {

    private PluginContext ctx;
    private ResourceBundle bundle;

    private VBox root;
    private TextField userIdField;
    private Label followersLabel, followingLabel, friendsLabel;
    private TableView<String[]> followersTable, followingTable, friendsTable;
    private Label placeholder;

    private Map<String, String> userNames = Map.of();

    @Override public String getId() { return "plugin-social"; }
    @Override public String getDisplayName() { return "Social Graph"; }
    @Override public void shutdown() {}

    @Override
    public void initialize(PluginContext ctx) {
        this.ctx = ctx;
        this.bundle = loadBundle();
    }

    @Override public Optional<Node> getView() {
        if (root == null) {
            try { buildUi(); } catch (Throwable e) { return Optional.empty(); }
        }
        return Optional.of(root);
    }

    // ── i18n ────────────────────────────────────────────────────────────────

    private ResourceBundle loadBundle() {
        String locale = "fr";
        try { locale = ctx.getI18n().getLocale(); } catch (Exception ignored) {}
        return ResourceBundle.getBundle("i18n/social/messages",
                java.util.Locale.forLanguageTag(locale));
    }
    private String t(String key, Object... args) {
        try { return java.text.MessageFormat.format(bundle.getString(key), args); }
        catch (Exception e) { return key; }
    }

    // ── UI ──────────────────────────────────────────────────────────────────

    private void buildUi() {
        loadUserNames();

        Label titleLabel = new Label(t("social.title"));
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        userIdField = new TextField();
        userIdField.setPromptText(t("social.prompt"));
        userIdField.setMaxWidth(300);
        userIdField.setOnAction(e -> onUserIdEntered(userIdField.getText().strip()));

        followersTable = buildTable();
        followingTable = buildTable();
        friendsTable = buildTable();

        followersLabel = new Label(t("social.followers", 0));
        followersLabel.setStyle("-fx-font-weight: bold;");
        followingLabel = new Label(t("social.following", 0));
        followingLabel.setStyle("-fx-font-weight: bold;");
        friendsLabel = new Label(t("social.friends", 0));
        friendsLabel.setStyle("-fx-font-weight: bold;");

        placeholder = new Label(t("social.noData"));
        placeholder.setStyle("-fx-text-fill: #8C8C8C;");

        TabPane tabs = new TabPane(
                tab(followersLabel, followersTable),
                tab(followingLabel, followingTable),
                tab(friendsLabel, friendsTable));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        root = new VBox(12, titleLabel,
                new Label(t("social.enterId")), userIdField,
                tabs, placeholder);
        root.setPadding(new Insets(16));

        clearTables();
    }

    private Tab tab(Label header, TableView<String[]> table) {
        VBox content = new VBox(6, header, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        content.setPadding(new Insets(8, 0, 0, 0));
        Tab t = new Tab();
        t.setContent(content);
        t.setClosable(false);
        t.textProperty().bind(header.textProperty());
        return t;
    }

    private TableView<String[]> buildTable() {
        TableView<String[]> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<String[], String> userCol = new TableColumn<>(t("social.col.user"));
        userCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()[0]));
        userCol.setPrefWidth(200);

        TableColumn<String[], String> sinceCol = new TableColumn<>(t("social.col.since"));
        sinceCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()[1]));
        sinceCol.setPrefWidth(150);

        t.getColumns().addAll(userCol, sinceCol);
        return t;
    }

    // ── User names ──────────────────────────────────────────────────────────

    private void loadUserNames() {
        userNames = new LinkedHashMap<>();
        for (User u : ctx.getDb().users().findAll()) {
            String display = u.firstName() + " " + u.lastName() + " (" + u.role().name() + ")";
            userNames.put(u.id(), display);
        }
    }

    private String resolveName(String userId) {
        return userNames.getOrDefault(userId, userId);
    }

    // ── Input ────────────────────────────────────────────────────────────────

    private void clearTables() {
        followersTable.getItems().clear();
        followingTable.getItems().clear();
        friendsTable.getItems().clear();
        placeholder.setVisible(true);
    }

    private void onUserIdEntered(String userId) {
        if (userId == null || userId.isBlank()) {
            clearTables();
            return;
        }
        placeholder.setVisible(false);

        // Followers: who follows this user
        List<Follow> followers = ctx.getDb().follows().findFollowersByUserId(userId);
        followersTable.setItems(buildRows(followers, Follow::followerId, Follow::followedAt));
        followersLabel.setText(t("social.followers", followers.size()));

        // Following: who this user follows
        List<Follow> following = ctx.getDb().follows().findFollowingByUserId(userId);
        followingTable.setItems(buildRows(following, Follow::followedId, Follow::followedAt));
        followingLabel.setText(t("social.following", following.size()));

        // Friends
        List<Friendship> friends = ctx.getDb().friendships().findByUserId(userId);
        friendsTable.setItems(buildFriendRows(friends, userId));
        friendsLabel.setText(t("social.friends", friends.size()));
    }

    private javafx.collections.ObservableList<String[]> buildRows(
            List<Follow> list, java.util.function.Function<Follow, String> nameFn,
            java.util.function.Function<Follow, Instant> timeFn) {
        return FXCollections.observableArrayList(
                list.stream().map(f -> new String[]{
                        resolveName(nameFn.apply(f)),
                        fmt(timeFn.apply(f))}).toList());
    }

    private javafx.collections.ObservableList<String[]> buildFriendRows(
            List<Friendship> list, String userId) {
        return FXCollections.observableArrayList(
                list.stream().map(f -> {
                    String other = f.user1Id().equals(userId) ? f.user2Id() : f.user1Id();
                    return new String[]{resolveName(other), fmt(f.friendedAt())};
                }).toList());
    }

    private String fmt(Instant i) {
        if (i == null) return "—";
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(java.util.Locale.forLanguageTag(bundle.getLocale().getLanguage()))
                .withZone(ZoneId.systemDefault()).format(i);
    }
}
