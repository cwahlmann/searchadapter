package de.dreierschach.searchadapter.customFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static de.dreierschach.searchadapter.customFilter.SearchWithFilterAdapter.OutputPageAndIndex.FIRST;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Adapts a repository, that supports paged searching, but lacks some needed filter functions.
 * <p>
 * It is strongly recommended to enable caching to minimize the amount of requests to the underlying repository.
 *
 * @param <T> the entity type
 * @param <U> the search type
 * @param <V> the custom filter type
 */
abstract public class SearchWithFilterAdapter<T, U, V> {
    private Cache<PagedSearch<U>, PagedSearchResult<T>> inputCache;
    private Cache<PagedSearchWithFilter<U, V>, Index> indexCache;
    private boolean cacheEnabled = false;

    // -------- abstract methods

    /**
     * Delegate a search-request to the underlying repository and map the result
     *
     * @param search the search-request
     * @return the search-result
     */
    abstract protected PagedSearchResult<T> find(PagedSearch<U> search);

    /**
     * tests is an item matches the custom filter
     *
     * @param item         the item to test
     * @param customFilter the custom filter
     * @return true, if the item matches the filter
     */
    abstract protected boolean test(T item, V customFilter);

    // -------- public methods

    /**
     * initialize caches for input-pages and indexes
     *
     * @param inputCacheSize a cache for requests to the underlying repository
     * @param indexCacheSize a cache for information, which pages to request when filling a result-page - this cache may be
     *                       quite big, because it stores only two long values
     */
    public void enableCache(long inputCacheSize, long indexCacheSize) {
        inputCache = Caffeine.newBuilder()
                .expireAfterWrite(5, MINUTES)
                .maximumSize(inputCacheSize)
                .build();
        indexCache = Caffeine.newBuilder()
                .expireAfterWrite(5, MINUTES)
                .maximumSize(indexCacheSize)
                .build();
        cacheEnabled = true;
    }

    /**
     * find items by a given search-request, page, page-size and a custom filter
     *
     * @param pagedSearchWithFilter an extended search-request that contains an extra filter not supported by the underlying repository
     * @return a result-page
     */
    public PagedSearchResult<T> findAndFilter(PagedSearchWithFilter<U, V> pagedSearchWithFilter) {
        // find position to start reading data for the output-page
        var index = cachedFindIndex(pagedSearchWithFilter);
        if (index == Index.NONE) {
            return new PagedSearchResult<>(List.of(), pagedSearchWithFilter.page(), pagedSearchWithFilter.pageSize());
        }

        var itemsResult = new ArrayList<T>();
        // read data as long as is needed and as there is any
        while (itemsResult.size() < pagedSearchWithFilter.pageSize()) {
            // read input page
            var searchResult = cachedFind(new PagedSearch<>(pagedSearchWithFilter.search(), index.page(), pagedSearchWithFilter.pageSize()));

            // stop when there is no result
            if (searchResult.items().isEmpty()) {
                return new PagedSearchResult<>(itemsResult, pagedSearchWithFilter.page(), pagedSearchWithFilter.pageSize());
            }

            // add all filtered items
            itemsResult.addAll(searchResult.items().stream().skip(index.item()).filter(item -> test(item, pagedSearchWithFilter.customFilter())).toList());

            // continue reading at the start of the next input-page
            index = new Index(index.page() + 1, 0);
        }
        // return read items, limited to the page-size
        return new PagedSearchResult<>(itemsResult.stream().limit(pagedSearchWithFilter.pageSize()).toList(), pagedSearchWithFilter.page(), pagedSearchWithFilter.pageSize());
    }

    // -------- public types

    /**
     * A search-request with pagination to delegate to a search-method of the underlying repository
     *
     * @param search   the search-request
     * @param page     the requested pagenumber
     * @param pageSize the requested page size
     * @param <U>      the search-request-type
     */
    public record PagedSearch<U>(U search, long page, long pageSize) {
    }

