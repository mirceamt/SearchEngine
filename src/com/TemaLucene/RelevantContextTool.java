package com.TemaLucene;

import com.sun.xml.internal.fastinfoset.util.CharArray;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl;
import org.apache.lucene.analysis.tokenattributes.PackedTokenAttributeImpl;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.Token;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.*;

/**
 * Created by Mircea on 22.05.2018.
 */


public class RelevantContextTool {
    class TokenAndOffset {
        public String token;
        public int startOffset;
        public int endOffset;
    }

    class Interval {
        public Interval(int _left, int _right)
        {
            left = _left;
            right = _right;
        }
        public int left, right;
        public List<Interval> boldPositions = new ArrayList<>();

        public void sortBoldPositions()
        {
            for (int i = 0; i < boldPositions.size(); ++i)
            {
                for (int j = i + 1; j < boldPositions.size(); ++ j)
                {
                    Interval x = boldPositions.get(i);
                    Interval y = boldPositions.get(j);
                    if (y.left < x.left)
                    {
                        boldPositions.set(i, y);
                        boldPositions.set(j, x);
                    }
                }
            }
        }
    }

    class Context
    {
        protected List<String> mm_normalizedQueryTerms;
        protected List<TokenAndOffset> mm_tokens;
        Deque<TokenAndOffset> mm_deque;
        String mm_rawDocument;
        public Context(Deque<TokenAndOffset> deque, List<String> normalizedQueryTerms, List<TokenAndOffset> tokens, String rawDocument)
        {
            mm_deque = deque;
            mm_normalizedQueryTerms = normalizedQueryTerms;
            mm_tokens = tokens;
            mm_rawDocument = rawDocument;
        }

        boolean isBetterThan(Context other)
        {
            int lengthOfThis = mm_deque.size();
            int lengthOfOther = other.mm_deque.size();

            if (lengthOfThis < lengthOfOther)
            {
                return true;
            }
            else if (lengthOfOther < lengthOfThis)
            {
                return false;
            }
            else
            {
                int countOfQueryTermsInThis = 0;
                int countOfQueryTermsInOther = 0;
                for (TokenAndOffset current : mm_deque)
                {
                    if (isInQueryTokens(current.token))
                    {
                        countOfQueryTermsInThis += 1;
                    }
                }

                for (TokenAndOffset current : other.mm_deque)
                {
                    if (isInQueryTokens(current.token))
                    {
                        countOfQueryTermsInOther += 1;
                    }
                }

                if (countOfQueryTermsInThis > countOfQueryTermsInOther)
                {
                    return true;
                }
                else if (countOfQueryTermsInOther > countOfQueryTermsInThis)
                {
                    return false;
                }
                else
                {
                    int totalLengthBetweenTermsInThis = 0;
                    int totalLengthBetweenTermsInOther = 0;

                    int countInThis = 0;
                    int countInOther = 0;

                    int partialSumInThis = 0;
                    int partialSumInOther = 0;

                    int index = 0;
                    for (TokenAndOffset current : mm_deque)
                    {
                        ++index;
                        if (isInQueryTokens(current.token))
                        {
                            totalLengthBetweenTermsInThis += countInThis * index - partialSumInThis;
                            countInThis += 1;
                            partialSumInThis += index;
                        }
                    }

                    index = 0;
                    for (TokenAndOffset current : other.mm_deque)
                    {
                        ++index;
                        if (isInQueryTokens(current.token))
                        {
                            totalLengthBetweenTermsInOther += countInOther * index - partialSumInOther;
                            countInOther += 1;
                            partialSumInOther += index;
                        }
                    }

                    if (totalLengthBetweenTermsInThis < totalLengthBetweenTermsInOther)
                    {
                        return true;
                    }
                    else
                    {
                        return false;
                    }
                }
            }
        }

