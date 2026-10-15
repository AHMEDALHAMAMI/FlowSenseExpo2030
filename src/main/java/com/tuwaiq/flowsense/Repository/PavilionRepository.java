package com.tuwaiq.flowsense.Repository;

import com.tuwaiq.flowsense.Model.Pavilion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PavilionRepository extends JpaRepository<Pavilion, Integer> {

    Pavilion findPavilionById(Integer id);

    Pavilion findPavilionByName(String name);

    List<Pavilion> findAllByOrderByIdAsc();
}
