package com.aiinterview.platform.service.admin.impl;

import com.aiinterview.platform.model.dto.response.CategoryGroupsResponse;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GET /api/v1/admin/categories (spec 009 FR-008) — gom những gì nhà tuyển dụng thật sự gõ vào bốn ô
 * taxonomy để admin biết giá trị nào phổ biến.
 * <p>
 * Hợp đồng đáng test: đúng 4 nhóm theo thứ tự cố định, sắp xếp giảm dần theo số tin (hòa thì theo
 * alphabet để kết quả ổn định), bỏ giá trị rỗng, và giá trị lấy từ cột enum phải được stringify.
 */
class AdminCategoryServiceImplTest {

    private JobDescriptionRepository jobRepository;
    private AdminCategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobDescriptionRepository.class);
        service = new AdminCategoryServiceImpl(jobRepository);
    }

    @Test
    @DisplayName("Tra ve dung 4 nhom theo thu tu co dinh, khoa trung voi query param cua job wizard")
    void getCategories_returnsFourGroupsInFixedOrder() {
        stubAllGroups(List.of());

        CategoryGroupsResponse response = service.getCategories();

        assertThat(response.getGroups())
                .extracting(CategoryGroupsResponse.Group::getKey)
                .containsExactly("department", "experienceLevel", "employmentType", "workplaceModel");
        assertThat(response.getGroups())
                .extracting(CategoryGroupsResponse.Group::getLabel)
                .containsExactly("Phòng ban", "Cấp kinh nghiệm", "Kiểu làm việc", "Hình thức làm việc");
        verify(jobRepository).countGroupedByDepartment();
        verify(jobRepository).countGroupedByExperienceLevel();
        verify(jobRepository).countGroupedByEmploymentType();
        verify(jobRepository).countGroupedByWorkplaceModel();
    }

    @Test
    @DisplayName("Sap xep giam dan theo so tin, hoa diem thi theo alphabet de ket qua on dinh")
    void getCategories_sortsByJobCountDescThenValueAsc() {
        stubAllGroups(List.of());
        when(jobRepository.countGroupedByDepartment()).thenReturn(List.of(
                row("Sales", 5L),
                row("IT", 5L),
                row("HR", 9L)));

        List<CategoryGroupsResponse.Value> values = service.getCategories().getGroups().get(0).getValues();

        assertThat(values).extracting(CategoryGroupsResponse.Value::getValue)
                .containsExactly("HR", "IT", "Sales");
        assertThat(values).extracting(CategoryGroupsResponse.Value::getJobCount)
                .containsExactly(9L, 5L, 5L);
    }

    @Test
    @DisplayName("Bo cac gia tri rong hoac chi co khoang trang, vi do la tin nhap do dang")
    void getCategories_dropsBlankValues() {
        stubAllGroups(List.of());
        when(jobRepository.countGroupedByDepartment()).thenReturn(List.of(
                row("", 4L),
                row("   ", 3L),
                row("  Ke toan  ", 2L)));

        assertThat(service.getCategories().getGroups().get(0).getValues())
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.getValue()).isEqualTo("Ke toan");
                    assertThat(value.getJobCount()).isEqualTo(2L);
                });
    }

    @Test
    @DisplayName("Gia tri cua cot enum duoc stringify, con so dem nhan ca Integer lan Long")
    void getCategories_stringifiesEnumValuesAndAcceptsAnyNumberCount() {
        stubAllGroups(List.of());
        when(jobRepository.countGroupedByEmploymentType()).thenReturn(List.of(
                new Object[]{DayOfWeek.MONDAY, 7},
                new Object[]{null, 1L}));

        List<CategoryGroupsResponse.Value> values = service.getCategories().getGroups().get(2).getValues();

        // null -> "null" (khong rong) nen van duoc giu lai; Integer 7 phai ra 7L
        assertThat(values).extracting(CategoryGroupsResponse.Value::getValue)
                .containsExactly("MONDAY", "null");
        assertThat(values).extracting(CategoryGroupsResponse.Value::getJobCount)
                .containsExactly(7L, 1L);
    }

    @Test
    @DisplayName("Chua co tin nao thi ca 4 nhom deu rong nhung van du 4 nhom")
    void getCategories_withNoData_returnsEmptyValueLists() {
        stubAllGroups(List.of());

        CategoryGroupsResponse response = service.getCategories();

        assertThat(response.getGroups()).hasSize(4);
        assertThat(response.getGroups()).allSatisfy(group -> assertThat(group.getValues()).isEmpty());
    }

    private void stubAllGroups(List<Object[]> rows) {
        when(jobRepository.countGroupedByDepartment()).thenReturn(rows);
        when(jobRepository.countGroupedByExperienceLevel()).thenReturn(rows);
        when(jobRepository.countGroupedByEmploymentType()).thenReturn(rows);
        when(jobRepository.countGroupedByWorkplaceModel()).thenReturn(rows);
    }

    private Object[] row(String value, long count) {
        return new Object[]{value, count};
    }
}
