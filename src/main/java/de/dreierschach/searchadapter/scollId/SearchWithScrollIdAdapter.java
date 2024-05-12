package de.dreierschach.searchadapter.scollId;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;
import java.util.stream.Collectors;

import static de.dreierschach.searchadapter.scollId.SearchWithScrollIdAdapter.OutputPageAndIndex.first;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Adapts a repository, that supports iterative instead of paged searching
 * <p>
 * It is strongly recommended to enable caching to minimize the amount of requests to the underlying repository.
 *
 * @param <T> the entity type
 * @param <U> the search type
 * @param <S> the scroll-id type
 */
abstract public class SearchWithScrollIdAdapter<T, U, S> {
    private Cache<IterativeSearch<U, S>, IterativeSearchResult<T, S>> inputCache;
    private Cache<PagedSearch<U>, S> scrollIdCache;
    private boolean cacheEnabled = false;

    // -------- abstract methods

    /**
     * Delegate a search-request to the underlying repository and map the result
     *
     * @param search the search-request
     * @return the search-result
     */
    abstract protected IterativeSearchResult<T, S> find(IterativeSearch<U, S> search);

    // -------- public methods

    /**
     * initialize caches for input-pages and scroll-ids
     *
     * @param inputCacheSize    the size of the cache for requests to the underlying repository
     * @param scrollIdCacheSize the size of the cache for scroll-ids
     */
    public void enableCache(long inputCacheSize, long scrollIdCacheSize) {
        inputCache = Caffeine.newBuilder()
                .expireAfterWrite(5, MINUTES)
                .maximumSize(inputCacheSize)
                .build();
        scrollIdCache = Caffeine.newBuilder()
                .expireAfterWrite(5, MINUTES)
                .maximumSize(scrollIdCacheSize)
                .build();
        cacheEnabled = true;
    }

    /**
     * find items by a given search-request, page, page-size and a custom filter
     *
     * @param pagedSearch an extended search-request that contains an extra filter not supported by the underlying repository
     * @return a result-page
     */
    public PagedSearchResult<T> findAndFilter(PagedSearch<U> pagedSearch) {
        // find position to start reading data for the output-page
        var optionalIndex = cachedFindScrollId(pagedSearch);
        if (optionalIndex.notPresent()) {
            return new PagedSearchResult<>(List.of(), pagedSearch.page(), pagedSearch.pageSize());
        }

        // read input page
        var searchResult = cachedFind(new IterativeSearch<>(pagedSearch.search(), optionalIndex.scrollId(), pagedSearch.pageSize()));

        // return read items, limited to the page-size
        return new PagedSearchResult<>(searchResult.items(), pagedSearch.page(), pagedSearch.pageSize());
    }

    // -------- public types

    /**
     * A search-request with scrollId to delegate to a search-method of the underlying repository
     *
     * @param search   the search-request
     * @param scrollId the scroll-id of the requested page
     * @param pageSize the requested page size
     * @param <U>      the search-request-type
     * @param <S>      the scroll-id type
     */
    public record IterativeSearch<U, S>(U search, S scrollId, long pageSize) {
    }

    /**
     * The result of a search delegated to a search-method of the underlying repository
     *
     * @param items    a list of items
     * @param scrollId the scrollId of the next page
     * @param pageSize the page-size
     * @param <T>      the items type
     * @param <S>      the scroll-id type
     */
    public record IterativeSearchResult<T, S>(List<T> items, S scrollId, long pageSize) {
        public String toString() {
            return "IterativeSearchResult:" +
                    "-- ScrollId:  " + scrollId() + "\n" +
                    "-- PageSize:  " + pageSize() + "\n" +
                    items().stream().map(Object::toString).map(s -> "-- " + s + "\n").collect(Collectors.joining());
        }
    }

    /**
     * A search-request with pagination
     *
     * @param search   the search-request
     * @param page     the requested pagenumber
     * @param pageSize the requested page size
     * @param <U>      the search-request type
     */
    public record PagedSearch<U>(U search, long page, long pageSize) {
    }

