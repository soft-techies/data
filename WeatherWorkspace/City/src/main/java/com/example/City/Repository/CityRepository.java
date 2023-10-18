package com.example.City.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.City.model.CityModel;

@Repository
public interface CityRepository extends JpaRepository<CityModel, Long> {
	
}
