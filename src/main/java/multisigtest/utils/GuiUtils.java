package multisigtest.utils;

import com.google.common.base.Throwables;
import multisigtest.MainController;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.function.BiConsumer;

import static com.google.common.base.Preconditions.checkState;

public class GuiUtils {
    public static void runAlert(BiConsumer<Stage, AlertWindowController> setup) {
        try {
            // JavaFX2 doesn't actually have a standard alert template. Instead the Scene Builder app will create FXML
            // files for an alert window for you, and then you customise it as you see fit. I guess it makes sense in
            // an odd sort of way.
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            FXMLLoader loader = new FXMLLoader(GuiUtils.class.getResource("alert.fxml"));
            Pane pane = loader.load();
            AlertWindowController controller = loader.getController();
            setup.accept(dialogStage, controller);
            dialogStage.setScene(new Scene(pane));
            dialogStage.showAndWait();
        } catch (IOException e) {
            // We crashed whilst trying to show the alert dialog (this should never happen). Give up!
            throw new RuntimeException(e);
        }
    }

    public static void crashAlert(Throwable t) {
        t.printStackTrace();
        Throwable rootCause = Throwables.getRootCause(t);
        Runnable r = () -> {
            runAlert((stage, controller) -> controller.crashAlert(stage, rootCause.toString()));
            Platform.exit();
        };
        if (Platform.isFxApplicationThread())
            r.run();
        else
            Platform.runLater(r);
    }

    /** Show a GUI alert box for any unhandled exceptions that propagate out of this thread. */
    public static void handleCrashesOnThisThread() {
        Thread.currentThread().setUncaughtExceptionHandler((thread, exception) -> {
            GuiUtils.crashAlert(Throwables.getRootCause(exception));
        });
    }

    public static void informationalAlert(String message, String details, Object... args) {
        String formattedDetails = String.format(details, args);
        Runnable r = () -> runAlert((stage, controller) -> controller.informational(stage, message, formattedDetails));
        if (Platform.isFxApplicationThread())
            r.run();
        else
            Platform.runLater(r);
    }

    public static final int UI_ANIMATION_TIME_MSEC = 600;
    public static final Duration UI_ANIMATION_TIME = Duration.millis(UI_ANIMATION_TIME_MSEC);

    public static Animation fadeIn(Node ui) {
        return fadeIn(ui, 0);
    }

    public static Animation fadeIn(Node ui, int delayMillis) {
        ui.setCache(true);
        FadeTransition ft = new FadeTransition(Duration.millis(UI_ANIMATION_TIME_MSEC), ui);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.setOnFinished(ev -> ui.setCache(false));
        ft.setDelay(Duration.millis(delayMillis));
        ft.play();
        return ft;
    }

    public static Animation fadeOut(Node ui) {
        FadeTransition ft = new FadeTransition(Duration.millis(UI_ANIMATION_TIME_MSEC), ui);
        ft.setFromValue(ui.getOpacity());
        ft.setToValue(0.0);
        ft.play();
        return ft;
    }

    public static Animation fadeOutAndRemove(Pane parentPane, Node... nodes) {
        Animation animation = fadeOut(nodes[0]);
        animation.setOnFinished(actionEvent -> parentPane.getChildren().removeAll(nodes));
        return animation;
    }

    public static Animation fadeOutAndRemove(Duration duration, Pane parentPane, Node... nodes) {
        nodes[0].setCache(true);
        FadeTransition ft = new FadeTransition(duration, nodes[0]);
        ft.setFromValue(nodes[0].getOpacity());
        ft.setToValue(0.0);
        ft.setOnFinished(actionEvent -> parentPane.getChildren().removeAll(nodes));
        ft.play();
        return ft;
    }

    public static void blurOut(Node node) {
        GaussianBlur blur = new GaussianBlur(0.0);
        node.setEffect(blur);
        Timeline timeline = new Timeline();
        KeyValue kv = new KeyValue(blur.radiusProperty(), 10.0);
        KeyFrame kf = new KeyFrame(Duration.millis(UI_ANIMATION_TIME_MSEC), kv);
        timeline.getKeyFrames().add(kf);
        timeline.play();
    }

    public static void blurIn(Node node) {
        GaussianBlur blur = (GaussianBlur) node.getEffect();
        Timeline timeline = new Timeline();
        KeyValue kv = new KeyValue(blur.radiusProperty(), 0.0);
        KeyFrame kf = new KeyFrame(Duration.millis(UI_ANIMATION_TIME_MSEC), kv);
        timeline.getKeyFrames().add(kf);
        timeline.setOnFinished(actionEvent -> node.setEffect(null));
        timeline.play();
    }

    public static ScaleTransition zoomIn(Node node) {
        return zoomIn(node, 0);
    }

    public static ScaleTransition zoomIn(Node node, int delayMillis) {
        return scaleFromTo(node, 0.95, 1.0, delayMillis);
    }

    public static ScaleTransition explodeOut(Node node) {
        return scaleFromTo(node, 1.0, 1.05, 0);
    }

    private static ScaleTransition scaleFromTo(Node node, double from, double to, int delayMillis) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(UI_ANIMATION_TIME_MSEC / 2), node);
        scale.setFromX(from);
        scale.setFromY(from);
        scale.setToX(to);
        scale.setToY(to);
        scale.setDelay(Duration.millis(delayMillis));
        scale.play();
        return scale;
    }

    /**
     * A useful helper for development purposes. Used as a switch for loading files from local disk, allowing live
     * editing whilst the app runs without rebuilds.
     */
    public static URL getResource(String name) {
        if (false)
            return WTUtils.unchecked(() -> new URL("file:///your/path/here/src/main/wallettemplate/" + name));
        else
            return MainController.class.getResource(name);
    }

    public static void checkGuiThread() {
        checkState(Platform.isFxApplicationThread());
    }

    public static void alertInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information Dialog");
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }

    public static void alertException(Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Exception Dialog");
        alert.setHeaderText("Look, an Exception Dialog");
        alert.setContentText(ex.getMessage());

//        Exception ex = new FileNotFoundException("Could not find file blabla.txt");

        // Create expandable Exception.
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String exceptionText = sw.toString();

        Label label = new Label("The exception stacktrace was:");

        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);

        // Set expandable Exception into the dialog pane.
        alert.getDialogPane().setExpandableContent(expContent);

        alert.showAndWait();
    }
}
