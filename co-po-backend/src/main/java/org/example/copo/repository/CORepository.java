package org.example.copo.repository;

import org.example.copo.entity.CO;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CORepository extends JpaRepository<CO, Integer> {
    List<CO> findAllByOrderByCoNumberAsc();
}
