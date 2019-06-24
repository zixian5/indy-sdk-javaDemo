package com.fanjungang.food.repository;

import com.fanjungang.food.model.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FoodRepository extends JpaRepository<Food , Integer> {

    @Query("select f.type from Food f")
    List<String> getAllTypes();

    @Query("from Food f where f.type=:type")
    List<Food> getByType(@Param("type") String type);

    @Query("from Food f where f.id=:id")
    Food getById(@Param("id")int id);
}
