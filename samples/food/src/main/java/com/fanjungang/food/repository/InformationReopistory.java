package com.fanjungang.food.repository;

import com.fanjungang.food.model.Information;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InformationReopistory extends JpaRepository<Information , Integer> {

    @Query("from Information i where i.id=:id")
    public Information getById(@Param("id") int id);
}
