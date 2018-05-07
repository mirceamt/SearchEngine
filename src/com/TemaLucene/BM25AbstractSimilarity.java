package com.TemaLucene;

import org.apache.lucene.search.similarities.BM25Similarity;

/**
 * Created by Mircea on 08.05.2018.
 */
public class BM25AbstractSimilarity extends BM25Similarity
{
    @Override
    protected float idf(long docFreq, long docCount)
    {
        return 2.0f * super.idf(docFreq, docCount);
    }
}
