package dev.xethh.tools.utils;

/**
 * Created on 2023-02-18
 */
public class PathUtils {
    /**
     * test if path is unix file path
     * @param path the path to test
     * @return true if path is unix file path
     */
    public static boolean isUnixFilePath(String path) {
        return path.matches("^(/[a-zA-Z]+[a-zA-Z0-9\\-_]*)+");
    }
}
