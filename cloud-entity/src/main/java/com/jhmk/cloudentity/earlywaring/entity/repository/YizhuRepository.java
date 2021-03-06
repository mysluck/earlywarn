package com.jhmk.cloudentity.earlywaring.entity.repository;

import com.jhmk.cloudentity.earlywaring.entity.rule.Yizhu;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface YizhuRepository extends PagingAndSortingRepository<Yizhu, Integer>, JpaSpecificationExecutor<Yizhu> {

    @Query("select b  from  Yizhu  b where b.patient_id = ?1 and b.visit_id = ?2")
    List<Yizhu> findAllByPatientIdAndVisitId(String patient_id, String visit_id);

    @Query("select b from Yizhu b where b.patient_id = ?1 and b.visit_id <=?2 ")
    List<Yizhu> findLessThanVisit_id(String patient_id, String visit_id);


    @Modifying
    @Query("delete from  Yizhu b where b.patient_id = ?1 and b.visit_id <=?2 ")
    void deleteByPatient_idAndVisit_id(String patient_id, String visit_id);

    @Modifying
    @Query("delete from  Yizhu b where b.order_no = ?1")
    void deleteByOrder_no(String orderNo);

    @Query("select b from  Yizhu b where b.order_no = ?1")
    List<Yizhu>findAllByOrder_no(String orderNo);
}
