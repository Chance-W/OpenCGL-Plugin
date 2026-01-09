package com.opencgl.qr.utils;

import java.io.File;
import java.util.List;
import javax.swing.filechooser.FileSystemView;

import com.opencgl.qr.i18n.I18N;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

/**
 * 文件选择工具
 *
 * @author xufeng
 */
@SuppressWarnings("unused")
public class FileChooserUtil {

    public static final File HOME_DIRECTORY = FileSystemView.getFileSystemView().getHomeDirectory();

    //选择多个文件
    public static List<File> chooseFiles() {
        return chooseFiles((ExtensionFilter) null);
    }

    public static List<File> chooseFiles(ExtensionFilter... extensionFilter) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("file.choose_file"));
        fileChooser.setInitialDirectory(HOME_DIRECTORY);

        if (extensionFilter != null) {
            fileChooser.getExtensionFilters().addAll(extensionFilter);
        }
        return fileChooser.showOpenMultipleDialog(null);
    }

    public static File chooseFile() {
        return chooseFile((ExtensionFilter) null);
    }

    public static File chooseFile(ExtensionFilter... extensionFilter) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("file.choose_file"));
        fileChooser.setInitialDirectory(HOME_DIRECTORY);

        if (extensionFilter != null) {
            fileChooser.getExtensionFilters().addAll(extensionFilter);
        }

        return fileChooser.showOpenDialog(null);
    }


    public static File chooseSaveFile(ExtensionFilter... extensionFilter) {
        return chooseSaveFile(null, extensionFilter);
    }

    public static File chooseSaveFile(String fileName) {
        return chooseSaveFile(fileName, (ExtensionFilter) null);
    }

    public static File chooseSaveFile(String fileName, ExtensionFilter... extensionFilter) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("file.choose_save"));
        fileChooser.setInitialDirectory(HOME_DIRECTORY);

        if (fileName != null) {
            fileChooser.setInitialFileName(fileName);
        }

        if (extensionFilter != null) {
            fileChooser.getExtensionFilters().addAll(extensionFilter);
        }

        return fileChooser.showSaveDialog(null);
    }
}
