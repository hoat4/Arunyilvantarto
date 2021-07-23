package arunyilvantarto.ui;

import arunyilvantarto.Main;
import arunyilvantarto.OperationListener;
import arunyilvantarto.SalesIO;
import arunyilvantarto.SalesVisitor;
import arunyilvantarto.domain.Article;
import arunyilvantarto.domain.Sale;
import arunyilvantarto.domain.SellingPeriod;
import arunyilvantarto.domain.User;
import arunyilvantarto.operations.AdminOperation;
import arunyilvantarto.operations.ClosePeriodOp;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.tbee.javafx.scene.layout.MigPane;

import java.time.Instant;
import java.util.*;

import static arunyilvantarto.ui.UIUtil.TableBuilder.UNLIMITED_WIDTH;
import static javafx.beans.binding.Bindings.*;
import static javafx.scene.input.KeyCode.*;
import static javafx.scene.layout.Region.USE_PREF_SIZE;

public class SellingTab implements OperationListener {

    private final Main main;

    private TableView<Sale> itemsTable;

    private int sumPrice;
    private Label sumPriceLabel;
    private SellingPeriod sellingPeriod;

    private Node mainPane;
    private Node tickIconPane;

    @SuppressWarnings("ConstantConditions")
    private final Image tickIcon = new Image(SellingTab.class.getResource("/arunyilvantarto/tickIcon.png").toString());

    private SellingTab(Main main) {
        this.main = main;
    }

    public static void begin(Main app, Runnable preloadDoneCallback, Runnable cancelCallback) {
        SellingTab sellingTab = new SellingTab(app);
        Scene scene = app.preload(sellingTab.build());

        Platform.runLater(() -> {
            if (preloadDoneCallback != null)
            preloadDoneCallback.run();

            SellingPeriod lastSellingPeriod = lastSellingPeriod(app.salesIO);
            if (lastSellingPeriod != null && lastSellingPeriod.endTime == null)
                throw new IllegalStateException("nem volt lezárva");

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Nyitás");
            dialog.setContentText("Kasszában lévő váltópénz: ");
            if (lastSellingPeriod != null)
                dialog.getEditor().setText(Integer.toString(lastSellingPeriod.closeCash));
            dialog.showAndWait().ifPresentOrElse(s -> {
                SellingPeriod sellingPeriod = new SellingPeriod();
                sellingPeriod.id = lastSellingPeriod == null ? 1 : lastSellingPeriod.id + 1;
                sellingPeriod.username = app.logonUser.name;
                sellingPeriod.beginTime = Instant.now();
                sellingPeriod.openCash = Integer.parseInt(s);
                sellingPeriod.sales = new ArrayList<>();
                app.currentSellingPeriod = sellingPeriod;

                app.salesIO.beginPeriod(sellingPeriod);

                sellingTab.sellingPeriod = sellingPeriod;
                app.switchPage(scene, sellingTab);
            }, ()->{
                if (cancelCallback != null)
                    cancelCallback.run();
            });
        });
    }

    public static SellingPeriod lastSellingPeriod(SalesIO salesIO) {
        SellingPeriod[] a = new SellingPeriod[1];
        salesIO.read(new SalesVisitor() {

            @Override
            public void beginPeriod(SellingPeriod period) {
                a[0] = period;
            }

        });
        return a[0];
    }

    public Node build() {
        sumPriceLabel = new Label();
        sumPriceLabel.setVisible(false);
        sumPriceLabel.setPadding(new Insets(10, 0, 0, 0)); // TODO gaptop miért marad ott, ha hidemode 2?
        sumPriceLabel.getStyleClass().add("sumPriceLabel");

        mainPane = new MigPane("align center center, wrap 1", "[70%:]", "unrelated [] related [grow] 0 [] 15 [] 18").
                add(topToolbar(), "grow").
                add(salesTable(), "grow").
                add(sumPriceLabel, "hidemode 2, align right").
                add(bottomToolbar(), "grow");

        ImageView imageView = new ImageView(tickIcon);
        imageView.setScaleX(.2);
        imageView.setScaleY(.2);
        tickIconPane = new MigPane("align center center").add(imageView);
        tickIconPane.setOpacity(0);
        tickIconPane.setMouseTransparent(true);

        StackPane sp = new StackPane(mainPane, tickIconPane);
        sp.getStyleClass().add("selling-tab");
        return sp;
    }

