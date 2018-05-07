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
import org.apache.lucene.index.FieldInvertState;
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
        TextField x = new TextField("abstract", "asd", Field.Store.YES);

        doc.add(new StringField("name", f.getName(), Field.Store.YES));
        doc.add(new StringField("path", f.getAbsolutePath(), Field.Store.YES));
        doc.add(new StringField("date", new Date(f.lastModified()).toString(), Field.Store.YES));
        String content =  GetContent(f);
        String abstractContent = ExtractAbstractContent(content);
        doc.add(new TextField("content", content, Field.Store.YES));
        doc.add(new TextField("abstract", abstractContent, Field.Store.YES));

        indexWriter.addDocument(doc);
        Logger.getGlobal().log(Level.INFO, "Indexed document: " + f.getAbsolutePath());
    }

    private String GetContent(File f) throws IOException, TikaException {
        String ret = "";
        Tika tika = new Tika();
        ret = tika.parseToString(f);
        return ret;
    }

    private String ExtractAbstractContent(String s)
    {
        // abstract = first 10 words
        int spacePos = s.indexOf(' ');
        if (spacePos == -1)
            spacePos = s.indexOf('\n');
        if (spacePos == -1)
            spacePos = s.indexOf('\t');
        if (spacePos == -1)
            spacePos = s.indexOf('\r');
        if (spacePos == -1)
            return s;

        for (int i = 0; i < 9; ++i)
        {
            if (spacePos + 1 >= s.length())
                return s;

            int newSpacePos1 = s.indexOf(' ', spacePos + 1);
            int newSpacePos2 = s.indexOf('\n', spacePos + 1);
            int newSpacePos3 = s.indexOf('\t', spacePos + 1);
            int newSpacePos4 = s.indexOf('\r', spacePos + 1);

            while (newSpacePos1 == spacePos + 1 || newSpacePos2 == spacePos + 1 || newSpacePos3 == spacePos + 1 || newSpacePos4 == spacePos + 4)
            {
                spacePos++;
                newSpacePos1 = s.indexOf(' ', spacePos + 1);
                newSpacePos2 = s.indexOf('\n', spacePos + 1);
                newSpacePos3 = s.indexOf('\t', spacePos + 1);
                newSpacePos4 = s.indexOf('\r', spacePos + 1);
            }

            if (newSpacePos1 == -1 && newSpacePos2 == -1 && newSpacePos3 == -1 && newSpacePos4 == -1)
                return s;

            if (newSpacePos1 == -1)
                newSpacePos1 = s.length() + 1;
            if (newSpacePos2 == -1)
                newSpacePos2 = s.length() + 1;
            if (newSpacePos3 == -1)
                newSpacePos3 = s.length() + 1;
            if (newSpacePos4 == -1)
                newSpacePos4 = s.length() + 1;

            spacePos = Math.min (Math.min(newSpacePos1, newSpacePos2), Math.min(newSpacePos3, newSpacePos4)) + 1;

        }
        return s.substring(0, spacePos);
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