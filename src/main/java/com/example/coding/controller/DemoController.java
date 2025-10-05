package com.example.coding.controller;

import com.example.coding.model.DemoEpisode;
import com.example.coding.model.DemoModel;
import com.example.coding.service.DemoServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DemoController {

    private DemoServiceImpl demoService;


    public DemoController(DemoServiceImpl demoService) {
        this.demoService=demoService;
    }

    @PostMapping("/create")
    public DemoModel createDemo(@RequestBody DemoModel model){
        return demoService.createDemo(model);
    }

    @GetMapping("/fetch/{id}")
    public DemoModel fetchDemo(@PathVariable Integer id){
        return demoService.fetchDemo(id);
    }

    @GetMapping("/episode/{id}")
    public DemoModel episode(@PathVariable Integer id){

        DemoModel episodeInfo = demoService.fetchDemoEpisode(id);
        List<String> result=new ArrayList<>();

        List<String> epdisodeCharacterURL = episodeInfo.getCharacters();
       // System.out.println("URL===>"+epdisodeCharacterURL.toString());

        for (String episodeURL : epdisodeCharacterURL) {
            String[] parts = episodeURL.split("/");
            String idChar = parts[parts.length - 1];
            DemoEpisode demoEpisode = demoService.fetchDemoEpisodeName(Integer.valueOf(idChar));
            result.add(demoEpisode.getName());
            episodeInfo.setCharacters(result);

        }
        return episodeInfo;
    }

}
