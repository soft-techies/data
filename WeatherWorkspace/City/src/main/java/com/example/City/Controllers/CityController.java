package com.example.City.Controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.City.Repository.CityRepository;
import com.example.City.model.CityModel;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/cities")
public class CityController {

    @Autowired
    private CityRepository cityRepository;

    @GetMapping("/data")
    public List<CityModel> getCities() {
        return cityRepository.findAll();
    }

    @PostMapping("/savedata")
    public CityModel createCity(@RequestBody CityModel city) {
        return cityRepository.save(city);
    }
    
    @PostMapping("/dltdata")
    public Object deleteCity(Long id) {
    	
    	cityRepository.deleteById(id);
    	
    	return 1; 
  
    }

  
}