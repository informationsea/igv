/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.util.blat;

import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.feature.PSLRecord;
import org.broad.igv.feature.Strand;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.feature.tribble.PSLCodec;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.track.*;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.HttpUtils;
import org.broad.igv.util.LongRunningTask;
import org.broad.igv.util.NamedRunnable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.*;

/**
 * Port of perl script blatPlot.pl   http://genomewiki.cse.ucsc.edu/index.php/Blat_Scripts
 *
 * @author jrobinso
 * Date: 11/21/12
 * Time: 8:28 AM
 */
public class BlatClient {

    private static Logger log = Logger.getLogger(BlatClient.class);
    public static final int MINIMUM_BLAT_LENGTH = 20;

    static int sleepTime = 15 * 1000;  //	#	milli seconds to wait between requests

    static String hgsid;  // cached, not sure what this is for but apparently its best to reuse it.
    static long lastQueryTime = 0;

//
//    public static void main(String[] args) throws IOException {
//
//        if (args.length != 6) {
//            Usage();
//            System.exit(255);
//        }
//
//        String org = args[0];
//        String db = args[1];
//        String searchType = args[2];
//        String sortOrder = args[3];
//        String outputType = args[4];
//        String userSeq = args[5];
//
//            if (searchType.equals("BLATGuess")) {
//        searchType = "Blat's Guess";
//    } else if (searchType.equals("transDNA")) {
//        searchType = "translated DNA";
//    } else if (searchType.equals("transRNA")) {
//        searchType = "translated RNA";
//    } else if (searchType.equals("DNA") || (searchType.equals("RNA"))) {
//    } else {
//        System.out.println("ERROR: have not specified an acceptable search type - it should be BLATGuess, transDNA, transRNA, DNA or RNA.");
//        Usage();
//        System.exit(255);
//    }
//        if (outputType.equals("pslNoHeader")) {
//        outputType = "psl no header";
//    } else if (outputType.equals("psl") || outputType.equals("hyperlink")) {
//    } else {
//        System.out.println("ERROR: have not specified an acceptable output type - it should be pslNoHeader, psl or hyperlink.");
//        Usage();
//        System.exit(255);
//    }
//        blat(org, db, searchType, sortOrder, outputType, userSeq);
//
//    }

    static void Usage() {
        System.out.println("usage: BlatBot <organism> <db> <searchType> <sortOrder>");
        System.out.println(" <outputType> <querySequence>");
        System.out.println("\tSpecify organism using the common name with first letter");
        System.out.println("capitalized.");
        System.out.println("\te.g. Human, Mouse, Rat etc.");
        System.out.println("\tDb is database or assembly name e.g hg17, mm5, rn3 etc.");
        System.out.println("\tsearchType can be BLATGuess, DNA, RNA, transDNA or transRNA");
        System.out.println("\tsortOrder can be query,score; query,start; chrom,score");
        System.out.println("\tchrom,start; score.");
        System.out.println("\toutputType can be pslNoHeader, psl or hyperlink.");
        System.out.println("\tblats will be run in groups of $batchCount sequences, all");
    }

