package com.parking.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.parking.order.entity.BookingOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<BookingOrder> {
}
