package com.opencgl.snippet.controller;

import com.opencgl.snippet.i18n.I18N;
import com.opencgl.snippet.model.Snippet;
import com.opencgl.snippet.service.SnippetService;
import com.opencgl.snippet.views.CodeSnippetView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 代码片段控制器
 */
public class CodeSnippetController extends CodeSnippetView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(CodeSnippetController.class);
    
    private final SnippetService snippetService = new SnippetService();
    private final ObservableList<Snippet> allSnippets = FXCollections.observableArrayList();
    private CodeArea codeArea;
    private Snippet currentSnippet;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-code-snippet");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean disposed;
    
    private static final List<String> LANGUAGES = Arrays.asList(
        "Java", "JavaScript", "Python", "SQL", "Shell", "Go", "Rust",
        "C++", "C#", "PHP", "Ruby", "Swift", "Kotlin", "TypeScript",
        "HTML", "CSS", "XML", "JSON", "YAML", "Markdown", "其他"
    );
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupCodeArea();
        setupLanguageTree();
        setupLanguageComboBox();
        bindEvents();
        loadAllSnippets();
        initI18n();
        setStatus(I18N.get("status.ready"));
    }

    private void initI18n() {
    }
    
    private void setupCodeArea() {
        codeArea = new CodeArea();
        codeArea.setStyle(
            "-fx-font-family: 'JetBrains Mono', 'Consolas', monospace;" +
            "-fx-font-size: 14px;"
        );
        codeArea.setWrapText(true);
        
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);
        codeAreaContainer.getChildren().add(scrollPane);
    }
    
    private void setupLanguageTree() {
        TreeItem<String> root = new TreeItem<>(I18N.get("tree.allLanguages"));
        root.setExpanded(true);
        
        TreeItem<String> allItem = new TreeItem<>(I18N.get("tree.all"));
        TreeItem<String> favItem = new TreeItem<>(I18N.get("tree.favorites"));
        root.getChildren().addAll(allItem, favItem);
        
        languageTree.setRoot(root);
        languageTree.setShowRoot(false);
        
        languageTree.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                filterByCategory(newVal.getValue());
            }
        });
    }
    
    private void setupLanguageComboBox() {
        languageComboBox.getItems().addAll(LANGUAGES);
        languageComboBox.setText("Java");
    }
    
    private void bindEvents() {
        newButton.setOnAction(e -> onNew());
        saveButton.setOnAction(e -> onSave());
        deleteButton.setOnAction(e -> onDelete());
        copyButton.setOnAction(e -> onCopy());
        
        snippetList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                loadSnippet(newVal);
            }
        });
        
        searchField.textProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                performSearch(newVal);
            } else {
                updateSnippetList(allSnippets);
            }
        });
    }
    
    private void loadAllSnippets() {
        CompletableFuture.supplyAsync(() -> snippetService.findAll(), executor)
            .thenAccept(snippets -> Platform.runLater(() -> {
                if (disposed) return;
                allSnippets.setAll(snippets);
                updateSnippetList(snippets);
                updateLanguageTree(snippets);
                setStatus(I18N.get("status.loaded", snippets.size()));
            }));
    }
    
    private void updateLanguageTree(List<Snippet> snippets) {
        TreeItem<String> root = (TreeItem<String>) languageTree.getRoot();
        
        // 清除语言分类
        while (root.getChildren().size() > 2) {
            root.getChildren().remove(2);
        }
        
        // 按语言分组
        Map<String, Long> languageCounts = snippets.stream()
            .collect(Collectors.groupingBy(Snippet::getLanguage, Collectors.counting()));
        
        languageCounts.forEach((lang, count) -> {
            TreeItem<String> langItem = new TreeItem<>(lang + " (" + count + ")");
            root.getChildren().add(langItem);
        });
    }
    
    private void updateSnippetList(List<Snippet> snippets) {
        snippetList.getItems().clear();
        snippets.forEach(s -> {
            String display = (s.isFavorite() ? "⭐ " : "") + s.getTitle();
            snippetList.getItems().add(display);
        });
    }
    
    private void filterByCategory(String category) {
        if (category.equals(I18N.get("tree.all"))) {
            updateSnippetList(allSnippets);
        } else if (category.equals(I18N.get("tree.favorites"))) {
            List<Snippet> favorites = allSnippets.stream()
                .filter(Snippet::isFavorite)
                .collect(Collectors.toList());
            updateSnippetList(favorites);
        } else {
            String lang = category.split(" ")[0];
            List<Snippet> filtered = allSnippets.stream()
                .filter(s -> s.getLanguage().equals(lang))
                .collect(Collectors.toList());
            updateSnippetList(filtered);
        }
    }
    
    private void performSearch(String keyword) {
        CompletableFuture.supplyAsync(() -> snippetService.search(keyword), executor)
            .thenAccept(results -> Platform.runLater(() -> {
                if (disposed) return;
                updateSnippetList(results);
                setStatus(I18N.get("status.searchResult", results.size()));
            }));
    }
    
    private void loadSnippet(String display) {
        String title = display.replace("⭐ ", "");
        Snippet snippet = allSnippets.stream()
            .filter(s -> s.getTitle().equals(title))
            .findFirst()
            .orElse(null);
        
        if (snippet != null) {
            currentSnippet = snippet;
            titleField.setText(snippet.getTitle());
            languageComboBox.setText(snippet.getLanguage());
            descriptionArea.setText(snippet.getDescription());
            tagsField.setText(String.join(",", snippet.getTags()));
            codeArea.replaceText(snippet.getCode());
            favoriteCheckBox.setSelected(snippet.isFavorite());
        }
    }
    
    private void onNew() {
        currentSnippet = null;
        titleField.clear();
        languageComboBox.setText("Java");
        descriptionArea.clear();
        tagsField.clear();
        codeArea.clear();
        favoriteCheckBox.setSelected(false);
        setStatus(I18N.get("status.newSnippet"));
    }
    
    private void onSave() {
        String title = titleField.getText().trim();
        String language = languageComboBox.getText();
        String code = codeArea.getText();
        
        if (title.isEmpty() || code.isEmpty()) {
            setStatus(I18N.get("status.titleCodeRequired"));
            return;
        }
        
        Snippet snippet = currentSnippet != null ? currentSnippet : new Snippet();
        snippet.setTitle(title);
        snippet.setLanguage(language);
        snippet.setCode(code);
        snippet.setDescription(descriptionArea.getText());
        snippet.setFavorite(favoriteCheckBox.isSelected());
        
        String tags = tagsField.getText().trim();
        if (!tags.isEmpty()) {
            snippet.setTags(Arrays.asList(tags.split(",")));
        }
        
        CompletableFuture.runAsync(() -> {
            if (snippet.getId() == null) {
                Long id = snippetService.save(snippet);
                snippet.setId(id);
            } else {
                snippetService.update(snippet);
            }
        }, executor).thenRun(() -> Platform.runLater(() -> {
            if (disposed) return;
            loadAllSnippets();
            currentSnippet = snippet;
            setStatus(I18N.get("status.saveSuccess"));
        }));
    }
    
    private void onDelete() {
        if (currentSnippet != null && currentSnippet.getId() != null) {
            CompletableFuture.runAsync(() -> {
                snippetService.delete(currentSnippet.getId());
            }, executor).thenRun(() -> Platform.runLater(() -> {
                if (disposed) return;
                loadAllSnippets();
                onNew();
                setStatus(I18N.get("status.deleteSuccess"));
            }));
        }
    }
    
    private void onCopy() {
        String code = codeArea.getText();
        if (!code.isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(code);
            Clipboard.getSystemClipboard().setContent(content);
            setStatus(I18N.get("status.copied"));
        }
    }
    
    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        executor.shutdownNow();
    }
}
