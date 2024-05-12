package de.dreierschach.searchadapter.scrollId;

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

    public SearchResult search(Search search, ScrollId scrollId, long pageSize) {
        requestCount++;
        var page = scrollId != null ? scrollId.id() / pageSize : 0;
        var result = items.stream()
                .filter(item -> StringUtils.isEmpty(search.name()) || item.name().contains(search.name()))
                .sorted(search.sort().comparator)
                .skip(page * pageSize).limit(pageSize).toList();
        return new SearchResult(result, items.size(), new ScrollId(search.name() + search.sort(), (page + 1) * pageSize), pageSize);
    }

    // count requests for statistics
    private int requestCount = 0;

    public int getRequestCount() {
        return requestCount;
    }

    // ------- types

    public record ScrollId(String key, long id) {
        @Override
        public String toString() {
            return "%s%Hd".formatted(key, id);
        }
    }

    public static void main(String[] args) {
        System.out.println(new ScrollId("jolla", 12345));
    }

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

    public record SearchResult(List<Item> items, long totalSize, ScrollId scrollId, long pageSize) {
        public String toString() {
            return "GeneralStore:" +
                    "-- scrollId:  " + scrollId() + "\n" +
                    "-- PageSize:  " + pageSize() + "\n" +
                    "-- TotalSize: " + totalSize() + "\n" +
                    items().stream().map(Object::toString).map(s -> "-- " + s + "\n").collect(Collectors.joining());
        }
    }
}
