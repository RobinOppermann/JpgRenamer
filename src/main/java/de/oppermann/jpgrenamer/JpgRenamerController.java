package de.oppermann.jpgrenamer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.stage.DirectoryChooser;
import org.apache.commons.imaging.ImageReadException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * This class contains the controller code of the JpgRenamer UI
 */
public class JpgRenamerController {

    //region UI fields
    @FXML
    private TextField directoryTextField, newNameTextField;

    @FXML
    private TableView<JpgFile> fileTable;

    @FXML
    private CheckBox fixConflictsCheckbox;

    @FXML
    private Button prevButton, renameButton, nextButton;

    @FXML
    private ImageView thumbnailImageView;

    @FXML
    private TableColumn<JpgFile, String> currentNameColumn, newNameColumn, dateTakenColumn, resolutionColumn;

    @FXML
    private Label currentNameLabel, dateTakenLabel, resolutionLabel;
    //endregion UI fields

    /**
     * Contains the JpgFiles loaded from the opened directory
      */
    private ObservableList<JpgFile> fileList;

    /**
     * Initializes the UI
     */
    @FXML
    private void initialize() {
        fileList = FXCollections.observableArrayList();
        currentNameColumn.setCellValueFactory(jpgFile -> jpgFile.getValue().currentNameProperty());
        newNameColumn.setCellValueFactory(jpgFile -> jpgFile.getValue().newNameProperty());
        dateTakenColumn.setCellValueFactory(new PropertyValueFactory<>("taken"));
        resolutionColumn.setCellValueFactory(new PropertyValueFactory<>("resolution"));

        newNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());

        newNameColumn.setEditable(true);

        fileTable.setEditable(true);

        fileTable.setItems(this.fileList);

        fileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            onSelectionChanged(oldSelection, newSelection);
        });
    }

    /**
     * Draws the thumbnail picture into the image view
     * @param image
     */
    private void setThumbnail(BufferedImage image) {
        if(image != null) {
            WritableImage wr = new WritableImage(image.getWidth(), image.getHeight());
            PixelWriter pw = wr.getPixelWriter();
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    pw.setArgb(x, y, image.getRGB(x, y));
                }
            }
            this.thumbnailImageView.setImage(wr);
        } else {
            this.thumbnailImageView.setImage(null);
        }

    }

    /**
     * Sets the background of the directory text field (white if input is ok, red otherwise)
     * @param hasError true, if the input is invalid
     */
    private void setDirectoryTextFieldBackgroundColor(boolean hasError) {
        if(hasError) {
            directoryTextField.setStyle("-fx-control-inner-background: darkred");
        } else {
            directoryTextField.setStyle("-fx-control-inner-background: white");
        }
    }

    /**
     * Populates the file table by loading the directory content
     * @param directory The directory to load
     */
    private void populateTable(File directory) {
        new Thread(() -> {
            // loading the directory content might take some time -> run in new thread to avoid UI freeze
            this.fileList.clear();
            List<File> jpgFiles = List.of();
            try {
                jpgFiles = Files.list(Path.of(directory.getAbsolutePath()))
                        .filter(f -> Files.isRegularFile(f)
                                && Arrays.stream(JpgFile.FILE_EXTENSIONS).anyMatch(e -> f.toString().toLowerCase(Locale.ROOT).endsWith(e)))
                        .map(Path::toFile).toList();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Opening directory failed");
                    alert.setHeaderText("Opening directory failed");
                    alert.setContentText(String.format("Opening the directory \"%s\" failed: %s", directory.getAbsolutePath(), e.getMessage()));
                    alert.showAndWait();
                });
            }
            for (File file : jpgFiles) {
                JpgFile jpgFile;
                try {
                    jpgFile = new JpgFile(file);
                    this.fileList.add(jpgFile);
                    Platform.runLater(() -> {
                        if(this.fileTable.getItems().size() > 0) {
                            this.fileTable.getSelectionModel().select(0);
                        }
                    });
                } catch (IOException | ImageReadException | ParseException e) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Image Error");
                        alert.setHeaderText("Could not open the image");
                        alert.setContentText(String.format("Could not open the image at \"%s\": %s", file.getAbsolutePath(), e.getMessage()));
                        alert.show();
                    });
                }
            }
        }).start();
    }

    //region event handlers

    /**
     * Handles the change of selection in the file table
     * @param oldSelection previously selected JpgFile
     * @param newSelection now selected JpgFile
     */
    private void onSelectionChanged(JpgFile oldSelection, JpgFile newSelection) {
        if(oldSelection != null) {
            this.newNameTextField.textProperty().unbindBidirectional(oldSelection.newNameProperty());
        }
        if(newSelection != null) {
            // bind fields
            this.currentNameLabel.textProperty().unbind();
            this.currentNameLabel.textProperty().bind(newSelection.currentNameProperty());
            this.dateTakenLabel.setText(newSelection.getTaken().toString());
            this.resolutionLabel.setText(newSelection.getResolution());
            this.newNameTextField.textProperty().unbind();
            this.newNameTextField.textProperty().bindBidirectional(newSelection.newNameProperty());
            this.setThumbnail(newSelection.getThumbnail());
        } else {
            this.currentNameLabel.textProperty().unbind();
            this.currentNameLabel.setText("no image selected");
            this.dateTakenLabel.setText("no image selected");
            this.resolutionLabel.setText("no image selected");
            this.newNameTextField.textProperty().unbind();
            this.newNameTextField.setText("");
            this.setThumbnail(null);
        }
        this.renameButton.setDisable(newSelection == null);
        this.prevButton.setDisable(newSelection == null || fileTable.getSelectionModel().getSelectedIndex() == 0);
        this.nextButton.setDisable(newSelection == null || fileTable.getSelectionModel().getSelectedIndex()+1 >= fileTable.getItems().size());
    }

    /**
     * Handles the completion of editing the directory input field
     */
    @FXML
    protected void onDirectoryTextEnter() {
        File directory = new File(directoryTextField.getText());
        if(directory.exists() && directory.isDirectory()) {
            setDirectoryTextFieldBackgroundColor(false);
            this.populateTable(directory);
        } else {
            setDirectoryTextFieldBackgroundColor(true);
        }
    }

    /**
     * Handles clicking the browse button by opening a directory chooser
     */
    @FXML
    protected void onBrowseClicked() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File result = directoryChooser.showDialog(directoryTextField.getScene().getWindow());
        if(result != null) {
            directoryTextField.setText(result.getAbsolutePath());
            setDirectoryTextFieldBackgroundColor(false);
            this.populateTable(result);
        }
    }

    /**
     * Handles clicking the previous button by selecting the previous element in the table
     */
    @FXML
    protected void onPrevClicked() {
        int currentSelection = this.fileTable.getSelectionModel().getSelectedIndex();
        if(currentSelection-1 >= 0 && !this.fileTable.getItems().isEmpty()) {
            this.fileTable.getSelectionModel().select(currentSelection-1);
        }
    }

    /**
     * Handles clicking the next button by selecting the next element in the table
     */
    @FXML
    protected void onNextClicked() {
        int currentSelection = this.fileTable.getSelectionModel().getSelectedIndex();
        if(this.fileTable.getItems().size() > currentSelection+1) {
            this.fileTable.getSelectionModel().select(currentSelection+1);
        }
    }

    /**
     * Handles clicking the rename button by renaming the currently selected JpgFile to the given new name
     */
    @FXML
    protected void onRenameClicked() {
        JpgFile jpgFile = this.fileTable.getSelectionModel().getSelectedItem();
        String oldName = jpgFile.getCurrentName();
        String newName = jpgFile.newNameProperty().getValue();
        try {
            jpgFile.renameFile();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Rename failed");
            alert.setHeaderText("Renaming the file failed");
            alert.setContentText(String.format("Renaming the file \"%s\" to \"%s\" failed: %s", oldName, newName, e.getMessage()));
            alert.showAndWait();
        }
    }

    /**
     * Handles clicking the rename all button by renaming all JpgFiles loaded from the directory.
     * If the checkbox is ticked, naming conflicts will be resolved automatically.
     */
    @FXML
    protected void onRenameAllClicked() {
        boolean resolveNamingConflicts = this.fixConflictsCheckbox.isSelected();
        for (JpgFile jpgFile: this.fileList) {
            String oldName = jpgFile.getCurrentName();
            String newName = jpgFile.newNameProperty().getValue();
            try {
                jpgFile.renameFile(resolveNamingConflicts);
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Rename failed");
                alert.setHeaderText("Renaming the file failed");
                alert.setContentText(String.format("Renaming the file \"%s\" to \"%s\" failed: %s", oldName, newName, e.getMessage()));
                alert.show();
            }
        }
    }
    //endregion


}