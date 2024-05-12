package de.dreierschach.searchadapter.scrollId;

import de.dreierschach.searchadapter.scollId.SearchWithScrollIdAdapter;
import de.dreierschach.searchadapter.scrollId.GeneralStoreRepository.Item;
import de.dreierschach.searchadapter.scrollId.GeneralStoreRepository.ScrollId;
import de.dreierschach.searchadapter.scrollId.GeneralStoreRepository.Search;

public class GeneralStoreScrollIdAdapter
        extends SearchWithScrollIdAdapter<Item, Search, ScrollId> {

    private final GeneralStoreRepository repository;

    public GeneralStoreScrollIdAdapter(GeneralStoreRepository repository) {
        this.repository = repository;
    }

    @Override
    protected IterativeSearchResult<Item, ScrollId> find(IterativeSearch<Search, ScrollId> search) {
        var result = repository.search(search.search(),
                search.scrollId(), search.pageSize());
        return new IterativeSearchResult<>(result.items(), result.scrollId(), result.pageSize());
    }
}
