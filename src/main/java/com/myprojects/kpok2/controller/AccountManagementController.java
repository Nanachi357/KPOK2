package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.service.AccountConfigurationService;
import com.myprojects.kpok2.service.AccountConfigurationService.AccountDTO;
import com.myprojects.kpok2.service.navigation.AccountManager;
import com.myprojects.kpok2.service.navigation.NavigationSessionFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the account management screen
 */
@Slf4j
@Component
public class AccountManagementController implements Initializable {

    @FXML
    private TableView<AccountDTO> accountsTable;
    
    @FXML
    private TableColumn<AccountDTO, String> usernameColumn;
    
    @FXML
    private TableColumn<AccountDTO, String> passwordColumn;
    
    @FXML
    private TableColumn<AccountDTO, Boolean> enabledColumn;
    
    @FXML
    private TableColumn<AccountDTO, Void> actionsColumn;
    
    @FXML
    private Label propertiesPathLabel;
    
    @FXML
    private Button addAccountButton;
    
    private final AccountConfigurationService accountService;
    private final ApplicationContext applicationContext;
    private final NavigationSessionFactory navigationSessionFactory;
    private final AccountManager accountManager;
    
    @Autowired
    public AccountManagementController(
            AccountConfigurationService accountService,
            ApplicationContext applicationContext,
            NavigationSessionFactory navigationSessionFactory,
            AccountManager accountManager) {
        this.accountService = accountService;
        this.applicationContext = applicationContext;
        this.navigationSessionFactory = navigationSessionFactory;
        this.accountManager = accountManager;
    }
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTableColumns();
        bindTableData();
        setupPropertiesPathLabel();
    }
    
    /**
     * Open the account management window
     */
    public void showAccountManagementWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AccountManagementView.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Test Center Account Management");
            stage.setScene(new Scene(root));
            stage.setMinWidth(700);
            stage.setMinHeight(500);
            
            // Handle key events for the stage
            stage.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    stage.close();
                }
            });
            
            // Handle stage close to refresh accounts in the navigation pool
            stage.setOnCloseRequest(event -> refreshAccountPool());
            
            stage.showAndWait();
        } catch (IOException e) {
            log.error("Failed to open account management window", e);
            showErrorAlert("Failed to open account management window", e.getMessage());
        }
    }
    
    /**
     * Set up the table columns
     */
    private void setupTableColumns() {
        // Username column
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        
        // Password column - show masked password
        passwordColumn.setCellValueFactory(cellData -> 
                new SimpleStringProperty("••••••••"));
        
        // Enabled column
        enabledColumn.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        enabledColumn.setCellFactory(column -> new TableCell<AccountDTO, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label statusLabel = new Label();
                    if (item) {
                        statusLabel.setText("✓");
                        statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold; -fx-font-size: 14px;");
                    } else {
                        statusLabel.setText("✗");
                        statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold; -fx-font-size: 14px;");
                    }
                    setGraphic(statusLabel);
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            }
        });
        
        // Actions column - edit and delete buttons
        actionsColumn.setCellFactory(createActionsColumnCellFactory());
    }
    
    /**
     * Create cell factory for actions column
     */
    private Callback<TableColumn<AccountDTO, Void>, TableCell<AccountDTO, Void>> createActionsColumnCellFactory() {
        return column -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Delete");
            private final ButtonBar buttonBar = new ButtonBar();
            
            {
                // Style and setup buttons
                editButton.getStyleClass().add("button-small");
                deleteButton.getStyleClass().add("button-small");
                
                // Add buttons to the button bar
                ButtonBar.setButtonData(editButton, ButtonBar.ButtonData.LEFT);
                ButtonBar.setButtonData(deleteButton, ButtonBar.ButtonData.RIGHT);
                buttonBar.getButtons().addAll(editButton, deleteButton);
                
                // Edit button action
                editButton.setOnAction(event -> {
                    AccountDTO account = getTableRow().getItem();
                    int index = getTableRow().getIndex();
                    if (account != null) {
                        editAccount(account, index);
                    }
                });
                
                // Delete button action
                deleteButton.setOnAction(event -> {
                    AccountDTO account = getTableRow().getItem();
                    int index = getTableRow().getIndex();
                    if (account != null) {
                        deleteAccount(account, index);
                    }
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(buttonBar);
                }
            }
        };
    }
    
    /**
     * Bind table data to the accounts list
     */
    private void bindTableData() {
        accountsTable.setItems(accountService.getAccounts());
        
        // Enabled column no longer has editable cells
        // Editing must now happen through the Edit dialog
    }
    
    /**
     * Set up the properties path label
     */
    private void setupPropertiesPathLabel() {
        propertiesPathLabel.setText("Configuration file: " + accountService.getPropertiesPath());
    }
    
    /**
     * Handle the add account button click
     */
    @FXML
    private void handleAddAccount() {
        showAccountDialog(new AccountDTO("", "", true), -1);
    }
    
    /**
     * Edit an account
     */
    private void editAccount(AccountDTO account, int index) {
        // Create a copy of the account to edit
        AccountDTO accountCopy = new AccountDTO(
                account.getUsername(),
                account.getPassword(),
                account.isEnabled()
        );
        
        showAccountDialog(accountCopy, index);
    }
    
    /**
     * Delete an account
     */
    private void deleteAccount(AccountDTO account, int index) {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Delete");
        confirmDialog.setHeaderText("Delete Account");
        confirmDialog.setContentText("Are you sure you want to delete the account: " + account.getUsername() + "?");
        
        confirmDialog.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                accountService.removeAccount(index);
                log.info("Deleted account: {}", account.getUsername());
                refreshAccountPool();
            }
        });
    }
    
    /**
     * Show the account dialog for adding or editing an account
     */
    private void showAccountDialog(AccountDTO account, int editIndex) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AccountDialog.fxml"));
            
            // Create and initialize controller manually
            AccountDialogController controller = new AccountDialogController(
                    account, editIndex, accountService, this::refreshAccountPool);
            loader.setController(controller);
            
            Parent root = loader.load();
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle(editIndex >= 0 ? "Edit Account" : "Add Account");
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);
            
            // Handle key events for the dialog
            dialogStage.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    dialogStage.close();
                }
            });
            
            dialogStage.showAndWait();
        } catch (IOException e) {
            log.error("Failed to open account dialog", e);
            showErrorAlert("Error", "Failed to open account dialog: " + e.getMessage());
        }
    }
    
    /**
     * Refresh the account pool after changes
     */
    private void refreshAccountPool() {
        log.info("Refreshing account pool after account changes");
        
        // Clear existing account pool
        accountManager.clearAccounts();
        
        // Reinitialize account pool from updated accounts
        navigationSessionFactory.initializeAccountPool();
    }
    
    /**
     * Show an error alert
     */
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Controller for the account dialog
     */
    private static class AccountDialogController {
        @FXML
        private TextField usernameField;
        
        @FXML
        private PasswordField passwordField;
        
        @FXML
        private TextField visiblePasswordField;
        
        @FXML
        private CheckBox showPasswordCheckBox;
        
        @FXML
        private CheckBox enabledCheckBox;
        
        @FXML
        private Button saveButton;
        
        @FXML
        private Button cancelButton;
        
        private final AccountDTO account;
        private final int editIndex;
        private final AccountConfigurationService accountService;
        private final Runnable onSaveCallback;
        
        public AccountDialogController(
                AccountDTO account,
                int editIndex,
                AccountConfigurationService accountService,
                Runnable onSaveCallback) {
            this.account = account;
            this.editIndex = editIndex;
            this.accountService = accountService;
            this.onSaveCallback = onSaveCallback;
        }
        
        @FXML
        private void initialize() {
            // Set initial values
            usernameField.setText(account.getUsername());
            passwordField.setText(account.getPassword());
            visiblePasswordField.setText(account.getPassword());
            enabledCheckBox.setSelected(account.isEnabled());
            
            // Hide visible password field initially
            visiblePasswordField.setVisible(false);
            visiblePasswordField.setManaged(false);
            
            // Show/hide password toggle
            showPasswordCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    visiblePasswordField.setText(passwordField.getText());
                    passwordField.setVisible(false);
                    passwordField.setManaged(false);
                    visiblePasswordField.setVisible(true);
                    visiblePasswordField.setManaged(true);
                } else {
                    passwordField.setText(visiblePasswordField.getText());
                    passwordField.setVisible(true);
                    passwordField.setManaged(true);
                    visiblePasswordField.setVisible(false);
                    visiblePasswordField.setManaged(false);
                }
            });
            
            // Sync password fields
            passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
                visiblePasswordField.setText(newValue);
            });
            
            visiblePasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
                passwordField.setText(newValue);
            });
            
            // Set up save button
            saveButton.setOnAction(event -> saveAccount());
            
            // Set up cancel button
            cancelButton.setOnAction(event -> ((Stage) cancelButton.getScene().getWindow()).close());
        }
        
        /**
         * Save the account
         */
        private void saveAccount() {
            // Validate inputs
            if (usernameField.getText().trim().isEmpty()) {
                showErrorAlert("Validation Error", "Username cannot be empty");
                return;
            }
            
            if (passwordField.getText().trim().isEmpty()) {
                showErrorAlert("Validation Error", "Password cannot be empty");
                return;
            }
            
            // Update account with form values
            account.setUsername(usernameField.getText().trim());
            account.setPassword(showPasswordCheckBox.isSelected() 
                    ? visiblePasswordField.getText() 
                    : passwordField.getText());
            account.setEnabled(enabledCheckBox.isSelected());
            
            // Save account
            if (editIndex >= 0) {
                accountService.updateAccount(editIndex, account);
            } else {
                accountService.addAccount(account);
            }
            
            // Execute callback to refresh account pool
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
            
            // Close dialog
            ((Stage) saveButton.getScene().getWindow()).close();
        }
        
        /**
         * Show an error alert
         */
        private void showErrorAlert(String title, String message) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        }
    }
} 