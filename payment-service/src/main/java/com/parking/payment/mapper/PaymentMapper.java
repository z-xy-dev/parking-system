package com.parking.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.parking.payment.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMapper extends BaseMapper<PaymentRecord> {
}
