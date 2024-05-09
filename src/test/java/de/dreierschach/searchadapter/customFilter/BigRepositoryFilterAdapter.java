package de.dreierschach.searchadapter.customFilter;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;

public class BigRepositoryFilterAdapter extends SearchWithFilterAdapter<BigRepository.Item, BigRepository.Search, BigRepositoryFilterAdapter.CustomFilter> {
    private final BigRepository bigRepository;

    public BigRepositoryFilterAdapter(BigRepository bigRepository) {
        this.bigRepository = bigRepository;
    }

    @Override
    protected PagedSearchResult<BigRepository.Item> find(PagedSearch<BigRepository.Search> search) {
        var result = bigRepository.search(search.search(), search.page(), search.pageSize());
        return new PagedSearchResult<>(result.items(), search.page(), search.pageSize());
    }

    @Override
    protected boolean test(BigRepository.Item item, BigRepositoryFilterAdapter.CustomFilter customFilter) {
        return (StringUtils.isEmpty(customFilter.name()) || item.name().contains(customFilter.name())) &&
                (customFilter.bornBefore() == null || customFilter.bornBefore().isBefore(item.dateOfBirth().plusDays(1)));
    }

    // -------- types

    public record CustomFilter(String name, LocalDate bornBefore) {
    }
}
