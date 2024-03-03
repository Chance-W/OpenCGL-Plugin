package com.xtool.opencgl.utils;

import javafx.event.EventHandler;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import org.apache.commons.io.FileUtils;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.nio.charset.Charset;

public class FileChooserUtil {
    //private static MyLogger myLogger = new MyLogger(FileChooserUtil.class);

    public FileChooserUtil() {
    }

    public static File chooseFile() {
        return chooseFile((ExtensionFilter[])null);
    }

    public static File chooseFile(ExtensionFilter... extensionFilter) {
        File file = null;

        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("请选择文件");
            fileChooser.setInitialDirectory(FileSystemView.getFileSystemView().getHomeDirectory());
            if (extensionFilter != null) {
                fileChooser.getExtensionFilters().addAll(extensionFilter);
            }

            file = fileChooser.showOpenDialog((Window)null);
        } catch (NullPointerException var3) {
            var3.printStackTrace();
            //myLogger.error(var3);
        }

        return file;
    }

    public static File chooseSaveFile(ExtensionFilter... extensionFilter) {
        return chooseSaveFile((String)null, extensionFilter);
    }

    public static File chooseSaveFile(String fileName) {
        return chooseSaveFile(fileName, (ExtensionFilter[])null);
    }

    public static File chooseSaveFile(String fileName, ExtensionFilter... extensionFilter) {
        File file = null;

        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialDirectory(FileSystemView.getFileSystemView().getHomeDirectory());
            if (fileName != null) {
                fileChooser.setInitialFileName(fileName);
            }

            if (extensionFilter != null) {
                fileChooser.getExtensionFilters().addAll(extensionFilter);
            }

            file = fileChooser.showSaveDialog((Window)null);
        } catch (NullPointerException var4) {
            var4.printStackTrace();
            //myLogger.error(var4);
        }

        return file;
    }

    public static File chooseSaveCommonImageFile(String fileName) {
        chooseSaveFile(fileName, new ExtensionFilter("All Images", new String[]{"*.*"}), new ExtensionFilter("JPG", new String[]{"*.jpg"}), new ExtensionFilter("PNG", new String[]{"*.png"}), new ExtensionFilter("gif", new String[]{"*.gif"}), new ExtensionFilter("jpeg", new String[]{"*.jpeg"}), new ExtensionFilter("bmp", new String[]{"*.bmp"}));
        return chooseSaveFile(fileName, (ExtensionFilter[])null);
    }

    public static File chooseSaveImageFile(String fileName) {
        File file = chooseSaveFile(fileName, new ExtensionFilter("All Images", new String[]{"*.*"}), new ExtensionFilter("JPG", new String[]{"*.jpg"}), new ExtensionFilter("PNG", new String[]{"*.png"}), new ExtensionFilter("gif", new String[]{"*.gif"}), new ExtensionFilter("jpeg", new String[]{"*.jpeg"}), new ExtensionFilter("bmp", new String[]{"*.bmp"}), new ExtensionFilter("ICO", new String[]{"*.ico"}), new ExtensionFilter("RGBE", new String[]{"*.rgbe"}));
        return file;
    }

    public static File chooseDirectory() {
        File file = null;

        try {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            file = directoryChooser.showDialog((Window)null);
        } catch (NullPointerException var2) {
            var2.printStackTrace();
           // myLogger.error(var2);
        }

        return file;
    }

    public static void setOnDrag(final TextField textField, final FileChooserUtil.FileType fileType) {
        textField.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                if (event.getGestureSource() != textField) {
                    event.acceptTransferModes(TransferMode.ANY);
                }

            }
        });
        textField.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                Dragboard dragboard = event.getDragboard();
                if (dragboard.hasFiles()) {
                    try {
                        File file = (File)dragboard.getFiles().get(0);
                        if (file != null) {
                            if (fileType == FileChooserUtil.FileType.FILE) {
                                if (file.isFile()) {
                                    textField.setText(file.getAbsolutePath());
                                }
                            } else if (fileType == FileChooserUtil.FileType.FOLDER && file.isDirectory()) {
                                textField.setText(file.getAbsolutePath());
                            }
                        }
                    } catch (Exception var4) {
                       // FileChooserUtil.myLogger.error(var4);
                    }
                }

            }
        });
    }

    public static void setOnDragByOpenFile(final TextInputControl textField) {
        textField.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                if (event.getGestureSource() != textField) {
                    event.acceptTransferModes(TransferMode.ANY);
                }

            }
        });
        textField.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                Dragboard dragboard = event.getDragboard();
                if (dragboard.hasFiles()) {
                    try {
                        File file = (File)dragboard.getFiles().get(0);
                        if (file != null && file.isFile()) {
                            textField.setAccessibleText(file.getAbsolutePath());
                            textField.setText(FileUtils.readFileToString(file, Charset.defaultCharset()));
                        }
                    } catch (Exception var4) {
                       // FileChooserUtil.myLogger.error(var4);
                    }
                }

            }
        });
    }

    public static enum FileType {
        FILE,
        FOLDER;

        private FileType() {
        }
    }
}