        @Override
        public String toString()
        {
            List<TokenAndOffset> finalTokens = new ArrayList<>();
            for (int i = 0; i < mm_normalizedQueryTerms.size(); ++i)
            {
                String token = mm_normalizedQueryTerms.get(i);
                int index1 = 0;
                int index2 = 0;
                int minim = -1;
                TokenAndOffset bestTokenAndOffset = null;
                for (TokenAndOffset current1 : mm_deque)
                {
                    if (current1.token.equals(token))
                    {
                        int totalAux = 0;
                        index2 = 0;
                        for (TokenAndOffset current2 : mm_deque)
                        {
                            if (isInQueryTokens(current2.token))
                            {
                                totalAux += Math.abs(index1 - index2);
                            }

                            index2 += 1;
                        }
                        if (minim == -1 || totalAux < minim)
                        {
                            minim = totalAux;
                            bestTokenAndOffset = current1;
                        }
                    }

                    index1 += 1;
                }
                finalTokens.add(bestTokenAndOffset);
            }
            List<Interval> intervals = new ArrayList<>();
            for (TokenAndOffset tokenAndOffset : finalTokens)
            {
                if (tokenAndOffset == null)
                {
                    continue;
                }
                Interval currentInterval = getInterval(tokenAndOffset, 5);
                currentInterval.boldPositions.add(new Interval(tokenAndOffset.startOffset, tokenAndOffset.endOffset));
                intervals.add(currentInterval);
            }

            List<Interval> finalIntervals = reunionIntervals(intervals);

            for (int i = 0; i < finalIntervals.size(); ++ i)
            {
                for (int j = i + 1; j < finalIntervals.size(); ++ j)
                {
                    Interval x = finalIntervals.get(i);
                    Interval y = finalIntervals.get(j);
                    if (y.left < x.left)
                    {
                        finalIntervals.set(i, y);
                        finalIntervals.set(j, x);
                    }
                }
            }
            for (int i = 0; i < finalIntervals.size(); ++i)
            {
                finalIntervals.get(i).sortBoldPositions();
            }

            String ret = getTextAccordingToIntervals(finalIntervals);
            return ret;
        }

        private String getTextAccordingToIntervals(List<Interval> finalIntervals)
        {
            String ret = "";
            for (int i = 0; i < finalIntervals.size(); ++i)
            {
                Interval currentInterval = finalIntervals.get(i);
                int index = currentInterval.left;
                if (index != 0)
                {
                    ret += " ... ";
                }
                for (index = currentInterval.left; index < currentInterval.right; ++index)
                {
                    for (int j = 0; j < currentInterval.boldPositions.size(); ++j)
                    {
                        Interval x = currentInterval.boldPositions.get(j);
                        if (index == x.left || index == x.right)
                        {
                            ret += "!!!!";
                        }
                    }
                    ret += mm_rawDocument.charAt(index);
                }
                if (i == finalIntervals.size() - 1 && index != mm_rawDocument.length())
                {
                    ret += " ... ";
                }
            }
            return ret;
        }

        private List<Interval> reunionIntervals(List<Interval> intervals)
        {
            List<Interval> aux = new ArrayList<>();

            while(true)
            {
                Interval newInterval = null;
                int pozi = -1, pozj = -1;
                for (int i = 0; i < intervals.size(); ++i)
                {
                    for (int j = i + 1; j < intervals.size(); ++j)
                    {
                        if (Intersects(intervals.get(i), intervals.get(j)))
                        {
                            Interval x = intervals.get(i);
                            Interval y = intervals.get(j);
                            int left = Math.min(x.left, y.left);
                            int right = Math.max(x.right, y.right);
                            newInterval = new Interval(left, right);
                            newInterval.boldPositions = x.boldPositions;

                            for (Interval boldPosition : y.boldPositions)
                            {
                                newInterval.boldPositions.add(boldPosition);
                            }
                            pozi = i;
                            pozj = j;
                        }
                        if (newInterval != null)
                        {
                            break;
                        }
                    }
                    if (newInterval != null)
                    {
                        break;
                    }
                }
                if (newInterval == null)
                {
                    break;
                }
                else
                {
                    aux.clear();
                    for (int i = 0; i < intervals.size(); ++i)
                    {
                        if (pozi == i || pozj == i)
                        {
                            continue;
                        }
                        aux.add(intervals.get(i));
                    }
                    aux.add(newInterval);
                    intervals.clear();
                    for (Interval x : aux)
                    {
                        intervals.add(x);
                    }
                }
            }
            return intervals;
        }

