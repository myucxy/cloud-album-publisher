package com.cloudalbum.publisher.common.model;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Getter;

import java.util.List;

@Getter
public class PageResult<T> {

    private final long total;
    private final long pages;
    private final int page;
    private final int size;
    private final List<T> list;

    private PageResult(long total, long pages, int page, int size, List<T> list) {
        this.total = total;
        this.pages = pages;
        this.page = page;
        this.size = size;
        this.list = list;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(
                page.getTotal(),
                page.getPages(),
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getRecords()
        );
    }

    public static <T> PageResult<T> of(long total, int page, int size, List<T> list) {
        long pages = size == 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(total, pages, page, size, list);
    }
}
