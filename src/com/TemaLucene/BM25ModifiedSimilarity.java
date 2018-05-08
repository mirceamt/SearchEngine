package com.TemaLucene;

import org.apache.lucene.index.*;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mircea on 08.05.2018.
 */
public class BM25ModifiedSimilarity extends Similarity {

    private final float k1;
    private final float b;
    protected boolean discountOverlaps;
    private static final float[] OLD_LENGTH_TABLE = new float[256];
    private static final float[] LENGTH_TABLE = new float[256];

    public BM25ModifiedSimilarity(float k1, float b) {
        this.discountOverlaps = true;
        if(Float.isFinite(k1) && k1 >= 0.0F) {
            if(!Float.isNaN(b) && b >= 0.0F && b <= 1.0F) {
                this.k1 = k1;
                this.b = b;
            } else {
                throw new IllegalArgumentException("illegal b value: " + b + ", must be between 0 and 1");
            }
        } else {
            throw new IllegalArgumentException("illegal k1 value: " + k1 + ", must be a non-negative finite value");
        }
    }

    public BM25ModifiedSimilarity() {
        this(1.2F, 0.75F);
    }

    protected float idf(long docFreq, long docCount) {
        return (float)Math.log(1.0D + ((double)(docCount - docFreq) + 0.5D) / ((double)docFreq + 0.5D));
    }

    protected float sloppyFreq(int distance) {
        return 1.0F / (float)(distance + 1);
    }

    protected float scorePayload(int doc, int start, int end, BytesRef payload) {
        return 1.0F;
    }

    protected float avgFieldLength(CollectionStatistics collectionStats) {
        long sumTotalTermFreq;
        if(collectionStats.sumTotalTermFreq() == -1L) {
            if(collectionStats.sumDocFreq() == -1L) {
                return 1.0F;
            }

            sumTotalTermFreq = collectionStats.sumDocFreq();
        } else {
            sumTotalTermFreq = collectionStats.sumTotalTermFreq();
        }

        long docCount = collectionStats.docCount() == -1L?collectionStats.maxDoc():collectionStats.docCount();
        return (float)((double)sumTotalTermFreq / (double)docCount);
    }

    public void setDiscountOverlaps(boolean v) {
        this.discountOverlaps = v;
    }

    public boolean getDiscountOverlaps() {
        return this.discountOverlaps;
    }

