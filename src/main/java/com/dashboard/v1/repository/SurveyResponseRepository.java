package com.dashboard.v1.repository;

import com.dashboard.v1.entity.SurveyResponse;
import com.dashboard.v1.entity.SurveyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long>,
        JpaSpecificationExecutor<SurveyResponse> {

    @Query("SELECT s FROM SurveyResponse s WHERE s.uId = :uId")
    Optional<SurveyResponse> findByUId(String uId);

    @Query("SELECT s FROM SurveyResponse s WHERE s.projectId = :pId")
    Optional<List<SurveyResponse>> findByProjectId(String pId);

    @Query("SELECT s FROM SurveyResponse s WHERE s.vendorUsername = :vendorUsername")
    Optional<List<SurveyResponse>> findByVendorUsername(@Param("vendorUsername") String vendorUsername);

    @Query("SELECT s FROM SurveyResponse s WHERE s.ipAddress = :ipAddress AND s.projectId = :projectId")
    List<SurveyResponse> findByIpAddress(String ipAddress, String projectId);

    @Query("SELECT s FROM SurveyResponse s order by s.startTime desc")
    List<SurveyResponse> findAllOrderByCreatedAt();

}