        private boolean Intersects(Interval x, Interval y)
        {
            if (x.left > y.left)
            {
                Interval aux = x;
                x = y;
                y = aux;
            }
            if (y.left <= x.right)
            {
                return true;
            }
            return false;
        }

        private Interval getInterval(TokenAndOffset tokenAndOffset, int wordsInInterval)
        {
            String separators = " \n\r\t";
            int left = tokenAndOffset.startOffset;
            int cuvinteLeft = 0;
            for (int i = left - 1; ;)
            {
                while (i >= 0 && isSeparator(mm_rawDocument.charAt(i), separators))
                {
                    i--;
                }
                while (i >= 0 && !isSeparator(mm_rawDocument.charAt(i), separators))
                {
                    i--;
                }
                cuvinteLeft++;
                if (cuvinteLeft == wordsInInterval)
                {
                    left = i + 1;
                    break;
                }
                else if (i == -1)
                {
                    left = 0;
                    break;
                }
            }

            int right = tokenAndOffset.endOffset;
            int cuvinteRight = 0;
            for (int i = right; ;)
            {
                while (i < mm_rawDocument.length() && isSeparator(mm_rawDocument.charAt(i), separators))
                {
                    i++;
                }
                while (i < mm_rawDocument.length() && !isSeparator(mm_rawDocument.charAt(i), separators))
                {
                    i++;
                }
                cuvinteRight++;
                if (cuvinteRight == wordsInInterval)
                {
                    right = i;
                    break;
                }
                else if (i == mm_rawDocument.length())
                {
                    right = i;
                    break;
                }

            }
            return new Interval(left, right);
        }

        private boolean isSeparator(char x, String separators)
        {
            char[] separatorsArr = separators.toCharArray();
            for (int i = 0; i < separatorsArr.length; ++i)
            {
                if (separatorsArr[i] == x)
                {
                    return true;
                }
            }
            return false;
        }
    }


    protected List<String> m_normalizedQueryTerms;
    protected List<TokenAndOffset> m_tokens;
    protected String m_queryString;
    protected Document m_doc;
    protected String m_rawDocument;
    protected int m_maxQueryTokensFound = 0;


    public RelevantContextTool(String queryString, Document doc) throws ParseException, IOException {

        m_normalizedQueryTerms = new ArrayList<>();
        m_tokens = new ArrayList<>();
        m_queryString = queryString;
        m_doc = doc;

        CustomRomanianAnalyzer analyzer = new CustomRomanianAnalyzer();
        TokenStream queryTokenStream = analyzer.tokenStream(null, queryString);
        CharTermAttribute termGetter = queryTokenStream.addAttribute(CharTermAttribute.class);
        queryTokenStream.reset();

        while (queryTokenStream.incrementToken())
        {
            String newTerm = termGetter.toString();
            boolean found = false;
            for (String token : m_normalizedQueryTerms)
            {
                if (token.equals(newTerm))
                {
                    found = true;
                }
            }
            if (!found)
            {
                m_normalizedQueryTerms.add(newTerm);
            }
        }

        String abstractFieldValue = doc.get("abstract");
        String contentFieldValue = doc.get("content");

        m_rawDocument = abstractFieldValue + "\n" + contentFieldValue;

        queryTokenStream.close();

        queryTokenStream = analyzer.tokenStream(null, m_rawDocument);
        termGetter = queryTokenStream.addAttribute(CharTermAttribute.class);
        queryTokenStream.reset();

        while (queryTokenStream.incrementToken())
        {
            String newToken = termGetter.toString();
            TokenAndOffset aux = new TokenAndOffset();
            aux.token = newToken;
            aux.startOffset = ((PackedTokenAttributeImpl) termGetter).startOffset();
            aux.endOffset = ((PackedTokenAttributeImpl) termGetter).endOffset();
            m_tokens.add(aux);
        }

        Set<String> viz = new HashSet<>();

        for (int j = 0; j < m_tokens.size(); ++j)
        {
            if (!viz.contains(m_tokens.get(j).token))
            {
                for (int i = 0; i < m_normalizedQueryTerms.size(); ++i)
                {
                    if (m_normalizedQueryTerms.get(i).equals(m_tokens.get(j).token))
                    {
                        viz.add(m_normalizedQueryTerms.get(i));
                        m_maxQueryTokensFound += 1;
                        break;
                    }
                }
            }
        }
    }

