package com.aiinterview.platform.service.commerce.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.OrderRequest;
import com.aiinterview.platform.model.dto.response.OrderResponse;
import com.aiinterview.platform.model.entity.Order;
import com.aiinterview.platform.model.entity.ServicePackage;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.OrderStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.OrderRepository;
import com.aiinterview.platform.model.repository.ServicePackageRepository;
import com.aiinterview.platform.service.infra.EmailService;
import com.aiinterview.platform.service.payment.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Order rules (Postman folder 13): a package that is no longer on sale cannot be bought, and both
 * pay and cancel are allowed only while the order is still PENDING.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class OrderServiceImplTest {

    private static final Long PACKAGE_ID = 5L;
    private static final Long ORDER_ID = 88L;

    private OrderServiceImpl service;
    private OrderRepository orderRepository;
    private ServicePackageRepository servicePackageRepository;
    private EmailService emailService;
    private WalletService walletService;

    private User buyer;
    private User otherBuyer;
    private User staff;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        servicePackageRepository = mock(ServicePackageRepository.class);
        emailService = mock(EmailService.class);
        walletService = mock(WalletService.class);
        service = new OrderServiceImpl(orderRepository, servicePackageRepository, emailService, walletService);

        // 3 vai: chu don, nguoi mua khac (test quyen), staff (duoc xem moi don)
        buyer = user(20L, "candidate@example.com", "Candidate User", Role.ROLE_USER);
        otherBuyer = user(21L, "other@example.com", "Other Buyer", Role.ROLE_USER);
        staff = user(2L, "staff@example.com", "Staff User", Role.ROLE_STAFF);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Dat mua goi khong ton tai tra 404")
    void createOrder_unknownPackage_returnsNotFound() {
        // Arrange: packageId khong co trong DB
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.empty());

        // Act & Assert: 404 truoc khi tao don
        assertThatThrownBy(() -> service.createOrder(buyer, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy gói dịch vụ");

        // Assert: khong tao don
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Dat mua goi da vo hieu hoa bi chan 400")
    void createOrder_inactivePackage_returnsBadRequest() {
        // Arrange: goi ton tai nhung active = false (ngung ban)
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.of(pkg(false)));

        // Act & Assert: khong ban tiep goi da tat
        assertThatThrownBy(() -> service.createOrder(buyer, request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Gói dịch vụ này hiện không còn mở bán");

        // Assert: khong tao don
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Dat mua thanh cong: trang thai PENDING, sinh orderCode, chep gia va ten goi")
    void createOrder_activePackage_createsPendingOrder() {
        // Arrange: goi dang mo ban
        when(servicePackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.of(pkg(true)));

        // Act
        OrderResponse response = service.createOrder(buyer, request());

        // Assert: don moi luon PENDING (chua thanh toan)
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Assert: gia va ten goi duoc chep tai thoi diem mua (goi doi gia sau khong anh huong don cu)
        assertThat(response.getAmount()).isEqualTo(299_000L);
        assertThat(response.getPackageName()).isEqualTo("Goi Pro");
        assertThat(response.getPaymentMethod()).isEqualTo("PAYOS");

        // Assert: orderCode duoc sinh theo tien to ORD- va chua thanh toan nen paidAt null
        assertThat(response.getOrderCode()).startsWith("ORD-");
        assertThat(response.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("Thanh toan don khong ton tai tra 404")
    void pay_unknownOrder_returnsNotFound() {
        // Arrange: orderId khong co trong DB
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        // Act & Assert: 404
        assertThatThrownBy(() -> service.pay(buyer, ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy đơn hàng");
    }

    @Test
    @DisplayName("Thanh toan don cua nguoi khac bi chan 400")
    void pay_orderOfAnotherBuyer_returnsBadRequest() {
        // Arrange: don thuoc buyer, nguoi goi la otherBuyer
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(buyer, OrderStatus.PENDING)));

        // Act & Assert: chi chu don duoc thanh toan
        assertThatThrownBy(() -> service.pay(otherBuyer, ORDER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không phải chủ sở hữu đơn hàng này");

        // Assert: chan truoc khi gui mail
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Chi don PENDING moi thanh toan duoc")
    void pay_paidOrder_returnsBadRequest() {
        // Arrange: don da PAID
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(buyer, OrderStatus.PAID)));

        // Act & Assert: chan thanh toan lap (tranh tru tien 2 lan)
        assertThatThrownBy(() -> service.pay(buyer, ORDER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Chỉ đơn hàng ở trạng thái chờ thanh toán mới có thể thanh toán");

        // Assert: khong gui mail
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Thanh toan thanh cong ghi PAID, moc paidAt va gui mail xac nhan")
    void pay_pendingOrder_marksPaidAndSendsEmail() {
        // Arrange: don PENDING cua chinh buyer
        Order order = order(buyer, OrderStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // Act
        OrderResponse response = service.pay(buyer, ORDER_ID);

        // Assert: chuyen PAID va ghi moc thanh toan
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.getPaidAt()).isNotNull();

        // Assert: mail xac nhan gui dung nguoi, dung goi, dung so tien va dung ma don
        verify(emailService).sendOrderConfirmationEmail(
                buyer.getEmail(), buyer.getFullName(), "Goi Pro", 299_000L, order.getOrderCode());
    }

    @Test
    @DisplayName("Chi don PENDING moi huy duoc")
    void cancel_cancelledOrder_returnsBadRequest() {
        // Arrange: don da CANCELLED
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(buyer, OrderStatus.CANCELLED)));

        // Act & Assert: khong huy lan hai
        assertThatThrownBy(() -> service.cancel(buyer, ORDER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Chỉ đơn hàng ở trạng thái chờ thanh toán mới có thể hủy");

        // Assert: khong ghi gi
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Huy don PENDING chuyen sang CANCELLED")
    void cancel_pendingOrder_marksCancelled() {
        // Arrange: don PENDING cua buyer
        Order order = order(buyer, OrderStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // Act
        OrderResponse response = service.cancel(buyer, ORDER_ID);

        // Assert: chuyen CANCELLED va luu lai
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("GET /orders/my chi tra don cua nguoi dang dang nhap")
    void getMyOrders_queriesCurrentBuyer() {
        // Arrange: buyer co 1 don da thanh toan
        when(orderRepository.findByUserOrderByCreatedAtDesc(buyer))
                .thenReturn(List.of(order(buyer, OrderStatus.PAID)));

        // Act
        List<OrderResponse> orders = service.getMyOrders(buyer);

        // Assert: query loc theo nguoi dang dang nhap
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getId()).isEqualTo(ORDER_ID);
        verify(orderRepository).findByUserOrderByCreatedAtDesc(buyer);
    }

    @Test
    @DisplayName("GET /orders/{id} cho phep ADMIN/STAFF xem don cua nguoi khac")
    void getOrder_staff_canReadAnyOrder() {
        // Arrange: don thuoc buyer, nguoi doc la staff
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(buyer, OrderStatus.PAID)));

        // Act
        OrderResponse response = service.getOrder(staff, ORDER_ID);

        // Assert: staff duoc doc (phuc vu ho tro khach hang)
        assertThat(response.getId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("GET /orders/{id} voi nguoi khong lien quan bi chan 400")
    void getOrder_otherBuyer_returnsBadRequest() {
        // Arrange: don thuoc buyer, nguoi doc la otherBuyer (khong phai staff)
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(buyer, OrderStatus.PAID)));

        // Act & Assert: chan doc cheo don hang
        assertThatThrownBy(() -> service.getOrder(otherBuyer, ORDER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không có quyền xem đơn hàng này");
    }

    @Test
    @DisplayName("GET /orders (admin) sap xep moi nhat truoc")
    void getAllOrders_sortsNewestFirst() {
        // Arrange: repository tra ve thu tu cu -> moi
        Order older = order(buyer, OrderStatus.PAID);
        older.setCreatedAt(LocalDateTime.now().minusHours(3));
        older.setPackageName("Cu");
        Order newer = order(buyer, OrderStatus.PENDING);
        newer.setCreatedAt(LocalDateTime.now());
        newer.setPackageName("Moi");
        when(orderRepository.findAll()).thenReturn(List.of(older, newer));

        // Act
        List<OrderResponse> orders = service.getAllOrders();

        // Assert: service tu sap xep lai — don moi dung dau
        assertThat(orders).extracting(OrderResponse::getPackageName).containsExactly("Moi", "Cu");
    }

    @Test
    @DisplayName("Mail hong van khong lam rollback giao dich da PAID")
    void pay_emailFailure_propagatesAfterPersistingPaidStatus() {
        // Arrange: SMTP chet trong luc gui mail xac nhan
        Order order = order(buyer, OrderStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        doThrow(new IllegalStateException("smtp down"))
                .when(emailService).sendOrderConfirmationEmail(any(), any(), any(), any(), any());

        // Act & Assert: loi duoc nem ra ngoai (transaction rollback theo RuntimeException)
        assertThatThrownBy(() -> service.pay(buyer, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("smtp down");

        // Assert: van bat duoc lenh save voi status PAID — tuc thu tu luu -> gui mail duoc giu nguyen
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    private OrderRequest request() {
        OrderRequest request = new OrderRequest();
        request.setPackageId(PACKAGE_ID);
        request.setPaymentMethod("PAYOS");
        return request;
    }

    private ServicePackage pkg(boolean active) {
        return ServicePackage.builder()
                .id(PACKAGE_ID)
                .name("Goi Pro")
                .price(299_000L)
                .durationDays(30)
                .jobPostLimit(10)
                .active(active)
                .build();
    }

    private Order order(User owner, OrderStatus status) {
        return Order.builder()
                .id(ORDER_ID)
                .orderCode("ORD-20240101-1234")
                .user(owner)
                .servicePackage(pkg(true))
                .packageName("Goi Pro")
                .amount(299_000L)
                .status(status)
                .paymentMethod("PAYOS")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private User user(Long id, String email, String fullName, Role role) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName(fullName)
                .password("secret")
                .role(role)
                .build();
    }
}
