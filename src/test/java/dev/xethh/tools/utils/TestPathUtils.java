package dev.xethh.tools.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created on 2023-02-18
 */
public class TestPathUtils {
    @Test
    public void testIsUnixFilePath() {
        String path = "/";
        assertEquals(false, PathUtils.isUnixFilePath(path));
        path = "/a";
        assertEquals(true, PathUtils.isUnixFilePath(path));

        path = "/0";
        assertEquals(false, PathUtils.isUnixFilePath(path));
        path = "/a0";
        assertEquals(true, PathUtils.isUnixFilePath(path));

        path = "/-";
        assertEquals(false, PathUtils.isUnixFilePath(path));
        path = "/a-";
        assertEquals(true, PathUtils.isUnixFilePath(path));

        path = "/_";
        assertEquals(false, PathUtils.isUnixFilePath(path));

        path = "/a_";
        assertEquals(true, PathUtils.isUnixFilePath(path));

        path = "/home/Downloads";
        assertEquals(true, PathUtils.isUnixFilePath(path));

        path = "/home/_ownloads";
        assertEquals(false, PathUtils.isUnixFilePath(path));

        path = "c:\\a\\b";
        assertEquals(false, PathUtils.isUnixFilePath(path));
    }

}
