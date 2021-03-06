package arunyilvantarto.ui;

import arunyilvantarto.Main;
import arunyilvantarto.Security;
import arunyilvantarto.domain.DataRoot;
import arunyilvantarto.domain.Message;
import arunyilvantarto.domain.SellingPeriod;
import arunyilvantarto.domain.User;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import org.tbee.javafx.scene.layout.MigPane;

import java.util.Arrays;
import java.util.Optional;

import static javafx.beans.binding.Bindings.createBooleanBinding;

public class LoginForm {

    ValidatableField<? extends TextField> usernameField, passwordField;

    private final Main app;
    private final DataRoot data;
    private ProgressIndicator progressIndicator;
    private Node loginForm;

    public LoginForm(Main app) {
        this.app = app;
        this.data = app.dataRoot;

        Platform.runLater(() -> {
            app.executor.execute(() -> {
                // preload
                Security.hashPassword("");
                app.preload(new TableView<>());
            });
        });
    }

    public Region buildLayout() {
        usernameField = new ValidatableField<>(new TextField());
        passwordField = new ValidatableField<>(new PasswordField());

        Button loginButton = new Button("Belépés");
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(evt -> login());

        Label title = new Label("Belépés");
        title.setFont(Font.font(24));

        progressIndicator = new ProgressIndicator(-1);
        progressIndicator.setVisible(false);
        progressIndicator.setScaleX(2);
        progressIndicator.setScaleY(2);

        return new StackPane(
                loginForm = new MigPane("align 50% 50%", null,
                        "[] unrelated []0[] related []0[] unrelated []").

                        add(title, "span, center, wrap").

                        add(new Label("Név:")).
                        add(usernameField.field, "grow 1, wrap").
                        add(usernameField.errorLabel, "skip 1, wrap").

                        add(new Label("Jelszó:")).
                        add(passwordField.field, "grow 1, wrap").
                        add(passwordField.errorLabel, "skip 1, wrap").

                        add(loginButton, "span, growx 1"),
                progressIndicator);
    }

    private void login() {
        usernameField.clearError();
        passwordField.clearError();
        if (usernameField.field.getText().isBlank())
            usernameField.showError("Kötelező kitölteni");
        if (passwordField.field.getText().isBlank())
            passwordField.showError("Kötelező kitölteni");
        if (ValidatableField.hasError(usernameField, passwordField))
            return;


        Optional<User> o = data.users.stream().filter(u -> u.name.equals(usernameField.field.getText())).findAny();
        if (o.isEmpty() || o.get().deleted) {
            usernameField.showError("Ilyen nevű felhasználó nem létezik");
            return;
        }

        User u = o.get();
        if (u.passwordHash == null) {
            usernameField.showError("A felhasználó létezik, de nem léphet be eladóként");
            return;
        }

        byte[] b = Security.hashPassword(passwordField.field.getText());
        if (!Arrays.equals(b, u.passwordHash)) {
            passwordField.showError("Hibás jelszó");
            return;
        }

        app.logonUser = u;

        progressIndicator.setVisible(true);

        loginForm.setOpacity(0);

        /*FadeTransition transition = new FadeTransition(Duration.seconds(0), rootNode);
        transition.setFromValue(1);
        transition.setToValue(0);
        transition.setOnFinished(evt->latch.countDown());
        transition.play();*/


        app.executor.execute(() -> {
            SellingPeriod p = SellingTab.lastSellingPeriod(app.salesIO).lastSellingPeriod;
            if (p != null && p.endTime == null) {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);

                    TextInputDialog d1 = new TextInputDialog();
                    d1.setTitle("Zárás");
                    d1.setHeaderText("Az előző értékesítési periódus nem lett bezárva");
                    d1.setContentText("Bankkártyás forgalom: ");
                    d1.getDialogPane().getButtonTypes().remove(ButtonType.CANCEL);
                    d1.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(
                            createBooleanBinding(() -> !d1.getEditor().getText().matches("[0-9]+"), d1.getEditor().textProperty()));

                    Optional<String> o2 = d1.showAndWait();
                    if (o2.isEmpty()) {
                        loginForm.setOpacity(1);
                        app.logonUser = null;
                        return;
                    }
                    int creditCardRevenue = Integer.parseInt(o2.get());

                    int remainingCash = p.remainingCash(creditCardRevenue);
                    SellingTab.MoneyConfirmationResult result = SellingTab.confirmMoneyInCash(app, remainingCash);
                    if (!result.canContinue) {
                        loginForm.setOpacity(1);
                        app.logonUser = null;
                        return;
                    }

                    progressIndicator.setVisible(true);

                    p.closeCash = remainingCash;
                    p.closeCreditCardAmount = p.openCreditCardAmount + creditCardRevenue;
                    if (result.message != null)
                        result.message.subject = new Message.ClosePeriodSubject(p.id);
                    app.executor.execute(() -> SellingTab.closePeriod(app, p, result.message));
                    app.executor.execute(this::loadAndShowNextPage);
                });
            } else
                loadAndShowNextPage();
        });
    }


    public void loadAndShowNextPage() {
        long begin = System.nanoTime();
        switch (app.logonUser.role) {
            case ADMIN:
            case ROOT:
                AdminPage adminPage = new AdminPage(app);
                final Node n = adminPage.build();
                app.preload(n);
                Platform.runLater(() -> {
                    app.switchPage(n, adminPage);
                    System.out.println((System.nanoTime() - begin) / 1000000);
                });
                break;
            case SELLER:
                SellingTab.begin(app, () -> {
                    progressIndicator.setVisible(false);
                    System.out.println((System.nanoTime() - begin) / 1000000);
                }, () -> {
                    app.logonUser = null;
                    loginForm.setOpacity(1);
                });
                break;
            case STAFF:
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    app.logonUser = null;
                    loginForm.setOpacity(1);

                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Belépés");
                    alert.setHeaderText("Hiányzó jogosultság");
                    alert.setContentText("Nem léphetsz be eladóként, mert csak vonatszemélyzet vagy");
                    alert.showAndWait();
                });
                break;
            default:
                throw new UnsupportedOperationException(app.logonUser.name);
        }
    }

}