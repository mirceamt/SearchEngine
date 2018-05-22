package com.TemaLucene;


import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.TopDocs;
import org.apache.sis.xml.XLink;
import org.apache.tika.exception.TikaException;

import java.io.IOException;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws IOException, TikaException, ParseException {
        RomanianIndexer indexer = new RomanianIndexer("D:\\F Drive\\master\\an1\\sem2\\IR\\documents", "D:\\F Drive\\master\\an1\\sem2\\IR\\indexInfo");
        RomanianSearcher searcher = new RomanianSearcher("D:\\F Drive\\master\\an1\\sem2\\IR\\indexInfo");

        int option = -1;
        Scanner scanner = new Scanner(System.in);

        while(option != 0)
        {
            ShowMenu();
            option = scanner.nextInt();
            scanner.nextLine();
            switch (option)
            {
                case 1:
                    indexer.DoIndexing();
                    break;
                case 2:
                    searcher.Load();
                    break;
                case 3:
                    System.out.println("Type your query: ");
                    String query = scanner.nextLine();

                    TopDocs topDocsResult = searcher.DoQuery(query);
                    String results = searcher.InterpretTopDocs(topDocsResult, query);
                    if (results == "")
                    {
                        results = "No results found!";
                    }
                    System.out.println(results);
                    break;
                case 0:
                    break;
            }
        }
    }

    private static void ShowMenu()
    {
        System.out.println("MENU");
        System.out.println();
        System.out.println("1. Do Indexing");
        System.out.println("2. Load Index");
        System.out.println("3. Search");
        System.out.println();
        System.out.println("0. Exit");
    }
}


