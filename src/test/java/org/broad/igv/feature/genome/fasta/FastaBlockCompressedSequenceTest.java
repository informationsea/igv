package org.broad.igv.feature.genome.fasta;

import org.broad.igv.feature.genome.Sequence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by jrobinso on 6/23/17.
 */
public class FastaBlockCompressedSequenceTest {


    @Test
    public void findBlockContaining() throws Exception {

        String fasta = "https://s3.amazonaws.com/igv.broadinstitute.org/genomes/seq/hg38/hg38.fa.gz";

        FastaBlockCompressedSequence seq = new FastaBlockCompressedSequence(fasta);

        FastaBlockCompressedSequence.Mapping mapping = seq.findBlockContaining(1000000);

        assertNotNull(mapping);

    }

    @Test
    public void compareSequences() throws Exception {

        String sequencePath = "https://s3.amazonaws.com/igv.broadinstitute.org/genomes/seq/hg38/hg38.fa";
        String compressedSequencePath = "https://s3.amazonaws.com/igv.broadinstitute.org/genomes/seq/hg38/hg38.fa.gz";

        Sequence fastaSequence = new FastaIndexedSequence(sequencePath);
        Sequence bgSequence = new FastaBlockCompressedSequence(compressedSequencePath);

        int len1 = fastaSequence.getChromosomeLength("chr12");
        int len2 = bgSequence.getChromosomeLength("chr12");
        assertEquals(len1, len2);

        byte[] seq1 = fastaSequence.getSequence("chr12", 50000, 51000, false);
        byte[] seq2 = fastaSequence.getSequence("chr12", 50000, 51000, false);

        for (int i = 0; i < seq1.length; i++) {
            byte b1 = seq1[i];
            if (b1 >= 97) b1 -= 32;

            byte b2 = seq2[i];
            if (b2 >= 97) b2 -= 32;

            if (b1 != b2) {
                assertEquals("Seq mismatch at position " + (i + 1) + "   " + Character.toString(b1) + " <> " + Character.toString(b2), b1, b2);
            }
        }
    }

}
