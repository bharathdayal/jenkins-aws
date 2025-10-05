package com.example.coding.service;

import com.example.coding.model.DemoEpisode;
import com.example.coding.model.DemoModel;

public interface DemoService {

    DemoModel createDemo(DemoModel demo);

    DemoModel fetchDemo(Integer id);

    DemoModel fetchDemoEpisode(Integer id);

    DemoEpisode fetchDemoEpisodeName(Integer id);
}
