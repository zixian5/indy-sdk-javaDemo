package com.fanjungang.food.schedule;

import com.fanjungang.food.model.External;
import com.fanjungang.food.model.Information;
import com.fanjungang.food.model.Internal;
import com.fanjungang.food.model.Weather;
import com.fanjungang.food.repository.ExternalRepository;
import com.fanjungang.food.repository.InformationReopistory;
import com.fanjungang.food.repository.InternalRepository;
import com.fanjungang.food.repository.WeatherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@Component
public class SpiderSchedule {
    @Autowired
    private ExternalRepository externalRepository;
    @Autowired
    private InformationReopistory informationReopistory;
    @Autowired
    private InternalRepository internalRepository;
    @Autowired
    private WeatherRepository weatherRepository;

    public static final String url = "http://www.cereal.com.cn/";

    @Scheduled(fixedRate = 60*60*12*1000)
    public void work() throws Exception
    {
        doWork("t_infos fl", 1);
        doWork("t_infos fr",1);
        doWork("t_infos fl", 2);
        doWork("t_infos fr",2);
    }

    public void doWork(String tag, int times) throws Exception
    {
        Document document = Jsoup.connect("http://www.cereal.com.cn/").get();

        Elements elements = document.getElementsByClass(tag);
        Element element = null;
        if(times == 1){ element = elements.first(); }
        if(times == 2){ element = elements.last(); }
        Elements contentElements = element.getElementsByClass("t_infos_c");
        String content = null;
        String title = null;
        String time = null;
        for(Element contentElement : contentElements)
        {
            Elements liElements =  contentElement.getElementsByTag("li");
            for(Element liElement : liElements)
            {
                String contentUrl = url + liElement.select("a").first().attr("href");
                content = Jsoup.connect(contentUrl).get().getElementsByClass("scont").text();
                title =  liElement.attr("title");
                time = liElement.select("cite").first().text();

                System.out.println("title : "+liElement.attr("title")+" time : "+
                            liElement.select("cite").first().text() + " url :" +contentUrl);
                System.out.println("content : "+content);
                if(tag.equals("t_infos fl") && times == 1 && content != null && !content.equals(""))
                {
                    Information information = new Information();
                    information.setContent(content);
                    information.setTime(time);
                    information.setTitle(title);
                    informationReopistory.saveAndFlush(information);
                }
                if(tag.equals("t_infos fr") && times == 1 && content != null&& !content.equals(""))
                {
                    Internal internal = new Internal();
                    internal.setContent(content);
                    internal.setTime(time);
                    internal.setTitle(title);
                    internalRepository.saveAndFlush(internal);
                }
                if(tag.equals("t_infos fl") && times == 2 && content != null&& !content.equals(""))
                {
                    External external = new External();
                    external.setContent(content);
                    external.setTitle(title);
                    external.setTime(time);
                    externalRepository.saveAndFlush(external);
                }
                if(tag.equals("t_infos fr") && times == 2&& content != null&& !content.equals(""))
                {
                    Weather weather = new Weather();
                    weather.setContent(content);
                    weather.setTime(time);
                    weather.setTitle(title);
                    weatherRepository.saveAndFlush(weather);
                }
            }
        }

    }
}
