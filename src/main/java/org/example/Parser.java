package org.example;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;

import javax.xml.transform.stream.StreamSource;
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
    private ConcurrentHashMap<String, Page> pages = new ConcurrentHashMap<>();
    private ExecutorService executor = Executors.newFixedThreadPool(100);

    public void crawlUrls(Iterable<String> urls) throws IOException, InterruptedException, ExecutionException {
        client.start();
        crawl(urls);
        executor.shutdown();
        client.close();
    }

    private CompletableFuture<Void> processHttpResponse(String url, HttpResponse httpResponse)
    {
        if(httpResponse!=null) {
            try
            {
                HttpEntity entity = httpResponse.getEntity();
                String responseString = null;
                responseString = EntityUtils.toString(entity, "UTF-8");
                Page p = new Page(url, responseString);
                if (pages.putIfAbsent(url, p) == null) {
                    System.out.println("URL:{"+url+"} PROCESSED BY THREAD: "+Thread.currentThread().getId());
                    System.out.println("CURRENT DICTIONARY SIZE: "+pages.size());
                    return CompletableFuture.supplyAsync(() -> crawl(getUrls(p)), executor);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return CompletableFuture.completedFuture(null);
    }
    private Void crawl(Iterable<String> Urls) {
        var result = StreamSupport.stream(Urls.spliterator(),false)
                      .map(url->new Object [] {url, client.execute(new HttpGet(url), null)})
                      .map(tuple->CompletableFuture.supplyAsync(()-> {
                          try {
                              HttpResponse response = ((Future<HttpResponse>)tuple[1]).get();
                              return new Object[] {tuple[0], response};
                          } catch( Exception e) { return new Object []{tuple[0], null}; }
                          }, executor))
                      .map(tupleCompletableFuture->tupleCompletableFuture.thenCompose((tuple)->processHttpResponse((String)tuple[0],(HttpResponse)tuple[1])))
                      .toArray(size-> new CompletableFuture[size]);
        CompletableFuture.allOf(result).join();
        return null;
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
