package org.example;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

public class Parser {
    private CloseableHttpAsyncClient client = HttpAsyncClients.createDefault();
    private ConcurrentHashMap<String,Page> pages = new ConcurrentHashMap<>();

    public void crawlUrls(Iterable<String> urls) throws IOException, InterruptedException, ExecutionException {
        client.start();
        crawl(urls);
        client.close();
    }
    private Integer crawl(Iterable<String> Urls) {

        Map<String, Future<HttpResponse>> futures = new HashMap<>();
        for (var url : Urls) {
            if (!pages.containsKey(url)) {
                HttpGet request = new HttpGet(url);
                futures.put(url, client.execute(request, null));
            }
        }
        List<CompletableFuture<Integer>> subUrls = new ArrayList<CompletableFuture<Integer>>();
        while (futures.size() > 0) {
            for (var kvp : futures.entrySet()) {
                try {
                    var response = kvp.getValue().get(1000, TimeUnit.MILLISECONDS);

                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity, "UTF-8");

                    Page p = new Page(kvp.getKey(), responseString);
                    if(pages.putIfAbsent(kvp.getKey(),p)==null) {
                        var urls = getUrls(p);
                        System.out.println(pages.size());
                        subUrls.add(CompletableFuture.supplyAsync(() -> crawl(urls)));
                    }
                    futures.remove(kvp.getKey());
                    break;
                } catch (Exception e) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }
            }
        }
        CompletableFuture[] cfs = subUrls.toArray(new CompletableFuture[futures.size()]);
        CompletableFuture.allOf(cfs).join();
        return 0;
    }
    private Iterable<String> getUrls(Page p)
    {
        HashSet<String> allMatches = new HashSet<String>();
        Matcher m = Pattern.compile("href=\\\"/(.*?)\\\"")
                .matcher(p.getContent());
        while (m.find()) {
            var href = m.group();
            href = "http://tengrinews.kz"+href.substring(6,href.length()-1);
            allMatches.add(href);
        }
        return allMatches;
    }
}
