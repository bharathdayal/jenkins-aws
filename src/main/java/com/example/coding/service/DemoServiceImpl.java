package com.example.coding.service;

import com.example.coding.model.DemoEpisode;
import com.example.coding.model.DemoModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DemoServiceImpl implements DemoService{

    @Autowired
    private RestClient restClient;

    @Override
    public DemoModel createDemo(DemoModel demo) {
        return demo;
    }

    @Override
    public DemoModel fetchDemo(Integer id){
        DemoModel model= new DemoModel();
        model.setName("DEMO NAME");
        return model;
    }

    @Override
    public DemoModel fetchDemoEpisode(Integer id) {
        return restClient.get()
                .uri("/api/episode/{id}",id)
                .retrieve()
                .body(DemoModel.class);
    }
    @Override
    public DemoEpisode fetchDemoEpisodeName(Integer id) {
        return restClient.get()
                .uri("/api/character/{id}",id)
                .retrieve()
                .body(DemoEpisode.class);
    }
}
