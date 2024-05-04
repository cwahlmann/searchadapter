package de.dreierschach.searchadapter.customFilter;

import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

public class GeneralStoreRepository {
    private final List<Item> items;

    public GeneralStoreRepository(List<Item> items) {
        this.items = items;
    }

    public SearchResult<Item> search(Search search, long page, long pageSize) {
        requestCount++;
        var result = items.stream()
                .filter(item -> StringUtils.isEmpty(search.name()) || item.name().contains(search.name()))
                .sorted(search.sort().comparator)
                .skip(page * pageSize).limit(pageSize).toList();
        return new SearchResult<>(result, items.size(), page, pageSize);
    }

    // count requests for statistics
    private int requestCount = 0;

    public int getRequestCount() {
        return requestCount;
    }

    // ------- types

    public record Search(String name, SortBy sort) {
        public enum SortBy {
            NAME(comparing(Item::name)),
            EATABLE(comparing(Item::eatable));
            private final Comparator<Item> comparator;

            SortBy(Comparator<Item> comparator) {
                this.comparator = comparator;
            }
        }
    }

    public record Item(String name, boolean eatable) {
    }

    public record SearchResult<T>(List<T> items, long totalSize, long page, long pageSize) {
        public String toString() {
            return "GeneralStore:" +
                    "-- Page:      " + page() + "\n" +
                    "-- PageSize:  " + pageSize() + "\n" +
                    "-- TotalSize: " + totalSize() + "\n" +
                    items().stream().map(Object::toString).map(s -> "-- "+s+"\n").collect(Collectors.joining());
        }
    }
}
