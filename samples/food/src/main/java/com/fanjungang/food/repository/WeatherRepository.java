package com.fanjungang.food.repository;

import com.fanjungang.food.model.Weather;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WeatherRepository extends JpaRepository<Weather,Integer> {

    @Query("from Weather  w where w.id=:id")
    public Weather getById(@Param("id") int id);
}
