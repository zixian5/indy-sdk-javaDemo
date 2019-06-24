package com.fanjungang.food.controller;

import com.fanjungang.food.model.Information;
import com.fanjungang.food.repository.ExternalRepository;
import com.fanjungang.food.repository.InformationReopistory;
import com.fanjungang.food.repository.InternalRepository;
import com.fanjungang.food.repository.WeatherRepository;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

@Controller
public class SpiderController {
    @Autowired
    private ExternalRepository externalRepository;
    @Autowired
    private InformationReopistory informationReopistory;
    @Autowired
    private InternalRepository internalRepository;
    @Autowired
    private WeatherRepository weatherRepository;


    @ResponseBody
    @RequestMapping("/getExternalList")
    public String getExternalList()
    {
        return new Gson().toJson(externalRepository.findAll().subList(0,6)).toString();
    }

    @ResponseBody
    @RequestMapping("/getExternalById")
    public String getExternalById(int id)
    {
        return new Gson().toJson(externalRepository.findById(id)).toString();
    }

    @ResponseBody
    @RequestMapping(value = "/getInternalList")
    public String getInternalList()
    {
        return new Gson().toJson(internalRepository.findAll().subList(0,6)).toString();
    }

    @ResponseBody
    @RequestMapping("/getInternalById")
    public String getInternalById(int id)
    {
        return new Gson().toJson(internalRepository.getById(id)).toString();
    }

    @ResponseBody
    @RequestMapping("/getInformationList")
    public String getInformationList()
    {
        return new Gson().toJson(informationReopistory.findAll().subList(0,6)).toString();
    }

    @ResponseBody
    @RequestMapping("/addInformation")
    public String addInformation(String title, String content)
    {
        Information information = new Information();
        information.setTitle(title);
        String time = "";
        Calendar calendar = Calendar.getInstance();
        time = time + calendar.get(Calendar.YEAR) + "-"+ (1+calendar.get(Calendar.MONTH)) +"-" + (calendar.get(Calendar.DAY_OF_MONTH));
        information.setTime(time);
        information.setContent(content);

        information = informationReopistory.saveAndFlush(information);

        Map<String, Object> result = new HashMap<>();
        result.put("result",1);
        result.put("data",information);

        return new Gson().toJson(result).toString();
    }

    @ResponseBody
    @RequestMapping("/getInformationById")
    public String getInformationById(int id)
    {
        return new Gson().toJson(informationReopistory.getById(id)).toString();
    }

    @ResponseBody
    @RequestMapping("/getWeatherList")
    public String getWeatherList()
    {
        return new Gson().toJson(weatherRepository.findAll().subList(0,6)).toString();
    }

    @ResponseBody
    @RequestMapping("/getWeatherById")
    public String getWeatherById(int id)
    {
        return new Gson().toJson(weatherRepository.getById(id));
    }













}
