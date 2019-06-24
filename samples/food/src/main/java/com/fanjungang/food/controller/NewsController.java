package com.fanjungang.food.controller;

import com.fanjungang.food.model.News;
import com.fanjungang.food.repository.NewsRepository;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class NewsController {
    @Autowired
    private NewsRepository newsRepository;

    @ResponseBody
    @RequestMapping("/addnews")
    public String addNews(String name , int amount , int year,
                            int month, int day ,String people, String telephone,
                            String address ,String message)
    {
        News news = new News();
        news.setAddress(name);
        news.setAmount(amount);
        news.setYear(year);
        news.setMonth(month);
        news.setDay(day);
        news.setTelephone(telephone);
        news.setAddress(address);
        news.setMessage(message);

        news = newsRepository.save(news);

        Map<String , Object> result = new HashMap<>();
        result.put("result",1);
        result.put("data",news);

        return new Gson().toJson(result).toString();
    }

    @ResponseBody
    @RequestMapping("/getnews")
    public String getNews()
    {
        return new Gson().toJson(newsRepository.findAll()).toString();
    }
}
