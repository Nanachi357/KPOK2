package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import com.myprojects.kpok2.service.parser.TestParsingStatistics.ParsingSessionInfo;
import com.myprojects.kpok2.service.parser.TestParsingStatistics.StatisticsData;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for parsing statistics dialog
 */
@Slf4j
@Component
public class ParsingStatisticsController {

    @FXML
    private Label totalProcessedLabel;
    
    @FXML
    private Label successLabel;
    
    @FXML
    private Label failedLabel;
    
    @FXML
    private Label newQuestionsLabel;
    
    @FXML
    private Label sinceLabel;
    
    @FXML
    private TableView<ParsingSessionInfo> sessionTable;
    
    @FXML
    private TableColumn<ParsingSessionInfo, String> accountColumn;
    
    @FXML
    private TableColumn<ParsingSessionInfo, String> timestampColumn;
    
    @FXML
    private TableColumn<ParsingSessionInfo, Integer> pagesColumn;
    
    @FXML
    private TableColumn<ParsingSessionInfo, Integer> questionsColumn;
    
    @FXML
    private TableView<AccountStat> accountTable;
    
    @FXML
    private TableColumn<AccountStat, String> accountNameColumn;
    
    @FXML
    private TableColumn<AccountStat, Integer> pagesParsedColumn;
    
    private final TestParsingStatistics statisticsService;
    private Stage stage;
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public ParsingStatisticsController(TestParsingStatistics statisticsService) {
        this.statisticsService = statisticsService;
    }
    
    public void setStage(Stage stage) {
        this.stage = stage;
    }
    
    @FXML
    public void initialize() {
        // Format for session history table
        accountColumn.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getAccountUsername()));
            
        timestampColumn.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getTimestamp().format(DATE_TIME_FORMATTER)));
            
        pagesColumn.setCellValueFactory(data -> 
            new SimpleIntegerProperty(data.getValue().getPagesParsed()).asObject());
            
        questionsColumn.setCellValueFactory(data -> 
            new SimpleIntegerProperty(data.getValue().getNewQuestionsFound()).asObject());
            
        // Format for account statistics table
        accountNameColumn.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getUsername()));
            
        pagesParsedColumn.setCellValueFactory(data -> 
            new SimpleIntegerProperty(data.getValue().getPagesParsed()).asObject());
            
        // Load data
        refreshData();
    }
    
    public void refreshData() {
        // Get current statistics
        StatisticsData stats = statisticsService.getCurrentStats();
        
        // Update summary labels
        totalProcessedLabel.setText(String.valueOf(stats.getProcessedCount()));
        successLabel.setText(String.valueOf(stats.getSuccessCount()));
        failedLabel.setText(String.valueOf(stats.getFailedCount()));
        newQuestionsLabel.setText(String.valueOf(stats.getNewQuestionsCount()));
        sinceLabel.setText(stats.getSince().format(DATE_TIME_FORMATTER));
        
        // Update session history table
        List<ParsingSessionInfo> sessionHistory = statisticsService.getSessionHistory();
        sessionTable.setItems(FXCollections.observableArrayList(sessionHistory));
        
        // Update account statistics table
        List<AccountStat> accountStats = stats.getAccountsUsed().entrySet().stream()
                .map(entry -> new AccountStat(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        accountTable.setItems(FXCollections.observableArrayList(accountStats));
    }
    
    @FXML
    public void onResetClick() {
        // Show confirmation dialog
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reset Statistics");
        alert.setHeaderText("Reset All Statistics Data");
        alert.setContentText("Are you sure you want to reset all parsing statistics? This action cannot be undone.");
        
        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                statisticsService.resetStats();
                refreshData();
                log.info("Statistics data has been reset");
            }
        });
    }
    
    @FXML
    public void onRefreshClick() {
        refreshData();
        log.info("Statistics data refreshed");
    }
    
    @FXML
    public void onCloseClick() {
        stage.close();
    }
    
    /**
     * Helper class to represent account statistics for the table
     */
    public static class AccountStat {
        private final String username;
        private final int pagesParsed;
        
        public AccountStat(String username, int pagesParsed) {
            this.username = username;
            this.pagesParsed = pagesParsed;
        }
        
        public String getUsername() {
            return username;
        }
        
        public int getPagesParsed() {
            return pagesParsed;
        }
    }
} 