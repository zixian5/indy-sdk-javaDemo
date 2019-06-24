package com.fanjungang.food.repository;

import com.fanjungang.food.model.Internal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InternalRepository extends JpaRepository<Internal, Integer> {

    @Query("from Internal i where i.id=:id")
    public Internal getById(@Param("id") int id);
}
