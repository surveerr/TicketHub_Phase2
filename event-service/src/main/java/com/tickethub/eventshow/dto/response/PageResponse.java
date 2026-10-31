package com.tickethub.eventshow.dto.response;

import lombok.*;

import java.util.*;

@Getter
@Setter
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public PageResponse() {
    }

    public PageResponse(org.springframework.data.domain.Page<T> p) {
        content = p.getContent();
        page = p.getNumber();
        size = p.getSize();
        totalElements = p.getTotalElements();
        totalPages = p.getTotalPages();
        first = p.isFirst();
        last = p.isLast();
    }
}
