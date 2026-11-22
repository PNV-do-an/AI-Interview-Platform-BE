package com.aiinterview.platform.service.commerce.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.ServicePackageRequest;
import com.aiinterview.platform.model.dto.response.ServicePackageResponse;
import com.aiinterview.platform.model.entity.ServicePackage;
import com.aiinterview.platform.model.repository.OrderRepository;
import com.aiinterview.platform.model.repository.ServicePackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Package catalogue rules (Postman folder 13): names are unique, new packages are active, and a
 * package that already has orders must be disabled instead of deleted so history stays readable.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class ServicePackageServiceImplTest {

    private static final Long PACKAGE_ID = 5L;

    private ServicePackageServiceImpl service;
    private ServicePackageRepository servicePackageRepository;
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        servicePackageRepository = mock(ServicePackageRepository.class);
        orderRepository = mock(OrderRepository.class);
        service = new ServicePackageServiceImpl(servicePackageRepository, orderRepository);

        when(servicePackageRepository.save(any(ServicePackage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("GET /packages chi tra goi dang mo ban")
    void listActive_queriesActivePackagesSortedByPrice() {
        // Arrange: chi co 1 goi active
        when(servicePackageRepository.findByActiveTrueOrderByPriceAsc()).thenReturn(List.of(pkg(true)));

        // Act
        List<ServicePackageResponse> packages = service.listActive();

        // Assert: dung query "active = true" va sap xep theo gia (endpoint public)
        assertThat(packages).hasSize(1);
        assertThat(packages.get(0).getActive()).isTrue();
        verify(servicePackageRepository).findByActiveTrueOrderByPriceAsc();
    }

    @Test
    @DisplayName("POST /admin/packages tao goi moi o trang thai active va trim ten")
    void create_newName_createsActivePackage() {
        // Arrange: ten chua ton tai trong catalogue
        when(servicePackageRepository.existsByName("Goi Pro")).thenReturn(false);

        // Act: ten gui len co khoang trang hai dau
        ServicePackageResponse response = service.create(request("  Goi Pro  "));

        // Assert: ten duoc trim truoc khi kiem tra trung va luu
        assertThat(response.getName()).isEqualTo("Goi Pro");

        // Assert: goi moi luon active, gia va features duoc map day du
        assertThat(response.getActive()).isTrue();
        assertThat(response.getPrice()).isEqualTo(299_000L);
        assertThat(response.getFeatures()).containsExactly("Khong gioi han", "Ho tro 24/7");
    }

    @Test
    @DisplayName("Ten goi trung bi chan 400")
    void create_duplicateName_returnsBadRequest() {
        // Arrange: da co goi ten "Goi Pro"
        when(servicePackageRepository.existsByName("Goi Pro")).thenReturn(true);

        // Act & Assert: ten goi la duy nhat (FE dung ten de phan biet)
        assertThatThrownBy(() -> service.create(request("Goi Pro")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tên gói đã tồn tại");

        // Assert: khong luu goi trung
        verify(servicePackageRepository, never()).save(any(ServicePackage.class));
    }

    @Test
    @DisplayName("Cap nhat goi khong ton tai tra 404")
    void update_unknownPackage_returnsNotFound() {
        // Arrange: packageId khong co trong DB
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.empty());

        // Act & Assert: 404
        assertThatThrownBy(() -> service.update(PACKAGE_ID, request("Goi Pro")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy gói dịch vụ");
    }

    @Test
    @DisplayName("Doi sang ten da co bi chan 400, giu nguyen ten cu")
    void update_toExistingName_returnsBadRequest() {
        // Arrange: goi "Goi Pro" doi ten thanh "Goi Enterprise" — ten nay da co o goi khac
        ServicePackage existing = pkg(true);
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.of(existing));
        when(servicePackageRepository.existsByName("Goi Enterprise")).thenReturn(true);

        // Act & Assert: chan trung ten
        assertThatThrownBy(() -> service.update(PACKAGE_ID, request("Goi Enterprise")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tên gói đã tồn tại");

        // Assert: entity khong bi sua giua chung va khong luu
        assertThat(existing.getName()).isEqualTo("Goi Pro");
        verify(servicePackageRepository, never()).save(any(ServicePackage.class));
    }

    @Test
    @DisplayName("Giu nguyen ten cu thi khong bi coi la trung")
    void update_sameName_skipsDuplicateCheck() {
        // Arrange: form gui lai dung ten cu, chi doi gia
        ServicePackage existing = pkg(true);
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.of(existing));
        ServicePackageRequest request = request("Goi Pro");
        request.setPrice(399_000L);

        // Act
        ServicePackageResponse response = service.update(PACKAGE_ID, request);

        // Assert: cap nhat duoc gia moi
        assertThat(response.getPrice()).isEqualTo(399_000L);

        // Assert: ten khong doi thi bo qua buoc check trung (tranh tu chan chinh minh)
        verify(servicePackageRepository, never()).existsByName(any());
    }

    @Test
    @DisplayName("Cap nhat day du cac truong cua goi")
    void update_persistsAllFields() {
        // Arrange: doi mo ta, thoi han, gioi han tin va danh sach quyen loi
        ServicePackage existing = pkg(true);
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.of(existing));
        ServicePackageRequest request = request("Goi Pro");
        request.setDescription("Mo ta moi");
        request.setDurationDays(60);
        request.setJobPostLimit(20);
        request.setFeatures(List.of("Uu tien hien thi"));

        // Act
        ServicePackageResponse response = service.update(PACKAGE_ID, request);

        // Assert: ca 4 truong deu duoc cap nhat va tra ve
        assertThat(response.getDescription()).isEqualTo("Mo ta moi");
        assertThat(response.getDurationDays()).isEqualTo(60);
        assertThat(response.getJobPostLimit()).isEqualTo(20);
        assertThat(response.getFeatures()).containsExactly("Uu tien hien thi");
    }

    @Test
    @DisplayName("PATCH toggle dao trang thai active theo ca hai chieu")
    void toggleActive_flipsFlagBothWays() {
        // Arrange: goi dang active
        ServicePackage active = pkg(true);
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.of(active));

        // Act: toggle 2 lan lien tiep
        ServicePackageResponse disabled = service.toggleActive(PACKAGE_ID);
        ServicePackageResponse reenabled = service.toggleActive(PACKAGE_ID);

        // Assert: tat -> bat lai (ham dao, khong phai set co dinh)
        assertThat(disabled.getActive()).isFalse();
        assertThat(reenabled.getActive()).isTrue();
        verify(servicePackageRepository, times(2)).save(active);
    }

    @Test
    @DisplayName("Goi da co don hang khong duoc xoa")
    void delete_packageWithOrders_returnsBadRequest() {
        // Arrange: goi da vo hieu hoa nhung con 2 don hang tham chieu
        ServicePackage existing = pkg(false);
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.of(existing));
        when(orderRepository.countByServicePackage(existing)).thenReturn(2L);

        // Act & Assert: chan xoa de khong mat lich su don hang, huong dan vo hieu hoa thay the
        assertThatThrownBy(() -> service.delete(PACKAGE_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Gói đã có đơn hàng — không thể xóa. Hãy vô hiệu hóa gói thay thế.");
        verify(servicePackageRepository, never()).delete(any(ServicePackage.class));
    }

    @Test
    @DisplayName("Goi chua co don hang thi xoa duoc")
    void delete_packageWithoutOrders_removesIt() {
        // Arrange: goi chua tung duoc mua
        ServicePackage existing = pkg(false);
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.of(existing));
        when(orderRepository.countByServicePackage(existing)).thenReturn(0L);

        // Act
        service.delete(PACKAGE_ID);

        // Assert: xoa dung dong vua doc
        verify(servicePackageRepository).delete(existing);
    }

    @Test
    @DisplayName("Xoa goi khong ton tai tra 404 va khong dem don hang")
    void delete_unknownPackage_returnsNotFound() {
        // Arrange: packageId khong co trong DB
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.empty());

        // Act & Assert: 404
        assertThatThrownBy(() -> service.delete(PACKAGE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy gói dịch vụ");

        // Assert: khong ton cong dem don hang khi goi khong ton tai
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("Danh sach cho admin tra ve ca goi da vo hieu hoa")
    void listAll_includesInactivePackages() {
        // Arrange: 1 goi active + 1 goi da tat
        when(servicePackageRepository.findAll()).thenReturn(List.of(pkg(true), pkg(false)));

        // Act
        List<ServicePackageResponse> packages = service.listAll();

        // Assert: man hinh admin thay ca 2 (khac endpoint public chi thay goi active)
        assertThat(packages).extracting(ServicePackageResponse::getActive)
                .containsExactly(true, false);
    }

    private ServicePackageRequest request(String name) {
        ServicePackageRequest request = new ServicePackageRequest();
        request.setName(name);
        request.setDescription("Mo ta");
        request.setPrice(299_000L);
        request.setDurationDays(30);
        request.setJobPostLimit(10);
        request.setFeatures(List.of("Khong gioi han", "Ho tro 24/7"));
        return request;
    }

    private ServicePackage pkg(boolean active) {
        return ServicePackage.builder()
                .id(PACKAGE_ID)
                .name("Goi Pro")
                .description("Mo ta")
                .price(299_000L)
                .durationDays(30)
                .jobPostLimit(10)
                .features(List.of("Khong gioi han"))
                .active(active)
                .build();
    }
}
