package com.opencgl.base.utils;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang.StringUtils;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2024/01/31 13:52
 * @since v9.0
 */
@SuppressWarnings("unused")
public abstract class PathValidatorUtil {

    public static boolean isValidPath(String pathString) {
        try {
            if (StringUtils.isEmpty(pathString)){
                return false;
            }
            Path path = Paths.get(pathString);
            // 如果路径是合法的，返回true
            return true;
        }
        catch (InvalidPathException e) {
            // 如果路径不合法，返回false
            return false;
        }
    }
}