    private Node topToolbar() {
        Button logoutButton = new Button(main.logonUser.role == User.Role.SELLER ? "Kijelentkezés" : "Zárás");
        logoutButton.setOnAction(evt -> {
            if (close()) {
                if (main.logonUser.role == User.Role.SELLER) {
                    final LoginForm loginForm = new LoginForm(main);
                    main.switchPage(loginForm.buildLayout(), null);
                } else {
                    AdminPage adminPage = new AdminPage(main);
                    main.switchPage(adminPage.build(), adminPage);
                }
            }
        });

        TextField barcodeField = new TextField();
        UIUtil.barcodeField(barcodeField, text -> {
            if (text.length() > 20) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Érvénytelen vonalkód");
                alert.setHeaderText("A megadott vonalkód túl hosszú");
                alert.setContentText("Valószínűleg olyan vonalkód is be lett olvasva, amihez nem tartozik termék. ");
                alert.showAndWait();
                return;
            }

            main.dataRoot.articles.stream().filter(a -> Objects.equals(a.barCode, text)).findAny().ifPresent(a -> {
                addArticle(a);
                Platform.runLater(() -> barcodeField.setText(""));
            });
        });
        barcodeField.focusedProperty().addListener((o, old, value) -> {
            if (!value)
                barcodeField.requestFocus();
        });


