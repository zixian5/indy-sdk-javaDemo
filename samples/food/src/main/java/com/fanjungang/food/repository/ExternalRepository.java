package com.fanjungang.food.repository;

import com.fanjungang.food.model.External;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExternalRepository extends JpaRepository<External, Integer> {

    @Query("from External e where e.id=:id")
    External findById(@Param("id") int id);
}
