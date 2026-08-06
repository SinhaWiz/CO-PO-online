package org.example.copo.repository;

import org.example.copo.entity.PO;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PORepository extends JpaRepository<PO, Integer> {
    List<PO> findAllByOrderByPoNumberAsc();
}
