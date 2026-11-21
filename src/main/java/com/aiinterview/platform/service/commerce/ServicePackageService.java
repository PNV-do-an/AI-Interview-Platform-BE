package com.aiinterview.platform.service.commerce;

import com.aiinterview.platform.model.dto.request.ServicePackageRequest;
import com.aiinterview.platform.model.dto.response.ServicePackageResponse;

import java.util.List;

public interface ServicePackageService {

    List<ServicePackageResponse> listActive();

    List<ServicePackageResponse> listAll();

    ServicePackageResponse create(ServicePackageRequest request);

    ServicePackageResponse update(Long id, ServicePackageRequest request);

    ServicePackageResponse toggleActive(Long id);

    void delete(Long id);
}
