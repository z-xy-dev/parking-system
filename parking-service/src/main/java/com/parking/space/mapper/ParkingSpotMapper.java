package com.parking.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.parking.space.entity.ParkingSpot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ParkingSpotMapper extends BaseMapper<ParkingSpot> {

    @Update("UPDATE parking_spot SET status = 'OCCUPIED', version = version + 1 " +
            "WHERE space_id = #{spaceId} AND spot_number = #{spotNumber} AND status = 'AVAILABLE'")
    int atomicReserveSpot(@Param("spaceId") Long spaceId, @Param("spotNumber") Integer spotNumber);

    @Update("UPDATE parking_spot SET status = 'AVAILABLE', version = version + 1 " +
            "WHERE space_id = #{spaceId} AND spot_number = #{spotNumber} AND status = 'OCCUPIED'")
    int atomicReleaseSpot(@Param("spaceId") Long spaceId, @Param("spotNumber") Integer spotNumber);
}
