package com.example.coding.repository;

import com.example.coding.model.DemoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.stereotype.Repository;

@NoRepositoryBean
public interface DemoRrepository extends JpaRepository<DemoModel,Long> {
}
