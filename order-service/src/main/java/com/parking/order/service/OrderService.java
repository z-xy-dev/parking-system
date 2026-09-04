package com.parking.order.service;

import com.parking.order.entity.BookingOrder;
import java.util.List;
import java.util.Map;

public interface OrderService {
    BookingOrder create(BookingOrder order);
    void cancel(Long orderId, Long userId);
    void complete(Long orderId, Long userId);
    void delete(Long orderId, Long userId);
    BookingOrder detail(Long orderId);
    List<BookingOrder> myOrders(Long userId);
    List<BookingOrder> spaceOrders(Long ownerId);

    /**
     * 结算预览：订单完成时应付多少钱（基础费 + 超时费），用于前端确认前展示。
     */
    Map<String, Object> previewSettle(Long orderId, Long userId);
}