    /**
     * An extended search-request with pagination and a custom filter
     *
     * @param search       the search-request
     * @param page         the requested pagenumber
     * @param pageSize     the requested page size
     * @param customFilter the custom filter
     * @param <U>          the search-request type
     * @param <V>          the custom filter type
     */
    public record PagedSearchWithFilter<U, V>(U search, V customFilter, long page, long pageSize) {
    }

    /**
     * The result of a search
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

    // -------- private types

    /**
     * an index points to the input-data that needs to be read next to fill a requested output page
     *
     * @param page the input page-number
     * @param item the input item-index (not filtered!)
     */
    record Index(long page, long item) {
        public static final Index NONE = new Index(-1, -1);
    }

    // -------- private methods

    // use cache for input pages
    private PagedSearchResult<T> cachedFind(PagedSearch<U> pagedSearch) {
        if (cacheEnabled) {
            return inputCache.get(pagedSearch, this::find);
        }
        return find(pagedSearch);
    }

    // use cache for indexes
    private Index cachedFindIndex(PagedSearchWithFilter<U, V> search) {
        if (!cacheEnabled) {
            return this.findIndex(search, FIRST);
        }
        // When iterating the input-pages, all found indexes will be cached.
        // To do this, the cache-method get(search, Function<search, index>) cannot be used,
        // because it is not allowed to add cache values within the lambda-function.
        var result = indexCache.getIfPresent(search);
        if (result != null) {
            return result;
        }
        // beginn the iteration at the last cached index prior to the requested
        return findIndex(search, findLastCachedIndex(search));
    }

    // A record for internal use that holds the last cached output-page and index previous to the requested output-page.
    record OutputPageAndIndex(Long page, Index index) {
        public static final OutputPageAndIndex FIRST = new OutputPageAndIndex(0L, new Index(0, 0));
    }

    // find the last index in cache beginning from the requested output-page -1 down to 0
    OutputPageAndIndex findLastCachedIndex(PagedSearchWithFilter<U, V> search) {
        // the index for ouput-page 0 is always (0, 0)
        if (search.page() == 0) {
            return FIRST;
        }
        // find last cached index
        var i = search.page();
        PagedSearchWithFilter<U, V> key;
        do {
            i--;
            key = new PagedSearchWithFilter<>(search.search(), search.customFilter(), i, search.pageSize());
        } while (i > 0 && !indexCache.asMap().containsKey(key));
        // nothing found? start by 0 / (0, 0)
        if (i == 0) {
            return FIRST;
        }
        // start searching there
        return new OutputPageAndIndex(i, indexCache.getIfPresent(key));
    }

    // find the index (input-page, input-item-index) for a requested output-page, beginning at a known output-page
    // and index
    private Index findIndex(PagedSearchWithFilter<U, V> search, OutputPageAndIndex start) {
        // counts up to the requested page
        long outputPage = start.page();
        var index = start.index();

        // holds the position in the output-page, where the next data would be stored
        long outputItemIndex = 0;

        // the current input-page
        long inputPage = 0;

        while (outputPage < search.page()) {
            // read next data from cache or repository
            var items = cachedFind(new PagedSearch<>(search.search(), inputPage, search.pageSize())).items();

            // no more items found? Then there is no date for the requested page
            if (items.isEmpty()) {
                return Index.NONE;
            }

            // map the data to indexes and filter them
            var inputIndexes = IntStream.iterate(0, i -> i < items.size(), i -> i + 1).filter(i -> test(items.get(i), search.customFilter()))
                    .boxed().toList();

            // update index in output-page
            outputItemIndex += inputIndexes.size();

            // is the current output page full?
            if (outputItemIndex > search.pageSize()) {
                // update output-page
                outputPage += outputItemIndex / search.pageSize();

                // update output-index
                outputItemIndex = outputItemIndex % search.pageSize();

                // recalculate the requested index
                index = new Index(inputPage, inputIndexes.get((int) (search.pageSize() - outputItemIndex - 1)));

                // add all found indexes to cache
                if (cacheEnabled) {
                    indexCache.put(new PagedSearchWithFilter<>(search.search(), search.customFilter(), outputPage, search.pageSize()), index);
                }
            }

            // load the next input page
            inputPage++;
        }

        // the requested output-page is reached, return the current calculated input-index
        return index;
    }
}
