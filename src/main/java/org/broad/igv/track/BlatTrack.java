package org.broad.igv.track;

import org.apache.log4j.Logger;
import org.broad.igv.event.DataLoadedEvent;
import org.broad.igv.event.IGVEventBus;
import org.broad.igv.feature.PSLRecord;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.feature.tribble.PSLCodec;
import org.broad.igv.renderer.IGVFeatureRenderer;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.panel.IGVPopupMenu;
import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.blat.BlatClient;
import org.broad.igv.util.blat.BlatQueryWindow;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class BlatTrack extends FeatureTrack {

    private static Logger log = Logger.getLogger(BlatTrack.class);
    private List<String> tokensList;

    String sequence;
    List<PSLRecord> features;

    public BlatTrack() {
        setDisplayMode(Track.DisplayMode.SQUISHED);
        setColor(Color.DARK_GRAY);
    }

    public BlatTrack(String sequence, List<String> tokensList, String trackLabel) {
        super(sequence, trackLabel);
        this.tokensList = tokensList;
        setDisplayMode(Track.DisplayMode.SQUISHED);
        setColor(Color.DARK_GRAY);
        init();
    }

    private void init() {

        setUseScore(true);

        // Convert tokens to features
        Genome genome = GenomeManager.getInstance().getCurrentGenome();
        PSLCodec codec = new PSLCodec(genome, true);

        features = new ArrayList<>(tokensList.size());
        for (String tokens : tokensList) {
            PSLRecord f = codec.decode(tokens);
            if (f != null) {
                features.add(f);
            }
        }

        if (features.isEmpty()) {
            MessageUtils.showMessage("No features found");
        }

        this.source = new FeatureCollectionSource(features, genome);

        this.renderer = new IGVFeatureRenderer();

        IGVEventBus.getInstance().subscribe(DataLoadedEvent.class, this);
    }

    private void openTableView() {

        BlatQueryWindow win = new BlatQueryWindow(IGV.getMainFrame(), sequence, features);
        win.setVisible(true);
    }

    public List<PSLRecord> getFeatures() {
        return features;
    }

    @Override
    protected boolean isShowFeatures(ReferenceFrame frame) {
        return true;
    }

    public IGVPopupMenu getPopupMenu(final TrackClickEvent te) {
        IGVPopupMenu menu = TrackMenuUtils.getPopupMenu(Arrays.asList(this), "Menu", te);

        JMenuItem item = new JMenuItem("Open table view");
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                openTableView();
            }
        });

        menu.addSeparator();
        menu.add(item);

        return menu;
    }


    @Override
    public void marshalXML(Document document, Element element) {

        super.marshalXML(document, element);

        String tmp = "";
        for(String t : tokensList) {
            tmp += (t + "\n");
        }
        element.setAttribute("tokensList", tmp);
    }

    @Override
    public void unmarshalXML(Element element, Integer version) {

        super.unmarshalXML(element, version);

        if (element.hasAttribute("sequence")) {
            // Legacy tracks
            String sequence = element.getAttribute("sequence");
            String species = element.getAttribute("species");
            String db = element.getAttribute("db");
            try {
                this.tokensList = BlatClient.blat(species, db, sequence);
            } catch (IOException e) {
                MessageUtils.showMessage("Error restoring blat track: " + e.getMessage());
                log.error("Error restoring blat track", e);
            }
        } else {
            String tmp = element.getAttribute("tokensList");
            this.tokensList = Arrays.asList(tmp.split("\n"));
        }

        init();

    }


}
