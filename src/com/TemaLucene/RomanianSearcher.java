package com.TemaLucene;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;


/**
 * Created by Mircea on 27.03.2018.
 */
public class RomanianSearcher
{
    private String m_indexSaveDirectory;
    private IndexSearcher m_indexSearcher = null;
    private IndexReader m_indexReader = null;
    private QueryParser m_queryParser = null;

    public RomanianSearcher(String indexSaveDirectory)
    {
        m_indexSaveDirectory = indexSaveDirectory;
    }

    public void Load() throws IOException {
        if (m_indexSearcher != null)
        {
            m_indexSearcher = null;

            m_indexReader.close();
            m_indexReader = null;

            m_queryParser = null;
        }
        Directory dir = FSDirectory.open(Paths.get(m_indexSaveDirectory));
        m_indexReader = DirectoryReader.open(dir);
        m_indexSearcher = new IndexSearcher(m_indexReader);
        m_queryParser = new QueryParser("content", new CustomRomanianAnalyzer());
    }

    public TopDocs DoQuery(String queryString) throws ParseException, IOException {
        Query query = m_queryParser.parse(queryString);
        return m_indexSearcher.search(query, 100);
    }

    public String InterpretTopDocs(TopDocs topDocsResult) throws IOException {
        String ret = "";
        for (int i = 0; i < topDocsResult.scoreDocs.length; ++i)
        {
            ScoreDoc scoreDoc = topDocsResult.scoreDocs[i];
            Document retrievedDoc = m_indexSearcher.doc(scoreDoc.doc);
            ret += Integer.toString(i) + ". Path: " + retrievedDoc.get("path"); // here we can actually retrieve all the fields we saved
            ret += "\n" + scoreDoc.toString();
            ret += "\n";
        }
        return ret;
    }

}
