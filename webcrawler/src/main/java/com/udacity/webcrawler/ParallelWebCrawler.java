package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final List<Pattern> ignoredUrls;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final int maxDepth; // added to fetch maxdepth from config json, influence from @SequentialWebCrawler.java
  private final PageParserFactory pageParserFactory;

  @Inject
  ParallelWebCrawler(
      Clock clock,
      @Timeout Duration timeout,
      @MaxDepth int maxDepth,
      @IgnoredUrls List<Pattern> ignoredUrls,
      @PopularWordCount int popularWordCount,
      @TargetParallelism int threadCount,
      PageParserFactory pageParserFactory) {
    this.clock = clock;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.maxDepth = maxDepth;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.pageParserFactory = pageParserFactory;
    this.ignoredUrls = ignoredUrls;

  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    // Implementation for this method is copied from SequentialWebCrawler.java file of the boilerplate code provided by udacity.
    // I have updated the HashMap and HashSet to ConcurrentHashMap and ConcurrentSkipListSet respectively.

    Instant deadline = clock.instant().plus(timeout);
    ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();
    ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();
    for (String url : startingUrls) {
//      crawlInternal(url, deadline, maxDepth, counts, visitedUrls);
      pool.invoke(new ParallelCrawlInternalTask(url, deadline, maxDepth, counts, visitedUrls));
    }

    if (counts.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(counts)
              .setUrlsVisited(visitedUrls.size())
              .build();
    }

    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(counts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }

  public class ParallelCrawlInternalTask extends RecursiveTask<Boolean> {
    // This extends RecursiveTask to immitate the recursive nature of `crawlInternal` method in SequentialWebCrawler.
    // As was advised in the Project Guidelines Page - 6: Parallel Crawler to implement either RecursiveAction or RecursiveTask subclass.
    private String url;
    private Instant deadline;
    private int maxDepth;
    private ConcurrentMap<String, Integer> counts;
    private ConcurrentSkipListSet<String> visitedUrls;
    public ParallelCrawlInternalTask(String url, Instant deadline, int maxDepth, ConcurrentMap<String, Integer> counts, ConcurrentSkipListSet<String> visitedUrls){
      this.url = url;
      this.deadline = deadline;
      this.maxDepth = maxDepth;
      this.counts = counts;
      this.visitedUrls = visitedUrls;
    }

    @Override
    protected Boolean compute() {
      // For RecursiveTask I have to override the compute method,
      // The logic for this compute method is influenced from SequentialWebCrawler.java, method: crawlInternal.
      if(maxDepth == 0 || clock.instant().isAfter(deadline)){
        return false;
      }
      for(Pattern pattern: ignoredUrls){
        if(pattern.matcher(url).matches()){ return false;}
      }
      if(visitedUrls.contains(url)){return false;}
      visitedUrls.add(url);
      PageParser.Result result = pageParserFactory.get(url).parse();
      for(ConcurrentMap.Entry<String, Integer> e: result.getWordCounts().entrySet()){
        counts.compute(e.getKey(), (k, v) -> (v==null) ? e.getValue(): e.getValue() + v);
      }
      List<ParallelCrawlInternalTask> subtasks = new ArrayList<>();
      //this bit is change a bit from the crawlInternal method as we need to invoke a subtask to sun parallel tasks
      // instead of recursive calls in crawlInternal method as advised in the project guidelines Page 6: Parallel Crawler.
      for(String link: result.getLinks()) {
        subtasks.add(new ParallelCrawlInternalTask(link, deadline, maxDepth - 1, counts, visitedUrls));
      }
      invokeAll(subtasks);
      return true;
    }}
}
