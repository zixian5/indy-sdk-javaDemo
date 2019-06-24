package com.fan.foodmanagement;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SpiderTest {
    public static final String url = "http://www.cereal.com.cn/";

    public static void main(String[] args) throws  Exception {

        Document document = Jsoup.connect("http://www.cereal.com.cn/").get();

        Elements elements = document.getElementsByClass("t_infos fl");
        for(Element element: elements)
        {
            Elements contentElements = element.getElementsByClass("t_infos_c");
            for(Element contentElement : contentElements)
            {
                Elements liElements =  contentElement.getElementsByTag("li");
                for(Element liElement : liElements)
                {
                    String contentUrl = url + liElement.select("a").first().attr("href");
                    String content = Jsoup.connect(contentUrl).get().getElementsByClass("scont").text();

                    System.out.println("title : "+liElement.attr("title")+" time : "+
                          liElement.select("cite").first().text() + " url :" +contentUrl);
                    System.out.println("content : "+content);
                }
            }
        }
    }
}
