package com.TemaLucene;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.tika.exception.TikaException;
import org.tartarus.snowball.ext.RomanianStemmer;

import org.apache.tika.Tika;


public class RomanianIndexer {
    private String m_documentsDirectory;
    private String m_indexSaveDirectory;

    public RomanianIndexer(String documentsDirectory, String indexSaveDirectory)
    {
        m_documentsDirectory = documentsDirectory;
        m_indexSaveDirectory = indexSaveDirectory;
    }

    public void DoIndexing() throws IOException, TikaException {
        CustomRomanianAnalyzer analyzer = new CustomRomanianAnalyzer();

        IndexWriterConfig indexWriterConfiguration = new IndexWriterConfig(analyzer);

        indexWriterConfiguration.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        Directory directory = FSDirectory.open(Paths.get(m_indexSaveDirectory));

        IndexWriter indexWriter = new IndexWriter(directory, indexWriterConfiguration);

        DFS(m_documentsDirectory, indexWriter);

       // directory.close();
        indexWriter.close();

        analyzer.close();

    }

    private void DFS(String dirPath, IndexWriter indexWriter) throws IOException, TikaException {
        File file = new File(dirPath);
        File[] filesInside = file.listFiles();

        for (File f : filesInside)
        {
            if (f.isDirectory())
            {
                DFS(f.getAbsolutePath(), indexWriter);
            }
            else
            {
                if (IsAcceptableFile(f))
                {
                    IndexFile(f, indexWriter);
                }
            }
        }
    }

    private void IndexFile(File f, IndexWriter indexWriter) throws IOException, TikaException {

        Document doc = new Document();
        doc.add(new StringField("name", f.getName(), Field.Store.YES));
        doc.add(new StringField("path", f.getAbsolutePath(), Field.Store.YES));
        doc.add(new StringField("date", new Date(f.lastModified()).toString(), Field.Store.YES));
        doc.add(new TextField("content", GetContent(f), Field.Store.YES));
        indexWriter.addDocument(doc);
        Logger.getGlobal().log(Level.INFO, "Indexed document: " + f.getAbsolutePath());
    }

    private String GetContent(File f) throws IOException, TikaException {
        String ret = "";
        Tika tika = new Tika();
        ret = tika.parseToString(f);
        return ret;
    }

    private boolean IsAcceptableFile(File f)
    {
        String name = f.getName();
        for (String extension : acceptableExtensions)
        {
            if (name.toLowerCase().endsWith(extension))
            {
                return true;
            }
        }
        return false;
    }

    public static ArrayList<String> acceptableExtensions = new ArrayList<>();

    static
    {
        acceptableExtensions.add(".doc");
        acceptableExtensions.add(".docx");
        acceptableExtensions.add(".pdf");
        acceptableExtensions.add(".txt");
    }
}