    public static List<String> blat(String org, String db, String userSeq) throws IOException {

        String searchType = "DNA";
        String sortOrder = "query,score";
        String outputType = "psl";

        String $url = PreferencesManager.getPreferences().get(Constants.BLAT_URL).trim();
        String serverType = PreferencesManager.getPreferences().get(Constants.BLAT_SERVER_TYPE);

        String result;
        if (serverType.equalsIgnoreCase("web_blat")) {
            String urlString = ($url + "?&wb_qtype=" + searchType + "&wb_sort=" + sortOrder +
                    "&wb_output=" + outputType + "&wb_seq=" + userSeq); // + "&hgsid=" + hgsid);
            //log.info("BLAT: " + urlString);
            result = HttpUtils.getInstance().getContentsAsString(new URL(urlString));

        } else {
            String urlString = ($url + "?org=" + org + "&db=" + db + "&type=" + searchType + "&sort=" + sortOrder +
                    "&output=" + outputType); // + "&hgsid=" + hgsid);

            //if an hgsid was obtained from the output of the first batch then resuse.  I'm not sure what this is all about.
            if (hgsid != null) {
                urlString += "&hgsid=" + hgsid;
            }

            URL url = HttpUtils.createURL(urlString);
            long dt = System.currentTimeMillis() - lastQueryTime;
            if (dt < sleepTime) {
                try {
                    Thread.sleep(dt);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
            lastQueryTime = System.currentTimeMillis();
            Map<String, String> params = new HashMap();
            params.put("userSeq", userSeq);
            result = HttpUtils.getInstance().doPost(url, params);

        }

        return parseResult(result);
    }

    public static List<String> webBlat(String userSeq) throws IOException {

        String searchType = "DNA";
        String sortOrder = "query,score";
        String outputType = "psl";

        String $url = PreferencesManager.getPreferences().get(Constants.BLAT_URL).trim();

        String result;
        String urlString = ($url + "?&wb_qtype=" + searchType + "&wb_sort=" + sortOrder +
                "&wb_output=" + outputType + "&wb_seq=" + userSeq); // + "&hgsid=" + hgsid);
        log.info("BLAT: " + urlString);
        result = HttpUtils.getInstance().getContentsAsString(new URL(urlString));

        List<String> records = parseResult(result);
        return fixWebBlat(records);
    }

    static List<String> fixWebBlat(List<String> records) {
        // Hack -- weblat appends the filename to sequenc names.  Strip it
        List<String> fixed = new ArrayList<>(records.size());
        for (String line : records) {
            if (line.startsWith("#")) {
                fixed.add(line);
                continue;
            }

            String fixedLine = "";
            String[] tokens = Globals.singleTabMultiSpacePattern.split(line);
            for (int i = 0; i < tokens.length; i++) {
                if (i > 0) {
                    fixedLine += "\t";
                }
                String t = tokens[i];
                if (i == 13) {
                    int idx = t.indexOf(":");
                    if (idx > 0) {
                        t = t.substring(idx + 1);
                    }
                }
                fixedLine += t;
            }
            fixed.add(fixedLine);
        }
        return fixed;
    }

    /**
     * Return the parsed results as an array of PSL records, where each record is simply an array of tokens.
     *
     * @param result
     * @return
     * @throws IOException
     */
    static List<String> parseResult(String result) throws IOException {

        List<String> records = new ArrayList<>();

        BufferedReader br = new BufferedReader(new StringReader(result));
        String l;
        boolean pslSectionFound = false;
        boolean pslHeaderFound = false;
        while ((l = br.readLine()) != null) {

            String line = l.trim();
            String lowerCase = line.toLowerCase();

            if (pslHeaderFound) {

                if (lowerCase.contains("</tt>")) {
                    break;
                }

                String[] tokens = Globals.whitespacePattern.split(line);
                if (tokens.length != 21) {
                    // PSL record section over
                    // Error?
                } else {
                    records.add(line);
                }
            }

            if (lowerCase.contains("<tt>") && lowerCase.contains("<pre>") && lowerCase.contains("pslayout")) {
                pslSectionFound = true;
                continue;
            }

            if (pslSectionFound) {
                if (lowerCase.startsWith("-----------------------------")) {
                    pslHeaderFound = true;
                }
            }
        }

        return records;
    }

    public static void doBlatQuery(final String chr, final int start, final int end, Strand strand) {
        doBlatQuery(chr, start, end, strand, "Blat");
    }

    public static void doBlatQuery(final String chr, final int start, final int end, Strand strand, final String trackLabel) {

        if ((end - start) > 8000) {
            MessageUtils.showMessage("BLAT searches are limited to 8kb.  Please try a shorter sequence.");
            return;
        }

        Genome genome = GenomeManager.getInstance().getCurrentGenome();
        final byte[] seqBytes = genome.getSequence(chr, start, end);
        String userSeq = new String(seqBytes);

        if (strand == Strand.NEGATIVE) {
            userSeq = SequenceTrack.getReverseComplement(userSeq);
        }

        doBlatQuery(userSeq);
    }

    public static void doBlatQuery(final String userSeq) {
        doBlatQuery(userSeq, "Blat");
    }

    public static void doBlatQuery(final String userSeq, final String trackLabel) {
        LongRunningTask.submit(new NamedRunnable() {
            public String getName() {
                return "Blat sequence";
            }

            public void run() {
                try {

                    String serverType = PreferencesManager.getPreferences().get(Constants.BLAT_SERVER_TYPE);
                    List<String> tokensList;
                    if (serverType.equalsIgnoreCase("web_blat")) {
                        tokensList = BlatClient.webBlat(userSeq);

                    } else {

                        Genome genome = IGV.hasInstance() ? GenomeManager.getInstance().getCurrentGenome() : null;

                        String db = genome.getId();
                        String species = genome.getSpecies();

                        if (species == null) {
                            MessageUtils.showMessage("Cannot determine species name for genome: " + genome.getDisplayName());
                            return;
                        }

                        tokensList = BlatClient.blat(species, db, userSeq);
                    }

                    if (tokensList.isEmpty()) {
                        MessageUtils.showMessage("No features found");
                    } else {
                        BlatTrack newTrack = new BlatTrack(userSeq, tokensList, trackLabel); //species, userSeq, db, genome, trackLabel);
                        IGV.getInstance().getTrackPanel(IGV.FEATURE_PANEL_NAME).addTrack(newTrack);
                        IGV.getInstance().repaint();
                        BlatQueryWindow win = new BlatQueryWindow(IGV.getMainFrame(), userSeq, newTrack.getFeatures());
                        win.setVisible(true);

                    }
                } catch (Exception e1) {
                    MessageUtils.showErrorMessage("Error running blat", e1);
                }
            }
        });
    }


    public static JMenuItem getMenuItem() {
        JMenuItem menuItem = new JMenuItem("BLAT ...");
        menuItem.addActionListener(e -> {

            String blatSequence = MessageUtils.showInputDialog("Enter sequence to blat:");
            if (blatSequence != null) {
                doBlatQuery(blatSequence);
            }

        });

        return menuItem;
    }
}

