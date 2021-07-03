package arunyilvantarto.ui;

import arunyilvantarto.Main;
import arunyilvantarto.domain.Article;
import arunyilvantarto.operations.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.tbee.javafx.scene.layout.MigPane;

import java.time.Instant;
import java.util.ArrayList;

import static javafx.beans.binding.Bindings.createBooleanBinding;

public class ArticlesTab {

    public final Main main;
    private final MigPane articleViewContainer = new MigPane("fill", "0[]0", "0[]0");
    private ArticleView visibleArticleView;
    private TableView<Article> articlesTable;

    public ArticlesTab(Main main) {
        this.main = main;
    }

    public Node build() {
        Button newArticleButton = new Button("Új árucikk");
        newArticleButton.setOnAction(evt -> newArticle());

        articlesTable = articlesTable();

        return new MigPane("fill", "[] [grow 1]", "[] [grow 1]").
                add(newArticleButton, "grow").
                add(articleViewContainer, "spany, grow, wrap").
                add(articlesTable, "grow");
    }

    public void onEvent(AdminOperation op) {
        if (op instanceof AddArticleOp)
            articlesTable.getItems().add(((AddArticleOp) op).article);
        if (op instanceof DeleteArticleOp) {
            final Article article = ((DeleteArticleOp) op).article;
            if (visibleArticleView != null && visibleArticleView.article.equals(article))
                articleViewContainer.getChildren().clear();
            articlesTable.getItems().remove(article);
        }

        if (op instanceof ChangeArticleOp || op instanceof AddItemOp)
            articlesTable.refresh();

        if (visibleArticleView != null)
            visibleArticleView.onEvent(op);
    }

    @SuppressWarnings("unchecked")
    private TableView<Article> articlesTable() {
        TableView<Article> table = new TableView<>();

        table.setPlaceholder(new Label("Nincs termék bejegyezve"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Article, String> nameColumn = new TableColumn<>("Név");
        nameColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name));

        TableColumn<Article, String> barcodeColumn = new TableColumn<>("Vonalkód");
        barcodeColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().barCode));

        TableColumn<Article, Object> priceColumn = new TableColumn<>("Eladási ár");
        priceColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().sellingPrice == -1 ? "" : c.getValue().sellingPrice));

        TableColumn<Article, Integer> quantityColumn = new TableColumn<>("Mennyiség");
        quantityColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().stockQuantity));

        // TODO kéne szólni JavaFX-eseknek, hogy csináljanak végre normális column resize policy-t, egyrészt JDK-8089280,
        //      másrészt nem is lehet értelmes módon most column weight-ot megadni (asszem azt még Swingben is lehet)

        nameColumn.setMinWidth(250);
        barcodeColumn.setMinWidth(130);
        priceColumn.setMinWidth(100);
        quantityColumn.setMinWidth(100);
        table.getColumns().addAll(nameColumn, barcodeColumn, priceColumn, quantityColumn);

        table.getSelectionModel().selectedItemProperty().addListener((o, oldValue, newValue) -> showArticle(newValue));
        table.getItems().addAll(main.dataRoot.articles);

        MenuItem deleteMenuItem = new MenuItem("Törlés");
        deleteMenuItem.setOnAction(evt -> {
            Article article = table.getSelectionModel().getSelectedItem();

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Árucikk törlése");
            alert.setHeaderText("\"" + article.name + "\" törlése");
            alert.setContentText("Biztos törlöd?");

            ButtonType deleteButtonType = new ButtonType("Törlés", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButtonType = new ButtonType("Mégsem", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getDialogPane().getButtonTypes().setAll(deleteButtonType, cancelButtonType);
            alert.getDialogPane().lookupButton(cancelButtonType).requestFocus();

            if (alert.showAndWait().get() == deleteButtonType) {
                main.executeOperation(new DeleteArticleOp(article));
            }
        });
        deleteMenuItem.disableProperty().bind(createBooleanBinding(() ->
                table.getSelectionModel().getSelectedItem() == null, table.getSelectionModel().selectedItemProperty()));
        table.setContextMenu(new ContextMenu(deleteMenuItem));

        return table;
    }

    private void showArticle(Article article) {
        articleViewContainer.getChildren().clear();
        articleViewContainer.add((visibleArticleView = new ArticleView(this, article)).build(), "grow");
    }

    private void newArticle() {
        Dialog<Article> dialog = new Dialog<>();
        dialog.setTitle("Új árucikk");
        dialog.setHeaderText("Termék felvitele");

        DialogPane d = dialog.getDialogPane();
        ButtonType addButtonType = new ButtonType("Hozzáadás", ButtonBar.ButtonData.OK_DONE);

        d.getButtonTypes().addAll(
                addButtonType,
                new ButtonType("Mégsem", ButtonBar.ButtonData.CANCEL_CLOSE)
        );

        TextField nameField = new TextField();
        TextField priceField = new TextField();
        TextField barcodeField = new TextField();

        Platform.runLater(nameField::requestFocus);

        d.setContent(new MigPane().
                add(new Label("Név: ")).
                add(nameField, "wrap").
                add(new Label("Eladási ár: ")).
                add(priceField, "wrap").
                add(new Label("Vonalkód: ")).
                add(barcodeField, "wrap"));
        d.getStylesheets().add("/arunyilvantarto/app.css");

        d.lookupButton(addButtonType).disableProperty().bind(createBooleanBinding(() ->
                        nameField.getText().isEmpty() || !priceField.getText().matches("[0-9]+") ||
                                !barcodeField.getText().matches("([0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])?"),
                nameField.textProperty(), priceField.textProperty(), barcodeField.textProperty()));

        dialog.setResultConverter(b -> {
            if (b == addButtonType) {
                Article a = new Article();
                a.name = nameField.getText();
                a.timestamp = Instant.now();
                a.barCode = barcodeField.getText().isEmpty() ? null : barcodeField.getText();
                a.sellingPrice = Integer.parseInt(priceField.getText());
                a.items = new ArrayList<>();
                return a;
            } else
                return null;
        });

        dialog.showAndWait().ifPresent(p -> {
            AddArticleOp op = new AddArticleOp(p);
            main.executeOperation(op);
            articlesTable.getSelectionModel().select(p);
        });
    }

}