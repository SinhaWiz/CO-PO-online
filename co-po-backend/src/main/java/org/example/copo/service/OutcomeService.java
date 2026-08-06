package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.CO;
import org.example.copo.entity.PO;
import org.example.copo.exception.ResourceNotFoundException;
import org.example.copo.repository.CORepository;
import org.example.copo.repository.PORepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CO/PO master data. Small, paired concept (a course picks its allowed subset of both
 * in one screen) so they share one service/controller rather than being split like the
 * larger entities (Course, Student, ...) are.
 */
@Service
@RequiredArgsConstructor
public class OutcomeService {

    private final CORepository coRepository;
    private final PORepository poRepository;

    public List<CO> getAllCOs() {
        return coRepository.findAllByOrderByCoNumberAsc();
    }

    public CO createCO(CO co) {
        return coRepository.save(co);
    }

    public void deleteCO(Integer id) {
        if (!coRepository.existsById(id)) {
            throw new ResourceNotFoundException("CO not found: " + id);
        }
        coRepository.deleteById(id);
    }

    public List<PO> getAllPOs() {
        return poRepository.findAllByOrderByPoNumberAsc();
    }

    public PO createPO(PO po) {
        return poRepository.save(po);
    }

    public void deletePO(Integer id) {
        if (!poRepository.existsById(id)) {
            throw new ResourceNotFoundException("PO not found: " + id);
        }
        poRepository.deleteById(id);
    }
}
