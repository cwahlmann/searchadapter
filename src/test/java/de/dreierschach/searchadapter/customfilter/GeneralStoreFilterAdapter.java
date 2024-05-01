package de.dreierschach.searchadapter.customfilter;

import de.dreierschach.searchadapter.customFilter.SearchWithFilterAdapter;
import de.dreierschach.searchadapter.customfilter.GeneralStoreRepository.Item;
import de.dreierschach.searchadapter.customfilter.GeneralStoreRepository.Search;

public class GeneralStoreFilterAdapter
        extends SearchWithFilterAdapter<Item, Search, GeneralStoreFilterAdapter.CustomFilter> {

    private final GeneralStoreRepository repository;

    public GeneralStoreFilterAdapter(GeneralStoreRepository repository) {
        this.repository = repository;
    }

    @Override
    public PagedSearchResult<Item> find(PagedSearch<Search> search) {
        var result = repository.search(search.search(), search.page(), search.pageSize());
        return new PagedSearchResult<>(result.items(), result.page(), result.pageSize());
    }

    @Override
    public boolean filter(Item item, CustomFilter customFilter) {
        return customFilter.eatable() == null || item.eatable() == customFilter.eatable();
    }

    // -------- types

    public record CustomFilter(Boolean eatable) {
    }
}
