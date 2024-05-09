package de.dreierschach.searchadapter.customFilter;

import com.github.javafaker.Faker;
import de.dreierschach.searchadapter.customFilter.BigRepository.Item;
import de.dreierschach.searchadapter.customFilter.BigRepository.Search;
import de.dreierschach.searchadapter.customFilter.BigRepositoryFilterAdapter.CustomFilter;
import de.dreierschach.searchadapter.customFilter.SearchWithFilterAdapter.PagedSearchWithFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Random;
import java.util.stream.Stream;

class CustomFilterCacheTest {
    private static final Logger log = LoggerFactory.getLogger(CustomFilterCacheTest.class);

    private final SearchWithFilterAdapter<Item, Search, CustomFilter> adapter;
    private final BigRepository repository;

    public static final int REPOSITORY_SIZE = 200000;

    public CustomFilterCacheTest() {
        repository = new BigRepository();
        var faker = Faker.instance();
        log.info("init repository");
        for (int i = 0; i < REPOSITORY_SIZE; i++) {
            repository.add(new Item("id_" + i, faker.name().fullName(), LocalDate.ofEpochDay((int) (Math.random() * Integer.MAX_VALUE))));
        }
        adapter = new BigRepositoryFilterAdapter(repository);
    }

    private static Stream<Arguments> testDataProvider() {
        return Stream.of(
                Arguments.of(10, 500, 25, 1000),
                Arguments.of(20, 500, 25, 1000),
                Arguments.of(50, 500, 25, 1000),
                Arguments.of(10, 1000, 25, 1000),
                Arguments.of(20, 1000, 25, 1000),
                Arguments.of(50, 1000, 25, 1000),
                Arguments.of(10, 2000, 25, 1000),
                Arguments.of(20, 2000, 25, 1000),
                Arguments.of(50, 2000, 25, 1000)
        );
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    void test(long inputCacheSize, long indexCacheSize, long pageSize, int iterations) {
        var random = new Random(12345);
        adapter.enableCache(inputCacheSize, indexCacheSize);
        int maxPages = (int) (REPOSITORY_SIZE / pageSize / 2);
        long totalRequestCount = 0;
        for (int i = 1; i <= iterations; i++) {
            var page = random.nextInt(maxPages);
            repository.clearRequestCount();
            adapter.findAndFilter(new PagedSearchWithFilter<>(new Search(), new CustomFilter(null, LocalDate.of(2000, 1,1)), page, pageSize));
            totalRequestCount += repository.getRequestCount();
        }
        log.info("==> Repository ({} Entries), Caches (input = {}, index = {}), iterations: {}, average requests: {}", REPOSITORY_SIZE, inputCacheSize, indexCacheSize, iterations, totalRequestCount / (double) iterations);
    }
}