        return new MigPane("align center center", "[] push [] push []").
                add(new Label("Pénztáros: " + main.logonUser.name)).
                add(barcodeField).
                add(logoutButton);
    }

    private TableView<Sale> salesTable() {
        return itemsTable = new UIUtil.TableBuilder<Sale>(new ArrayList<>()).
                col("Termék", 0, UNLIMITED_WIDTH, sale -> sale.article.name).
                col("Ár", 150, 150, sale -> sale.pricePerProduct).
                col("Mennyiség", 150, 150, sale -> sale.quantity).
                col("Összesen", 150, 150, sale -> sale.pricePerProduct * sale.quantity).
                placeholder("Olvasd be a termékek vonalkódját").
                build();
    }

    private Node bottomToolbar() {
        Button selectArticleManuallyButton = new Button("Termék kiválasztása (F6)");
        selectArticleManuallyButton.setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        selectArticleManuallyButton.setOnAction(evt -> selectArticleManually());
        UIUtil.assignShortcut(selectArticleManuallyButton, new KeyCodeCombination(F6));

        Button stornoButton = new Button("Sztornó (F7)");
        stornoButton.setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        stornoButton.setOnAction(evt -> stornoByBarcode());
        UIUtil.assignShortcut(stornoButton, new KeyCodeCombination(F7));

        Button payFromStaffBillButton = new Button("Személyzeti számlára (F8)");
        payFromStaffBillButton.setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        payFromStaffBillButton.setOnAction(evt -> payToStaffBill());
        UIUtil.assignShortcut(payFromStaffBillButton, new KeyCodeCombination(F8));

        Button payByCashButton = new Button("Fizetés készpénzzel (F9)");
        payByCashButton.setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        payByCashButton.setOnAction(evt -> payByCash());
        UIUtil.assignShortcut(payByCashButton, new KeyCodeCombination(F9));

        Button payByCardButton = new Button("Fizetés kártyával (F10)");
        payByCardButton.setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        payByCardButton.setOnAction(evt -> payByCard());
        UIUtil.assignShortcut(payByCardButton, new KeyCodeCombination(F10));

        ObservableValue<Boolean> hasNoItems = isEmpty(itemsTable.getItems());
        stornoButton.disableProperty().bind(hasNoItems);
        payFromStaffBillButton.disableProperty().bind(hasNoItems);
        payByCardButton.disableProperty().bind(hasNoItems);
        payByCashButton.disableProperty().bind(hasNoItems);

        return new FlowPane(selectArticleManuallyButton, stornoButton, payFromStaffBillButton, payByCashButton, payByCardButton) {
            {
                setHgap(10);
                setVgap(10);
            }

            @Override
            protected double computePrefWidth(double forHeight) {
                if (forHeight != -1)
                    return super.computePrefWidth(forHeight);

                return getInsets().getLeft() + getInsets().getRight() +
                        snapSpaceX(this.getHgap()) * (getChildren().size() - 1) +
                        getChildren().stream().mapToDouble(c -> snapSizeX(c.prefWidth(-1))).sum();
            }
        };
    }

    private TableView<Article> articlesTable() {
        return new UIUtil.TableBuilder<>(main.dataRoot.articles).
                col("Árucikk", 100, UNLIMITED_WIDTH, a -> a.name).
                col("Ár", 100, 100, a -> Integer.toString(a.sellingPrice)).
                col("Mennyiség", 150, 150, a -> Integer.toString(a.stockQuantity)).
                build();
    }

    @Override
    public void onEvent(AdminOperation op) {

    }

    private void selectArticleManually() {
        Dialog<Article> dialog = new Dialog();
        dialog.setTitle("Termékek");

        TableView<Article> articlesTable = articlesTable();
        SearchableTable<Article> articleSearchableTable = new SearchableTable<>(articlesTable, a -> List.of(a.name, a.barCode));
        articleSearchableTable.textField.setFocusTraversable(true);

        Platform.runLater(articleSearchableTable.textField::requestFocus);
        dialog.getDialogPane().setContent(articleSearchableTable.build());
        dialog.getDialogPane().setPrefWidth(900);
        dialog.getDialogPane().setPrefHeight(800);
        dialog.setResizable(true);

        ButtonType addButtonType = new ButtonType("Kiválasztás", ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes().addAll(
                addButtonType,
                new ButtonType("Mégsem", ButtonBar.ButtonData.CANCEL_CLOSE)
        );
        dialog.getDialogPane().getStylesheets().add("/arunyilvantarto/selling-dialog.css");
        dialog.setResultConverter(b -> b == addButtonType ? articlesTable.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().ifPresent(this::addArticle);
    }


    private void addArticle(Article a) {
        final Sale s = new Sale();
        s.pricePerProduct = a.sellingPrice;
        s.quantity = 1;
        s.article = a;
        s.seller = main.logonUser.name;
        s.timestamp = Instant.now();
        itemsTable.getItems().add(s);

        sumPrice += s.quantity * s.article.sellingPrice;
        updateSumPrice();
    }

    private void updateSumPrice() {
        sumPriceLabel.setText("Összesen: " + sumPrice + " Ft");
        sumPriceLabel.setVisible(sumPrice != 0);
    }

    private void payByCash() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Fizetés");
        dialog.setHeaderText("Fizetés készpénzzel");
        dialog.setContentText("Kapott készpénz: ");
        dialog.getEditor().setText(Integer.toString(sumPrice));
        dialog.getDialogPane().getStylesheets().add("/arunyilvantarto/selling-dialog.css");
        dialog.showAndWait().ifPresent(s -> {
            int receivedCash = Integer.parseInt(s);
            if (receivedCash < sumPrice) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Hibás összeg");
                alert.setHeaderText("Alulfizetés " + (sumPrice - receivedCash) + " forinttal");
                alert.setContentText("A kapott összeg (" + receivedCash + " Ft) " + " kevesebb, mint a vásárolt termékek árának összege. ");
                alert.getDialogPane().getStylesheets().add("/arunyilvantarto/selling-dialog.css");
                alert.showAndWait();
                payByCash();
                return;
            }

            if (receivedCash > sumPrice) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Visszajáró");
                alert.setHeaderText("Visszajáró: " + (receivedCash - sumPrice) + " Ft");
                alert.getDialogPane().getStylesheets().add("/arunyilvantarto/selling-dialog.css");
                alert.showAndWait();
            }

            for (Sale sale : itemsTable.getItems())
                sale.billID = new Sale.PeriodBillID(sellingPeriod.id);

            payDone();
        });

    }

    private void payByCard() {
        for (Sale sale : itemsTable.getItems())
            sale.billID = new Sale.PeriodCardBillID(sellingPeriod.id);

        payDone();
    }

    private void payToStaffBill() {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Személyzeti számlák");

        TableView<User> usersTable = new UIUtil.TableBuilder<>(main.dataRoot.users).
                col("Felhasználók", 0, UNLIMITED_WIDTH, a -> a.name).
                build();

        SearchableTable<User> articleSearchableTable = new SearchableTable<>(usersTable, u -> List.of(u.name));
        articleSearchableTable.textField.setFocusTraversable(true);

        Platform.runLater(articleSearchableTable.textField::requestFocus);
        dialog.getDialogPane().setContent(articleSearchableTable.build());
        dialog.getDialogPane().setPrefWidth(900);
        dialog.getDialogPane().setPrefHeight(800);
        dialog.setResizable(true);

        ButtonType addButtonType = new ButtonType("Kiválasztás", ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes().addAll(
                addButtonType,
                new ButtonType("Mégsem", ButtonBar.ButtonData.CANCEL_CLOSE)
        );
        dialog.getDialogPane().getStylesheets().add("/arunyilvantarto/selling-dialog.css");
        dialog.setResultConverter(b -> b == addButtonType ? usersTable.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().ifPresent(staffBill -> {
            itemsTable.getItems().forEach(sale -> sale.billID = new Sale.StaffBillID(staffBill.name));
            payDone();
        });

    }

    private void payDone() {
        mainPane.setOpacity(0);
        tickIconPane.setOpacity(.8);

        Timeline delay = new Timeline(new KeyFrame(Duration.seconds(.7), event -> {
            mainPane.setOpacity(1);
            tickIconPane.setOpacity(0);
        }));
        delay.play();

        sellingPeriod.sales.addAll(itemsTable.getItems());

        main.executor.execute(() -> {
            itemsTable.getItems().forEach(main.salesIO::sale);
            Platform.runLater(() -> {
                itemsTable.getItems().clear();
                sumPrice = 0;
                sumPriceLabel.setVisible(false);
            });
        });
    }

    private void stornoByBarcode() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Sztornó");
        dialog.setHeaderText("Olvasd be a törlendő termék vonalkódját");
        dialog.getDialogPane().getStylesheets().add("/arunyilvantarto/selling-dialog.css");
        UIUtil.barcodeField(dialog.getEditor(), barcode -> {
            main.dataRoot.articles.stream().filter(a -> Objects.equals(a.barCode, barcode)).findAny().ifPresent(article -> {
                dialog.close();

                List<Sale> sales = new ArrayList<>(itemsTable.getItems());
                Collections.reverse(sales);
                sales.stream().filter(s -> Objects.equals(s.article.barCode, barcode)).findFirst().ifPresentOrElse(s -> {
                    itemsTable.getItems().remove(s);
                    sumPrice -= s.quantity * article.sellingPrice;
                    updateSumPrice();
                }, () -> Platform.runLater(() -> { // időnként JavaFX bug miatt a dialógus teljesen fehér volt e nélkül
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Sztornó sikertelen");
                    alert.setHeaderText("Ez a termék nem volt a megvásárolva");
                    alert.setContentText("Termék neve: " + article.name);
                    alert.showAndWait().ifPresent(b -> stornoByBarcode());
                }));
            });
        });
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(createBooleanBinding(() ->
                !UIUtil.isBarcode(dialog.getEditor().getText()), dialog.getEditor().textProperty()));
        dialog.showAndWait().ifPresent(s -> {
            Platform.runLater(() -> { // időnként JavaFX bug miatt a dialógus teljesen fehér volt e nélkül
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Sztornó sikertelen");
                alert.setHeaderText("Ismeretlen vonalkód");
                alert.setContentText("Nem ismert olyan termék, melynek ez lenne a vonalkódja. ");
                alert.showAndWait();
                stornoByBarcode();
            });
        });
    }

    public boolean close() {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Zárás");
        d.setHeaderText(sellingPeriod.remainingCash() + " Ft maradt elvileg a kasszában. ");
        d.setContentText("Kasszában hagyott váltópénz: ");
        d.getDialogPane().getButtonTypes().remove(ButtonType.CANCEL);
        d.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(
                createBooleanBinding(() -> !d.getEditor().getText().matches("[0-9]+"), d.getEditor().textProperty()));

        final Optional<String> o = d.showAndWait();
        o.ifPresent(s -> {
            sellingPeriod.closeCash = Integer.parseInt(s);
            closePeriod(main, sellingPeriod);
        });
        return o.isPresent();
    }

    public static void closePeriod(Main main, SellingPeriod sellingPeriod) {
        sellingPeriod.endTime = Instant.now();
        main.salesIO.endPeriod(sellingPeriod);

        Map<String, Integer> purchasedProducts = new HashMap<>();
        for (Sale sale : sellingPeriod.sales) {
            purchasedProducts.put(sale.article.name, purchasedProducts.getOrDefault(sale.article.name, 0) + sale.quantity);
        }

        Map<String, Integer> staffBillGrowths = new HashMap<>();
        for (Sale sale : sellingPeriod.sales) {
            if (sale.billID instanceof Sale.StaffBillID) {
                String n = ((Sale.StaffBillID) sale.billID).username;
                staffBillGrowths.put(n, purchasedProducts.getOrDefault(n, 0) + sale.quantity * sale.pricePerProduct);
            }
        }

        main.executeOperation(new ClosePeriodOp(sellingPeriod, purchasedProducts, staffBillGrowths));
    }
}
