package org.broad.igv.util.blat;

import org.broad.igv.Globals;
import org.broad.igv.util.TestUtils;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.*;

public class BlatClientTest {

    @Test
    public void parseUCSCResult() throws Exception {
        String testPath = TestUtils.DATA_DIR + "blat/UCSC_blat_results.html";
        String response = new String(Files.readAllBytes(Paths.get(testPath)));
        List<String> results = BlatClient.parseResult(response);
        assertEquals(5, results.size());
    }

    @Test
    public void parseCustomResult() throws Exception {
        String testPath = TestUtils.DATA_DIR + "blat/CUSTOM_blat_results.html";
        String response = new String(Files.readAllBytes(Paths.get(testPath)));
        List<String> results = BlatClient.parseResult(response);
        assertEquals(8, results.size());
    }

    @Test
    public void fixWebBlat() throws Exception {
        String testPath = TestUtils.DATA_DIR + "blat/CUSTOM_blat_results.html";
        String response = new String(Files.readAllBytes(Paths.get(testPath)));
        List<String> results = BlatClient.parseResult(response);
        List<String> fixed = BlatClient.fixWebBlat(results);
        assertEquals(8, fixed.size());

        for (String t : fixed) {
            String[] tokens = Globals.singleTabMultiSpacePattern.split(t);
            String chrName = tokens[13];
            assertTrue(chrName.startsWith("chr"));
        }
    }

//    public static void main(String [] args) throws Exception {
//        (new BlatClientTest()).parseUCSCResult();
//        (new BlatClientTest()).parseCustomResult();
//    }
}