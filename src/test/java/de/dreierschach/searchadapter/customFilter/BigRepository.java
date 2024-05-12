package de.dreierschach.searchadapter.customFilter;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BigRepository {
    private final Set<Item> items = new TreeSet<>();

    // count requests for statistics
    private int requestCount = 0;

    public int getRequestCount() {
        return requestCount;
    }

    public void clearRequestCount() {
        this.requestCount = 0;
    }

    public SearchResult search(Search search, long page, long pageSize) {
        requestCount++;
        var result = items.stream().skip(page * pageSize).limit(pageSize).toList();
        return new SearchResult(result, items.size(), page, pageSize);
    }

    public void add(Item item) {
        this.items.add(item);
    }

    // ------- types

    public record Search() {
    }

    public record Item(String id, String name, LocalDate dateOfBirth) implements Comparable<Item> {
        @Override
        public int compareTo(Item o) {
            return StringUtils.compare(this.id(), o.id());
        }

        @Override
        public boolean equals(Object obj) {
            if (this.id() == null && obj == null) {
                return false;
            }
            return StringUtils.equals(this.id(), String.valueOf(obj));
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    public record SearchResult(List<Item> items, long totalSize, long page, long pageSize) {
        public String toString() {
            return "GeneralStore:" +
                    "-- Page:      " + page() + "\n" +
                    "-- PageSize:  " + pageSize() + "\n" +
                    "-- TotalSize: " + totalSize() + "\n" +
                    items().stream().map(Object::toString).map(s -> "-- " + s + "\n").collect(Collectors.joining());
        }
    }
}
