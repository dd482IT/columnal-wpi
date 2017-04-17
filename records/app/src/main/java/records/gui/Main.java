package records.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import records.data.DataSource;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.error.InternalException;
import records.error.UserException;
import records.importers.HTMLImport;
import records.importers.TextImport;
import records.transformations.TransformationEditor;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Workers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * Created by neil on 18/10/2016.
 */
public class Main extends Application
{
    @Override
    @OnThread(value = Tag.FXPlatform,ignoreParent = true)
    public void start(final Stage primaryStage) throws Exception
    {
        /*
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        if (cl != null)
        {
            URL[] urls = ((URLClassLoader) cl).getURLs();

            for (URL url : urls)
            {
                System.out.println(url.getFile());
            }
        }
        */

        View v = new View();
        Menu menu = new Menu("Data");
        MenuItem manualItem = new MenuItem("New");
        menu.getItems().add(manualItem);
        manualItem.setOnAction(e -> {
            Workers.onWorkerThread("Create new table", () -> {
                try
                {
                    EditableRecordSet rs = new EditableRecordSet(Collections.emptyList(), 0);
                    ImmediateDataSource ds = new ImmediateDataSource(v.getManager(), rs);
                }
                catch (InternalException | UserException ex)
                {
                    showError(ex);
                }
            });
        });
        MenuItem importItem = new MenuItem("Text");
        importItem.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File chosen = fc.showOpenDialog(primaryStage);
            if (chosen != null)
            {
                Workers.onWorkerThread("GuessFormat data", () ->
                {
                    try
                    {
                        TextImport.importTextFile(v.getManager(), chosen, rs ->
                            Utility.alertOnErrorFX_(() -> v.addSource(rs)));
                    }
                    catch (InternalException | UserException | IOException ex)
                    {
                        ex.printStackTrace();
                        showError(ex);
                    }
                });
            }
        });
        MenuItem importHTMLItem = new MenuItem("HTML");
        importHTMLItem.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File chosen = fc.showOpenDialog(primaryStage);
            if (chosen != null)
            {
                Workers.onWorkerThread("GuessFormat data", () ->
                {
                    try
                    {
                        for (DataSource rs : HTMLImport.importHTMLFile(v.getManager(), chosen))
                        {
                            Platform.runLater(() -> Utility.alertOnErrorFX_(() -> v.addSource(rs)));
                        }
                    }
                    catch (IOException | InternalException | UserException ex)
                    {
                        ex.printStackTrace();
                        showError(ex);
                    }
                });
            }
        });
        menu.getItems().addAll(importItem, importHTMLItem);
        MenuItem saveItem = new MenuItem("Save to Clipboard");
        saveItem.setOnAction(e -> {
            v.save(null, s ->
                Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, s)));
        });
        menu.getItems().add(saveItem);
        MenuItem saveAsItem = new MenuItem(TransformationEditor.getString("main.saveas"));
        saveAsItem.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File dest = fc.showSaveDialog(primaryStage);
            v.save(dest, content -> Utility.alertOnErrorFX_(() ->
            {
                try
                {
                    FileUtils.writeStringToFile(dest, content, "UTF-8");
                }
                catch (IOException ex)
                {
                    throw new UserException("Error writing file", ex);
                }
            }));
        });
        menu.getItems().add(saveAsItem);
        /*
        Workers.onWorkerThread("Example import", () -> {
            try
            {
                DataSource rs = HTMLImport.importHTMLFile(v.getManager(), new File("S:\\Downloads\\Report_10112016.xls")).get(0);
                    //TextImport.importTextFile(new File("J:\\price\\farm-output-jun-2016.txt"  "J:\\price\\detailed.txt"));
                Platform.runLater(() -> Utility.alertOnErrorFX_(() -> v.addSource(rs)));
            }
            catch (IOException | InternalException | UserException ex)
            {
                showError(ex);
            }
        });
        */

        ScrollPane scrollPane = new ScrollPane(v);

        // From https://reportmill.wordpress.com/2014/06/03/make-scrollpane-content-fill-viewport-bounds/
        Utility.addChangeListenerPlatform(scrollPane.viewportBoundsProperty(), bounds -> {
            if (bounds != null)
            {
                Node content = scrollPane.getContent();
                scrollPane.setFitToWidth(content.prefWidth(-1) < bounds.getWidth());
                scrollPane.setFitToHeight(content.prefHeight(-1) < bounds.getHeight());
            }
        });

        BorderPane root = new BorderPane(scrollPane, new MenuBar(menu), null, null, null);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Utility.getStylesheet("mainview.css"));
        primaryStage.setScene(scene);
        primaryStage.setWidth(1000);
        primaryStage.setHeight(800);
        primaryStage.show();
        //org.scenicview.ScenicView.show(primaryStage.getScene());
    }

    @OnThread(Tag.Simulation)
    private void showError(Exception ex)
    {
        Platform.runLater(() -> new Alert(AlertType.ERROR, ex.getMessage() == null ? "" : ex.getMessage(), ButtonType.OK).showAndWait());
    }

    // TODO pass -XX:AutoBoxCacheMax= parameter on execution
    public static void main(String[] args)
    {
        Application.launch(Main.class);
    }
}
