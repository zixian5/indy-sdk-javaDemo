package com.fanjungang.food.controller;

import com.fanjungang.food.model.Food;
import com.fanjungang.food.repository.FoodRepository;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class FoodController {
    @Autowired
    private FoodRepository foodRepository;

    @ResponseBody
    @RequestMapping("/getAllTypes")
    public String getAllTypes()
    {
        return new Gson().toJson(foodRepository.getAllTypes()).toString();
    }

    @ResponseBody
    @RequestMapping("/getByTypes")
    public String getByTypes(String type)
    {
        return new Gson().toJson(foodRepository.getByType(type)).toString();
    }

    @ResponseBody
    @RequestMapping("/addFood")
    public String addFood(String name, String type ,String introduction,String address, int price ,int amount)
    {
        Food food = new Food();
        food.setName(name);
        food.setType(type);
        food.setIntroduction(introduction);
        food.setAddress(address);
        food.setPrice(price);
        food.setAmount(amount);

        food = foodRepository.save(food);

        Map<String , Object> result = new HashMap<>();
        result.put("result",1);
        result.put("data",food);

        return new Gson().toJson(result).toString();
    }

    @ResponseBody
    @RequestMapping("/getFoodById")
    public String getFoodById(int id)
    {
        return new Gson().toJson(foodRepository.getById(id));
    }
}
