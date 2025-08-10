package com.example.imagecollage.repository;

import com.example.imagecollage.entity.Collage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollageRepository extends JpaRepository<Collage, Long> {
}