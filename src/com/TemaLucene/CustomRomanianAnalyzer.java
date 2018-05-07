package com.TemaLucene;

/**
 * Created by Mircea on 26.03.2018.
 */

import com.sun.xml.internal.fastinfoset.util.CharArray;
import com.sun.xml.internal.messaging.saaj.util.CharReader;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl;
import org.apache.lucene.search.BoostAttribute;
import org.tartarus.snowball.ext.RomanianStemmer;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Scanner;

public class CustomRomanianAnalyzer extends StopwordAnalyzerBase {
    private final CharArraySet stemExclusionSet;
    public static final String ROMANIAN_STOPWORDS_FILE = "D:\\F Drive\\master\\an1\\sem2\\IR\\resources\\stopwords_ro.txt";
    public static final String DEFAULT_STOPWORDS_FILE = "D:\\F Drive\\master\\an1\\sem2\\IR\\resources\\default_stopwords.txt";
    private static final String STOPWORDS_COMMENT = "#";

    public static CharArraySet getDefaultStopSet() {
        return CustomRomanianAnalyzer.StopwordsSetHolder.DEFAULT_STOPWORDS_SET;
    }

    public CustomRomanianAnalyzer() {
        this(StopwordsSetHolder.DEFAULT_STOPWORDS_SET);
    }

    public CustomRomanianAnalyzer(CharArraySet stopwords) {
        this(stopwords, CharArraySet.EMPTY_SET);
    }

    public CustomRomanianAnalyzer(CharArraySet stopwords, CharArraySet stemExclusionSet) {
        super(stopwords);
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet));
    }

    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new StandardTokenizer();
        TokenStream result = new StandardFilter(source);
        result = new LowerCaseFilter(result);
        result = new ASCIIFoldingFilter(result);
        result = new StopFilter(result, this.stopwords);
        result = new SnowballFilter(result, new RomanianStemmer());
        return new TokenStreamComponents(source, result);
    }

    private static class StopwordsSetHolder
    {
        static final CharArraySet ROMANIAN_STOPWORDS_SET;
        static final CharArraySet DEFAULT_STOPWORDS_SET;

        private StopwordsSetHolder() {
        }

        private static CharArraySet ReadFromFile(String pathString) throws IOException {

            File file = new File(pathString);
            Scanner scanner = new Scanner(file, "UTF-8");

            String entireFile = "";
            while(scanner.hasNext())
            {
                entireFile += scanner.nextLine() + " ";
            }
            scanner.close();

            StandardAnalyzer auxAnalyzer = new StandardAnalyzer();
            TokenStream auxTokenStream = auxAnalyzer.tokenStream(null, entireFile);
            auxTokenStream = new ASCIIFoldingFilter(auxTokenStream);

            CharTermAttribute termGetter = auxTokenStream.addAttribute(CharTermAttribute.class);

            auxTokenStream.reset();

            String normalizedFile = "";

            while (auxTokenStream.incrementToken())
            {
                normalizedFile += termGetter.toString() + "\n";
            }
            Reader reader = new StringReader(normalizedFile);

            auxTokenStream.end();
            auxTokenStream.close();
            auxAnalyzer.close();

            CharArraySet ret = WordlistLoader.getWordSet(reader, STOPWORDS_COMMENT);
            reader.close();

            return ret;
        }

        static {
            try {
//                ROMANIAN_STOPWORDS_SET = CustomRomanianAnalyzer.loadStopwordSet(true, CustomRomanianAnalyzer.class, ROMANIAN_STOPWORDS_FILE, STOPWORDS_COMMENT);
//                DEFAULT_STOPWORDS_SET = CustomRomanianAnalyzer.loadStopwordSet(true, CustomRomanianAnalyzer.class, DEFAULT_STOPWORDS_FILE, STOPWORDS_COMMENT);
                DEFAULT_STOPWORDS_SET = ReadFromFile(DEFAULT_STOPWORDS_FILE);
                ROMANIAN_STOPWORDS_SET = ReadFromFile(ROMANIAN_STOPWORDS_FILE);

            } catch (IOException var1) {
                throw new RuntimeException("Unable to load default stopword set. Exception string: " + var1.toString());
            }
        }
    }
}
