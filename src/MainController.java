import com.google.gson.Gson;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.controls.JFXTextField;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML
    private JFXButton newNoteButton;

    @FXML
    private JFXButton editHeaderButton;

    @FXML
    private JFXButton deleteButton;

    @FXML
    private JFXButton settingsButton;

    @FXML
    private JFXButton exitButton;

    @FXML
    private JFXListView<String> notesListView = new JFXListView<>();

    @FXML
    private JFXTextArea noteTextArea;

    @FXML
    private JFXTextArea detailsTextArea;

    @FXML
    private JFXTextField searchNoteField;

    public static boolean isThemeSwitchOn;

    public static String currentHeader;  //  EditHeader will access this, cannot setText otherwise

    public static ObservableList<String> items;

    private static String chosenHeader;  //  For autosave

    public void newNoteAction(ActionEvent actionEvent) throws IOException {
        Note newNote = AddNote.initiateAddNoteScreen(Main.isDarkModeEnabled).get();
        DataHandler.addToListView(newNote);
    }

    public void exitApp(ActionEvent actionEvent) throws SQLException, IOException {
        boolean answer = ExitApp.confirmExit(Main.isDarkModeEnabled);
        if (answer){
            Stage window = (Stage) exitButton.getScene().getWindow();
            DataBase.closeConnection();
            window.close();
        }
    }

    public void getSelectedNote(MouseEvent mouseEvent) throws IOException, SQLException, ClassNotFoundException {

        if (!Settings.isAutoSaveEnabled) {
            if (chosenHeader != null && !chosenHeader.equals(notesListView.getSelectionModel().getSelectedItem()) && !noteTextArea.getText().equals(DataHandler.getNotes().get(chosenHeader).getBody())) {
                boolean answer = SaveDialog.confirmSave();

                if (answer) {
                    DataHandler.getNotes().get(chosenHeader).setBody(noteTextArea.getText());
                    DataHandler.getNotes().get(chosenHeader).setTime(DataHandler.formatDate(LocalDateTime.now()));

                    DataBase.updateNoteBody(chosenHeader, noteTextArea.getText());
                }
            }
        }else{
            if (chosenHeader != null && !chosenHeader.equals(notesListView.getSelectionModel().getSelectedItem()) && !noteTextArea.getText().equals(DataHandler.getNotes().get(chosenHeader).getBody())) {
                DataHandler.getNotes().get(chosenHeader).setBody(noteTextArea.getText());
                DataHandler.getNotes().get(chosenHeader).setTime(DataHandler.formatDate(LocalDateTime.now()));

                DataBase.updateNoteBody(chosenHeader, noteTextArea.getText());
            }
        }
        chosenHeader = notesListView.getSelectionModel().getSelectedItem();

        enableButtons();
        noteTextArea.setEditable(true);
        String chosenNoteHeader = notesListView.getSelectionModel().getSelectedItem();
        noteTextArea.setText(DataHandler.getNotes().get(chosenNoteHeader).getBody());
        detailsTextArea.setText("Last edited: " + DataHandler.getNotes().get(chosenNoteHeader).getTime() + " By: " + System.getProperty("user.name"));
    }
    public void editHeaderAction(ActionEvent actionEvent) throws SQLException, ClassNotFoundException, IOException {
        currentHeader = notesListView.getSelectionModel().getSelectedItem();
        String oldHeader = currentHeader;
        String newHeader = EditHeader.initiateEditHeaderScreen(Main.isDarkModeEnabled).get();

        if (newHeader != null && !DataHandler.getNotes().containsKey(newHeader)) {
            int index = MainController.items.indexOf(notesListView.getSelectionModel().getSelectedItem());

            Note note = DataHandler.getNotes().remove(notesListView.getSelectionModel().getSelectedItem());

            items.remove(note.getHeader());
            note.setHeader(newHeader);

            note.setTime(DataHandler.formatDate(LocalDateTime.now()));

            DataHandler.insertToListView(note, index);

            DataBase.updateNoteHeader(oldHeader, newHeader);
        }
    }

    public void deleteNoteAction(ActionEvent actionEvent) throws SQLException, ClassNotFoundException, IOException {
        String deletedHeader = notesListView.getSelectionModel().getSelectedItem();
        boolean answer = DeleteNote.confirmDelete(Main.isDarkModeEnabled);

        if (answer){
            items.remove(deletedHeader);
            DataHandler.getNotes().remove(DataHandler.getNotes().get(deletedHeader).getHeader());

            DataBase.deleteNote(deletedHeader);

            noteTextArea.setText("Note is deleted");
            noteTextArea.setEditable(false);
            disableButtons();
            if (items.isEmpty()){
                newNoteButton.setDisable(false);
            }
        }
        disableNoteEditing();
    }

    private void enableButtons() {
        if (notesListView.getSelectionModel().getSelectedItem() != null) {
            newNoteButton.setDisable(false);
            editHeaderButton.setDisable(false);
            deleteButton.setDisable(false);
        }
    }

    private void disableButtons() {
        newNoteButton.setDisable(true);
        editHeaderButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    private void disableNoteEditing() {
        if (notesListView.getSelectionModel().getSelectedItem() != null) {
            noteTextArea.setEditable(false);
        }
    }

    public static void saveConfigs(Map<String, String> map, Writer writer) {
        Gson gson = new Gson();

        try {
            gson.toJson(map, writer);
            writer.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void appSettingsAction(ActionEvent actionEvent) throws IOException {
        Settings.initializeSettingsScreen();
    }

    public void aboutAction(ActionEvent actionEvent) throws IOException {
        About.initializeAboutScreen();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        DataBase dataBase = new DataBase();  //Load note data from SQLite database
        ResultSet rs;

        try {
            rs = dataBase.getNotesResultSet();
            while (rs.next()) {
                notesListView.getItems().add(rs.getString("header"));
                DataHandler.getNotes().put(rs.getString("header"), new Note(rs.getString("header"), rs.getString("body"), rs.getString("time")));
            }
        } catch (SQLException | ClassNotFoundException throwables) {
            throwables.printStackTrace();
        }

        isThemeSwitchOn = Main.isDarkModeEnabled;

        // Setting up note headers search feature
        items = notesListView.getItems();
        FilteredList<String> filteredData = new FilteredList<>(items, s -> true);

        searchNoteField.textProperty().addListener(obs -> {
            String filter = searchNoteField.getText();
            if(filter == null || filter.length() == 0) {
                filteredData.setPredicate(s -> true);
                notesListView.setItems(items);
            }
            else {
                filteredData.setPredicate(s -> s.contains(filter));
                notesListView.setItems(filteredData);
            }
        });
    }

    public void copyAction(ActionEvent actionEvent) {
        StringSelection stringSelection = new StringSelection(noteTextArea.getText());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);

    }
}
