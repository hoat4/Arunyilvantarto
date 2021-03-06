package arunyilvantarto.ui;

import arunyilvantarto.Main;
import arunyilvantarto.domain.DataRoot;
import arunyilvantarto.events.InventoryEvent;
import arunyilvantarto.domain.User;
import arunyilvantarto.events.*;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import org.tbee.javafx.scene.layout.MigPane;

import static arunyilvantarto.ui.UIUtil.TableBuilder.UNLIMITED_WIDTH;
import static java.util.Comparator.comparing;
import static javafx.beans.binding.Bindings.createBooleanBinding;

public class UsersTab {

    private final Main app;
    private final DataRoot data;

    private final MigPane userViewContainer = new MigPane("fill", "0[]0", "0[]0");

    private UserView userView;
    private TableView<User> usersTable;

    public UsersTab(Main app) {
        this.app = app;
        this.data = app.dataRoot;
    }

    public Node build() {
        return new MigPane("fill, insets unrelated", "[] unrelated [grow]", "[] [grow]").
                add(newUserButton(), "grow").
                add(userViewContainer, "grow, spany, wrap").
                add(usersTable(), "grow");
    }

    public void onEvent(InventoryEvent op) {
        if (op instanceof AddUserOp || op instanceof ChangeRoleOp || op instanceof RenameUserOp || op instanceof SetUserDeletedOp) {
            usersTable.getItems().setAll(app.dataRoot.users);
            usersTable.refresh();
        }
        if (userView != null)
            userView.onEvent(op);
    }

    private Button newUserButton() {
        Button button = new Button("Felhasználó hozzáadása");
        button.setOnAction(evt -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Új felhasználó");
            dialog.setHeaderText("Felhasználó hozzáadása");
            dialog.setContentText("Név: ");
            dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(createBooleanBinding(() -> {
                String n = dialog.getEditor().getText();
                return n.isBlank() || data.users.stream().anyMatch(u -> u.name.equals(n));
            }, dialog.getEditor().textProperty()));
            dialog.showAndWait().ifPresent(s -> {
                User user = new User();
                user.name = s;
                user.passwordHash = null;
                user.role = User.Role.STAFF;
                app.onEvent(new AddUserOp(user));
            });
        });
        return button;
    }

    private TableView<User> usersTable() {
        usersTable = new UIUtil.TableBuilder<>(app.dataRoot.users).
                col("Név", 230, UNLIMITED_WIDTH, u -> u.name, u -> u.deleted ? "inactive-user-cell" : null).
                col("Típus", 180, UNLIMITED_WIDTH, u -> roleToString(u.role), u -> u.deleted ? "inactive-user-cell" : null).
                onSelected(user -> {
                    boolean showStaffBill = userView != null && userView.staffBillShown();
                    userViewContainer.getChildren().clear();
                    if (user == null) {
                        userView = null;
                    } else {
                        userView = new UserView(app, user);
                        userViewContainer.add(userView.build(showStaffBill), "grow");
                    }
                }).
                build();

        usersTable.setSortPolicy(tv -> {
            if (usersTable.getSortOrder().isEmpty()) {
                FXCollections.sort(tv.getItems(), comparing(u -> u.deleted));
                return true;
            } else
                return TableView.DEFAULT_SORT_POLICY.call(tv);
        });

        return usersTable;
    }

    void showUser(User user) {
        usersTable.getSelectionModel().select(user);
    }

    private String roleToString(User.Role role) {
        switch (role) {
            case ADMIN:
            case ROOT:
                return "admin";
            case STAFF:
                return "vasútszemélyzet";
            case SELLER:
                return "eladó";
            default:
                throw new UnsupportedOperationException(role.toString());
        }
    }
}
