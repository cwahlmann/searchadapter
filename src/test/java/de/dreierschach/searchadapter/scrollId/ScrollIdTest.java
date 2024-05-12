package de.dreierschach.searchadapter.scrollId;

import de.dreierschach.searchadapter.scollId.SearchWithScrollIdAdapter;
import de.dreierschach.searchadapter.scollId.SearchWithScrollIdAdapter.PagedSearch;
import de.dreierschach.searchadapter.scrollId.GeneralStoreRepository.Item;
import de.dreierschach.searchadapter.scrollId.GeneralStoreRepository.ScrollId;
import de.dreierschach.searchadapter.scrollId.GeneralStoreRepository.Search;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScrollIdTest {
    private static final Logger log = LoggerFactory.getLogger(ScrollIdTest.class);

    public static final Item APPLES = new Item("Apples", true);
    public static final Item BANANAS = new Item("Bananas", true);
    public static final Item BREAD = new Item("Bread", true);
    public static final Item CHEESE = new Item("Cheese", true);
    public static final Item KIWIS = new Item("Kiwis", true);
    public static final Item PEANUTS = new Item("Peanuts", true);
    public static final Item SALAMI = new Item("Salami", true);
    public static final Item SOJA = new Item("Soja", true);
    public static final Item ZUCCHINI = new Item("Zucchini", true);
    private static final List<Item> TEST_ITEMS = List.of(
            APPLES,
            BANANAS,
            BREAD,
            CHEESE,
            KIWIS,
            PEANUTS,
            SALAMI,
            SOJA,
            ZUCCHINI
    );

    private GeneralStoreScrollIdAdapter adapter;
    private GeneralStoreRepository repository;

    @BeforeEach
    void init() {
        repository = new GeneralStoreRepository(TEST_ITEMS);
        adapter = new GeneralStoreScrollIdAdapter(repository);
    }

    @Test
    void testPage0() {
        adapter.enableCache(2, 5);
        var result = adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 0, 4));
        log.info(result.toString());
        log.info("==> requests:  {}", repository.getRequestCount());

        assertThat(result.page()).isEqualTo(0);
        assertThat(result.pageSize()).isEqualTo(4);
        assertThat(result.items()).containsExactly(APPLES, BANANAS, BREAD, CHEESE);
    }

    @Test
    void testPage1() {
        adapter.enableCache(2, 5);
        var result = adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 1, 4));
        log.info(result.toString());
        log.info("==> requests:  {}", repository.getRequestCount());

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(4);
        assertThat(result.items()).containsExactly(KIWIS, PEANUTS, SALAMI, SOJA);
    }


    @Test
    void testPage2() {
        adapter.enableCache(2, 5);
        var result = adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 2, 4));
        log.info(result.toString());
        log.info("==> requests:  {}", repository.getRequestCount());

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(4);
        assertThat(result.items()).containsExactly(ZUCCHINI);
    }

    @Test
    void testNoCache() {
        // adapter.enableCache(2, 2);
        adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 2, 4));
        adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 1, 4));
        adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 0, 4));
        adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 2, 4));
        log.info("==> requests without cache:  {}", repository.getRequestCount());
        assertThat(repository.getRequestCount()).isEqualTo(9);
    }

    @Test
    void testCache() {
        adapter.enableCache(2, 2);
        adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 2, 4));
        adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 1, 4));
        adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 0, 4));
        adapter.findAndFilter(new PagedSearch<>(new Search(null, Search.SortBy.NAME), 2, 4));
        log.info("==> requests without cache:  {}", repository.getRequestCount());
        assertThat(repository.getRequestCount()).isLessThanOrEqualTo(5);
    }
}
