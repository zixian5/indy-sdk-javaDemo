package com.fanjungang.food.repository;

import com.fanjungang.food.model.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsRepository  extends JpaRepository<News, Integer> {
}
