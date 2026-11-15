package com.centram.classzy.billing;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByOrderByPaidOnDescIdDesc();

    List<Payment> findByInstitutionIdOrderByPaidOnDescIdDesc(Long institutionId);

    List<Payment> findByPaidOnGreaterThanEqual(LocalDate from);
}
