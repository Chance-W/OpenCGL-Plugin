package com.opencgl.base.utils;

import java.io.File;
import java.util.Comparator;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class FileSortUtil implements Comparator<File> {

    @Override
    public int compare(File f1, File f2) {
        long diff = f1.lastModified() - f2.lastModified();
        if (diff > 0) {
            return -1;
        } else if (diff == 0) {
            return 0;
        } else {
            return 1;
        }
    }

}

