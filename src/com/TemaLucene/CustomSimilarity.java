package com.TemaLucene;

import org.apache.lucene.search.similarities.*;
import org.apache.lucene.util.BytesRef;

/**
 * Created by Mircea on 07.05.2018.
 */

public class CustomSimilarity extends PerFieldSimilarityWrapper
{
    @Override
    public Similarity get(String fieldName)
    {
        return fieldName.equals("abstract") ? new BM25AbstractSimilarity() : new BM25Similarity();
    }
}
