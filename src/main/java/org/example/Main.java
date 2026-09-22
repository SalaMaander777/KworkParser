package org.example;

import org.example.parser.KworkConfig;
import org.example.parser.KworkParsingException;
import org.example.parser.KworkResponseMapper;
import org.example.parser.fetcher.JsoupKworkHtmlFetcher;
import org.example.parser.fetcher.KworkHtmlFetcher;


public class Main {
    public static KworkConfig kworkConfig = KworkConfig.defaultConfig();
    public static KworkHtmlFetcher fetcher = new JsoupKworkHtmlFetcher(kworkConfig);
    public static KworkResponseMapper mapper = new KworkResponseMapper();
    public static KworkService service = new KworkService(fetcher, mapper, kworkConfig);
    static void main(String[] args) throws KworkParsingException {

        service.fetchAllKworks();


    }
}