    /**
     * The result of a paged search
     *
     * @param items    a list of items
     * @param page     the page-number
     * @param pageSize the page-size
     * @param <T>      the items type
     */
    public record PagedSearchResult<T>(List<T> items, long page, long pageSize) {
        public String toString() {
            return "PagedSearchResult:" +
                    "-- Page:      " + page() + "\n" +
                    "-- PageSize:  " + pageSize() + "\n" +
                    items().stream().map(Object::toString).map(s -> "-- " + s + "\n").collect(Collectors.joining());
        }
    }

    // -------- private methods

    // use cache for input pages
    private IterativeSearchResult<T, S> cachedFind(IterativeSearch<U, S> iterativeSearch) {
        if (cacheEnabled) {
            return inputCache.get(iterativeSearch, this::find);
        }
        return find(iterativeSearch);
    }

    private record OptionalScrollId<S>(S scrollId, Boolean present) {
        public static <S> OptionalScrollId<S> of(S scrollId) {
            return new OptionalScrollId<>(scrollId, true);
        }

        public static <S> OptionalScrollId<S> empty() {
            return new OptionalScrollId<>(null, false);
        }

        public boolean notPresent() {
            return !present();
        }
    }

    // use cache for scroll-ids
    private OptionalScrollId<S> cachedFindScrollId(PagedSearch<U> search) {
        if (!cacheEnabled) {
            return findIndex(search, new OutputPageAndIndex<>(0L, null));
        }
        // When iterating the input-pages, all found scroll-ids will be cached.
        // To do this, the cache-method get(search, Function<search, scrollId>) cannot be used,
        // because it is not allowed to add cache values within the lambda-function.
        var result = scrollIdCache.getIfPresent(search);
        if (result != null) {
            return OptionalScrollId.of(result);
        }
        // beginn the iteration at the last cached scroll-id prior to the requested
        return findIndex(search, findLastCachedIndex(search));
    }

    // A record for internal use that holds the last cached output-page and scroll-id previous to the requested output-page.
    record OutputPageAndIndex<S>(Long page, S scrollId) {
        public static <S> OutputPageAndIndex<S> first() {
            return new OutputPageAndIndex<>(0L, null);
        }
    }

    // find the last scrollId in cache beginning from the requested output-page -1 down to 0
    OutputPageAndIndex<S> findLastCachedIndex(PagedSearch<U> search) {
        // the scrollId for ouput-page 0 is always (0, 0)
        if (search.page() == 0) {
            return first();
        }
        // find last cached scrollId
        var i = search.page();
        PagedSearch<U> key;
        do {
            i--;
            key = new PagedSearch<>(search.search(), i, search.pageSize());
        } while (i > 0 && !scrollIdCache.asMap().containsKey(key));
        // nothing found? start by 0 / (0, 0)
        if (i == 0) {
            return first();
        }
        // start searching there
        return new OutputPageAndIndex<>(i, scrollIdCache.getIfPresent(key));
    }

    // find the scrollId (input-page, input-item-scrollId) for a requested output-page, beginning at a known output-page
    // and scrollId
    private OptionalScrollId<S> findIndex(PagedSearch<U> search, OutputPageAndIndex<S> start) {
        // counts up to the requested page
        long outputPage = start.page();
        // the scroll-id of the current input-page
        var scrollId = start.scrollId();

        while (outputPage < search.page()) {
            // read next data from cache or repository
            var result = cachedFind(new IterativeSearch<>(search.search(), scrollId, search.pageSize()));

            // no more items found? Then there is no date for the requested page
            if (result.items().isEmpty() || result.scrollId() == null) {
                return OptionalScrollId.empty();
            }

            outputPage++;
            scrollId = result.scrollId();

            // add all found scroll-ids to cache
            if (cacheEnabled) {
                scrollIdCache.put(new PagedSearch<>(search.search(), outputPage, search.pageSize()), scrollId);
            }
        }

        // the requested output-page is reached, return the current calculated input-scrollId
        return OptionalScrollId.of(scrollId);
    }
}
