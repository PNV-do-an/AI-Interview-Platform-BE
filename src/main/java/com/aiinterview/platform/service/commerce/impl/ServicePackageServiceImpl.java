package com.aiinterview.platform.service.commerce.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.ServicePackageRequest;
import com.aiinterview.platform.model.dto.response.ServicePackageResponse;
import com.aiinterview.platform.model.entity.ServicePackage;
import com.aiinterview.platform.model.repository.OrderRepository;
import com.aiinterview.platform.model.repository.ServicePackageRepository;
import com.aiinterview.platform.service.commerce.ServicePackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicePackageServiceImpl implements ServicePackageService {

    private final ServicePackageRepository servicePackageRepository;
    private final OrderRepository orderRepository;

    // readOnly: features là @ElementCollection LAZY, phải map sang DTO trong lúc session còn mở.
    // open-in-view=false nên nếu không bọc transaction, Jackson chạm features lúc serialize sẽ
    // ném LazyInitializationException (500) ngay khi DB đã seed gói dịch vụ.
    @Override
    @Transactional(readOnly = true)
    public List<ServicePackageResponse> listActive() {
        return servicePackageRepository.findByActiveTrueOrderByPriceAsc().stream()
                .map(ServicePackageResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServicePackageResponse> listAll() {
        return servicePackageRepository.findAll().stream()
                .map(ServicePackageResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ServicePackageResponse create(ServicePackageRequest request) {
        if (servicePackageRepository.existsByName(request.getName().trim())) {
            throw new BadRequestException("Tên gói đã tồn tại");
        }
        ServicePackage pkg = ServicePackage.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .price(request.getPrice())
                .durationDays(request.getDurationDays())
                .jobPostLimit(request.getJobPostLimit())
                .features(request.getFeatures() != null ? new ArrayList<>(request.getFeatures()) : new ArrayList<>())
                .active(true)
                .build();
        return ServicePackageResponse.from(servicePackageRepository.save(pkg));
    }

    @Override
    @Transactional
    public ServicePackageResponse update(Long id, ServicePackageRequest request) {
        ServicePackage pkg = findById(id);
        String newName = request.getName().trim();
        if (!pkg.getName().equals(newName) && servicePackageRepository.existsByName(newName)) {
            throw new BadRequestException("Tên gói đã tồn tại");
        }
        pkg.setName(newName);
        pkg.setDescription(request.getDescription());
        pkg.setPrice(request.getPrice());
        pkg.setDurationDays(request.getDurationDays());
        pkg.setJobPostLimit(request.getJobPostLimit());
        pkg.setFeatures(request.getFeatures() != null ? new ArrayList<>(request.getFeatures()) : new ArrayList<>());
        return ServicePackageResponse.from(servicePackageRepository.save(pkg));
    }

    @Override
    @Transactional
    public ServicePackageResponse toggleActive(Long id) {
        ServicePackage pkg = findById(id);
        pkg.setActive(!Boolean.TRUE.equals(pkg.getActive()));
        return ServicePackageResponse.from(servicePackageRepository.save(pkg));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ServicePackage pkg = findById(id);
        if (orderRepository.countByServicePackage(pkg) > 0) {
            throw new BadRequestException("Gói đã có đơn hàng — không thể xóa. Hãy vô hiệu hóa gói thay thế.");
        }
        servicePackageRepository.delete(pkg);
    }

    private ServicePackage findById(Long id) {
        return servicePackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ"));
    }
}
