package com.parking.common.result;

import java.util.List;

public class PageResult<T> {
    private long total;
    private long page;
    private long size;
    private List<T> records;

    private PageResult() {}

    public static <T> PageResult<T> of(long total, long page, long size, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.total = total;
        r.page = page;
        r.size = size;
        r.records = records;
        return r;
    }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }
}