    public final long computeNorm(FieldInvertState state) {
        int numTerms = this.discountOverlaps?state.getLength() - state.getNumOverlap():state.getLength();
        int indexCreatedVersionMajor = state.getIndexCreatedVersionMajor();
        return indexCreatedVersionMajor >= 7?(long) SmallFloat.intToByte4(numTerms):(long)SmallFloat.floatToByte315((float)(1.0D / Math.sqrt((double)numTerms)));
    }

    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
        long df = termStats.docFreq();
        long docCount = collectionStats.docCount() == -1L?collectionStats.maxDoc():collectionStats.docCount();
        float idf = this.idf(df, docCount);
        return Explanation.match(idf, "idf, computed as log(1 + (docCount - docFreq + 0.5) / (docFreq + 0.5)) from:", new Explanation[]{Explanation.match((float)df, "docFreq", new Explanation[0]), Explanation.match((float)docCount, "docCount", new Explanation[0])});
    }

    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics[] termStats) {
        double idf = 0.0D;
        List<Explanation> details = new ArrayList();
        TermStatistics[] var6 = termStats;
        int var7 = termStats.length;

        for(int var8 = 0; var8 < var7; ++var8) {
            TermStatistics stat = var6[var8];
            Explanation idfExplain = this.idfExplain(collectionStats, stat);
            details.add(idfExplain);
            idf += (double)idfExplain.getValue();
        }

        return Explanation.match((float)idf, "idf(), sum of:", details);
    }

    public final SimWeight computeWeight(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        Explanation idf = termStats.length == 1?this.idfExplain(collectionStats, termStats[0]):this.idfExplain(collectionStats, termStats);
        float avgdl = this.avgFieldLength(collectionStats);
        float[] oldCache = new float[256];
        float[] cache = new float[256];

        for(int i = 0; i < cache.length; ++i) {
            oldCache[i] = this.k1 * (1.0F - this.b + this.b * OLD_LENGTH_TABLE[i] / avgdl);
            cache[i] = this.k1 * (1.0F - this.b + this.b * LENGTH_TABLE[i] / avgdl);
        }

        return new BM25ModifiedSimilarity.BM25Stats(collectionStats.field(), boost, idf, avgdl, oldCache, cache);
    }

    public final SimScorer simScorer(SimWeight stats, LeafReaderContext context) throws IOException {
        BM25ModifiedSimilarity.BM25Stats bm25stats = (BM25ModifiedSimilarity.BM25Stats)stats;
        return new BM25ModifiedSimilarity.BM25DocScorer(bm25stats, context.reader().getMetaData().getCreatedVersionMajor(), context.reader().getNormValues(bm25stats.field));
    }

    private Explanation explainTFNorm(int doc, Explanation freq, BM25ModifiedSimilarity.BM25Stats stats, NumericDocValues norms, float[] lengthCache) throws IOException {
        List<Explanation> subs = new ArrayList();
        subs.add(freq);
        subs.add(Explanation.match(this.k1, "parameter k1", new Explanation[0]));
        if(norms == null) {
            subs.add(Explanation.match(0.0F, "parameter b (norms omitted for field)", new Explanation[0]));
            return Explanation.match(freq.getValue() * (this.k1 + 1.0F) / (freq.getValue() + this.k1), "tfNorm, computed as (freq * (k1 + 1)) / (freq + k1) from:", subs);
        } else {
            byte norm;
            if(norms.advanceExact(doc)) {
                norm = (byte)((int)norms.longValue());
            } else {
                norm = 0;
            }

            float doclen = lengthCache[norm & 255];
            subs.add(Explanation.match(this.b, "parameter b", new Explanation[0]));
            subs.add(Explanation.match(stats.avgdl, "avgFieldLength", new Explanation[0]));
            subs.add(Explanation.match(doclen, "fieldLength", new Explanation[0]));
            return Explanation.match(freq.getValue() * (this.k1 + 1.0F) / (freq.getValue() + this.k1 * (1.0F - this.b + this.b * doclen / stats.avgdl)), "tfNorm, computed as (freq * (k1 + 1)) / (freq + k1 * (1 - b + b * fieldLength / avgFieldLength)) from:", subs);
        }
    }

    private Explanation explainScore(int doc, Explanation freq, BM25ModifiedSimilarity.BM25Stats stats, NumericDocValues norms, float[] lengthCache) throws IOException {
        Explanation boostExpl = Explanation.match(stats.boost, "boost", new Explanation[0]);
        List<Explanation> subs = new ArrayList();
        if(boostExpl.getValue() != 1.0F) {
            subs.add(boostExpl);
        }

        subs.add(stats.idf);
        Explanation tfNormExpl = this.explainTFNorm(doc, freq, stats, norms, lengthCache);
        subs.add(tfNormExpl);
        return Explanation.match(boostExpl.getValue() * stats.idf.getValue() * tfNormExpl.getValue(), "score(doc=" + doc + ",freq=" + freq + "), product of:", subs);
    }

    public String toString() {
        return "BM25(k1=" + this.k1 + ",b=" + this.b + ")";
    }

    public final float getK1() {
        return this.k1;
    }

    public final float getB() {
        return this.b;
    }

    static {
        int i;
        for(i = 1; i < 256; ++i) {
            float f = SmallFloat.byte315ToFloat((byte)i);
            OLD_LENGTH_TABLE[i] = 1.0F / (f * f);
        }

        OLD_LENGTH_TABLE[0] = 1.0F / OLD_LENGTH_TABLE[255];

        for(i = 0; i < 256; ++i) {
            LENGTH_TABLE[i] = (float)SmallFloat.byte4ToInt((byte)i);
        }

    }

    private static class BM25Stats extends SimWeight {
        private final Explanation idf;
        private final float avgdl;
        private final float boost;
        private final float weight;
        private final String field;
        private final float[] oldCache;
        private final float[] cache;

        BM25Stats(String field, float boost, Explanation idf, float avgdl, float[] oldCache, float[] cache) {
            this.field = field;
            this.boost = boost;
            this.idf = idf;
            this.avgdl = avgdl;
            this.weight = idf.getValue() * boost;
            this.oldCache = oldCache;
            this.cache = cache;
        }
    }

    private class BM25DocScorer extends SimScorer {
        private final BM25ModifiedSimilarity.BM25Stats stats;
        private final float weightValue;
        private final NumericDocValues norms;
        private final float[] lengthCache;
        private final float[] cache;

        BM25DocScorer(BM25ModifiedSimilarity.BM25Stats stats, int indexCreatedVersionMajor, NumericDocValues norms) throws IOException {
            this.stats = stats;
            this.weightValue = stats.weight * (BM25ModifiedSimilarity.this.k1 + 1.0F);
            this.norms = norms;
            if(indexCreatedVersionMajor >= 7) {
                this.lengthCache = BM25ModifiedSimilarity.LENGTH_TABLE;
                this.cache = stats.cache;
            } else {
                this.lengthCache = BM25ModifiedSimilarity.OLD_LENGTH_TABLE;
                this.cache = stats.oldCache;
            }

        }

        public float score(int doc, float freq) throws IOException {
            float norm;
            if(this.norms == null) {
                norm = BM25ModifiedSimilarity.this.k1;
            } else if(this.norms.advanceExact(doc)) {
                norm = this.cache[(byte)((int)this.norms.longValue()) & 255];
            } else {
                norm = this.cache[0];
            }

            float coef = 1.0f;
            if (stats.field.equals("abstract"))
            {
                coef = 3.0f;
            }

            return coef * this.weightValue * freq / (freq + norm);
        }

        public Explanation explain(int doc, Explanation freq) throws IOException {
            return BM25ModifiedSimilarity.this.explainScore(doc, freq, this.stats, this.norms, this.lengthCache);
        }

        public float computeSlopFactor(int distance) {
            return BM25ModifiedSimilarity.this.sloppyFreq(distance);
        }

        public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
            return BM25ModifiedSimilarity.this.scorePayload(doc, start, end, payload);
        }
    }
}