    protected boolean isInQueryTokens(String token)
    {
        for (int i = 0; i < m_normalizedQueryTerms.size(); ++i)
        {
            if (m_normalizedQueryTerms.get(i).equals(token))
            {
                return true;
            }
        }
        return false;
    }
    public String extractMostRelevantContext()
    {
        Deque<TokenAndOffset> deque = new ArrayDeque<>();
        String ret = "";
        Map<String, Integer> freq = new HashMap<>();
        for (int i = 0; i < m_normalizedQueryTerms.size(); ++i)
        {
            freq.put(m_normalizedQueryTerms.get(i), 0);
        }
        int queryTokensFound = 0;

        Context bestContext = null;

        int i = 0;
        for (i = 0; i < m_tokens.size(); ++i)
        {
            String token = m_tokens.get(i).token;
            if (isInQueryTokens(token))
            {
                break;
            }
        }

        for (; i < m_tokens.size(); ++i)
        {
            String token = m_tokens.get(i).token;
            deque.addLast(m_tokens.get(i));
            if (isInQueryTokens(token))
            {
                if (freq.get(token) == 0)
                {
                    freq.put(token, 1);
                    queryTokensFound += 1;
                    if (queryTokensFound == m_maxQueryTokensFound)
                    {
                        Context auxContext = new Context(cloneDeque(deque), m_normalizedQueryTerms, m_tokens, m_rawDocument);
                        if (bestContext == null || auxContext.isBetterThan(bestContext))
                        {
                            bestContext = auxContext;
                        }
                        while(queryTokensFound == m_maxQueryTokensFound)
                        {
                            TokenAndOffset removedToken = deque.removeFirst();

                            if (freq.containsKey(removedToken.token))
                            {
                                int aux = freq.get(removedToken.token);
                                freq.put(removedToken.token, aux - 1);
                                if (aux - 1 == 0)
                                {
                                    queryTokensFound -= 1;
                                }
                                else
                                {
                                    auxContext = new Context(cloneDeque(deque), m_normalizedQueryTerms, m_tokens, m_rawDocument);
                                    if (bestContext == null || auxContext.isBetterThan(bestContext))
                                    {
                                        bestContext = auxContext;
                                    }
                                }
                            }
                            else
                            {
                                auxContext = new Context(cloneDeque(deque), m_normalizedQueryTerms, m_tokens, m_rawDocument);
                                if (bestContext == null || auxContext.isBetterThan(bestContext))
                                {
                                    bestContext = auxContext;
                                }
                            }
                        }
                    }
                }
                else
                {
                    int aux = freq.get(token);
                    freq.put(token, aux + 1);
                }
            }
        }

        return bestContext.toString();
    }

    Deque<TokenAndOffset> cloneDeque(Deque<TokenAndOffset> deque)
    {
        Deque<TokenAndOffset> ret = new ArrayDeque<>();
        for (TokenAndOffset aux : deque)
        {
            ret.addLast(aux);
        }
        return ret;
    }
}
